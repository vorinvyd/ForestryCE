package forestry.api.plugin;

import forestry.api.apiculture.hives.IHiveDrop;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public interface IHiveBuilder {
	default IHiveBuilder addDrop(double chance, ResourceLocation speciesId, Supplier<List<ItemStack>> extraItems) {
		return addDrop(chance, speciesId, extraItems, 0f, Map.of());
	}

	default IHiveBuilder addDrop(double chance, ResourceLocation speciesId, Supplier<List<ItemStack>> extraItems, float ignobleChance) {
		return addDrop(chance, speciesId, extraItems, ignobleChance, Map.of());
	}

	/**
	 * Adds a bee to this hive's drops, along with any extra items associated with that bee.
	 * Princess drops are rolled up to 10 times or until a princess drop is added, whichever comes first.
	 * Drone drops and extra drops are rolled only once.
	 *
	 * @param chance        The chance for this drop to be chosen per roll.
	 * @param speciesId     The ID of the bee species to add.
	 * @param extraItems    The extra items that should drop with this species.
	 * @param ignobleChance The chance that the princess dropped is Ignoble Stock instead of <i>Pristine Stock</i>.
	 * @param alleles       A map of alleles to be set on the bee when it is dropped.
	 */
	IHiveBuilder addDrop(double chance, ResourceLocation speciesId, Supplier<List<ItemStack>> extraItems, float ignobleChance, Map<IChromosome<?>, Allele<?>> alleles);

	/**
	 * Used to add custom implementations of {@link IHiveDrop} to this hive's drops.
	 *
	 * @param drop The supplier of the drop. Called after items and bee species are all registered.
	 */
	IHiveBuilder addCustomDrop(IHiveDrop drop);

	/**
	 * Changes the generation chance of the hive.
	 *
	 * <p>1.0 is the baseline chance; higher numbers spawn more hives, lower numbers spawn fewer.
	 * Tree hives generally want 3.0–4.0 because their valid placement set is much smaller than
	 * ground hives. Returns {@code this} so the call chains cleanly with
	 * {@link forestry.api.plugin.IApicultureRegistration#registerHive} / {@link #addDrop}.
	 *
	 * @since 1.1.1
	 */
	IHiveBuilder setGenerationChance(float generationChance);
}
