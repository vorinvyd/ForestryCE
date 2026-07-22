package forestry.api.multiblock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * Used for testing the new multiblock engine without coupling tests to either the old BigReactors implementation
 * or this new Forestry-specific implementation. Exposes the inventory contents of a multiblock to allow checking
 * for data loss/duplication.
 *
 * @see forestry.api.multiblock.IMultiblockController
 */
@ApiStatus.Internal
public interface IMultiblockInventoryProbe {
	/**
	 * @return A deep copy of this multiblock's current inventory contents.
	 */
	List<ItemStack> snapshotSharedInventory();

	/**
	 * Deep-copies inventory contents into a list.
	 */
	static List<ItemStack> snapshotContainer(Container container) {
		List<ItemStack> snapshot = new ArrayList<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				snapshot.add(stack.copy());
			}
		}
		return snapshot;
	}
}
