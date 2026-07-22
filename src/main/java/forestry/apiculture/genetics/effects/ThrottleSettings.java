package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The four fields every {@link ThrottledBeeEffect}-derived primitive shares. Spliced flat into each primitive's
 * object by {@link #codec} (a group {@code MapCodec} contributes its fields to the parent), so a definition stays a
 * flat JSON object with no nested settings block.
 * <p>
 * {@code AgingBeeEffect} is not covered: it extends {@code NonStackingBeeEffect}, which uses a tracked-owner
 * mechanism rather than throttling and so has no throttle to expose.
 */
public record ThrottleSettings(boolean dominant, int throttle, boolean requiresWorking, boolean combinable) {
	/**
	 * @param defThrottle        the primitive's historical hardcoded throttle.
	 * @param defRequiresWorking the primitive's historical hardcoded requires-working-queen flag.
	 * @param defCombinable      the primitive's historical hardcoded combinable flag.
	 *                           <p>
	 *                           Defaults are per-primitive, and each one equals what that primitive used to hardcode.
	 *                           That is what lets the already-generated built-in JSON stay unchanged: a built-in only
	 *                           emits a field it actually deviates on.
	 */
	public static MapCodec<ThrottleSettings> codec(int defThrottle, boolean defRequiresWorking, boolean defCombinable) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("dominant", true).forGetter(ThrottleSettings::dominant),
			Codec.INT.optionalFieldOf("throttle", defThrottle).forGetter(ThrottleSettings::throttle),
			Codec.BOOL.optionalFieldOf("requires_working", defRequiresWorking).forGetter(ThrottleSettings::requiresWorking),
			Codec.BOOL.optionalFieldOf("combinable", defCombinable).forGetter(ThrottleSettings::combinable)
		).apply(instance, ThrottleSettings::new));
	}
}
