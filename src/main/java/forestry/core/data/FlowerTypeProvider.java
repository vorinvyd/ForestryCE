package forestry.core.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryTags;
import forestry.api.apiculture.ForestryFlowerTypes;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;
import forestry.apiculture.genetics.FlowerTypeTypes;
import forestry.core.genetics.GeneticsReloadHandler;

/**
 * Generates {@code data/forestry/flower_type/*.json} for the 15 built-in flower types. This provider is the single
 * source of truth for the built-ins (they are no longer code-registered at runtime); it must stay in sync with the
 * tags/dominance the mod ships. No registry access is needed — flower-type fields are block/biome tags, which encode
 * as plain resource locations.
 * <p>
 * Addon mods generate their own flower types by subclassing and overriding {@link #addFlowerTypes()}, mirroring
 * {@link MutationProvider}.
 */
public class FlowerTypeProvider implements DataProvider {
	private final PackOutput.PathProvider path;
	private final Map<ResourceLocation, IFlowerType> pending = new LinkedHashMap<>();

	public FlowerTypeProvider(PackOutput output) {
		this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "flower_type");
	}

	// Collector used by seedLiveFlowerTypesForDatagen: gathers the built-ins via addFlowerTypes() without needing a
	// PackOutput to write to (it never runs the provider). Never call this to write JSON - path is null.
	private FlowerTypeProvider() {
		this.path = null;
	}

	/**
	 * Add your flower types here. Make sure NOT to call the super constructor in your mod.
	 */
	protected void addFlowerTypes() {
		add(ForestryFlowerTypes.VANILLA, new TagFlowerType(ForestryTags.Blocks.VANILLA_FLOWERS, true));
		add(ForestryFlowerTypes.NETHER, new TagFlowerType(ForestryTags.Blocks.NETHER_FLOWERS, false));
		add(ForestryFlowerTypes.CACTI, new TagFlowerType(ForestryTags.Blocks.CACTI_FLOWERS, false));
		add(ForestryFlowerTypes.MUSHROOMS, new TagFlowerType(ForestryTags.Blocks.MUSHROOMS_FLOWERS, false));
		add(ForestryFlowerTypes.END, new TagFlowerType(ForestryTags.Blocks.END_FLOWERS, false, BiomeTags.IS_END));
		add(ForestryFlowerTypes.JUNGLE, new TagFlowerType(ForestryTags.Blocks.JUNGLE_FLOWERS, false));
		add(ForestryFlowerTypes.SNOW, new TagFlowerType(ForestryTags.Blocks.SNOW_FLOWERS, true));
		add(ForestryFlowerTypes.WHEAT, new TagFlowerType(ForestryTags.Blocks.WHEAT_FLOWERS, true));
		add(ForestryFlowerTypes.GOURD, new TagFlowerType(ForestryTags.Blocks.GOURD_FLOWERS, true));
		add(ForestryFlowerTypes.CAVE, new TagFlowerType(ForestryTags.Blocks.CAVE_FLOWERS, true));
		add(ForestryFlowerTypes.PHOTOSYNTHESIS, new PhotosynthesisFlowerType());
		add(ForestryFlowerTypes.ANCIENT, new TagFlowerType(ForestryTags.Blocks.ANCIENT_FLOWERS, true));
		add(ForestryFlowerTypes.SEA, new WaterTagFlowerType(ForestryTags.Blocks.SEA_FLOWERS, false));
		add(ForestryFlowerTypes.CORAL, new WaterTagFlowerType(ForestryTags.Blocks.CORAL_FLOWERS, false));
		add(ForestryFlowerTypes.SCULK, new TagFlowerType(ForestryTags.Blocks.SCULK_FLOWERS, false));
	}

	protected void add(ResourceLocation id, IFlowerType type) {
		this.pending.put(id, type);
	}

	/**
	 * Populates the live flower-type map directly from {@link #addFlowerTypes()}, bypassing the datapack JSON round
	 * trip. Only for use by the standalone data generator ({@code Data#preDataGen}): a data-generator invocation never
	 * fires the {@code AddReloadListenerEvent}/datapack-reload cycle that loads flower types at real server start, but
	 * karyotype default-allele resolution for the {@code FLOWER_TYPE} chromosome (e.g. while seeding live bee/butterfly
	 * species for datagen) requires the built-ins to already be registered. Mirrors {@code
	 * BeeSpeciesProvider#seedLiveSpeciesForDatagen}/{@code TreeSpeciesProvider}/{@code ButterflySpeciesProvider}.
	 */
	public static void seedLiveFlowerTypesForDatagen() {
		FlowerTypeTypes.registerBuiltins();
		FlowerTypeProvider collector = new FlowerTypeProvider();
		collector.addFlowerTypes();
		GeneticsReloadHandler.rebuildFlowerTypes(collector.pending);
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		FlowerTypeTypes.registerBuiltins();
		this.pending.clear();
		addFlowerTypes();
		var futures = this.pending.entrySet().stream().map(entry -> {
			JsonElement json = FlowerTypeTypes.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
			return DataProvider.saveStable(output, json, this.path.json(entry.getKey()));
		}).toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(futures);
	}

	@Override
	public String getName() {
		return "Forestry Flower Types";
	}
}
