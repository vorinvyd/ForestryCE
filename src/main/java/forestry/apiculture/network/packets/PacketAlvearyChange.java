package forestry.apiculture.network.packets;

import forestry.core.multiblock.MultiblockValidation;
import forestry.core.network.PacketIdClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record PacketAlvearyChange(BlockPos pos) implements CustomPacketPayload {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.ALVEARY_CONTROLLER_CHANGE;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, PacketAlvearyChange msg) {
		buffer.writeBlockPos(msg.pos);
	}

	public static PacketAlvearyChange decode(RegistryFriendlyByteBuf buffer) {
		return new PacketAlvearyChange(buffer.readBlockPos());
	}

	public static void handle(PacketAlvearyChange msg, Player player) {
		// Client-side re-validation (spec §5.3, §9): refresh the client's assembled state + entrance textures.
		MultiblockValidation.validateAt(player.level(), msg.pos);
	}
}
