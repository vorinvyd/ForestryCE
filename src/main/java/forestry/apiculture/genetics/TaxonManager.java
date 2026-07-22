package forestry.apiculture.genetics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import forestry.Forestry;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.TaxonDefinition;

/**
 * Datapack loader for taxa: a {@link SimpleJsonResourceReloadListener} over the {@code taxon} folder. Decodes each JSON
 * entry via {@link TaxonDefinition#CODEC} (fail-soft: a bad file is logged and skipped), stores the last-parsed
 * definitions, and hands them to {@link GeneticsReloadHandler#rebuildTaxa} to merge onto the code-registered taxonomy.
 * <p>
 * Mirrors {@link FlowerTypeManager}/{@link BeeEffectManager}: a species' genus must resolve to a taxon when the species
 * is projected, so taxa must be merged before species are (re)built. Registered before {@link BeeSpeciesManager} in
 * {@code ModuleCore}. The client keeps the definitions delivered by {@code TaxonSyncPacket} and re-applies them.
 */
public final class TaxonManager extends SimpleJsonResourceReloadListener {
	public static final TaxonManager INSTANCE = new TaxonManager();

	private static final String FOLDER = "taxon";

	private volatile Map<ResourceLocation, TaxonDefinition> definitions = Map.of();

	private TaxonManager() {
		super(new Gson(), FOLDER);
	}

	/**
	 * @return The last-parsed taxon definitions: from the datapack on the server, or the sync packet on the client.
	 * Never {@code null}; empty before the first load.
	 */
	public Map<ResourceLocation, TaxonDefinition> getDefinitions() {
		return this.definitions;
	}

	/** Client-side mirror: stores the definitions received via {@code TaxonSyncPacket} and re-applies them. */
	public void setDefinitions(Map<ResourceLocation, TaxonDefinition> definitions) {
		this.definitions = definitions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, TaxonDefinition> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<TaxonDefinition> result = TaxonDefinition.CODEC.parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping taxon {}: {}", id, error))
				.ifPresent(def -> parsed.put(id, def));
		}

		this.definitions = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildTaxa(this.definitions.values());
	}
}
