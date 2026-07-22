package forestry.gametest;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesManager;

/**
 * Note: {@code rebuildRepopulates} builds a single definition inline from the live Monarch species (mirroring
 * {@code ButterflySpeciesProjectorTest}) rather than round-tripping every code-registered butterfly species.
 * <p>
 * Both tests below mutate the live {@code BUTTERFLY_TYPE}'s species map and restore it (plus rebuild mutations) in a
 * {@code finally} block (mirroring {@code TreeSpeciesReloadTest}), so other GameTests running later in the same
 * server session - notably {@code MutationRecipeTest}'s butterfly mutation assertions - still see the full built-in
 * butterfly species set.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpeciesReloadTest {
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void rebuildRepopulates(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		IButterflySpecies monarch = type.getSpecies(ForestryButterflySpecies.MONARCH);

		// Snapshot the live, full species map before mutating it, so it can be restored afterwards.
		ImmutableMap<ResourceLocation, IButterflySpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(java.util.stream.Collectors.toMap(id -> id, type::getSpecies))
		);

		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();

		Map<ResourceLocation, ButterflySpeciesDefinition> defs = Map.of(ForestryButterflySpecies.MONARCH, def);

		try {
			GeneticsReloadHandler.rebuildButterflySpecies(defs);

			if (type.getAllSpeciesIds().isEmpty()) {
				helper.fail("Expected rebuildButterflySpecies to repopulate the species map from the projected definitions");
				return;
			}
			if (type.getSpeciesSafe(ForestryButterflySpecies.MONARCH) == null) {
				helper.fail("Expected rebuildButterflySpecies to repopulate Monarch from the projected definitions map");
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session (e.g. MutationRecipeTest's
			// butterfly mutation assertions) still see the full built-in butterfly species set, and re-pair the
			// mutation index with the restored species (rebuildMutations rebuilds all species types, mirroring
			// TreeSpeciesReloadTest).
			((SpeciesType<IButterflySpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}

	/**
	 * Confirms {@code ButterflySpeciesManager.INSTANCE} actually ran as a server reload listener at server start
	 * (Task 9 cutover): it should have parsed the full 35-entry built-in {@code butterfly_species} datapack
	 * (generated in Task 8, byte-faithful to the code-registered species), and re-deriving the live species map from
	 * exactly those definitions should reproduce the full built-in set. Mutates and restores the live species map
	 * exactly like {@code rebuildRepopulates} above, so it doesn't leak into later tests in the same server session.
	 */
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void managerLoadedAllSpeciesAtServerStart(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();

		Map<ResourceLocation, ButterflySpeciesDefinition> definitions = ButterflySpeciesManager.INSTANCE.getDefinitions();
		if (definitions.size() != 35) {
			helper.fail("Expected ButterflySpeciesManager to have loaded 35 butterfly species from the datapack at server start, got " + definitions.size());
			return;
		}

		// Snapshot the live, full species map before re-deriving it from the manager's definitions, so it can be
		// restored afterwards (mirrors rebuildRepopulates above).
		ImmutableMap<ResourceLocation, IButterflySpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(java.util.stream.Collectors.toMap(id -> id, type::getSpecies))
		);

		try {
			GeneticsReloadHandler.rebuildButterflySpecies(definitions);

			if (type.getAllSpeciesIds().size() != 35) {
				helper.fail("Expected rebuildButterflySpecies(manager definitions) to reproduce the full 35-species built-in set, got " + type.getAllSpeciesIds().size());
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session (e.g. MutationRecipeTest's
			// butterfly mutation assertions) still see the full built-in butterfly species set, and re-pair the
			// mutation index with the restored species (rebuildMutations rebuilds all species types, mirroring
			// TreeSpeciesReloadTest).
			((SpeciesType<IButterflySpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}
}
