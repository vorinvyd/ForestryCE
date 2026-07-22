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

import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;

/**
 * Server -&gt; client sync of the loaded tree species definitions, sent on player login/reload (see
 * {@code ModuleCore}'s {@code OnDatapackSyncEvent} listener). The client has no datapack access to
 * {@code tree_species} JSON, so this packet is its only source: {@link #handle} stores the definitions into the
 * client's {@link TreeSpeciesManager} mirror and rebuilds the client-side species index, then (species-before-
 * mutations) the client-side mutation index from the already-synced
 * {@link net.minecraft.client.multiplayer.ClientPacketListener} recipe manager. Mirrors
 * {@code BeeSpeciesSyncPacket}.
 * <p>
 * {@link TreeSpeciesDefinition#streamCodec()} is looked up lazily inside {@link #encode}/{@link #decode} (never
 * cached in a static field here) since it is keyed against the tree karyotype, which does not exist until the tree
 * species type is registered - see {@link TreeSpeciesDefinition}'s class doc.
 */
public record TreeSpeciesSyncPacket(Map<ResourceLocation, TreeSpeciesDefinition> definitions) implements CustomPacketPayload {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.TREE_SPECIES_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, TreeSpeciesSyncPacket msg) {
		definitionsStreamCodec().encode(buffer, msg.definitions);
	}

	public static TreeSpeciesSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new TreeSpeciesSyncPacket(definitionsStreamCodec().decode(buffer));
	}

	public static void handle(TreeSpeciesSyncPacket msg, Player player) {
		// On an integrated server (single-player or LAN host) the client shares the very TreeSpeciesType /
		// TreeSpeciesManager singletons the server's datapack reload already populated authoritatively. Re-applying
		// the sync here would build a fresh set of TreeSpecies objects and swap them in, transiently desyncing the
		// server's identity-keyed mutation index (MutationManager uses an IdentityHashMap keyed by the species
		// objects the server installed). Skip it - the shared state is already correct. A true remote client has
		// its own singletons and must apply the sync.
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		TreeSpeciesManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildTreeSpecies(msg.definitions);
		GeneticsReloadHandler.rebuildMutations(Minecraft.getInstance().getConnection().getRecipeManager());
	}

	private static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, TreeSpeciesDefinition>> definitionsStreamCodec() {
		return ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, TreeSpeciesDefinition.streamCodec());
	}
}
