package forestry.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITree;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.features.ArboricultureBlocks;
import forestry.arboriculture.genetics.TreeGrowthHelper;
import forestry.arboriculture.tiles.TileSapling;
import forestry.core.utils.SpeciesUtil;

/**
 * Regression test for Forestry saplings never growing (on bonemeal or random tick) after the allele-foundation
 * migration.
 * <p>
 * {@code TreeGrowthHelper.isSapling} compared the planted sapling's species against the growing genome's species with
 * {@code getActiveAllele(SPECIES) == getActiveAllele(SPECIES)}. Alleles are now non-interned records (allele-foundation),
 * so a planted sapling's genome - decoded from NBT into a fresh instance - never {@code ==}-matched the species' default
 * genome. {@code getGrowthPos} therefore always returned {@code null} and growth silently no-opped (bonemeal wasn't even
 * consumed).
 * <p>
 * This plants a real {@link TileSapling} whose genome is round-tripped through serialize/deserialize (exactly how a
 * placed sapling exists) and asserts {@link TreeGrowthHelper#getGrowthPos} finds a growth position. It first asserts the
 * two genomes hold DISTINCT species-allele instances but EQUAL species values, so it genuinely exercises the reference-
 * equality bug: a naive test reusing {@code getDefaultGenome()} on both sides would {@code ==}-match and pass even with
 * the bug present. Keep the serialize round-trip and the distinct-instance guard - they are what make this a real
 * regression test.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SaplingGrowthTest {
	@GameTest(template = "empty")
	public static void saplingWithFreshGenomeIsRecognizedForGrowth(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ITreeSpecies species = type.getAllSpecies().iterator().next();

		// Build the planted individual the way a real sapling exists: round-trip through NBT so its genome is a FRESH
		// instance, distinct from species.getDefaultGenome().
		ITree defaultTree = species.createIndividual();
		Tag nbt = SpeciesUtil.serializeIndividual(defaultTree);
		ITree planted = SpeciesUtil.deserializeIndividual(type, nbt);

		// Guard: this test only covers the regression if the two genomes are distinct instances but equal by species
		// value. If a future change ever interns genomes/alleles, this fails loudly instead of silently ceasing to
		// cover the bug.
		helper.assertTrue(planted.getGenome().getActiveAllele(TreeChromosomes.SPECIES)
				!= species.getDefaultGenome().getActiveAllele(TreeChromosomes.SPECIES),
			"expected distinct species-allele instances to exercise the reference-equality bug");
		helper.assertTrue(planted.getGenome().getActiveValue(TreeChromosomes.SPECIES)
				.equals(species.getDefaultGenome().getActiveValue(TreeChromosomes.SPECIES)),
			"expected equal species value across the two genomes");

		BlockPos rel = new BlockPos(2, 1, 2);
		helper.setBlock(rel, ArboricultureBlocks.SAPLING_GE.block());
		if (!(helper.getBlockEntity(rel) instanceof TileSapling tile)) {
			helper.fail("Expected a TileSapling at " + rel);
			return;
		}
		tile.setTree(planted);

		BlockPos abs = helper.absolutePos(rel);
		// girth 1, height 1: the empty template has air above so the room check passes; the only thing that can make
		// getGrowthPos null here is the sapling-species recognition (the bug under test).
		BlockPos growthPos = TreeGrowthHelper.getGrowthPos(helper.getLevel(), species.getDefaultGenome(), abs, 1, 1);

		helper.assertTrue(growthPos != null,
			"TreeGrowthHelper.getGrowthPos returned null: a planted sapling of species " + species.id()
				+ " was not recognized because species were compared by reference instead of value");
		helper.succeed();
	}
}
