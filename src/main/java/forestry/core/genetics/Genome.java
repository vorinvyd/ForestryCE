package forestry.core.genetics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IGenomeBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public final class Genome implements IGenome {
	final ImmutableMap<IChromosome<?>, AllelePair<?>> chromosomes;
	private final IKaryotype karyotype;

	private boolean isDefaultGenome;
	private boolean hasCachedDefaultGenome;

	public Genome(IKaryotype karyotype, ImmutableMap<IChromosome<?>, AllelePair<?>> chromosomes) {
		this.karyotype = karyotype;
		this.chromosomes = chromosomes;
	}

	// Used by codec to sort alleles properly and populate missing chromosomes.
	public static IGenome sanitizeAlleles(Karyotype karyotype, Map<IChromosome<?>, AllelePair<?>> map) {
		ImmutableMap.Builder<IChromosome<?>, AllelePair<?>> sorted = ImmutableMap.builderWithExpectedSize(map.size());
		for (IChromosome<?> chromosome : karyotype.getChromosomes()) {
			// Fix missing chromosomes for backwards compatibility.
			AllelePair<?> pair = map.get(chromosome);
			if (pair == null) {
				ResourceLocation speciesId;
				AllelePair<?> speciesPair = map.get(karyotype.getSpeciesChromosome());
				if (speciesPair == null) {
					// If the stored species was added/removed, revert to the default species.
					speciesId = karyotype.getDefaultSpecies();
				} else {
					speciesId = (ResourceLocation) speciesPair.active().value();
				}

				IChromosome.IReferenceResolver<?> speciesResolver = Objects.requireNonNull(karyotype.getSpeciesChromosome().resolver(), "Species chromosome has no resolver");
				ISpecies<?> species = (ISpecies<?>) speciesResolver.get(speciesId);
				pair = species.getDefaultGenome().getAllelePair(chromosome);
			}

			sorted.put(chromosome, pair);
		}
		return new Genome(karyotype, sorted.buildOrThrow());
	}

	@Override
	public ImmutableList<AllelePair<?>> getAllelePairs() {
		return this.chromosomes.values().asList();
	}

	@Override
	public IKaryotype getKaryotype() {
		return this.karyotype;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <V> AllelePair<V> getAllelePair(IChromosome<V> chromosome) {
		return (AllelePair<V>) this.chromosomes.get(chromosome);
	}

	@Override
	public boolean isDefaultGenome() {
		if (!this.hasCachedDefaultGenome) {
			Genome defaultGenome = (Genome) this.<ISpecies<?>>getActiveSpecies().getDefaultGenome();

			this.isDefaultGenome = this == defaultGenome || isSameAlleles(defaultGenome);
			this.hasCachedDefaultGenome = true;
		}

		return this.isDefaultGenome;
	}

	@Override
	public ImmutableMap<IChromosome<?>, AllelePair<?>> getChromosomes() {
		return this.chromosomes;
	}

	@Override
	public IGenome copyWithPairs(Map<IChromosome<?>, AllelePair<?>> alleles) {
		if (alleles.isEmpty()) {
			return this;
		} else {
			Genome.Builder builder = new Genome.Builder(this.karyotype);
			boolean isDefault = true;

			for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : this.chromosomes.entrySet()) {
				IChromosome<?> chromosome = entry.getKey();
				AllelePair<?> pair = entry.getValue();
				AllelePair<?> override = alleles.get(chromosome);

				if (override == null || override.equals(pair)) {
					builder.setUnchecked(chromosome, pair);
				} else {
					builder.setUnchecked(chromosome, override);
					isDefault = false;
				}
			}

			return isDefault ? this : builder.build();
		}
	}

	@Override
	public boolean isSameAlleles(IGenome other) {
		return other.getKaryotype() == this.karyotype && this.chromosomes.equals(other.getChromosomes());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Genome genome = (Genome) o;
		return this.chromosomes.equals(genome.chromosomes) && this.karyotype.equals(genome.karyotype);
	}

	@Override
	public int hashCode() {
		int result = this.chromosomes.hashCode();
		result = 31 * result + this.karyotype.hashCode();
		return result;
	}

	public static class Builder implements IGenomeBuilder {
		private final IKaryotype karyotype;
		private final IdentityHashMap<IChromosome<?>, Allele<?>> active = new IdentityHashMap<>();
		private final IdentityHashMap<IChromosome<?>, Allele<?>> inactive = new IdentityHashMap<>();

		public Builder(IKaryotype karyotype) {
			this.karyotype = karyotype;
		}

		@Override
		public <V> void set(IChromosome<V> chromosome, Allele<V> allele) {
			this.active.put(chromosome, allele);
			this.inactive.put(chromosome, allele);
		}

		@Override
		public void set(IChromosome<ResourceLocation> chromosome, ResourceLocation id) {
			IChromosome.IReferenceResolver<?> resolver = Objects.requireNonNull(chromosome.resolver(), () -> "Not a reference chromosome: " + chromosome.id());
			Allele<ResourceLocation> allele = new Allele<>(id, resolver.isDominant(id));
			this.active.put(chromosome, allele);
			this.inactive.put(chromosome, allele);
		}

		@Override
		public <V> void setActive(IChromosome<V> chromosome, Allele<V> allele) {
			this.active.put(chromosome, allele);
		}

		@Override
		public <V> void setInactive(IChromosome<V> chromosome, Allele<V> allele) {
			this.inactive.put(chromosome, allele);
		}

		@Override
		public boolean isEmpty() {
			return this.active.isEmpty() && this.inactive.isEmpty();
		}

		@Override
		public void setRemainingDefault() {
			for (IChromosome<?> chromosome : this.karyotype.getChromosomes()) {
				Allele<?> def = null;
				if (!this.active.containsKey(chromosome)) {
					def = this.karyotype.getDefaultAllele(chromosome);
					this.active.put(chromosome, def);
				}
				if (!this.inactive.containsKey(chromosome)) {
					if (def == null) {
						def = this.karyotype.getDefaultAllele(chromosome);
					}
					this.inactive.put(chromosome, def);
				}
			}
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public IGenome build() {
			if (this.karyotype.size() != this.active.size()) {
				StringBuilder msg = new StringBuilder("Tried to build genome, but the following chromosomes are missing from the karyotype: { ");
				for (IChromosome<?> chromosome : this.karyotype.getChromosomes()) {
					if (!this.active.containsKey(chromosome)) {
						msg.append(chromosome.id()).append(' ');
					}
				}
				msg.append('}');
				throw new IllegalStateException(msg.toString());
			}

			ImmutableMap.Builder<IChromosome<?>, AllelePair<?>> genome = new ImmutableMap.Builder<>();
			for (IChromosome<?> chromosome : this.karyotype.getChromosomes()) {
				Allele<?> activeAllele = this.active.get(chromosome);
				Allele<?> inactiveAllele = this.inactive.get(chromosome);
				if (activeAllele == null || inactiveAllele == null) {
					throw new IllegalStateException("Tried to build genome, but the allele pair was incomplete for the chromosome: " + chromosome.id());
				}
				genome.put(chromosome, new AllelePair(activeAllele, inactiveAllele));
			}

			return new Genome(this.karyotype, genome.build());
		}
	}
}
