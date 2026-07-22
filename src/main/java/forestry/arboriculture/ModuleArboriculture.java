package forestry.arboriculture;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import forestry.api.client.IClientModuleHandler;
import forestry.api.modules.ForestryModule;
import forestry.api.modules.ForestryModuleIds;
import forestry.api.modules.IPacketRegistry;
import forestry.arboriculture.client.ArboricultureClientHandler;
import forestry.arboriculture.commands.CommandTree;
import forestry.arboriculture.features.ArboricultureBlocks;
import forestry.arboriculture.features.ArboricultureEntities;
import forestry.arboriculture.features.ArboricultureItems;
import forestry.arboriculture.items.ForestryBoatDispenserBehavior;
import forestry.arboriculture.network.PacketRipeningUpdate;
import forestry.arboriculture.villagers.ArboricultureVillagers;
import forestry.core.network.PacketIdClient;
import forestry.modules.BlankForestryModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ForestryModule
public class ModuleArboriculture extends BlankForestryModule {
	@Override
	public ResourceLocation getId() {
		return ForestryModuleIds.ARBORICULTURE;
	}

	@Override
	public void registerEvents(IEventBus modBus) {
		NeoForge.EVENT_BUS.addListener(ArboricultureVillagers::villagerTrades);

		modBus.addListener(ModuleArboriculture::registerCapabilities);
		modBus.addListener(ModuleArboriculture::commonSetup);
		modBus.addListener(ModuleArboriculture::registerHangingSignBlocks);
		NeoForge.EVENT_BUS.addListener(ModuleArboriculture::modifySnifferLoot);
	}

	/**
	 * Adds Forestry's ceiling and wall hanging sign blocks to vanilla's
	 * {@link BlockEntityType#HANGING_SIGN} valid-blocks set. This is required because
	 * {@code HangingSignBlockEntity}'s public constructor hardcodes that vanilla type, so
	 * any Forestry-owned BlockEntityType for hanging signs would never be used at runtime
	 * (breaking save validation and tickers).
	 */
	private static void registerHangingSignBlocks(BlockEntityTypeAddBlocksEvent event) {
		Block[] hangingSignBlocks = Stream.concat(
			ArboricultureBlocks.HANGING_SIGN.getList().stream(),
			ArboricultureBlocks.WALL_HANGING_SIGN.getList().stream()
		).toArray(Block[]::new);
		event.modify(BlockEntityType.HANGING_SIGN, hangingSignBlocks);
	}

	private static void modifySnifferLoot(LootTableLoadEvent event) {
		if (event.getName().equals(BuiltInLootTables.SNIFFER_DIGGING)) {
			LootPool main = event.getTable().getPool("main");

			if (main != null) {
				List<LootPoolEntryContainer> entries = new ArrayList<>(main.entries);
				entries.add(LootItem.lootTableItem(ArboricultureItems.AMBER_SAPLING_FOSSIL).build());
				main.entries = entries;
			}
		}
	}

	@Override
	public void addToRootCommand(LiteralArgumentBuilder<CommandSourceStack> command) {
		command.then(CommandTree.register());
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerEntity(Capabilities.ItemHandler.ENTITY, ArboricultureEntities.CHEST_BOAT.entityType(), (boat, context) -> boat.isAlive() ? new InvWrapper(boat) : null);
	}

	private static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			for (ForestryWoodType type : ForestryWoodType.VALUES) {
				DispenserBlock.registerBehavior(ArboricultureItems.BOAT.item(type), new ForestryBoatDispenserBehavior(type, false));
				DispenserBlock.registerBehavior(ArboricultureItems.CHEST_BOAT.item(type), new ForestryBoatDispenserBehavior(type, true));
				WoodType.register(type.getWoodType());
			}
		});
	}

	@Override
	public void registerPackets(IPacketRegistry registry) {
		registry.clientbound(PacketIdClient.RIPENING_UPDATE, PacketRipeningUpdate::encode, PacketRipeningUpdate::decode, PacketRipeningUpdate::handle);
	}

	@Override
	public void registerClientHandler(Consumer<IClientModuleHandler> registrar) {
		registrar.accept(new ArboricultureClientHandler());
	}
}
