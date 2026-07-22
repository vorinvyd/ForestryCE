package forestry.arboriculture.features;

import forestry.api.arboriculture.genetics.TreeLifeStage;
import forestry.api.modules.ForestryModuleIds;
import forestry.arboriculture.ForestryWoodType;
import forestry.arboriculture.items.ItemForestryBoat;
import forestry.arboriculture.items.TreeItem;
import forestry.arboriculture.items.GrafterItem;
import forestry.core.items.ItemForestry;
import forestry.modules.features.*;

@FeatureProvider
public class ArboricultureItems {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.ARBORICULTURE);

	public static final FeatureItem<TreeItem> TREE_SAPLING = REGISTRY.item(() -> new TreeItem(TreeLifeStage.SAPLING), "tree_sapling");
	public static final FeatureItem<TreeItem> TREE_POLLEN = REGISTRY.item(() -> new TreeItem(TreeLifeStage.POLLEN), "tree_pollen");
	public static final FeatureItem<GrafterItem> GRAFTER = REGISTRY.item(() -> new GrafterItem(9), "grafter");
	public static final FeatureItem<GrafterItem> PROVEN_GRAFTER = REGISTRY.item(() -> new GrafterItem(149), "proven_grafter");
	// If you want to implement boats in your addon, look at ItemForestryBoat, ForestryBoat, ForestryChestBoat, and ForestryBoatRenderer
	public static final FeatureItemGroup<ItemForestryBoat, ForestryWoodType> BOAT = REGISTRY.itemGroup(type -> new ItemForestryBoat(type, false), ForestryWoodType.VALUES).identifier("boat", FeatureGroup.IdentifierType.SUFFIX).create();
	public static final FeatureItemGroup<ItemForestryBoat, ForestryWoodType> CHEST_BOAT = REGISTRY.itemGroup(type -> new ItemForestryBoat(type, true), ForestryWoodType.VALUES).identifier("chest_boat", FeatureGroup.IdentifierType.SUFFIX).create();

	// MISC
	public static final FeatureItem<ItemForestry> AMBER_SAPLING_FOSSIL = REGISTRY.item(ItemForestry::new, "amber_sapling_fossil");
}
