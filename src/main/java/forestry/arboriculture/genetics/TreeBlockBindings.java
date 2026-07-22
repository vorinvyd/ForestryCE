package forestry.arboriculture.genetics;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import forestry.api.arboriculture.ITreeGenerator;

/**
 * The non-serializable, code-registered bindings of a tree species, keyed by species id and captured at plugin
 * registration from the {@code DefaultTreeSpecies} builders. A datapack {@link TreeSpeciesDefinition} carries only the
 * genetics layer; these worldgen/block bindings stay code-side ("code-registered & ID-bound" per the Stage-4 roadmap)
 * and are merged back in by {@link TreeSpeciesProjector}. {@code woodType} is intentionally absent: {@code TreeSpecies}
 * never reads it (it is baked into the {@link ITreeGenerator}).
 */
public record TreeBlockBindings(
	ITreeGenerator generator,
	List<BlockState> vanillaLeafStates,
	List<Item> vanillaSaplingItems,
	ItemStack decorativeLeaves
) {
}
