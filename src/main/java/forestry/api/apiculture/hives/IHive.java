package forestry.api.apiculture.hives;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public interface IHive {
	IHiveDefinition getDefinition();

	BlockState getHiveBlockState();

	List<IHiveDrop> getDrops();

	float genChance();

	void postGen(WorldGenLevel world, RandomSource rand, BlockPos pos);

	boolean isGoodBiome(Holder<Biome> biome);

	boolean isGoodHumidity(HumidityType humidity);

	boolean isGoodTemperature(TemperatureType temperature);

	boolean isValidLocation(WorldGenLevel world, BlockPos pos);

	boolean canReplace(WorldGenLevel world, BlockPos pos);

	/**
	 * Determines the position of a hive.
	 *
	 * @param level The level to generate the hive in.
	 * @param rand  The world generation random. Use this instead of the level random.
	 * @param posX  The X coordinate of the position where the hive should be generated.
	 * @param posZ  The Z coordinate of the position where the hive should be generated.
	 * @return The adjusted position where the hive should be generated or {@code null} if no hive should be generated.
	 */
	@Nullable
	BlockPos getPosForHive(WorldGenLevel level, RandomSource rand, int posX, int posZ);

	@Override
	String toString();
}
