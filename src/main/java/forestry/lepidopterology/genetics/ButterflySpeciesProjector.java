package forestry.lepidopterology.genetics;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.genetics.SpeciesProjection;
import forestry.lepidopterology.ButterflySpecies;

/**
 * Projects a pure-data {@link ButterflySpeciesDefinition} into a runtime {@link ButterflySpecies}, reusing the same
 * {@link SpeciesProjection#buildGenome} genome path as the code-registered species. Butterflies
 * have no per-species code-side bindings (unlike trees), and the cocoon/butterfly_effect reference chromosomes are
 * resolved lazily by genome reads, not at projection time, so there is nothing to look up here beyond the
 * definition itself.
 * <p>
 * Fails soft: any failure (unknown chromosome override, exception) is logged and yields {@code null} rather than
 * crashing species loading.
 */
public final class ButterflySpeciesProjector {
	private ButterflySpeciesProjector() {
	}

	/**
	 * @return The runtime {@link ButterflySpecies} for the given definition, or {@code null} if projection failed
	 * (logged).
	 */
	@Nullable
	public static ButterflySpecies project(IButterflySpeciesType type, ResourceLocation id, ButterflySpeciesDefinition def) {
		try {
			IKaryotype karyotype = type.getKaryotype();
			IGenome genome = SpeciesProjection.buildGenome(karyotype, id, def);
			return new ButterflySpecies(id, type, genome, new DefinitionButterflySpeciesBuilder(def));
		} catch (Exception e) {
			Forestry.LOGGER.error("Skipping butterfly species {} - projection failed", id, e);
			return null;
		}
	}
}
