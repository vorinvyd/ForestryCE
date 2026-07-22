package forestry.arboriculture.genetics;

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

/**
 * Datapack loader for tree species: a {@link SimpleJsonResourceReloadListener} over the {@code tree_species} folder.
 * Decodes each JSON entry via {@link TreeSpeciesDefinition#codec()} (fail-soft: a bad file is logged and skipped),
 * stores the last-parsed map, and hands it to {@link GeneticsReloadHandler#rebuildTreeSpecies} to swap into the live
 * tree species type. Mirrors {@code forestry.apiculture.genetics.BeeSpeciesManager}.
 * <p>
 * Singleton, registered once as a server reload listener (see {@code ModuleCore#registerReloadListeners}); the
 * client never registers it as a reload listener (it has no datapack access), but reuses the same instance as a
 * plain data holder for the definitions delivered by {@code TreeSpeciesSyncPacket} on login/reload (see
 * {@link #setDefinitions}).
 */
public final class TreeSpeciesManager extends SimpleJsonResourceReloadListener {
	public static final TreeSpeciesManager INSTANCE = new TreeSpeciesManager();

	private static final String FOLDER = "tree_species";

	// Empty until the first load (server: this class's own apply(); client: the sync packet). Never null. Volatile:
	// written from the reload game executor or the network handler thread, read from wherever getDefinitions() is
	// called (e.g. ModuleCore's OnDatapackSyncEvent listener).
	private volatile Map<ResourceLocation, TreeSpeciesDefinition> definitions = Map.of();

	private TreeSpeciesManager() {
		super(new Gson(), FOLDER);
	}

	/**
	 * @return The last-parsed species definitions: from the datapack on the server, or the sync packet on the
	 * client. Never {@code null}; empty before the first load.
	 */
	public Map<ResourceLocation, TreeSpeciesDefinition> getDefinitions() {
		return this.definitions;
	}

	/**
	 * Client-side mirror: stores the definitions received via {@code TreeSpeciesSyncPacket} directly, without going
	 * through the JSON decode path (the packet already carries decoded {@link TreeSpeciesDefinition}s, produced by
	 * the registry-aware network stream codec).
	 */
	public void setDefinitions(Map<ResourceLocation, TreeSpeciesDefinition> definitions) {
		this.definitions = definitions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		// getRegistryLookup() (NeoForge ContextAwareReloadListener) carries the current reload's registry access,
		// populated before apply() for both the cold server start and every /reload - NOT ServerLifecycleHooks
		// (null on cold start). See BeeSpeciesManager's apply() for the full rationale.
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, TreeSpeciesDefinition> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<TreeSpeciesDefinition> result = TreeSpeciesDefinition.codec().parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping tree species {}: {}", id, error))
				.ifPresent(def -> parsed.put(id, def));
		}

		this.definitions = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildTreeSpecies(this.definitions);
	}
}
