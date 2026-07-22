package forestry.core.genetics;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.alleles.Allele;

/**
 * The base genetics/metadata shared by every data-driven species definition
 * ({@code BeeSpeciesDefinition}, {@code TreeSpeciesDefinition}, {@code ButterflySpeciesDefinition}).
 * The three records are flat and implement this interface directly (their record accessors satisfy
 * it), letting the shared adapter base ({@link AbstractDefinitionSpeciesBuilder}) and projection
 * helper ({@link SpeciesProjection}) read base fields polymorphically without a common supertype.
 */
public interface ISpeciesDefinition {
	String genus();

	String species();

	boolean dominant();

	boolean glint();

	boolean secret();

	int complexity();

	String authority();

	int escritoireColor();

	TemperatureType temperature();

	HumidityType humidity();

	Map<ResourceLocation, Allele<?>> genome();

	/**
	 * @return A {@link SpeciesCore} view of this definition's base fields, used by the definition codecs
	 * to serialize the shared fields through one shared codec.
	 */
	default SpeciesCore core() {
		return new SpeciesCore(genus(), species(), dominant(), glint(), secret(),
			complexity(), authority(), escritoireColor(), temperature(), humidity());
	}
}
