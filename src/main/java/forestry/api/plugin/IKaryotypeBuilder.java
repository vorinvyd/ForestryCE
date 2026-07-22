package forestry.api.plugin;

import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import net.minecraft.resources.ResourceLocation;

/**
 * Configures the chromosome set (karyotype) of a species type and the default allele for each chromosome.
 * Validity is permissive: any value of a chromosome's type may appear in a genome, so there is no allele whitelist.
 */
public interface IKaryotypeBuilder {
	/**
	 * Sets the species chromosome of the karyotype.
	 *
	 * @param species   The species (reference) chromosome.
	 * @param defaultId The ID of the default species, used as a fallback when a genome is unavailable or corrupt.
	 */
	void setSpecies(IChromosome<ResourceLocation> species, ResourceLocation defaultId);

	/**
	 * Adds a data chromosome with the given default allele.
	 */
	<V> IChromosomeBuilder<V> set(IChromosome<V> chromosome, Allele<V> defaultAllele);

	/**
	 * Shortcut for a dominant boolean default.
	 */
	default IChromosomeBuilder<Boolean> set(IChromosome<Boolean> chromosome, boolean value) {
		return set(chromosome, Allele.of(value, true));
	}

	/**
	 * Adds a reference chromosome (species, flower type, effect, ...) with the given default value ID. The value's
	 * declared dominance is resolved lazily, after registries are populated.
	 */
	IChromosomeBuilder<ResourceLocation> set(IChromosome<ResourceLocation> chromosome, ResourceLocation defaultId);

	/**
	 * Returns the chromosome builder for an already-added chromosome (e.g. to set weak inheritance).
	 *
	 * @throws IllegalArgumentException If the chromosome has not been added.
	 */
	<V> IChromosomeBuilder<V> get(IChromosome<V> chromosome);
}
