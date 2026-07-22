package forestry.apiimpl.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;

import forestry.api.client.lepidopterology.IButterflyClientManager;
import forestry.api.lepidopterology.ForestryButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpecies;

public class ButterflyClientManager implements IButterflyClientManager {
	private final Map<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> textures;

	public ButterflyClientManager(Map<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> textures) {
		this.textures = textures;
	}

	@Override
	public Pair<ResourceLocation, ResourceLocation> getTextures(IButterflySpecies species) {
		ResourceLocation id = species.id();
		Pair<ResourceLocation, ResourceLocation> pair = this.textures.get(id);
		if (pair != null) {
			return pair;
		}
		// Default item/entity texture naming convention, computed lazily from the species id itself (mirrors
		// TreeClientManager#getTint's render-time fallback), so it works for datapack-added species too and needs no
		// species-list iteration at client-registration time.
		return defaultTexturesFor(id);
	}

	@Override
	public Pair<ResourceLocation, ResourceLocation> getDefaultTextures() {
		Pair<ResourceLocation, ResourceLocation> cabbageWhite = this.textures.get(ForestryButterflySpecies.CABBAGE_WHITE);
		if (cabbageWhite != null) {
			return cabbageWhite;
		}
		// last-resort literal fallback so bake never NPEs before any species/textures are registered
		return defaultTexturesFor(ForestryButterflySpecies.CABBAGE_WHITE);
	}

	@Override
	public Collection<Pair<ResourceLocation, ResourceLocation>> getAllTextures() {
		return Collections.unmodifiableCollection(this.textures.values());
	}

	private static Pair<ResourceLocation, ResourceLocation> defaultTexturesFor(ResourceLocation id) {
		String path = id.getPath().replace("butterfly_", "");
		return Pair.of(
			ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "item/butterfly/" + path),
			ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/entity/butterfly/" + path + ".png")
		);
	}
}
