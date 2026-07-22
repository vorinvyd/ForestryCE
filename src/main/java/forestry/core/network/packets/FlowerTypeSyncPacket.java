package forestry.core.network.packets;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.genetics.FlowerTypeManager;
import forestry.apiculture.genetics.FlowerTypeTypes;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;

/**
 * Server -&gt; client sync of the loaded flower-type definitions, sent on player login/reload (before
 * {@code BeeSpeciesSyncPacket}, so flower resolution is ready when genomes materialise). The client has no
 * datapack access to {@code flower_type} JSON, so this packet is its only source.
 */
public record FlowerTypeSyncPacket(Map<ResourceLocation, IFlowerType> definitions) implements CustomPacketPayload {
	private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, IFlowerType>> MAP_STREAM_CODEC =
		ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, FlowerTypeTypes.STREAM_CODEC);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.FLOWER_TYPE_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, FlowerTypeSyncPacket msg) {
		MAP_STREAM_CODEC.encode(buffer, msg.definitions);
	}

	public static FlowerTypeSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new FlowerTypeSyncPacket(MAP_STREAM_CODEC.decode(buffer));
	}

	public static void handle(FlowerTypeSyncPacket msg, Player player) {
		// Integrated server shares the server's already-authoritative singletons; re-applying is redundant.
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		FlowerTypeManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildFlowerTypes(msg.definitions);
	}
}
