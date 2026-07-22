package forestry.arboriculture.features;

import com.mojang.datafixers.util.Function3;
import forestry.api.arboriculture.IWoodType;
import forestry.api.arboriculture.WoodBlockKind;
import forestry.api.modules.ForestryModuleIds;
import forestry.arboriculture.ForestryWoodType;
import forestry.arboriculture.IWoodTyped;
import forestry.arboriculture.VanillaWoodType;
import forestry.arboriculture.WoodAccess;
import forestry.arboriculture.blocks.*;
import forestry.arboriculture.items.*;
import forestry.modules.features.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

@FeatureProvider
public class ArboricultureBlocks {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.ARBORICULTURE);

	/* VANILLA LOGS & WOOD */
	public static final FeatureBlockGroup<BlockForestryLog, VanillaWoodType> LOGS_VANILLA_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.LOG, true, VanillaWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, VanillaWoodType> WOOD_VANILLA_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.WOOD, true, VanillaWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, VanillaWoodType> STRIPPED_LOGS_VANILLA_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_LOG, true, VanillaWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, VanillaWoodType> STRIPPED_WOOD_VANILLA_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_WOOD, true, VanillaWoodType.VALUES);

	/* LOGS & WOOD */
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> LOGS = woodGroup(BlockForestryLog::new, WoodBlockKind.LOG, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> LOGS_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.LOG, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> STRIPPED_LOGS = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_LOG, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> STRIPPED_LOGS_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_LOG, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> WOOD = woodGroup(BlockForestryLog::new, WoodBlockKind.WOOD, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> WOOD_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.WOOD, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> STRIPPED_WOOD = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_WOOD, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryLog, ForestryWoodType> STRIPPED_WOOD_FIREPROOF = woodGroup(BlockForestryLog::new, WoodBlockKind.STRIPPED_WOOD, true, ForestryWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryPlank, ForestryWoodType> PLANKS = woodGroup(BlockForestryPlank::new, WoodBlockKind.PLANKS, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryPlank, ForestryWoodType> PLANKS_FIREPROOF = woodGroup(BlockForestryPlank::new, WoodBlockKind.PLANKS, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryPlank, VanillaWoodType> PLANKS_VANILLA_FIREPROOF = woodGroup(BlockForestryPlank::new, WoodBlockKind.PLANKS, true, VanillaWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestrySlab, ForestryWoodType> SLABS = woodGroup((type) -> new BlockForestrySlab(PLANKS.get(type).block()), ItemBlockWoodSlab::new, WoodBlockKind.SLAB, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestrySlab, ForestryWoodType> SLABS_FIREPROOF = woodGroup((type) -> new BlockForestrySlab(PLANKS_FIREPROOF.get(type).block()), ItemBlockWoodSlab::new, WoodBlockKind.SLAB, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestrySlab, VanillaWoodType> SLABS_VANILLA_FIREPROOF = woodGroup((type) -> new BlockForestrySlab(PLANKS_VANILLA_FIREPROOF.get(type).block()), ItemBlockWoodSlab::new, WoodBlockKind.SLAB, true, VanillaWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryFence, ForestryWoodType> FENCES = woodGroup(BlockForestryFence::new, WoodBlockKind.FENCE, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryFence, ForestryWoodType> FENCES_FIREPROOF = woodGroup(BlockForestryFence::new, WoodBlockKind.FENCE, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryFence, VanillaWoodType> FENCES_VANILLA_FIREPROOF = woodGroup(BlockForestryFence::new, WoodBlockKind.FENCE, true, VanillaWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryFenceGate, ForestryWoodType> FENCE_GATES = woodGroup(BlockForestryFenceGate::new, WoodBlockKind.FENCE_GATE, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryFenceGate, ForestryWoodType> FENCE_GATES_FIREPROOF = woodGroup(BlockForestryFenceGate::new, WoodBlockKind.FENCE_GATE, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryFenceGate, VanillaWoodType> FENCE_GATES_VANILLA_FIREPROOF = woodGroup(BlockForestryFenceGate::new, WoodBlockKind.FENCE_GATE, true, VanillaWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryStairs, ForestryWoodType> STAIRS = woodGroup((type) -> new BlockForestryStairs(PLANKS.get(type).block()), WoodBlockKind.STAIRS, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryStairs, ForestryWoodType> STAIRS_FIREPROOF = woodGroup((type) -> new BlockForestryStairs(PLANKS_FIREPROOF.get(type).block()), WoodBlockKind.STAIRS, true, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryStairs, VanillaWoodType> STAIRS_VANILLA_FIREPROOF = woodGroup((type) -> new BlockForestryStairs(PLANKS_VANILLA_FIREPROOF.get(type).block()), WoodBlockKind.STAIRS, true, VanillaWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryDoor, ForestryWoodType> DOORS = woodGroup(BlockForestryDoor::new, ItemBlockWoodDoor::new, WoodBlockKind.DOOR, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryTrapdoor, ForestryWoodType> TRAPDOORS = woodGroup(BlockForestryTrapdoor::new, WoodBlockKind.TRAPDOOR, false, ForestryWoodType.VALUES);

	public static final FeatureBlockGroup<BlockForestryStandingSign, ForestryWoodType> SIGN = registerWood(REGISTRY.blockGroup(BlockForestryStandingSign::new, Arrays.asList(ForestryWoodType.VALUES)).item(ItemBlockSign::new).identifier("sign", FeatureGroup.IdentifierType.SUFFIX).create(), WoodBlockKind.SIGN);
	public static final FeatureBlockGroup<BlockForestryWallSign, ForestryWoodType> WALL_SIGN = registerWood(REGISTRY.blockGroup(BlockForestryWallSign::new, Arrays.asList(ForestryWoodType.VALUES)).identifier("wall_sign", FeatureGroup.IdentifierType.SUFFIX).create(), WoodBlockKind.WALL_SIGN);
	public static final FeatureBlockGroup<BlockForestryHangingSign, ForestryWoodType> HANGING_SIGN = registerWood(REGISTRY.blockGroup(BlockForestryHangingSign::new, Arrays.asList(ForestryWoodType.VALUES)).item(ItemBlockHangingSign::new).identifier("hanging_sign", FeatureGroup.IdentifierType.SUFFIX).create(), WoodBlockKind.HANGING_SIGN);
	public static final FeatureBlockGroup<BlockForestryWallHangingSign, ForestryWoodType> WALL_HANGING_SIGN = registerWood(REGISTRY.blockGroup(BlockForestryWallHangingSign::new, Arrays.asList(ForestryWoodType.VALUES)).identifier("wall_hanging_sign", FeatureGroup.IdentifierType.SUFFIX).create(), WoodBlockKind.WALL_HANGING_SIGN);

	public static final FeatureBlockGroup<BlockForestryButton, ForestryWoodType> BUTTON = woodGroup(BlockForestryButton::new, WoodBlockKind.BUTTON, false, ForestryWoodType.VALUES);
	public static final FeatureBlockGroup<BlockForestryPressurePlate, ForestryWoodType> PRESSURE_PLATE = woodGroup(BlockForestryPressurePlate::new, WoodBlockKind.PRESSURE_PLATE, false, ForestryWoodType.VALUES);

	/* GENETICS */
	public static final FeatureBlock<BlockSapling, BlockItem> SAPLING_GE = REGISTRY.block(BlockSapling::new, "sapling_ge");
	public static final FeatureBlock<BlockForestryLeaves, ItemBlockLeaves> LEAVES = REGISTRY.block(BlockForestryLeaves::new, ItemBlockLeaves::new, "leaves");
	public static final FeatureBlockGroup<BlockDefaultLeaves, ForestryLeafType> LEAVES_DEFAULT = REGISTRY.blockGroup(BlockDefaultLeaves::new, ForestryLeafType.values()).item(ItemBlockDefaultLeaves::new).identifier("default_leaves", FeatureGroup.IdentifierType.SUFFIX).create();
	public static final FeatureBlockGroup<BlockDefaultLeavesFruit, ForestryLeafType> LEAVES_DEFAULT_FRUIT = REGISTRY.blockGroup(BlockDefaultLeavesFruit::new, ForestryLeafType.values()).item(ItemBlockDefaultLeaves::new).identifier("default_leaves_fruit", FeatureGroup.IdentifierType.SUFFIX).create();
	public static final FeatureBlockGroup<BlockDecorativeLeaves, ForestryLeafType> LEAVES_DECORATIVE = REGISTRY.blockGroup(BlockDecorativeLeaves::new, ForestryLeafType.values()).item(ItemBlockDecorativeLeaves::new).identifier("decorative_leaves", FeatureGroup.IdentifierType.SUFFIX).create();
	public static final FeatureBlockGroup<BlockFruitPod, ForestryPodType> PODS = REGISTRY.blockGroup(BlockFruitPod::new, Arrays.asList(ForestryPodType.values())).identifier("pods").create();

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> woodGroup(Function3<WoodBlockKind, Boolean, S, B> constructor, WoodBlockKind kind, boolean fireproof, S[] types) {
		return woodGroup((fireproof1, type) -> constructor.apply(kind, fireproof1, type), ItemBlockWood::new, kind, fireproof, types);
	}

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> woodGroup(BiFunction<Boolean, S, B> constructor, WoodBlockKind kind, boolean fireproof, S[] types) {
		return woodGroup(constructor, ItemBlockWood::new, kind, fireproof, types);
	}

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> woodGroup(BiFunction<Boolean, S, B> constructor, Function<B, BlockItem> itemConstructor, WoodBlockKind kind, boolean fireproof, S[] types) {
		return registerWood(REGISTRY.blockGroup((type) -> constructor.apply(fireproof, type), Arrays.asList(types)).item((block, properties) -> itemConstructor.apply(block)).identifier((fireproof ? "fireproof_" : "") + kind.getSerializedName(), FeatureGroup.IdentifierType.SUFFIX).create(), kind);
	}

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> woodGroup(Function<S, B> constructor, WoodBlockKind kind, boolean fireproof, S[] types) {
		return woodGroup(constructor, ItemBlockWood::new, kind, fireproof, types);
	}

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> woodGroup(Function<S, B> constructor, Function<B, BlockItem> itemConstructor, WoodBlockKind kind, boolean fireproof, S[] types) {
		return registerWood(REGISTRY.blockGroup(constructor, Arrays.asList(types)).item((block, properties) -> itemConstructor.apply(block)).identifier((fireproof ? "fireproof_" : "") + kind.getSerializedName(), FeatureGroup.IdentifierType.SUFFIX).create(), kind);
	}

	private static <B extends Block & IWoodTyped, S extends IWoodType> FeatureBlockGroup<B, S> registerWood(FeatureBlockGroup<B, S> group, WoodBlockKind kind) {
		REGISTRY.addRegistryListener(Registries.ITEM, () -> WoodAccess.INSTANCE.registerFeatures(group, kind));
		return group;
	}
}
