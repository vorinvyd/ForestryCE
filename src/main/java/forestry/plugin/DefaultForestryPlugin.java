package forestry.plugin;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.*;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.arboriculture.ForestryFruits;
import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.genetics.TreeLifeStage;
import forestry.api.circuits.ForestryCircuitLayouts;
import forestry.api.circuits.ForestryCircuitSocketTypes;
import forestry.api.client.plugin.IClientRegistration;
import forestry.api.core.ForestryError;
import forestry.api.core.IError;
import forestry.api.core.Product;
import forestry.api.farming.ForestryFarmTypes;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.ButterflyLifeStage;
import forestry.api.plugin.*;
import forestry.apiculture.*;
import forestry.apiculture.features.ApicultureEffects;
import forestry.apiculture.features.ApicultureItems;
import forestry.apiculture.genetics.BeeSpeciesType;
import forestry.apiculture.genetics.DefaultBeeJubilance;
import forestry.apiculture.genetics.HermitBeeJubilance;
import forestry.apiculture.genetics.effects.*;
import forestry.apiculture.hives.HiveDefinition;
import forestry.apiculture.items.EnumHoneyComb;
import forestry.arboriculture.ArboricultureFilterRuleType;
import forestry.arboriculture.DummyFruit;
import forestry.arboriculture.PodFruit;
import forestry.arboriculture.RipeningFruit;
import forestry.arboriculture.blocks.ForestryPodType;
import forestry.arboriculture.genetics.BlossomingTreeEffect;
import forestry.arboriculture.genetics.DummyTreeEffect;
import forestry.arboriculture.genetics.TreePollenType;
import forestry.arboriculture.genetics.TreeSpeciesType;
import forestry.core.features.CoreItems;
import forestry.core.items.ItemFruit;
import forestry.core.items.definitions.EnumCraftingMaterial;
import forestry.core.items.definitions.EnumElectronTube;
import forestry.factory.circuits.CircuitMachineUpgrade;
import forestry.farming.circuits.CircuitFarmLogic;
import forestry.lepidopterology.DummyButterflyEffect;
import forestry.lepidopterology.LepidopterologyFilterRule;
import forestry.lepidopterology.LepidopterologyFilterRuleType;
import forestry.lepidopterology.genetics.ButterflySpeciesType;
import forestry.lepidopterology.genetics.DefaultCocoon;
import forestry.plugin.client.DefaultForestryClientRegistration;
import forestry.sorting.DefaultFilterRuleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import forestry.api.apiculture.ForestryActivityTypes;
import forestry.api.apiculture.ForestryBeeEffects;
import forestry.api.lepidopterology.ForestryButterflyEffects;
import forestry.api.lepidopterology.ForestryCocoons;
import forestry.api.apiculture.ForestryFlowerTypes;

public class DefaultForestryPlugin implements IForestryPlugin {
	public static final ResourceLocation ID = ForestryConstants.forestry("default");

	@Override
	public void registerGenetics(IGeneticRegistration genetics) {
		// Bee type
		genetics.registerSpeciesType(ForestrySpeciesTypes.BEE, BeeSpeciesType::new)
			.setKaryotype(karyotype -> {
				karyotype.setSpecies(BeeChromosomes.SPECIES, ForestryBeeSpecies.FOREST);
				karyotype.set(BeeChromosomes.SPEED, ForestryAlleles.SPEED_SLOWEST);
				karyotype.set(BeeChromosomes.LIFESPAN, ForestryAlleles.LIFESPAN_SHORTER);
				karyotype.set(BeeChromosomes.FERTILITY, ForestryAlleles.FERTILITY_2);
				karyotype.set(BeeChromosomes.TEMPERATURE_TOLERANCE, ForestryAlleles.TOLERANCE_NONE)
					.setWeaklyInherited(true);
				karyotype.set(BeeChromosomes.HUMIDITY_TOLERANCE, ForestryAlleles.TOLERANCE_NONE)
					.setWeaklyInherited(true);
				karyotype.set(BeeChromosomes.ACTIVITY, ForestryActivityTypes.DIURNAL)
					.setWeaklyInherited(true);
				karyotype.set(BeeChromosomes.CAVE_DWELLING, false)
					.setWeaklyInherited(true);
				karyotype.set(BeeChromosomes.TOLERATES_RAIN, false)
					.setWeaklyInherited(true);
				karyotype.set(BeeChromosomes.FLOWER_TYPE, ForestryFlowerTypes.VANILLA);
				karyotype.set(BeeChromosomes.TERRITORY, ForestryAlleles.TERRITORY_AVERAGE);
				karyotype.set(BeeChromosomes.EFFECT, ForestryBeeEffects.NONE);
				karyotype.set(BeeChromosomes.POLLINATION, ForestryAlleles.POLLINATION_SLOWEST);
			})
			.addStages(BeeLifeStage.DRONE, BeeLifeStage.PRINCESS, BeeLifeStage.QUEEN, BeeLifeStage.LARVAE)
			.setDefaultStage(BeeLifeStage.DRONE);

		// Tree type
		genetics.registerSpeciesType(ForestrySpeciesTypes.TREE, TreeSpeciesType::new)
			.setKaryotype(karyotype -> {
				karyotype.setSpecies(TreeChromosomes.SPECIES, ForestryTreeSpecies.OAK);
				karyotype.set(TreeChromosomes.HEIGHT, ForestryAlleles.HEIGHT_SMALL);
				karyotype.set(TreeChromosomes.SAPLINGS, ForestryAlleles.SAPLINGS_LOWER);
				karyotype.set(TreeChromosomes.FRUIT, ForestryFruits.NONE);
				karyotype.set(TreeChromosomes.YIELD, ForestryAlleles.YIELD_LOWEST);
				karyotype.set(TreeChromosomes.SAPPINESS, ForestryAlleles.SAPPINESS_LOWEST);
				karyotype.set(TreeChromosomes.EFFECT, ForestryConstants.forestry("tree_effect_none"));
				karyotype.set(TreeChromosomes.MATURATION, ForestryAlleles.MATURATION_AVERAGE);
				karyotype.set(TreeChromosomes.GIRTH, ForestryAlleles.GIRTH_1);
				karyotype.set(TreeChromosomes.FIREPROOF, false);
			})
			.addStages(TreeLifeStage.SAPLING, TreeLifeStage.POLLEN)
			.setDefaultStage(TreeLifeStage.SAPLING);

		// Butterfly type
		genetics.registerSpeciesType(ForestrySpeciesTypes.BUTTERFLY, ButterflySpeciesType::new)
			.setKaryotype(karyotype -> {
				karyotype.setSpecies(ButterflyChromosomes.SPECIES, ForestryButterflySpecies.MONARCH);
				karyotype.set(ButterflyChromosomes.SIZE, ForestryAlleles.SIZE_SMALL);
				karyotype.set(ButterflyChromosomes.SPEED, ForestryAlleles.SPEED_SLOWEST);
				karyotype.set(ButterflyChromosomes.LIFESPAN, ForestryAlleles.LIFESPAN_SHORTER);
				karyotype.set(ButterflyChromosomes.METABOLISM, ForestryAlleles.METABOLISM_SLOWER);
				karyotype.set(ButterflyChromosomes.FERTILITY, ForestryAlleles.FERTILITY_3);
				karyotype.set(ButterflyChromosomes.TEMPERATURE_TOLERANCE, ForestryAlleles.TOLERANCE_NONE)
					.setWeaklyInherited(true);
				karyotype.set(ButterflyChromosomes.HUMIDITY_TOLERANCE, ForestryAlleles.TOLERANCE_NONE)
					.setWeaklyInherited(true);
				karyotype.set(ButterflyChromosomes.NEVER_SLEEPS, false)
					.setWeaklyInherited(true);
				karyotype.set(ButterflyChromosomes.TOLERATES_RAIN, false)
					.setWeaklyInherited(true);
				karyotype.set(ButterflyChromosomes.FIREPROOF, false);
				karyotype.set(ButterflyChromosomes.FLOWER_TYPE, ForestryFlowerTypes.VANILLA);
				karyotype.set(ButterflyChromosomes.EFFECT, ForestryButterflyEffects.NONE);
				karyotype.set(ButterflyChromosomes.COCOON, ForestryCocoons.DEFAULT);
			})
			.addStages(ButterflyLifeStage.BUTTERFLY, ButterflyLifeStage.SERUM, ButterflyLifeStage.CATERPILLAR, ButterflyLifeStage.COCOON)
			.setDefaultStage(ButterflyLifeStage.BUTTERFLY)
			.addResearchMaterials(map -> map.put(Items.GLASS_BOTTLE, 0.9f));

		// Taxonomy is no longer registered in code here: base Forestry's whole taxonomy ships as datapack JSON
		// (generated by TaxonProvider from ForestryTaxonomy) and is merged into the live taxonomy on datapack
		// (re)load by TaxonManager, before species are projected.

		// Filter rules for the Genetic Filter
		genetics.registerFilterRuleTypes(DefaultFilterRuleType.values());
		genetics.registerFilterRuleTypes(ApicultureFilterRuleType.values());
		genetics.registerFilterRuleTypes(ArboricultureFilterRuleType.values());
		genetics.registerFilterRuleTypes(LepidopterologyFilterRuleType.values());
		LepidopterologyFilterRule.init();
		ApicultureFilterRule.init();
	}

	@Override
	public void registerApiculture(IApicultureRegistration apiculture) {
		// Bee species themselves are no longer registered here; they are generated as datapack JSON
		// by BeeSpeciesProvider (which calls DefaultBeeSpecies) and loaded at runtime by the
		// datapack reload listener (BeeSpeciesManager). DefaultBeeSpecies stays in the source tree
		// as datagen input only.

		// Default hives
		Supplier<List<ItemStack>> honeyComb = getHoneyComb(EnumHoneyComb.HONEY);
		Supplier<List<ItemStack>> parchedComb = getHoneyComb(EnumHoneyComb.PARCHED);
		Supplier<List<ItemStack>> silkyComb = getHoneyComb(EnumHoneyComb.SILKY);
		Supplier<List<ItemStack>> mysteriousComb = getHoneyComb(EnumHoneyComb.MYSTERIOUS);
		Supplier<List<ItemStack>> frozenComb = getHoneyComb(EnumHoneyComb.FROZEN);
		Supplier<List<ItemStack>> mossyComb = getHoneyComb(EnumHoneyComb.MOSSY);
		Supplier<List<ItemStack>> spongeComb = getHoneyComb(EnumHoneyComb.SPONGE);
		Supplier<List<ItemStack>> simmerComb = getHoneyComb(EnumHoneyComb.SIMMERING);

		apiculture.registerHive(ForestryBeeSpecies.FOREST, HiveDefinition.FOREST)
			.setGenerationChance(HiveDefinition.FOREST.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.FOREST, honeyComb, 0.7f)
			.addDrop(0.08, ForestryBeeSpecies.FOREST, honeyComb, 0.0f, Map.of(BeeChromosomes.TOLERATES_RAIN, ForestryAlleles.TRUE))
			.addDrop(0.08, ForestryBeeSpecies.VALIANT, honeyComb);

		apiculture.registerHive(ForestryBeeSpecies.MEADOWS, HiveDefinition.MEADOWS)
			.setGenerationChance(HiveDefinition.MEADOWS.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.MEADOWS, honeyComb, 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, honeyComb);

		apiculture.registerHive(ForestryBeeSpecies.MODEST, HiveDefinition.DESERT)
			.setGenerationChance(HiveDefinition.DESERT.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.MODEST, parchedComb, 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, parchedComb);

		apiculture.registerHive(ForestryBeeSpecies.TROPICAL, HiveDefinition.JUNGLE)
			.setGenerationChance(HiveDefinition.JUNGLE.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.TROPICAL, silkyComb, 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, silkyComb);

		apiculture.registerHive(ForestryBeeSpecies.ENDED, HiveDefinition.END)
			.setGenerationChance(HiveDefinition.END.defaultGenChance())
			.addDrop(0.90, ForestryBeeSpecies.ENDED, mysteriousComb, 0.7f)
			.addDrop(0.09, ForestryBeeSpecies.ENDED, mysteriousComb, 0.7f, Map.of(BeeChromosomes.EFFECT, Allele.reference(ForestryBeeEffects.PHASING)))
			.addDrop(0.03, ForestryBeeSpecies.ENDED, mysteriousComb, 0.7f, Map.of(BeeChromosomes.EFFECT, Allele.reference(ForestryBeeEffects.ASCENSION)));

		apiculture.registerHive(ForestryBeeSpecies.WINTRY, HiveDefinition.SNOW)
			.setGenerationChance(HiveDefinition.SNOW.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.WINTRY, frozenComb, 0.5f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, frozenComb);

		apiculture.registerHive(ForestryBeeSpecies.MARSHY, HiveDefinition.SWAMP)
			.setGenerationChance(HiveDefinition.SWAMP.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.MARSHY, mossyComb, 0.7f)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, mossyComb);

		apiculture.registerHive(ForestryBeeSpecies.SAVANNA, HiveDefinition.SAVANNA)
			.setGenerationChance(HiveDefinition.SAVANNA.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.SAVANNA, parchedComb, 0.7f)
			.addDrop(0.35, ForestryBeeSpecies.SAVANNA, parchedComb, 0.7f, Map.of(BeeChromosomes.EFFECT, Allele.reference(ForestryBeeEffects.AGGRESSIVE)))
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, parchedComb);

		apiculture.registerHive(ForestryBeeSpecies.LUSH, HiveDefinition.LUSH)
			.setGenerationChance(HiveDefinition.LUSH.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.LUSH, honeyComb, 0.5F)
			.addDrop(0.08, ForestryBeeSpecies.VALIANT, honeyComb);

		apiculture.registerHive(ForestryBeeSpecies.AQUATIC, HiveDefinition.AQUATIC)
			.setGenerationChance(HiveDefinition.AQUATIC.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.AQUATIC, spongeComb, 0.4F)
			.addDrop(0.03, ForestryBeeSpecies.VALIANT, spongeComb);

		apiculture.registerHive(ForestryBeeSpecies.EMBITTERED, HiveDefinition.NETHER)
			.setGenerationChance(HiveDefinition.NETHER.defaultGenChance())
			.addDrop(0.80, ForestryBeeSpecies.EMBITTERED, simmerComb, 0.7F);

		// Common village bees
		apiculture.addVillageBee(ForestryBeeSpecies.FOREST, false);
		apiculture.addVillageBee(ForestryBeeSpecies.MEADOWS, false);
		apiculture.addVillageBee(ForestryBeeSpecies.MODEST, false);
		apiculture.addVillageBee(ForestryBeeSpecies.MARSHY, false);
		apiculture.addVillageBee(ForestryBeeSpecies.WINTRY, false);
		apiculture.addVillageBee(ForestryBeeSpecies.TROPICAL, false);
		apiculture.addVillageBee(ForestryBeeSpecies.SAVANNA, false);

		// Rare village bees
		apiculture.addVillageBee(ForestryBeeSpecies.FOREST, true, Map.of(BeeChromosomes.TOLERATES_RAIN, ForestryAlleles.TRUE));
		apiculture.addVillageBee(ForestryBeeSpecies.COMMON, true, Map.of(
			BeeChromosomes.TEMPERATURE_TOLERANCE, ForestryAlleles.TOLERANCE_BOTH_1,
			BeeChromosomes.HUMIDITY_TOLERANCE, ForestryAlleles.TOLERANCE_BOTH_1
		));
		apiculture.addVillageBee(ForestryBeeSpecies.VALIANT, true);

		apiculture.registerBeeEffect(ForestryBeeEffects.NONE, new DummyBeeEffect(true));
		apiculture.registerBeeEffect(ForestryBeeEffects.RADIOACTIVE, new RadioactiveBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.CREEPER, new CreeperBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.IGNITION, new IgnitionBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.EXPLORATION, new ExplorationBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.EASTER, new DummyBeeEffect(true));
		apiculture.registerBeeEffect(ForestryBeeEffects.SNOWING, new SnowingBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.REPULSION, new RepulsionBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.FERTILE, new FertileBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.MYCOPHILIC, new FungificationBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.HAKUNA_MATATA, new PotionBeeEffectExclusive(false, ApicultureEffects.HAKUNA_MATATA, 20 * 60 * 3, 100, 1.0f, ApicultureEffects.MATATA));
		apiculture.registerBeeEffect(ForestryBeeEffects.GUARDIAN, new GuardianBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.PHASING, new PhasingBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.ASCENSION, new AscensionBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.SCULK, new SculkSpreadBeeEffect());

		apiculture.registerBeeJubilance(ForestryBeeJubilances.DEFAULT, DefaultBeeJubilance.INSTANCE);
		apiculture.registerBeeJubilance(ForestryBeeJubilances.HERMIT, HermitBeeJubilance.INSTANCE);

		apiculture.registerActivityType(ForestryActivityTypes.DIURNAL, new SingleActivityType(0, 12000, ForestryError.NOT_DAY, LightPreference.ANY));
		apiculture.registerActivityType(ForestryActivityTypes.NOCTURNAL, new SingleActivityType(12000, 24000, ForestryError.NOT_NIGHT, LightPreference.DARK));
		apiculture.registerActivityType(ForestryActivityTypes.METATURNAL, new SingleActivityType(0, 24000, ForestryError.INVALID, LightPreference.ANY));
		apiculture.registerActivityType(ForestryActivityTypes.CREPUSCULAR, new CrepuscularActivityType());
		apiculture.registerActivityType(ForestryActivityTypes.CATHEMERAL, new CathemeralActivityType());

		apiculture.registerSwarmerMaterial(ApicultureItems.ROYAL_JELLY.get(), 0.01f);
	}

	private static Supplier<List<ItemStack>> getHoneyComb(EnumHoneyComb type) {
		return () -> List.of(ApicultureItems.BEE_COMBS.stack(type));
	}

	@Override
	public void registerArboriculture(IArboricultureRegistration arboriculture) {
		DefaultTreeSpecies.register(arboriculture);

		ResourceLocation pomes = ForestryConstants.forestry("block/leaves/fruits.pomes");
		ResourceLocation nuts = ForestryConstants.forestry("block/leaves/fruits.nuts");
		ResourceLocation berries = ForestryConstants.forestry("block/leaves/fruits.berries");
		ResourceLocation citrus = ForestryConstants.forestry("block/leaves/fruits.citrus");
		ResourceLocation plums = ForestryConstants.forestry("block/leaves/fruits.plums");

		arboriculture.registerFruit(ForestryFruits.NONE, new DummyFruit(false));
		arboriculture.registerFruit(ForestryFruits.APPLE, new RipeningFruit(false, 10, pomes, 0xFF1C2B, 0xe3f49c, List.of(Product.of(Items.APPLE))));
		// todo match vanilla cocoa and use fortune OR better yet, make pod fruits use actual loot tables
		arboriculture.registerFruit(ForestryFruits.COCOA, new PodFruit(false, ForestryPodType.COCOA, List.of(Product.of(Items.COCOA_BEANS))));
		arboriculture.registerFruit(ForestryFruits.CHESTNUT, new RipeningFruit(true, 6, nuts, 0x76403C, 0xc4d24a, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.CHESTNUT)))));
		arboriculture.registerFruit(ForestryFruits.WALNUT, new RipeningFruit(true, 8, nuts, 0xBC784E, 0xc4d24a, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.WALNUT)))));
		arboriculture.registerFruit(ForestryFruits.CHERRY, new RipeningFruit(true, 10, berries, 0xCC1C10, 0xc4d24a, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.CHERRY))))); //Should be a Drupe, actually
		arboriculture.registerFruit(ForestryFruits.DATES, new PodFruit(false, ForestryPodType.DATES, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.DATES)))));
		arboriculture.registerFruit(ForestryFruits.PAPAYA, new PodFruit(false, ForestryPodType.PAPAYA, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.PAPAYA)))));
		arboriculture.registerFruit(ForestryFruits.LEMON, new RipeningFruit(true, 10, citrus, 0xFFD500, 0x99ff00, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.LEMON)))));
		arboriculture.registerFruit(ForestryFruits.PLUM, new RipeningFruit(true, 10, plums, 0x773352, 0xeeff1a, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.PLUM))))); //Should also be a drupe

		arboriculture.registerFruit(ForestryFruits.COCONUT, new PodFruit(false, ForestryPodType.COCONUT, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.COCONUT)))));
		arboriculture.registerFruit(ForestryFruits.PEAR, new RipeningFruit(true, 10, pomes, 0xD8D345, 0xE3DD9C, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.PEAR)))));
		arboriculture.registerFruit(ForestryFruits.FEIJOA, new RipeningFruit(true, 10, berries, 0x7AB15C, 0x6A7D7B, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.FEIJOA))))); //What actually is a feijoa? I couldn't find the answer.
		arboriculture.registerFruit(ForestryFruits.ORANGE, new RipeningFruit(true, 10, citrus, 0xF4842D, 0xBCA627, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.ORANGE)))));
		arboriculture.registerFruit(ForestryFruits.OLIVE, new RipeningFruit(true, 10, berries, 0xAAC348, 0x604632, List.of(Product.of(CoreItems.FRUITS.item(ItemFruit.EnumFruit.OLIVE))))); //Should also be a drupe

		arboriculture.registerTreeEffect(ForestryConstants.forestry("tree_effect_none"), new DummyTreeEffect(false));
		arboriculture.registerTreeEffect(ForestryConstants.forestry("tree_effect_blossoming"), new BlossomingTreeEffect());

		DefaultWoods.register(arboriculture);

		arboriculture.registerCharcoalPitWall(Blocks.CLAY, 3);
		arboriculture.registerCharcoalPitWall(Blocks.END_STONE, 6);
		arboriculture.registerCharcoalPitWall(Blocks.END_STONE_BRICKS, 6);
		arboriculture.registerCharcoalPitWall(Blocks.DIRT, 2);
		arboriculture.registerCharcoalPitWall(Blocks.GRAVEL, 1);
		arboriculture.registerCharcoalPitWall(Blocks.NETHERRACK, 3);
	}

	@Override
	public void registerLepidopterology(ILepidopterologyRegistration lepidopterology) {
		DefaultButterflySpecies.register(lepidopterology);

		lepidopterology.registerCocoon(ForestryCocoons.DEFAULT, new DefaultCocoon("default", List.of(
			Product.of(Items.STRING, 2, 1f),
			Product.of(Items.STRING, 1, 0.75f),
			Product.of(Items.STRING, 3, 0.25f)
		)));

		lepidopterology.registerCocoon(ForestryCocoons.SILK, new DefaultCocoon("silk", List.of(
			Product.of(CoreItems.CRAFTING_MATERIALS.item(EnumCraftingMaterial.SILK_WISP), 3, 0.75f),
			Product.of(CoreItems.CRAFTING_MATERIALS.item(EnumCraftingMaterial.SILK_WISP), 2, 0.25f)
		)));

		lepidopterology.registerEffect(ForestryButterflyEffects.NONE, new DummyButterflyEffect());
	}

	@Override
	public void registerCircuits(ICircuitRegistration circuits) {
		// Layouts
		circuits.registerLayout(ForestryCircuitLayouts.MANAGED_FARM, ForestryCircuitSocketTypes.FARM);
		circuits.registerLayout(ForestryCircuitLayouts.MANUAL_FARM, ForestryCircuitSocketTypes.FARM);
		circuits.registerLayout(ForestryCircuitLayouts.MACHINE_UPGRADE, ForestryCircuitSocketTypes.MACHINE);

		// Managed Farms
		registerFarmCircuit(circuits, EnumElectronTube.COPPER, ForestryFarmTypes.ARBOREAL, false);
		registerFarmCircuit(circuits, EnumElectronTube.TIN, ForestryFarmTypes.PEAT, false);
		registerFarmCircuit(circuits, EnumElectronTube.BRONZE, ForestryFarmTypes.CROPS, false);
		registerFarmCircuit(circuits, EnumElectronTube.IRON, ForestryFarmTypes.ENDER, false);
		registerFarmCircuit(circuits, EnumElectronTube.BLAZE, ForestryFarmTypes.INFERNAL, false);
		registerFarmCircuit(circuits, EnumElectronTube.OBSIDIAN, ForestryFarmTypes.GOURD, false);
		registerFarmCircuit(circuits, EnumElectronTube.APATITE, ForestryFarmTypes.SHROOM, false);

		// Manual Farms
		registerFarmCircuit(circuits, EnumElectronTube.COPPER, ForestryFarmTypes.ORCHARD, true);
		registerFarmCircuit(circuits, EnumElectronTube.TIN, ForestryFarmTypes.PEAT, true);
		registerFarmCircuit(circuits, EnumElectronTube.BRONZE, ForestryFarmTypes.CROPS, true);
		registerFarmCircuit(circuits, EnumElectronTube.IRON, ForestryFarmTypes.ENDER, true);
		registerFarmCircuit(circuits, EnumElectronTube.GOLD, ForestryFarmTypes.SUCCULENTES, true);
		registerFarmCircuit(circuits, EnumElectronTube.DIAMOND, ForestryFarmTypes.POALES, true);
		registerFarmCircuit(circuits, EnumElectronTube.OBSIDIAN, ForestryFarmTypes.GOURD, true);
		registerFarmCircuit(circuits, EnumElectronTube.APATITE, ForestryFarmTypes.SHROOM, true);
		registerFarmCircuit(circuits, EnumElectronTube.LAPIS, ForestryFarmTypes.COCOA, true);

		// Factory
		circuits.registerCircuit(ForestryCircuitLayouts.MACHINE_UPGRADE, CoreItems.ELECTRON_TUBES.stack(EnumElectronTube.EMERALD, 1), new CircuitMachineUpgrade("machine.speed.boost.1", 0.125f, 0.05f, 1.0f));
		circuits.registerCircuit(ForestryCircuitLayouts.MACHINE_UPGRADE, CoreItems.ELECTRON_TUBES.stack(EnumElectronTube.BLAZE, 1), new CircuitMachineUpgrade("machine.speed.boost.2", 0.250f, 0.10f, 1.0f));
		circuits.registerCircuit(ForestryCircuitLayouts.MACHINE_UPGRADE, CoreItems.ELECTRON_TUBES.stack(EnumElectronTube.GOLD, 1), new CircuitMachineUpgrade("machine.efficiency.1", 0, -0.10f, 1.0f));
		circuits.registerCircuit(ForestryCircuitLayouts.MACHINE_UPGRADE, CoreItems.ELECTRON_TUBES.stack(EnumElectronTube.AMBER, 1), new CircuitMachineUpgrade("machine.fortune.1", 0, 0.05f, 1.25f));
	}

	private static void registerFarmCircuit(ICircuitRegistration circuits, EnumElectronTube tube, ResourceLocation typeId, boolean manual) {
		String id = manual ? "farm.manual." + typeId.getPath() : "farm.managed." + typeId.getPath();
		circuits.registerCircuit(manual ? ForestryCircuitLayouts.MANUAL_FARM : ForestryCircuitLayouts.MANAGED_FARM, CoreItems.ELECTRON_TUBES.stack(tube, 1), new CircuitFarmLogic(id, typeId, manual));
	}

	@Override
	public void registerErrors(IErrorRegistration errors) {
		for (IError error : ForestryError.values()) {
			errors.registerError(error);
		}
	}

	@Override
	public void registerFarming(IFarmingRegistration farming) {
		DefaultFarms.registerFarmTypes(farming);

		farming.registerFertilizer(CoreItems.FERTILIZER_COMPOUND.get(), 500);
	}

	@Override
	public void registerPollen(IPollenRegistration pollen) {
		pollen.registerPollenType(new TreePollenType());
	}

	@Override
	public void registerClient(Consumer<Consumer<IClientRegistration>> registrar) {
		registrar.accept(new DefaultForestryClientRegistration());
	}

	@Override
	public ResourceLocation id() {
		return ID;
	}
}
