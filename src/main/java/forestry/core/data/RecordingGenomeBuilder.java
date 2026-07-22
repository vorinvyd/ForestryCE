package forestry.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.plugin.IGenomeBuilder;

/**
 * A {@link IGenomeBuilder} that records the sparse genome overrides a species builder's {@code setGenome} closure
 * applies, instead of actually building a genome. Used by {@link BeeSpeciesProvider} to capture the same overrides
 * a {@code BeeSpeciesBuilder} would apply at build time, for serialization into a {@link
 * forestry.apiculture.genetics.BeeSpeciesDefinition}.
 * <p>
 * Only the two {@code set(chromosome, ...)} overloads are supported (mirroring what {@code createDefaultGenomeBuilder}
 * does NOT need from per-species override closures - those closures only ever call {@code set}, directly or via the
 * boolean/reference default methods). {@link #setActive}/{@link #setInactive} are not used by any species builder's
 * {@code setGenome} consumer, so they throw to catch any future surprise.
 */
public class RecordingGenomeBuilder implements IGenomeBuilder {
	public final Map<ResourceLocation, Allele<?>> overrides = new LinkedHashMap<>();

	@Override
	public <V> void set(IChromosome<V> chromosome, Allele<V> allele) {
		this.overrides.put(chromosome.id(), allele);
	}

	@Override
	public void set(IChromosome<ResourceLocation> chromosome, ResourceLocation id) {
		this.overrides.put(chromosome.id(), Allele.reference(id));
	}

	@Override
	public IGenome build() {
		return null;
	}

	@Override
	public void setRemainingDefault() {
		// no-op: datagen only cares about the sparse overrides, not the karyotype defaults
	}

	@Override
	public boolean isEmpty() {
		return this.overrides.isEmpty();
	}

	@Override
	public <V> void setActive(IChromosome<V> chromosome, Allele<V> allele) {
		throw new UnsupportedOperationException("RecordingGenomeBuilder does not support setActive; species builder genome closures are expected to only call set(...)");
	}

	@Override
	public <V> void setInactive(IChromosome<V> chromosome, Allele<V> allele) {
		throw new UnsupportedOperationException("RecordingGenomeBuilder does not support setInactive; species builder genome closures are expected to only call set(...)");
	}
}
