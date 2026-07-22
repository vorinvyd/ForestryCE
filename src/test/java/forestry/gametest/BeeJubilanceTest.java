package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.ForestryBeeJubilances;
import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.apiculture.genetics.DefaultBeeJubilance;
import forestry.apiculture.genetics.HermitBeeJubilance;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for the jubilance registry (Task 1 of the data-driven bees Stage 3 plumbing). Proves the two
 * builtin {@link IBeeJubilance}s registered by {@code DefaultForestryPlugin} are addressable by id through
 * {@link IBeeSpeciesType#getJubilance} / {@link IBeeSpeciesType#getJubilanceSafe}, and that the safe getter returns
 * {@code null} for an unregistered id instead of throwing.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class BeeJubilanceTest {
	/** The two builtin jubilances must resolve to their exact singleton instances by id. */
	@GameTest(template = "empty")
	public static void builtinJubilancesResolveById(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();

		IBeeJubilance defaultJubilance = beeType.getJubilance(ForestryBeeJubilances.DEFAULT);
		if (defaultJubilance != DefaultBeeJubilance.INSTANCE) {
			helper.fail("getJubilance(DEFAULT) did not resolve to DefaultBeeJubilance.INSTANCE");
			return;
		}

		IBeeJubilance hermitJubilance = beeType.getJubilance(ForestryBeeJubilances.HERMIT);
		if (hermitJubilance != HermitBeeJubilance.INSTANCE) {
			helper.fail("getJubilance(HERMIT) did not resolve to HermitBeeJubilance.INSTANCE");
			return;
		}

		helper.succeed();
	}

	/** The safe getter must return null for an id that was never registered, rather than throwing. */
	@GameTest(template = "empty")
	public static void unregisteredJubilanceSafeGetterReturnsNull(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();

		IBeeJubilance jubilance = beeType.getJubilanceSafe(ForestryConstants.forestry("nonexistent"));
		if (jubilance != null) {
			helper.fail("getJubilanceSafe(nonexistent) returned " + jubilance + ", expected null");
			return;
		}

		helper.succeed();
	}
}
