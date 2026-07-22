package forestry.core.tiles;

import forestry.api.genetics.ISpeciesType;
import forestry.core.gui.ContainerNaturalistInventory;
import forestry.core.gui.IPagedInventory;
import forestry.core.inventory.InventoryNaturalistChest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class TileNaturalistChest extends TileBase implements IPagedInventory {
	private static final float lidAngleVariationPerTick = 0.1F;
	public static final VoxelShape CHEST_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

	private final ISpeciesType<?, ?> speciesType;
	public float lidAngle;
	public float prevLidAngle;
	private int numPlayersUsing;

	public TileNaturalistChest(BlockEntityType type, BlockPos pos, BlockState state, ISpeciesType<?, ?> speciesType) {
		super(type, pos, state);
		this.speciesType = speciesType;
		setInternalInventory(new InventoryNaturalistChest(this, speciesType));
	}

	public void increaseNumPlayersUsing() {
		if (this.numPlayersUsing == 0) {
			playLidSound(this.level, SoundEvents.CHEST_OPEN);
		}

		this.numPlayersUsing++;
		sendNetworkUpdate();
	}

	public void decreaseNumPlayersUsing() {
		this.numPlayersUsing--;
		if (this.numPlayersUsing < 0) {
			this.numPlayersUsing = 0;
		}
		if (this.numPlayersUsing == 0) {
			playLidSound(this.level, SoundEvents.CHEST_CLOSE);
		}
		sendNetworkUpdate();
	}

	@Override
	public void clientTick(Level level, BlockPos pos, BlockState state) {
		this.prevLidAngle = this.lidAngle;

		if (this.numPlayersUsing == 0 && this.lidAngle > 0.0F || this.numPlayersUsing > 0 && this.lidAngle < 1.0F) {
			if (this.numPlayersUsing > 0) {
				this.lidAngle += lidAngleVariationPerTick;
			} else {
				this.lidAngle -= lidAngleVariationPerTick;
			}

			this.lidAngle = Math.max(Math.min(this.lidAngle, 1), 0);
		}
	}

	private void playLidSound(Level level, SoundEvent sound) {
		level.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
	}

	@Override
	public void flipPage(ServerPlayer player, short page) {
		openMenu(player, page, true);
	}

	@Override
	public void openGui(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		openMenu(player, 0, false);
	}

	private void openMenu(ServerPlayer player, int page, boolean isFlipPage) {
		player.openMenu(new PagedMenuProvider(page, isFlipPage), p -> {
			p.writeBlockPos(this.worldPosition);
			p.writeVarInt(page);
			p.writeBoolean(isFlipPage);
		});
	}

	@Override
	public void writeData(FriendlyByteBuf data) {
		data.writeInt(this.numPlayersUsing);
	}

	@Override
	public void readData(FriendlyByteBuf data) {
		this.numPlayersUsing = data.readInt();
	}

	@Override
	public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
		// this is unused but return a default just in case
		return new ContainerNaturalistInventory(windowId, inv, this, 0, false);
	}

	public ISpeciesType<?, ?> getSpeciesType() {
		return this.speciesType;
	}

	// ensures ContainerNaturalistInventory.page is correct on the server side
	private class PagedMenuProvider implements MenuProvider {
		private final int page;
		private final boolean isFlipPage;

		private PagedMenuProvider(int page, boolean isFlipPage) {
			this.page = page;
			this.isFlipPage = isFlipPage;
		}

		@Override
		public Component getDisplayName() {
			return TileNaturalistChest.this.getDisplayName();
		}

		@Override
		public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
			return new ContainerNaturalistInventory(windowId, playerInv, TileNaturalistChest.this, this.page, this.isFlipPage);
		}

		@Override
		public boolean shouldTriggerClientSideContainerClosingOnOpen() {
			// Flipping a page reopens this menu server-side. The default (true) sends the client a
			// container-close packet first, which momentarily returns it to the in-game GUI and grabs
			// the mouse - warping the cursor to the window centre. Returning false skips that close so
			// the new page swaps in without moving the cursor.
			return !this.isFlipPage;
		}
	}
}
