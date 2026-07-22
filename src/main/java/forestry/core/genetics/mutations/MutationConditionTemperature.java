package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.climate.IClimateProvider;
import forestry.api.core.ClimateCodecs;
import forestry.api.core.TemperatureType;
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

public class MutationConditionTemperature implements IMutationCondition {
	public static final MapCodec<MutationConditionTemperature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ClimateCodecs.TEMPERATURE.fieldOf("min").forGetter(MutationConditionTemperature::getMin),
		ClimateCodecs.TEMPERATURE.fieldOf("max").forGetter(MutationConditionTemperature::getMax)
	).apply(instance, MutationConditionTemperature::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionTemperature> STREAM_CODEC = StreamCodec.composite(
		ClimateCodecs.TEMPERATURE_STREAM, MutationConditionTemperature::getMin,
		ClimateCodecs.TEMPERATURE_STREAM, MutationConditionTemperature::getMax,
		MutationConditionTemperature::new
	);
	public static final MutationConditionType<MutationConditionTemperature> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final TemperatureType minTemperature;
	private final TemperatureType maxTemperature;

	public MutationConditionTemperature(TemperatureType minTemperature, TemperatureType maxTemperature) {
		this.minTemperature = minTemperature;
		this.maxTemperature = maxTemperature;
	}

	public TemperatureType getMin() {
		return this.minTemperature;
	}

	public TemperatureType getMax() {
		return this.maxTemperature;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		TemperatureType biomeTemperature = climate.temperature();

		if (biomeTemperature.ordinal() < this.minTemperature.ordinal() || biomeTemperature.ordinal() > this.maxTemperature.ordinal()) {
			return 0f;
		}
		return currentChance;
	}

	@Override
	public Component getDescription() {
		Component minString = ClimateHelper.toDisplay(this.minTemperature);

		if (this.minTemperature != this.maxTemperature) {
			Component maxString = ClimateHelper.toDisplay(this.maxTemperature);
			return Component.translatable("for.mutation.condition.temperature.range", minString, maxString);
		} else {
			return Component.translatable("for.mutation.condition.temperature.single", minString);
		}
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
