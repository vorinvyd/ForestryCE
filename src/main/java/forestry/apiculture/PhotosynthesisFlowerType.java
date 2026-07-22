package forestry.apiculture;

import forestry.api.apiculture.FlowerTypeType;
import forestry.api.apiculture.IFlowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import com.mojang.serialization.MapCodec;

public class PhotosynthesisFlowerType implements IFlowerType {
	public static final PhotosynthesisFlowerType INSTANCE = new PhotosynthesisFlowerType();
	public static final MapCodec<PhotosynthesisFlowerType> CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, PhotosynthesisFlowerType> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final FlowerTypeType<PhotosynthesisFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);

	@Override
	public boolean isAcceptableFlower(Level level, BlockPos pos) {
		return level.isDay() && level.getBrightness(LightLayer.SKY, pos) >= 15;
	}

	@Override
	public boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers) {
		return false;
	}

	@Override
	public boolean isDominant() {
		return false;
	}

	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}

	// Stateless: all instances are interchangeable. Needed by StreamCodec.unit(INSTANCE), which requires
	// encoded values to be equal() to the singleton it always decodes to.
	@Override
	public boolean equals(Object obj) {
		return obj instanceof PhotosynthesisFlowerType;
	}

	@Override
	public int hashCode() {
		return PhotosynthesisFlowerType.class.hashCode();
	}
}
