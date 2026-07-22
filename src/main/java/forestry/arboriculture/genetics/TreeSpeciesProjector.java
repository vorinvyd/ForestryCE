package forestry.arboriculture.genetics;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.arboriculture.TreeSpecies;
import forestry.core.genetics.SpeciesProjection;

/**
 * Projects a pure-data {@link TreeSpeciesDefinition} + its code-side {@link TreeBlockBindings} into a runtime
 * {@link TreeSpecies}, reusing the same {@link SpeciesProjection#buildGenome} genome path as the
 * code-registered species. Fails soft: a missing binding, unknown chromosome, or any exception is logged and yields
 * {@code null} rather than crashing species loading.
 */
public final class TreeSpeciesProjector {
	private TreeSpeciesProjector() {
	}

	@Nullable
	public static TreeSpecies project(ITreeSpeciesType type, ResourceLocation id, TreeSpeciesDefinition def) {
		try {
			TreeBlockBindings bindings = ((TreeSpeciesType) type).getBindings(id);
			if (bindings == null) {
				Forestry.LOGGER.warn("Skipping tree species {}: no code-side block/worldgen bindings registered for this id", id);
				return null;
			}
			IKaryotype karyotype = type.getKaryotype();
			IGenome genome = SpeciesProjection.buildGenome(karyotype, id, def);
			return new TreeSpecies(id, type, genome, new DefinitionTreeSpeciesBuilder(def, bindings));
		} catch (Exception e) {
			Forestry.LOGGER.error("Failed to project tree species {}", id, e);
			return null;
		}
	}
}
