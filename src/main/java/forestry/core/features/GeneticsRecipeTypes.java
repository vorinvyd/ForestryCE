package forestry.core.features;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.modules.ForestryModuleIds;
import forestry.core.genetics.mutations.MutationRecipe;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.FeatureRecipeType;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;

@FeatureProvider
public class GeneticsRecipeTypes {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.CORE);

	public static final FeatureRecipeType<MutationRecipe> BEE_MUTATION =
		REGISTRY.recipeType("bee_mutation", () -> new MutationRecipe.Serializer(ForestrySpeciesTypes.BEE));
	public static final FeatureRecipeType<MutationRecipe> TREE_MUTATION =
		REGISTRY.recipeType("tree_mutation", () -> new MutationRecipe.Serializer(ForestrySpeciesTypes.TREE));
	public static final FeatureRecipeType<MutationRecipe> BUTTERFLY_MUTATION =
		REGISTRY.recipeType("butterfly_mutation", () -> new MutationRecipe.Serializer(ForestrySpeciesTypes.BUTTERFLY));

	@Nullable
	public static FeatureRecipeType<MutationRecipe> forType(ResourceLocation speciesTypeId) {
		if (speciesTypeId.equals(ForestrySpeciesTypes.BEE)) {
			return BEE_MUTATION;
		}
		if (speciesTypeId.equals(ForestrySpeciesTypes.TREE)) {
			return TREE_MUTATION;
		}
		if (speciesTypeId.equals(ForestrySpeciesTypes.BUTTERFLY)) {
			return BUTTERFLY_MUTATION;
		}
		return null; // unknown/third-party species type
	}
}
