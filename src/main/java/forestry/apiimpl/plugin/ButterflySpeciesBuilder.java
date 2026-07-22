package forestry.apiimpl.plugin;

import forestry.api.core.IProduct;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.api.plugin.IButterflySpeciesBuilder;
import forestry.lepidopterology.ButterflySpecies;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.List;

public class ButterflySpeciesBuilder extends SpeciesBuilder<IButterflySpeciesType, IButterflySpecies, IButterflySpeciesBuilder> implements IButterflySpeciesBuilder {
	private float flightDistance = 5.0f;
	private int serumColor;
	private boolean nocturnal;
	private boolean moth;
	private float rarity = 0.1f;
	@Nullable
	private TagKey<Biome> spawnBiomes = null;

	public ButterflySpeciesBuilder(ResourceLocation id, String genus, String species) {
		super(id, genus, species);
	}

	@Override
	public IButterflySpeciesBuilder setSerumColor(TextColor color) {
		this.serumColor = color.getValue();
		return this;
	}

	@Override
	public IButterflySpeciesBuilder setFlightDistance(float flightDistance) {
		this.flightDistance = flightDistance;
		return this;
	}

	@Override
	public IButterflySpeciesBuilder setNocturnal(boolean nocturnal) {
		this.nocturnal = nocturnal;
		return this;
	}

	@Override
	public IButterflySpeciesBuilder setMoth(boolean moth) {
		this.moth = moth;
		return this;
	}

	@Override
	public IButterflySpeciesBuilder setSpawnBiomes(TagKey<Biome> biomeTag) {
		this.spawnBiomes = biomeTag;
		return this;
	}

	@Override
	public IButterflySpeciesBuilder setRarity(float rarity) {
		if (rarity < 0 || rarity > 1) {
			throw new IllegalArgumentException("Invalid rarity " + rarity + " - must be within [0,1]");
		}
		this.rarity = rarity;
		return this;
	}

	@Override
	public int getSerumColor() {
		return this.serumColor;
	}

	@Override
	public float getFlightDistance() {
		return this.flightDistance;
	}

	@Override
	public boolean isNocturnal() {
		return this.nocturnal;
	}

	@Override
	public boolean isMoth() {
		return this.moth;
	}

	@Nullable
	@Override
	public TagKey<Biome> getSpawnBiomes() {
		return this.spawnBiomes;
	}

	@Override
	public float getRarity() {
		return this.rarity;
	}

	@Override
	public List<IProduct> buildProducts() {
		// todo implement
		return List.of();
	}

	@Override
	public List<IProduct> buildCaterpillarProducts() {
		// todo implement
		return List.of();
	}

	@Override
	public ISpeciesFactory<IButterflySpeciesType, IButterflySpecies, IButterflySpeciesBuilder> createSpeciesFactory() {
		return ButterflySpecies::new;
	}
}
