package forestry.api.genetics.alleles;

import net.minecraft.util.RandomSource;

/**
 * A pair of an active allele and an inactive allele for one chromosome of a {@link forestry.api.genetics.IGenome}.
 * <p>
 * Alleles are inline values ({@link Allele}); the pair is serialized per-chromosome by the karyotype's genome codec,
 * built from each chromosome's value codec. There is no global allele registry.
 *
 * @param active   The active (expressed) allele for the chromosome.
 * @param inactive The inactive allele for the chromosome.
 * @param <V>      The type of value held by this pair's alleles.
 */
public record AllelePair<V>(Allele<V> active, Allele<V> inactive) {
	/**
	 * Creates an allele pair where both the active and inactive alleles are the same.
	 */
	public static <V> AllelePair<V> both(Allele<V> allele) {
		return new AllelePair<>(allele, allele);
	}

	/**
	 * Creates a new pair out of one allele from this pair and one from the other pair, ordered by dominance.
	 */
	public AllelePair<V> inheritOther(RandomSource rand, AllelePair<V> other) {
		Allele<V> firstChoice = rand.nextBoolean() ? this.active : this.inactive;
		Allele<V> secondChoice = rand.nextBoolean() ? other.active : other.inactive;

		if (rand.nextBoolean()) {
			return create(firstChoice, secondChoice);
		} else {
			return create(secondChoice, firstChoice);
		}
	}

	public AllelePair<V> inheritHaploid(RandomSource rand) {
		Allele<V> choice = rand.nextBoolean() ? this.active : this.inactive;
		return new AllelePair<>(choice, choice);
	}

	/**
	 * @return {@code true} if the active allele equals the inactive allele.
	 */
	public boolean isSameAlleles() {
		return this.active.equals(this.inactive);
	}

	/**
	 * A pair where the active allele is the first dominant allele and the inactive allele is the other.
	 * THIS IS DIFFERENT THAN THE CONSTRUCTOR.
	 */
	public static <V> AllelePair<V> create(Allele<V> first, Allele<V> second) {
		return new AllelePair<>(activeOf(first, second), inactiveOf(first, second));
	}

	private static <V> Allele<V> activeOf(Allele<V> first, Allele<V> second) {
		if (first.dominant()) {
			return first;
		}
		if (second.dominant()) {
			return second;
		}
		// Both recessive
		return first;
	}

	private static <V> Allele<V> inactiveOf(Allele<V> first, Allele<V> second) {
		if (!second.dominant()) {
			return second;
		}
		if (!first.dominant()) {
			return first;
		}
		// Both dominant
		return second;
	}
}
