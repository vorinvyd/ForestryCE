package forestry.arboriculture.models;

import com.mojang.datafixers.util.Pair;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.client.IForestryClientApi;
import forestry.api.client.arboriculture.ITreeClientManager;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.arboriculture.tiles.TileSapling;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ModelSapling implements IUnbakedGeometry<ModelSapling> {
	@Override
	public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
		ITreeClientManager treeManager = IForestryClientApi.INSTANCE.getTreeManager();
		Map<ResourceLocation, BakedModel> baked = new HashMap<>();

		for (Pair<ResourceLocation, ResourceLocation> pair : treeManager.getAllSaplingModels()) {
			bakeInto(baked, baker, spriteGetter, pair.getFirst());
			bakeInto(baked, baker, spriteGetter, pair.getSecond());
		}
		// ensure the default pair is baked even if no species registered a model
		Pair<ResourceLocation, ResourceLocation> def = treeManager.getDefaultSaplingModels();
		bakeInto(baked, baker, spriteGetter, def.getFirst());
		bakeInto(baked, baker, spriteGetter, def.getSecond());

		return new Baked(treeManager, baked);
	}

	private static void bakeInto(Map<ResourceLocation, BakedModel> baked, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation location) {
		if (!baked.containsKey(location)) {
			BakedModel model = baker.bake(location, BlockModelRotation.X0_Y0, spriteGetter);
			if (model != null) {
				baked.put(location, model);
			}
		}
	}

	public static class Baked implements BakedModel {
		private final ITreeClientManager treeManager;
		private final Map<ResourceLocation, BakedModel> baked;
		@Nullable
		private ItemOverrides overrideList;

		public Baked(ITreeClientManager treeManager, Map<ResourceLocation, BakedModel> baked) {
			this.treeManager = treeManager;
			this.baked = baked;
		}

		private BakedModel blockModelFor(@Nullable ITreeSpecies species) {
			Pair<ResourceLocation, ResourceLocation> pair = species != null ? this.treeManager.getSaplingModels(species) : this.treeManager.getDefaultSaplingModels();
			BakedModel model = this.baked.get(pair.getFirst());
			return model != null ? model : this.baked.get(this.treeManager.getDefaultSaplingModels().getFirst());
		}

		private BakedModel itemModelFor(ITreeSpecies species) {
			Pair<ResourceLocation, ResourceLocation> pair = this.treeManager.getSaplingModels(species);
			BakedModel model = this.baked.get(pair.getSecond());
			return model != null ? model : this.baked.get(this.treeManager.getDefaultSaplingModels().getSecond());
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
			return blockModelFor(extraData.get(TileSapling.TREE_SPECIES)).getQuads(state, side, rand);
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
			return getQuads(state, side, rand, ModelData.EMPTY, null);
		}

		@Override public boolean useAmbientOcclusion() { return blockModelFor(null).useAmbientOcclusion(); }
		@Override public boolean isGui3d() { return blockModelFor(null).isGui3d(); }
		@Override public boolean usesBlockLight() { return false; }
		@Override public boolean isCustomRenderer() { return false; }
		@Override public TextureAtlasSprite getParticleIcon() { return blockModelFor(null).getParticleIcon(); }

		@Override
		public TextureAtlasSprite getParticleIcon(ModelData data) {
			return blockModelFor(data.get(TileSapling.TREE_SPECIES)).getParticleIcon();
		}

		@Override
		public ItemOverrides getOverrides() {
			if (this.overrideList == null) {
				this.overrideList = new OverrideList();
			}
			return this.overrideList;
		}

		public class OverrideList extends ItemOverrides {
			@Nullable
			@Override
			public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
				IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
				if (individual == null) {
					return model;
				}
				return itemModelFor((ITreeSpecies) individual.getSpecies());
			}
		}
	}
}
