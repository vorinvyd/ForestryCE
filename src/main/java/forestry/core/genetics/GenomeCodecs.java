package forestry.core.genetics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;

/**
 * Karyotype-aware codecs for a sparse map of chromosome-id to inline {@link Allele}, shared by every place that
 * serializes a partial set of genome overrides (mutation result alleles, species genome overrides, ...).
 * <p>
 * Keys are chromosome IDs; each value is a single {@link Allele} serialized inline via the chromosome's own value
 * codec. Both the JSON/NBT codec and the network stream codec are keyed against a specific {@link IKaryotype}, which
 * is only available once the owning species type has been registered - callers build these lazily.
 */
public final class GenomeCodecs {
	private GenomeCodecs() {
	}

	/**
	 * @return A codec for a sparse {@code chromosome id -> allele} map, keyed against the given karyotype.
	 */
	public static Codec<Map<ResourceLocation, Allele<?>>> alleleMapCodec(IKaryotype karyotype) {
		return Codec.<IChromosome<?>, Allele<?>>dispatchedMap(karyotype.chromosomeKeyCodec(), chromosome -> Allele.codec(chromosome.valueCodec()))
			.xmap(
				byChromosome -> {
					Map<ResourceLocation, Allele<?>> byId = new LinkedHashMap<>(byChromosome.size());
					byChromosome.forEach((chromosome, allele) -> byId.put(chromosome.id(), allele));
					return byId;
				},
				byId -> {
					Map<IChromosome<?>, Allele<?>> byChromosome = new LinkedHashMap<>(byId.size());
					byId.forEach((id, allele) -> {
						// Unknown chromosome ids are dropped on encode; a parsed map only ever holds valid ids.
						IChromosome<?> chromosome = karyotype.getChromosome(id);
						if (chromosome != null) {
							byChromosome.put(chromosome, allele);
						}
					});
					return byChromosome;
				}
			);
	}

	/**
	 * @return A stream codec for a sparse {@code chromosome id -> allele} map, keyed against the given karyotype.
	 */
	public static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> alleleMapStreamCodec(IKaryotype karyotype) {
		return StreamCodec.of(
			(buf, map) -> {
				buf.writeVarInt(map.size());
				map.forEach((id, allele) -> {
					IChromosome<?> chromosome = karyotype.getChromosome(id);
					if (chromosome == null) {
						throw new IllegalStateException("Unknown chromosome in genome allele map: " + id);
					}
					ResourceLocation.STREAM_CODEC.encode(buf, id);
					encodeAllele(buf, chromosome, allele);
				});
			},
			buf -> {
				int size = buf.readVarInt();
				Map<ResourceLocation, Allele<?>> map = new LinkedHashMap<>(size);
				for (int i = 0; i < size; i++) {
					ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
					IChromosome<?> chromosome = karyotype.getChromosome(id);
					if (chromosome == null) {
						throw new IllegalStateException("Unknown chromosome in genome allele map: " + id);
					}
					map.put(id, Allele.streamCodec(chromosome.valueStreamCodec()).decode(buf));
				}
				return map;
			}
		);
	}

	@SuppressWarnings("unchecked")
	private static <V> void encodeAllele(RegistryFriendlyByteBuf buf, IChromosome<V> chromosome, Allele<?> allele) {
		Allele.streamCodec(chromosome.valueStreamCodec()).encode(buf, (Allele<V>) allele);
	}
}
