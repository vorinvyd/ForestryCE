package forestry.apiculture.features;

import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.modules.ForestryModuleIds;
import forestry.apiculture.items.*;
import forestry.core.items.ItemForestry;
import forestry.core.items.ItemForestryFood;
import forestry.modules.features.*;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

@FeatureProvider
public class ApicultureItems {
	// / BEES
	public static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.APICULTURE);

	public static final FeatureItem<ItemBeeGE> BEE_QUEEN = REGISTRY.item(() -> new ItemBeeGE(BeeLifeStage.QUEEN), "queen_bee");
	public static final FeatureItem<ItemBeeGE> BEE_DRONE = REGISTRY.item(() -> new ItemBeeGE(BeeLifeStage.DRONE), "drone_bee");
	public static final FeatureItem<ItemBeeGE> BEE_PRINCESS = REGISTRY.item(() -> new ItemBeeGE(BeeLifeStage.PRINCESS), "princess_bee");
	public static final FeatureItem<ItemBeeGE> BEE_LARVAE = REGISTRY.item(() -> new ItemBeeGE(BeeLifeStage.LARVAE), "larvae_bee");

	// / COMB FRAMES
	public static final FeatureItem<ItemHiveFrame> FRAME_UNTREATED = REGISTRY.item(() -> new ItemHiveFrame(80, 0.9f), "untreated_frame");
	public static final FeatureItem<ItemHiveFrame> FRAME_IMPREGNATED = REGISTRY.item(() -> new ItemHiveFrame(240, 0.4f), "impregnated_frame");
	public static final FeatureItem<ItemHiveFrame> FRAME_PROVEN = REGISTRY.item(() -> new ItemHiveFrame(720, 0.3f), "proven_frame");
	public static final FeatureItem<ItemCreativeHiveFrame> FRAME_CREATIVE = REGISTRY.item(ItemCreativeHiveFrame::new, "creative_frame");

	// BEE RESOURCES
	public static final FeatureItem<Item> HONEY_DROP = REGISTRY.item("honey_drop");
	public static final FeatureItem<Item> HONEYDEW = REGISTRY.item("honeydew");
	public static final FeatureItem<Item> EXPERIENCE_DROP = REGISTRY.item("experience_drop");
	public static final FeatureItemGroup<ItemPropolis, EnumPropolis> PROPOLIS = REGISTRY.itemGroup(ItemPropolis::new, EnumPropolis.values()).identifier(type -> type == EnumPropolis.NORMAL ? "propolis" : type.getSerializedName() + "_propolis").create();

	public static final FeatureItem<Item> ROYAL_JELLY = REGISTRY.item("royal_jelly");

	public static final FeatureItemGroup<ItemPollenCluster, EnumPollenCluster> POLLEN_CLUSTER = REGISTRY.itemGroup(ItemPollenCluster::new, EnumPollenCluster.values()).identifier(type -> type == EnumPollenCluster.NORMAL ? "pollen_cluster" : type.getSerializedName() + "_pollen_cluster").create();
	public static final FeatureItemGroup<ItemHoneyComb, EnumHoneyComb> BEE_COMBS = REGISTRY.itemGroup(ItemHoneyComb::new, EnumHoneyComb.VALUES).identifier(type -> (type == EnumHoneyComb.SPONGE ? "spongy" : type.getSerializedName()) + "_comb").create();

	// / BEE FOOD PRODUCTS
	public static final FeatureItem<ItemForestryFood> HONEYED_SLICE = REGISTRY.item(() -> new ItemForestryFood(8, 0.6f), "honeyed_slice");
	public static final FeatureItem<ItemForestryFood> AMBROSIA = REGISTRY.item(() -> new ItemAmbrosia().setIsDrink(), "ambrosia");
	public static final FeatureItem<ItemForestryFood> HONEY_POT = REGISTRY.item(() -> new ItemForestryFood(2, 0.2f).setIsDrink(), "honey_pot");

	// / APIARIST'S CLOTHES
	public static final FeatureItem<ItemArmorApiarist> APIARIST_HELMET = REGISTRY.item(() -> new ItemArmorApiarist(ArmorItem.Type.HELMET), "apiarists_hat");
	public static final FeatureItem<ItemArmorApiarist> APIARIST_CHEST = REGISTRY.item(() -> new ItemArmorApiarist(ArmorItem.Type.CHESTPLATE), "apiarists_shirt");
	public static final FeatureItem<ItemArmorApiarist> APIARIST_LEGS = REGISTRY.item(() -> new ItemArmorApiarist(ArmorItem.Type.LEGGINGS), "apiarists_pants");
	public static final FeatureItem<ItemArmorApiarist> APIARIST_BOOTS = REGISTRY.item(() -> new ItemArmorApiarist(ArmorItem.Type.BOOTS), "apiarists_shoes");

	// TOOLS
	public static final FeatureItem<ItemScoop> SCOOP = REGISTRY.item(ItemScoop::new, "scoop");
	public static final FeatureItem<ItemSmoker> SMOKER = REGISTRY.item(ItemSmoker::new, "bee_smoker");

	// MISC
	public static final FeatureItem<ItemForestry> AMBER_DRONE = REGISTRY.item(ItemForestry::new, "amber_drone_fossil");
}
