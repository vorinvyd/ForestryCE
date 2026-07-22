package forestry.core.genetics;

import java.util.function.Consumer;

import net.minecraft.network.chat.TextColor;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.ISpeciesBuilder;

/**
 * Shared read-only {@link ISpeciesBuilder} adapter over an {@link ISpeciesDefinition}: answers every
 * base getter the {@code Species} constructor reads from the definition, and throws from every base
 * mutator/factory method, since datapack species are immutable data. Concrete per-type subclasses add
 * only their type-specific getters (and their type-specific throwing setters).
 *
 * @param <D> the concrete definition type, so subclasses can read type-specific fields off {@link #def}.
 */
public abstract class AbstractDefinitionSpeciesBuilder<
	D extends ISpeciesDefinition,
	T extends ISpeciesType<S, ?>,
	S extends ISpecies<?>,
	B extends ISpeciesBuilder<T, S, B>>
	implements ISpeciesBuilder<T, S, B> {

	protected static final String READ_ONLY_MESSAGE = "datapack species builder is read-only";

	protected final D def;

	protected AbstractDefinitionSpeciesBuilder(D def) {
		this.def = def;
	}

	// --- base getters (from the definition) ---
	@Override public String getGenus() { return def.genus(); }
	@Override public String getSpecies() { return def.species(); }
	@Override public boolean isDominant() { return def.dominant(); }
	@Override public boolean hasGlint() { return def.glint(); }
	@Override public boolean isSecret() { return def.secret(); }
	@Override public int getComplexity() { return def.complexity(); }
	@Override public String getAuthority() { return def.authority(); }
	@Override public int getEscritoireColor() { return def.escritoireColor(); }
	@Override public TemperatureType getTemperature() { return def.temperature(); }
	@Override public HumidityType getHumidity() { return def.humidity(); }

	// --- base mutators / factory (all throw) ---
	@Override public B setDominant(boolean dominant) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setGenome(Consumer<IGenomeBuilder> genome) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setGlint(boolean glint) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setTemperature(TemperatureType temperature) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setHumidity(HumidityType humidity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setComplexity(int complexity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setEscritoireColor(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setSecret(boolean secret) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setAuthority(String authority) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setFactory(ISpeciesBuilder.ISpeciesFactory<T, S, B> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IGenome buildGenome(IGenomeBuilder builder) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ISpeciesBuilder.ISpeciesFactory<T, S, B> createSpeciesFactory() { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
