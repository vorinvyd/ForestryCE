package forestry.apiculture;

import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record VillageHive(ResourceLocation speciesId, Map<IChromosome<?>, Allele<?>> alleles) {
}
