package forestry.core.genetics.alleles;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.alleles.IChromosome;
import forestry.core.utils.GeneticsUtil;

/**
 * Factory for the generic {@link Chromosome}. Replaces the chromosome-creation methods of the old {@code IAlleleManager}.
 * Chromosomes are plain singletons created in the chromosome holder classes (BeeChromosomes, TreeChromosomes, ...).
 */
public final class ChromosomeFactory {
	private ChromosomeFactory() {
	}

	public static IChromosome<Float> floatChromosome(ResourceLocation id) {
		return new Chromosome<>(id, Codec.FLOAT, dataNaming(id, String::valueOf), null);
	}

	public static IChromosome<Integer> intChromosome(ResourceLocation id) {
		return new Chromosome<>(id, Codec.INT, dataNaming(id, String::valueOf), null);
	}

	public static IChromosome<Boolean> booleanChromosome(ResourceLocation id) {
		// Booleans are universally Yes/No, so every boolean chromosome shares one key per value
		// (allele.<ns>.true / allele.<ns>.false) instead of a per-chromosome key. This matches the long-standing
		// allele.forestry.true = "Yes" / allele.forestry.false = "No" lang entries.
		String base = "allele." + id.getNamespace() + '.';
		return new Chromosome<>(id, Codec.BOOL, value -> base + value, null);
	}

	/**
	 * A data chromosome holding an enum, record, or other non-primitive value.
	 *
	 * @param valueKeyPart Maps a value to its translation-key suffix (e.g. enum name, "x_y_z" for Vec3i).
	 */
	public static <V> IChromosome<V> valueChromosome(ResourceLocation id, Codec<V> valueCodec, Function<V, String> valueKeyPart) {
		return new Chromosome<>(id, valueCodec, dataNaming(id, valueKeyPart), null);
	}

	/**
	 * A reference chromosome: the genome stores a {@link ResourceLocation}, resolved to a behavior object on demand.
	 * The translation key reproduces the existing scheme ("allele.&lt;ns&gt;.&lt;chromosome&gt;.&lt;ref path&gt;").
	 *
	 * @param get       Resolves an id to its behavior object (lazily, after registries are populated).
	 * @param dominant  Reads the default dominance of a resolved value.
	 */
	public static <R> IChromosome<ResourceLocation> referenceChromosome(ResourceLocation id, Function<ResourceLocation, R> get, Predicate<R> dominant) {
		IChromosome.IReferenceResolver<R> resolver = new IChromosome.IReferenceResolver<>() {
			@Override
			public R get(ResourceLocation refId) {
				return get.apply(refId);
			}

			@Override
			public boolean isDominant(ResourceLocation refId) {
				return dominant.test(get.apply(refId));
			}
		};
		return new Chromosome<>(id, ResourceLocation.CODEC, refId -> GeneticsUtil.createTranslationKey("allele", id, refId), resolver);
	}

	private static <V> Function<V, String> dataNaming(ResourceLocation id, Function<V, String> valueKeyPart) {
		String base = "allele." + id.getNamespace() + '.' + id.getPath() + '.';
		return value -> base + sanitize(valueKeyPart.apply(value));
	}

	private static String sanitize(String raw) {
		return raw.toLowerCase(Locale.ROOT).replace('.', '_').replace('-', 'n');
	}
}
