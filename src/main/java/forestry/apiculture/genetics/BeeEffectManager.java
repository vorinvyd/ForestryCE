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
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.core.genetics.GeneticsReloadHandler;

/**
 * Datapack loader for bee effects: a {@link SimpleJsonResourceReloadListener} over the {@code bee_effect} folder.
 * Decodes each JSON entry via the {@link IBeeEffect#CODEC} dispatch codec (fail-soft: a bad file is logged and
 * skipped), stores the last-parsed map, and hands it to {@link GeneticsReloadHandler#rebuildBeeEffects} to merge onto
 * the code builtins in the live bee species type.
 * <p>
 * This mirrors {@link BeeSpeciesManager}, and for the same reason: bee effects are datapack-defined and must reload on
 * every {@code /reload}. A {@code SimpleJsonResourceReloadListener} re-reads on every reload; a datapack registry would
 * not (dynamic registries are frozen from world-load), which is why effects are loaded this way rather than as a
 * registry. Registered as a server reload listener <em>before</em> {@link BeeSpeciesManager} (see
 * {@code ModuleCore#registerReloadListeners}) so effects exist before species that reference them are projected. The
 * client never registers it as a reload listener (no datapack access) but reuses the same instance as a plain data
 * holder for the effects delivered by {@code BeeEffectSyncPacket} on login/reload (see {@link #setEffects}).
 */
public final class BeeEffectManager extends SimpleJsonResourceReloadListener {
	public static final BeeEffectManager INSTANCE = new BeeEffectManager();

	private static final String FOLDER = "bee_effect";

	// Empty until the first load (server: this class's own apply(); client: the sync packet). Never null. Volatile:
	// written from the reload game executor or the network handler thread, read from wherever getEffects() is called.
	private volatile Map<ResourceLocation, IBeeEffect> effects = Map.of();

	private BeeEffectManager() {
		super(new Gson(), FOLDER);
	}

	/**
	 * @return The last-parsed bee effects: from the datapack on the server, or the sync packet on the client. Never
	 * {@code null}; empty before the first load.
	 */
	public Map<ResourceLocation, IBeeEffect> getEffects() {
		return this.effects;
	}

	/**
	 * Client-side mirror: stores the effects received via {@code BeeEffectSyncPacket} directly, without going through
	 * the JSON decode path (the packet already carries decoded {@link IBeeEffect}s).
	 */
	public void setEffects(Map<ResourceLocation, IBeeEffect> effects) {
		this.effects = effects;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		// getRegistryLookup() is populated with the current reload's registry access before apply() runs, for both the
		// cold server start and every /reload (see BeeSpeciesManager#apply for the full rationale). Effect codecs may
		// reference registries (mob effects, entity types, fluids, blocks), so decode with registry-aware ops.
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, IBeeEffect> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<IBeeEffect> result = IBeeEffect.CODEC.parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping bee effect {}: {}", id, error))
				.ifPresent(effect -> parsed.put(id, effect));
		}

		this.effects = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildBeeEffects(this.effects);
	}
}
