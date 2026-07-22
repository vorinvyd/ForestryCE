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
import forestry.api.apiculture.IFlowerType;
import forestry.core.genetics.GeneticsReloadHandler;

/**
 * Datapack loader for flower types: a {@link SimpleJsonResourceReloadListener} over the {@code flower_type} folder.
 * Decodes each entry via {@link FlowerTypeTypes#CODEC} (fail-soft), stores the last-parsed map, and hands it to
 * {@link GeneticsReloadHandler#rebuildFlowerTypes} which installs code-base union datapack into the bee species type.
 * Server-only reload listener; the client reuses the instance as a data holder for {@code FlowerTypeSyncPacket}.
 */
public class FlowerTypeManager extends SimpleJsonResourceReloadListener {
	public static final FlowerTypeManager INSTANCE = new FlowerTypeManager();

	private static final String FOLDER = "flower_type";

	private volatile Map<ResourceLocation, IFlowerType> definitions = Map.of();

	private FlowerTypeManager() {
		super(new Gson(), FOLDER);
	}

	public Map<ResourceLocation, IFlowerType> getDefinitions() {
		return this.definitions;
	}

	public void setDefinitions(Map<ResourceLocation, IFlowerType> definitions) {
		this.definitions = definitions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		FlowerTypeTypes.registerBuiltins();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, IFlowerType> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<IFlowerType> result = FlowerTypeTypes.CODEC.parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping flower type {}: {}", id, error))
				.ifPresent(type -> parsed.put(id, type));
		}

		this.definitions = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildFlowerTypes(this.definitions);
	}
}
