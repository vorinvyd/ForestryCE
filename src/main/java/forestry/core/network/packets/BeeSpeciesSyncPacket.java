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

import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;

/**
 * Server -&gt; client sync of the loaded bee species definitions, sent on player login/reload (see
 * {@code ModuleCore}'s {@code OnDatapackSyncEvent} listener). The client has no datapack access to {@code bee_species}
 * JSON, so this packet is its only source: {@link #handle} stores the definitions into the client's
 * {@link BeeSpeciesManager} mirror and rebuilds the client-side species index, then (species-before-mutations) the
 * client-side mutation index from the already-synced {@link net.minecraft.client.multiplayer.ClientPacketListener}
 * recipe manager.
 * <p>
 * {@link BeeSpeciesDefinition#streamCodec()} is looked up lazily inside {@link #encode}/{@link #decode} (never
 * cached in a static field here) since it is keyed against the bee karyotype, which does not exist until the bee
 * species type is registered - see {@link BeeSpeciesDefinition}'s class doc.
 */
public record BeeSpeciesSyncPacket(Map<ResourceLocation, BeeSpeciesDefinition> definitions) implements CustomPacketPayload {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.BEE_SPECIES_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, BeeSpeciesSyncPacket msg) {
		definitionsStreamCodec().encode(buffer, msg.definitions);
	}

	public static BeeSpeciesSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new BeeSpeciesSyncPacket(definitionsStreamCodec().decode(buffer));
	}

	public static void handle(BeeSpeciesSyncPacket msg, Player player) {
		// On an integrated server (single-player or LAN host) the client shares the very BeeSpeciesType /
		// BeeSpeciesManager singletons the server's datapack reload already populated authoritatively. Re-applying the
		// sync here would build a fresh set of BeeSpecies objects and swap them in, transiently desyncing the server's
		// identity-keyed mutation index (MutationManager uses an IdentityHashMap keyed by the species objects the
		// server installed). Skip it - the shared state is already correct. A true remote client has its own
		// singletons and must apply the sync.
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		BeeSpeciesManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildSpecies(msg.definitions);
		GeneticsReloadHandler.rebuildMutations(Minecraft.getInstance().getConnection().getRecipeManager());
	}

	private static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, BeeSpeciesDefinition>> definitionsStreamCodec() {
		return ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, BeeSpeciesDefinition.streamCodec());
	}
}
