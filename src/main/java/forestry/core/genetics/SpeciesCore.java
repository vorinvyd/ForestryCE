package forestry.core.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import forestry.api.core.ClimateCodecs;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;

/**
 * The 10 base genetics/metadata fields shared by every {@link ISpeciesDefinition}, extracted purely so
 * their codec + stream codec are written once instead of triplicated. {@link #MAP_CODEC} is a
 * {@link MapCodec}, so composing it into a definition codec inlines the same top-level JSON keys the
 * definitions used before — the serialized shape is byte-identical. {@code genome} is NOT part of this
 * record: its codec is karyotype-keyed and stays factored through {@code GenomeCodecs}.
 */
public record SpeciesCore(
	String genus,
	String species,
	boolean dominant,
	boolean glint,
	boolean secret,
	int complexity,
	String authority,
	int escritoireColor,
	TemperatureType temperature,
	HumidityType humidity
) {
	public static final MapCodec<SpeciesCore> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.STRING.fieldOf("genus").forGetter(SpeciesCore::genus),
		Codec.STRING.fieldOf("species").forGetter(SpeciesCore::species),
		Codec.BOOL.optionalFieldOf("dominant", false).forGetter(SpeciesCore::dominant),
		Codec.BOOL.optionalFieldOf("glint", false).forGetter(SpeciesCore::glint),
		Codec.BOOL.optionalFieldOf("secret", false).forGetter(SpeciesCore::secret),
		Codec.INT.optionalFieldOf("complexity", 0).forGetter(SpeciesCore::complexity),
		Codec.STRING.optionalFieldOf("authority", "Sengir").forGetter(SpeciesCore::authority),
		Codec.INT.optionalFieldOf("escritoire_color", -1).forGetter(SpeciesCore::escritoireColor),
		ClimateCodecs.TEMPERATURE.optionalFieldOf("temperature", TemperatureType.NORMAL).forGetter(SpeciesCore::temperature),
		ClimateCodecs.HUMIDITY.optionalFieldOf("humidity", HumidityType.NORMAL).forGetter(SpeciesCore::humidity)
	).apply(instance, SpeciesCore::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeciesCore> STREAM_CODEC = StreamCodec.of(
		(buf, core) -> {
			buf.writeUtf(core.genus());
			buf.writeUtf(core.species());
			buf.writeBoolean(core.dominant());
			buf.writeBoolean(core.glint());
			buf.writeBoolean(core.secret());
			buf.writeVarInt(core.complexity());
			buf.writeUtf(core.authority());
			buf.writeInt(core.escritoireColor());
			ClimateCodecs.TEMPERATURE_STREAM.encode(buf, core.temperature());
			ClimateCodecs.HUMIDITY_STREAM.encode(buf, core.humidity());
		},
		buf -> new SpeciesCore(
			buf.readUtf(),
			buf.readUtf(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readUtf(),
			buf.readInt(),
			ClimateCodecs.TEMPERATURE_STREAM.decode(buf),
			ClimateCodecs.HUMIDITY_STREAM.decode(buf)
		)
	);
}
