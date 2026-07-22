package forestry.apiculture.genetics;

import com.google.common.collect.ImmutableMap;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import forestry.api.IForestryApi;
import forestry.api.apiculture.IActivityType;
import forestry.api.apiculture.IApiaristTracker;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.IFlowerType;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.IProduct;
import forestry.api.genetics.*;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.api.plugin.IForestryPlugin;
import forestry.api.plugin.ISpeciesTypeBuilder;
import forestry.apiimpl.ForestryApiImpl;
import forestry.apiimpl.plugin.ApicultureRegistration;
import forestry.core.config.ForestryConfig;
import forestry.core.genetics.BreedingTracker;
import forestry.core.genetics.SpeciesType;
import forestry.core.genetics.root.BreedingTrackerManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.List;

public class BeeSpeciesType extends SpeciesType<IBeeSpecies, IBee> implements IBeeSpeciesType {
	// Reference-value registries backing the flower_type, bee_effect, activity, and jubilance chromosomes.
	private ImmutableMap<ResourceLocation, IFlowerType> flowerTypes = ImmutableMap.of();
	private ImmutableMap<ResourceLocation, IFlowerType> codeFlowerTypes = ImmutableMap.of();
	private ImmutableMap<ResourceLocation, IBeeEffect> beeEffects = ImmutableMap.of();
	private ImmutableMap<ResourceLocation, IBeeEffect> codeEffects = ImmutableMap.of();
	@Nullable
	private ImmutableMap<ResourceLocation, IActivityType> activityTypes;
	@Nullable
	private ImmutableMap<ResourceLocation, IBeeJubilance> jubilances;

	public BeeSpeciesType(IKaryotype karyotype, ISpeciesTypeBuilder builder) {
		super(ForestrySpeciesTypes.BEE, karyotype, builder);
	}

	@Override
	public IFlowerType getFlowerType(ResourceLocation id) {
		return requireValue(this.flowerTypes, id, "flower type");
	}

	@Nullable
	@Override
	public IFlowerType getFlowerTypeSafe(ResourceLocation id) {
		return valueSafe(this.flowerTypes, id);
	}

	public void setFlowerTypes(ImmutableMap<ResourceLocation, IFlowerType> flowerTypes) {
		this.flowerTypes = flowerTypes;
	}

	public ImmutableMap<ResourceLocation, IFlowerType> getCodeFlowerTypes() {
		return this.codeFlowerTypes;
	}

	@Override
	public IBeeEffect getBeeEffect(ResourceLocation id) {
		return requireValue(this.beeEffects, id, "bee effect");
	}

	public void setBeeEffects(ImmutableMap<ResourceLocation, IBeeEffect> beeEffects) {
		this.beeEffects = beeEffects;
	}

	public ImmutableMap<ResourceLocation, IBeeEffect> getCodeBeeEffects() {
		return this.codeEffects;
	}

	@Override
	public IActivityType getActivityType(ResourceLocation id) {
		return requireValue(this.activityTypes, id, "activity type");
	}

	@Override
	public IBeeJubilance getJubilance(ResourceLocation id) {
		return requireValue(this.jubilances, id, "bee jubilance");
	}

	@Nullable
	@Override
	public IBeeJubilance getJubilanceSafe(ResourceLocation id) {
		return valueSafe(this.jubilances, id);
	}

	@Override
	public ILifeStage getTypeForMutation(int position) {
		return switch (position) {
			case 0 -> BeeLifeStage.PRINCESS;
			case 1 -> BeeLifeStage.DRONE;
			case 2 -> BeeLifeStage.QUEEN;
			default -> getDefaultStage();
		};
	}

	@Override
	public boolean isDrone(ItemStack stack) {
		return getLifeStage(stack) == BeeLifeStage.DRONE;
	}

	@Override
	public boolean isMated(ItemStack stack) {
		return IIndividualHandlerItem.filter(stack, (individual, stage) -> {
			return stage == BeeLifeStage.QUEEN && individual.getMate() != null;
		});
	}

	@Override
	public IApiaristTracker getBreedingTracker(LevelAccessor level, @Nullable GameProfile profile) {
		return BreedingTrackerManager.INSTANCE.getTracker(this, level, profile);
	}

	@Override
	public String getBreedingTrackerFile(@Nullable GameProfile profile) {
		return "ApiaristTracker." + (profile == null ? "common" : profile.getId());
	}

	@Override
	public IBreedingTracker createBreedingTracker() {
		return new ApiaristTracker();
	}

	@Override
	public void initializeBreedingTracker(IBreedingTracker tracker, @Nullable Level world, @Nullable GameProfile profile) {
		if (tracker instanceof BreedingTracker apiaristTracker) {
			apiaristTracker.setLevel(world);
			apiaristTracker.setUsername(profile);
		}
	}

	@Override
	public boolean isMember(IIndividual individual) {
		return individual instanceof IBee;
	}

	@Override
	public Codec<? extends IBee> getIndividualCodec() {
		return Bee.CODEC;
	}

	@Override
	public float getResearchSuitability(IBeeSpecies species, ItemStack stack) {
		for (IProduct product : species.getProducts()) {
			if (stack.is(product.item())) {
				return 1.0f;
			}
		}
		for (IProduct product : species.getSpecialties()) {
			if (stack.is(product.item())) {
				return 1.0f;
			}
		}
		return super.getResearchSuitability(species, stack);
	}

	@Override
	public List<ItemStack> getResearchBounty(IBeeSpecies species, Level level, GameProfile researcher, IBee individual, int bountyLevel) {
		List<ItemStack> bounty = super.getResearchBounty(species, level, researcher, individual, bountyLevel);
		if (bountyLevel > 10) {
			for (IProduct stack : species.getSpecialties()) {
				bounty.add(formBountyStack(stack, bountyLevel, level.random));
			}
		}
		for (IProduct stack : species.getProducts()) {
			bounty.add(formBountyStack(stack, bountyLevel, level.random));
		}
		return bounty;
	}


	@Override
	public ImmutableMap<ResourceLocation, IBeeSpecies> handleSpeciesRegistration(List<IForestryPlugin> plugins) {
		ApicultureRegistration registration = new ApicultureRegistration(this);

		for (IForestryPlugin plugin : plugins) {
			plugin.registerApiculture(registration);
		}

		// store the reference-value registries backing the flower_type, bee_effect, and activity chromosomes
		this.codeEffects = registration.getBeeEffects();
		this.beeEffects = this.codeEffects; // bootstrap: code base alone until the first datapack load
		this.codeFlowerTypes = registration.getFlowerTypes();
		this.flowerTypes = this.codeFlowerTypes; // bootstrap: code base alone until the first datapack load
		this.activityTypes = registration.getActivityTypes();
		this.jubilances = registration.getJubilances();

		// initialize hive manager
		((ForestryApiImpl) IForestryApi.INSTANCE).setHiveManager(registration.buildHiveManager());

		// Bee species are no longer built at setup; they come exclusively from the datapack loader
		// (BeeSpeciesManager -> GeneticsReloadHandler.rebuildSpecies), which replaces this species
		// type's species map on datapack reload.
		return ImmutableMap.of();
	}

	private ItemStack formBountyStack(IProduct product, int bountyLevel, RandomSource rand) {
		double productGenChance = product.chance() * ForestryConfig.SERVER.escritoireBountyMultiplier.get();
		int productGenSuccessCounter = 0;
		for (int i = 0; i < bountyLevel; i++) {
			double randVal = rand.nextDouble();
			if (randVal < productGenChance) {
				productGenSuccessCounter++;
			}
		}
		ItemStack copy = product.createRandomStack(rand);
		int stackMaxSize = copy.getMaxStackSize();
		copy.setCount(Math.min(productGenSuccessCounter, stackMaxSize));
		return copy;
	}
}
