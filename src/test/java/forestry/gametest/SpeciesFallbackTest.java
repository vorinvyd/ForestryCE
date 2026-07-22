package forestry.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.apiculture.BeeSpecies;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesManager;
import forestry.apiculture.genetics.BeeSpeciesProjector;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for the data-driven bee species type's fail-soft/empty-tolerant behavior, now that species come
 * exclusively from datapack JSON (see {@link forestry.apiculture.genetics.BeeSpeciesType#handleSpeciesRegistration}
 * returning an empty map at setup): a datapack can legitimately reference an unknown jubilance, or the live species
 * map can legitimately be empty for a moment (before the first reload runs), and neither must crash.
 * <p>
 * Both tests mutate the live {@code BEE_TYPE}'s species/mutation state and restore it in a {@code finally} block
 * (re-driving the real reload path from {@link BeeSpeciesManager#INSTANCE}'s already-loaded definitions), so other
 * GameTests running later in the same server session are unaffected.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpeciesFallbackTest {
	/**
	 * {@link BeeSpeciesProjector#project} must return {@code null} (not throw) for a definition referencing an
	 * unknown jubilance id, and {@link GeneticsReloadHandler#rebuildSpecies} over a map containing that one bad
	 * definition alongside the real, currently-loaded ones must yield a species set missing only that entry.
	 */
	@GameTest(template = "empty")
	public static void unknownJubilanceIsSkippedGracefully(GameTestHelper helper) {
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();

		BeeSpeciesDefinition badDefinition = TestSpeciesDefinitions.bee("Apis", "nonexistens")
			.jubilance(ForestryConstants.forestry("nonexistent_jubilance"))
			.build();
		ResourceLocation badId = ForestryConstants.forestry("test_species_fallback_unknown_jubilance");

		// (a) Direct projection of the bad definition must fail soft: null, not an exception.
		BeeSpecies projected = BeeSpeciesProjector.project(type, badId, badDefinition);
		if (projected != null) {
			helper.fail("Expected BeeSpeciesProjector.project to return null for an unknown jubilance id");
			return;
		}

		// (b) rebuildSpecies over the real definitions plus the bad one must not crash, and must skip only the bad one.
		Map<ResourceLocation, BeeSpeciesDefinition> realDefinitions = BeeSpeciesManager.INSTANCE.getDefinitions();
		if (realDefinitions.isEmpty()) {
			helper.fail("BeeSpeciesManager holds no definitions; cannot exercise rebuildSpecies with a mix of good/bad entries");
			return;
		}

		Map<ResourceLocation, BeeSpeciesDefinition> combined = new LinkedHashMap<>(realDefinitions);
		combined.put(badId, badDefinition);

		try {
			GeneticsReloadHandler.rebuildSpecies(combined);

			IBeeSpeciesType typeAfterRebuild = SpeciesUtil.BEE_TYPE.get();
			if (typeAfterRebuild.getAllSpeciesIds().contains(badId)) {
				helper.fail("Species set unexpectedly contains a species projected from a definition with an unknown jubilance");
				return;
			}
			if (typeAfterRebuild.getSpeciesCount() != realDefinitions.size()) {
				helper.fail("Expected rebuildSpecies to skip exactly the one bad definition; got "
					+ typeAfterRebuild.getSpeciesCount() + " species from " + combined.size()
					+ " definitions (" + realDefinitions.size() + " real ones expected to survive)");
				return;
			}
		} finally {
			// Restore the live state from the real, already-loaded definitions so later tests aren't affected.
			GeneticsReloadHandler.rebuildSpecies(realDefinitions);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}

	/**
	 * A fresh {@code setSpecies(ImmutableMap.of())} followed by {@code getAllSpecies()}/{@code getSpeciesCount()}
	 * must return empty without throwing - the same invariant the old {@code checkSpecies()} guard used to violate
	 * (it threw {@code IllegalStateException} whenever the species map was empty). This is now the normal state of
	 * {@code BeeSpeciesType} between setup and the first datapack reload.
	 */
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void emptySpeciesSetIsTolerated(GameTestHelper helper) {
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		Map<ResourceLocation, BeeSpeciesDefinition> realDefinitions = BeeSpeciesManager.INSTANCE.getDefinitions();
		if (realDefinitions.isEmpty()) {
			helper.fail("BeeSpeciesManager holds no definitions; cannot safely restore live state after this test");
			return;
		}

		try {
			((SpeciesType<IBeeSpecies, ?>) type).setSpecies(ImmutableMap.of());

			List<IBeeSpecies> allSpecies = type.getAllSpecies();
			if (allSpecies == null) {
				helper.fail("getAllSpecies() returned null after setSpecies(empty); expected an empty list");
				return;
			}
			if (!allSpecies.isEmpty()) {
				helper.fail("Expected getAllSpecies() to be empty after setSpecies(empty), found " + allSpecies.size());
				return;
			}
			if (type.getSpeciesCount() != 0) {
				helper.fail("Expected getSpeciesCount() == 0 after setSpecies(empty), found " + type.getSpeciesCount());
				return;
			}
			if (!type.getAllSpeciesIds().isEmpty()) {
				helper.fail("Expected getAllSpeciesIds() to be empty after setSpecies(empty)");
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session see the full built-in set again.
			GeneticsReloadHandler.rebuildSpecies(realDefinitions);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}
}
