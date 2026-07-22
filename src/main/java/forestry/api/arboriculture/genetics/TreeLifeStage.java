package forestry.api.arboriculture.genetics;

import forestry.api.genetics.ILifeStage;
import forestry.arboriculture.features.ArboricultureItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.Locale;

public enum TreeLifeStage implements ILifeStage {
	SAPLING(ArboricultureItems.TREE_SAPLING),
	POLLEN(ArboricultureItems.TREE_POLLEN);

	private final String name;
	private final ItemLike itemForm;

	TreeLifeStage(ItemLike itemForm) {
		this.name = name().toLowerCase(Locale.ENGLISH);
		this.itemForm = itemForm;
	}

	public String getSerializedName() {
		return this.name;
	}

	@Override
	public Item getItemForm() {
		return this.itemForm.asItem();
	}
}
