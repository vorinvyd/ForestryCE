package forestry.core.genetics;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IGenomeBuilder;

/**
 * Species-agnostic genome-override application shared by the data-driven species projectors (bee, tree, ...):
 * applies a definition's sparse genome overrides onto a genome builder, dispatching reference vs data chromosomes
 * exactly as the code-built path does: reference chromosomes (non-null {@link IChromosome#resolver()}) use the
 * {@code ResourceLocation} overload so dominance is resolved from the reference; data chromosomes use the plain
 * {@code Allele} overload.
 */
public final class GenomeProjection {
	private GenomeProjection() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void applyOverrides(IGenomeBuilder builder, IKaryotype karyotype, Map<ResourceLocation, Allele<?>> overrides) {
		for (Map.Entry<ResourceLocation, Allele<?>> e : overrides.entrySet()) {
			IChromosome<?> chromosome = karyotype.getChromosome(e.getKey());
			if (chromosome == null) {
				Forestry.LOGGER.warn("Skipping unknown chromosome {} in genome override", e.getKey());
				continue;
			}
			Allele<?> allele = e.getValue();
			if (chromosome.resolver() != null) {
				builder.set((IChromosome<ResourceLocation>) chromosome, (ResourceLocation) allele.value());
			} else {
				builder.set((IChromosome) chromosome, allele);
			}
		}
	}
}
