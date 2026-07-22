package forestry.arboriculture.blocks;

import forestry.api.arboriculture.genetics.IFruit;
import forestry.api.core.IBlockSubtype;
import forestry.core.utils.SpeciesUtil;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import forestry.api.arboriculture.ForestryFruits;

public enum ForestryPodType implements IBlockSubtype {
	COCOA(ForestryFruits.COCOA),
	//TODO: change all of these to be 'bunches'. Could also be used for Bananas?
	DATES(ForestryFruits.DATES),
	PAPAYA(ForestryFruits.PAPAYA),
	COCONUT(ForestryFruits.COCONUT);

	private final ResourceLocation fruitId;

	ForestryPodType(ResourceLocation fruitId) {
		this.fruitId = fruitId;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public IFruit getFruit() {
		return SpeciesUtil.TREE_TYPE.get().getFruit(this.fruitId);
	}
}
