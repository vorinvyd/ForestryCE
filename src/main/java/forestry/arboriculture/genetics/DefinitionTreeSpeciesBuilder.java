package forestry.arboriculture.genetics;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import forestry.api.arboriculture.ITreeGenData;
import forestry.api.arboriculture.ITreeGenerator;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.IWoodType;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.plugin.ITreeSpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link ITreeSpeciesBuilder} adapter over a {@link TreeSpeciesDefinition} + its code-side
 * {@link TreeBlockBindings}: base getters/mutators come from {@link AbstractDefinitionSpeciesBuilder};
 * this class adds the tree-specific rarity/block/worldgen getters (from the definition or the bindings)
 * and throws from the tree-specific mutators.
 *
 * @see TreeSpeciesProjector
 */
public class DefinitionTreeSpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<TreeSpeciesDefinition, ITreeSpeciesType, ITreeSpecies, ITreeSpeciesBuilder>
	implements ITreeSpeciesBuilder {

	private final TreeBlockBindings bindings;

	public DefinitionTreeSpeciesBuilder(TreeSpeciesDefinition def, TreeBlockBindings bindings) {
		super(def);
		this.bindings = bindings;
	}

	@Override public float getRarity() { return def.rarity(); }

	// --- block/worldgen getters (from the code-side bindings) ---
	@Override public ITreeGenerator getGenerator() { return bindings.generator(); }
	@Override public List<BlockState> getVanillaLeafStates() { return bindings.vanillaLeafStates(); }
	@Override public List<Item> getVanillaSaplingItems() { return bindings.vanillaSaplingItems(); }
	@Override public ItemStack getDecorativeLeaves() { return bindings.decorativeLeaves(); }

	// --- tree-specific mutators (all throw) ---
	@Override public ITreeSpeciesBuilder setTreeFeature(Function<ITreeGenData, Feature<NoneFeatureConfiguration>> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setGenerator(ITreeGenerator generator) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaStates(Collection<BlockState> states) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaSapling(Item sapling) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setDecorativeLeaves(ItemStack stack) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setWoodType(IWoodType woodType) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setRarity(float rarity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
