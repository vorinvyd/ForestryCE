package forestry.core.multiblock;

import forestry.api.multiblock.IAlvearyComponent;
import forestry.api.multiblock.IFarmComponent;
import forestry.apiculture.multiblock.AlvearyPattern;
import forestry.apiculture.multiblock.TileAlvearyPlain;
import forestry.core.multiblock.pattern.StructurePos;
import forestry.core.multiblock.pattern.StructureView;
import forestry.farming.multiblock.FarmPattern;
import forestry.farming.tiles.TileFarmGearbox;
import forestry.farming.tiles.TileFarmPlain;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The Phase-2 world adapter: a {@link StructureView} over a Forge {@link Level} (plan Task 2.1; spec
 * §5.2). It performs <b>chunk-safe</b> reads — {@link #isLoaded} checks the chunk source rather than
 * forcing a load — so the pattern engine's maximality + loaded-shell rule (spec §5.2, audit D1) can defer
 * on partially-loaded structures instead of assembling a shrunken sub-prism.
 *
 * <p>{@link #sample} reads the in-world {@link BlockEntity}, the blockstate's
 * {@code is(BlockTags.WOODEN_SLABS)} (the alveary slab cap), and {@code isSolidRender} (the alveary
 * entrance air ring), and resolves the cell's {@code componentTypeId} from the BE itself.
 *
 * <p><b>Type-id translation.</b> See {@link #typeIdFor}. A BE that is not a member of either machine maps
 * to {@code null} (the cell is reported as not-a-component -> {@code invalid.interior}).
 */
public final class LevelStructureView implements StructureView {
	private final Level level;

	public LevelStructureView(Level level) {
		this.level = level;
	}

	/* ===== StructurePos <-> BlockPos ===== */

	private static BlockPos toBlockPos(StructurePos pos) {
		return new BlockPos(pos.x(), pos.y(), pos.z());
	}

	@Override
	public boolean isLoaded(StructurePos pos) {
		// Chunk-safe: do NOT force-load. hasChunk(chunkX, chunkZ) mirrors the old engine's chunk checks.
		return this.level.getChunkSource().hasChunk(pos.x() >> 4, pos.z() >> 4);
	}

	@Override
	public CellSample sample(StructurePos pos) {
		BlockPos blockPos = toBlockPos(pos);

		// Component: resolve via the BlockEntity. getBlockEntity is safe here because validation
		// only samples cells it has already confirmed loaded (loaded-shell rule).
		BlockEntity be = this.level.getBlockEntity(blockPos);
		String componentTypeId = be == null ? null : typeIdFor(be);
		boolean isComponent = componentTypeId != null;

		BlockState state = this.level.getBlockState(blockPos);
		boolean isWoodenSlab = state.is(BlockTags.WOODEN_SLABS);
		boolean isSolidRender = state.isSolidRender(this.level, blockPos);

		return new CellSample(isComponent, be, isWoodenSlab, isSolidRender, componentTypeId);
	}

	/* ===== BlockEntity -> pattern type-id mapping ===== */

	/**
	 * Resolves a cell's pattern type-id from its block entity, mirroring the pre-rewrite controllers
	 * one-for-one.
	 *
	 * <p>The machine comes from the public component interface, as in the old base cube loop's
	 * {@code te instanceof IMultiblockComponent} and its controller-class guard. Any block entity that
	 * implements {@link IAlvearyComponent} or {@link IFarmComponent} is a member of that machine, so addon
	 * parts are recognised by the same rule as Forestry's own.
	 *
	 * <p>The role comes from the concrete part class, as in the old
	 * {@code AlvearyController.isGoodForInterior} ({@code instanceof TileAlvearyPlain}) and
	 * {@code FarmController} ({@code instanceof TileFarmPlain} or {@code TileFarmGearbox}). An addon that
	 * subclasses a role part inherits that role, again as before the rewrite.
	 *
	 * <p>Every other component is the machine's generic {@code *_part}. The pattern layer never
	 * distinguishes them, because {@code Predicates} only compares {@code PLAIN} and {@code GEARBOX} by
	 * equality and {@code PREFIX} by prefix, so a per-part id would carry no information.
	 *
	 * <p>Membership also requires {@link MultiblockTileEntityForestry}, because the rest of the engine
	 * does. {@code MultiblockValidation.assemble} resolves the holder (the lowest member) as one and
	 * returns otherwise, and anchoring and stash-clearing skip anything else. A part the engine cannot
	 * anchor would make a structure fail to form with no error. Addon parts therefore extend
	 * {@code TileAlveary} or {@code TileFarm}, as they did before the rewrite.
	 */
	@Nullable
	static String typeIdFor(BlockEntity be) {
		if (!(be instanceof MultiblockTileEntityForestry<?>)) {
			return null;
		}
		if (be instanceof IAlvearyComponent<?>) {
			return be instanceof TileAlvearyPlain ? AlvearyPattern.PLAIN : AlvearyPattern.PART;
		}
		if (be instanceof IFarmComponent<?>) {
			if (be instanceof TileFarmPlain) {
				return FarmPattern.PLAIN;
			}
			if (be instanceof TileFarmGearbox) {
				return FarmPattern.GEARBOX;
			}
			return FarmPattern.PART;
		}
		return null;
	}
}
