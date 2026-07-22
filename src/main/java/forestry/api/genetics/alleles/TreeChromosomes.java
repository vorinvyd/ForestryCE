package forestry.api.genetics.alleles;

import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.IFruit;
import forestry.api.arboriculture.genetics.ITreeEffect;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.core.genetics.alleles.ChromosomeFactory;
import forestry.core.utils.SpeciesUtil;

import static forestry.api.ForestryConstants.forestry;

public class TreeChromosomes {
	/**
	 * The species of a tree. The genome stores the species' ID.
	 */
	public static final IChromosome<ResourceLocation> SPECIES = ChromosomeFactory.referenceChromosome(ForestrySpeciesTypes.TREE, TreeChromosomes::resolveSpeciesOrDefault, ITreeSpecies::isDominant);

	/**
	 * Resolves a tree species id stored in a genome to its species, falling back to the default species (instead of
	 * throwing) if a datapack has since removed it. Backs every SPECIES chromosome read (tooltips, analyzer, growth,
	 * saved items), so a removed id must never crash those paths.
	 */
	private static ITreeSpecies resolveSpeciesOrDefault(ResourceLocation id) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ITreeSpecies species = type.getSpeciesSafe(id);
		if (species != null) {
			return species;
		}
		Forestry.LOGGER.warn("Tree species {} not found (removed by a datapack?); falling back to the default species", id);
		return type.getDefaultSpecies();
	}

	/**
	 * Modifies the height of a tree.
	 */
	public static final IChromosome<Float> HEIGHT = ChromosomeFactory.floatChromosome(forestry("height"));
	/**
	 * Chance for saplings.
	 */
	public static final IChromosome<Float> SAPLINGS = ChromosomeFactory.floatChromosome(forestry("saplings"));
	/**
	 * Determines what fruits are grown on the tree.
	 */
	public static final IChromosome<ResourceLocation> FRUIT = ChromosomeFactory.referenceChromosome(forestry("fruits"), id -> SpeciesUtil.TREE_TYPE.get().getFruit(id), IFruit::isDominant);
	/**
	 * Chance for fruit leaves and/or drops.
	 */
	public static final IChromosome<Float> YIELD = ChromosomeFactory.floatChromosome(forestry("yield"));
	/**
	 * Determines the speed at which fruit will ripen on this tree.
	 */
	public static final IChromosome<Float> SAPPINESS = ChromosomeFactory.floatChromosome(forestry("sappiness"));
	/**
	 * Unimplemented. All trees added by base Forestry have the "none" tree effect.
	 */
	public static final IChromosome<ResourceLocation> EFFECT = ChromosomeFactory.referenceChromosome(forestry("tree_effect"), id -> SpeciesUtil.TREE_TYPE.get().getTreeEffect(id), ITreeEffect::isDominant);
	/**
	 * Amount of random ticks which need to elapse before a sapling will grow into a tree.
	 */
	public static final IChromosome<Integer> MATURATION = ChromosomeFactory.intChromosome(forestry("maturation"));
	/**
	 * The diameter of the tree. If the allele is 2, then the tree trunk is a 2x2 and requires four saplings to grow.
	 */
	public static final IChromosome<Integer> GIRTH = ChromosomeFactory.intChromosome(forestry("girth"));
	/**
	 * Determines if the tree can burn.
	 */
	public static final IChromosome<Boolean> FIREPROOF = ButterflyChromosomes.FIREPROOF;
}
