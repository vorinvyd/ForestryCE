package forestry.factory.recipes;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.core.FluidProductType;
import forestry.api.core.IFluidProduct;
import forestry.api.recipes.ISqueezerContainerRecipe;
import forestry.factory.features.FactoryRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class SqueezerContainerRecipe implements ISqueezerContainerRecipe {
	// Container recipes have no fluid output at all; this is never serialized (there is no "output" codec field
	// below), so createFluidStack() being EMPTY is enough to make TileSqueezer refuse a fluid-fill for these recipes.
	private static final IFluidProduct NO_FLUID_OUTPUT = new IFluidProduct() {
		@Override
		public FluidStack createFluidStack() {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidProductType<?> type() {
			throw new UnsupportedOperationException("SqueezerContainerRecipe's fluid output is never serialized");
		}
	};

	private static final MapCodec<SqueezerContainerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ResourceLocation.CODEC.fieldOf("id").forGetter(SqueezerContainerRecipe::getId),
		ItemStack.STRICT_CODEC.fieldOf("container").forGetter(SqueezerContainerRecipe::getEmptyContainer),
		Codec.INT.fieldOf("time").forGetter(SqueezerContainerRecipe::getProcessingTime),
		ItemStack.STRICT_CODEC.fieldOf("remnants").forGetter(SqueezerContainerRecipe::getRemnants),
		Codec.FLOAT.fieldOf("remnantsChance").forGetter(SqueezerContainerRecipe::getRemnantsChance)
	).apply(instance, SqueezerContainerRecipe::new));
	private static final StreamCodec<RegistryFriendlyByteBuf, SqueezerContainerRecipe> STREAM_CODEC = StreamCodec.of(
		Serializer::toNetwork,
		Serializer::fromNetwork
	);

	private final ResourceLocation id;
	private final ItemStack emptyContainer;
	private final int processingTime;
	private final ItemStack remnants;
	private final float remnantsChance;

	public SqueezerContainerRecipe(ResourceLocation id, ItemStack emptyContainer, int processingTime, ItemStack remnants, float remnantsChance) {
		this.id = id;
		Preconditions.checkNotNull(emptyContainer);
		Preconditions.checkArgument(!emptyContainer.isEmpty());
		Preconditions.checkNotNull(remnants);

		this.emptyContainer = emptyContainer;
		this.processingTime = processingTime;
		this.remnants = remnants;
		this.remnantsChance = remnantsChance;
	}

	@Override
	public ItemStack getEmptyContainer() {
		return this.emptyContainer;
	}

	@Override
	public List<Ingredient> getInputs() {
		return List.of();
	}

	@Override
	public int getProcessingTime() {
		return this.processingTime;
	}

	@Override
	public ItemStack getRemnants() {
		return this.remnants;
	}

	@Override
	public float getRemnantsChance() {
		return this.remnantsChance;
	}

	@Override
	public IFluidProduct getFluidOutput() {
		return NO_FLUID_OUTPUT;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider lookupProvider) {
		return this.remnants;
	}

	@Override
	public ResourceLocation getId() {
		return this.id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return FactoryRecipeTypes.SQUEEZER_CONTAINER.serializer();
	}

	@Override
	public RecipeType<?> getType() {
		return FactoryRecipeTypes.SQUEEZER_CONTAINER.type();
	}

	public static class Serializer implements RecipeSerializer<SqueezerContainerRecipe> {
		@Override
		public MapCodec<SqueezerContainerRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, SqueezerContainerRecipe> streamCodec() {
			return STREAM_CODEC;
		}

		private static SqueezerContainerRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
			ResourceLocation recipeId = ResourceLocation.STREAM_CODEC.decode(buffer);
			ItemStack emptyContainer = ItemStack.STREAM_CODEC.decode(buffer);
			int processingTime = ByteBufCodecs.VAR_INT.decode(buffer);
			ItemStack remnants = ItemStack.STREAM_CODEC.decode(buffer);
			float remnantsChance = ByteBufCodecs.FLOAT.decode(buffer);

			return new SqueezerContainerRecipe(recipeId, emptyContainer, processingTime, remnants, remnantsChance);
		}

		private static void toNetwork(RegistryFriendlyByteBuf buffer, SqueezerContainerRecipe recipe) {
			ResourceLocation.STREAM_CODEC.encode(buffer, recipe.id);
			ItemStack.STREAM_CODEC.encode(buffer, recipe.emptyContainer);
			ByteBufCodecs.VAR_INT.encode(buffer, recipe.processingTime);
			ItemStack.STREAM_CODEC.encode(buffer, recipe.remnants);
			ByteBufCodecs.FLOAT.encode(buffer, recipe.remnantsChance);
		}
	}
}
