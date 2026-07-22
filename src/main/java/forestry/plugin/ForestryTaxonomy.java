package forestry.plugin;

import com.google.common.collect.ImmutableMap;

import forestry.api.genetics.ForestryTaxa;
import forestry.api.genetics.ITaxon;
import forestry.api.plugin.ITaxonBuilder;
import forestry.apiimpl.plugin.GeneticRegistration;

/**
 * The authoritative in-code definition of base Forestry's whole taxonomy (domains down to genera, including each
 * genus's default chromosomes). It is consumed <em>only</em> by the data generator ({@code TaxonProvider}), which
 * serializes it to {@code data/forestry/taxon/*.json}; at runtime the taxonomy is loaded from that generated JSON and
 * merged in by {@code TaxonManager}, not built from here. Kept in the plugin package next to the per-kingdom taxonomy
 * definitions ({@link BeeTaxonomy}, {@link TreeTaxonomy}, {@link ButterflyTaxonomy}) it stitches together.
 */
public final class ForestryTaxonomy {
	private ForestryTaxonomy() {
	}

	/**
	 * Builds the full base taxonomy as live {@link ITaxon}s (keyed by taxon name) using the same registration machinery
	 * the runtime uses for add-on/KubeJS taxa. The generator reads these to emit one JSON file per taxon.
	 */
	public static ImmutableMap<String, ITaxon> buildDefaultTaxa() {
		GeneticRegistration genetics = new GeneticRegistration();
		defineSpine(genetics);
		BeeTaxonomy.defineTaxa(genetics);
		TreeTaxonomy.defineTaxa(genetics);
		ButterflyTaxonomy.defineTaxa(genetics);
		return genetics.buildTaxa();
	}

	// Domains, kingdoms and the arthropod -> insect spine shared by bees and butterflies (seven-kingdom model, Ruggiero
	// et al. 2015). Trees hang their vascular-plants phylum under plantae; bees and butterflies hang their orders under
	// insecta.
	@SuppressWarnings("CodeBlock2Expr")
	private static void defineSpine(GeneticRegistration genetics) {
		ITaxonBuilder prokaryota = genetics.defineDomain(ForestryTaxa.DOMAIN_PROKARYOTA);
		prokaryota.defineSubTaxon(ForestryTaxa.KINGDOM_ARCHAEA);
		prokaryota.defineSubTaxon(ForestryTaxa.KINGDOM_BACTERIA);

		ITaxonBuilder eukaryota = genetics.defineDomain(ForestryTaxa.DOMAIN_EUKARYOTA);
		eukaryota.defineSubTaxon(ForestryTaxa.KINGDOM_FUNGI);
		eukaryota.defineSubTaxon(ForestryTaxa.KINGDOM_PLANT);
		eukaryota.defineSubTaxon(ForestryTaxa.KINGDOM_ANIMAL, animalia -> {
			animalia.defineSubTaxon(ForestryTaxa.PHYLUM_ARTHROPODS, arthropoda -> {
				arthropoda.defineSubTaxon(ForestryTaxa.CLASS_INSECTS);
			});
		});
		eukaryota.defineSubTaxon(ForestryTaxa.KINGDOM_PROTOZOA);
		eukaryota.defineSubTaxon(ForestryTaxa.KINGDOM_CHROMISTA);
	}
}
