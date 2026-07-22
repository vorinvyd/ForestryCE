package forestry.arboriculture.genetics;

import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.IForestryApi;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.core.genetics.GenomeCodecs;
import forestry.core.genetics.ISpeciesDefinition;
import forestry.core.genetics.SpeciesCore;

/**
 * Pure-data, datapack-loadable genetics layer of a tree species (the block/worldgen bindings live code-side in
 * {@link TreeBlockBindings}). Also the network sync payload. {@link #codec()}/{@link #streamCodec()} are built lazily
 * against the tree karyotype, which only exists once the tree species type is registered - see
 * {@code forestry.apiculture.genetics.BeeSpeciesDefinition} for the same pattern.
 *
 * @param genus           The scientific genus name (e.g. {@code "Quercus"}).
 * @param species         The scientific species name (e.g. {@code "robur"}).
 * @param dominant        Whether this species' species-allele is dominant.
 * @param glint           Whether this species' item/entity renders with an enchantment glint.
 * @param secret          Whether this species is hidden from the analyzer/database until discovered.
 * @param complexity      The breeding complexity used to gate research/analysis costs.
 * @param authority       The credited discoverer/author, shown in the database.
 * @param escritoireColor The color used for this species' entry in the escritoire, or {@code -1} for none.
 * @param temperature     This species' ideal temperature.
 * @param humidity        This species' ideal humidity.
 * @param rarity          The relative rarity used when picking this species for worldgen.
 * @param genome          Sparse genome overrides: chromosome id -&gt; inline allele, applied over the karyotype defaults.
 */
public record TreeSpeciesDefinition(
	String genus,
	String species,
	boolean dominant,
	boolean glint,
	boolean secret,
	int complexity,
	String authority,
	int escritoireColor,
	TemperatureType temperature,
	HumidityType humidity,
	float rarity,
	Map<ResourceLocation, Allele<?>> genome
) implements ISpeciesDefinition {
	@Nullable
	private static Codec<TreeSpeciesDefinition> codec;
	@Nullable
	private static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec;

	/**
	 * @return The lazily-built JSON/NBT codec for this record, keyed against the tree karyotype.
	 */
	public static Codec<TreeSpeciesDefinition> codec() {
		Codec<TreeSpeciesDefinition> codec = TreeSpeciesDefinition.codec;
		if (codec == null) {
			codec = buildCodec();
			TreeSpeciesDefinition.codec = codec;
		}
		return codec;
	}

	/**
	 * @return The lazily-built network stream codec for this record, keyed against the tree karyotype.
	 */
	public static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec = TreeSpeciesDefinition.streamCodec;
		if (streamCodec == null) {
			streamCodec = buildStreamCodec();
			TreeSpeciesDefinition.streamCodec = streamCodec;
		}
		return streamCodec;
	}

	private static IKaryotype karyotype() {
		return IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(ForestrySpeciesTypes.TREE).getKaryotype();
	}

	private static Codec<TreeSpeciesDefinition> buildCodec() {
		Codec<Map<ResourceLocation, Allele<?>>> genomeCodec = GenomeCodecs.alleleMapCodec(karyotype());
		return RecordCodecBuilder.create(instance -> instance.group(
			SpeciesCore.MAP_CODEC.forGetter(TreeSpeciesDefinition::core),
			Codec.FLOAT.optionalFieldOf("rarity", 0.0f).forGetter(TreeSpeciesDefinition::rarity),
			genomeCodec.optionalFieldOf("genome", Map.of()).forGetter(TreeSpeciesDefinition::genome)
		).apply(instance, (core, rarity, genome) -> new TreeSpeciesDefinition(
			core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
			core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
			rarity, genome)));
	}

	private static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> buildStreamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> genomeStreamCodec = GenomeCodecs.alleleMapStreamCodec(karyotype());
		return StreamCodec.of(
			(buf, def) -> {
				SpeciesCore.STREAM_CODEC.encode(buf, def.core());
				buf.writeFloat(def.rarity);
				genomeStreamCodec.encode(buf, def.genome);
			},
			buf -> {
				SpeciesCore core = SpeciesCore.STREAM_CODEC.decode(buf);
				float rarity = buf.readFloat();
				Map<ResourceLocation, Allele<?>> genome = genomeStreamCodec.decode(buf);
				return new TreeSpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
					core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
					rarity, genome);
			}
		);
	}
}
