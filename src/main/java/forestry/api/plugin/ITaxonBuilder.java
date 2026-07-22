package forestry.api.plugin;

import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Builder for a taxon that allows defining subtaxa and adding species to a taxon.
 */
public interface ITaxonBuilder {
	/**
	 * Defines a taxon with this taxon as the its parent.
	 * Do not use with species names, species are set by the {@link ISpeciesBuilder}.
	 *
	 * @param name The name of the taxon. Must be unique.
	 * @throws UnsupportedOperationException If this taxon is a genus. Species names are set by the species builder.
	 */
	void defineSubTaxon(String name);

	/**
	 * Defines and configures a taxon with this taxon as its parent.
	 *
	 * @param name   The name of the taxon. Must be unique.
	 * @param action A consumer that adds additional information to the taxon after it is created.
	 */
	void defineSubTaxon(String name, Consumer<ITaxonBuilder> action);

	/**
	 * Sets the default allele of a data chromosome for all members of this taxon.
	 *
	 * @param chromosome The chromosome to set.
	 * @param allele     The default allele of the chromosome.
	 */
	default <V> void setDefaultChromosome(IChromosome<V> chromosome, Allele<V> allele) {
		setDefaultChromosome(chromosome, allele, true);
	}

	/**
	 * Sets the default allele of a data chromosome for all members of this taxon.
	 *
	 * @param chromosome The chromosome to set.
	 * @param allele     The default allele of this chromosome.
	 * @param required   If {@code true}, members of the taxon are expected to have this chromosome.
	 */
	<V> void setDefaultChromosome(IChromosome<V> chromosome, Allele<V> allele, boolean required);

	/**
	 * Sets the default value of a reference chromosome (species, flower type, effect, ...) for all members of this
	 * taxon by the referenced value's ID. The value's declared dominance is resolved after registries are populated.
	 *
	 * @param chromosome The reference chromosome to set.
	 * @param id         The ID of the default referenced value.
	 */
	default void setDefaultChromosome(IChromosome<ResourceLocation> chromosome, ResourceLocation id) {
		setDefaultChromosome(chromosome, id, true);
	}

	/**
	 * Sets the default value of a reference chromosome for all members of this taxon by the referenced value's ID.
	 *
	 * @param chromosome The reference chromosome to set.
	 * @param id         The ID of the default referenced value.
	 * @param required   If {@code true}, members of the taxon are expected to have this chromosome.
	 */
	void setDefaultChromosome(IChromosome<ResourceLocation> chromosome, ResourceLocation id, boolean required);
}
