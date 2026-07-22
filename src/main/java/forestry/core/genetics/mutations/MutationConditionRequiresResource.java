package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import forestry.core.tiles.TileUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

public class MutationConditionRequiresResource implements IMutationCondition {
	public static final MapCodec<MutationConditionRequiresResource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		BlockState.CODEC.listOf().fieldOf("blocks").forGetter(MutationConditionRequiresResource::getBlocks)
	).apply(instance, MutationConditionRequiresResource::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionRequiresResource> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC).apply(ByteBufCodecs.list()),
		MutationConditionRequiresResource::getBlocks,
		MutationConditionRequiresResource::new
	);
	public static final MutationConditionType<MutationConditionRequiresResource> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final List<BlockState> acceptedBlockStates;

	public MutationConditionRequiresResource(List<BlockState> acceptedBlockStates) {
		this.acceptedBlockStates = acceptedBlockStates;
	}

	public MutationConditionRequiresResource(BlockState... acceptedBlockStates) {
		this(Arrays.asList(acceptedBlockStates));
	}

	public List<BlockState> getBlocks() {
		return this.acceptedBlockStates;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		BlockEntity tile;
		do {
			pos = pos.below();
			tile = TileUtil.getTile(level, pos);
		} while (tile instanceof IBeeHousing);

		BlockState blockState = level.getBlockState(pos);
		return this.acceptedBlockStates.contains(blockState) ? currentChance : 0f;
	}

	@Override
	public Component getDescription() {
		if (this.acceptedBlockStates.isEmpty()) {
			return GameNarrator.NO_TITLE;
		} else {
			return Component.translatable("for.mutation.condition.resource", this.acceptedBlockStates.get(0).getBlock().getName());
		}
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
