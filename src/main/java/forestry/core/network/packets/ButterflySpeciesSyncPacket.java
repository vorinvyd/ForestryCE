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

import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesManager;

/**
 * Server -&gt; client sync of the loaded butterfly species definitions, sent on player login/reload (see
 * {@code ModuleCore}'s {@code OnDatapackSyncEvent} listener). The client has no datapack access to
 * {@code butterfly_species} JSON, so this packet is its only source: {@link #handle} stores the definitions into the
 * client's {@link ButterflySpeciesManager} mirror and rebuilds the client-side species index, then (species-before-
 * mutations) the client-side mutation index from the already-synced
 * {@link net.minecraft.client.multiplayer.ClientPacketListener} recipe manager. Mirrors {@code TreeSpeciesSyncPacket}.
 * <p>
 * No separate trigger is needed to refresh the id-keyed butterfly client models (the {@code ButterflyItemModel}
 * bake introduced for reloadable species): {@code ButterflyClientManager#getTextures}/{@code #getDefaultTextures}
 * resolve textures by species id at render time, with a default-naming-convention fallback for any id that wasn't
 * explicitly registered/baked, so a datapack-added species renders correctly without rebaking. Item model rebaking
 * itself only ever happens on a client resource-pack reload, which is independent of this data sync. Mirrors how
 * {@code TreeSpeciesSyncPacket#handle} likewise does not trigger any extra sapling-model rebuild.
 * <p>
 * {@link ButterflySpeciesDefinition#streamCodec()} is looked up lazily inside {@link #encode}/{@link #decode} (never
 * cached in a static field here) since it is keyed against the butterfly karyotype, which does not exist until the
 * butterfly species type is registered - see {@link ButterflySpeciesDefinition}'s class doc.
 */
public record ButterflySpeciesSyncPacket(Map<ResourceLocation, ButterflySpeciesDefinition> definitions) implements CustomPacketPayload {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.BUTTERFLY_SPECIES_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, ButterflySpeciesSyncPacket msg) {
		definitionsStreamCodec().encode(buffer, msg.definitions);
	}

	public static ButterflySpeciesSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new ButterflySpeciesSyncPacket(definitionsStreamCodec().decode(buffer));
	}

	public static void handle(ButterflySpeciesSyncPacket msg, Player player) {
		// On an integrated server (single-player or LAN host) the client shares the very ButterflySpeciesType /
		// ButterflySpeciesManager singletons the server's datapack reload already populated authoritatively.
		// Re-applying the sync here would build a fresh set of ButterflySpecies objects and swap them in, transiently
		// desyncing the server's identity-keyed mutation index (MutationManager uses an IdentityHashMap keyed by the
		// species objects the server installed). Skip it - the shared state is already correct. A true remote client
		// has its own singletons and must apply the sync.
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		ButterflySpeciesManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildButterflySpecies(msg.definitions);
		GeneticsReloadHandler.rebuildMutations(Minecraft.getInstance().getConnection().getRecipeManager());
	}

	private static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, ButterflySpeciesDefinition>> definitionsStreamCodec() {
		return ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ButterflySpeciesDefinition.streamCodec());
	}
}
