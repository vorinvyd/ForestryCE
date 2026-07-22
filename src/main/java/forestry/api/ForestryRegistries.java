package forestry.api;

import com.mojang.serialization.MapCodec;

import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.circuits.ICircuit;
import forestry.api.genetics.ISpeciesType;
import forestry.api.mail.IPostalCarrier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

public class ForestryRegistries {
	public static final Registry<ICircuit> CIRCUIT = new RegistryBuilder<>(Keys.CIRCUIT_TYPE)
		.sync(true)
		.create();

	public static final Registry<IPostalCarrier> POSTAL_CARRIER = new RegistryBuilder<>(Keys.POSTAL_CARRIER)
		.sync(true)
		.create();

	public static final Registry<ISpeciesType<?, ?>> SPECIES_TYPE = new RegistryBuilder<>(Keys.SPECIES_TYPE)
		.sync(true)
		.create();

	/**
	 * Serializer registry for {@link IBeeEffect} primitive types, used to dispatch {@link IBeeEffect#CODEC}
	 * in datapack effect definitions. Not synced: the datapack-loaded {@link IBeeEffect} instances themselves
	 * are delivered to clients by {@code BeeEffectSyncPacket} (mirroring {@code BeeSpeciesSyncPacket}); this
	 * registry only needs to be populated on both sides at mod init to resolve the {@code "type"} dispatch key.
	 */
	public static final Registry<MapCodec<? extends IBeeEffect>> BEE_EFFECT_TYPE = new RegistryBuilder<>(Keys.BEE_EFFECT_TYPE)
		.create();

	public static class Keys {
		public static final ResourceKey<Registry<ICircuit>> CIRCUIT_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("circuit"));
		public static final ResourceKey<Registry<IPostalCarrier>> POSTAL_CARRIER = ResourceKey.createRegistryKey(ForestryConstants.forestry("postal_carrier"));
		public static final ResourceKey<Registry<ISpeciesType<?, ?>>> SPECIES_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("species_type"));
		public static final ResourceKey<Registry<MapCodec<? extends IBeeEffect>>> BEE_EFFECT_TYPE = ResourceKey.createRegistryKey(ForestryConstants.forestry("bee_effect_type"));
	}
}
