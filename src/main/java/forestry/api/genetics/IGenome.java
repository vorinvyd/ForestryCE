package forestry.api.genetics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the {@link AllelePair}s which comprise the traits of a given individual. Allele values are stored inline;
 * reference chromosomes (species, flower type, effect, ...) store a {@link ResourceLocation} resolved on demand.
 */
public interface IGenome {
	Codec<IGenome> CODEC = IKaryotype.CODEC.dispatch("karyotype", IGenome::getKaryotype, karyotype -> karyotype.getGenomeCodec().fieldOf("genome"));

	/**
	 * @return A list with all allele pairs of this genome, in karyotype order.
	 */
	ImmutableList<AllelePair<?>> getAllelePairs();

	/**
	 * @return The allele pair for the given chromosome.
	 */
	<V> AllelePair<V> getAllelePair(IChromosome<V> chromosome);

	/**
	 * @return {@code true} if this genome equals the default genome of its species.
	 */
	boolean isDefaultGenome();

	/**
	 * @return The karyotype of this genome.
	 */
	IKaryotype getKaryotype();

	ImmutableMap<IChromosome<?>, AllelePair<?>> getChromosomes();

	/**
	 * Copies this genome, setting both alleles of each given chromosome to the given value. For reference chromosomes
	 * the value's dominance is intrinsic, so it is resolved from the chromosome's resolver here (ignoring the placeholder
	 * dominance of an {@link Allele#reference} value).
	 */
	default IGenome copyWith(Map<IChromosome<?>, Allele<?>> alleles) {
		IdentityHashMap<IChromosome<?>, AllelePair<?>> pairMap = new IdentityHashMap<>(alleles.size());
		for (Map.Entry<IChromosome<?>, Allele<?>> entry : alleles.entrySet()) {
			IChromosome<?> chromosome = entry.getKey();
			Allele<?> allele = entry.getValue();
			IChromosome.IReferenceResolver<?> resolver = chromosome.resolver();
			if (resolver != null) {
				ResourceLocation id = (ResourceLocation) allele.value();
				allele = new Allele<>(id, resolver.isDominant(id));
			}
			pairMap.put(chromosome, AllelePair.both(allele));
		}
		return copyWithPairs(pairMap);
	}

	/**
	 * Copies this genome, replacing allele pairs from the given map. Returns this genome if nothing changed.
	 */
	IGenome copyWithPairs(Map<IChromosome<?>, AllelePair<?>> allelePairs);

	/**
	 * @return {@code true} if this genome has the same karyotype and alleles as the other genome.
	 */
	boolean isSameAlleles(IGenome other);

	/**
	 * @return The active (expressed) allele of the given chromosome.
	 */
	default <V> Allele<V> getActiveAllele(IChromosome<V> chromosome) {
		return getAllelePair(chromosome).active();
	}

	/**
	 * @return The inactive allele of the given chromosome.
	 */
	default <V> Allele<V> getInactiveAllele(IChromosome<V> chromosome) {
		return getAllelePair(chromosome).inactive();
	}

	/**
	 * @return The active value of the given chromosome. For reference chromosomes this is the stored
	 * {@link ResourceLocation}; use {@link #resolveActive} for the behavior object.
	 */
	default <V> V getActiveValue(IChromosome<V> chromosome) {
		return getActiveAllele(chromosome).value();
	}

	/**
	 * @return The inactive value of the given chromosome.
	 */
	default <V> V getInactiveValue(IChromosome<V> chromosome) {
		return getInactiveAllele(chromosome).value();
	}

	/**
	 * Resolves the active reference value to its behavior object via the chromosome's resolver.
	 *
	 * @throws NullPointerException If the chromosome is a data chromosome (has no resolver).
	 */
	@SuppressWarnings("unchecked")
	default <R> R resolveActive(IChromosome<ResourceLocation> chromosome) {
		IChromosome.IReferenceResolver<R> resolver = (IChromosome.IReferenceResolver<R>) Objects.requireNonNull(chromosome.resolver(), () -> "Not a reference chromosome: " + chromosome.id());
		return resolver.get(getActiveValue(chromosome));
	}

	/**
	 * Resolves the inactive reference value to its behavior object via the chromosome's resolver.
	 *
	 * @throws NullPointerException If the chromosome is a data chromosome (has no resolver).
	 */
	@SuppressWarnings("unchecked")
	default <R> R resolveInactive(IChromosome<ResourceLocation> chromosome) {
		IChromosome.IReferenceResolver<R> resolver = (IChromosome.IReferenceResolver<R>) Objects.requireNonNull(chromosome.resolver(), () -> "Not a reference chromosome: " + chromosome.id());
		return resolver.get(getInactiveValue(chromosome));
	}

	/**
	 * Note: Use {@link IIndividual#getSpecies} whenever possible.
	 *
	 * @return The active species of the individual.
	 */
	default <S extends ISpecies<?>> S getActiveSpecies() {
		return resolveActive(getKaryotype().getSpeciesChromosome());
	}

	/**
	 * Note: Use {@link IIndividual#getInactiveSpecies} whenever possible.
	 *
	 * @return The inactive species of the individual.
	 */
	default <S extends ISpecies<?>> S getInactiveSpecies() {
		return resolveInactive(getKaryotype().getSpeciesChromosome());
	}
}
