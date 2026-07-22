package forestry.api.apiculture.genetics;

import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import forestry.api.ForestryRegistries;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.core.render.ParticleRender;
import net.minecraft.core.BlockPos;

public interface IBeeEffect extends IEffect {
	/**
	 * Dispatch codec for datapack effect definitions ({@code data/<namespace>/forestry/bee_effect/<name>.json}).
	 * The {@code "type"} field is resolved against {@link ForestryRegistries#BEE_EFFECT_TYPE} to a parameterized
	 * primitive (apply_potion, transform_block, …). Effects are always type-keyed, so there is no plain fallback
	 * (mirrors {@code MutationConditionTypes#CODEC}). The reloadable {@code BeeEffectManager} decodes each entry
	 * with this codec and, via {@code GeneticsReloadHandler}, feeds the results into the bee species type's effect
	 * map before species are (re)built, so a species genome can reference a datapack effect allele by its entry key.
	 */
	Codec<IBeeEffect> CODEC = ForestryRegistries.BEE_EFFECT_TYPE.byNameCodec().dispatch("type", IBeeEffect::codec, Function.identity());

	/**
	 * @return The serializer used to (de)serialize this effect in a datapack effect definition. Only primitives
	 * that are meant to be datapack-configurable override this; code-only base effects never pass through the
	 * datapack registry, so they inherit the throwing default.
	 */
	default MapCodec<? extends IBeeEffect> codec() {
		throw new UnsupportedOperationException(getClass().getName() + " is not a datapack-serializable bee effect (no codec())");
	}

	/**
	 * @return Whether the allele for this value is dominant or recessive.
	 */
	boolean isDominant();

	@Override
	default IEffectData validateStorage(IEffectData storedData) {
		return storedData;
	}

	@Override
	default boolean isCombinable() {
		return false;
	}

	/**
	 * Called by apiaries to cause an effect in the world. (server)
	 *
	 * @param genome     Genome of the bee queen causing this effect
	 * @param storedData Object containing the stored effect data for the apiary/hive the bee is in.
	 * @param housing    {@link IBeeHousing} the bee currently resides in.
	 * @return storedData, may have been manipulated.
	 */
	default IEffectData doEffect(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		return storedData;
	}

	/**
	 * Called on the client side to produce visual bee effects.
	 *
	 * @param genome     Genome of the bee queen causing this effect
	 * @param storedData Object containing the stored effect data for the apiary/hive the bee is in.
	 * @param housing    {@link IBeeHousing} the bee currently resides in.
	 * @return storedData, may have been manipulated.
	 */
	default IEffectData doFX(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		IBeekeepingLogic beekeepingLogic = housing.getBeekeepingLogic();
		List<BlockPos> flowerPositions = beekeepingLogic.getFlowerPositions();

		ParticleRender.addBeeHiveFX(housing, genome, flowerPositions);
		return storedData;
	}
}
