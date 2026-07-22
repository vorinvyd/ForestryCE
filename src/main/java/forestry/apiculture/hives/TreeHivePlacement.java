package forestry.apiculture.hives;

import forestry.api.apiculture.hives.IHivePlacement;
import forestry.core.utils.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public enum TreeHivePlacement implements IHivePlacement {
	INSTANCE;

	@Override
	public boolean canReplace(BlockState state, WorldGenLevel level, BlockPos pos) {
		return BlockUtil.canReplace(state, level, pos);
	}

	@Override
	public boolean isValidLocation(WorldGenLevel level, BlockPos pos) {
		BlockPos posAbove = pos.above();
		BlockState blockStateAbove = level.getBlockState(posAbove);
		if (!IHivePlacement.isTreeBlock(blockStateAbove)) {
			return false;
		}

		// not a good location if right on top of something
		BlockPos posBelow = pos.below();
		BlockState blockStateBelow = level.getBlockState(posBelow);
		return canReplace(blockStateBelow, level, posBelow);
	}

	@Override
	@Nullable
	public BlockPos getPosForHive(WorldGenLevel level, RandomSource random, int posX, int posZ) {
		ChunkAccess chunk = level.getChunk(posX >> 4, posZ >> 4);

		// get top leaf block
		int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, posX & 0xFF, posZ & 0xFF) - 1;

		if (height <= chunk.getMinBuildHeight()) {
			return null;
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(posX, height, posZ);
		BlockState state = chunk.getBlockState(pos);

		if (!IHivePlacement.isTreeBlock(state)) {
			return null;
		}

		// get to the bottom of the leaves
		do {
			pos.move(Direction.DOWN);
			state = chunk.getBlockState(pos);
		} while (IHivePlacement.isTreeBlock(state));

		return pos.immutable();
	}
}
