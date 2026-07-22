package forestry.apiimpl.client;

import forestry.api.client.apiculture.IBeeClientManager;
import forestry.api.genetics.ILifeStage;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

public class BeeClientManager implements IBeeClientManager {
	// life stage -> default model location
	private final IdentityHashMap<ILifeStage, ResourceLocation> defaultModels;
	// life stage -> (species id -> custom model location)
	private final IdentityHashMap<ILifeStage, Map<ResourceLocation, ResourceLocation>> customModels;

	public BeeClientManager(IdentityHashMap<ILifeStage, ResourceLocation> defaultModels, IdentityHashMap<ILifeStage, Map<ResourceLocation, ResourceLocation>> customModels) {
		this.defaultModels = defaultModels;
		this.customModels = customModels;
	}

	@Override
	public ResourceLocation getModelLocation(ILifeStage stage, ResourceLocation speciesId) {
		Map<ResourceLocation, ResourceLocation> customs = this.customModels.get(stage);
		ResourceLocation custom = customs == null ? null : customs.get(speciesId);

		if (custom != null) {
			return custom;
		}

		return Objects.requireNonNull(this.defaultModels.get(stage), "No default bee model registered for life stage " + stage.getSerializedName());
	}

	@Override
	public ResourceLocation getDefaultModelLocation(ILifeStage stage) {
		return Objects.requireNonNull(this.defaultModels.get(stage), "No default bee model registered for life stage " + stage.getSerializedName());
	}

	@Override
	public Collection<ResourceLocation> getAllModelLocations(ILifeStage stage) {
		LinkedHashSet<ResourceLocation> locations = new LinkedHashSet<>();
		ResourceLocation defaultModel = this.defaultModels.get(stage);

		if (defaultModel != null) {
			locations.add(defaultModel);
		}

		Map<ResourceLocation, ResourceLocation> customs = this.customModels.get(stage);

		if (customs != null) {
			locations.addAll(customs.values());
		}

		return locations;
	}
}
