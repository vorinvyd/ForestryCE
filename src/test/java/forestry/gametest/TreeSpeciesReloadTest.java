package forestry.gametest;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;

/**
 * Note: {@code TreeSpeciesProvider.buildDefinitions()} (Task 8) doesn't exist yet, so {@code rebuildRepopulatesSpecies}
 * builds a single definition inline from the live oak species (mirroring {@code TreeSpeciesProjectorTest}). Task 8
 * will expand this to the full round-trip over every code-registered tree species.
 * <p>
 * Both tests below mutate the live {@code TREE_TYPE}'s species map and restore it (plus rebuild mutations) in a
 * {@code finally} block (mirroring {@code SpeciesFallbackTest}), so other GameTests running later in the same
 * server session - notably {@code MutationRecipeTest}'s tree mutation assertions - still see the full built-in tree
 * species set.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesReloadTest {
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void rebuildRepopulatesSpecies(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ITreeSpecies oak = type.getSpecies(ForestryTreeSpecies.OAK);

		// Snapshot the live, full species map before mutating it, so it can be restored afterwards.
		ImmutableMap<ResourceLocation, ITreeSpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(java.util.stream.Collectors.toMap(id -> id, type::getSpecies))
		);

		TreeSpeciesDefinition def = TestSpeciesDefinitions.treeFrom(oak)
			.escritoireColor(oak.getEscritoireColor())
			.genome(Map.of(TreeChromosomes.HEIGHT.id(), ForestryAlleles.HEIGHT_AVERAGE))
			.build();

		Map<ResourceLocation, TreeSpeciesDefinition> defs = Map.of(ForestryTreeSpecies.OAK, def);

		try {
			GeneticsReloadHandler.rebuildTreeSpecies(defs);

			if (type.getSpeciesSafe(ForestryTreeSpecies.OAK) == null) {
				helper.fail("Expected rebuildTreeSpecies to repopulate oak from the projected definitions map");
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session (e.g. MutationRecipeTest's tree
			// mutation assertions) still see the full built-in tree species set, and re-pair the mutation index with
			// the restored species (rebuildMutations rebuilds all species types, mirroring SpeciesFallbackTest).
			((SpeciesType<ITreeSpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}

	/**
	 * Confirms {@code TreeSpeciesManager.INSTANCE} actually ran as a server reload listener at server start (Task 9
	 * cutover): it should have parsed the full 50-entry built-in {@code tree_species} datapack (generated in Task 8,
	 * byte-faithful to the code-registered species), and re-deriving the live species map from exactly those
	 * definitions should reproduce the full built-in set. Mutates and restores the live species map exactly like
	 * {@code rebuildRepopulatesSpecies} above, so it doesn't leak into later tests in the same server session.
	 */
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void managerLoadedAllSpeciesAtServerStart(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();

		Map<ResourceLocation, TreeSpeciesDefinition> definitions = TreeSpeciesManager.INSTANCE.getDefinitions();
		if (definitions.size() != 50) {
			helper.fail("Expected TreeSpeciesManager to have loaded 50 tree species from the datapack at server start, got " + definitions.size());
			return;
		}

		// Snapshot the live, full species map before re-deriving it from the manager's definitions, so it can be
		// restored afterwards (mirrors rebuildRepopulatesSpecies above).
		ImmutableMap<ResourceLocation, ITreeSpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(java.util.stream.Collectors.toMap(id -> id, type::getSpecies))
		);

		try {
			GeneticsReloadHandler.rebuildTreeSpecies(definitions);

			if (type.getAllSpeciesIds().size() != 50) {
				helper.fail("Expected rebuildTreeSpecies(manager definitions) to reproduce the full 50-species built-in set, got " + type.getAllSpeciesIds().size());
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session (e.g. MutationRecipeTest's tree
			// mutation assertions) still see the full built-in tree species set, and re-pair the mutation index with
			// the restored species (rebuildMutations rebuilds all species types, mirroring SpeciesFallbackTest).
			((SpeciesType<ITreeSpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}
}
