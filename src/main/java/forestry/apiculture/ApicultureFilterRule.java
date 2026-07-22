package forestry.apiculture;

import forestry.api.apiculture.IActivityType;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.filter.FilterData;
import forestry.api.genetics.filter.IFilterRule;
import forestry.api.genetics.filter.IFilterRuleType;
import forestry.sorting.DefaultFilterRuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import forestry.api.apiculture.ForestryActivityTypes;

public enum ApicultureFilterRule implements IFilterRule {
	PURE_BREED(DefaultFilterRuleType.PURE_BREED) {
		@Override
		protected boolean isValid(IBee bee) {
			// Alleles are value records (not interned), so compare by value, not reference.
			return bee.getGenome().getAllelePair(BeeChromosomes.SPECIES).isSameAlleles();
		}
	},
	NOCTURNAL(DefaultFilterRuleType.NOCTURNAL) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().getActiveValue(BeeChromosomes.ACTIVITY).equals(ForestryActivityTypes.METATURNAL);
		}
	},
	PURE_NOCTURNAL(DefaultFilterRuleType.PURE_NOCTURNAL) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().<IActivityType>resolveActive(BeeChromosomes.ACTIVITY).isActive(0, IActivityType.NIGHT_TIME, BlockPos.ZERO);
		}
	},
	FLYER(DefaultFilterRuleType.FLYER) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().getActiveValue(BeeChromosomes.TOLERATES_RAIN);
		}
	},
	PURE_FLYER((DefaultFilterRuleType.PURE_FLYER)) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().getActiveValue(BeeChromosomes.TOLERATES_RAIN) && bee.getGenome().getInactiveValue(BeeChromosomes.TOLERATES_RAIN);
		}
	},
	CAVE(DefaultFilterRuleType.CAVE) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().getActiveValue(BeeChromosomes.CAVE_DWELLING);
		}
	},
	PURE_CAVE(DefaultFilterRuleType.PURE_CAVE) {
		@Override
		protected boolean isValid(IBee bee) {
			return bee.getGenome().getActiveValue(BeeChromosomes.CAVE_DWELLING) && bee.getGenome().getInactiveValue(BeeChromosomes.CAVE_DWELLING);
		}
	};

	ApicultureFilterRule(IFilterRuleType rule) {
		rule.addLogic(this);
	}

	public static void init() {
	}

	@Override
	public boolean isValid(ItemStack stack, FilterData data) {
		IIndividual individual = data.individual();
		return individual instanceof IBee bee && isValid(bee);
	}

	protected boolean isValid(IBee bee) {
		return false;
	}

	@Override
	public ResourceLocation getSpeciesTypeId() {
		return ForestrySpeciesTypes.BEE;
	}
}
