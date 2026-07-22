package forestry.api.genetics.alleles;

import net.minecraft.core.Vec3i;

import forestry.api.core.ToleranceType;

/**
 * Plain inline allele value constants used by base Forestry. An {@link Allele} is just a value plus its dominance;
 * these constants are convenient, reusable building blocks for default genomes and karyotype defaults. They are no
 * longer interned or registered. Reference values (species, flower types, effects, fruits, cocoons, activity) are now
 * referenced directly by their {@link net.minecraft.resources.ResourceLocation} ids, not via constants here.
 */
public class ForestryAlleles {
	// Booleans
	public static final Allele<Boolean> TRUE = Allele.dominant(true);
	public static final Allele<Boolean> FALSE = Allele.dominant(false);
	public static final Allele<Boolean> TRUE_RECESSIVE = Allele.recessive(true);
	public static final Allele<Boolean> FALSE_RECESSIVE = Allele.recessive(false);

	// Bee Lifespan
	public static final Allele<Integer> LIFESPAN_SHORTEST = Allele.dominant(10);
	public static final Allele<Integer> LIFESPAN_SHORTER = Allele.dominant(20);
	public static final Allele<Integer> LIFESPAN_SHORT = Allele.dominant(30);
	public static final Allele<Integer> LIFESPAN_SHORTENED = Allele.dominant(35);
	public static final Allele<Integer> LIFESPAN_NORMAL = Allele.recessive(40);
	public static final Allele<Integer> LIFESPAN_ELONGATED = Allele.dominant(45);
	public static final Allele<Integer> LIFESPAN_LONG = Allele.recessive(50);
	public static final Allele<Integer> LIFESPAN_LONGER = Allele.recessive(60);
	public static final Allele<Integer> LIFESPAN_LONGEST = Allele.recessive(70);
	public static final Allele<Integer> LIFESPAN_IMMORTAL = Allele.recessive(Integer.MAX_VALUE);

	// Fertility
	public static final Allele<Integer> FERTILITY_0 = Allele.recessive(0);
	public static final Allele<Integer> FERTILITY_1 = Allele.dominant(1);
	public static final Allele<Integer> FERTILITY_2 = Allele.dominant(2);
	public static final Allele<Integer> FERTILITY_3 = Allele.recessive(3);
	public static final Allele<Integer> FERTILITY_4 = Allele.recessive(4);
	public static final Allele<Integer> FERTILITY_5 = Allele.recessive(5);
	public static final Allele<Integer> FERTILITY_6 = Allele.recessive(6);
	public static final Allele<Integer> FERTILITY_7 = Allele.recessive(7);
	public static final Allele<Integer> FERTILITY_8 = Allele.recessive(8);
	public static final Allele<Integer> FERTILITY_9 = Allele.recessive(9);
	public static final Allele<Integer> FERTILITY_10 = Allele.recessive(10);

	// Pollination
	public static final Allele<Integer> POLLINATION_SLOWEST = Allele.dominant(5);
	public static final Allele<Integer> POLLINATION_SLOWER = Allele.recessive(10);
	public static final Allele<Integer> POLLINATION_SLOW = Allele.recessive(15);
	public static final Allele<Integer> POLLINATION_AVERAGE = Allele.recessive(20);
	public static final Allele<Integer> POLLINATION_FAST = Allele.recessive(25);
	public static final Allele<Integer> POLLINATION_FASTER = Allele.recessive(30);
	public static final Allele<Integer> POLLINATION_FASTEST = Allele.recessive(35);
	public static final Allele<Integer> POLLINATION_MAXIMUM = Allele.dominant(99);

	// Tolerance
	public static final Allele<ToleranceType> TOLERANCE_NONE = Allele.recessive(ToleranceType.NONE);
	public static final Allele<ToleranceType> TOLERANCE_BOTH_1 = Allele.dominant(ToleranceType.BOTH_1);
	public static final Allele<ToleranceType> TOLERANCE_BOTH_2 = Allele.recessive(ToleranceType.BOTH_2);
	public static final Allele<ToleranceType> TOLERANCE_BOTH_3 = Allele.recessive(ToleranceType.BOTH_3);
	public static final Allele<ToleranceType> TOLERANCE_BOTH_4 = Allele.recessive(ToleranceType.BOTH_4);
	public static final Allele<ToleranceType> TOLERANCE_BOTH_5 = Allele.recessive(ToleranceType.BOTH_5);
	public static final Allele<ToleranceType> TOLERANCE_UP_1 = Allele.dominant(ToleranceType.UP_1);
	public static final Allele<ToleranceType> TOLERANCE_UP_2 = Allele.recessive(ToleranceType.UP_2);
	public static final Allele<ToleranceType> TOLERANCE_UP_3 = Allele.recessive(ToleranceType.UP_3);
	public static final Allele<ToleranceType> TOLERANCE_UP_4 = Allele.recessive(ToleranceType.UP_4);
	public static final Allele<ToleranceType> TOLERANCE_UP_5 = Allele.recessive(ToleranceType.UP_5);
	public static final Allele<ToleranceType> TOLERANCE_DOWN_1 = Allele.dominant(ToleranceType.DOWN_1);
	public static final Allele<ToleranceType> TOLERANCE_DOWN_2 = Allele.recessive(ToleranceType.DOWN_2);
	public static final Allele<ToleranceType> TOLERANCE_DOWN_3 = Allele.recessive(ToleranceType.DOWN_3);
	public static final Allele<ToleranceType> TOLERANCE_DOWN_4 = Allele.recessive(ToleranceType.DOWN_4);
	public static final Allele<ToleranceType> TOLERANCE_DOWN_5 = Allele.recessive(ToleranceType.DOWN_5);

	// Territory
	public static final Allele<Vec3i> TERRITORY_AVERAGE = Allele.recessive(new Vec3i(9, 6, 9));
	public static final Allele<Vec3i> TERRITORY_LARGE = Allele.recessive(new Vec3i(11, 8, 11));
	public static final Allele<Vec3i> TERRITORY_LARGER = Allele.recessive(new Vec3i(13, 12, 13));
	public static final Allele<Vec3i> TERRITORY_LARGEST = Allele.recessive(new Vec3i(15, 13, 15));

	// Speed
	public static final Allele<Float> SPEED_SLOWEST = Allele.dominant(0.3f);
	public static final Allele<Float> SPEED_SLOWER = Allele.dominant(0.6f);
	public static final Allele<Float> SPEED_SLOW = Allele.dominant(0.8f);
	public static final Allele<Float> SPEED_NORMAL = Allele.recessive(1.0f);
	public static final Allele<Float> SPEED_FAST = Allele.dominant(1.2f);
	public static final Allele<Float> SPEED_FASTER = Allele.recessive(1.4f);
	public static final Allele<Float> SPEED_FASTEST = Allele.recessive(1.7f);

	// Size
	public static final Allele<Float> SIZE_SMALLEST = Allele.recessive(0.3f);
	public static final Allele<Float> SIZE_SMALLER = Allele.recessive(0.4f);
	public static final Allele<Float> SIZE_SMALL = Allele.recessive(0.5f);
	public static final Allele<Float> SIZE_AVERAGE = Allele.recessive(0.6f);
	public static final Allele<Float> SIZE_LARGE = Allele.recessive(0.75f);
	public static final Allele<Float> SIZE_LARGER = Allele.recessive(0.9f);
	public static final Allele<Float> SIZE_LARGEST = Allele.recessive(1.0f);

	// Metabolism
	public static final Allele<Integer> METABOLISM_SLOWEST = Allele.recessive(1);
	public static final Allele<Integer> METABOLISM_SLOWER = Allele.recessive(2);
	public static final Allele<Integer> METABOLISM_SLOW = Allele.recessive(3);
	public static final Allele<Integer> METABOLISM_NORMAL = Allele.recessive(5);
	public static final Allele<Integer> METABOLISM_FAST = Allele.recessive(7);
	public static final Allele<Integer> METABOLISM_FASTER = Allele.recessive(8);
	public static final Allele<Integer> METABOLISM_FASTEST = Allele.recessive(10);

	// Sappiness
	public static final Allele<Float> SAPPINESS_LOWEST = Allele.dominant(0.1f);
	public static final Allele<Float> SAPPINESS_LOWER = Allele.dominant(0.2f);
	public static final Allele<Float> SAPPINESS_LOW = Allele.dominant(0.3f);
	public static final Allele<Float> SAPPINESS_AVERAGE = Allele.dominant(0.4f);
	public static final Allele<Float> SAPPINESS_HIGH = Allele.dominant(0.6f);
	public static final Allele<Float> SAPPINESS_HIGHER = Allele.recessive(0.8f);
	public static final Allele<Float> SAPPINESS_HIGHEST = Allele.recessive(1.0f);

	// Saplings
	public static final Allele<Float> SAPLINGS_LOWEST = Allele.dominant(0.01f);
	public static final Allele<Float> SAPLINGS_LOWER = Allele.dominant(0.025f);
	public static final Allele<Float> SAPLINGS_LOW = Allele.dominant(0.035f);
	public static final Allele<Float> SAPLINGS_AVERAGE = Allele.dominant(0.05f);
	public static final Allele<Float> SAPLINGS_HIGH = Allele.dominant(0.1f);
	public static final Allele<Float> SAPLINGS_HIGHER = Allele.dominant(0.2f);
	public static final Allele<Float> SAPLINGS_HIGHEST = Allele.dominant(0.3f);

	// Maturation
	public static final Allele<Integer> MATURATION_SLOWEST = Allele.dominant(10);
	public static final Allele<Integer> MATURATION_SLOWER = Allele.recessive(7);
	public static final Allele<Integer> MATURATION_SLOW = Allele.dominant(5);
	public static final Allele<Integer> MATURATION_AVERAGE = Allele.recessive(4);
	public static final Allele<Integer> MATURATION_FAST = Allele.recessive(3);
	public static final Allele<Integer> MATURATION_FASTER = Allele.recessive(2);
	public static final Allele<Integer> MATURATION_FASTEST = Allele.recessive(1);

	// Yield
	public static final Allele<Float> YIELD_LOWEST = Allele.dominant(0.025f);
	public static final Allele<Float> YIELD_LOWER = Allele.dominant(0.05f);
	public static final Allele<Float> YIELD_LOW = Allele.dominant(0.1f);
	public static final Allele<Float> YIELD_AVERAGE = Allele.dominant(0.2f);
	public static final Allele<Float> YIELD_HIGH = Allele.recessive(0.3f);
	public static final Allele<Float> YIELD_HIGHER = Allele.recessive(0.35f);
	public static final Allele<Float> YIELD_HIGHEST = Allele.recessive(0.4f);

	// Height
	public static final Allele<Float> HEIGHT_SMALLEST = Allele.recessive(0.25f);
	public static final Allele<Float> HEIGHT_SMALLER = Allele.recessive(0.5f);
	public static final Allele<Float> HEIGHT_SMALL = Allele.recessive(0.75f);
	public static final Allele<Float> HEIGHT_AVERAGE = Allele.recessive(1.0f);
	public static final Allele<Float> HEIGHT_LARGE = Allele.recessive(1.25f);
	public static final Allele<Float> HEIGHT_LARGER = Allele.recessive(1.5f);
	public static final Allele<Float> HEIGHT_LARGEST = Allele.recessive(1.75f);
	public static final Allele<Float> HEIGHT_GIGANTIC = Allele.recessive(2.0f);

	// Girth
	public static final Allele<Integer> GIRTH_1 = Allele.recessive(1);
	public static final Allele<Integer> GIRTH_2 = Allele.recessive(2);
	public static final Allele<Integer> GIRTH_3 = Allele.recessive(3);
	public static final Allele<Integer> GIRTH_4 = Allele.recessive(4);
	public static final Allele<Integer> GIRTH_5 = Allele.recessive(5);
	public static final Allele<Integer> GIRTH_6 = Allele.recessive(6);
	public static final Allele<Integer> GIRTH_7 = Allele.recessive(7);
	public static final Allele<Integer> GIRTH_8 = Allele.recessive(8);
	public static final Allele<Integer> GIRTH_9 = Allele.recessive(9);
	public static final Allele<Integer> GIRTH_10 = Allele.recessive(10);
}
