package forestry.core.genetics.mutations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import forestry.core.utils.DayMonth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

public class MutationConditionTimeLimited implements IMutationCondition {
	public static final MapCodec<MutationConditionTimeLimited> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.INT.fieldOf("start_month").forGetter(MutationConditionTimeLimited::getStartMonth),
		Codec.INT.fieldOf("start_day").forGetter(MutationConditionTimeLimited::getStartDay),
		Codec.INT.fieldOf("end_month").forGetter(MutationConditionTimeLimited::getEndMonth),
		Codec.INT.fieldOf("end_day").forGetter(MutationConditionTimeLimited::getEndDay)
	).apply(instance, MutationConditionTimeLimited::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionTimeLimited> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, MutationConditionTimeLimited::getStartMonth,
		ByteBufCodecs.VAR_INT, MutationConditionTimeLimited::getStartDay,
		ByteBufCodecs.VAR_INT, MutationConditionTimeLimited::getEndMonth,
		ByteBufCodecs.VAR_INT, MutationConditionTimeLimited::getEndDay,
		MutationConditionTimeLimited::new
	);
	public static final MutationConditionType<MutationConditionTimeLimited> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final DayMonth start;
	private final DayMonth end;

	public MutationConditionTimeLimited(int startMonth, int startDay, int endMonth, int endDay) {
		this.start = new DayMonth(startDay, startMonth);
		this.end = new DayMonth(endDay, endMonth);
	}

	public int getStartMonth() {
		return this.start.month();
	}

	public int getStartDay() {
		return this.start.day();
	}

	public int getEndMonth() {
		return this.end.month();
	}

	public int getEndDay() {
		return this.end.day();
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		DayMonth now = DayMonth.now();

		if (now.between(this.start, this.end)) {
			return currentChance;
		}

		return 0;
	}

	@Override
	public Component getDescription() {
		return Component.translatable("for.mutation.condition.date", this.start.getDisplayName(), this.end.getDisplayName());
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
