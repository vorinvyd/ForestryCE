package forestry.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.IForestryApi;
import forestry.api.ForestryConstants;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;

/**
 * Guard for the allele display-name regression: after the allele-foundation refactor, {@code ChromosomeFactory}
 * generates translation keys like {@code allele.forestry.sappiness.0_2} (sanitized value, no {@code f}/{@code fd}/
 * {@code i}/{@code id} suffix), but the lang file still held the old {@code allele.forestry.sappiness.0.2fd} keys.
 * {@code translatableWithFallback} then fell through to the raw value ("0.2") in the Portable Analyzer and tooltips.
 * <p>
 * This walks every registered species' default genome and, for the active AND inactive allele of every chromosome,
 * computes {@link IChromosome#translationKey} - exactly the key the UI resolves - and asserts the shipped lang file
 * (the merged {@code en_us.json} on the classpath) actually contains it. Any missing key is a value that would render
 * raw. This is the authoritative check: it covers the whole value space that natural species contribute (mutations
 * only recombine those), across every species type, not just the numeric traits noticed by hand.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class AlleleTranslationKeyTest {
	@GameTest(template = "empty")
	public static void everyAlleleValueHasATranslation(GameTestHelper helper) {
		Set<String> langKeys = loadLangKeys(helper);

		// Sorted + deduped so the failure message is stable and readable.
		Set<String> missing = new TreeSet<>();
		for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
			for (ISpecies<?> species : type.getAllSpecies()) {
				IGenome genome = species.getDefaultGenome();
				for (var entry : genome.getChromosomes().entrySet()) {
					IChromosome<?> chromosome = entry.getKey();
					AllelePair<?> pair = entry.getValue();
					checkKey(chromosome, pair.active(), langKeys, missing);
					checkKey(chromosome, pair.inactive(), langKeys, missing);
				}
			}
		}

		if (!missing.isEmpty()) {
			helper.fail(missing.size() + " allele value(s) have no translation key in en_us.json:\n  "
				+ String.join("\n  ", missing));
			return;
		}
		helper.succeed();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void checkKey(IChromosome<?> chromosome, Allele<?> allele, Set<String> langKeys, Set<String> missing) {
		String key = ((IChromosome) chromosome).translationKey(allele.value());
		if (!langKeys.contains(key)) {
			missing.add(key + "   (value=" + allele.value() + ")");
		}
	}

	private static Set<String> loadLangKeys(GameTestHelper helper) {
		try (InputStream in = AlleleTranslationKeyTest.class.getResourceAsStream("/assets/forestry/lang/en_us.json")) {
			if (in == null) {
				helper.fail("Could not find assets/forestry/lang/en_us.json on the classpath");
				return Set.of();
			}
			JsonObject json = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			return json.keySet();
		} catch (Exception e) {
			helper.fail("Failed to read en_us.json: " + e);
			return Set.of();
		}
	}
}
