package forestry.core.genetics;

import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IGenomeBuilder;
import forestry.apiimpl.plugin.SpeciesRegistration;

/**
 * The genome-build skeleton shared by every species projector: seed the karyotype defaults via
 * {@link SpeciesRegistration#createDefaultGenomeBuilder}, apply the definition's sparse overrides via
 * {@link GenomeProjection#applyOverrides}, and build. The per-type projector keeps its own fail-soft
 * {@code try/catch}, type-specific preflight (jubilance / bindings lookup), and final species
 * construction.
 */
public final class SpeciesProjection {
	private SpeciesProjection() {
	}

	public static IGenome buildGenome(IKaryotype karyotype, ResourceLocation id, ISpeciesDefinition def) {
		IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
		GenomeProjection.applyOverrides(gb, karyotype, def.genome());
		return gb.build();
	}
}
