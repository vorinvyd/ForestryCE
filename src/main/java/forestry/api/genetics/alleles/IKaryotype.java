package forestry.api.genetics.alleles;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import forestry.api.IForestryApi;
import forestry.api.genetics.IGenome;
import forestry.api.plugin.IGenomeBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * A karyotype is the set of all chromosomes that make up a species type's genome.
 * It defines the chromosome list, each chromosome's default allele, and the genome codecs.
 * Genomes serialize their allele values inline via each chromosome's value codec, so no global allele registry exists.
 */
public interface IKaryotype {
	Codec<IKaryotype> CODEC = ResourceLocation.CODEC.comapFlatMap(id -> {
		var type = IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypeSafe(id);
		return type != null ? DataResult.success(type.getKaryotype()) : DataResult.error(() -> "Unknown karyotype: " + id);
	}, IKaryotype::id);

	/**
	 * @return The stable ID used to serialize this karyotype.
	 */
	ResourceLocation id();

	/**
	 * @return All chromosome types of this karyotype, in the order they were defined.
	 */
	ImmutableList<IChromosome<?>> getChromosomes();

	/**
	 * Checks if this karyotype contains the given chromosome. This is the only membership/validity check; any value of
	 * the correct type is permitted for a chromosome (validity is permissive).
	 */
	boolean contains(IChromosome<?> chromosome);

	/**
	 * @return The chromosome that determines this individual's species. The genome stores the species' ID.
	 */
	IChromosome<ResourceLocation> getSpeciesChromosome();

	/**
	 * @return The chromosome in this karyotype with the given ID, or {@code null} if no such chromosome exists.
	 */
	@Nullable
	IChromosome<?> getChromosome(ResourceLocation id);

	/**
	 * @return A codec mapping a chromosome ID to its {@link IChromosome} in this karyotype, erroring on unknown IDs.
	 * Used as the key codec for genome and mutation-result dispatched maps.
	 */
	Codec<IChromosome<?>> chromosomeKeyCodec();

	/**
	 * @return The number of chromosomes in this karyotype.
	 */
	int size();

	/**
	 * @return The default allele for the given chromosome in this karyotype. For reference chromosomes this is
	 * resolved lazily (after registries are populated).
	 */
	<V> Allele<V> getDefaultAllele(IChromosome<V> chromosome);

	/**
	 * @return A map of every chromosome to its default allele. Resolved lazily for reference chromosomes.
	 */
	ImmutableMap<IChromosome<?>, Allele<?>> getDefaultAlleles();

	/**
	 * A weakly inherited chromosome's default allele is always overridden by non-default alleles during inheritance
	 * (e.g. a bee's temperature tolerance).
	 */
	boolean isWeaklyInherited(IChromosome<?> chromosome);

	/**
	 * @return The default species ID for this species type.
	 */
	ResourceLocation getDefaultSpecies();

	/**
	 * @return The codec used to serialize/deserialize genomes of this karyotype (values stored inline).
	 */
	Codec<IGenome> getGenomeCodec();

	/**
	 * @return The stream codec used to sync genomes of this karyotype over the network.
	 */
	StreamCodec<RegistryFriendlyByteBuf, IGenome> getGenomeStreamCodec();

	/**
	 * @return A new genome builder using this karyotype.
	 */
	IGenomeBuilder createGenomeBuilder();
}
