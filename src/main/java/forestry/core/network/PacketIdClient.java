package forestry.core.network;

import forestry.apiculture.network.packets.PacketAlvearyChange;
import forestry.apiculture.network.packets.PacketBeeLogicActive;
import forestry.apiculture.network.packets.PacketHabitatBiomePointer;
import forestry.arboriculture.network.PacketRipeningUpdate;
import forestry.core.network.packets.BeeSpeciesSyncPacket;
import forestry.core.network.packets.ButterflySpeciesSyncPacket;
import forestry.core.network.packets.BeeEffectSyncPacket;
import forestry.core.network.packets.FlowerTypeSyncPacket;
import forestry.core.network.packets.TaxonSyncPacket;
import forestry.core.network.packets.TreeSpeciesSyncPacket;
import forestry.core.network.packets.PacketActiveUpdate;
import forestry.core.network.packets.PacketErrorUpdate;
import forestry.core.network.packets.PacketGenomeTrackerSync;
import forestry.core.network.packets.PacketGuiEnergy;
import forestry.core.network.packets.PacketGuiLayoutSelect;
import forestry.core.network.packets.PacketGuiStream;
import forestry.core.network.packets.PacketItemStackDisplay;
import forestry.core.network.packets.PacketSocketUpdate;
import forestry.core.network.packets.PacketTankLevelUpdate;
import forestry.core.network.packets.PacketTileStream;
import forestry.core.network.packets.RecipeCachePacket;
import forestry.core.network.packets.PacketRefractoryWax;
import forestry.factory.network.packets.PacketRecipeTransferUpdate;
import forestry.mail.network.packets.PacketLetterInfoResponsePlayer;
import forestry.mail.network.packets.PacketLetterInfoResponseTrader;
import forestry.mail.network.packets.PacketPOBoxInfoResponse;
import forestry.mail.network.packets.PacketTraderAddressResponse;
import forestry.sorting.network.packets.PacketGuiFilterUpdate;
import forestry.worktable.network.packets.PacketWorktableMemoryUpdate;
import forestry.worktable.network.packets.PacketWorktableRecipeUpdate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import static forestry.core.network.PacketIdServer.type;

/**
 * Packets sent to the client from the server
 */
public class PacketIdClient {
	// Core
	public static final CustomPacketPayload.Type<RecipeCachePacket> RECIPE_CACHE = type("recipe_cache");
	// Core Gui
	public static final CustomPacketPayload.Type<PacketErrorUpdate> ERROR_UPDATE = type("error_update");
	public static final CustomPacketPayload.Type<PacketGuiStream> GUI_UPDATE = type("gui_update");
	public static final CustomPacketPayload.Type<PacketGuiLayoutSelect> GUI_LAYOUT_SELECT = type("gui_layout_select");
	public static final CustomPacketPayload.Type<PacketGuiEnergy> GUI_ENERGY = type("gui_energy");
	public static final CustomPacketPayload.Type<PacketSocketUpdate> SOCKET_UPDATE = type("socket_update");
	// Core Tile Entities
	public static final CustomPacketPayload.Type<PacketTileStream> TILE_FORESTRY_UPDATE = type("tile_forestry_update");
	public static final CustomPacketPayload.Type<PacketItemStackDisplay> ITEMSTACK_DISPLAY = type("itemstack_display");
	public static final CustomPacketPayload.Type<PacketTankLevelUpdate> TANK_LEVEL_UPDATE = type("tank_level_update");
	public static final CustomPacketPayload.Type<PacketRefractoryWax> REFRACTORY_WAX_ON = type("refractory_wax_on");
	// Core Genome
	public static final CustomPacketPayload.Type<PacketGenomeTrackerSync> GENOME_TRACKER_UPDATE = type("genome_tracker_update");
	// Factory
	public static final CustomPacketPayload.Type<PacketWorktableMemoryUpdate> WORKTABLE_MEMORY_UPDATE = type("worktable_memory_update");
	public static final CustomPacketPayload.Type<PacketWorktableRecipeUpdate> WORKTABLE_CRAFTING_UPDATE = type("worktable_crafting_update");
	// Apiculture
	public static final CustomPacketPayload.Type<PacketActiveUpdate> TILE_FORESTRY_ACTIVE = type("tile_forestry_active");
	public static final CustomPacketPayload.Type<PacketBeeLogicActive> BEE_LOGIC_ACTIVE = type("bee_logic_active");
	public static final CustomPacketPayload.Type<PacketHabitatBiomePointer> HABITAT_BIOME_POINTER = type("habitat_biome_pointer");
	public static final CustomPacketPayload.Type<PacketAlvearyChange> ALVEARY_CONTROLLER_CHANGE = type("alveary_controller_change");
	public static final CustomPacketPayload.Type<BeeSpeciesSyncPacket> BEE_SPECIES_SYNC = type("bee_species_sync");
	public static final CustomPacketPayload.Type<FlowerTypeSyncPacket> FLOWER_TYPE_SYNC = type("flower_type_sync");
	public static final CustomPacketPayload.Type<BeeEffectSyncPacket> BEE_EFFECT_SYNC = type("bee_effect_sync");
	public static final CustomPacketPayload.Type<TaxonSyncPacket> TAXON_SYNC = type("taxon_sync");
	// Arboriculture
	public static final CustomPacketPayload.Type<PacketRipeningUpdate> RIPENING_UPDATE = type("ripening_update");
	public static final CustomPacketPayload.Type<TreeSpeciesSyncPacket> TREE_SPECIES_SYNC = type("tree_species_sync");
	// Lepidopterology
	public static final CustomPacketPayload.Type<ButterflySpeciesSyncPacket> BUTTERFLY_SPECIES_SYNC = type("butterfly_species_sync");
	// Mail
	public static final CustomPacketPayload.Type<PacketTraderAddressResponse> TRADING_ADDRESS_RESPONSE = type("trading_address_response");
	public static final CustomPacketPayload.Type<PacketLetterInfoResponsePlayer> LETTER_INFO_RESPONSE_PLAYER = type("letter_info_response_player");
	public static final CustomPacketPayload.Type<PacketLetterInfoResponseTrader> LETTER_INFO_RESPONSE_TRADER = type("letter_info_response_trader");
	public static final CustomPacketPayload.Type<PacketPOBoxInfoResponse> POBOX_INFO_RESPONSE = type("pobox_info_response");
	// Sorting
	public static final CustomPacketPayload.Type<PacketGuiFilterUpdate> GUI_UPDATE_FILTER = type("gui_update_filter");
	// JEI
	public static final CustomPacketPayload.Type<PacketRecipeTransferUpdate> RECIPE_TRANSFER_UPDATE = type("recipe_transfer_update");
}
