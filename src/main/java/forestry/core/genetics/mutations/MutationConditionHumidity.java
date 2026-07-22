package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.climate.IClimateProvider;
import forestry.api.core.ClimateCodecs;
import forestry.api.core.HumidityType;
import forestry.api.genetics.ClimateHelper;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

public class MutationConditionHumidity implements IMutationCondition {
	public static final MapCodec<MutationConditionHumidity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ClimateCodecs.HUMIDITY.fieldOf("min").forGetter(MutationConditionHumidity::getMin),
		ClimateCodecs.HUMIDITY.fieldOf("max").forGetter(MutationConditionHumidity::getMax)
	).apply(instance, MutationConditionHumidity::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionHumidity> STREAM_CODEC = StreamCodec.composite(
		ClimateCodecs.HUMIDITY_STREAM, MutationConditionHumidity::getMin,
		ClimateCodecs.HUMIDITY_STREAM, MutationConditionHumidity::getMax,
		MutationConditionHumidity::new
	);
	public static final MutationConditionType<MutationConditionHumidity> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final HumidityType minHumidity;
	private final HumidityType maxHumidity;

	public MutationConditionHumidity(HumidityType minHumidity, HumidityType maxHumidity) {
		this.minHumidity = minHumidity;
		this.maxHumidity = maxHumidity;
	}

	public HumidityType getMin() {
		return this.minHumidity;
	}

	public HumidityType getMax() {
		return this.maxHumidity;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		HumidityType biomeHumidity = climate.humidity();

		if (biomeHumidity.ordinal() < this.minHumidity.ordinal() || biomeHumidity.ordinal() > this.maxHumidity.ordinal()) {
			return 0f;
		}
		return currentChance;
	}

	@Override
	public Component getDescription() {
		Component minHumidityString = ClimateHelper.toDisplay(this.minHumidity);

		if (this.minHumidity != this.maxHumidity) {
			Component maxHumidityString = ClimateHelper.toDisplay(this.maxHumidity);
			return Component.translatable("for.mutation.condition.humidity.range", minHumidityString, maxHumidityString);
		} else {
			return Component.translatable("for.mutation.condition.humidity.single", minHumidityString);
		}
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
