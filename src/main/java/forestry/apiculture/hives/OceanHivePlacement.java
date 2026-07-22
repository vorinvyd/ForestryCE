package forestry.apiculture.hives;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class OceanHivePlacement extends GroundHivePlacement {
	public OceanHivePlacement(TagKey<Block> blocks) {
		super(blocks);
	}

	@Override
	public boolean canReplace(BlockState state, WorldGenLevel level, BlockPos pos) {
		return state.getBlock() == Blocks.WATER;
	}

	@Override
	public Heightmap.Types getHeightmapType() {
		return Heightmap.Types.OCEAN_FLOOR_WG;
	}
}
