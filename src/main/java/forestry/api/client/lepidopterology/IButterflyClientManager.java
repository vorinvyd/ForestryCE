package forestry.api.client.lepidopterology;

import com.mojang.datafixers.util.Pair;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/**
 * Tracks client-only data for butterfly species.
 */
public interface IButterflyClientManager {
	/**
	 * @return The butterfly item and entity textures, respectively, resolved by the species' id. Falls back to a
	 * default-naming-convention pair (and ultimately {@link #getDefaultTextures()}) when no explicit pair was
	 * registered for the species' id, so this never needs the species list/instance.
	 */
	Pair<ResourceLocation, ResourceLocation> getTextures(IButterflySpecies species);

	/**
	 * @return The item+entity texture pair used when a species has no registered/derivable pair. Never requires the
	 * species list, so it is safe before/after a datapack species reload.
	 */
	Pair<ResourceLocation, ResourceLocation> getDefaultTextures();

	/**
	 * @return A collection of every explicitly registered item/entity texture pair.
	 */
	Collection<Pair<ResourceLocation, ResourceLocation>> getAllTextures();
}
