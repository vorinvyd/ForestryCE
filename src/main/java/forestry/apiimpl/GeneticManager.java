package forestry.apiimpl;

import com.google.common.collect.ImmutableMap;
import forestry.Forestry;
import forestry.api.genetics.*;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.core.genetics.Taxon;
import forestry.core.genetics.TaxonDefinition;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeneticManager implements IGeneticManager {
	// The code-registered taxa from setup, kept as the immutable base so a datapack (re)load merges on top of them and
	// removing a datapack cleanly reverts to exactly the built-in taxonomy (see applyDatapackTaxa).
	private final ImmutableMap<String, ITaxon> baseTaxa;
	// The live taxa (code builtins + datapack taxa), rebuilt on every datapack (re)load before species are projected.
	// Volatile: written on the reload game executor / network thread, read wherever getTaxon is called.
	private volatile ImmutableMap<String, ITaxon> taxa;
	private final ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes;

	public GeneticManager(ImmutableMap<String, ITaxon> taxa, ImmutableMap<ResourceLocation, ISpeciesType<?, ?>> speciesTypes) {
		this.baseTaxa = taxa;
		this.taxa = taxa;
		this.speciesTypes = speciesTypes;
	}

	@Override
	public ITaxon getTaxon(String name) {
		ITaxon taxon = this.taxa.get(name);
		if (taxon == null) {
			throw new IllegalStateException("No taxon was registered with name '" + name + "'");
		}
		return taxon;
	}

	@Nullable
	@Override
	public ITaxon getTaxonSafe(String name) {
		return this.taxa.get(name);
	}

	/**
	 * Merges the datapack-loaded taxa onto the code-registered base and swaps the result into the live taxonomy. Always
	 * rebuilds from the {@link #baseTaxa} snapshot, so passing an empty collection reverts to exactly the built-in
	 * taxonomy. Since base Forestry now ships its whole taxonomy as datapack JSON, a definition may be a root
	 * {@link TaxonomicRank#DOMAIN domain} (null parent, explicit rank) or any child taxon (rank derived from its parent
	 * unless stated). Definitions are resolved in dependency order via a fixpoint, so a datapack may define a taxon and
	 * its parent in any order. A definition whose parent never resolves (or whose parent is a genus, which cannot have
	 * children) is skipped with a warning rather than crashing the reload — a species referencing a skipped genus fails
	 * its own (fail-soft) build, so the generator must emit a taxon for every genus it uses.
	 */
	@ApiStatus.Internal
	public void applyDatapackTaxa(Collection<TaxonDefinition> definitions) {
		if (definitions.isEmpty()) {
			this.taxa = this.baseTaxa;
			return;
		}

		Map<String, ITaxon> merged = new LinkedHashMap<>(this.baseTaxa);
		ArrayList<TaxonDefinition> pending = new ArrayList<>(definitions);

		boolean progress = true;
		while (progress && !pending.isEmpty()) {
			progress = false;
			Iterator<TaxonDefinition> it = pending.iterator();
			while (it.hasNext()) {
				TaxonDefinition def = it.next();

				boolean isRoot = def.parent() == null;
				ITaxon parent = null;
				if (!isRoot) {
					parent = merged.get(def.parent());
					if (parent == null) {
						// Parent not resolved yet; maybe a later iteration (another datapack taxon) will define it.
						continue;
					}
				}
				it.remove();
				progress = true;

				if (isRoot && def.rank() == null) {
					Forestry.LOGGER.warn("Datapack taxon '{}' skipped: a root taxon (no parent) must declare a rank", def.name());
				} else if (!isRoot && parent.rank() == TaxonomicRank.GENUS) {
					Forestry.LOGGER.warn("Datapack taxon '{}' skipped: its parent '{}' is a genus, which cannot have sub-taxa", def.name(), def.parent());
				} else if (merged.containsKey(def.name())) {
					Forestry.LOGGER.warn("Datapack taxon '{}' skipped: a taxon with that name is already registered", def.name());
				} else {
					TaxonomicRank rank = def.rank() != null ? def.rank() : parent.rank().next();
					merged.put(def.name(), new Taxon(def.name(), rank, parent, buildTaxonAlleles(def)));
				}
			}
		}

		for (TaxonDefinition def : pending) {
			Forestry.LOGGER.warn("Datapack taxon '{}' skipped: parent taxon '{}' was never registered", def.name(), def.parent());
		}

		this.taxa = ImmutableMap.copyOf(merged);
	}

	// Reconstructs a taxon's default-chromosome map from its (already karyotype-resolved) allele overrides. Reference
	// chromosomes keep only the referenced id (dominance is resolved from the referenced value at projection time);
	// data chromosomes keep their inline allele. Mirrors GeneticRegistration.TaxonBuilder#setDefaultChromosome.
	private IdentityHashMap<IChromosome<?>, ITaxon.TaxonAllele> buildTaxonAlleles(TaxonDefinition def) {
		IdentityHashMap<IChromosome<?>, ITaxon.TaxonAllele> result = new IdentityHashMap<>();
		if (def.alleles().isEmpty()) {
			return result;
		}
		ISpeciesType<?, ?> type = def.type() == null ? null : getSpeciesTypeSafe(def.type());
		if (type == null) {
			Forestry.LOGGER.warn("Taxon '{}' declares alleles but species type '{}' is unknown; ignoring its defaults", def.name(), def.type());
			return result;
		}
		IKaryotype karyotype = type.getKaryotype();
		for (Map.Entry<ResourceLocation, Allele<?>> entry : def.alleles().entrySet()) {
			IChromosome<?> chromosome = karyotype.getChromosome(entry.getKey());
			if (chromosome == null) {
				Forestry.LOGGER.warn("Taxon '{}' default allele skipped: unknown chromosome '{}' for type '{}'", def.name(), entry.getKey(), def.type());
				continue;
			}
			Allele<?> allele = entry.getValue();
			if (chromosome.resolver() != null) {
				result.put(chromosome, ITaxon.TaxonAllele.reference((ResourceLocation) allele.value(), true));
			} else {
				result.put(chromosome, ITaxon.TaxonAllele.data(allele, true));
			}
		}
		return result;
	}

	@Override
	public ITaxon[] getParentTaxa(String name) {
		ITaxon taxon = getTaxon(name);
		int ordinal = taxon.rank().ordinal();
		ITaxon[] taxa = new Taxon[1 + ordinal];

		for (int i = ordinal; i >= 0; i--) {
			taxa[i] = taxon;
			taxon = taxon.parent();
		}

		return taxa;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S extends ISpecies<?>> IMutationManager<S> getMutations(ISpeciesType<?, ?> speciesType) {
		return (IMutationManager<S>) speciesType.getMutations();
	}

	@Override
	public ISpeciesType<?, ?> getSpeciesType(ResourceLocation speciesTypeId) {
		ISpeciesType<?, ?> type = this.speciesTypes.get(speciesTypeId);
		if (type == null) {
			throw new IllegalStateException("No species type was registered with ID: " + speciesTypeId);
		}
		return type;
	}

	@Nullable
	@Override
	public ISpeciesType<?, ?> getSpeciesTypeSafe(ResourceLocation speciesTypeId) {
		return this.speciesTypes.get(speciesTypeId);
	}

	@Override
	public Collection<ISpeciesType<?, ?>> getSpeciesTypes() {
		return this.speciesTypes.values();
	}
}
