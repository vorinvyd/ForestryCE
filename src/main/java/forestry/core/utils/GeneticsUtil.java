package forestry.core.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import forestry.api.ForestryCapabilities;
import forestry.api.IForestryApi;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.core.ISpectacleVision;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.ILifeStage;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.IMutationManager;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.arboriculture.genetics.TreeLifeStage;
import forestry.api.lepidopterology.IButterflyNursery;
import forestry.api.lepidopterology.genetics.IButterfly;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.arboriculture.capabilities.SpectacleVision;
import forestry.compat.curios.CuriosCompat;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import forestry.core.genetics.ItemGE;
import forestry.core.tiles.TileUtil;

public class GeneticsUtil {
	private static String getKeyPrefix(ISpecies<?> allele) {
		if (allele instanceof IBeeSpecies) {
			return "for.bees";
		} else if (allele instanceof ITreeSpecies) {
			return "for.trees";
		} else if (allele instanceof IButterflySpecies) {
			return "for.butterflies";
		}
		throw new IllegalStateException();
	}

	public static Component getAlyzerName(ILifeStage type, ISpecies<?> allele) {
		String customKey = getKeyPrefix(allele) +
			".custom.alyzer." +
			type.getSerializedName() +
			'.' +
			allele.getTranslationKey();
		return Translator.tryTranslate(customKey, allele::getDisplayName);
	}

	public static Component getItemName(ILifeStage type, ISpecies<?> species) {
		String prefix = getKeyPrefix(species);
		String customKey = prefix +
			".custom." +
			type.getSerializedName() +
			'.' +
			species.getTranslationKey();
		return Translator.tryTranslate(customKey, () -> {
			Component speciesName = species.getDisplayName();
			Component typeName = Component.translatable(prefix + ".grammar." + type.getSerializedName() + ".type");
			return Component.translatable(prefix + ".grammar." + type.getSerializedName(), speciesName, typeName);
		});
	}

	public static boolean hasNaturalistEye(Player player) {
		return hasNaturalistEye(player, player.getItemBySlot(EquipmentSlot.HEAD)) || (CuriosCompat.IS_LOADED && CuriosCompat.hasNaturalistEye(player));
	}

	public static boolean hasNaturalistEye(Player player, ItemStack armorItemStack) {
		if (armorItemStack.isEmpty()) {
			return false;
		}
		ISpectacleVision armorNaturalist = armorItemStack.getCapability(ForestryCapabilities.SPECTACLE_VISION);
		if (armorNaturalist == null) {
			return false;
		}

		return armorNaturalist.canSeePollination(player, armorItemStack, true);
	}

	public static boolean canNurse(IButterfly butterfly, Level world, final BlockPos pos) {
		IButterflyNursery tile = TileUtil.getTile(world, pos, IButterflyNursery.class);
		return tile != null && tile.canNurse(butterfly);
	}

	public static ItemStack convertToGeneticEquivalent(ItemStack foreign) {
		IIndividual tree = SpeciesUtil.TREE_TYPE.get().getVanillaIndividual(foreign.getItem());
		if (tree != null) {
			ItemStack equivalent = tree.getSpecies().createStack(TreeLifeStage.SAPLING);
			equivalent.setCount(foreign.getCount());
			return equivalent;
		}
		return foreign;
	}

	public static int getResearchComplexity(ISpecies<?> species) {
		return 1 + getGeneticAdvancement(species, new HashSet<>());
	}

	@SuppressWarnings("unchecked")
	private static int getGeneticAdvancement(ISpecies<?> species, Set<ISpecies<?>> exclude) {
		int highest = 0;
		exclude.add(species);

		ISpeciesType<?, ?> type = species.getType();
		for (IMutation<?> mutation : ((IMutationManager<ISpecies<?>>) type.getMutations()).getMutationsInto(species)) {
			highest = getHighestAdvancement(mutation.getFirstParent(), highest, exclude);
			highest = getHighestAdvancement(mutation.getSecondParent(), highest, exclude);
		}

		return 1 + highest;
	}

	private static int getHighestAdvancement(ISpecies<?> mutationSpecies, int highest, Set<ISpecies<?>> exclude) {
		if (exclude.contains(mutationSpecies)) {
			return highest;
		}

		int otherAdvance = getGeneticAdvancement(mutationSpecies, exclude);
		return Math.max(otherAdvance, highest);
	}

	/**
	 * Creates a translation key for an OBJECT of a TYPE (ex. a SPECIES of a SPECIES TYPE, or an ALLELE of a CHROMOSOME)
	 * The format is as follows:
	 * <ul>
	 *     <li>If {@code typeId} and {@code objectId} share the same namespace, the format is: <br> {@code TYPE.NAMESPACE.TYPEPATH.OBJECTPATH}</li>
	 *     <li>If {@code typeId} and {@code objectId} have different namespaces, the format is: <br> {@code TYPE.TYPENAMESPACE.TYPEPATH.OBJECTNAMESPACE.OBJECTPATH}</li>
	 * </ul>
	 * For example, the Austere bee species from Forestry has the translation key: <br> {@code species.forestry.bee.austere} <br>
	 * and a bee species contributed by an add-on under its own namespace has the translation key: <br> {@code species.forestry.bee.myaddon.mybee}
	 *
	 * @param type     The first part of the translation key that describes the type of object this is. Can be empty.
	 * @param typeId   The ID of the type. An example would be the ID of the species type.
	 * @param objectId The ID of the object. An example would be the ID of the species.
	 * @return The translation key.
	 */
	public static String createTranslationKey(String type, ResourceLocation typeId, ResourceLocation objectId) {
		String typeNamespace = typeId.getNamespace();
		StringBuilder translationKey = new StringBuilder(type);
		if (!type.isEmpty()) {
			translationKey.append('.');
		}
		translationKey.append(typeNamespace);
		translationKey.append('.');
		translationKey.append(typeId.getPath());
		translationKey.append('.');

		String speciesNamespace = objectId.getNamespace();
		if (speciesNamespace.equals(typeNamespace)) {
			// for species from the same mod as species type, use the following format:
			// species.forestry.bee.austere
			translationKey.append(objectId.getPath());
		} else {
			// if species type is from another mod, use this format instead:
			// species.forestry.bee.myaddon.mybee
			translationKey.append(speciesNamespace);
			translationKey.append('.');
			translationKey.append(objectId.getPath());
		}

		return translationKey.toString();
	}

	/**
	 * Builds the display name for the active allele value of a chromosome. Components are built here, at the UI render
	 * edge, from the chromosome's translation key; the genetics model itself stores no {@link Component}.
	 */
	public static <V> MutableComponent getActiveName(IGenome genome, IChromosome<V> chromosome) {
		return getName(chromosome, genome.getActiveValue(chromosome));
	}

	/**
	 * Builds the display name for the inactive allele value of a chromosome.
	 */
	public static <V> MutableComponent getInactiveName(IGenome genome, IChromosome<V> chromosome) {
		return getName(chromosome, genome.getInactiveValue(chromosome));
	}

	/**
	 * Builds the display name for a chromosome value, falling back to the raw value if its translation key is unset.
	 */
	public static <V> MutableComponent getName(IChromosome<V> chromosome, V value) {
		return Component.translatableWithFallback(chromosome.translationKey(value), String.valueOf(value));
	}

	/**
	 * Builds the display name of a chromosome itself (e.g. "Speed", "Flower Type").
	 */
	public static MutableComponent getChromosomeName(IChromosome<?> chromosome) {
		return Component.translatable(chromosome.chromosomeTranslationKey());
	}

	/**
	 * Collects the distinct alleles that appear (active or inactive) for the given chromosome across the default genomes
	 * of all registered species, sorted by their value's string form. Replaces the old karyotype allele whitelist; used
	 * by debug commands to enumerate candidate alleles.
	 */
	public static <V> List<Allele<V>> getKnownAlleles(IChromosome<V> chromosome) {
		LinkedHashSet<Allele<V>> set = new LinkedHashSet<>();
		for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
			if (!type.getKaryotype().contains(chromosome)) {
				continue;
			}
			for (ISpecies<?> species : type.getAllSpecies()) {
				AllelePair<V> pair = species.getDefaultGenome().getAllelePair(chromosome);
				set.add(pair.active());
				set.add(pair.inactive());
			}
		}
		List<Allele<V>> list = new ArrayList<>(set);
		list.sort(Comparator.comparing(allele -> String.valueOf(allele.value())));
		return list;
	}

	/**
	 * @return A stable, whitespace-free string key identifying an allele value, used by debug commands for suggestions
	 * and matching. {@link Vec3i#toString} contains spaces, so it is rendered as {@code x_y_z}; all other built-in
	 * value types ({@code Float}/{@code Integer}/{@code Boolean}/enum/{@link ResourceLocation}) are already space-free.
	 */
	public static String alleleKey(Allele<?> allele) {
		Object value = allele.value();
		if (value instanceof Vec3i vec) {
			return vec.getX() + "_" + vec.getY() + "_" + vec.getZ();
		}
		return String.valueOf(value);
	}

	public static IdentityHashMap<ISpecies<?>, ItemStack> getIconStacks(ILifeStage stage, ISpeciesType<?, ?> type) {
		IdentityHashMap<ISpecies<?>, ItemStack> map = new IdentityHashMap<>();
		getIconStacks(map, stage, type);
		return map;
	}

	public static void getIconStacks(Map<ISpecies<?>, ItemStack> map, ILifeStage stage, ISpeciesType<?, ?> type) {
		ArrayList<ItemStack> itemList = new ArrayList<>(type.getAllSpecies().size());
		ItemGE.addCreativeItems(stage, itemList, false, type);

		for (ItemStack stack : itemList) {
			IIndividualHandlerItem.ifPresent(stack, individual -> {
				ISpecies<?> species = individual.getSpecies();
				map.put(species, stack);
			});
		}
	}
}
