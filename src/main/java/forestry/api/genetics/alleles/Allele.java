package forestry.api.genetics.alleles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * An allele is a single inline value of a chromosome, together with its dominance.
 * <p>
 * Alleles are plain data: they are not interned, have no ID, and are not stored in any registry. A genome stores
 * {@link AllelePair} of these directly, serialized inline via each chromosome's value codec. For "reference"
 * chromosomes (species, flower type, bee effect, activity, fruit, tree effect, cocoon, butterfly effect) the value
 * is the referenced object's {@link net.minecraft.resources.ResourceLocation}; the behavior object is resolved on
 * demand through {@link IChromosome#resolver()}.
 *
 * @param value    The value held by this allele.
 * @param dominant Whether this allele is dominant.
 * @param <V>      The type of value held by this allele.
 */
public record Allele<V>(V value, boolean dominant) {
	public static <V> Allele<V> dominant(V value) {
		return new Allele<>(value, true);
	}

	public static <V> Allele<V> recessive(V value) {
		return new Allele<>(value, false);
	}

	public static <V> Allele<V> of(V value, boolean dominant) {
		return new Allele<>(value, dominant);
	}

	/**
	 * Creates an allele for a reference chromosome from the referenced value's ID. The dominance is a placeholder:
	 * a reference value's dominance is intrinsic to the value, so it is resolved from the chromosome's resolver when
	 * the genome is materialized (e.g. in {@link IGenome#copyWith}). Use this for genome overrides built before
	 * registries are populated, such as hive drops or village bees.
	 */
	public static Allele<ResourceLocation> reference(ResourceLocation id) {
		return new Allele<>(id, false);
	}

	/**
	 * @return A codec for a single allele, serializing its value inline via the given value codec as
	 * {@code { "value": ..., "dominant": false }} (dominance defaults to false when omitted).
	 */
	public static <V> Codec<Allele<V>> codec(Codec<V> valueCodec) {
		return RecordCodecBuilder.create(instance -> instance.group(
			valueCodec.fieldOf("value").forGetter(Allele::value),
			Codec.BOOL.optionalFieldOf("dominant", false).forGetter(allele -> allele.dominant())
		).apply(instance, (value, dominant) -> new Allele<>(value, dominant)));
	}

	/**
	 * @return A stream codec for a single allele: its value (via the given value stream codec) followed by a boolean dominance flag.
	 */
	public static <V> StreamCodec<RegistryFriendlyByteBuf, Allele<V>> streamCodec(StreamCodec<RegistryFriendlyByteBuf, V> valueStreamCodec) {
		return StreamCodec.composite(
			valueStreamCodec, Allele::value,
			ByteBufCodecs.BOOL, allele -> allele.dominant(),
			(value, dominant) -> new Allele<>(value, dominant)
		);
	}
}
