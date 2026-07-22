package forestry.apiculture;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import forestry.api.ForestryTags;
import forestry.api.apiculture.FlowerTypeType;
import forestry.api.apiculture.IFlowerType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class TagFlowerType implements IFlowerType {
	protected final TagKey<Block> acceptableFlowers;
	protected final boolean dominant;
	@Nullable
	protected final TagKey<Biome> biomes;

	static final StreamCodec<io.netty.buffer.ByteBuf, TagKey<Block>> BLOCK_TAG_STREAM_CODEC =
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.BLOCK, rl), TagKey::location);
	static final StreamCodec<io.netty.buffer.ByteBuf, TagKey<Biome>> BIOME_TAG_STREAM_CODEC =
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.BIOME, rl), TagKey::location);

	public static final MapCodec<TagFlowerType> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		TagKey.codec(Registries.BLOCK).fieldOf("flowers").forGetter(t -> t.acceptableFlowers),
		Codec.BOOL.fieldOf("dominant").forGetter(t -> t.dominant),
		TagKey.codec(Registries.BIOME).optionalFieldOf("biomes").forGetter(t -> Optional.ofNullable(t.biomes))
	).apply(inst, (flowers, dominant, biomes) -> new TagFlowerType(flowers, dominant, biomes.orElse(null))));

	public static final StreamCodec<RegistryFriendlyByteBuf, TagFlowerType> STREAM_CODEC = StreamCodec.composite(
		BLOCK_TAG_STREAM_CODEC, t -> t.acceptableFlowers,
		ByteBufCodecs.BOOL, t -> t.dominant,
		ByteBufCodecs.optional(BIOME_TAG_STREAM_CODEC), t -> Optional.ofNullable(t.biomes),
		(flowers, dominant, biomes) -> new TagFlowerType(flowers, dominant, biomes.orElse(null)));

	public static final FlowerTypeType<TagFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);

	public TagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant) {
		this(acceptableFlowers, dominant, null);
	}

	public TagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant, @Nullable TagKey<Biome> biomes) {
		this.acceptableFlowers = acceptableFlowers;
		this.dominant = dominant;
		this.biomes = biomes;
	}

	@Override
	public boolean isAcceptableFlower(Level level, BlockPos pos) {
		if (this.biomes != null && level.getBiome(pos).is(this.biomes)) {
			return true;
		}
		return level.getBlockState(pos).is(this.acceptableFlowers);
	}

	@Override
	public boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers) {
		if (level.hasChunkAt(pos) && isPlantablePosition(level, pos)) {
			ObjectArrayList<BlockState> uniqueNearbyFlowers = new ObjectArrayList<>(new HashSet<>(nearbyFlowers));
			Util.shuffle(uniqueNearbyFlowers, level.random);

			for (BlockState state : uniqueNearbyFlowers) {
				if (state.is(ForestryTags.Blocks.PLANTABLE_FLOWERS) && state.canSurvive(level, pos)) {
					if (state.hasProperty(DoublePlantBlock.HALF)) {
						BlockPos topPos = pos.above();
						if (level.isEmptyBlock(topPos)) {
							return level.setBlockAndUpdate(pos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
								&& level.setBlockAndUpdate(topPos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
						}
					} else {
						return level.setBlockAndUpdate(pos, state);
					}
				}
			}
		}
		return false;
	}

	public boolean isPlantablePosition(Level level, BlockPos pos) {
		return level.isEmptyBlock(pos);
	}

	@Override
	public boolean isDominant() {
		return this.dominant;
	}

	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}

	public TagKey<Block> acceptableFlowers() {
		return this.acceptableFlowers;
	}

	public boolean dominant() {
		return this.dominant;
	}

	@Nullable
	public TagKey<Biome> biomes() {
		return this.biomes;
	}
}
