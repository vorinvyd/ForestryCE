package forestry.arboriculture.features;

import forestry.api.modules.ForestryModuleIds;
import forestry.arboriculture.tiles.TileFruitPod;
import forestry.arboriculture.tiles.TileLeaves;
import forestry.arboriculture.tiles.TileSapling;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.FeatureTileType;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.stream.Stream;

@FeatureProvider
public class ArboricultureTiles {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.ARBORICULTURE);

	public static final FeatureTileType<TileSapling> SAPLING = REGISTRY.tile(TileSapling::new, "tree_sapling", ArboricultureBlocks.SAPLING_GE::collect);
	public static final FeatureTileType<TileLeaves> LEAVES = REGISTRY.tile(TileLeaves::new, "leaves", ArboricultureBlocks.LEAVES::collect);
	public static final FeatureTileType<TileFruitPod> PODS = REGISTRY.tile(TileFruitPod::new, "pods", ArboricultureBlocks.PODS::getList);

	public static final FeatureTileType<SignBlockEntity> SIGN = REGISTRY.tile((pos, state) -> new SignBlockEntity(ArboricultureTiles.SIGN.tileType(), pos, state), "sign", () -> Stream.concat(ArboricultureBlocks.SIGN.getList().stream(), ArboricultureBlocks.WALL_SIGN.getList().stream()).toList());
	// Hanging signs deliberately do NOT register a Forestry-owned BlockEntityType.
	// HangingSignBlockEntity's only public constructor hardcodes vanilla BlockEntityType.HANGING_SIGN, so any
	// Forestry-owned type would never actually be used at runtime (breaking save validation and tickers).
	// Instead, Forestry's hanging-sign blocks are added to vanilla's BlockEntityType.HANGING_SIGN#validBlocks
	// via BlockEntityTypeAddBlocksEvent in ModuleArboriculture.
}
