package forestry.apiculture.genetics;

import java.util.List;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.IProduct;
import forestry.api.plugin.IBeeSpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link IBeeSpeciesBuilder} adapter over a {@link BeeSpeciesDefinition}: the base
 * getters/mutators come from {@link AbstractDefinitionSpeciesBuilder}; this class adds the bee-specific
 * sprite-color/product/jubilance getters and throws from the bee-specific mutators.
 *
 * @see BeeSpeciesProjector
 */
public class DefinitionBeeSpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<BeeSpeciesDefinition, IBeeSpeciesType, IBeeSpecies, IBeeSpeciesBuilder>
	implements IBeeSpeciesBuilder {

	private final IBeeJubilance jubilance;

	public DefinitionBeeSpeciesBuilder(BeeSpeciesDefinition def, IBeeJubilance jubilance) {
		super(def);
		this.jubilance = jubilance;
	}

	@Override public List<IProduct> buildProducts() { return List.copyOf(def.products()); }
	@Override public List<IProduct> buildSpecialties() { return List.copyOf(def.specialties()); }
	@Override public int getBody() { return def.body(); }
	@Override public int getStripes() { return def.stripes(); }
	@Override public int getOutline() { return def.outline(); }
	@Override public IBeeJubilance getJubilance() { return this.jubilance; }

	@Override public IBeeSpeciesBuilder addProduct(IProduct product) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addProduct(ItemStack stack, float chance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addSpecialty(IProduct specialty) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addSpecialty(ItemStack stack, float chance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setBody(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setStripes(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setOutline(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setJubilance(IBeeJubilance jubilance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
