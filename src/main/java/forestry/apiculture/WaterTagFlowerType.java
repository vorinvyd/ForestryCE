package forestry.apiculture;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import forestry.api.apiculture.FlowerTypeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class WaterTagFlowerType extends TagFlowerType {
	public static final MapCodec<WaterTagFlowerType> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		TagKey.codec(Registries.BLOCK).fieldOf("flowers").forGetter(TagFlowerType::acceptableFlowers),
		Codec.BOOL.fieldOf("dominant").forGetter(TagFlowerType::dominant),
		TagKey.codec(Registries.BIOME).optionalFieldOf("biomes").forGetter(t -> Optional.ofNullable(t.biomes()))
	).apply(inst, (flowers, dominant, biomes) -> new WaterTagFlowerType(flowers, dominant, biomes.orElse(null))));

	public static final StreamCodec<RegistryFriendlyByteBuf, WaterTagFlowerType> STREAM_CODEC = StreamCodec.composite(
		TagFlowerType.BLOCK_TAG_STREAM_CODEC, TagFlowerType::acceptableFlowers,
		ByteBufCodecs.BOOL, TagFlowerType::dominant,
		ByteBufCodecs.optional(TagFlowerType.BIOME_TAG_STREAM_CODEC), t -> Optional.ofNullable(t.biomes()),
		(flowers, dominant, biomes) -> new WaterTagFlowerType(flowers, dominant, biomes.orElse(null)));

	public static final FlowerTypeType<WaterTagFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);

	public WaterTagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant) {
		super(acceptableFlowers, dominant);
	}

	public WaterTagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant, @Nullable TagKey<Biome> biomes) {
		super(acceptableFlowers, dominant, biomes);
	}

	@Override
	public boolean isPlantablePosition(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() == Blocks.WATER;
	}

	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}
}
