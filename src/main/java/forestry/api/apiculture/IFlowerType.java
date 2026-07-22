package forestry.api.apiculture;

import forestry.api.genetics.IIndividual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public interface IFlowerType {
	/**
	 * @return Whether the allele for this value is dominant or recessive.
	 */
	boolean isDominant();

	/**
	 * Checks if the flower at the specified position is accepted by this rule.
	 */
	boolean isAcceptableFlower(Level level, BlockPos pos);

	/**
	 * Tries to plant a random flower at a position near a hive.
	 *
	 * @param level         The current dimension of the hive.
	 * @param pos           The position to try to plant at.
	 * @param nearbyFlowers The list of flowers already near the hive.
	 * @return {@code true} if the flower was planted.
	 */
	boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers);

	/**
	 * Allows the flower provider to affect the produce at the given location.
	 * If this flowerProvider does not affect the products, it should return the products unchanged.
	 */
	default List<ItemStack> affectProducts(Level level, BlockPos pos, IIndividual individual, List<ItemStack> products) {
		return products;
	}

	/**
	 * @return The serializer for this flower type, used to encode it for datapacks/network. Only serializable
	 * (datapack-backed or synced) flower types override this; purely code-registered types that hold behaviour
	 * as lambdas (e.g. KubeJS) are never serialized and keep the throwing default.
	 */
	default FlowerTypeType<?> type() {
		throw new UnsupportedOperationException(getClass().getName() + " is not a serializable flower type");
	}
}
