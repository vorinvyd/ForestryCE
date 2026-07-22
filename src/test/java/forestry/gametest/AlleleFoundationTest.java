package forestry.gametest;

import java.util.List;
import java.util.Map;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.apiculture.genetics.Bee;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for the inline-value allele foundation. Where {@link GenomeBaselineTest} proves the default genomes
 * are unchanged, this exercises the runtime mechanics that the golden master does not cover: codec round-trips
 * (NBT + network), lazy reference resolution, the "reference dominance is intrinsic" invariant, override resolution
 * via {@link IGenome#copyWith}, and offspring construction.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class AlleleFoundationTest {
	private static List<ISpeciesType<?, ?>> types() {
		return List.copyOf(IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes());
	}

	/** Every default genome must survive an NBT codec round-trip with identical alleles. */
	@GameTest(template = "empty")
	public static void nbtCodecRoundTrip(GameTestHelper helper) {
		for (ISpeciesType<?, ?> type : types()) {
			Codec<IGenome> codec = type.getKaryotype().getGenomeCodec();
			for (ISpecies<?> species : type.getAllSpecies()) {
				IGenome genome = species.getDefaultGenome();
				Tag tag = codec.encodeStart(NbtOps.INSTANCE, genome).getOrThrow();
				IGenome decoded = codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
				if (!decoded.isSameAlleles(genome)) {
					helper.fail("NBT codec round-trip changed alleles for " + species.id());
					return;
				}
			}
		}
		helper.succeed();
	}

	/** Every default genome must survive a network stream-codec round-trip with identical alleles. */
	@GameTest(template = "empty")
	public static void streamCodecRoundTrip(GameTestHelper helper) {
		for (ISpeciesType<?, ?> type : types()) {
			StreamCodec<RegistryFriendlyByteBuf, IGenome> codec = type.getKaryotype().getGenomeStreamCodec();
			for (ISpecies<?> species : type.getAllSpecies()) {
				IGenome genome = species.getDefaultGenome();
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
				codec.encode(buf, genome);
				IGenome decoded = codec.decode(buf);
				if (!decoded.isSameAlleles(genome)) {
					helper.fail("Stream codec round-trip changed alleles for " + species.id());
					return;
				}
			}
		}
		helper.succeed();
	}

	/** The species chromosome must resolve back to the species that owns the default genome. */
	@GameTest(template = "empty")
	public static void speciesChromosomeResolves(GameTestHelper helper) {
		for (ISpeciesType<?, ?> type : types()) {
			IChromosome<ResourceLocation> speciesChromosome = type.getKaryotype().getSpeciesChromosome();
			for (ISpecies<?> species : type.getAllSpecies()) {
				IGenome genome = species.getDefaultGenome();
				ISpecies<?> resolved = genome.resolveActive(speciesChromosome);
				if (resolved != species) {
					helper.fail("Species chromosome did not resolve to its own species for " + species.id() + " (got " + resolved.id() + ")");
					return;
				}
				if (!genome.getActiveValue(speciesChromosome).equals(species.id())) {
					helper.fail("Species chromosome stored id mismatch for " + species.id());
					return;
				}
			}
		}
		helper.succeed();
	}

	/**
	 * For every reference chromosome, the stored allele's dominance must equal the referenced value's intrinsic
	 * dominance (resolver.isDominant), and the value must resolve to a non-null behavior object.
	 */
	@GameTest(template = "empty")
	public static void referenceDominanceIsIntrinsic(GameTestHelper helper) {
		for (ISpeciesType<?, ?> type : types()) {
			for (ISpecies<?> species : type.getAllSpecies()) {
				IGenome genome = species.getDefaultGenome();
				for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
					IChromosome<?> chromosome = entry.getKey();
					IChromosome.IReferenceResolver<?> resolver = chromosome.resolver();
					if (resolver == null) {
						continue;
					}
					AllelePair<?> pair = entry.getValue();
					for (Allele<?> allele : List.of(pair.active(), pair.inactive())) {
						ResourceLocation id = (ResourceLocation) allele.value();
						if (resolver.get(id) == null) {
							helper.fail("Reference chromosome " + chromosome.id() + " resolved null for id " + id + " (species " + species.id() + ")");
							return;
						}
						if (allele.dominant() != resolver.isDominant(id)) {
							helper.fail("Reference allele dominance not intrinsic for " + chromosome.id() + " id " + id + " (species " + species.id() + ")");
							return;
						}
					}
				}
			}
		}
		helper.succeed();
	}

	/** copyWith on a reference chromosome must adopt the referenced value's intrinsic dominance, ignoring the placeholder. */
	@GameTest(template = "empty")
	public static void copyWithResolvesReferenceDominance(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		IChromosome<ResourceLocation> flowerType = forestry.api.genetics.alleles.BeeChromosomes.FLOWER_TYPE;
		IGenome base = beeType.getDefaultSpecies().getDefaultGenome();

		ResourceLocation flowerId = base.getActiveValue(flowerType);
		boolean intrinsic = flowerType.resolver().isDominant(flowerId);

		// Allele.reference uses a placeholder dominance of false; copyWith must override it with the intrinsic value.
		IGenome modified = base.copyWith(Map.of(flowerType, Allele.reference(flowerId)));
		if (modified.getActiveAllele(flowerType).dominant() != intrinsic) {
			helper.fail("copyWith did not resolve intrinsic dominance for flower type " + flowerId);
			return;
		}
		helper.succeed();
	}

	/** Offspring construction from two default genomes yields a complete, well-formed genome. */
	@GameTest(template = "empty")
	public static void offspringConstruction(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		List<IBeeSpecies> species = beeType.getAllSpecies();
		if (species.size() < 2) {
			helper.succeed();
			return;
		}
		IGenome first = species.get(0).getDefaultGenome();
		IGenome second = species.get(1).getDefaultGenome();
		RandomSource rand = helper.getLevel().random;

		IBee child = SpeciesUtil.createOffspring(rand, first, second, (p1, p2) -> null, Bee::new);
		IGenome childGenome = child.getGenome();
		int expected = beeType.getKaryotype().getChromosomes().size();
		if (childGenome.getChromosomes().size() != expected) {
			helper.fail("Offspring genome has " + childGenome.getChromosomes().size() + " chromosomes, expected " + expected);
			return;
		}
		// Active species must resolve to one of the two parents' species.
		ISpecies<?> childSpecies = childGenome.getActiveSpecies();
		if (childSpecies != species.get(0) && childSpecies != species.get(1)) {
			helper.fail("Offspring active species " + childSpecies.id() + " is neither parent");
			return;
		}
		helper.succeed();
	}
}
