package forestry.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import forestry.api.ForestryCapabilities;
import forestry.api.ForestryConstants;
import forestry.api.ForestryRegistries;
import forestry.api.IForestryApi;
import forestry.api.client.IClientModuleHandler;
import forestry.api.modules.ForestryModule;
import forestry.api.modules.ForestryModuleIds;
import forestry.api.modules.IForestryModule;
import forestry.api.modules.IPacketRegistry;
import forestry.apiculture.genetics.BeeSpeciesManager;
import forestry.apiculture.genetics.BeeEffectManager;
import forestry.apiculture.genetics.FlowerTypeManager;
import forestry.apiculture.genetics.TaxonManager;
import forestry.apiimpl.plugin.PluginManager;
import forestry.arboriculture.genetics.TreeSpeciesManager;
import forestry.arboriculture.loot.GrafterLootModifier;
import forestry.core.blocks.TileStreamUpdateTracker;
import forestry.core.client.CoreClientHandler;
import forestry.core.climate.ForestryClimateManager;
import forestry.core.commands.DiagnosticsCommand;
import forestry.core.commands.DumpCommand;
import forestry.core.features.CoreItems;
import forestry.core.features.CoreTiles;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.items.ItemPipette;
import forestry.core.items.ItemSpectacles;
import forestry.core.loot.ConditionLootModifier;
import forestry.core.network.PacketIdClient;
import forestry.core.network.PacketIdServer;
import forestry.core.network.packets.*;
import forestry.core.owner.GameProfileDataSerializer;
import forestry.core.recipes.RecipeManagers;
import forestry.core.utils.NetworkUtil;
import forestry.lepidopterology.genetics.ButterflySpeciesManager;
import forestry.modules.BlankForestryModule;
import forestry.modules.ForestryModuleManager;
import forestry.modules.ModuleUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.function.Consumer;

@ForestryModule
public class ModuleCore extends BlankForestryModule {
	@Override
	public ResourceLocation getId() {
		return ForestryModuleIds.CORE;
	}

	private static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS =
		DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, ForestryConstants.MOD_ID);

	static {
		ENTITY_DATA_SERIALIZERS.register("game_profile", () -> GameProfileDataSerializer.INSTANCE);
	}

	@Override
	public void registerEvents(IEventBus modBus) {
		ENTITY_DATA_SERIALIZERS.register(modBus);
		modBus.addListener(ModuleCore::onCommonSetup);
		modBus.addListener(ModuleCore::registerCapabilities);
		modBus.addListener(ModuleCore::registerGlobalLootModifiers);
		modBus.addListener(ModuleCore::registerForestryRegistries);
		modBus.addListener(ModuleCore::onGatherData);

		ModuleUtil.loadFeatureProviders();
		NeoForge.EVENT_BUS.addListener(ModuleCore::onItemPickup);
		NeoForge.EVENT_BUS.addListener(ModuleCore::onLevelTick);
		NeoForge.EVENT_BUS.addListener(ModuleCore::onTagsUpdated);
		NeoForge.EVENT_BUS.addListener(ModuleCore::registerReloadListeners);
		NeoForge.EVENT_BUS.addListener(ModuleCore::registerCommands);
		NeoForge.EVENT_BUS.addListener(ModuleCore::onDatapackSync);

		PluginManager.registerAsyncException(modBus);
	}

	private static void onCommonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(ModuleCore::ensureApiInitialized);
	}

	private static void registerForestryRegistries(NewRegistryEvent event) {
		event.register(ForestryRegistries.CIRCUIT);
		event.register(ForestryRegistries.POSTAL_CARRIER);
		event.register(ForestryRegistries.SPECIES_TYPE);
		event.register(ForestryRegistries.BEE_EFFECT_TYPE);
	}

	private static void onGatherData(net.neoforged.neoforge.data.event.GatherDataEvent event) {
		// Datagen skips FMLCommonSetupEvent, so initialize the API here so data
		// providers can resolve TreeManager / BeeManager / ButterflyManager.
		ensureApiInitialized();
	}

	private static volatile boolean apiInitialized = false;

	/**
	 * Idempotent bootstrap of Forestry's runtime API. Some client-side events
	 * (e.g. ModelEvent.RegisterGeometryLoaders) fire before FMLCommonSetupEvent
	 * is processed and need TreeManager / BeeManager / etc. already wired up.
	 * Safe to call from any post-RegisterEvent context.
	 */
	public static synchronized void ensureApiInitialized() {
		if (apiInitialized) {
			return;
		}
		PluginManager.registerCircuits();
		postItemRegistry();
		((ForestryModuleManager) IForestryApi.INSTANCE.getModuleManager()).setupApi();
		apiInitialized = true;
	}

	private static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerItem(ForestryCapabilities.SPECTACLE_VISION, (stack, context) -> ItemSpectacles.SPECTACLE_VISION, CoreItems.SPECTACLES.item());
		event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> ((ItemPipette) stack.getItem()).createFluidHandler(stack), CoreItems.PIPETTE.item());
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreTiles.ANALYZER.tileType(), (tile, side) -> tile.getItemHandler(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreTiles.ESCRITOIRE.tileType(), (tile, side) -> tile.getItemHandler(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreTiles.APIARIST_CHEST.tileType(), (tile, side) -> tile.getItemHandler(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreTiles.ARBORIST_CHEST.tileType(), (tile, side) -> tile.getItemHandler(side));
		event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CoreTiles.LEPIDOPTERIST_CHEST.tileType(), (tile, side) -> tile.getItemHandler(side));
		event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CoreTiles.ANALYZER.tileType(), (tile, side) -> tile.getEnergyHandler(side));
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CoreTiles.ANALYZER.tileType(), (tile, side) -> tile.getTankManager());
	}

	private static void registerGlobalLootModifiers(RegisterEvent event) {
		event.register(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, helper -> {
			helper.register(ForestryConstants.forestry("condition_modifier"), ConditionLootModifier.CODEC);
			helper.register(ForestryConstants.forestry("grafter_modifier"), GrafterLootModifier.CODEC);
		});
	}

	private static void postItemRegistry() {
		PluginManager.registerGenetics();
		PluginManager.registerFarming();
		PluginManager.registerPollen();
	}

	private static void onItemPickup(ItemEntityPickupEvent.Post event) {
		PickupHandlerCore.onItemPickup(event.getPlayer(), event.getItemEntity());
	}

	private static void onLevelTick(LevelTickEvent.Post event) {
		var server = event.getLevel().getServer();
		if (server != null) {
			TileStreamUpdateTracker.syncVisualUpdates(server);
		}
	}

	private static void onTagsUpdated(TagsUpdatedEvent event) {
		if (event.shouldUpdateStaticData()) {
			event.getRegistryAccess().registry(Registries.BIOME).ifPresent(registry -> ((ForestryClimateManager) IForestryApi.INSTANCE.getClimateManager()).onBiomesReloaded(registry));
		}
	}

	private static void registerReloadListeners(AddReloadListenerEvent event) {
		event.addListener((prepBarrier, resourceManager, prepProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
			return prepBarrier.wait(Unit.INSTANCE).thenRunAsync(() -> {
				RecipeManagers.invalidateCaches();
				NetworkUtil.sendToAllPlayers(new RecipeCachePacket());
			});
		});

		// Load flower types from the "flower_type" datapack folder and install the code-base union datapack map
		// into the live bee species type. The FLOWER_TYPE chromosome resolves ids lazily via
		// BeeSpeciesType#getFlowerType, so strict ordering against BeeSpeciesManager isn't required today, but this
		// is registered immediately before it to keep the "referenced data before dependent data" convention used
		// by the other reload listeners below.
		event.addListener(forestry.apiculture.genetics.FlowerTypeManager.INSTANCE);

		// Load bee effects from the "bee_effect" folder and install the code-base union datapack map into the live bee
		// species type. Registered before BeeSpeciesManager: species projection resolves each genome's bee_effect
		// reference via getBeeEffect, so effects must exist first.
		event.addListener(BeeEffectManager.INSTANCE);

		// Load datapack taxa from the "taxon" folder and merge them onto the code-registered taxonomy. Registered
		// before BeeSpeciesManager: a species' genus is resolved to a taxon as it is projected, so taxa must exist first.
		event.addListener(TaxonManager.INSTANCE);

		// Load bee species from the "bee_species" datapack folder and rebuild the live species map from them.
		// SimpleJsonResourceReloadListener#apply already runs on the game executor (see
		// SimplePreparableReloadListener#reload: prepare() -> prepBarrier.wait() -> apply() via thenAcceptAsync(...,
		// gameExecutor)), so no extra marshalling onto gameExecutor is needed here. Registered before the mutation
		// listener below: apply order follows registration order, and mutations must resolve species that already
		// exist in the live map.
		event.addListener(BeeSpeciesManager.INSTANCE);

		// Load tree species from the "tree_species" datapack folder and rebuild the live species map from them.
		// Registered right after BeeSpeciesManager and, like it, before the mutation listener below: apply order
		// follows registration order, and mutations must resolve species that already exist in the live map.
		event.addListener(TreeSpeciesManager.INSTANCE);

		// Load butterfly species from the "butterfly_species" datapack folder and rebuild the live species map from
		// them. Registered right after TreeSpeciesManager and, like it, before the mutation listener below: apply
		// order follows registration order, and mutations must resolve species that already exist in the live map.
		event.addListener(ButterflySpeciesManager.INSTANCE);

		// Rebuild each species type's mutation index from the (re)loaded mutation recipes. Mod reload listeners run
		// after vanilla ones (and the reload barrier applies listeners in order), so by the apply phase the vanilla
		// RecipeManager is fully populated. Run on the game executor since this mutates shared species-type state.
		RecipeManager recipeManager = event.getServerResources().getRecipeManager();
		event.addListener((prepBarrier, resourceManager, prepProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
			return prepBarrier.wait(Unit.INSTANCE).thenRunAsync(() -> GeneticsReloadHandler.rebuildMutations(recipeManager), gameExecutor);
		});
	}

	/**
	 * Sends the loaded flower-type, bee, tree, and butterfly species definitions to the client on login/reload,
	 * before tags and recipes sync (per {@code OnDatapackSyncEvent}'s contract). The client has no datapack access,
	 * so these packets are its only source for {@code FlowerTypeManager}'s/{@code BeeSpeciesManager}'s/
	 * {@code TreeSpeciesManager}'s/{@code ButterflySpeciesManager}'s definitions; {@code FlowerTypeSyncPacket} is
	 * sent first since bee genome dominance resolution reads {@code IFlowerType}. {@code FlowerTypeSyncPacket}'s/
	 * {@code BeeSpeciesSyncPacket}'s/{@code TreeSpeciesSyncPacket}'s/{@code ButterflySpeciesSyncPacket}'s
	 * {@code handle} rebuild the client-side flower-type/species (and, in order, mutation) index from them.
	 */
	private static void onDatapackSync(OnDatapackSyncEvent event) {
		FlowerTypeSyncPacket flowerTypePacket = new FlowerTypeSyncPacket(FlowerTypeManager.INSTANCE.getDefinitions());
		BeeEffectSyncPacket beeEffectPacket = new BeeEffectSyncPacket(BeeEffectManager.INSTANCE.getEffects());
		TaxonSyncPacket taxonPacket = new TaxonSyncPacket(TaxonManager.INSTANCE.getDefinitions());
		BeeSpeciesSyncPacket beePacket = new BeeSpeciesSyncPacket(BeeSpeciesManager.INSTANCE.getDefinitions());
		TreeSpeciesSyncPacket treePacket = new TreeSpeciesSyncPacket(TreeSpeciesManager.INSTANCE.getDefinitions());
		ButterflySpeciesSyncPacket butterflyPacket = new ButterflySpeciesSyncPacket(ButterflySpeciesManager.INSTANCE.getDefinitions());
		event.getRelevantPlayers().forEach(player -> {
			// Flower types, effects and taxa must arrive before species (projection resolves those references).
			NetworkUtil.sendToPlayer(flowerTypePacket, player);
			NetworkUtil.sendToPlayer(beeEffectPacket, player);
			NetworkUtil.sendToPlayer(taxonPacket, player);
			NetworkUtil.sendToPlayer(beePacket, player);
			NetworkUtil.sendToPlayer(treePacket, player);
			NetworkUtil.sendToPlayer(butterflyPacket, player);
		});
	}

	private static void registerCommands(RegisterCommandsEvent event) {
		LiteralArgumentBuilder<CommandSourceStack> forestryCommand = LiteralArgumentBuilder.literal("forestry");

		forestryCommand.then(DiagnosticsCommand.register());
		forestryCommand.then(DumpCommand.register());
		forestryCommand.then(forestry.core.commands.MultiblockDebugCommand.register());

		for (IForestryModule module : IForestryApi.INSTANCE.getModuleManager().getModulesForMod(ForestryConstants.MOD_ID)) {
			if (module instanceof BlankForestryModule forestryModule) {
				forestryModule.addToRootCommand(forestryCommand);
			}
		}

		event.getDispatcher().register(forestryCommand);
	}

	@Override
	public boolean isCore() {
		return true;
	}

	@Override
	public List<ResourceLocation> getModuleDependencies() {
		return List.of();
	}

	@Override
	public void registerPackets(IPacketRegistry registry) {
		registry.serverbound(PacketIdServer.GUI_SELECTION_REQUEST, PacketGuiSelectRequest::encode, PacketGuiSelectRequest::decode, PacketGuiSelectRequest::handle);
		registry.serverbound(PacketIdServer.PIPETTE_CLICK, PacketPipetteClick::encode, PacketPipetteClick::decode, PacketPipetteClick::handle);
		registry.serverbound(PacketIdServer.CHIPSET_CLICK, PacketChipsetClick::encode, PacketChipsetClick::decode, PacketChipsetClick::handle);
		registry.serverbound(PacketIdServer.SOLDERING_IRON_CLICK, PacketSolderingIronClick::encode, PacketSolderingIronClick::decode, PacketSolderingIronClick::handle);

		registry.clientbound(PacketIdClient.ERROR_UPDATE, PacketErrorUpdate::encode, PacketErrorUpdate::decode, PacketErrorUpdate::handle);
		registry.clientbound(PacketIdClient.GUI_UPDATE, PacketGuiStream::encode, PacketGuiStream::decode, PacketGuiStream::handle);
		registry.clientbound(PacketIdClient.GUI_LAYOUT_SELECT, PacketGuiLayoutSelect::encode, PacketGuiLayoutSelect::decode, PacketGuiLayoutSelect::handle);
		registry.clientbound(PacketIdClient.GUI_ENERGY, PacketGuiEnergy::encode, PacketGuiEnergy::decode, PacketGuiEnergy::handle);
		registry.clientbound(PacketIdClient.SOCKET_UPDATE, PacketSocketUpdate::encode, PacketSocketUpdate::decode, PacketSocketUpdate::handle);
		registry.clientbound(PacketIdClient.TILE_FORESTRY_UPDATE, PacketTileStream::encode, PacketTileStream::decode, PacketTileStream::handle);
		registry.clientbound(PacketIdClient.TILE_FORESTRY_ACTIVE, PacketActiveUpdate::encode, PacketActiveUpdate::decode, PacketActiveUpdate::handle);
		registry.clientbound(PacketIdClient.ITEMSTACK_DISPLAY, PacketItemStackDisplay::encode, PacketItemStackDisplay::decode, PacketItemStackDisplay::handle);
		registry.clientbound(PacketIdClient.GENOME_TRACKER_UPDATE, PacketGenomeTrackerSync::encode, PacketGenomeTrackerSync::decode, PacketGenomeTrackerSync::handle);
		registry.clientbound(PacketIdClient.TANK_LEVEL_UPDATE, PacketTankLevelUpdate::encode, PacketTankLevelUpdate::decode, PacketTankLevelUpdate::handle);
		registry.clientbound(PacketIdClient.RECIPE_CACHE, RecipeCachePacket::encode, RecipeCachePacket::decode, RecipeCachePacket::handle);
		registry.clientbound(PacketIdClient.REFRACTORY_WAX_ON, PacketRefractoryWax::encode, PacketRefractoryWax::decode, PacketRefractoryWax::handle);
		registry.clientbound(PacketIdClient.FLOWER_TYPE_SYNC, FlowerTypeSyncPacket::encode, FlowerTypeSyncPacket::decode, FlowerTypeSyncPacket::handle);
		registry.clientbound(PacketIdClient.BEE_EFFECT_SYNC, BeeEffectSyncPacket::encode, BeeEffectSyncPacket::decode, BeeEffectSyncPacket::handle);
		registry.clientbound(PacketIdClient.TAXON_SYNC, TaxonSyncPacket::encode, TaxonSyncPacket::decode, TaxonSyncPacket::handle);
		registry.clientbound(PacketIdClient.BEE_SPECIES_SYNC, BeeSpeciesSyncPacket::encode, BeeSpeciesSyncPacket::decode, BeeSpeciesSyncPacket::handle);
		registry.clientbound(PacketIdClient.TREE_SPECIES_SYNC, TreeSpeciesSyncPacket::encode, TreeSpeciesSyncPacket::decode, TreeSpeciesSyncPacket::handle);
		registry.clientbound(PacketIdClient.BUTTERFLY_SPECIES_SYNC, ButterflySpeciesSyncPacket::encode, ButterflySpeciesSyncPacket::decode, ButterflySpeciesSyncPacket::handle);
	}

	@Override
	public void registerClientHandler(Consumer<IClientModuleHandler> registrar) {
		registrar.accept(new CoreClientHandler());
	}
}
