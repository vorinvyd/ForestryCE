package forestry.apiimpl.plugin;

import com.google.common.collect.ImmutableMap;
import forestry.Forestry;
import forestry.api.IForestryApi;
import forestry.api.genetics.*;
import forestry.api.genetics.alleles.*;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.ISpeciesBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base implementation of {@link ISpeciesBuilder} with common logic.
 *
 * @param <I> Interface type of the species builders used by this species registration.
 * @param <S> Interface type of the species registered by this species registration.
 * @param <B> The concrete type of the species builder used by this species registration.
 */
public abstract class SpeciesRegistration<I extends ISpeciesBuilder<? extends ISpeciesType<S, ?>, S, I>, S extends ISpecies<?>, B extends I> {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private final ModifiableRegistrar<ResourceLocation, I, B> species = new ModifiableRegistrar(ISpeciesBuilder.class);

	protected final ISpeciesType<S, ?> type;

	public SpeciesRegistration(ISpeciesType<S, ?> type) {
		this.type = type;
	}

	protected abstract B createSpeciesBuilder(ResourceLocation id, String genus, String species);

	protected I register(ResourceLocation id, String genus, String species) {
		return this.species.create(id, createSpeciesBuilder(id, genus, species));
	}

	public void modifySpecies(ResourceLocation id, Consumer<I> action) {
		this.species.modify(id, action);
	}

	/**
	 * Iterates the registered species builders in registration order, without building any species. Used by datagen
	 * to read builder state (genus, products, genome overrides, ...) directly, bypassing
	 * {@link #createDefaultGenomeBuilder}/{@link #buildAll} entirely.
	 */
	public void forEachSpeciesBuilder(BiConsumer<ResourceLocation, B> consumer) {
		this.species.forEach(consumer);
	}

	// Builds the base genome (taxon defaults + species chromosome + remaining defaults) shared by
	// the code-builder path and the data-driven JSON projector. Does not apply per-species overrides
	// or call build().
	@SuppressWarnings({"unchecked"})
	public static IGenomeBuilder createDefaultGenomeBuilder(IKaryotype karyotype, ResourceLocation speciesId, String genus, boolean dominant) {
		IChromosome<ResourceLocation> speciesChromosome = karyotype.getSpeciesChromosome();
		IGenomeBuilder builder = karyotype.createGenomeBuilder();
		ITaxon[] ancestry = IForestryApi.INSTANCE.getGeneticManager().getParentTaxa(genus);
		for (ITaxon taxon : ancestry) {
			for (Map.Entry<IChromosome<?>, ITaxon.TaxonAllele> e : taxon.alleles().entrySet()) {
				IChromosome<?> chromosome = e.getKey();
				ITaxon.TaxonAllele taxonAllele = e.getValue();
				if (!karyotype.contains(chromosome)) {
					Forestry.LOGGER.warn("Default allele set by taxon {} skipped for species {} due to being invalid for its karyotype", taxon.name(), speciesId);
					continue;
				}
				ResourceLocation reference = taxonAllele.reference();
				if (reference != null) {
					builder.set((IChromosome<ResourceLocation>) chromosome, reference);
				} else {
					builder.setUnchecked(chromosome, AllelePair.both(taxonAllele.allele()));
				}
			}
		}
		builder.setUnchecked(speciesChromosome, AllelePair.both(new Allele<>(speciesId, dominant)));
		builder.setRemainingDefault();
		return builder;
	}

	// Creates the final map of species. The species chromosome is a reference chromosome,
	// so it no longer needs to be populated separately; it is resolved on demand via the species type.
	@SuppressWarnings({"unchecked", "rawtypes"})
	public ImmutableMap<ResourceLocation, S> buildAll() {
		IKaryotype karyotype = this.type.getKaryotype();

		ImmutableMap<ResourceLocation, S> allSpecies = this.species.build((id, builder) -> {
			IGenomeBuilder defaultGenomeBuilder = createDefaultGenomeBuilder(karyotype, id, builder.getGenus(), builder.isDominant());
			IGenome defaultGenome = builder.buildGenome(defaultGenomeBuilder);
			return builder.createSpeciesFactory().create(id, this.type.cast(), defaultGenome, builder);
		});

		return allSpecies;
	}
}
