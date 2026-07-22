package forestry.core.data;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.ITaxon;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.TaxonDefinition;
import forestry.plugin.ForestryTaxonomy;

/**
 * Generates {@code data/forestry/taxon/*.json} for base Forestry's whole taxonomy (domains down to genera, including the
 * genus default chromosomes bee species inherit). This provider is the single source of truth for the built-in taxa
 * (they are no longer code-registered at runtime); it must stay in sync with {@link ForestryTaxonomy}.
 * <p>
 * Add-on mods generate their own taxa by subclassing and overriding {@link #addTaxa()}, mirroring {@link MutationProvider}
 * and {@link FlowerTypeProvider}.
 */
public class TaxonProvider implements DataProvider {
	private final PackOutput.PathProvider path;
	private final Map<ResourceLocation, TaxonDefinition> pending = new LinkedHashMap<>();

	public TaxonProvider(PackOutput output) {
		this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "taxon");
	}

	// Collector used by seedLiveTaxaForDatagen: gathers the built-ins via addTaxa() without needing a PackOutput to
	// write to (it never runs the provider). Never call this to write JSON - path is null.
	private TaxonProvider() {
		this.path = null;
	}

	/**
	 * Add your taxa here. Make sure NOT to call the super constructor in your mod.
	 */
	protected void addTaxa() {
		ForestryTaxonomy.buildDefaultTaxa().values().forEach(taxon -> add(toDefinition(taxon)));
	}

	protected void add(TaxonDefinition def) {
		this.pending.put(ResourceLocation.fromNamespaceAndPath(ForestryConstants.MOD_ID, def.name()), def);
	}

	// Converts a live taxon into its serializable definition. Reference-chromosome defaults are stored by id only
	// (their dominance is intrinsic to the referenced value and re-resolved at projection time), so they serialize
	// without a dominance flag; data-chromosome defaults keep their inline allele (dominance included).
	private static TaxonDefinition toDefinition(ITaxon taxon) {
		String parent = taxon.parent() == null ? null : taxon.parent().name();
		Map<IChromosome<?>, ITaxon.TaxonAllele> defaults = taxon.alleles();
		if (defaults.isEmpty()) {
			return new TaxonDefinition(parent, taxon.name(), taxon.rank(), null, Map.of());
		}

		// Sorted by chromosome id so the generated JSON is deterministic (taxon.alleles() is an IdentityHashMap).
		Map<ResourceLocation, Allele<?>> alleles = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
		for (Map.Entry<IChromosome<?>, ITaxon.TaxonAllele> entry : defaults.entrySet()) {
			IChromosome<?> chromosome = entry.getKey();
			ITaxon.TaxonAllele taxonAllele = entry.getValue();
			Allele<?> allele = taxonAllele.reference() != null
				? Allele.reference(taxonAllele.reference())
				: taxonAllele.allele();
			alleles.put(chromosome.id(), allele);
		}

		ISpeciesType<?, ?> type = findType(defaults.keySet().iterator().next());
		ResourceLocation typeId = type == null ? null : type.id();
		return new TaxonDefinition(parent, taxon.name(), taxon.rank(), typeId, alleles);
	}

	// The species type whose karyotype owns the given chromosome: needed so the alleles field can be serialized against
	// that karyotype. All of a taxon's default chromosomes belong to the same karyotype, so the first suffices.
	@Nullable
	private static ISpeciesType<?, ?> findType(IChromosome<?> chromosome) {
		for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
			if (type.getKaryotype().contains(chromosome)) {
				return type;
			}
		}
		return null;
	}

	/**
	 * Populates the live taxonomy directly from {@link #addTaxa()}, bypassing the datapack JSON round trip. Only for use
	 * by the standalone data generator ({@code Data#preDataGen}): a data-generator invocation never fires the datapack
	 * reload that loads taxa at real server start, but a species' genus must resolve to a taxon when the species is
	 * seeded for datagen. Mirrors {@code FlowerTypeProvider#seedLiveFlowerTypesForDatagen}; must run before the species
	 * providers seed their live species.
	 */
	public static void seedLiveTaxaForDatagen() {
		TaxonProvider collector = new TaxonProvider();
		collector.addTaxa();
		GeneticsReloadHandler.rebuildTaxa(collector.pending.values());
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		this.pending.clear();
		addTaxa();
		var futures = this.pending.entrySet().stream().map(entry -> {
			JsonElement json = TaxonDefinition.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
			return DataProvider.saveStable(output, json, this.path.json(entry.getKey()));
		}).toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(futures);
	}

	@Override
	public String getName() {
		return "Forestry Taxa";
	}
}
