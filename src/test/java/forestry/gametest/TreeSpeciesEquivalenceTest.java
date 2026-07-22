package forestry.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.arboriculture.TreeSpecies;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesProjector;
import forestry.core.utils.SpeciesUtil;

/**
 * The pivotal "no behavior change" proof for Stage 4's tree species datagen: for every built-in tree species,
 * decodes the {@code TreeSpeciesProvider}-generated JSON from the test classpath, projects it via
 * {@link TreeSpeciesProjector}, and asserts the projected {@link TreeSpecies} is equivalent to the live code-built
 * species from {@link ITreeSpeciesType#getAllSpecies()} - the runtime still uses the code-built path, so this proves
 * the data-driven definition is a faithful parallel artifact, not yet that it's wired up.
 * <p>
 * Mirrors {@code BeeSpeciesEquivalenceTest}. Trees need no jubilance-style instance -&gt; id inversion, so the
 * comparison surface is simpler: genetics fields, resolved bindings (the generator instance), and the default genome.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesEquivalenceTest {
	@GameTest(template = "empty")
	public static void generatedJsonMatchesCodeBuilt(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		// Canary against a silent species drop: 50 is the current number of DefaultTreeSpecies registrations, which
		// is exactly the number of tree_species/*.json files runData generates (one per builder).
		int expectedBuiltinCount = 50;
		if (type.getAllSpecies().size() < expectedBuiltinCount) {
			helper.fail("Expected at least " + expectedBuiltinCount + " built-in tree species, but only found " + type.getAllSpecies().size() + " (a species was silently dropped?)");
			return;
		}

		for (ITreeSpecies expected : type.getAllSpecies()) {
			ResourceLocation id = expected.id();

			JsonElement json = readGeneratedJson(id);
			if (json == null) {
				helper.fail("Missing generated tree species JSON for " + id + " (run runData?)");
				return;
			}

			TreeSpeciesDefinition def;
			try {
				def = TreeSpeciesDefinition.codec().parse(ops, json).getOrThrow();
			} catch (Exception e) {
				helper.fail("Failed to decode generated JSON for " + id + ": " + e);
				return;
			}

			TreeSpecies projected = TreeSpeciesProjector.project(type, id, def);
			if (projected == null) {
				helper.fail("Projection failed for " + id + " (see log for the projector's failure cause)");
				return;
			}

			String mismatch = compare(expected, projected, def);
			if (mismatch != null) {
				helper.fail("Mismatch for " + id + ": " + mismatch);
				return;
			}
		}

		helper.succeed();
	}

	@Nullable
	private static String compare(ITreeSpecies a, ITreeSpecies b, TreeSpeciesDefinition def) {
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
		// surrounding equivalence assertions. Two facts force this (mirrors BeeSpeciesEquivalenceTest):
		//   (a) Species#getComplexity() only returns the stored/authored value directly when it is non-zero
		//       (all built-ins author 0 except Giant Sequoia, which authors 10); when it is 0, the getter lazily
		//       *derives* a research value by walking the mutation tree (GeneticsUtil#getResearchComplexity).
		//   (b) That derivation reads the *live* MutationManager, which indexes species by object identity
		//       (IdentityHashMap). The freshly-projected TreeSpecies here was never inserted into that index, so
		//       projected.getComplexity() would derive a shallower/wrong mutation-chain depth than
		//       codeBuilt.getComplexity() - an identity-based divergence that has nothing to do with whether
		//       projection is faithful, and cannot be reconciled at this layer.
		// The achievable, honest check: when a species authors a non-zero complexity (skipping derivation
		// entirely), both code-built and projected must return exactly that authored value.
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
		if (Float.compare(a.getRarity(), b.getRarity()) != 0) {
			return "rarity " + a.getRarity() + " != " + b.getRarity();
		}
		if (a.getGenerator() != b.getGenerator()) {
			// Same code-side binding instance from TreeBlockBindings - the projected species reuses the
			// code-registered generator, it does not fabricate a new one.
			return "generator instance mismatch";
		}
		return genomeMismatch(a.getDefaultGenome(), b.getDefaultGenome());
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
		String resourcePath = "/data/" + id.getNamespace() + "/tree_species/" + id.getPath() + ".json";
		try (InputStream in = TreeSpeciesEquivalenceTest.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
