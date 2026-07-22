package forestry.core.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.plugin.IArboricultureRegistration;
import forestry.api.plugin.ITreeSpeciesBuilder;
import forestry.apiimpl.plugin.ArboricultureRegistration;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.utils.SpeciesUtil;
import forestry.plugin.DefaultTreeSpecies;

/**
 * Generates {@code data/forestry/tree_species/*.json} for every built-in tree, read directly from the
 * {@code DefaultTreeSpecies} builders via {@link ArboricultureRegistration#forEachSpeciesBuilder} - the same builders
 * the code-registration path uses, so the generated definitions are a faithful parallel artifact (proven by
 * {@code TreeSpeciesEquivalenceTest}).
 * <p>
 * Unlike {@link BeeSpeciesProvider}, trees need no companion instance -&gt; id inversion: the only reference
 * chromosomes on a tree genome (fruit, tree effect) are already recorded by {@link RecordingGenomeBuilder} as
 * {@code Allele.reference(id)}, since {@code IGenomeBuilder#set(IChromosome, ResourceLocation)} is the id-based
 * overload species builders call directly for those chromosomes.
 * <p>
 * Addon mods generate their own tree species by subclassing and overriding
 * {@link #addSpecies(IArboricultureRegistration)}, mirroring {@code MutationProvider}.
 */
public class TreeSpeciesProvider implements DataProvider {
	private final PackOutput.PathProvider pathProvider;
	private final CompletableFuture<HolderLookup.Provider> lookupProvider;

	public TreeSpeciesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "tree_species");
		this.lookupProvider = lookupProvider;
	}

	// Collector ctor used by the static buildDefinitions()/seedLiveSpeciesForDatagen(): assembles definitions via
	// addSpecies() without a PackOutput to write to. Never call run() on this instance - both fields are null.
	private TreeSpeciesProvider() {
		this.pathProvider = null;
		this.lookupProvider = null;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		return this.lookupProvider.thenCompose(provider -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
			List<CompletableFuture<?>> futures = new ArrayList<>();
			buildSpeciesDefinitions().forEach((id, def) -> {
				JsonElement json = TreeSpeciesDefinition.codec().encodeStart(ops, def).getOrThrow();
				futures.add(DataProvider.saveStable(cache, json, this.pathProvider.json(id)));
			});
			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		});
	}

	/**
	 * Add your tree species here, e.g. {@code MyModTreeSpecies.register(reg)}. Override this in your addon's provider
	 * and do NOT call {@code super.addSpecies(reg)} - that would re-emit Forestry's built-in tree JSON into your datapack.
	 */
	protected void addSpecies(IArboricultureRegistration reg) {
		DefaultTreeSpecies.register(reg);
	}

	/**
	 * Assembles the tree species definitions this provider emits, straight from the {@link ArboricultureRegistration}
	 * builders populated by {@link #addSpecies}. Needs no registry access (unlike {@link #run}, which only needs
	 * {@link RegistryOps} to encode the result to JSON).
	 */
	protected Map<ResourceLocation, TreeSpeciesDefinition> buildSpeciesDefinitions() {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ArboricultureRegistration reg = new ArboricultureRegistration(type);
		addSpecies(reg);

		Map<ResourceLocation, TreeSpeciesDefinition> definitions = new LinkedHashMap<>();
		reg.forEachSpeciesBuilder((id, builder) -> definitions.put(id, buildDefinition(builder)));
		return definitions;
	}

	/**
	 * Builds every built-in tree species definition from the {@code DefaultTreeSpecies} builders - the faithful parallel
	 * artifact of the code-built species. For datagen seeding ({@link #seedLiveSpeciesForDatagen()}) and equivalence
	 * tests; addon species are emitted through {@link #run}/{@link #addSpecies} instead.
	 */
	public static Map<ResourceLocation, TreeSpeciesDefinition> buildDefinitions() {
		return new TreeSpeciesProvider().buildSpeciesDefinitions();
	}

	/**
	 * Populates the live tree species type directly from {@link #buildDefinitions()}, bypassing the datapack JSON
	 * round trip. Only for the standalone data generator ({@code Data#preDataGen}), which never fires the datapack
	 * reload that loads species at real server start. Species built here come from the identical
	 * {@code DefaultTreeSpecies} source the generated JSON is derived from.
	 */
	public static void seedLiveSpeciesForDatagen() {
		GeneticsReloadHandler.rebuildTreeSpecies(buildDefinitions());
	}

	private static TreeSpeciesDefinition buildDefinition(ITreeSpeciesBuilder builder) {
		RecordingGenomeBuilder rec = new RecordingGenomeBuilder();
		builder.buildGenome(rec);
		return new TreeSpeciesDefinition(
			builder.getGenus(),
			builder.getSpecies(),
			builder.isDominant(),
			builder.hasGlint(),
			builder.isSecret(),
			builder.getComplexity(),
			builder.getAuthority(),
			builder.getEscritoireColor(),
			builder.getTemperature(),
			builder.getHumidity(),
			builder.getRarity(),
			rec.overrides
		);
	}

	@Override
	public String getName() {
		return "Forestry Tree Species";
	}
}
