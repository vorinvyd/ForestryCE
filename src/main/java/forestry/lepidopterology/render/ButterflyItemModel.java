package forestry.lepidopterology.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import forestry.api.ForestryConstants;
import forestry.api.client.IForestryClientApi;
import forestry.api.client.lepidopterology.IButterflyClientManager;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.core.models.AbstractBakedModel;
import forestry.core.models.TRSRBakedModel;
import forestry.core.utils.ResourceUtil;
import it.unimi.dsi.fastutil.floats.FloatObjectPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.SeparateTransformsModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ButterflyItemModel extends AbstractBakedModel {
	// Keyed by item texture location (not species id/instance) so bake-time dedupes correctly and render-time
	// resolution goes through IButterflyClientManager#getTextures(species), which works for any species id -
	// datapack-added or not - via its default-naming-convention fallback. Mirrors ModelSapling's
	// location-keyed `baked` map from the Stage-4 tree rework.
	private final Map<ResourceLocation, BakedModel> subModels;
	private final Cache<FloatObjectPair<String>, BakedModel> cache = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build();

	public ButterflyItemModel(Map<ResourceLocation, BakedModel> subModels) {
		this.subModels = subModels;
	}

	@Override
	protected ItemOverrides createOverrides() {
		return new OverrideList();
	}

	private BakedModel modelFor(ResourceLocation itemTexture) {
		BakedModel model = this.subModels.get(itemTexture);
		if (model != null) {
			return model;
		}
		// The requested texture wasn't among the ones known/baked at model-load time (e.g. a datapack species added
		// after the last resource reload) - fall back to the default butterfly's baked model rather than iterating
		// or NPEing.
		ResourceLocation defaultTexture = IForestryClientApi.INSTANCE.getButterflyManager().getDefaultTextures().getFirst();
		return this.subModels.get(defaultTexture);
	}

	private class OverrideList extends ItemOverrides {
		@Override
		public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel worldIn, @Nullable LivingEntity entityIn, int p_173469_) {
			IIndividual individual = Objects.requireNonNull(IIndividualHandlerItem.getIndividual(stack));
			IGenome genome = individual.getGenome();
			IButterflySpecies species = genome.resolveActive(ButterflyChromosomes.SPECIES);
			float size = genome.getActiveValue(ButterflyChromosomes.SIZE);
			// should this be using the path? or the ID?
			try {
				return ButterflyItemModel.this.cache.get(FloatObjectPair.of(size, species.id().getPath()), () -> {
					ResourceLocation itemTexture = IForestryClientApi.INSTANCE.getButterflyManager().getTextures(species).getFirst();
					// todo include scale, otherwise having the float in the pair is useless
					return new SeparateTransformsModel.Baked(false, true, false, ResourceUtil.getMissingTexture(), ItemOverrides.EMPTY, new TRSRBakedModel(ButterflyItemModel.this.modelFor(itemTexture), 0, 0, 0, size), ImmutableMap.of());
				});
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public record Geometry(Collection<ResourceLocation> itemTextures,
							ResourceLocation defaultItemTexture) implements IUnbakedGeometry<Geometry> {
		@Override
		public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
			UnbakedModel modelButterfly = baker.getModel(ForestryConstants.forestry("item/butterfly_base"));

			if (!(modelButterfly instanceof BlockModel modelBlock)) {
				return null;
			}
			ResourceLocation parentLocation = modelBlock.getParentLocation();
			List<BlockElement> elements = modelBlock.getElements();
			ModelState transform = ResourceUtil.loadTransform(ForestryConstants.forestry("item/butterfly_base"));
			Map<ResourceLocation, BakedModel> subModelBuilder = new HashMap<>();

			for (ResourceLocation texture : this.itemTextures) {
				bakeInto(subModelBuilder, parentLocation, elements, modelBlock, baker, spriteGetter, transform, texture);
			}
			// ensure the default texture is baked even if no species registered an explicit texture pair
			bakeInto(subModelBuilder, parentLocation, elements, modelBlock, baker, spriteGetter, transform, this.defaultItemTexture);

			return new ButterflyItemModel(subModelBuilder);
		}

		private static void bakeInto(Map<ResourceLocation, BakedModel> baked, ResourceLocation parentLocation, List<BlockElement> elements, BlockModel modelBlock, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ResourceLocation texture) {
			if (baked.containsKey(texture)) {
				return;
			}
			BlockModel model = new BlockModel(parentLocation, elements, ImmutableMap.of("butterfly", Either.left(new Material(InventoryMenu.BLOCK_ATLAS, texture))), modelBlock.hasAmbientOcclusion, modelBlock.getGuiLight(), modelBlock.getTransforms(), modelBlock.getOverrides());
			baked.put(texture, model.bake(baker, model, spriteGetter, transform, true));
		}
	}

	public static class Loader implements IGeometryLoader<ButterflyItemModel.Geometry> {
		@Override
		public ButterflyItemModel.Geometry read(JsonObject modelContents, JsonDeserializationContext context) throws JsonParseException {
			// Built from the client-registered texture map's keys (never the live species list), so this stays
			// correct whether species are populated (pre-sync built-ins) or empty (post-sync-empty datapack state).
			IButterflyClientManager manager = IForestryClientApi.INSTANCE.getButterflyManager();
			Set<ResourceLocation> itemTextures = new HashSet<>();
			for (Pair<ResourceLocation, ResourceLocation> pair : manager.getAllTextures()) {
				itemTextures.add(pair.getFirst());
			}
			ResourceLocation defaultItemTexture = manager.getDefaultTextures().getFirst();
			return new ButterflyItemModel.Geometry(itemTextures, defaultItemTexture);
		}
	}
}
