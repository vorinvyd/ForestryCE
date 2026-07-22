package forestry.lepidopterology.genetics;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import forestry.api.core.IProduct;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.api.plugin.IButterflySpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link IButterflySpeciesBuilder} adapter over a {@link ButterflySpeciesDefinition}: base
 * getters/mutators come from {@link AbstractDefinitionSpeciesBuilder}; this class adds the
 * butterfly-specific getters and throws from the butterfly-specific mutators. Butterflies have no
 * code-side block/worldgen bindings (unlike trees), so there is nothing else to adapt.
 *
 * @see ButterflySpeciesProjector
 */
public class DefinitionButterflySpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<ButterflySpeciesDefinition, IButterflySpeciesType, IButterflySpecies, IButterflySpeciesBuilder>
	implements IButterflySpeciesBuilder {

	public DefinitionButterflySpeciesBuilder(ButterflySpeciesDefinition def) {
		super(def);
	}

	@Override public boolean isNocturnal() { return def.nocturnal(); }
	@Override public boolean isMoth() { return def.moth(); }
	@Override public float getRarity() { return def.rarity(); }
	@Override public float getFlightDistance() { return def.flightDistance(); }
	@Override public int getSerumColor() { return def.serumColor(); }

	@Nullable
	@Override public TagKey<Biome> getSpawnBiomes() { return def.spawnBiomes().orElse(null); }

	@Override public List<IProduct> buildProducts() { return List.copyOf(def.products()); }
	@Override public List<IProduct> buildCaterpillarProducts() { return List.copyOf(def.caterpillarProducts()); }

	// --- butterfly-specific mutators (all throw) ---
	@Override public IButterflySpeciesBuilder setSerumColor(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setFlightDistance(float flightDistance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setNocturnal(boolean nocturnal) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setMoth(boolean moth) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setSpawnBiomes(TagKey<Biome> biomeTag) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setRarity(float rarity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
