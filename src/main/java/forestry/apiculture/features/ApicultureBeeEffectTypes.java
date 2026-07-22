package forestry.apiculture.features;

import com.mojang.serialization.MapCodec;
import forestry.api.ForestryRegistries;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.modules.ForestryModuleIds;
import forestry.apiculture.genetics.effects.*;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@FeatureProvider
public class ApicultureBeeEffectTypes {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.APICULTURE);

	public static final DeferredRegister<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPES = REGISTRY.getRegistry(ForestryRegistries.Keys.BEE_EFFECT_TYPE);

	// Parameterized bee-effect primitives, dispatched by IBeeEffect#CODEC in datapack effect definitions.
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PotionBeeEffect>> APPLY_POTION = BEE_EFFECT_TYPES.register("apply_potion", () -> PotionBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<DamageBeeEffect>> DAMAGE_ENTITIES = BEE_EFFECT_TYPES.register("damage_entities", () -> DamageBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<TransformBlockBeeEffect>> TRANSFORM_BLOCK = BEE_EFFECT_TYPES.register("transform_block", () -> TransformBlockBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<ResurrectionBeeEffect>> RESURRECT = BEE_EFFECT_TYPES.register("resurrect", () -> ResurrectionBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<AgingBeeEffect>> AGING = BEE_EFFECT_TYPES.register("aging", () -> AgingBeeEffect.MAP_CODEC);
}
