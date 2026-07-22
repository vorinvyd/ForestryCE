package forestry.api.genetics;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/**
 * Represents taxonomic ranks, sometimes called levels of classification, for an {@link ITaxon}.
 */
public enum TaxonomicRank implements StringRepresentable {
	DOMAIN(0x777fff, true),
	KINGDOM(0x77c3ff),
	PHYLUM(0x77ffb6, true),
	CLASS(0x7bff77),
	ORDER(0xbeff77),
	FAMILY(0xfffd77),
	GENUS(0xffba77);

	public static final List<TaxonomicRank> VALUES = List.of(values());

	/** Serializes a rank by its lowercase name (e.g. {@code "domain"}), used by datapack taxon definitions. */
	public static final Codec<TaxonomicRank> CODEC = StringRepresentable.fromEnum(TaxonomicRank::values);

	/** Network form of {@link #CODEC} (by ordinal), used to sync datapack taxa to clients. */
	public static final StreamCodec<RegistryFriendlyByteBuf, TaxonomicRank> STREAM_CODEC = StreamCodec.of(
		(buf, rank) -> buf.writeVarInt(rank.ordinal()),
		buf -> VALUES.get(buf.readVarInt())
	);

	private final String serializedName = name().toLowerCase(Locale.ROOT);

	private final int colour;
	private final boolean isDroppable;

	TaxonomicRank(int colour) {
		this(colour, false);
	}

	TaxonomicRank(int colour, boolean isDroppable) {
		this.colour = colour;
		this.isDroppable = isDroppable;
	}

	/**
	 * @return Colour to use for displaying this classification.
	 */
	public int getColour() {
		return this.colour;
	}

	/**
	 * @return Whether display of this classification level can be omitted in case of space constraints.
	 */
	public boolean isDroppable() {
		return this.isDroppable;
	}

	/**
	 * @return The rank below this one. Never goes past {@link #GENUS}.
	 */
	public TaxonomicRank next() {
		return VALUES.get(Mth.clamp(ordinal() + 1, 0, VALUES.size()));
	}

	@Override
	public String getSerializedName() {
		return this.serializedName;
	}
}
