package forestry.api.core;

import com.google.common.base.Preconditions;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Default implementation of {@link IFluidProduct}: a fixed fluid output. Used by all of Forestry's own squeezer
 * recipes. Wraps a {@link FluidStack} so fluid, amount, and data components are captured together.
 *
 * @param stack The fluid stack this product always produces. Must not be empty.
 */
public record FluidProduct(FluidStack stack) implements IFluidProduct {
	public FluidProduct {
		Preconditions.checkNotNull(stack);
		Preconditions.checkArgument(!stack.isEmpty(), "FluidProduct stack must not be empty");
	}

	public static final MapCodec<FluidProduct> MAP_CODEC = FluidStack.CODEC.fieldOf("stack").xmap(FluidProduct::new, FluidProduct::stack);
	public static final StreamCodec<RegistryFriendlyByteBuf, FluidProduct> STREAM_CODEC = FluidStack.STREAM_CODEC.map(FluidProduct::new, FluidProduct::stack);
	/**
	 * The default product type. The dispatch codec in {@code forestry.core.FluidProductTypes} treats this type
	 * specially: products of this type serialize without a {@code "type"} key, and a missing {@code "type"} key on
	 * decode resolves back to it.
	 */
	public static final FluidProductType<FluidProduct> TYPE = new FluidProductType<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public FluidStack createFluidStack() {
		return this.stack.copy();
	}

	@Override
	public FluidStack createRandomFluidStack(RandomSource random) {
		return this.stack.copy();
	}

	@Override
	public FluidProductType<?> type() {
		return TYPE;
	}

	public static FluidProduct of(FluidStack stack) {
		return new FluidProduct(stack);
	}

	public static FluidProduct of(Fluid fluid, int amount) {
		return new FluidProduct(new FluidStack(fluid, amount));
	}
}
