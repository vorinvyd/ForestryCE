package forestry.apiculture.features;

import forestry.api.modules.ForestryModuleIds;
import forestry.apiculture.blocks.*;
import forestry.apiculture.items.EnumHoneyComb;
import forestry.apiculture.items.ItemBlockHoneyComb;
import forestry.core.blocks.BlockBase;
import forestry.core.items.ItemBlockForestry;
import forestry.modules.features.FeatureBlockGroup;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.FeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

import java.util.List;

@FeatureProvider
public class ApicultureBlocks {
	private static final FeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.APICULTURE);

	public static final FeatureBlockGroup<BlockBase<BlockTypeApiculture>, BlockTypeApiculture> BASE = REGISTRY.blockGroup(
			(BlockTypeApiculture type) -> new BlockBase<>(Block.Properties.of().sound(SoundType.WOOD).strength(2.0f), type),
			List.of(BlockTypeApiculture.values()))
		.item(ItemBlockForestry::new)
		.create();

	public static final FeatureBlockGroup<BlockBeeHive, BlockHiveType> BEEHIVE = REGISTRY.blockGroup(BlockBeeHive::new, List.of(BlockHiveType.values())).item((block, properties) -> new ItemBlockForestry<>(block, properties)).identifier(type -> switch (type) {
		case DESERT -> "modest_hive";
		case JUNGLE -> "tropical_hive";
		case END -> "ender_hive";
		case SNOW -> "wintry_hive";
		case SWAMP -> "marshy_hive";
		default -> type.getSerializedName() + "_hive";
	}).create();

	public static final FeatureBlockGroup<BlockHoneyComb, EnumHoneyComb> BEE_COMB = REGISTRY.blockGroup(BlockHoneyComb::new, List.of(EnumHoneyComb.VALUES)).item((block, properties) -> new ItemBlockHoneyComb(block)).identifier(type -> (type == EnumHoneyComb.SPONGE ? "spongy" : type.getSerializedName()) + "_comb_block").create();
	public static final FeatureBlockGroup<BlockAlveary, BlockAlveary.Type> ALVEARY = REGISTRY.blockGroup(BlockAlveary::new, BlockAlveary.Type.DEFAULT_VALUES).item((block, properties) -> new ItemBlockForestry<>(block, properties)).identifier(type -> switch (type.getSerializedName()) {
		case "plain" -> "alveary_block";
		case "hygro" -> "alveary_hygroregulator";
		case "stabiliser" -> "alveary_stabilizer";
		default -> "alveary_" + type.getSerializedName();
	}).create();
}
