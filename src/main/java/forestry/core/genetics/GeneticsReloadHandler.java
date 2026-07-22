package forestry.core.genetics;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import forestry.Forestry;
import forestry.api.IForestryApi;
import forestry.api.apiculture.IFlowerType;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.apiculture.BeeSpecies;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesProjector;
import forestry.apiculture.genetics.BeeSpeciesType;
import forestry.apiimpl.GeneticManager;
import forestry.apiculture.genetics.FlowerTypeTypes;
import forestry.arboriculture.TreeSpecies;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesProjector;
import forestry.core.features.GeneticsRecipeTypes;
import forestry.core.genetics.mutations.Mutation;
import forestry.core.genetics.mutations.MutationConditionTypes;
import forestry.core.genetics.mutations.MutationRecipe;
import forestry.core.utils.SpeciesUtil;
import forestry.lepidopterology.ButterflySpecies;
import forestry.lepidopterology.entities.EntityButterfly;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesProjector;
import forestry.modules.features.FeatureRecipeType;

/**
 * Rebuilds runtime genetics state from loaded data.
 * <p>
 * {@link #rebuildSpecies} projects the datapack-loaded (or, on the client, sync-packet-delivered) bee species
 * definitions into the live {@code BeeSpeciesType}: fires on server datapack (re)load (via {@code BeeSpeciesManager},
 * itself driven by {@code AddReloadListenerEvent}) and on client sync (via {@code BeeSpeciesSyncPacket}).
 * <p>
 * {@link #rebuildMutations} rebuilds each species type's {@link MutationManager} from the loaded
 * {@link MutationRecipe}s. Fires on server datapack (re)load (via {@code AddReloadListenerEvent}) and on client
 * recipe sync (via {@code RecipesUpdatedEvent}), so the runtime mutation index always mirrors the active recipes for
 * both gameplay and JEI/analyzer display.
 * <p>
 * <b>Ordering matters:</b> mutation recipes resolve their species by looking them up (by id) in the live
 * {@code allSpecies} map at rebuild time, and the resulting {@link IMutation}s are indexed by species <em>object
 * identity</em> ({@link MutationManager} uses an {@code IdentityHashMap}). Species must therefore always be rebuilt
 * before mutations - every call site in this codebase does {@code rebuildSpecies} then {@code rebuildMutations}, in
 * that order, on the same thread/executor.
 * <p>
 * Safe to run with zero definitions/recipes: species/mutation indexes are simply left/set empty.
 */
public final class GeneticsReloadHandler {
	/**
	 * Projects each definition into a {@link BeeSpecies} (fail-soft: a bad definition is logged and dropped by
	 * {@link BeeSpeciesProjector#project}) and swaps the resulting map into the live bee species type.
	 */
	@SuppressWarnings("unchecked")
	public static void rebuildSpecies(Map<ResourceLocation, BeeSpeciesDefinition> defs) {
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		ImmutableMap.Builder<ResourceLocation, IBeeSpecies> builder = ImmutableMap.builderWithExpectedSize(defs.size());
		for (Map.Entry<ResourceLocation, BeeSpeciesDefinition> entry : defs.entrySet()) {
			ResourceLocation id = entry.getKey();
			BeeSpecies species = BeeSpeciesProjector.project(type, id, entry.getValue());
			if (species != null) {
				builder.put(id, species);
			}
		}
		ImmutableMap<ResourceLocation, IBeeSpecies> allSpecies = builder.build();
		((SpeciesType<IBeeSpecies, ?>) type).setSpecies(allSpecies);
		Forestry.LOGGER.info("Loaded {} bee species", allSpecies.size());
	}

	/**
	 * Installs the effective flower-type map into the live bee species type: the code-registered base
	 * (KubeJS/addons) overlaid by the datapack-loaded (or sync-delivered) definitions, datapack winning on id.
	 */
	public static void rebuildFlowerTypes(Map<ResourceLocation, IFlowerType> dataDefinitions) {
		FlowerTypeTypes.registerBuiltins(); // idempotent safety net
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		Map<ResourceLocation, IFlowerType> effective = new LinkedHashMap<>(((BeeSpeciesType) type).getCodeFlowerTypes());
		effective.putAll(dataDefinitions);
		((BeeSpeciesType) type).setFlowerTypes(ImmutableMap.copyOf(effective));
	}

	/**
	 * Installs the effective bee-effect map into the live bee species type: the code-registered base overlaid by the
	 * datapack-loaded (or sync-delivered) effects, datapack winning on id. Mirrors {@link #rebuildFlowerTypes}.
	 */
	public static void rebuildBeeEffects(Map<ResourceLocation, IBeeEffect> dataDefinitions) {
		BeeSpeciesType type = (BeeSpeciesType) SpeciesUtil.BEE_TYPE.get();
		Map<ResourceLocation, IBeeEffect> effective = new LinkedHashMap<>(type.getCodeBeeEffects());
		effective.putAll(dataDefinitions);
		type.setBeeEffects(ImmutableMap.copyOf(effective));
	}

	/**
	 * Merges the datapack-loaded taxa onto the code-registered taxonomy in the genetic manager. Must run before
	 * {@link #rebuildSpecies}, because a species' genus is resolved to a taxon as it is projected.
	 */
	public static void rebuildTaxa(Collection<TaxonDefinition> taxa) {
		((GeneticManager) IForestryApi.INSTANCE.getGeneticManager()).applyDatapackTaxa(taxa);
	}

	/**
	 * Projects each tree definition into a {@link TreeSpecies} (fail-soft: a bad/binding-less definition is logged and
	 * dropped by {@link TreeSpeciesProjector#project}) and swaps the resulting map into the live tree species type.
	 */
	@SuppressWarnings("unchecked")
	public static void rebuildTreeSpecies(Map<ResourceLocation, TreeSpeciesDefinition> defs) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ImmutableMap.Builder<ResourceLocation, ITreeSpecies> builder = ImmutableMap.builderWithExpectedSize(defs.size());
		for (Map.Entry<ResourceLocation, TreeSpeciesDefinition> entry : defs.entrySet()) {
			ResourceLocation id = entry.getKey();
			TreeSpecies species = TreeSpeciesProjector.project(type, id, entry.getValue());
			if (species != null) {
				builder.put(id, species);
			}
		}
		ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies = builder.build();
		((SpeciesType<ITreeSpecies, ?>) type).setSpecies(allSpecies);
		Forestry.LOGGER.info("Loaded {} tree species", allSpecies.size());
	}

	/**
	 * Projects each butterfly definition into a {@link ButterflySpecies} (fail-soft: a bad definition is logged and
	 * dropped by {@link ButterflySpeciesProjector#project}) and swaps the resulting map into the live butterfly
	 * species type.
	 */
	@SuppressWarnings("unchecked")
	public static void rebuildButterflySpecies(Map<ResourceLocation, ButterflySpeciesDefinition> defs) {
		IButterflySpeciesType type = SpeciesUtil.BUTTERFLY_TYPE.get();
		ImmutableMap.Builder<ResourceLocation, IButterflySpecies> builder = ImmutableMap.builderWithExpectedSize(defs.size());
		for (Map.Entry<ResourceLocation, ButterflySpeciesDefinition> entry : defs.entrySet()) {
			ResourceLocation id = entry.getKey();
			ButterflySpecies species = ButterflySpeciesProjector.project(type, id, entry.getValue());
			if (species != null) {
				builder.put(id, species);
			}
		}
		ImmutableMap<ResourceLocation, IButterflySpecies> allSpecies = builder.build();
		((SpeciesType<IButterflySpecies, ?>) type).setSpecies(allSpecies);
		Forestry.LOGGER.info("Loaded {} butterfly species", allSpecies.size());

		// Any already-loaded EntityButterfly caches its resolved individual/species (see Individual's species
		// field); refresh those now so they pick up the fresh instances just swapped in above, otherwise a butterfly
		// that mates after this reload would look up mutations by an identity the (identity-keyed) MutationManager
		// no longer recognizes. No-op with a null server (e.g. the initial WorldLoader.load, before any world/entity
		// exists).
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server != null) {
			for (ServerLevel level : server.getAllLevels()) {
				for (EntityButterfly entity : level.getEntities(EntityTypeTest.forClass(EntityButterfly.class), e -> true)) {
					entity.refreshSpeciesFromReload();
				}
			}
		}
	}

	public static void rebuildMutations(RecipeManager recipeManager) {
		MutationConditionTypes.registerBuiltins(); // idempotent safety net
		for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
			rebuildOne(type, recipeManager);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <S extends ISpecies<?>> void rebuildOne(ISpeciesType<S, ?> type, RecipeManager rm) {
		FeatureRecipeType<MutationRecipe> featureType = GeneticsRecipeTypes.forType(type.id());
		if (featureType == null) {
			return; // species type without a mutation recipe type (e.g. third-party)
		}
		// build id -> species lookup once
		Map<ResourceLocation, S> lookup = new HashMap<>();
		for (S species : type.getAllSpecies()) {
			lookup.put(species.id(), species);
		}
		ImmutableList.Builder<IMutation<S>> builder = ImmutableList.builder();
		for (RecipeHolder<MutationRecipe> holder : rm.getAllRecipesFor(featureType.type())) {
			Mutation<S> mutation = holder.value().toMutation(type, lookup::get);
			if (mutation != null) {
				builder.add(mutation);
			} else {
				Forestry.LOGGER.warn("Skipping mutation recipe {} (unknown or mismatched species)", holder.id());
			}
		}
		ImmutableList<IMutation<S>> mutations = builder.build();
		((SpeciesType<S, ?>) type).setMutations(new MutationManager<>(mutations));
		Forestry.LOGGER.debug("Loaded {} {} mutation recipes", mutations.size(), type.id());
	}

	private GeneticsReloadHandler() {
	}
}
