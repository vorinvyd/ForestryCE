package forestry.core.genetics.mutations;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.climate.IClimateProvider;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

// todo separate classes for single biome and tag
public class MutationConditionBiome implements IMutationCondition {
	public static final MapCodec<MutationConditionBiome> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		TagKey.codec(Registries.BIOME).fieldOf("biome").forGetter(MutationConditionBiome::getBiome)
	).apply(instance, MutationConditionBiome::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionBiome> STREAM_CODEC = StreamCodec.composite(
		ResourceLocation.STREAM_CODEC.map(location -> TagKey.create(Registries.BIOME, location), TagKey::location),
		MutationConditionBiome::getBiome,
		MutationConditionBiome::new
	);
	public static final MutationConditionType<MutationConditionBiome> TYPE = new MutationConditionType<>(CODEC, STREAM_CODEC);

	private final TagKey<Biome> validBiomes;

	public MutationConditionBiome(TagKey<Biome> validBiomes) {
		this.validBiomes = validBiomes;
	}

	public TagKey<Biome> getBiome() {
		return this.validBiomes;
	}

	@Override
	public float modifyChance(Level level, BlockPos pos, IMutation<?> mutation, IGenome genome0, IGenome genome1, IClimateProvider climate, float currentChance) {
		return level.getBiome(pos).is(this.validBiomes) ? currentChance : 0f;
	}

	@Override
	public Component getDescription() {
		String biomeType = this.validBiomes.location().toString();
		return Component.translatable("for.mutation.condition.biome.single", biomeType);
	}

	@Override
	public MutationConditionType<?> type() {
		return TYPE;
	}
}
