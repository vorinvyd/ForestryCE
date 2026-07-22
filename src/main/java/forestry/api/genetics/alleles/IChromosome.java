package forestry.api.genetics.alleles;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A chromosome is a typed key in a genome that maps to an {@link AllelePair} of inline values.
 * <p>
 * The five former subtypes (boolean/float/integer/value/registry) are collapsed into this single generic type.
 * <ul>
 *     <li><b>Data chromosomes</b> hold a literal value type ({@code Float}, {@code Integer}, {@code Boolean},
 *     an enum, {@code Vec3i}, ...). {@link #resolver()} is {@code null}.</li>
 *     <li><b>Reference chromosomes</b> (species, flower type, bee effect, activity, fruit, tree effect, cocoon,
 *     butterfly effect) have {@code V = ResourceLocation}: the genome stores the id, and {@link #resolver()}
 *     resolves the id to the behavior object on demand.</li>
 * </ul>
 *
 * @param <V> The value type stored inline in the genome for this chromosome.
 */
public interface IChromosome<V> {
	/**
	 * @return Unique ID for this chromosome.
	 */
	ResourceLocation id();

	/**
	 * @return Codec for the value stored inline in the genome. Reference chromosomes encode a {@link ResourceLocation}.
	 */
	Codec<V> valueCodec();

	/**
	 * @return Stream codec for the value, used to sync genomes over the network.
	 */
	StreamCodec<RegistryFriendlyByteBuf, V> valueStreamCodec();

	/**
	 * @return The translation key for the name of this chromosome (e.g. "chromosome.forestry.speed").
	 */
	String chromosomeTranslationKey();

	/**
	 * The naming rule for values of this chromosome. Returns a translation key only; never a
	 * {@link net.minecraft.network.chat.Component}. Consumers build components at the UI edge, falling back to the
	 * raw value when the key has no translation.
	 *
	 * @return A translation key naming the given value.
	 */
	String translationKey(V value);

	/**
	 * @return The id-to-object resolver for reference chromosomes, or {@code null} for data chromosomes.
	 */
	@Nullable
	IReferenceResolver<?> resolver();

	/**
	 * Resolves a reference chromosome's stored {@link ResourceLocation} to its behavior object and back.
	 * Resolution is lazy: it reads the code registry at call time, after registries have been populated.
	 *
	 * @param <R> The resolved behavior object type.
	 */
	interface IReferenceResolver<R> {
		R get(ResourceLocation id);

		/**
		 * @return The default dominance of the referenced value (its declared {@code isDominant()}). Used when a
		 * reference allele is created from an id without an explicit dominance (default genomes, genome overrides).
		 */
		boolean isDominant(ResourceLocation id);
	}
}
