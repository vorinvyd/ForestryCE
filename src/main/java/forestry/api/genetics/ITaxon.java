package forestry.api.genetics;

import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Biological classifications from domain down to genus.
 * Used by the Forestry analyzers to display hierarchies.
 */
public interface ITaxon {
	/**
	 * @return The lowercase name of the taxon.
	 */
	String name();

	/**
	 * @return The level inside the full hierarchy this particular taxon is located at.
	 */
	TaxonomicRank rank();

	/**
	 * @return The taxa directly below this taxon. Guaranteed to be empty if this taxon is a {@link TaxonomicRank#GENUS}.
	 */
	List<ITaxon> children();

	/**
	 * @return The member species of this group. Empty if this taxon is not a {@link TaxonomicRank#GENUS}.
	 */
	List<ISpecies<?>> species();

	/**
	 * @return The parent taxon, or {@code null} if this is a {@link TaxonomicRank#DOMAIN}.
	 */
	@Nullable
	ITaxon parent();

	/**
	 * A taxon may have alleles added to it so that its species will inherit a common set of default alleles upon registration.
	 * For example, the default genomes of all bee species in the boggy genus will have the Mushroom flower type allele.
	 *
	 * @return A map of default alleles inherited by members of this taxon. Does not include alleles from parent taxa.
	 */
	Map<IChromosome<?>, TaxonAllele> alleles();

	/**
	 * A default allele associated with a taxon. Exactly one of {@code allele} (a data-chromosome value) or
	 * {@code reference} (a reference-chromosome ID, resolved lazily) is non-null.
	 *
	 * @param allele    The inline value allele for a data chromosome, or {@code null} for a reference chromosome.
	 * @param reference The referenced value's ID for a reference chromosome, or {@code null} for a data chromosome.
	 * @param required  Whether members of the taxon are required to have this chromosome.
	 */
	record TaxonAllele(@Nullable Allele<?> allele, @Nullable ResourceLocation reference, boolean required) {
		/**
		 * @return A taxon default for a data chromosome.
		 */
		public static TaxonAllele data(Allele<?> allele, boolean required) {
			return new TaxonAllele(allele, null, required);
		}

		/**
		 * @return A taxon default for a reference chromosome (species, flower type, effect, ...).
		 */
		public static TaxonAllele reference(ResourceLocation id, boolean required) {
			return new TaxonAllele(null, id, required);
		}
	}
}
