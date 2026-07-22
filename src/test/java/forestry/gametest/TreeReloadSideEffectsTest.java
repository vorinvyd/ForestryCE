package forestry.gametest;

import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import net.minecraft.world.level.block.state.BlockState;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITree;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;

/**
 * Exercises {@code TreeSpeciesType}'s overridden {@code setSpecies} directly, without going through
 * {@code GeneticsReloadHandler.rebuildTreeSpecies}/{@code TreeSpeciesProvider} (Task 8, not yet implemented).
 * <p>
 * Re-applies the CURRENT full live species map back through {@code setSpecies} (same objects, no identity churn),
 * so the live {@code TREE_TYPE} state is unchanged after this test runs and no restore is needed. This still
 * exercises the override's side effects (vanilla-membership rebuild + {@code ForestryLeafType} rewiring) and proves
 * {@code leafTickHandlers} survive the swap untouched, which is what the butterfly-registered {@code ButterflySpawner}
 * depends on across a real reload.
 * <p>
 * The vanilla-membership half of this test is a reference-identity guard, not a mere non-null check: since
 * {@code vanillaIndividuals} is already populated at mod setup, asserting non-null after re-applying the same
 * species objects would pass even if the {@code setSpecies} override under test were deleted entirely. Instead,
 * this test captures the {@code ITree} instance mapped to a vanilla leaf state BEFORE the swap and asserts a
 * DIFFERENT (freshly-created) instance is mapped to that same state AFTER the swap - that identity change can only
 * happen if {@code rebuildVanillaMembership} actually re-ran during {@code setSpecies}. Do not "simplify" this back
 * to a non-null check; it would silently stop guarding the behavior this task delivers.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeReloadSideEffectsTest {
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void sideEffectsSurviveReload(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();

		// Find a "keeper" species with at least one vanilla leaf state to probe identity across the swap.
		// If none has vanilla states in this build, there's nothing to assert for this half - skip it (still succeed).
		BlockState keptState = null;
		for (ITreeSpecies species : type.getAllSpecies()) {
			if (!species.getVanillaLeafStates().isEmpty()) {
				keptState = species.getVanillaLeafStates().get(0);
				break;
			}
		}
		if (keptState == null) {
			helper.succeed();
			return;
		}

		ITree before = type.getVanillaIndividual(keptState);
		int handlersBefore = type.getLeafTickHandlers().size();

		ImmutableMap<ResourceLocation, ITreeSpecies> full = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(Collectors.toMap(id -> id, type::getSpecies))
		);

		((SpeciesType<ITreeSpecies, ?>) type).setSpecies(full);

		ITree after = type.getVanillaIndividual(keptState);

		// vanilla membership rebuilt: the mapped individual must be a FRESH object, proving rebuildVanillaMembership
		// actually ran during setSpecies (see class javadoc for why a plain non-null check would be a no-op guard).
		if (before == null || after == null || before == after) {
			helper.fail("Expected setSpecies to rebuild vanilla-membership with a fresh individual for state " + keptState
				+ " (before=" + before + ", after=" + after + ")");
			return;
		}

		// leaf-tick handlers untouched by the swap
		if (type.getLeafTickHandlers().size() != handlersBefore) {
			helper.fail("Expected leaf-tick handlers to survive a tree species setSpecies swap (was " + handlersBefore + ", now " + type.getLeafTickHandlers().size() + ")");
			return;
		}

		helper.succeed();
	}
}
