package forestry.gametest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;

/**
 * Golden-master regression oracle for the allele-foundation refactor.
 *
 * <p>Dumps every built-in species' default genome into a canonical, representation-agnostic form so the inline-value
 * allele rewrite can be proven to preserve genetics. The canonical strings are identical under the old (interned
 * {@link IAllele}) and new ({@code Allele<V>}) models, so a mismatch means a real regression, not a representation
 * change. All lines are sorted, so species/chromosome iteration order is irrelevant.
 *
 * <p>Run modes (system property {@code forestry.genomeBaseline}):
 * <ul>
 *     <li>{@code generate} — writes the live dump to {@code genome-baseline.txt} in the run directory, to be copied
 *     into {@code src/test/resources/forestry/gametest/genome-baseline.txt}.</li>
 *     <li>default (assert) — compares the live dump against the committed resource and fails with a diff on mismatch.</li>
 * </ul>
 *
 * <p>The NEW-API dumper (post-refactor) MUST reproduce {@link #canonical} byte-for-byte: float -&gt; Float.toString,
 * int -&gt; Integer.toString, boolean -&gt; Boolean.toString, enum -&gt; name(), Vec3i -&gt; "x,y,z", reference value
 * -&gt; its ResourceLocation; each rendered as {@code <value>:<dominant>}.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class GenomeBaselineTest {
	private static final String BASELINE_RESOURCE = "/forestry/gametest/genome-baseline.txt";
	private static final String OUTPUT_FILE = "genome-baseline.txt";

	@GameTest(template = "empty")
	public static void defaultGenomesMatchBaseline(GameTestHelper helper) {
		String actual = dumpAllDefaultGenomes();

		if ("generate".equalsIgnoreCase(System.getProperty("forestry.genomeBaseline"))) {
			try {
				Files.writeString(Path.of(OUTPUT_FILE), actual, StandardCharsets.UTF_8);
				System.out.println("[GenomeBaselineTest] Wrote baseline to " + new File(OUTPUT_FILE).getAbsolutePath());
			} catch (IOException e) {
				throw new RuntimeException("Failed to write genome baseline", e);
			}
			helper.succeed();
			return;
		}

		String expected = readBaselineResource();
		if (expected == null) {
			helper.fail("Baseline resource " + BASELINE_RESOURCE + " not found on the classpath. " +
					"Run with -Dforestry.genomeBaseline=generate and copy genome-baseline.txt into src/test/resources" + BASELINE_RESOURCE);
			return;
		}

		String expectedNorm = expected.strip();
		String actualNorm = actual.strip();
		if (!expectedNorm.equals(actualNorm)) {
			writeActualForDebugging(actualNorm);
			helper.fail("Default genomes changed vs baseline. First diff:\n" + firstDiff(expectedNorm, actualNorm));
			return;
		}

		helper.succeed();
	}

	private static String dumpAllDefaultGenomes() {
		List<String> lines = new ArrayList<>();

		List<ISpeciesType<?, ?>> types = new ArrayList<>(IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes());

		for (ISpeciesType<?, ?> type : types) {
			ResourceLocation typeId = type.id();

			for (ResourceLocation speciesId : type.getAllSpeciesIds()) {
				ISpecies<?> species = type.getSpecies(speciesId);
				IGenome genome = species.getDefaultGenome();

				for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
					IChromosome<?> chromosome = entry.getKey();
					AllelePair<?> pair = entry.getValue();

					lines.add(typeId + " " + speciesId + " " + chromosome.id() + " = "
							+ canonical(pair.active()) + " | " + canonical(pair.inactive()));
				}
			}
		}

		lines.sort(String::compareTo);
		return String.join("\n", lines);
	}

	/**
	 * Canonical, representation-agnostic rendering of an allele as {@code <value>:<dominant>}.
	 * Reproduces the pre-refactor rendering exactly: reference values render as their {@link ResourceLocation};
	 * float/int/boolean via their {@code toString}; enums via {@code name()}; {@link Vec3i} as {@code "x,y,z"}.
	 */
	private static String canonical(Allele<?> allele) {
		return renderValue(allele.value()) + ":" + allele.dominant();
	}

	private static String renderValue(Object value) {
		if (value instanceof Enum<?> e) {
			return e.name();
		}
		if (value instanceof Vec3i vec) {
			return vec.getX() + "," + vec.getY() + "," + vec.getZ();
		}
		return String.valueOf(value);
	}

	private static String readBaselineResource() {
		try (InputStream in = GenomeBaselineTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
			if (in == null) {
				return null;
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read genome baseline resource", e);
		}
	}

	private static void writeActualForDebugging(String actual) {
		try {
			Files.writeString(Path.of("genome-baseline-actual.txt"), actual, StandardCharsets.UTF_8);
			System.out.println("[GenomeBaselineTest] Wrote actual dump to " + new File("genome-baseline-actual.txt").getAbsolutePath());
		} catch (IOException ignored) {
		}
	}

	private static String firstDiff(String expected, String actual) {
		String[] e = expected.split("\n");
		String[] a = actual.split("\n");
		int n = Math.min(e.length, a.length);
		for (int i = 0; i < n; i++) {
			if (!e[i].equals(a[i])) {
				return "line " + (i + 1) + "\n  expected: " + e[i] + "\n  actual:   " + a[i];
			}
		}
		if (e.length != a.length) {
			return "line count differs: expected " + e.length + ", actual " + a.length;
		}
		return "(no line-level diff found)";
	}
}
