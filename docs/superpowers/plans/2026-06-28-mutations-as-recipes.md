# Mutations as Recipes — Implementation Plan (Stage 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make genetics mutations data-driven by modeling each as a custom Minecraft `RecipeType` loaded from datapack JSON (reload + client sync + JEI for free), replacing the code-registered `IMutationsRegistration` path.

**Architecture:** A `MutationRecipe` (implementing the existing no-op `IForestryRecipe`) is registered under three `RecipeType`s (`bee_/tree_/butterfly_mutation`) with one generic serializer class instantiated per species type. Conditions become a dispatch-codec registry. A reload listener rebuilds each species type's `MutationManager` from `RecipeManager.byType(...)` on server reload and client `RecipesUpdatedEvent`; mutations are the single responsibility of `SpeciesType.getMutations()` (the duplicate `GeneticManager.mutationsByType` is removed). Built-in mutations move to datagen.

**Tech Stack:** NeoForge 21.1.x / Minecraft 1.21.1, Java 21, Mojang DataFixerUpper codecs (`MapCodec`/`StreamCodec`/`RecordCodecBuilder`/`Codec.dispatch`), NeoForge `DeferredRegister`/`AddReloadListenerEvent`/`RecipesUpdatedEvent`, GameTest.

**Spec:** `docs/superpowers/specs/2026-06-28-mutations-as-recipes-design.md`

**Build/verify commands (this repo):**
- Compile main: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava`
- Compile tests: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileTestJava`
- GameTests (authoritative): `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
- Run datagen: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`
- Full build: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew build`

> **Verification note:** this project has **no JUnit tests** — all tests are GameTests run via `runGameTestServer`. "Write the failing test" steps below add GameTests; "run it to verify it fails" means it fails (or the build fails to compile) under `runGameTestServer`. Commit after each task.

---

## File structure (what changes)

**Conditions (dispatch-codec registry):**
- Create `src/main/java/forestry/api/genetics/IMutationConditionType.java` — a `(MapCodec, StreamCodec)` holder + dispatch.
- Modify `src/main/java/forestry/api/genetics/IMutationCondition.java` — add `IMutationConditionType<?> type()`.
- Create `src/main/java/forestry/core/genetics/mutations/MutationConditionTypes.java` — static registry + the dispatch `Codec`/`StreamCodec`; registers the 7 built-ins.
- Modify the 7 `src/main/java/forestry/core/genetics/mutations/MutationCondition*.java` — add a `MapCodec`, `StreamCodec`, and `type()`.
- Create `src/main/java/forestry/api/core/ClimateCodecs.java` (or add to an existing climate util) — `Codec`/`StreamCodec` for `TemperatureType` and `HumidityType`.

**Recipe + registration:**
- Create `src/main/java/forestry/core/genetics/mutations/MutationRecipe.java` — the recipe + its `Serializer` (per-species-type instance).
- Create `src/main/java/forestry/core/features/GeneticsRecipeTypes.java` — the three `FeatureRecipeType`s.

**Index + reload + lifecycle:**
- Create `src/main/java/forestry/core/genetics/mutations/MutationReloadHandler.java` — rebuilds per-type `MutationManager` from the recipe manager; server + client event subscribers.
- Modify `src/main/java/forestry/core/genetics/SpeciesType.java` — `getMutations()` returns empty before load; add `setMutations(...)`.
- Modify `src/main/java/forestry/api/genetics/ISpeciesType.java` — `handleSpeciesRegistration` returns species only; `onSpeciesRegistered` drops the mutations param.
- Modify `src/main/java/forestry/apiimpl/plugin/SpeciesRegistration.java` — `buildAll` returns species only (drop mutation building).
- Modify `src/main/java/forestry/apiimpl/plugin/PluginManager.java` — adapt the registration loop; drop `geneticManager.setMutations(...)`.
- Modify `src/main/java/forestry/apiimpl/GeneticManager.java` + `src/main/java/forestry/api/genetics/IGeneticManager.java` — remove `mutationsByType`; `getMutations(type)` delegates to `type.getMutations()`.
- Modify the three species type impls (`BeeSpeciesType`/`TreeSpeciesType`/`ButterflySpeciesType`) — adapt `handleSpeciesRegistration`/`onSpeciesRegistered` overrides.

**Remove the runtime mutation API:**
- Delete `src/main/java/forestry/api/plugin/IMutationsRegistration.java`, `IMutationBuilder.java`, `src/main/java/forestry/apiimpl/plugin/MutationsRegistration.java`.
- Modify `src/main/java/forestry/api/plugin/ISpeciesBuilder.java` + `src/main/java/forestry/apiimpl/plugin/SpeciesBuilder.java` — remove `addMutations`/`buildMutations`.

**Datagen:**
- Create `src/main/java/forestry/core/data/builder/MutationRecipeBuilder.java`.
- Create `src/main/java/forestry/core/data/MutationProvider.java` (or a method set inside `ForestryRecipeProvider`).
- Modify `src/main/java/forestry/core/data/Data.java` — register the provider.
- Port built-in mutations out of `DefaultBeeSpecies`/`DefaultTreeSpecies`/`DefaultButterflySpecies` `.addMutations(...)`.

**JEI:**
- Modify `src/main/java/forestry/apiculture/compat/MutationsRecipeCategory.java` + delete/replace `MutationRecipe` POJO + modify `ApicultureJeiPlugin.java`.

**Tests:**
- Create `src/test/java/forestry/gametest/MutationRecipeTest.java`.

---

## Task 1: `TemperatureType` / `HumidityType` codecs

**Files:**
- Create: `src/main/java/forestry/api/core/ClimateCodecs.java`
- Reference: Stage 1 used `Codec.STRING.xmap(ToleranceType::valueOf, Enum::name)` (`BeeChromosomes.TOLERANCE_CODEC`).

- [ ] **Step 1:** Create `ClimateCodecs` with lowercase string codecs + stream codecs for both enums:
```java
package forestry.api.core;

import java.util.Locale;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class ClimateCodecs {
	public static final Codec<TemperatureType> TEMPERATURE = Codec.STRING.xmap(
		s -> TemperatureType.valueOf(s.toUpperCase(Locale.ROOT)),
		t -> t.name().toLowerCase(Locale.ROOT));
	public static final Codec<HumidityType> HUMIDITY = Codec.STRING.xmap(
		s -> HumidityType.valueOf(s.toUpperCase(Locale.ROOT)),
		h -> h.name().toLowerCase(Locale.ROOT));
	public static final StreamCodec<io.netty.buffer.ByteBuf, TemperatureType> TEMPERATURE_STREAM =
		ByteBufCodecs.idMapper(TemperatureType.VALUES::get, TemperatureType::ordinal);
	public static final StreamCodec<io.netty.buffer.ByteBuf, HumidityType> HUMIDITY_STREAM =
		ByteBufCodecs.idMapper(HumidityType.VALUES::get, HumidityType::ordinal);

	private ClimateCodecs() {}
}
```
Note: `TemperatureType.VALUES`/`HumidityType.VALUES` are `List<...>` (`List.of(values())`), so use `VALUES.get(i)` (NOT `VALUES[i]`). Confirm the field exists; otherwise use `values()[i]`.

- [ ] **Step 2:** Compile: `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 3:** Commit: `git commit -am "feat(genetics): codecs for TemperatureType/HumidityType"`.

---

## Task 2: Condition type holder + dispatch (`IMutationConditionType`, `IMutationCondition.type()`)

**Files:**
- Create: `src/main/java/forestry/api/genetics/IMutationConditionType.java`
- Modify: `src/main/java/forestry/api/genetics/IMutationCondition.java` (add `type()`)
- Create: `src/main/java/forestry/core/genetics/mutations/MutationConditionTypes.java`

- [ ] **Step 1:** Create the type holder:
```java
package forestry.api.genetics;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record IMutationConditionType<T extends IMutationCondition>(
	MapCodec<T> codec,
	StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

- [ ] **Step 2:** Add to `IMutationCondition`:
```java
IMutationConditionType<?> type();
```

- [ ] **Step 3:** Create the registry + dispatch codecs. The registry is a static map populated at genetics-registration time (before datapacks load), so the dispatch lookup is ready when recipes parse:
```java
package forestry.core.genetics.mutations;

import java.util.LinkedHashMap;
import java.util.Map;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.IMutationConditionType;

public final class MutationConditionTypes {
	private static final Map<ResourceLocation, IMutationConditionType<?>> BY_ID = new LinkedHashMap<>();
	private static final Map<IMutationConditionType<?>, ResourceLocation> ID_OF = new LinkedHashMap<>();

	public static synchronized void register(ResourceLocation id, IMutationConditionType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate mutation condition type: " + id);
		}
		ID_OF.put(type, id);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final Codec<IMutationCondition> CODEC = ResourceLocation.CODEC
		.dispatch("type", c -> ID_OF.get(c.type()), id -> ((IMutationConditionType) BY_ID.get(id)).codec());

	public static final StreamCodec<RegistryFriendlyByteBuf, IMutationCondition> STREAM_CODEC = StreamCodec.of(
		(buf, condition) -> {
			ResourceLocation id = ID_OF.get(condition.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			//noinspection unchecked,rawtypes
			((StreamCodec) condition.type().streamCodec()).encode(buf, condition);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return BY_ID.get(id).streamCodec().decode(buf);
		});

	public static final Codec<java.util.List<IMutationCondition>> LIST_CODEC = CODEC.listOf();
	public static final StreamCodec<RegistryFriendlyByteBuf, java.util.List<IMutationCondition>> LIST_STREAM_CODEC =
		STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list());

	private MutationConditionTypes() {}
}
```
Add a `public static void registerBuiltins()` (idempotent, via the `putIfAbsent` guard) that registers all 7 built-in types. **CRITICAL TIMING:** this must run at **setup time, before any datapack load/parse** — recipe JSON `conditions` arrays dispatch on `type` at `RecipeManager` parse time (which happens *before* the reload handler runs, and before client `RecipesUpdatedEvent` on sync-decode). If the types aren't registered by then, `LIST_CODEC` fails and **every conditioned mutation recipe is silently dropped** (the built-ins use ~25 temperature / 23 humidity / 13 biome / 5 date / 6 custom conditions). The concrete registration call site is **`PluginManager.registerGenetics`** (Task 6 Step 6) — which runs at `postItemRegistry`, before worlds/datapacks. Also call it (harmlessly, idempotent) from `MutationReloadHandler.rebuild` as a safety net.

- [ ] **Step 4:** Compile: `./gradlew compileJava`. Expected: FAIL — the 7 condition classes don't implement `type()` yet. That's the next task.
- [ ] **Step 5:** Commit after Task 3 compiles (these two tasks land together).

---

## Task 3: Give the 7 conditions codecs + types

**Files (modify each):** `src/main/java/forestry/core/genetics/mutations/MutationCondition{Temperature,Humidity,Biome,Daytime,TimeLimited,RequiresResource,Cave}.java`

For **each** condition, add (a) a `public static final MapCodec<X>` over its fields, (b) a `public static final StreamCodec<RegistryFriendlyByteBuf, X>`, (c) a `public static final IMutationConditionType<X> TYPE = new IMutationConditionType<>(CODEC, STREAM_CODEC);`, and (d) `@Override public IMutationConditionType<?> type() { return TYPE; }`. Keep the existing constructors/fields (codecs map to them). Use these field mappings:

- [ ] **Temperature** (`min`,`max` via `ClimateCodecs.TEMPERATURE`):
```java
public static final MapCodec<MutationConditionTemperature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
	ClimateCodecs.TEMPERATURE.fieldOf("min").forGetter(c -> c.minTemperature),
	ClimateCodecs.TEMPERATURE.fieldOf("max").forGetter(c -> c.maxTemperature)
).apply(i, MutationConditionTemperature::new));
public static final StreamCodec<RegistryFriendlyByteBuf, MutationConditionTemperature> STREAM_CODEC = StreamCodec.composite(
	ClimateCodecs.TEMPERATURE_STREAM, c -> c.minTemperature,
	ClimateCodecs.TEMPERATURE_STREAM, c -> c.maxTemperature,
	MutationConditionTemperature::new);
```
(make the two fields package-visible or add getters).
- [ ] **Humidity** — same shape with `ClimateCodecs.HUMIDITY` / `HUMIDITY_STREAM`, fields `min`/`max`.
- [ ] **Biome** — single field `biome` via `TagKey.codec(Registries.BIOME)` and `TagKey.streamCodec(Registries.BIOME)`; constructor `MutationConditionBiome(TagKey<Biome>)`.
- [ ] **Daytime** — single field `day` (boolean) via `Codec.BOOL` / `ByteBufCodecs.BOOL`; constructor `(boolean)`.
- [ ] **TimeLimited** — four int fields `start_month`,`start_day`,`end_month`,`end_day` via `Codec.INT` / `ByteBufCodecs.VAR_INT`; constructor `(int,int,int,int)` already matches `restrictDateRange` ordering (startMonth, startDay, endMonth, endDay). Add getters for the four ints (derive from `start`/`end` `DayMonth`).
- [ ] **RequiresResource** — field `blocks` as `List<BlockState>` via `BlockState.CODEC.listOf()` / `ByteBufCodecs` list of `ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC)`; add a `List<BlockState>` constructor alongside the existing varargs one (the varargs one delegates).
- [ ] **Cave** — no fields: `MapCodec.unit(new MutationConditionCave())` and `StreamCodec.unit(...)`.

- [ ] **Step 8 (register builtins):** Add a static `registerBuiltins()` to `MutationConditionTypes` that calls `register(forestry("temperature"), MutationConditionTemperature.TYPE)` etc. for all 7 (ids: `temperature`,`humidity`,`biome`,`daytime`,`time_range`,`requires_resource`,`cave`). It must be idempotent (the `putIfAbsent` guard) and is invoked at setup from `PluginManager.registerGenetics` (Task 6 Step 6), with an idempotent safety-net call in `MutationReloadHandler.rebuild` (Task 7).
- [ ] **Step 9:** Compile: `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 10:** Commit: `git commit -am "feat(genetics): mutation condition codecs + dispatch registry"`.

---

## Task 4: `GeneticsRecipeTypes` — register the three RecipeTypes

**Files:**
- Create: `src/main/java/forestry/core/features/GeneticsRecipeTypes.java`
- Reference: `FactoryRecipeTypes.java` (extraction §A.4) + `FeatureRecipeType` (§A.3).

- [ ] **Step 1:** Decide the module id. Use the core module registry (mutations span all three genetics modules): `ModFeatureRegistry.get(ForestryModuleIds.CORE)` (verify a CORE module id exists; otherwise use the apiculture/arboriculture/lepidopterology registries respectively for each type). Create:
```java
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
		if (speciesTypeId.equals(ForestrySpeciesTypes.BEE)) return BEE_MUTATION;
		if (speciesTypeId.equals(ForestrySpeciesTypes.TREE)) return TREE_MUTATION;
		if (speciesTypeId.equals(ForestrySpeciesTypes.BUTTERFLY)) return BUTTERFLY_MUTATION;
		return null; // unknown/third-party species type — caller skips with a warning
	}
}
```
`forType` returns `null` for species types without a mutation recipe type (e.g. a third-party species type), so the rebuild loop skips them with a warning instead of crashing.

(`MutationRecipe.Serializer` is created in Task 5; this task will not compile until then — land Tasks 4+5 together.)

---

## Task 5: `MutationRecipe` + `Serializer` (lazy karyotype-bound codec)

**Files:**
- Create: `src/main/java/forestry/core/genetics/mutations/MutationRecipe.java`
- Reference: `IForestryRecipe` (§A.1), `HygroregulatorRecipe` serializer (§A.2), `Mutation` ctor (§C.9), Stage 1 `Karyotype.getGenomeCodec` dispatched-map for `resultAlleles`.

- [ ] **Step 1:** Write the recipe class. Fields: `ResourceLocation id, firstParentId, secondParentId, resultId; float chance; List<IMutationCondition> conditions; Map<ResourceLocation, Allele<?>> resultAlleles`. Implement `IForestryRecipe`: `getId()`, `getType()`/`getSerializer()` return the bound `FeatureRecipeType` (the serializer instance injects which one). Store the owning `FeatureRecipeType<MutationRecipe>` reference (or the species type id) so `getType()` returns the right one.

- [ ] **Step 2:** `toMutation`:
```java
public <S extends ISpecies<?>> Mutation<S> toMutation(ISpeciesType<S, ?> type, java.util.function.Function<ResourceLocation, S> lookup) {
	S first = lookup.apply(this.firstParentId);
	S second = lookup.apply(this.secondParentId);
	S result = lookup.apply(this.resultId);
	// resultAlleles keyed by chromosome id -> resolve via karyotype
	Map<IChromosome<?>, Allele<?>> resolved = new IdentityHashMap<>();
	IKaryotype karyotype = type.getKaryotype();
	this.resultAlleles.forEach((chromId, allele) -> {
		IChromosome<?> chrom = karyotype.getChromosome(chromId); // add helper if missing; else iterate getChromosomes()
		if (chrom != null) resolved.put(chrom, allele);
	});
	return new Mutation<>(type, first, second, result, resolved, this.chance, this.conditions);
}
```
Return `null` (and the caller logs+skips) if any species id is unknown / wrong type (`lookup` returns null).

- [ ] **Step 3:** Serializer (one class, per-instance bound to a species type id; karyotype + resultAlleles codec resolved lazily on first use, since species types exist by datapack-load time):
```java
public static class Serializer implements RecipeSerializer<MutationRecipe> {
	private final ResourceLocation speciesTypeId;
	private MapCodec<MutationRecipe> codec;        // lazy
	private StreamCodec<RegistryFriendlyByteBuf, MutationRecipe> streamCodec; // lazy

	public Serializer(ResourceLocation speciesTypeId) { this.speciesTypeId = speciesTypeId; }

	@Override public MapCodec<MutationRecipe> codec() { if (codec == null) codec = buildCodec(); return codec; }
	@Override public StreamCodec<RegistryFriendlyByteBuf, MutationRecipe> streamCodec() { if (streamCodec == null) streamCodec = buildStreamCodec(); return streamCodec; }
	// buildCodec(): RecordCodecBuilder.mapCodec over id, first/second/result (ResourceLocation.CODEC),
	//   chance (Codec.FLOAT), conditions (MutationConditionTypes.LIST_CODEC),
	//   resultAlleles (karyotype-aware dispatched map, optionalFieldOf default empty).
	// buildStreamCodec(): mirror with STREAM codecs (MutationConditionTypes.LIST_STREAM_CODEC).
}
```
The `resultAlleles` map codec follows Stage 1 `Karyotype.buildGenomeCodec`'s `Codec.dispatchedMap(keyCodec, chromosome -> valueCodec)` pattern — but the value is a single `Allele<V>` (the inner `alleleCodec` from `Karyotype.pairCodecFor`), not an `AllelePair<V>`. Key codec = chromosome-by-id. Resolve the karyotype via `IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(this.speciesTypeId).getKaryotype()` inside `buildCodec()`. Since no built-in uses it, `optionalFieldOf("result_alleles", Map.of())`.

- [ ] **Step 4:** Compile (Tasks 4+5 together): `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -am "feat(genetics): MutationRecipe + per-type serializers + recipe types"`.

---

## Task 6: Collapse dual mutation storage + decouple lifecycle

**Files:** `SpeciesType.java`, `ISpeciesType.java`, `IGeneticManager.java`, `GeneticManager.java`, `SpeciesRegistration.java`, `PluginManager.java`, `BeeSpeciesType.java`/`TreeSpeciesType.java`/`ButterflySpeciesType.java`, `SpeciesBuilder.java`, `ISpeciesBuilder.java`.

- [ ] **Step 1:** `SpeciesType`: initialize `mutations` to an **empty** manager and add a setter; `getMutations()` never throws:
```java
private IMutationManager<S> mutations = new MutationManager<>(ImmutableList.of());
@ApiStatus.Internal public void setMutations(IMutationManager<S> mutations) { this.mutations = mutations; }
@Override public IMutationManager<S> getMutations() { return this.mutations; }
```
- [ ] **Step 2:** `ISpeciesType`: change `handleSpeciesRegistration` to return `ImmutableMap<ResourceLocation, S>` (drop the `Pair`/mutations); change `onSpeciesRegistered(ImmutableMap<ResourceLocation, S> allSpecies)` (drop the mutations param). Update the three impl overrides + `SpeciesType.onSpeciesRegistered`.
- [ ] **Step 3:** `SpeciesRegistration.buildAll`: return `ImmutableMap<ResourceLocation, S>` only — delete the "build mutations once species are available" block (extraction §D.18) and the final `Pair.of(...)`.
- [ ] **Step 4:** `ISpeciesBuilder`/`SpeciesBuilder`: remove `addMutations` + `buildMutations` (and the `MutationsRegistration mutations` field / its uses in `SpeciesRegistration.register`).
- [ ] **Step 5:** `IGeneticManager`/`GeneticManager`: remove `mutationsByType`, its setter, and the constructor wiring; collapse `getMutations(ISpeciesType)` to `return (IMutationManager<S>) speciesType.getMutations();` (keep the default `getMutations(ResourceLocation)`).
- [ ] **Step 6:** `PluginManager.registerGenetics`: the species loop calls `speciesType.handleSpeciesRegistration(...)` → `onSpeciesRegistered(species)`; drop `allMutations`, the `getMutations()` assertion, and `geneticManager.setMutations(...)` (extraction §D.16). **Also add `MutationConditionTypes.registerBuiltins();` here** (e.g. right after species types are built) so condition `type` ids are registered before any datapack parse — see Task 2/3.
- [ ] **Step 7:** Delete `IMutationsRegistration.java`, `IMutationBuilder.java`, `MutationsRegistration.java` via `git rm`.
- [ ] **Step 8:** Compile: `./gradlew compileJava`. Expected: FAIL only in `DefaultBeeSpecies`/`DefaultTreeSpecies`/`DefaultButterflySpecies` (the `.addMutations(...)` calls). Leave those temporarily compiling by deleting the `.addMutations(...)` blocks now (they move to datagen in Task 8) — or comment-delete and re-add in Task 8. Re-compile to PASS.
- [ ] **Step 9:** Commit: `git commit -am "refactor(genetics): single mutation index on species type; drop runtime mutation API"`.

> After this task `getMutations()` returns empty (no mutations load yet) — breeding produces no offspring until Task 7 wires the reload index. That's expected mid-plan; Task 7 + Task 8 restore behavior.

---

## Task 7: Reload handler + event wiring + builtin condition registration

**Files:**
- Create: `src/main/java/forestry/core/genetics/mutations/MutationReloadHandler.java`
- Modify: an event-subscriber class (find where `@SubscribeEvent`/`@EventBusSubscriber` server+client genetics events live; e.g. a `forestry.core` events class) to subscribe `AddReloadListenerEvent` (server) and `RecipesUpdatedEvent` (client).

- [ ] **Step 1:** `MutationReloadHandler.rebuild(RecipeManager recipeManager)`:
```java
public static void rebuild(RecipeManager recipeManager) {
	MutationConditionTypes.registerBuiltins(); // idempotent
	for (ISpeciesType<?, ?> type : IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()) {
		rebuildOne(type, recipeManager);
	}
}
@SuppressWarnings({"unchecked","rawtypes"})
private static <S extends ISpecies<?>> void rebuildOne(ISpeciesType<S,?> type, RecipeManager rm) {
	FeatureRecipeType<MutationRecipe> featureType = GeneticsRecipeTypes.forType(type.id());
	if (featureType == null) { return; } // third-party species type without a mutation recipe type
	ImmutableMap<ResourceLocation, S> lookup = /* type.getAllSpecies() keyed by id */;
	ImmutableList.Builder<IMutation<S>> builder = ImmutableList.builder();
	for (RecipeHolder<MutationRecipe> holder : rm.getAllRecipesFor(featureType.type())) {
		Mutation<S> m = holder.value().toMutation(type, lookup::get);
		if (m != null) builder.add(m); else Forestry.LOGGER.warn("Skipping mutation recipe {} (unknown/mismatched species)", holder.id());
	}
	((SpeciesType<S,?>) type).setMutations(new MutationManager<>(builder.build()));
}
```
Build `lookup` from `type.getAllSpeciesIds()`/`getSpecies(id)` (a helper on the species type already exposes the map, or build it once).

- [ ] **Step 2 (server):** subscribe `AddReloadListenerEvent`; register a `PreparableReloadListener` that, in `apply`, calls `MutationReloadHandler.rebuild(event...recipeManager)`. The listener must run **after** the vanilla `RecipeManager`. Pin the mechanism: NeoForge's `AddReloadListenerEvent#getServerResources().getRecipeManager()` returns the manager that will be (re)populated; register the listener and read the recipe manager in `apply` after recipes are applied. If ordering is uncertain, alternatively subscribe `net.neoforged.neoforge.event.OnDatapackSyncEvent` / `ServerStartedEvent` + a tick-deferred rebuild reading `server.getRecipeManager()`. **Implementer: confirm ordering by logging the mutation count after rebuild.**
- [ ] **Step 3 (client):** subscribe `net.neoforged.neoforge.client.event.RecipesUpdatedEvent`; call `MutationReloadHandler.rebuild(event.getRecipeManager())`.
- [ ] **Step 4:** Compile: `./gradlew compileJava`. Expected: PASS.
- [ ] **Step 5:** Commit: `git commit -am "feat(genetics): rebuild mutation index from recipes on (re)load + sync"`.

---

## Task 8: Datagen — builder, provider, port built-ins

**Files:**
- Create: `src/main/java/forestry/core/data/builder/MutationRecipeBuilder.java`
- Create: `src/main/java/forestry/core/data/MutationProvider.java` (+ register in `Data.java`)
- Modify: `DefaultBeeSpecies`/`DefaultTreeSpecies`/`DefaultButterflySpecies` (remove the old `.addMutations` blocks if not already removed in Task 6).
- Reference: `HygroregulatorRecipeBuilder` (§B), `MutationsRegistration.MutationBuilder` (§C.11) for the convenience-method surface.

- [ ] **Step 1:** `MutationRecipeBuilder` mirrors the old `IMutationBuilder` surface (`temperature`, `humidity`, `biome`, `day`/`night`, `timeRange`, `requiresResource`, `condition`, `resultAllele`) accumulating `firstParent`/`secondParent`/`result`/`chance` + a `List<IMutationCondition>` + result-allele map, with `build(RecipeOutput, ResourceLocation)` emitting `new MutationRecipe(...)` for the right species type:
```java
public void build(RecipeOutput output, ResourceLocation id) {
	output.accept(id, new MutationRecipe(id, speciesTypeId, first, second, result, chance, conditions, resultAlleles), null);
}
```
- [ ] **Step 2:** `MutationProvider` (a `RecipeProvider` or standalone provider invoked from `Data.java`'s `createRecipes`/`addProvider`). Port every built-in mutation from the three `Default*Species.addMutations` blocks into `add(speciesType, first, second, result, chance)....build(output, id)` calls. Recipe ids: `forestry:<bee|tree|butterfly>_mutation/<result_path>[_n]`. File output goes to `data/forestry/recipe/...` automatically via `RecipeOutput`.
- [ ] **Step 3:** Register the provider in `Data.java` (extraction §B.5) alongside the other recipe datagen.
- [ ] **Step 4:** Run datagen: `./gradlew runData`. Expected: PASS; inspect `src/generated/resources/data/forestry/recipe/bee_mutation/*.json` for shape (matches the spec preview).
- [ ] **Step 5:** Compile + commit: `git commit -am "feat(datagen): generate built-in mutations as recipes"`.

---

## Task 9: JEI — feed the apiculture mutation category from the recipe type

**Files:** `apiculture/compat/MutationsRecipeCategory.java`, `apiculture/compat/MutationRecipe.java` (POJO), `apiculture/compat/ApicultureJeiPlugin.java`.

> Scope note (corrected): the **single** `ApicultureJeiPlugin` already registers a `MutationsRecipeCategory` for **all three** species types — `registerCategories` loops `getGeneticManager().getSpeciesTypes()` and `registerRecipes` feeds each from `category.speciesType.getMutations().getAllMutations()` (extraction §E.21). This reconciles the spec's "three categories" (they all live in the one apiculture plugin). **Preserve that `getSpeciesTypes()` loop unchanged** — do NOT add a bee-only filter (that would regress the tree/butterfly mutation pages). Because `getMutations()` now reflects the recipe-backed index, the feed keeps working with no logic change; the only required edit is renaming the colliding display POJO.

- [ ] **Step 1:** The JEI POJO `apiculture/compat/MutationRecipe` collides in simple-name with the new recipe. Rename the POJO to `MutationDisplay` (or fold its display-stack logic into the category) to remove the collision. Update `MutationsRecipeCategory`/`ApicultureJeiPlugin` references.
- [ ] **Step 2:** `ApicultureJeiPlugin.registerRecipes` already feeds from `category.speciesType.getMutations().getAllMutations()` (extraction §E.21) — since `getMutations()` now reflects the loaded recipes, this keeps working. Verify it still compiles after the rename; no logic change needed.
- [ ] **Step 3:** Compile + commit: `git commit -am "refactor(jei): mutation category reads recipe-backed mutation index; drop name collision"`.

---

## Task 10: GameTests

**Files:** Create `src/test/java/forestry/gametest/MutationRecipeTest.java` (pattern: `AlleleFoundationTest`).

- [ ] **Step 1:** Write tests (each `@GameTest(template="empty")`):
  1. `mutationIndexPopulated` — after load, `SpeciesUtil.BEE_TYPE.get().getMutations().getAllMutations()` is non-empty and contains a known built-in (e.g. result Common from Forest×Meadows: assert `getCombinations(forest, meadows)` yields a mutation whose result is Common).
  2. `conditionCodecRoundTrip` — build one of each of the 7 conditions, encode via `MutationConditionTypes.CODEC` → JSON → decode, assert `getDescription()` equal; and via `STREAM_CODEC` over a `RegistryFriendlyByteBuf` (use `helper.getLevel().registryAccess()`).
  3. `recipeCodecRoundTrip` — build a `MutationRecipe` (with a couple conditions), round-trip via the bee serializer's `codec()` (NBT) and `streamCodec()`; assert parents/result/chance/conditions equal.
  4. `breedingProducesMutation` — call `SpeciesUtil.mutateSpecies(...)` for a known unconditioned parent pair and assert a non-empty result is reachable (chance > 0); and that a temperature-gated mutation returns 0 chance under a wrong climate (call `Mutation.getChance` with a stub `IClimateProvider`).
  5. `getMutationsNeverThrows` — `getMutations()` returns non-null even with no recipes (empty manager).
- [ ] **Step 2:** Run: `./gradlew runGameTestServer`. Expected: all pass (incl. the Stage 1 golden master + AlleleFoundationTest).
- [ ] **Step 3:** Commit: `git commit -am "test(genetics): mutation recipe gametests"`.

---

## Task 11: Full verification

- [ ] **Step 1:** `./gradlew compileJava compileTestJava` — green.
- [ ] **Step 2:** `./gradlew runGameTestServer` — all tests pass.
- [ ] **Step 3:** `./gradlew runData` — datagen clean; confirm generated mutation recipes are committed (or generated into `src/generated`).
- [ ] **Step 4:** `./gradlew build` — green.
- [ ] **Step 5:** Manual smoke (optional, if a dev client is run): breed a known pair → offspring mutates; `/reload` → mutations still work; JEI bee mutations page shows entries.
- [ ] **Step 6:** Adversarial review (workflow) of the changed runtime files (recipe codec, reload handler, lifecycle edits, JEI) for behavior regressions, then fix findings.
- [ ] **Step 7:** Update memory `data-driven-genetics-overhaul.md` with Stage 2 completion + key decisions; commit.

---

## Risks & notes for the implementer

- **Serializer/karyotype timing:** serializers are constructed at `RegisterEvent` (before species types exist), so the codec **must** resolve the karyotype lazily (first `codec()` call), not in the constructor. Species types exist by datapack-load time.
- **Reload ordering (Task 7 Step 2):** the single real risk. The mutation index rebuild must run after the vanilla `RecipeManager` has applied. Confirm via a logged mutation count; if `AddReloadListenerEvent` ordering is unreliable, fall back to `OnDatapackSyncEvent`/`ServerStartedEvent` reading `server.getRecipeManager()`.
- **Identity vs equality:** `MutationManager`/`Mutation` compare species with `==` (species are code-registered singletons — still true in Stage 2), so the index built from recipes works unchanged.
- **`resultAlleles`:** unused by built-ins; keep optional+empty. Don't over-build its codec — but it must round-trip if present.
- **Research progress:** keyed by parent+result species ids (`BreedingTracker.getMutationString`) — unchanged, survives the migration.
- **`getChromosome(id)` helper:** if `IKaryotype` lacks a by-id chromosome lookup, add one (or iterate `getChromosomes()` matching `id()`), needed by `MutationRecipe.toMutation` for `resultAlleles`.
