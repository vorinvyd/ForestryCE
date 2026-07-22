package forestry.apiculture.models;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.client.IForestryClientApi;
import forestry.api.client.apiculture.IBeeClientManager;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ILifeStage;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

// The item model for a bee, which supports custom bee textures
// Only supports the four life stages used in base Forestry (DRONE, LARVAE, PRINCESS, QUEEN)
// If you have a custom life stage, you'll need to implement your own model code
public class ModelBee implements IUnbakedGeometry<ModelBee> {
	private final ILifeStage stage;

	public ModelBee(ILifeStage stage) {
		this.stage = stage;
	}

	@Override
	public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
		IBeeClientManager manager = IForestryClientApi.INSTANCE.getBeeManager();
		Map<ResourceLocation, BakedModel> bakedModels = new HashMap<>();

		for (ResourceLocation location : manager.getAllModelLocations(this.stage)) {
			BakedModel model = baker.bake(location, BlockModelRotation.X0_Y0, spriteGetter);

			if (model != null) {
				bakedModels.put(location, model);
			}
		}

		return new ModelBee.Baked(this.stage, manager, bakedModels);
	}

	public static class Loader implements IGeometryLoader<ModelBee> {
		private final ModelBee[] models = new ModelBee[BeeLifeStage.values().length];

		@Override
		public ModelBee read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
			String stageName = GsonHelper.getAsString(jsonObject, "stage");
			BeeLifeStage stage = BeeLifeStage.valueOf(stageName.toUpperCase(Locale.ENGLISH));
			int ordinal = stage.ordinal();

			if (this.models[ordinal] == null) {
				this.models[ordinal] = new ModelBee(stage);
			}

			return this.models[ordinal];
		}
	}

	private static class Baked implements BakedModel {
		private final ILifeStage stage;
		private final IBeeClientManager manager;
		private final Map<ResourceLocation, BakedModel> bakedModels;

		public Baked(ILifeStage stage, IBeeClientManager manager, Map<ResourceLocation, BakedModel> bakedModels) {
			this.stage = stage;
			this.manager = manager;
			this.bakedModels = bakedModels;
		}

		@Override
		public List<BakedQuad> getQuads(@Nullable BlockState pState, @Nullable Direction pDirection, RandomSource pRandom) {
			return List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return false;
		}

		@Override
		public boolean isGui3d() {
			return false;
		}

		@Override
		public boolean usesBlockLight() {
			return false;
		}

		@Override
		public boolean isCustomRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleIcon() {
			return null;
		}

		@Override
		public ItemOverrides getOverrides() {
			return new OverrideList();
		}

		public class OverrideList extends ItemOverrides {
			@Override
			public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int p_173469_) {
				IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
				if (individual == null) {
					return model;
				}

				ResourceLocation speciesId = individual.getSpecies().id();
				ResourceLocation location = Baked.this.manager.getModelLocation(Baked.this.stage, speciesId);
				BakedModel resolved = Baked.this.bakedModels.get(location);

				if (resolved != null) {
					return resolved;
				}

				// Fall back to the default model for this life stage, then the vanilla-provided model.
				ResourceLocation defaultLocation = Baked.this.manager.getDefaultModelLocation(Baked.this.stage);
				return Baked.this.bakedModels.getOrDefault(defaultLocation, model);
			}
		}
	}
}
