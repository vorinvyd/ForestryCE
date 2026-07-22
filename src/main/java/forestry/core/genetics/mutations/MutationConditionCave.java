package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

public class MutationConditionCave implements IMutationCondition {
	public static final MapCodec<MutationConditionCave> CODEC = MapCodec.unit(new MutationConditionCave());
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionCave> STREAM_CODEC = StreamCodec.unit(new MutationConditionCave());
	public static final MutationConditionType<MutationConditionCave> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome firstGenome, IGenome secondGenome, IClimateProvider climate, float currentChance) {
		for (Direction direction : Direction.VALUES) {
			if (level.getBrightness(LightLayer.SKY, pos.relative(direction)) > 0) {
				return 0;
			}
		}
		return currentChance;
	}

	@Override
	public Component getDescription() {
		return Component.translatable("for.mutation.condition.underground");
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}

	// Stateless value object: all instances are equal. Required so the unit stream codec (which validates
	// value.equals(expectedValue) on encode) can serialize JSON-parsed cave conditions during recipe sync.
	@Override
	public boolean equals(Object obj) {
		return obj instanceof MutationConditionCave;
	}

	@Override
	public int hashCode() {
		return MutationConditionCave.class.hashCode();
	}
}
