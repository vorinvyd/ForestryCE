package forestry.api.core;

import java.util.Locale;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class ClimateCodecs {
	public static final Codec<TemperatureType> TEMPERATURE = Codec.STRING.xmap(
		s -> TemperatureType.valueOf(s.toUpperCase(Locale.ROOT)),
		t -> t.name().toLowerCase(Locale.ROOT));
	public static final Codec<HumidityType> HUMIDITY = Codec.STRING.xmap(
		s -> HumidityType.valueOf(s.toUpperCase(Locale.ROOT)),
		h -> h.name().toLowerCase(Locale.ROOT));
	public static final StreamCodec<io.netty.buffer.ByteBuf, TemperatureType> TEMPERATURE_STREAM =
		ByteBufCodecs.idMapper(TemperatureType.VALUES::get, TemperatureType::ordinal);
	public static final StreamCodec<io.netty.buffer.ByteBuf, HumidityType> HUMIDITY_STREAM =
		ByteBufCodecs.idMapper(HumidityType.VALUES::get, HumidityType::ordinal);

	private ClimateCodecs() {}
}
