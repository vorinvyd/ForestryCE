package forestry.api.plugin;

import forestry.api.genetics.alleles.Allele;

/**
 * Interface for customizing a default chromosome in a karyotype.
 *
 * @param <V> The value type stored by this chromosome.
 */
public interface IChromosomeBuilder<V> {
	/**
	 * Override the default allele of this chromosome that was previously set in {@link IKaryotypeBuilder#set}.
	 */
	IChromosomeBuilder<V> setDefault(Allele<V> allele);

	/**
	 * Sets whether this chromosome is "weakly inherited."
	 * <p>
	 * If a chromosome is weakly inherited, then its default allele will always be overridden by a non-default allele
	 * during inheritance or mutations. An example in Forestry is the temperature and humidity tolerance chromosomes.
	 * This ensures that breeding a Common bee using a Modest princess and Savanna drone will produce a Common bee that
	 * can survive either in the Savanna or in the Desert, instead of one that can only survive in the plains.
	 *
	 * @param weaklyInherited Whether this chromosome should be weakly inherited.
	 */
	IChromosomeBuilder<V> setWeaklyInherited(boolean weaklyInherited);
}
