package forestry.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.IForestryApi;
import forestry.api.apiculture.ForestryBeeSpecies;
import forestry.api.apiculture.ForestryFlowerTypes;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.genetics.ForestryTaxa;
import forestry.api.genetics.IGeneticManager;
import forestry.api.genetics.ITaxon;
import forestry.api.genetics.TaxonomicRank;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.apiculture.genetics.TaxonManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.genetics.TaxonDefinition;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for the (now fully data-driven) taxonomy. Proves that base Forestry's whole taxonomy is loaded from
 * the generated datapack JSON (domains through genera, none of it code-registered anymore), that a genus's datapack
 * default chromosomes are inherited into the genome of species projected under it, and that datapack taxa merge with
 * fixpoint parent resolution (a child listed before its parent still resolves; an orphan is skipped; an empty reload
 * reverts to exactly the - now empty - code base).
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TaxonSystemTest {
	@GameTest(template = "empty")
	public static void baseTaxonomyLoadedFromDatapack(GameTestHelper helper) {
		IGeneticManager gm = IForestryApi.INSTANCE.getGeneticManager();

		// A slice of the tree from the root down to a genus, spanning the spine + bee kingdom - all datapack-loaded now.
		assertRank(helper, gm, ForestryTaxa.DOMAIN_EUKARYOTA, TaxonomicRank.DOMAIN);
		assertRank(helper, gm, ForestryTaxa.KINGDOM_ANIMAL, TaxonomicRank.KINGDOM);
		assertRank(helper, gm, ForestryTaxa.CLASS_INSECTS, TaxonomicRank.CLASS);
		assertRank(helper, gm, ForestryTaxa.FAMILY_BEES, TaxonomicRank.FAMILY);
		assertRank(helper, gm, ForestryTaxa.GENUS_INFERNAL, TaxonomicRank.GENUS);

		ITaxon domain = gm.getTaxonSafe(ForestryTaxa.DOMAIN_EUKARYOTA);
		if (domain != null && domain.parent() != null) {
			helper.fail("a domain must have no parent, but '" + ForestryTaxa.DOMAIN_EUKARYOTA + "' had '" + domain.parent().name() + "'");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void genusDefaultAllelesInheritedFromDatapack(GameTestHelper helper) {
		IGeneticManager gm = IForestryApi.INSTANCE.getGeneticManager();

		// The infernal genus carries a NETHER flower-type default in its datapack taxon (data/forestry/taxon/diapis.json).
		ITaxon infernal = gm.getTaxonSafe(ForestryTaxa.GENUS_INFERNAL);
		if (infernal == null || !infernal.alleles().containsKey(BeeChromosomes.FLOWER_TYPE)) {
			helper.fail("the infernal genus should carry its datapack FLOWER_TYPE default allele");
			return;
		}

		// ...and that default must flatten into the genome of a species projected under it (Demonic, genus infernal),
		// which no longer states a flower type of its own - it inherits the genus's.
		IBeeSpecies demonic = SpeciesUtil.BEE_TYPE.get().getSpecies(ForestryBeeSpecies.DEMONIC);
		if (demonic == null) {
			helper.fail("the Demonic bee species was not loaded");
			return;
		}
		ResourceLocation flowerType = demonic.getDefaultGenome().getActiveValue(BeeChromosomes.FLOWER_TYPE);
		if (!ForestryFlowerTypes.NETHER.equals(flowerType)) {
			helper.fail("Demonic should inherit the infernal genus NETHER flower type, but got '" + flowerType + "'");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void datapackTaxaMergeWithFixpointAndSkip(GameTestHelper helper) {
		IGeneticManager gm = IForestryApi.INSTANCE.getGeneticManager();
		var original = TaxonManager.INSTANCE.getDefinitions().values();
		try {
			// A self-contained slice: a root domain, a kingdom under it (listed BEFORE its parent, so the fixpoint must
			// still resolve it and derive its rank), and an orphan whose parent never resolves.
			TaxonDefinition domain = new TaxonDefinition(null, "gametest_domain", TaxonomicRank.DOMAIN, null, Map.of());
			TaxonDefinition kingdom = new TaxonDefinition("gametest_domain", "gametest_kingdom");
			TaxonDefinition orphan = new TaxonDefinition("no_such_parent", "gametest_orphan");
			GeneticsReloadHandler.rebuildTaxa(List.of(kingdom, domain, orphan));

			ITaxon builtDomain = gm.getTaxonSafe("gametest_domain");
			ITaxon builtKingdom = gm.getTaxonSafe("gametest_kingdom");
			if (builtDomain == null || builtDomain.rank() != TaxonomicRank.DOMAIN) {
				helper.fail("a root domain (null parent, explicit rank) failed to register");
				return;
			}
			if (builtKingdom == null || builtKingdom.rank() != TaxonomicRank.KINGDOM) {
				helper.fail("fixpoint resolution failed to register a child listed before its parent, or mis-derived its rank");
				return;
			}
			if (gm.getTaxonSafe("gametest_orphan") != null) {
				helper.fail("a taxon whose parent never resolves should be skipped, not registered");
				return;
			}

			// An empty reload reverts to exactly the code base, which for vanilla Forestry is now empty: the whole
			// taxonomy (including the built-in domains) comes from the datapack.
			GeneticsReloadHandler.rebuildTaxa(List.of());
			if (gm.getTaxonSafe("gametest_domain") != null || gm.getTaxonSafe(ForestryTaxa.DOMAIN_EUKARYOTA) != null) {
				helper.fail("an empty reload should revert to exactly the (empty) code base");
				return;
			}
		} finally {
			GeneticsReloadHandler.rebuildTaxa(original);
		}
		helper.succeed();
	}

	private static void assertRank(GameTestHelper helper, IGeneticManager gm, String name, TaxonomicRank expected) {
		ITaxon taxon = gm.getTaxonSafe(name);
		if (taxon == null) {
			helper.fail("base taxon '" + name + "' was not loaded from the datapack");
		} else if (taxon.rank() != expected) {
			helper.fail("base taxon '" + name + "' should be rank " + expected + " but was " + taxon.rank());
		}
	}
}
