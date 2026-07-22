package forestry.gametest;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.SpeciesType;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.ButterflySpawner;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;

/**
 * Locks the Stage-5 §6 invariant: {@code ButterflySpawner} is registered on the tree species type's leaf tick
 * handlers exactly once, at setup ({@code ButterflySpeciesType.onSpeciesRegistered}, called once by
 * {@code PluginManager}), and stays at exactly one across a butterfly species reload - because reloads go through
 * {@code SpeciesType.setSpecies} directly (via {@code GeneticsReloadHandler.rebuildButterflySpecies}), which does not
 * call {@code onSpeciesRegistered}.
 * <p>
 * Note: {@code ButterflySpeciesProvider.buildDefinitions()} (Task 8) doesn't exist yet, so the reload is driven by a
 * single definition built inline from the live Monarch species, mirroring {@code ButterflySpeciesReloadTest}
 * (Task 4). Mutates the live {@code BUTTERFLY_TYPE}'s species map and restores it (plus rebuilds mutations) in a
 * {@code finally} block, so other GameTests running later in the same server session - notably
 * {@code MutationRecipeTest}'s butterfly mutation assertions - still see the full built-in butterfly species set.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpawnerReloadTest {
	@GameTest(template = "empty")
	@SuppressWarnings("unchecked")
	public static void spawnerCountStableAcrossReload(GameTestHelper helper) {
		ITreeSpeciesType treeType = SpeciesUtil.TREE_TYPE.get();
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		IButterflySpecies monarch = type.getSpecies(ForestryButterflySpecies.MONARCH);

		long before = treeType.getLeafTickHandlers().stream()
			.filter(h -> h instanceof ButterflySpawner)
			.count();
		if (before != 1) {
			helper.fail("Expected exactly one ButterflySpawner registered at setup, found " + before);
			return;
		}

		// Snapshot the live, full species map before mutating it, so it can be restored afterwards.
		ImmutableMap<ResourceLocation, IButterflySpecies> snapshot = ImmutableMap.copyOf(
			type.getAllSpeciesIds().stream().collect(java.util.stream.Collectors.toMap(id -> id, type::getSpecies))
		);

		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();

		Map<ResourceLocation, ButterflySpeciesDefinition> defs = Map.of(ForestryButterflySpecies.MONARCH, def);

		try {
			// simulate a butterfly species reload via the real reload path (not the raw setSpecies swap)
			GeneticsReloadHandler.rebuildButterflySpecies(defs);

			long after = treeType.getLeafTickHandlers().stream()
				.filter(h -> h instanceof ButterflySpawner)
				.count();
			if (after != 1) {
				helper.fail("Expected the ButterflySpawner count to remain exactly 1 across a butterfly species "
					+ "reload (was " + before + ", now " + after + ") - it must be registered once at setup, not "
					+ "duplicated or dropped per reload");
				return;
			}
		} finally {
			// Restore the live state so later tests in this same server session (e.g. MutationRecipeTest's
			// butterfly mutation assertions) still see the full built-in butterfly species set, and re-pair the
			// mutation index with the restored species (rebuildMutations rebuilds all species types, mirroring
			// TreeSpeciesReloadTest / ButterflySpeciesReloadTest).
			((SpeciesType<IButterflySpecies, ?>) type).setSpecies(snapshot);
			GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
		}

		helper.succeed();
	}
}
