package forestry.core.genetics;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.IForestryApi;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.TaxonomicRank;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IKaryotype;

/**
 * A datapack-loadable definition of a taxon (a node in the classification tree), loaded through the reloadable
 * {@link forestry.apiculture.genetics.TaxonManager} (a {@code SimpleJsonResourceReloadListener} over the {@code taxon}
 * folder, synced by {@code TaxonSyncPacket}) and merged into the live taxonomy by {@code GeneticManager#applyDatapackTaxa}
 * on every (re)load, before species are projected.
 * <p>
 * Since the base Forestry taxonomy is itself shipped as generated datapack JSON (see {@code TaxonProvider}, mirroring the
 * flower-type/bee-effect/species migrations), a definition can describe any node in the tree:
 * <ul>
 *     <li>{@link #parent} is {@code null} for a root {@link TaxonomicRank#DOMAIN domain}; otherwise it names the taxon
 *     this one hangs under.</li>
 *     <li>{@link #rank} is optional: when absent it is derived from the parent's rank ({@code parent.rank().next()}), so
 *     an add-on adding a genus only needs {@code parent} + {@code name}. It must be given for a parentless domain.</li>
 *     <li>{@link #alleles} are the taxon's default chromosomes, inherited by every species whose genus lies under this
 *     taxon (flattened into the genome by {@code SpeciesRegistration#createDefaultGenomeBuilder} at projection time).
 *     They are serialized exactly like a species' genome overrides (a sparse {@code chromosome id -> allele} map), which
 *     requires a karyotype to resolve the chromosome ids against — hence {@link #type}, the id of the owning species type
 *     (e.g. {@code forestry:bee_species}). {@code type} is required iff {@code alleles} is non-empty.</li>
 * </ul>
 */
public record TaxonDefinition(@Nullable String parent, String name, @Nullable TaxonomicRank rank,
							  @Nullable ResourceLocation type, Map<ResourceLocation, Allele<?>> alleles) {
	/** Convenience form for a structural taxon (no explicit rank, no default alleles) — used by add-ons and tests. */
	public TaxonDefinition(@Nullable String parent, String name) {
		this(parent, name, null, null, Map.of());
	}

	// Intermediate form holding the still-raw (karyotype-dependent) alleles as a Dynamic, so the alleles field can be
	// decoded against the karyotype named by the sibling `type` field. See CODEC.
	private record Unresolved(Optional<String> parent, String name, Optional<TaxonomicRank> rank,
							  Optional<ResourceLocation> type, Optional<Dynamic<?>> alleles) {
	}

	private static final Codec<Unresolved> UNRESOLVED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("parent").forGetter(Unresolved::parent),
		Codec.STRING.fieldOf("name").forGetter(Unresolved::name),
		TaxonomicRank.CODEC.optionalFieldOf("rank").forGetter(Unresolved::rank),
		ResourceLocation.CODEC.optionalFieldOf("type").forGetter(Unresolved::type),
		Codec.PASSTHROUGH.optionalFieldOf("alleles").forGetter(Unresolved::alleles)
	).apply(instance, Unresolved::new));

	public static final Codec<TaxonDefinition> CODEC = UNRESOLVED_CODEC.comapFlatMap(TaxonDefinition::resolve, TaxonDefinition::unresolve);

	/** Used by {@code TaxonSyncPacket} to deliver datapack taxa to remote clients (a species' genus resolves there too). */
	public static final StreamCodec<RegistryFriendlyByteBuf, TaxonDefinition> STREAM_CODEC = StreamCodec.of(
		TaxonDefinition::encode, TaxonDefinition::decode
	);

	private static DataResult<TaxonDefinition> resolve(Unresolved u) {
		String parent = u.parent.orElse(null);
		TaxonomicRank rank = u.rank.orElse(null);
		ResourceLocation type = u.type.orElse(null);
		Optional<Dynamic<?>> rawAlleles = u.alleles;

		if (rawAlleles.isEmpty()) {
			return DataResult.success(new TaxonDefinition(parent, u.name, rank, type, Map.of()));
		}
		if (type == null) {
			return DataResult.error(() -> "Taxon '" + u.name + "' declares alleles but no 'type' to resolve their chromosomes against");
		}
		IKaryotype karyotype = karyotypeFor(type);
		if (karyotype == null) {
			return DataResult.error(() -> "Taxon '" + u.name + "' references unknown species type '" + type + "'");
		}
		return GenomeCodecs.alleleMapCodec(karyotype).parse(rawAlleles.get())
			.map(alleles -> new TaxonDefinition(parent, u.name, rank, type, alleles));
	}

	private static Unresolved unresolve(TaxonDefinition def) {
		Optional<Dynamic<?>> alleles;
		if (def.alleles.isEmpty()) {
			alleles = Optional.empty();
		} else {
			IKaryotype karyotype = karyotypeFor(def.type);
			if (karyotype == null) {
				throw new IllegalStateException("Cannot encode alleles for taxon '" + def.name + "': unknown species type '" + def.type + "'");
			}
			JsonElement json = GenomeCodecs.alleleMapCodec(karyotype).encodeStart(JsonOps.INSTANCE, def.alleles).getOrThrow();
			alleles = Optional.of(new Dynamic<>(JsonOps.INSTANCE, json));
		}
		return new Unresolved(Optional.ofNullable(def.parent), def.name, Optional.ofNullable(def.rank), Optional.ofNullable(def.type), alleles);
	}

	@Nullable
	private static IKaryotype karyotypeFor(@Nullable ResourceLocation typeId) {
		if (typeId == null) {
			return null;
		}
		ISpeciesType<?, ?> type = IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypeSafe(typeId);
		return type == null ? null : type.getKaryotype();
	}

	private static void encode(RegistryFriendlyByteBuf buf, TaxonDefinition def) {
		buf.writeNullable(def.parent, FriendlyByteBuf::writeUtf);
		buf.writeUtf(def.name);
		buf.writeNullable(def.rank, (b, rank) -> TaxonomicRank.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, rank));
		buf.writeNullable(def.type, FriendlyByteBuf::writeResourceLocation);
		if (def.type != null && !def.alleles.isEmpty()) {
			buf.writeBoolean(true);
			IKaryotype karyotype = karyotypeFor(def.type);
			if (karyotype == null) {
				throw new IllegalStateException("Cannot sync alleles for taxon '" + def.name + "': unknown species type '" + def.type + "'");
			}
			GenomeCodecs.alleleMapStreamCodec(karyotype).encode(buf, def.alleles);
		} else {
			buf.writeBoolean(false);
		}
	}

	private static TaxonDefinition decode(RegistryFriendlyByteBuf buf) {
		String parent = buf.readNullable(FriendlyByteBuf::readUtf);
		String name = buf.readUtf();
		TaxonomicRank rank = buf.readNullable(b -> TaxonomicRank.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b));
		ResourceLocation type = buf.readNullable(FriendlyByteBuf::readResourceLocation);
		Map<ResourceLocation, Allele<?>> alleles = Map.of();
		if (buf.readBoolean()) {
			IKaryotype karyotype = karyotypeFor(type);
			if (karyotype == null) {
				throw new IllegalStateException("Cannot read synced alleles for taxon '" + name + "': unknown species type '" + type + "'");
			}
			alleles = GenomeCodecs.alleleMapStreamCodec(karyotype).decode(buf);
		}
		return new TaxonDefinition(parent, name, rank, type, alleles);
	}
}
