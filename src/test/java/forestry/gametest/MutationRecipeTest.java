package forestry.gametest;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.google.gson.JsonElement;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.climate.IClimateProvider;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.IMutationManager;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.core.features.GeneticsRecipeTypes;
import forestry.core.genetics.mutations.Mutation;
import forestry.core.genetics.mutations.MutationConditionBiome;
import forestry.core.genetics.mutations.MutationConditionCave;
import forestry.core.genetics.mutations.MutationConditionDaytime;
import forestry.core.genetics.mutations.MutationConditionHumidity;
import forestry.core.genetics.mutations.MutationConditionRequiresResource;
import forestry.core.genetics.mutations.MutationConditionTemperature;
import forestry.core.genetics.mutations.MutationConditionTimeLimited;
import forestry.core.genetics.mutations.MutationConditionTypes;
import forestry.core.genetics.mutations.MutationRecipe;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for "mutations as recipes". Proves that the datapack-loaded mutation recipes were parsed into the
 * runtime {@link IMutationManager} index for every species type, that the condition and recipe codecs survive JSON/NBT
 * and network round-trips, that breeding actually yields a known built-in mutation, and that climate conditions gate
 * the mutation chance. All assertions key off stable, classic Forestry mutations (Forest+Marshy&rarr;Common, etc.) and
 * "non-empty"/"contains" checks rather than exact counts, so datapack edits do not make them brittle.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class MutationRecipeTest {
	/** The mutation index must be populated from the generated recipes for all three species types. */
	@GameTest(template = "empty")
	public static void mutationIndexPopulated(GameTestHelper helper) {
		// --- Bees: Forest + Marshy -> Common @ 0.15 (a stable, unconditioned classic mutation) ---
		IMutationManager<IBeeSpecies> beeMutations = SpeciesUtil.BEE_TYPE.get().getMutations();
		if (beeMutations.getAllMutations().isEmpty()) {
			helper.fail("Bee mutation index is empty; recipes were not loaded into the MutationManager");
			return;
		}
		IBeeSpecies forest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
		IBeeSpecies marshy = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.MARSHY);
		IBeeSpecies common = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.COMMON);

		IMutation<IBeeSpecies> forestMarshy = findMutation(beeMutations.getCombinations(forest, marshy), ForestryBeeSpecies.COMMON);
		if (forestMarshy == null) {
			helper.fail("Expected Forest+Marshy->Common bee mutation in the index");
			return;
		}
		if (Math.abs(forestMarshy.getChance() - 0.15f) > 1.0e-6f) {
			helper.fail("Forest+Marshy->Common chance was " + forestMarshy.getChance() + ", expected 0.15");
			return;
		}
		if (beeMutations.getMutationsInto(common).isEmpty()) {
			helper.fail("No mutations resolve into Common bee");
			return;
		}

		// --- Trees: Teak + Birch -> Balsa ---
		IMutationManager<ITreeSpecies> treeMutations = SpeciesUtil.TREE_TYPE.get().getMutations();
		if (treeMutations.getAllMutations().isEmpty()) {
			helper.fail("Tree mutation index is empty");
			return;
		}
		ITreeSpecies teak = SpeciesUtil.TREE_TYPE.get().getSpecies(ForestryTreeSpecies.TEAK);
		ITreeSpecies birch = SpeciesUtil.TREE_TYPE.get().getSpecies(ForestryTreeSpecies.BIRCH);
		if (findMutation(treeMutations.getCombinations(teak, birch), ForestryTreeSpecies.BALSA) == null) {
			helper.fail("Expected Teak+Birch->Balsa tree mutation in the index");
			return;
		}

		// --- Butterflies: Latticed Heath + Brimstone -> Bombyx Mori (the single built-in butterfly mutation) ---
		IMutationManager<IButterflySpecies> butterflyMutations = SpeciesUtil.BUTTERFLY_TYPE.get().getMutations();
		if (butterflyMutations.getAllMutations().isEmpty()) {
			helper.fail("Butterfly mutation index is empty");
			return;
		}
		IButterflySpecies latticed = SpeciesUtil.BUTTERFLY_TYPE.get().getSpecies(ForestryButterflySpecies.LATTICED_HEATH);
		IButterflySpecies brimstone = SpeciesUtil.BUTTERFLY_TYPE.get().getSpecies(ForestryButterflySpecies.BRIMSTONE);
		if (findMutation(butterflyMutations.getCombinations(latticed, brimstone), ForestryButterflySpecies.BOMBYX_MORI) == null) {
			helper.fail("Expected Latticed Heath+Brimstone->Bombyx Mori butterfly mutation in the index");
			return;
		}

		helper.succeed();
	}

	/** Each of the 7 condition types must survive a JSON dispatch-codec and a network stream-codec round-trip. */
	@GameTest(template = "empty")
	public static void conditionCodecRoundTrip(GameTestHelper helper) {
		TagKey<Biome> forestBiomes = BiomeTags.IS_FOREST;
		List<IMutationCondition> conditions = List.of(
			new MutationConditionTemperature(TemperatureType.WARM, TemperatureType.HOT),
			new MutationConditionHumidity(HumidityType.NORMAL, HumidityType.DAMP),
			new MutationConditionBiome(forestBiomes),
			new MutationConditionDaytime(true),
			new MutationConditionTimeLimited(3, 29, 4, 15),
			new MutationConditionRequiresResource(Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState()),
			new MutationConditionCave()
		);

		for (IMutationCondition condition : conditions) {
			Component expected = condition.getDescription();

			// JSON dispatch codec.
			JsonElement json = MutationConditionTypes.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow();
			IMutationCondition fromJson = MutationConditionTypes.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
			if (fromJson.type() != condition.type()) {
				helper.fail("JSON round-trip changed condition type for " + condition.getClass().getSimpleName());
				return;
			}
			if (!fromJson.getDescription().equals(expected)) {
				helper.fail("JSON round-trip changed condition for " + condition.getClass().getSimpleName()
						+ ": " + expected + " -> " + fromJson.getDescription());
				return;
			}

			// Network stream codec.
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			MutationConditionTypes.STREAM_CODEC.encode(buf, condition);
			IMutationCondition fromBuf = MutationConditionTypes.STREAM_CODEC.decode(buf);
			if (fromBuf.type() != condition.type() || !fromBuf.getDescription().equals(expected)) {
				helper.fail("Stream round-trip changed condition for " + condition.getClass().getSimpleName());
				return;
			}
		}

		helper.succeed();
	}

	/** A built MutationRecipe must survive the BEE serializer's NBT codec and network stream-codec unchanged. */
	@GameTest(template = "empty")
	public static void recipeCodecRoundTrip(GameTestHelper helper) {
		List<IMutationCondition> conditions = List.of(
			new MutationConditionTemperature(TemperatureType.WARM, TemperatureType.HOT),
			new MutationConditionDaytime(true)
		);
		MutationRecipe recipe = new MutationRecipe(
			ForestrySpeciesTypes.BEE,
			ForestryConstants.forestry("bee_mutation/test_roundtrip"),
			ForestryBeeSpecies.FOREST,
			ForestryBeeSpecies.MARSHY,
			ForestryBeeSpecies.COMMON,
			0.15f,
			conditions,
			Map.of()
		);

		MutationRecipe.Serializer serializer = (MutationRecipe.Serializer) GeneticsRecipeTypes.BEE_MUTATION.serializer();

		// NBT codec.
		MapCodec<MutationRecipe> mapCodec = serializer.codec();
		Codec<MutationRecipe> codec = mapCodec.codec();
		Tag nbt = codec.encodeStart(NbtOps.INSTANCE, recipe).getOrThrow();
		MutationRecipe fromNbt = codec.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		if (!recipesEqual(recipe, fromNbt)) {
			helper.fail("NBT codec round-trip changed the mutation recipe");
			return;
		}

		// Network stream codec.
		StreamCodec<RegistryFriendlyByteBuf, MutationRecipe> streamCodec = serializer.streamCodec();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		streamCodec.encode(buf, recipe);
		MutationRecipe fromBuf = streamCodec.decode(buf);
		if (!recipesEqual(recipe, fromBuf)) {
			helper.fail("Stream codec round-trip changed the mutation recipe");
			return;
		}

		helper.succeed();
	}

	/**
	 * Breeding must be able to produce a known mutation, and climate conditions must gate the chance to zero when unmet.
	 * <p>
	 * The breeding loop uses the real {@link SpeciesUtil#mutateSpecies} path on Forest+Marshy, which has exactly one
	 * (unconditioned, chance 0.15) combination. Across 1000 attempts the probability of never succeeding is
	 * {@code 0.85^1000 (~1e-71)}, so a non-null result is effectively guaranteed and any such result must be the Common
	 * mutation. Condition gating is checked deterministically via the full {@link Mutation#getChance} path and a direct
	 * {@code modifyChance} call using inline {@link IClimateProvider}s, so it never depends on the template's biome.
	 */
	@GameTest(template = "empty")
	public static void breedingProducesMutation(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);

		IMutationManager<IBeeSpecies> beeMutations = SpeciesUtil.BEE_TYPE.get().getMutations();
		IBeeSpecies forest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FOREST);
		IBeeSpecies marshy = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.MARSHY);

		IMutation<IBeeSpecies> common = findMutation(beeMutations.getCombinations(forest, marshy), ForestryBeeSpecies.COMMON);
		if (common == null) {
			helper.fail("Missing Forest+Marshy->Common mutation");
			return;
		}
		if (common.getChance() <= 0f) {
			helper.fail("Forest+Marshy->Common has non-positive base chance");
			return;
		}

		// (a) Actually run breeding until the single combination fires; the result alleles must be the Common mutation's.
		IGenome forestGenome = forest.getDefaultGenome();
		IGenome marshyGenome = marshy.getDefaultGenome();
		ImmutableList<AllelePair<?>> result = null;
		for (int i = 0; i < 1000 && result == null; i++) {
			result = SpeciesUtil.<IBeeSpecies>mutateSpecies(level, pos, null, forestGenome, marshyGenome, BeeChromosomes.SPECIES, Mutation::getChance);
		}
		if (result == null) {
			helper.fail("Breeding Forest+Marshy never produced a mutation in 1000 attempts");
			return;
		}
		if (!result.equals(common.getResultAlleles())) {
			helper.fail("Breeding produced unexpected result alleles for Forest+Marshy");
			return;
		}

		// (b) Climate gating on the real, temperature+humidity-gated Modest+Frugal->Austere mutation.
		IBeeSpecies modest = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.MODEST);
		IBeeSpecies frugal = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.FRUGAL);
		IMutation<IBeeSpecies> austere = findMutation(beeMutations.getCombinations(modest, frugal), ForestryBeeSpecies.AUSTERE);
		if (austere == null) {
			helper.fail("Missing Modest+Frugal->Austere mutation");
			return;
		}
		if (austere.getConditions().isEmpty()) {
			helper.fail("Austere mutation unexpectedly has no conditions; climate gating cannot be tested");
			return;
		}
		IGenome modestGenome = modest.getDefaultGenome();
		IGenome frugalGenome = frugal.getDefaultGenome();

		// Austere requires HOT..HELLISH temperature and ARID humidity. A NORMAL climate must zero the chance.
		float blocked = Mutation.getChance(austere, level, pos, modestGenome, frugalGenome, climate(TemperatureType.NORMAL, HumidityType.NORMAL));
		if (blocked != 0f) {
			helper.fail("Austere mutation chance under a mismatched climate was " + blocked + ", expected 0");
			return;
		}
		// A matching HOT+ARID climate must yield the base chance.
		float allowed = Mutation.getChance(austere, level, pos, modestGenome, frugalGenome, climate(TemperatureType.HOT, HumidityType.ARID));
		if (Math.abs(allowed - austere.getChance()) > 1.0e-6f) {
			helper.fail("Austere mutation chance under a matching climate was " + allowed + ", expected " + austere.getChance());
			return;
		}

		// (c) A standalone temperature condition must gate modifyChance directly.
		MutationConditionTemperature hot = new MutationConditionTemperature(TemperatureType.HOT, TemperatureType.HELLISH);
		float base = 0.5f;
		if (hot.modifyChance(level, pos, austere, modestGenome, frugalGenome, climate(TemperatureType.NORMAL, HumidityType.NORMAL), base) != 0f) {
			helper.fail("Temperature condition did not zero the chance under a cold climate");
			return;
		}
		if (hot.modifyChance(level, pos, austere, modestGenome, frugalGenome, climate(TemperatureType.HOT, HumidityType.NORMAL), base) != base) {
			helper.fail("Temperature condition did not pass through the chance under a hot climate");
			return;
		}

		helper.succeed();
	}

	/** {@code getMutations()} must be non-null for all three species types and never throw. */
	@GameTest(template = "empty")
	public static void getMutationsNeverThrows(GameTestHelper helper) {
		if (SpeciesUtil.BEE_TYPE.get().getMutations() == null
				|| SpeciesUtil.TREE_TYPE.get().getMutations() == null
				|| SpeciesUtil.BUTTERFLY_TYPE.get().getMutations() == null) {
			helper.fail("getMutations() returned null for a species type");
			return;
		}
		// getAllMutations() must also be callable without throwing.
		SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations();
		SpeciesUtil.TREE_TYPE.get().getMutations().getAllMutations();
		SpeciesUtil.BUTTERFLY_TYPE.get().getMutations().getAllMutations();
		helper.succeed();
	}

	@Nullable
	private static <S extends forestry.api.genetics.ISpecies<?>> IMutation<S> findMutation(List<IMutation<S>> mutations, ResourceLocation resultId) {
		for (IMutation<S> mutation : mutations) {
			if (mutation.getResult().id().equals(resultId)) {
				return mutation;
			}
		}
		return null;
	}

	private static boolean recipesEqual(MutationRecipe a, MutationRecipe b) {
		return a.getId().equals(b.getId())
				&& a.getFirstParentId().equals(b.getFirstParentId())
				&& a.getSecondParentId().equals(b.getSecondParentId())
				&& a.getResultId().equals(b.getResultId())
				&& Math.abs(a.getChance() - b.getChance()) <= 1.0e-6f
				&& a.getConditions().size() == b.getConditions().size();
	}

	private static IClimateProvider climate(TemperatureType temperature, HumidityType humidity) {
		return new IClimateProvider() {
			@Override
			public TemperatureType temperature() {
				return temperature;
			}

			@Override
			public HumidityType humidity() {
				return humidity;
			}
		};
	}
}
