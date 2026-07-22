package forestry.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.core.IProduct;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.ButterflySpecies;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesProjector;

/**
 * The pivotal "no behavior change" proof for Stage 5's butterfly datagen: for every built-in butterfly/moth, decodes
 * the {@code ButterflySpeciesProvider}-generated JSON from the test classpath, projects it via
 * {@link ButterflySpeciesProjector}, and asserts the projected {@link ButterflySpecies} is equivalent to the live
 * code-built species from {@link IButterflySpeciesType#getAllSpecies()} - the runtime still uses the code-built path,
 * so this proves the data-driven definition is a faithful parallel artifact, not yet that it's wired up.
 * <p>
 * Mirrors {@code TreeSpeciesEquivalenceTest} (no jubilance-style instance -&gt; id inversion to verify, unlike
 * {@code BeeSpeciesEquivalenceTest}); the default-genome comparison is the heart of the guarantee, since it also
 * covers the cocoon/butterfly_effect reference chromosomes recorded via {@code RecordingGenomeBuilder}.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpeciesEquivalenceTest {
	@GameTest(template = "empty")
	public static void generatedJsonMatchesCodeBuilt(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		List<IButterflySpecies> allSpecies = type.getAllSpecies();
		// Canary against a silent species drop: 35 is the current number of DefaultButterflySpecies registrations
		// (31 butterflies + 4 moths), which is exactly the number of butterfly_species/*.json files runData
		// generates (one per builder).
		int expectedBuiltinCount = 35;
		if (allSpecies.size() < expectedBuiltinCount) {
			helper.fail("Expected at least " + expectedBuiltinCount + " built-in butterfly species, but only found " + allSpecies.size() + " (a species was silently dropped?)");
			return;
		}

		for (IButterflySpecies codeBuilt : allSpecies) {
			ResourceLocation id = codeBuilt.id();

			JsonElement json = readGeneratedJson(id);
			if (json == null) {
				helper.fail("Missing generated butterfly species JSON for " + id + " (run runData?)");
				return;
			}

			ButterflySpeciesDefinition def;
			try {
				def = ButterflySpeciesDefinition.codec().parse(ops, json).getOrThrow();
			} catch (Exception e) {
				helper.fail("Failed to decode generated JSON for " + id + ": " + e);
				return;
			}

			ButterflySpecies projected = ButterflySpeciesProjector.project(type, id, def);
			if (projected == null) {
				helper.fail("Projection failed for " + id + " (see log for the projector's failure cause)");
				return;
			}

			String mismatch = compare(codeBuilt, projected, def);
			if (mismatch != null) {
				helper.fail("Mismatch for " + id + ": " + mismatch);
				return;
			}
		}

		helper.succeed();
	}

	@Nullable
	private static String compare(IButterflySpecies a, IButterflySpecies b, ButterflySpeciesDefinition def) {
		if (!a.getGenus().equals(b.getGenus())) {
			return "genus " + a.getGenus().name() + " != " + b.getGenus().name();
		}
		if (!a.getSpeciesName().equals(b.getSpeciesName())) {
			return "species " + a.getSpeciesName() + " != " + b.getSpeciesName();
		}
		if (a.isDominant() != b.isDominant()) {
			return "dominant " + a.isDominant() + " != " + b.isDominant();
		}
		if (a.hasGlint() != b.hasGlint()) {
			return "glint " + a.hasGlint() + " != " + b.hasGlint();
		}
		if (a.isSecret() != b.isSecret()) {
			return "secret " + a.isSecret() + " != " + b.isSecret();
		}
		// Complexity is NOT compared a.getComplexity() vs b.getComplexity() like the fields above - this is a
		// weaker invariant guard, called out explicitly so a future reader does not mistake its strength for the
		// surrounding equivalence assertions. Two facts force this (mirrors TreeSpeciesEquivalenceTest /
		// BeeSpeciesEquivalenceTest):
		//   (a) Species#getComplexity() only returns the stored/authored value directly when it is non-zero; when it
		//       is 0 (every built-in butterfly authors 0), the getter lazily *derives* a research value by walking
		//       the mutation tree (GeneticsUtil#getResearchComplexity).
		//   (b) That derivation reads the *live* MutationManager, which indexes species by object identity
		//       (IdentityHashMap). The freshly-projected ButterflySpecies here was never inserted into that index, so
		//       projected.getComplexity() would derive a shallower/different mutation-chain depth than
		//       codeBuilt.getComplexity() - an identity-based divergence that has nothing to do with whether
		//       projection is faithful, and cannot be reconciled at this layer.
		// The achievable, honest check: when a species authors a non-zero complexity (skipping derivation entirely),
		// both code-built and projected must return exactly that authored value.
		if (def.complexity() != 0 && (a.getComplexity() != def.complexity() || b.getComplexity() != def.complexity())) {
			return "authored complexity " + def.complexity() + " not honored: code-built=" + a.getComplexity() + " projected=" + b.getComplexity();
		}
		if (!a.getAuthority().equals(b.getAuthority())) {
			return "authority " + a.getAuthority() + " != " + b.getAuthority();
		}
		if (a.getEscritoireColor() != b.getEscritoireColor()) {
			return "escritoireColor " + a.getEscritoireColor() + " != " + b.getEscritoireColor();
		}
		if (a.getTemperature() != b.getTemperature()) {
			return "temperature " + a.getTemperature() + " != " + b.getTemperature();
		}
		if (a.getHumidity() != b.getHumidity()) {
			return "humidity " + a.getHumidity() + " != " + b.getHumidity();
		}
		if (a.isNocturnal() != b.isNocturnal()) {
			return "nocturnal " + a.isNocturnal() + " != " + b.isNocturnal();
		}
		if (a.isMoth() != b.isMoth()) {
			return "moth " + a.isMoth() + " != " + b.isMoth();
		}
		if (Float.compare(a.getRarity(), b.getRarity()) != 0) {
			return "rarity " + a.getRarity() + " != " + b.getRarity();
		}
		if (Float.compare(a.getFlightDistance(), b.getFlightDistance()) != 0) {
			return "flightDistance " + a.getFlightDistance() + " != " + b.getFlightDistance();
		}
		if (a.getSerumColor() != b.getSerumColor()) {
			return "serumColor " + a.getSerumColor() + " != " + b.getSerumColor();
		}
		if (!Objects.equals(a.getSpawnBiomes(), b.getSpawnBiomes())) {
			return "spawnBiomes " + a.getSpawnBiomes() + " != " + b.getSpawnBiomes();
		}

		String productsMismatch = compareProducts("products", a.getButterflyLoot(), b.getButterflyLoot());
		if (productsMismatch != null) {
			return productsMismatch;
		}
		String caterpillarProductsMismatch = compareProducts("caterpillarProducts", a.getCaterpillarProducts(), b.getCaterpillarProducts());
		if (caterpillarProductsMismatch != null) {
			return caterpillarProductsMismatch;
		}

		return genomeMismatch(a.getDefaultGenome(), b.getDefaultGenome());
	}

	@Nullable
	private static String compareProducts(String label, List<IProduct> a, List<IProduct> b) {
		if (a.size() != b.size()) {
			return label + " size " + a.size() + " != " + b.size();
		}
		for (int i = 0; i < a.size(); i++) {
			String mismatch = compareProduct(a.get(i), b.get(i));
			if (mismatch != null) {
				return label + "[" + i + "] " + mismatch;
			}
		}
		return null;
	}

	@Nullable
	private static String compareProduct(IProduct a, IProduct b) {
		if (a.item() != b.item()) {
			return "item " + a.item() + " != " + b.item();
		}
		if (Math.abs(a.chance() - b.chance()) > 1.0e-6f) {
			return "chance " + a.chance() + " != " + b.chance();
		}
		ItemStack stackA = a.createStack();
		ItemStack stackB = b.createStack();
		if (stackA.getCount() != stackB.getCount()) {
			return "count " + stackA.getCount() + " != " + stackB.getCount();
		}
		if (!stackA.getComponentsPatch().equals(stackB.getComponentsPatch())) {
			return "components " + stackA.getComponentsPatch() + " != " + stackB.getComponentsPatch();
		}
		return null;
	}

	@Nullable
	private static String genomeMismatch(IGenome a, IGenome b) {
		Map<IChromosome<?>, AllelePair<?>> ca = a.getChromosomes();
		Map<IChromosome<?>, AllelePair<?>> cb = b.getChromosomes();
		if (!ca.keySet().equals(cb.keySet())) {
			return "default genome chromosome sets differ: " + ca.keySet() + " != " + cb.keySet();
		}
		for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : ca.entrySet()) {
			AllelePair<?> pairA = entry.getValue();
			AllelePair<?> pairB = cb.get(entry.getKey());
			if (!pairA.equals(pairB)) {
				return "default genome chromosome " + entry.getKey().id() + ": " + pairA + " != " + pairB;
			}
		}
		return null;
	}

	@Nullable
	private static JsonElement readGeneratedJson(ResourceLocation id) {
		String resourcePath = "/data/" + id.getNamespace() + "/butterfly_species/" + id.getPath() + ".json";
		try (InputStream in = ButterflySpeciesEquivalenceTest.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
