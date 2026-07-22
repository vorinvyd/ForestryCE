package forestry.core.genetics.mutations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

public class MutationConditionDaytime implements IMutationCondition {
	public static final MapCodec<MutationConditionDaytime> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.fieldOf("day").forGetter(MutationConditionDaytime::isDaytime)
	).apply(instance, MutationConditionDaytime::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionDaytime> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, MutationConditionDaytime::isDaytime,
		MutationConditionDaytime::new
	);
	public static final MutationConditionType<MutationConditionDaytime> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final boolean daytime;

	public MutationConditionDaytime(boolean daytime) {
		this.daytime = daytime;
	}

	public boolean isDaytime() {
		return this.daytime;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		if (level.isDay() == this.daytime) {
			return currentChance;
		}
		return 0f;
	}

	@Override
	public Component getDescription() {
		if (this.daytime) {
			return Component.translatable("for.mutation.condition.daytime.day");
		} else {
			return Component.translatable("for.mutation.condition.daytime.night");
		}
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
