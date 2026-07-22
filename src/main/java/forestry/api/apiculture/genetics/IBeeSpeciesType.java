package forestry.api.apiculture.genetics;

import com.mojang.authlib.GameProfile;
import forestry.api.apiculture.IActivityType;
import forestry.api.apiculture.IApiaristTracker;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.IFlowerType;
import forestry.api.genetics.ISpeciesType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;

// todo reimplement beekeeping mode
public interface IBeeSpeciesType extends ISpeciesType<IBeeSpecies, IBee> {
	/**
	 * @return {@link IApiaristTracker} associated with the passed world.
	 */
	@Override
	IApiaristTracker getBreedingTracker(LevelAccessor level, @Nullable GameProfile profile);

	/**
	 * @return The flower type registered with the given ID. Backs the {@code flower_type} reference chromosome.
	 */
	IFlowerType getFlowerType(ResourceLocation id);

	/**
	 * @return The flower type registered with the given ID, or {@code null} if none is registered (graceful fallback variant).
	 */
	@Nullable
	IFlowerType getFlowerTypeSafe(ResourceLocation id);

	/**
	 * @return The bee effect registered with the given ID. Backs the {@code bee_effect} reference chromosome.
	 */
	IBeeEffect getBeeEffect(ResourceLocation id);

	/**
	 * @return The activity type registered with the given ID. Backs the {@code activity} reference chromosome.
	 */
	IActivityType getActivityType(ResourceLocation id);

	/**
	 * @return The bee jubilance registered with the given ID. Backs the {@code jubilance} reference chromosome.
	 */
	IBeeJubilance getJubilance(ResourceLocation id);

	/**
	 * @return The bee jubilance registered with the given ID, or {@code null} if none is registered (graceful fallback variant).
	 */
	@Nullable
	IBeeJubilance getJubilanceSafe(ResourceLocation id);

	/**
	 * @return true if passed item is a drone. Equal to getLifeStage(ItemStack stack) == EnumBeeType.DRONE
	 */
	boolean isDrone(ItemStack stack);

	/**
	 * @return true if passed item is mated (i.e. a queen)
	 */
	boolean isMated(ItemStack stack);
}
