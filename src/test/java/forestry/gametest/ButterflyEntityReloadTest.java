package forestry.gametest;

import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.lepidopterology.genetics.IButterfly;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.entities.EntityButterfly;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;

/**
 * Verifies that a live {@link EntityButterfly} is refreshed to hold a fresh species instance after a datapack
 * reload swaps the butterfly species map ({@code GeneticsReloadHandler#rebuildButterflySpecies}), rather than
 * keeping the stale instance it cached at spawn time - see {@code EntityButterfly#refreshSpeciesFromReload}.
 * <p>
 * Note: {@code ButterflySpeciesProvider.buildDefinitions()} (Task 8) doesn't exist yet, so the reload here builds
 * a single definition inline from the live default species (mirroring {@code ButterflySpeciesReloadTest}).
 * <p>
 * Mutates the live {@code BUTTERFLY_TYPE}'s species map and restores it (plus rebuilds mutations) in a
 * {@code finally} block, so later GameTests in the same server session still see the full built-in species set.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflyEntityReloadTest {
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void entityRefreshedAfterReload(GameTestHelper helper) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();

		// Snapshot the live, full species map before mutating it, so it can be restored afterwards.
		ImmutableMap<ResourceLocation, IButterflySpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(Collectors.toMap(id -> id, type::getSpecies))
		);

		EntityButterfly entity = null;
		try {
			IButterflySpecies defaultSpecies = type.getDefaultSpecies();
			IButterfly individual = defaultSpecies.createIndividual();
			BlockPos rel = new BlockPos(2, 2, 2);
			BlockPos abs = helper.absolutePos(rel);
			entity = (EntityButterfly) type.spawnButterflyInWorld(helper.getLevel(), individual, abs.getX(), abs.getY(), abs.getZ());
			IButterflySpecies beforeSpecies = entity.getButterfly().getSpecies();

			ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(defaultSpecies).build();
			Map<ResourceLocation, ButterflySpeciesDefinition> defs = Map.of(defaultSpecies.id(), def);

			// reload: rebuild the species map (fresh species instances) then refresh loaded entities.
			GeneticsReloadHandler.rebuildButterflySpecies(defs);

			IButterflySpecies afterSpecies = entity.getButterfly().getSpecies();
			if (afterSpecies == beforeSpecies) {
				helper.fail("Expected the entity's species to be refreshed to the new instance after a reload");
				return;
			}
			helper.assertTrue(afterSpecies.id().equals(beforeSpecies.id()),
				"refreshed species must be the same id, a fresh instance");
		} finally {
			if (entity != null) {
				entity.discard();
			}
			((SpeciesType<IButterflySpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}
}
