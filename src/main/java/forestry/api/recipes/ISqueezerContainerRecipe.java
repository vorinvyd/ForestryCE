package forestry.api.recipes;

import net.minecraft.world.item.ItemStack;

/**
 * A container-draining squeezer recipe (e.g. squeezing a lava bucket for the empty bucket). These recipes have no
 * fluid output of their own: the inherited {@link #getFluidOutput()} returns a display/gate-only product whose
 * {@link forestry.api.core.IFluidProduct#createFluidStack() createFluidStack()} is empty. That product is never
 * serialized (this recipe's codec has no {@code "output"} field), so its
 * {@link forestry.api.core.IFluidProduct#type() type()} is unsupported — do not call it.
 */
public interface ISqueezerContainerRecipe extends IForestryRecipe, ISqueezerRecipe {
	ItemStack getEmptyContainer();

	int getProcessingTime();

	ItemStack getRemnants();

	float getRemnantsChance();
}
