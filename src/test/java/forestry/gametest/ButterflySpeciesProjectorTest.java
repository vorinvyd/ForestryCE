package forestry.gametest;

import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.ButterflySpecies;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesProjector;

/**
 * Behavioral oracle for {@link ButterflySpeciesProjector}: proves a {@link ButterflySpeciesDefinition} round-tripped
 * from the code-registered Monarch butterfly projects into a runtime {@link ButterflySpecies} whose fields and
 * default genome match the live built-in, including the sparse genome override dispatch (data chromosome, SIZE).
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpeciesProjectorTest {
	@GameTest(template = "empty")
	public static void projectsMatchingButterflySpecies(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		// Read the genus/species/etc. off the real Monarch so the definition is faithful.
		IButterflySpecies monarch = type.getSpecies(ForestryButterflySpecies.MONARCH);

		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();

		// Project against the real Monarch id.
		ButterflySpecies projected = ButterflySpeciesProjector.project(type, ForestryButterflySpecies.MONARCH, def);
		if (projected == null) {
			helper.fail("Projection returned null for a valid definition");
			return;
		}

		if (!projected.getGenusName().equals(monarch.getGenusName())) {
			helper.fail("Expected genus " + monarch.getGenusName() + " but got " + projected.getGenusName());
			return;
		}
		if (!projected.getSpeciesName().equals(monarch.getSpeciesName())) {
			helper.fail("Expected species " + monarch.getSpeciesName() + " but got " + projected.getSpeciesName());
			return;
		}
		if (projected.getTemperature() != monarch.getTemperature()) {
			helper.fail("Expected temperature " + monarch.getTemperature() + " but got " + projected.getTemperature());
			return;
		}
		if (projected.getHumidity() != monarch.getHumidity()) {
			helper.fail("Expected humidity " + monarch.getHumidity() + " but got " + projected.getHumidity());
			return;
		}
		if (projected.isNocturnal() != monarch.isNocturnal()) {
			helper.fail("Expected nocturnal " + monarch.isNocturnal() + " but got " + projected.isNocturnal());
			return;
		}
		if (projected.isMoth() != monarch.isMoth()) {
			helper.fail("Expected moth " + monarch.isMoth() + " but got " + projected.isMoth());
			return;
		}
		if (projected.getRarity() != monarch.getRarity()) {
			helper.fail("Expected rarity " + monarch.getRarity() + " but got " + projected.getRarity());
			return;
		}
		if (projected.getFlightDistance() != monarch.getFlightDistance()) {
			helper.fail("Expected flight distance " + monarch.getFlightDistance() + " but got " + projected.getFlightDistance());
			return;
		}
		if (projected.getSerumColor() != monarch.getSerumColor()) {
			helper.fail("Expected serum color " + monarch.getSerumColor() + " but got " + projected.getSerumColor());
			return;
		}

		IGenome genome = projected.getDefaultGenome();
		float size = genome.getActiveValue(ButterflyChromosomes.SIZE);
		if (size != ForestryAlleles.SIZE_AVERAGE.value()) {
			helper.fail("Expected default genome SIZE active value " + ForestryAlleles.SIZE_AVERAGE.value() + " but got " + size);
			return;
		}

		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void unknownChromosomeOverrideIsSkippedNotFatal(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		// Reuse a real, taxon-registered genus (Monarch's) so the only abnormality under test is the bogus
		// chromosome id in the genome override map.
		IButterflySpecies monarch = type.getSpecies(ForestryButterflySpecies.MONARCH);
		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterfly(monarch.getGenusName(), "phantom")
			.rarity(0.1f)
			.genome(Map.of(ForestryConstants.forestry("no_such_chromosome"), ForestryAlleles.SIZE_AVERAGE))
			.build();
		ButterflySpecies projected = ButterflySpeciesProjector.project(type, ForestryConstants.forestry("phantom_butterfly_bad_override"), def);
		if (projected == null) {
			helper.fail("Expected projection to succeed (with the unknown override logged and skipped), not fail entirely");
			return;
		}
		helper.succeed();
	}
}
