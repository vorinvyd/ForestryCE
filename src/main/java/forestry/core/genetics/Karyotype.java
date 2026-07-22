package forestry.core.genetics;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IChromosomeBuilder;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.IKaryotypeBuilder;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Karyotype implements IKaryotype {
	private final ResourceLocation id;
	private final ImmutableList<IChromosome<?>> chromosomes;
	private final ImmutableMap<ResourceLocation, IChromosome<?>> byId;
	private final IChromosome<ResourceLocation> speciesChromosome;
	// Eager defaults for data chromosomes; reference chromosomes store an ID resolved lazily (post-population).
	private final ImmutableMap<IChromosome<?>, Allele<?>> dataDefaults;
	private final ImmutableMap<IChromosome<?>, ResourceLocation> referenceDefaults;
	private final ResourceLocation defaultSpecies;
	private final Set<IChromosome<?>> weaklyInherited;
	private final Codec<IGenome> genomeCodec;
	private final StreamCodec<RegistryFriendlyByteBuf, IGenome> genomeStreamCodec;
	private final Map<IChromosome<?>, Allele<?>> resolvedReferenceDefaults = new IdentityHashMap<>();
	@Nullable
	private ImmutableMap<IChromosome<?>, Allele<?>> allDefaults;

	public Karyotype(ResourceLocation id, ImmutableList<IChromosome<?>> chromosomes, IChromosome<ResourceLocation> speciesChromosome, ImmutableMap<IChromosome<?>, Allele<?>> dataDefaults, ImmutableMap<IChromosome<?>, ResourceLocation> referenceDefaults, Set<IChromosome<?>> weaklyInherited, ResourceLocation defaultSpecies) {
		this.id = id;
		this.chromosomes = chromosomes;
		this.speciesChromosome = speciesChromosome;
		this.dataDefaults = dataDefaults;
		this.referenceDefaults = referenceDefaults;
		this.weaklyInherited = weaklyInherited;
		this.defaultSpecies = defaultSpecies;

		ImmutableMap.Builder<ResourceLocation, IChromosome<?>> byId = ImmutableMap.builderWithExpectedSize(chromosomes.size());
		for (IChromosome<?> chromosome : chromosomes) {
			byId.put(chromosome.id(), chromosome);
		}
		this.byId = byId.build();

		this.genomeCodec = buildGenomeCodec();
		this.genomeStreamCodec = buildGenomeStreamCodec();
	}

	private Codec<IGenome> buildGenomeCodec() {
		return Codec.<IChromosome<?>, AllelePair<?>>dispatchedMap(chromosomeKeyCodec(), chromosome -> pairCodecFor(chromosome))
				.xmap(map -> Genome.sanitizeAlleles(this, map), IGenome::getChromosomes);
	}

	private static <V> Codec<AllelePair<V>> pairCodecFor(IChromosome<V> chromosome) {
		Codec<Allele<V>> alleleCodec = Allele.codec(chromosome.valueCodec());
		return RecordCodecBuilder.create(instance -> instance.group(
				alleleCodec.fieldOf("active").forGetter(AllelePair::active),
				alleleCodec.fieldOf("inactive").forGetter(AllelePair::inactive)
		).apply(instance, (active, inactive) -> new AllelePair<>(active, inactive)));
	}

	private StreamCodec<RegistryFriendlyByteBuf, IGenome> buildGenomeStreamCodec() {
		return StreamCodec.of(
				(buf, genome) -> {
					for (IChromosome<?> chromosome : this.chromosomes) {
						writePair(buf, genome, chromosome);
					}
				},
				buf -> {
					Map<IChromosome<?>, AllelePair<?>> map = new IdentityHashMap<>(this.chromosomes.size());
					for (IChromosome<?> chromosome : this.chromosomes) {
						map.put(chromosome, readPair(buf, chromosome));
					}
					return Genome.sanitizeAlleles(this, map);
				}
		);
	}

	private static <V> void writePair(RegistryFriendlyByteBuf buf, IGenome genome, IChromosome<V> chromosome) {
		StreamCodec<RegistryFriendlyByteBuf, Allele<V>> alleleCodec = Allele.streamCodec(chromosome.valueStreamCodec());
		AllelePair<V> pair = genome.getAllelePair(chromosome);
		alleleCodec.encode(buf, pair.active());
		alleleCodec.encode(buf, pair.inactive());
	}

	private static <V> AllelePair<V> readPair(RegistryFriendlyByteBuf buf, IChromosome<V> chromosome) {
		StreamCodec<RegistryFriendlyByteBuf, Allele<V>> alleleCodec = Allele.streamCodec(chromosome.valueStreamCodec());
		Allele<V> active = alleleCodec.decode(buf);
		Allele<V> inactive = alleleCodec.decode(buf);
		return new AllelePair<>(active, inactive);
	}

	@Override
	public ResourceLocation id() {
		return this.id;
	}

	@Override
	public ImmutableList<IChromosome<?>> getChromosomes() {
		return this.chromosomes;
	}

	@Override
	public boolean contains(IChromosome<?> chromosome) {
		return this.byId.containsKey(chromosome.id());
	}

	@Override
	public IChromosome<ResourceLocation> getSpeciesChromosome() {
		return this.speciesChromosome;
	}

	@Nullable
	@Override
	public IChromosome<?> getChromosome(ResourceLocation id) {
		return this.byId.get(id);
	}

	@Override
	public Codec<IChromosome<?>> chromosomeKeyCodec() {
		return ResourceLocation.CODEC.flatXmap(
				rid -> {
					IChromosome<?> chromosome = this.byId.get(rid);
					return chromosome != null ? DataResult.success(chromosome) : DataResult.error(() -> "Unknown chromosome: " + rid);
				},
				chromosome -> DataResult.success(chromosome.id())
		);
	}

	@Override
	public int size() {
		return this.chromosomes.size();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <V> Allele<V> getDefaultAllele(IChromosome<V> chromosome) {
		Allele<?> data = this.dataDefaults.get(chromosome);
		if (data != null) {
			return (Allele<V>) data;
		}
		return (Allele<V>) this.resolvedReferenceDefaults.computeIfAbsent(chromosome, this::resolveReferenceDefault);
	}

	private Allele<?> resolveReferenceDefault(IChromosome<?> chromosome) {
		ResourceLocation refId = this.referenceDefaults.get(chromosome);
		if (refId == null) {
			throw new IllegalArgumentException("Chromosome " + chromosome.id() + " is not part of karyotype " + this.id);
		}
		// A chromosome with a reference (ID) default is by definition a reference chromosome, so it must have a resolver.
		IChromosome.IReferenceResolver<?> resolver = Objects.requireNonNull(chromosome.resolver(), () -> "Reference chromosome " + chromosome.id() + " has no resolver");
		return new Allele<>(refId, resolver.isDominant(refId));
	}

	@Override
	public ImmutableMap<IChromosome<?>, Allele<?>> getDefaultAlleles() {
		if (this.allDefaults == null) {
			ImmutableMap.Builder<IChromosome<?>, Allele<?>> builder = ImmutableMap.builderWithExpectedSize(this.chromosomes.size());
			for (IChromosome<?> chromosome : this.chromosomes) {
				builder.put(chromosome, getDefaultAllele(chromosome));
			}
			this.allDefaults = builder.build();
		}
		return this.allDefaults;
	}

	@Override
	public boolean isWeaklyInherited(IChromosome<?> chromosome) {
		return this.weaklyInherited.contains(chromosome);
	}

	@Override
	public ResourceLocation getDefaultSpecies() {
		return this.defaultSpecies;
	}

	@Override
	public Codec<IGenome> getGenomeCodec() {
		return this.genomeCodec;
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, IGenome> getGenomeStreamCodec() {
		return this.genomeStreamCodec;
	}

	@Override
	public IGenomeBuilder createGenomeBuilder() {
		return new Genome.Builder(this);
	}

	public static class Builder implements IKaryotypeBuilder {
		private final LinkedHashMap<IChromosome<?>, ChromosomeBuilder<?>> chromosomes = new LinkedHashMap<>();
		@Nullable
		private IChromosome<ResourceLocation> speciesChromosome;
		@Nullable
		private ResourceLocation defaultSpeciesId;

		@Override
		public void setSpecies(IChromosome<ResourceLocation> species, ResourceLocation defaultId) {
			if (this.speciesChromosome != null && this.speciesChromosome != species) {
				throw new IllegalStateException("The species chromosome for this karyotype has already been set: " + this.speciesChromosome.id() + ", but tried setting to " + species.id());
			}
			this.speciesChromosome = species;
			this.defaultSpeciesId = defaultId;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <V> IChromosomeBuilder<V> set(IChromosome<V> chromosome, Allele<V> defaultAllele) {
			ChromosomeBuilder<V> builder = (ChromosomeBuilder<V>) this.chromosomes.computeIfAbsent(chromosome, key -> new ChromosomeBuilder<>(chromosome));
			return builder.setDefault(defaultAllele);
		}

		@SuppressWarnings("unchecked")
		@Override
		public IChromosomeBuilder<ResourceLocation> set(IChromosome<ResourceLocation> chromosome, ResourceLocation defaultId) {
			ChromosomeBuilder<ResourceLocation> builder = (ChromosomeBuilder<ResourceLocation>) this.chromosomes.computeIfAbsent(chromosome, key -> new ChromosomeBuilder<>(chromosome));
			builder.defaultAllele = null;
			builder.defaultReferenceId = defaultId;
			return builder;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <V> IChromosomeBuilder<V> get(IChromosome<V> chromosome) {
			ChromosomeBuilder<?> builder = this.chromosomes.get(chromosome);
			if (builder == null) {
				throw new IllegalArgumentException("Chromosome " + chromosome.id() + " has not been added to this karyotype");
			}
			return (IChromosomeBuilder<V>) builder;
		}

		public Karyotype build(ResourceLocation id) {
			Preconditions.checkState(this.defaultSpeciesId != null && this.speciesChromosome != null, "IKaryotypeBuilder is missing a species chromosome.");

			ImmutableList.Builder<IChromosome<?>> order = ImmutableList.builderWithExpectedSize(this.chromosomes.size() + 1);
			ImmutableMap.Builder<IChromosome<?>, Allele<?>> dataDefaults = ImmutableMap.builder();
			ImmutableMap.Builder<IChromosome<?>, ResourceLocation> referenceDefaults = ImmutableMap.builder();
			Set<IChromosome<?>> weaklyInherited = Collections.newSetFromMap(new IdentityHashMap<>());

			// Species chromosome goes first.
			order.add(this.speciesChromosome);
			referenceDefaults.put(this.speciesChromosome, this.defaultSpeciesId);

			for (Map.Entry<IChromosome<?>, ChromosomeBuilder<?>> entry : this.chromosomes.entrySet()) {
				IChromosome<?> chromosome = entry.getKey();
				ChromosomeBuilder<?> builder = entry.getValue();

				if (chromosome == this.speciesChromosome) {
					continue;
				}

				order.add(chromosome);

				if (builder.defaultAllele != null) {
					dataDefaults.put(chromosome, builder.defaultAllele);
				} else if (builder.defaultReferenceId != null) {
					referenceDefaults.put(chromosome, builder.defaultReferenceId);
				} else {
					throw new IllegalStateException("Chromosome \"" + chromosome.id() + "\" has no default allele in the karyotype for species " + this.defaultSpeciesId);
				}

				if (builder.weaklyInherited) {
					weaklyInherited.add(chromosome);
				}
			}

			return new Karyotype(id, order.build(), this.speciesChromosome, dataDefaults.build(), referenceDefaults.build(), weaklyInherited, this.defaultSpeciesId);
		}
	}
}
