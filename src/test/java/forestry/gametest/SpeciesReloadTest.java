package forestry.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for the reloadable {@code allSpecies} map on {@link forestry.core.genetics.SpeciesType}: proves
 * that {@code getAllSpecies()}/{@code getSpeciesCount()} on the live bee type are safe to call without throwing (the
 * old {@code checkSpecies()} guard used to throw {@code IllegalStateException} before registration completed) and
 * stay consistent with each other, and that the real reload path - {@link BeeSpeciesManager} parsing the generated
 * {@code bee_species} datapack JSON at server start, then {@link GeneticsReloadHandler#rebuildSpecies} projecting it
 * into the live species type - actually produces the full built-in set.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpeciesReloadTest {
	/**
	 * Mirrors {@code BeeSpeciesEquivalenceTest}'s canary: 69 is the current number of built-in bee species / generated
	 * {@code bee_species/*.json} files. A tight floor makes a silently-dropped species (or a manager that never
	 * loaded any JSON at all) fail here immediately instead of passing with a quietly-shortened set.
	 */
	private static final int EXPECTED_BUILTIN_COUNT = 69;

	@GameTest(template = "empty")
	public static void allSpeciesNeverThrowsAndCountMatches(GameTestHelper helper) {
		List<IBeeSpecies> allSpecies = SpeciesUtil.BEE_TYPE.get().getAllSpecies();
		if (allSpecies == null) {
			helper.fail("getAllSpecies() returned null for the bee species type");
			return;
		}
		int count = SpeciesUtil.BEE_TYPE.get().getSpeciesCount();
		if (count != allSpecies.size()) {
			helper.fail("getSpeciesCount() (" + count + ") did not match getAllSpecies().size() (" + allSpecies.size() + ")");
			return;
		}

		helper.succeed();
	}

	/**
	 * Proves the actual load path: {@link BeeSpeciesManager#INSTANCE} must have parsed the generated
	 * {@code bee_species} JSON at server start (this test only reads {@code getDefinitions()}, it never parses JSON
	 * itself - a manager that silently loaded 0 definitions, e.g. because the generated-resources source set is not
	 * on the runtime classpath, must fail here). Re-driving {@link GeneticsReloadHandler#rebuildSpecies} from those
	 * definitions must then (re)populate the live bee species type with the full built-in set, one live species per
	 * definition id.
	 */
	@GameTest(template = "empty")
	public static void rebuildFromLoadedDefinitionsProducesFullSet(GameTestHelper helper) {
		Map<ResourceLocation, BeeSpeciesDefinition> defs = BeeSpeciesManager.INSTANCE.getDefinitions();
		if (defs.size() < EXPECTED_BUILTIN_COUNT) {
			helper.fail("BeeSpeciesManager only holds " + defs.size() + " definitions (expected at least "
				+ EXPECTED_BUILTIN_COUNT + "); the bee_species datapack JSON was not loaded at server start");
			return;
		}

		GeneticsReloadHandler.rebuildSpecies(defs);
		// Species and mutations are always rebuilt together in production (species-before-mutations, since the
		// mutation index keys species by object identity - see GeneticsReloadHandler's class doc). Re-pairing them
		// here keeps the live state consistent for any other GameTest that runs later in this same server session.
		GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());

		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		List<IBeeSpecies> allSpecies = type.getAllSpecies();
		if (allSpecies.size() != defs.size()) {
			helper.fail("rebuildSpecies produced " + allSpecies.size() + " species from " + defs.size()
				+ " definitions (a definition failed to project - see log)");
			return;
		}
		if (allSpecies.size() < EXPECTED_BUILTIN_COUNT) {
			helper.fail("Expected at least " + EXPECTED_BUILTIN_COUNT + " bee species after rebuildSpecies, found " + allSpecies.size());
			return;
		}
		if (!type.getAllSpeciesIds().equals(defs.keySet())) {
			helper.fail("Live species ids after rebuildSpecies did not match the loaded definition ids");
			return;
		}

		helper.succeed();
	}
}
