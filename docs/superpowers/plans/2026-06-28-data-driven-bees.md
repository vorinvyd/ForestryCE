# Data-Driven Bee Species Implementation Plan (Stage 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bee species loadable from datapack JSON (`data/<ns>/bee_species/*.json`), live-reloadable on `/reload`, and client-synced on login, with the ~60 built-in bees shipped as generated JSON — no behavior change to default genomes.

**Architecture:** A new pure-data `BeeSpeciesDefinition` (the JSON shape + sync payload, with a lazy karyotype-bound codec/stream-codec lifted from `MutationRecipe`) is loaded by a `SimpleJsonResourceReloadListener` (`BeeSpeciesManager`) on the server and a clientbound sync packet on the client; a `GeneticsReloadHandler` projects definitions into runtime `BeeSpecies` (via a `DefinitionBeeSpeciesBuilder` adapter + the existing genome-composition logic) and swaps them into `BeeSpeciesType` (a new `volatile setSpecies`, mirroring `setMutations`), then rebuilds mutations. The code `registerSpecies` path is demoted to datagen-only. Client item models switch from a startup per-species bake to a lazy id-keyed bake.

**Tech Stack:** NeoForge 1.21.1, Java 21, Mojang DataFixerUpper codecs, Gradle (JBR 21). All gradle commands MUST be prefixed with `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9`.

**Spec:** `docs/superpowers/specs/2026-06-28-data-driven-bees-design.md`

---

## Conventions for every task

- **Compile:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
- **Gametests:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` (the golden master `GenomeBaselineTest` and Stage-2 `MutationRecipeTest` must stay green every task).
- **Datagen:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`
- New gametests go in `src/test/java/forestry/gametest/` and register through the same harness as `MutationRecipeTest`/`AlleleFoundationTest` (find how those are registered and follow it exactly).
- Commit after each task with a `wip:` or `feat(genetics):` message ending with the `Co-Authored-By` trailer.
- DRY/YAGNI/TDD/frequent commits. Do **not** restructure unrelated code.

## Phasing (why this order)

- **Phase A (Tasks 1–6):** purely additive machinery. The existing runtime species path stays active, so the game still works at every step.
- **Phase B (Task 7):** datagen emits the JSON as a *parallel artifact* and a test proves code-built == JSON-projected. The runtime still uses the old path.
- **Phase C (Tasks 8–9):** the cutover. Task 8 wires the loader/sync (both paths active, consistent). Task 9 demotes the old path (runtime = JSON only).
- **Phase D (Task 10):** final verification + review.

---

## Task 1: Jubilance registry

Make `IBeeJubilance` addressable by id so a definition can name it. Mirror the existing `registerBeeEffect` triple exactly.

**Files:**
- Create: `src/main/java/forestry/api/apiculture/ForestryBeeJubilances.java`
- Modify: `src/main/java/forestry/api/plugin/IApicultureRegistration.java:85-95` (add `registerBeeJubilance`)
- Modify: `src/main/java/forestry/apiimpl/plugin/ApicultureRegistration.java:29-82` (add jubilance `Registrar` + register + `getJubilances`)
- Modify: `src/main/java/forestry/api/apiculture/genetics/IBeeSpeciesType.java` (add `getJubilance` + `getJubilanceSafe`)
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesType.java:36-68` + `:156-172` (field + getter + capture in `handleSpeciesRegistration`)
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java` (`registerApiculture`: register the two builtins)
- Test: `src/test/java/forestry/gametest/BeeJubilanceTest.java`

- [ ] **Step 1: Constants class**

```java
package forestry.api.apiculture;

import net.minecraft.resources.ResourceLocation;
import forestry.api.ForestryConstants;

public class ForestryBeeJubilances {
	public static final ResourceLocation DEFAULT = ForestryConstants.forestry("default");
	public static final ResourceLocation HERMIT = ForestryConstants.forestry("hermit");
}
```

- [ ] **Step 2: Add registration method** to `IApicultureRegistration` next to the other `register...` methods:

```java
void registerBeeJubilance(ResourceLocation id, IBeeJubilance jubilance);
```

- [ ] **Step 3: Implement in `ApicultureRegistration`** mirroring `beeEffects` (field, create, build-getter):

```java
private final Registrar<ResourceLocation, IBeeJubilance, IBeeJubilance> jubilances = new Registrar<>(IBeeJubilance.class);

@Override
public void registerBeeJubilance(ResourceLocation id, IBeeJubilance jubilance) {
	this.jubilances.create(id, jubilance);
}

public ImmutableMap<ResourceLocation, IBeeJubilance> getJubilances() {
	return this.jubilances.build();
}
```

- [ ] **Step 4: Add getter to `IBeeSpeciesType`** (mirror `getFlowerType`/`getFlowerTypeSafe`):

```java
IBeeJubilance getJubilance(ResourceLocation id);

@Nullable
IBeeJubilance getJubilanceSafe(ResourceLocation id);
```

- [ ] **Step 5: Implement in `BeeSpeciesType`** — add `@Nullable private ImmutableMap<ResourceLocation, IBeeJubilance> jubilances;` field, the two getters (`requireValue`/`valueSafe`), and capture in `handleSpeciesRegistration`:

```java
this.jubilances = registration.getJubilances();
```

- [ ] **Step 6: Register builtins** in `DefaultForestryPlugin.registerApiculture` (near the `registerBeeEffect` calls):

```java
apiculture.registerBeeJubilance(ForestryBeeJubilances.DEFAULT, DefaultBeeJubilance.INSTANCE);
apiculture.registerBeeJubilance(ForestryBeeJubilances.HERMIT, HermitBeeJubilance.INSTANCE);
```

(imports: `forestry.apiculture.genetics.DefaultBeeJubilance`, `forestry.apiculture.genetics.HermitBeeJubilance`, `forestry.api.apiculture.ForestryBeeJubilances`.)

- [ ] **Step 7: Test** — `BeeJubilanceTest` gametest: assert `SpeciesUtil.BEE_TYPE.get().getJubilance(ForestryBeeJubilances.DEFAULT) == DefaultBeeJubilance.INSTANCE` and same for HERMIT; assert `getJubilanceSafe(forestry("nonexistent")) == null`.

- [ ] **Step 8:** Compile, run gametests (all green), commit.

---

## Task 2: `BeeSpeciesDefinition` record + lazy codec + stream codec

The JSON shape and sync payload. Codecs are built lazily (karyotype not available at registration time) — lift the pattern verbatim from `MutationRecipe` (`src/main/java/forestry/core/genetics/mutations/MutationRecipe.java:141-274`).

**Files:**
- Create: `src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java`
- Test: `src/test/java/forestry/gametest/BeeSpeciesDefinitionTest.java`

**Record fields** (see spec §1 for the field→builder mapping). Use the exact defaults from `BeeSpeciesBuilder`/`SpeciesBuilder` (body `0xffdc16`, stripes `0`, outline `-1`, escritoireColor `-1`, complexity `0`, authority `"Sengir"`, temperature/humidity `NORMAL`, dominant/glint/secret `false`, jubilance `forestry:default`):

```java
public record BeeSpeciesDefinition(
	String genus,
	String species,
	boolean dominant,
	boolean glint,
	boolean secret,
	int complexity,
	String authority,
	int escritoireColor,
	TemperatureType temperature,
	HumidityType humidity,
	int body,
	int stripes,
	int outline,
	List<Product> products,
	List<Product> specialties,
	ResourceLocation jubilance,
	Map<ResourceLocation, Allele<?>> genome // sparse overrides, chromosomeId -> allele
) {
	public static final ResourceLocation DEFAULT_JUBILANCE = ForestryBeeJubilances.DEFAULT;
	// ... codec + stream codec below
}
```

- [ ] **Step 1: Genome map codec** — lift `MutationRecipe.resultAllelesCodec(IKaryotype)` (`:220-240`) and `resultAllelesStreamCodec(IKaryotype)` (`:242-274`, including the `encodeAllele` helper) verbatim into static helpers here (or extract them into a shared util `forestry/core/genetics/GenomeCodecs.java` and have BOTH `MutationRecipe` and this class use it — preferred for DRY; if extracting, do it as its own sub-step and keep `MutationRecipe` green). These produce `Codec<Map<ResourceLocation, Allele<?>>>` / `StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>>`.

- [ ] **Step 2: Lazy MapCodec/Codec** built against the bee karyotype, mirroring `MutationRecipe.buildCodec` (`:176-188`). The bee karyotype is `IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(ForestrySpeciesTypes.BEE).getKaryotype()`. Fields use:
  - `Codec.STRING` for genus/species/authority,
  - `Codec.BOOL.optionalFieldOf(..., false)` for dominant/glint/secret,
  - `Codec.INT.optionalFieldOf("complexity", 0)`, `Codec.INT.optionalFieldOf("escritoire_color", -1)`,
  - `Codec.INT.fieldOf("body")`/`stripes`/`outline` with the defaults via `optionalFieldOf`,
  - `TemperatureType`/`HumidityType`: reuse the enum codecs from `forestry.api.core.ClimateCodecs` (Stage 2),
  - `Product.CODEC.listOf().optionalFieldOf("products", List.of())` and same for specialties,
  - `ResourceLocation.CODEC.optionalFieldOf("jubilance", DEFAULT_JUBILANCE)`,
  - the genome map codec from Step 1: `.optionalFieldOf("genome", Map.of())`.
  Cache in a `@Nullable static Codec<BeeSpeciesDefinition>` with a `codec()` accessor (lazy), exactly like `MutationRecipe.codec()`.

- [ ] **Step 3: Lazy StreamCodec** mirroring `MutationRecipe.buildStreamCodec` (`:190-214`): hand-write each field (`ByteBufCodecs`/`ResourceLocation.STREAM_CODEC`/the climate stream codecs from `ClimateCodecs`/`Product.STREAM_CODEC.apply(ByteBufCodecs.list())`/the genome map stream codec). Cache + `streamCodec()` accessor.

- [ ] **Step 4: Failing test** `BeeSpeciesDefinitionTest`: construct a definition with a couple of genome overrides (one data chromosome e.g. `BeeChromosomes.SPEED` → `ForestryAlleles.SPEED_SLOWER`, one reference chromosome e.g. `BeeChromosomes.EFFECT` → `Allele.reference(ForestryBeeEffects.BEATIFIC)`), encode→decode via `codec()` (with plain `JsonOps.INSTANCE` — that is how `MutationRecipeTest` round-trips its codec; the products in this test use empty component patches so plain ops suffice) and via `streamCodec()` (through a `RegistryFriendlyByteBuf`, as `MutationRecipeTest` does for its stream codec), assert round-trip equality. Run, watch it fail to compile/assert.

- [ ] **Step 5:** Implement to green. Compile, gametests, commit.

---

## Task 3: `SpeciesType.setSpecies` + volatile map + tolerant `checkSpecies`

Make the species map reloadable like `mutations` already is, and stop `checkSpecies` from throwing when species haven't loaded yet.

**Files:**
- Modify: `src/main/java/forestry/core/genetics/SpeciesType.java:33-35` (field), `:92-109` (add setter), `:156-160` (tolerant check + getters)
- Modify: `src/main/java/forestry/api/genetics/ISpeciesType.java:31-103` (relax the throws javadoc; no signature change needed)
- Test: `src/test/java/forestry/gametest/SpeciesReloadTest.java`

- [ ] **Step 1:** Change the field to volatile, never-null, empty-until-loaded (mirror the `mutations` comment):

```java
private int speciesCount = 0;
// Empty until species are loaded (datapack on the server, sync packet on the client). Never null. Volatile:
// swapped by setSpecies from the reload/sync path, read by gameplay/JEI/GUI on many threads.
private volatile ImmutableMap<ResourceLocation, S> allSpecies = ImmutableMap.of();
```

- [ ] **Step 2:** Add the internal setter (mirror `setMutations`):

```java
@org.jetbrains.annotations.ApiStatus.Internal
public void setSpecies(ImmutableMap<ResourceLocation, S> allSpecies) {
	this.allSpecies = allSpecies;
	this.speciesCount = allSpecies.size();
}
```

- [ ] **Step 3:** Keep `onSpeciesRegistered` as a thin delegate to `setSpecies` (subclasses may still override for side effects):

```java
@OverridingMethodsMustInvokeSuper
@Override
public void onSpeciesRegistered(ImmutableMap<ResourceLocation, S> allSpecies) {
	setSpecies(allSpecies);
}
```

- [ ] **Step 4:** Delete the `checkSpecies()` method and remove all its call sites in `getAllSpecies`/`getSpecies`/`getSpeciesSafe`/`getAllSpeciesIds`/`getSpeciesCount` (the map is never null now). `getSpecies(id)` still throws `RuntimeException` on a missing id (unchanged). `getDefaultSpecies()` unchanged (delegates to `getSpecies`).

- [ ] **Step 5:** Update `ISpeciesType` javadoc to drop the "throws IllegalStateException if not all species registered" clauses (the methods no longer throw that). No signature changes.

- [ ] **Step 6: Test** `SpeciesReloadTest`: a fresh `BeeSpeciesType`-shaped check is hard in isolation, so instead assert on the live bee type: `getAllSpecies()` does not throw and `getSpeciesCount() == getAllSpecies().size()`; then call `((SpeciesType) BEE_TYPE).setSpecies(ImmutableMap.of())` on a *throwaway* — NO: do not mutate the live type. Instead: assert `getAllSpecies()` never throws even conceptually by verifying the field default. Keep this test minimal: assert the live `BEE_TYPE.getAllSpecies()` is non-null and `getSpeciesCount()` matches. (The real reload behavior is covered in Task 8.)

- [ ] **Step 7:** Compile, gametests (GenomeBaselineTest must still pass — species still built by old path → `onSpeciesRegistered` → `setSpecies`), commit.

---

## Task 4: Extract `createDefaultGenomeBuilder` (reusable genome composition)

Extract steps 1–4 of `SpeciesRegistration.buildAll()` (`:44-85`) into a reusable method so the JSON projector and the existing builder path share it. **No behavior change** — `GenomeBaselineTest` is the guardrail.

**Files:**
- Modify: `src/main/java/forestry/apiimpl/plugin/SpeciesRegistration.java:44-85`

- [ ] **Step 1:** Add a static method (generic over species type) that produces the *base* genome builder (taxon defaults + species chromosome + `setRemainingDefault`), but does **not** apply per-species overrides or call `build()`:

```java
public static IGenomeBuilder createDefaultGenomeBuilder(IKaryotype karyotype, ResourceLocation speciesId, String genus, boolean dominant) {
	IChromosome<ResourceLocation> speciesChromosome = karyotype.getSpeciesChromosome();
	IGenomeBuilder builder = karyotype.createGenomeBuilder();
	ITaxon[] ancestry = IForestryApi.INSTANCE.getGeneticManager().getParentTaxa(genus);
	for (ITaxon taxon : ancestry) {
		for (Map.Entry<IChromosome<?>, ITaxon.TaxonAllele> e : taxon.alleles().entrySet()) {
			IChromosome<?> chromosome = e.getKey();
			ITaxon.TaxonAllele taxonAllele = e.getValue();
			if (!karyotype.contains(chromosome)) {
				Forestry.LOGGER.warn("Default allele set by taxon {} skipped for species {} due to being invalid for its karyotype", taxon.name(), speciesId);
				continue;
			}
			ResourceLocation reference = taxonAllele.reference();
			if (reference != null) {
				builder.set((IChromosome<ResourceLocation>) chromosome, reference);
			} else {
				builder.setUnchecked(chromosome, AllelePair.both(taxonAllele.allele()));
			}
		}
	}
	builder.setUnchecked(speciesChromosome, AllelePair.both(new Allele<>(speciesId, dominant)));
	builder.setRemainingDefault();
	return builder;
}
```

- [ ] **Step 2:** Refactor `buildAll()` to call it, then apply the per-species consumer via the unchanged `builder.buildGenome(defaultGenomeBuilder)`:

```java
ImmutableMap<ResourceLocation, S> allSpecies = this.species.build((id, builder) -> {
	IGenomeBuilder defaultGenomeBuilder = createDefaultGenomeBuilder(karyotype, id, builder.getGenus(), builder.isDominant());
	IGenome defaultGenome = builder.buildGenome(defaultGenomeBuilder);
	return builder.createSpeciesFactory().create(id, this.type.cast(), defaultGenome, builder);
});
```

- [ ] **Step 3:** Compile, run `runGameTestServer` — **`GenomeBaselineTest` must still be byte-for-byte green** (proves the extraction is behavior-preserving). Commit.

---

## Task 5: `DefinitionBeeSpeciesBuilder` adapter + `BeeSpeciesProjector`

Turn a `BeeSpeciesDefinition` into a runtime `BeeSpecies` without touching `BeeSpecies`/`Species` constructors. The adapter implements only the getter surface those constructors read (see grounding: `BeeSpecies` reads `buildProducts/buildSpecialties/getTemperature/getHumidity/getJubilance/getBody/getOutline/getStripes`; `Species` reads `getComplexity/getEscritoireColor/isSecret/hasGlint/isDominant/getAuthority/getSpecies/getGenus`).

**Files:**
- Create: `src/main/java/forestry/apiculture/genetics/DefinitionBeeSpeciesBuilder.java`
- Create: `src/main/java/forestry/apiculture/genetics/BeeSpeciesProjector.java`
- Test: `src/test/java/forestry/gametest/BeeSpeciesProjectorTest.java`

- [ ] **Step 1: Adapter** — implements `IBeeSpeciesBuilder`; constructor takes `(BeeSpeciesDefinition def, IBeeJubilance jubilance)`. All getters return from `def`/`jubilance`; all setters and `buildGenome`/`createSpeciesFactory`/`setFactory` throw `UnsupportedOperationException("datapack species builder is read-only")`. Key getters:

```java
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
@Override public List<IProduct> buildProducts() { return List.copyOf(def.products()); }
@Override public List<IProduct> buildSpecialties() { return List.copyOf(def.specialties()); }
@Override public int getBody() { return def.body(); }
@Override public int getStripes() { return def.stripes(); }
@Override public int getOutline() { return def.outline(); }
@Override public IBeeJubilance getJubilance() { return this.jubilance; }
```

- [ ] **Step 2: Override-application helper** in `BeeSpeciesProjector` — applies the sparse genome map onto a genome builder, dispatching reference vs data chromosomes (reference chromosomes have `resolver() != null` and must use the `set(IChromosome<ResourceLocation>, ResourceLocation)` overload so dominance resolves correctly — matching the code path; data chromosomes use `set(IChromosome<V>, Allele<V>)`):

```java
@SuppressWarnings({"unchecked", "rawtypes"})
private static void applyOverrides(IGenomeBuilder builder, IKaryotype karyotype, Map<ResourceLocation, Allele<?>> overrides) {
	for (Map.Entry<ResourceLocation, Allele<?>> e : overrides.entrySet()) {
		IChromosome<?> chromosome = karyotype.getChromosome(e.getKey());
		if (chromosome == null) {
			Forestry.LOGGER.warn("Skipping unknown chromosome {} in bee species genome override", e.getKey());
			continue;
		}
		Allele<?> allele = e.getValue();
		if (chromosome.resolver() != null) {
			builder.set((IChromosome<ResourceLocation>) chromosome, (ResourceLocation) allele.value());
		} else {
			builder.set((IChromosome) chromosome, allele);
		}
	}
}
```

- [ ] **Step 3: Project** — definition → `BeeSpecies` (null on failure, logged; fail-soft per spec §7):

```java
@Nullable
public static BeeSpecies project(IBeeSpeciesType type, ResourceLocation id, BeeSpeciesDefinition def) {
	try {
		IBeeJubilance jubilance = type.getJubilanceSafe(def.jubilance());
		if (jubilance == null) {
			Forestry.LOGGER.warn("Skipping bee species {}: unknown jubilance {}", id, def.jubilance());
			return null;
		}
		IKaryotype karyotype = type.getKaryotype();
		IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
		applyOverrides(gb, karyotype, def.genome());
		IGenome genome = gb.build();
		return new BeeSpecies(id, type, genome, new DefinitionBeeSpeciesBuilder(def, jubilance));
	} catch (Exception e) {
		Forestry.LOGGER.error("Failed to project bee species {}", id, e);
		return null;
	}
}
```

- [ ] **Step 4: Failing test** `BeeSpeciesProjectorTest`: hand-build a `BeeSpeciesDefinition` for a known bee (e.g. genus/species of `bee_forest`, dominant true, outline `0x19d0ec`, one product, genome override `POLLINATION → POLLINATION_SLOWER`), project against `SpeciesUtil.BEE_TYPE.get()`, assert the resulting `BeeSpecies` has the expected body/stripes/outline/temperature/humidity/products/jubilance and that its default genome's `POLLINATION` active value matches `POLLINATION_SLOWER`. Run, fail.

- [ ] **Step 5:** Implement to green. Compile, gametests, commit.

---

## Task 6: Lazy id-keyed client bee models

Replace the three startup `getAllBeeSpecies()` iterations (`ModelBee.bake` `:47-63`, `CoreClientHandler.additionalBakedModels` `:138-147`, `PluginManager.registerClient` `:250-271`) with id-keyed resolution that tolerates an empty species list. Bakes the *distinct model locations* (default per stage + code-registered customs) — not per species — and resolves by species id at render. Item colors are untouched (`ItemBeeGE.getColorFromItemStack` already per-stack).

**Files:**
- Modify: `src/main/java/forestry/apiimpl/client/BeeClientManager.java` (id-keyed shape)
- Modify: `src/main/java/forestry/api/client/IBeeClientManager.java` (interface to match)
- Modify: `src/main/java/forestry/apiculture/models/ModelBee.java:47-135` (bake distinct locations + resolve by id)
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java:250-271` (build id-keyed manager without iterating species)
- Modify: `src/main/java/forestry/core/client/CoreClientHandler.java:138-147` (register distinct locations, not per-species)

- [ ] **Step 1: `BeeClientManager`** holds, per `ILifeStage`: the default model location + a `Map<ResourceLocation /*speciesId*/, ResourceLocation /*modelLocation*/>` of customs. Add methods: `ResourceLocation getModelLocation(ILifeStage stage, ResourceLocation speciesId)` (custom or default) and `Collection<ResourceLocation> getAllModelLocations(ILifeStage stage)` (default + all customs, distinct). Update `IBeeClientManager` accordingly (drop the `Map<IBeeSpecies, ResourceLocation> getBeeModels` if nothing else needs it; otherwise keep a compatibility shim). Source data is `ClientRegistration.getBeeModels()` (already id-keyed: `ILifeStage → (speciesId → modelId)`) + `getDefaultBeeModel(stage)`.

- [ ] **Step 2: `PluginManager.registerClient`** builds the id-keyed `BeeClientManager` directly from `ClientRegistration` — **delete** the `for (IBeeSpecies species : beeSpecies)` loop and the `SpeciesUtil.getAllBeeSpecies()` call. No species needed.

- [ ] **Step 3: `ModelBee.bake`** bakes each distinct model location for the stage into a `Map<ResourceLocation, BakedModel>` (skip nulls), and `ModelBee.Baked` keeps that map + the per-stage default location + a reference to the manager. `OverrideList.resolve`: get the individual; if null return `model`; else `speciesId = individual.getSpecies().id()`, look up `manager.getModelLocation(stage, speciesId)`, return the pre-baked model for that location (fallback to the default-location model, then to `model`). No `IBeeSpecies`-instance keying.

- [ ] **Step 4: `CoreClientHandler.additionalBakedModels`** registers `ModelResourceLocation.standalone(loc)` for each `manager.getAllModelLocations(stage)` — drop the `SpeciesUtil.getAllBeeSpecies()` loop.

- [ ] **Step 5:** Compile (`compileJava` covers client). There is no gametest for client rendering; verify `compileJava`/`compileTestJava` green and that `runGameTestServer` (server-only) still passes. Commit. (Manual in-game render check is deferred to Task 10.)

---

## Task 7: `BeeSpeciesProvider` datagen + generated JSON

Generate `data/forestry/bee_species/*.json` for all built-ins from the existing `DefaultBeeSpecies` builders, following the `MutationProvider` template (`src/main/java/forestry/core/data/MutationProvider.java:29-87`). Prove code-built == JSON-projected.

**Files:**
- Create: `src/main/java/forestry/core/data/BeeSpeciesProvider.java`
- Create: `src/main/java/forestry/core/data/RecordingGenomeBuilder.java`
- Modify: `src/main/java/forestry/apiimpl/plugin/SpeciesRegistration.java` (add a builder-iteration accessor)
- Modify: `src/main/java/forestry/core/data/recipe/ForestryRecipeProvider.java:151-152` (call the provider) **or** `Data.java` (add a provider) — match the existing `MutationProvider` wiring
- Generated: `src/generated/resources/data/forestry/bee_species/*.json`
- Test: `src/test/java/forestry/gametest/BeeSpeciesEquivalenceTest.java`

- [ ] **Step 1: Builder accessor** on `SpeciesRegistration` so datagen can read the registered builders without building species:

```java
public void forEachSpeciesBuilder(java.util.function.BiConsumer<ResourceLocation, B> consumer) {
	this.species.forEach(consumer); // implement against the Registrar's iteration; if Registrar lacks forEach, add one or expose its backing map
}
```

(Check `Registrar`'s API; if it only offers `build(BiFunction)`, add a `forEach(BiConsumer)` that walks its `LinkedHashMap` without constructing values.)

- [ ] **Step 2: `RecordingGenomeBuilder`** implements `IGenomeBuilder`, recording the override map. Records the two `set` overloads; `build()` returns a stub/`null` (datagen ignores it); `setRemainingDefault`/`isEmpty` no-op; `setActive`/`setInactive`/`setUnchecked` are not used by `setGenome` consumers — throw `UnsupportedOperationException` to catch surprises:

```java
public class RecordingGenomeBuilder implements IGenomeBuilder {
	public final Map<ResourceLocation, Allele<?>> overrides = new LinkedHashMap<>();
	@Override public <V> void set(IChromosome<V> chromosome, Allele<V> allele) { overrides.put(chromosome.id(), allele); }
	@Override public void set(IChromosome<ResourceLocation> chromosome, ResourceLocation id) { overrides.put(chromosome.id(), Allele.reference(id)); }
	@Override public IGenome build() { return null; }
	@Override public void setRemainingDefault() {}
	@Override public boolean isEmpty() { return overrides.isEmpty(); }
	@Override public <V> void setActive(IChromosome<V> c, Allele<V> a) { throw new UnsupportedOperationException(); }
	@Override public <V> void setInactive(IChromosome<V> c, Allele<V> a) { throw new UnsupportedOperationException(); }
}
```

(Note: the inherited `default set(IChromosome<Boolean>, boolean)` routes to `set(chromosome, Allele.of(value, true))` → recorded. The inherited `default setUnchecked` calls setActive/setInactive — if any consumer trips it, the UOE will surface it during `runData`.)

- [ ] **Step 3: `BeeSpeciesProvider`** — a **vanilla `DataProvider`** (NOT a `RecipeOutput`-based one — ModKit's `DataHelper` has no generic JSON sink, and bee species are not recipes, so `RecipeOutput.accept`/`MutationRecipeBuilder.build` cannot be reused). Constructor `(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup)`; in `run(CachedOutput cache)`:
  1. get `IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get()`,
  2. create `ApicultureRegistration reg = new ApicultureRegistration(type)` and call `DefaultBeeSpecies.register(reg)`,
  3. build the **jubilance instance→id reverse map** once from `reg.getJubilances()` (i.e. `Map<IBeeJubilance, ResourceLocation>` by inverting the id→instance map; both built-ins are singletons so identity/equality works) — used to turn `builder.getJubilance()` (an *instance*) back into the `ResourceLocation` the definition stores; default to `ForestryBeeJubilances.DEFAULT` if not found,
  4. `reg.forEachSpeciesBuilder((id, builder) -> { ... })`: read fields, run `RecordingGenomeBuilder rec = new RecordingGenomeBuilder(); builder.buildGenome(rec);` to capture `rec.overrides`, resolve the jubilance id via the reverse map, assemble a `BeeSpeciesDefinition`, encode via `BeeSpeciesDefinition.codec()` to a `JsonElement` (use the registry-aware ops from the resolved `lookup` — `RegistryOps.create(JsonOps.INSTANCE, provider)` — so item-NBT in products survives), and `DataProvider.saveStable(cache, json, target)` where `target = output.createPathProvider(PackOutput.Target.DATA_PACK, "bee_species").json(id)`.
  - `getName()` → `"Forestry Bee Species"`.
  - Register it in `Data.gatherData` via `generator.addProvider(event.includeServer(), new BeeSpeciesProvider(packOutput, lookupProvider))` alongside the existing `generator.addProvider(...)` calls.

- [ ] **Step 4: Wire + generate** — add the provider to `Data.gatherData` (or `ForestryRecipeProvider.addRecipes`, matching `MutationProvider`). Run `runData`. Confirm `src/generated/resources/data/forestry/bee_species/` contains ~60 files. Inspect 2-3 for sanity (genus/species/colors/genome present, defaults omitted).

- [ ] **Step 5: Equivalence test** `BeeSpeciesEquivalenceTest` — for each built-in bee in the live `BEE_TYPE.getAllSpecies()` (old path, still active), load the corresponding generated JSON from the test classpath (the generated resources are on the classpath), decode via `BeeSpeciesDefinition.codec()`, project via `BeeSpeciesProjector.project(...)`, and assert the projected `BeeSpecies` equals the code-built one on: body/stripes/outline/escritoireColor/temperature/humidity/dominant/glint/secret/complexity/authority/species/genus, products, specialties, jubilance behavior (same instance/id), and **default genome equality** (compare `getDefaultGenome()` — same active+inactive allele per chromosome). This is the heart of the no-behavior-change guarantee for the cutover. Run, get it green (fixing the provider/codec as needed).

- [ ] **Step 6:** Compile, `runData` (idempotent — no diff on a second run), gametests green, commit (including the generated JSON).

---

## Task 8: Loader + sync wiring (cutover part 1 — both paths active)

Generalize the mutation reload handler, add the species manager + sync packet, and wire both sides. After this task, species are set by BOTH the old setup path AND the new load/sync path — and they're identical (JSON exists from Task 7), so everything stays green.

**Files:**
- Rename/generalize: `src/main/java/forestry/core/genetics/mutations/MutationReloadHandler.java` → `src/main/java/forestry/core/genetics/GeneticsReloadHandler.java`
- Create: `src/main/java/forestry/apiculture/genetics/BeeSpeciesManager.java` (`SimpleJsonResourceReloadListener`)
- Create: `src/main/java/forestry/core/network/packets/BeeSpeciesSyncPacket.java`
- Modify: `src/main/java/forestry/core/network/PacketIdClient.java:34-69` (add id)
- Modify: `src/main/java/forestry/core/ModuleCore.java:206-221` (register manager + generalized rebuild), add `OnDatapackSyncEvent` listener, `:249-268` (register packet)
- Modify: `src/main/java/forestry/core/client/CoreClientHandler.java:315-318` (use generalized handler)
- Test: extend `src/test/java/forestry/gametest/SpeciesReloadTest.java`

- [ ] **Step 1: `GeneticsReloadHandler`** — split the current `rebuild` into:
  - `rebuildSpecies(Map<ResourceLocation, BeeSpeciesDefinition> defs)`: project each into `BeeSpecies` (fail-soft, drop nulls), build an `ImmutableMap`, `((SpeciesType) BEE_TYPE).setSpecies(map)`; log `Loaded N bee species`.
  - `rebuildMutations(RecipeManager rm)`: the existing per-type mutation loop (`:37-60`), unchanged.
  Keep the old `MutationReloadHandler` name as a thin `@Deprecated` delegate **only if** other code references it; otherwise update all references. (Grep for `MutationReloadHandler`.)

- [ ] **Step 2: `BeeSpeciesManager`** extends `SimpleJsonResourceReloadListener` over folder `"bee_species"` with the standard JSON gson. Singleton (`INSTANCE`) holding the last-parsed `Map<ResourceLocation, BeeSpeciesDefinition>` (`volatile`, default empty) accessible via `getDefinitions()`. In `apply(Map<ResourceLocation, JsonElement> object, ResourceManager rm, ProfilerFiller profiler)`: decode each entry via `BeeSpeciesDefinition.codec()` (log-and-skip on `DataResult` error), store the map, then call `GeneticsReloadHandler.rebuildSpecies(map)`. **Decode with `RegistryOps`, not plain `JsonOps`** — `SimpleJsonResourceReloadListener.apply` does not receive a `HolderLookup.Provider`, so obtain one via the current server (`net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()`) and build `RegistryOps.create(JsonOps.INSTANCE, registryAccess)` so product item-NBT in datapack bees decodes correctly. (If no server is available — e.g. a stray client-side reload — fall back to plain `JsonOps` or skip; the authoritative client data arrives via the sync packet, which uses the registry-aware stream codec.)

- [ ] **Step 3: `BeeSpeciesSyncPacket`** — `record BeeSpeciesSyncPacket(Map<ResourceLocation, BeeSpeciesDefinition> definitions)` implementing `CustomPacketPayload` (template: `RecipeCachePacket`). `type()` → `PacketIdClient.BEE_SPECIES_SYNC`. `encode`/`decode` use a `ByteBufCodecs.map(...)` of `ResourceLocation.STREAM_CODEC` → `BeeSpeciesDefinition.streamCodec()`. `handle(msg, player)` (client): store defs into `BeeSpeciesManager.INSTANCE` (or a client mirror) and call `GeneticsReloadHandler.rebuildSpecies(defs)`, then `GeneticsReloadHandler.rebuildMutations(Minecraft.getInstance().getConnection().getRecipeManager())` (species-before-mutations).

- [ ] **Step 4: Add packet id** in `PacketIdClient`: `public static final CustomPacketPayload.Type<BeeSpeciesSyncPacket> BEE_SPECIES_SYNC = type("bee_species_sync");` and register it in `ModuleCore.registerPackets` (`registry.clientbound(PacketIdClient.BEE_SPECIES_SYNC, BeeSpeciesSyncPacket::encode, BeeSpeciesSyncPacket::decode, BeeSpeciesSyncPacket::handle);`).

- [ ] **Step 5: Server reload wiring** in `ModuleCore.registerReloadListeners` — register `BeeSpeciesManager.INSTANCE` **before** the mutation rebuild listener (apply order = registration order), and change the mutation listener to call `GeneticsReloadHandler.rebuildMutations(recipeManager)`:

```java
event.addListener(BeeSpeciesManager.INSTANCE); // parses JSON + rebuildSpecies in its apply (game thread)
RecipeManager recipeManager = event.getServerResources().getRecipeManager();
event.addListener((prepBarrier, rm, pp, rp, bg, game) ->
	prepBarrier.wait(Unit.INSTANCE).thenRunAsync(() -> GeneticsReloadHandler.rebuildMutations(recipeManager), game));
```

(Confirm `SimpleJsonResourceReloadListener` runs its `apply` on the game thread within the barrier; if not, wrap its rebuildSpecies onto `gameExecutor` like the mutation listener.)

- [ ] **Step 6: `OnDatapackSyncEvent`** listener in `ModuleCore` (game bus, `NeoForge.EVENT_BUS.addListener`): send the sync packet to the relevant players:

```java
private static void onDatapackSync(OnDatapackSyncEvent event) {
	BeeSpeciesSyncPacket packet = new BeeSpeciesSyncPacket(BeeSpeciesManager.INSTANCE.getDefinitions());
	event.getRelevantPlayers().forEach(player -> NetworkUtil.sendToPlayer(packet, player));
}
```

- [ ] **Step 7: Client** — change `CoreClientHandler.onRecipesUpdated` to call `GeneticsReloadHandler.rebuildMutations(event.getRecipeManager())` (species arrive via the sync packet, not here). Keep `EventPriority.HIGH`.

- [ ] **Step 8: Test** — extend `SpeciesReloadTest`: drive `GeneticsReloadHandler.rebuildSpecies(BeeSpeciesManager.INSTANCE.getDefinitions())` (defs loaded at server start) and assert `BEE_TYPE.getAllSpecies()` is the full built-in set and equals the code-built set (reuse the equivalence assertions). Confirm `runGameTestServer` boots (the manager loads the generated JSON at server start) and logs `Loaded N bee species` with N≈60.

- [ ] **Step 9:** Compile, gametests (all green: species now loaded from JSON at server start *and* the old setup path — identical), commit.

---

## Task 9: Demote the runtime `registerSpecies` path (cutover part 2 — JSON only)

Remove the old runtime species build so datapacks are the sole source. After this, `runGameTestServer` proves the game runs on JSON-only species.

**Files:**
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java:183-191` (drop the `onSpeciesRegistered` loop + empty-species guard)
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesType.java:156-172` (stop building species in `handleSpeciesRegistration`; keep companion captures + hive manager)
- Modify: `src/main/java/forestry/api/genetics/ISpeciesType.java:219-247` (adjust `handleSpeciesRegistration` contract: returns companion data only / `void`)
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java` (remove the `DefaultBeeSpecies.register(apiculture)` call from `registerApiculture`)
- Modify: other species types' `handleSpeciesRegistration` (tree/butterfly) only if the interface signature changes — keep their old behavior compiling (they still build species the old way; **only bees go data-driven this stage**). See note below.
- Modify: missing-species fallback audit (callers of `getSpecies` on player-facing/saved paths → `getSpeciesSafe` + default)

> **Important scoping note:** `handleSpeciesRegistration` is shared by tree/butterfly types, which are NOT data-driven yet. Do **not** change the interface in a way that breaks them. Two options — pick the one that keeps trees/butterflies building species as before:
> - **(A, preferred)** Keep `handleSpeciesRegistration` returning `ImmutableMap<ResourceLocation, S>` for tree/butterfly (unchanged), and for **bees** return `ImmutableMap.of()` (no species built at setup). Then in `PluginManager.registerGenetics`, still call `onSpeciesRegistered(map)` per type (trees/butterflies get their real map; bees get empty → `setSpecies(empty)`, later overwritten by the manager). **Remove only the empty-species throw guard** (`:188-190`) since bees are legitimately empty at setup. This is the smallest, safest change.
> - (B) Split the lifecycle method — more invasive; avoid unless A doesn't work.

- [ ] **Step 1:** In `BeeSpeciesType.handleSpeciesRegistration`, keep the registration replay + companion captures (`beeEffects`/`flowerTypes`/`activityTypes`/`jubilances`) + `setHiveManager`, but **return `registration.buildAll()` only if you keep option A's contract** — actually, to stop building bees at setup, change the final line from `return registration.buildAll();` to `return ImmutableMap.of();`. (The companion registries and hive manager still get set.)

- [ ] **Step 2:** In `PluginManager.registerGenetics` (`:183-191`), **remove the empty-species throw guard**:

```java
for (Map.Entry<ISpeciesType<?, ?>, ImmutableMap<ResourceLocation, ?>> entry : allSpecies.entrySet()) {
	entry.getKey().onSpeciesRegistered((ImmutableMap) entry.getValue());
	// (removed) if (getAllSpecies().isEmpty()) throw ...  — bees are empty until the datapack loads
}
```

- [ ] **Step 3:** In `DefaultForestryPlugin.registerApiculture`, **remove** the `DefaultBeeSpecies.register(apiculture);` call (species now come from datagen/JSON). Keep all companion registrations (flower types, effects, activities, jubilances, hives, village bees, swarmer). `DefaultBeeSpecies` stays in the source tree as datagen input (Task 7 calls it).

- [ ] **Step 4:** Audit `getSpecies(` call sites for bee paths that handle stale/saved/player-facing data (e.g. analyzer, tooltips, the bee item). Where a missing species would now be possible (datapack removed it), switch to `getSpeciesSafe` + fall back to `getDefaultSpecies()` or a graceful no-op. (Stage 1 already hardened several; grep `BEE_TYPE.get().getSpecies(` and `SpeciesUtil.getBeeSpecies(`.) Keep the change minimal and behavior-preserving for present species.

- [ ] **Step 5: Tests** — `runGameTestServer` is now the real proof: bees exist only because the manager loaded the generated JSON at server start. `GenomeBaselineTest` (default genomes from JSON projection) and `MutationRecipeTest` (114/42/1, mutations find their data-driven species) **must be green**. Add a `SpeciesFallbackTest`: project a definition referencing an unknown jubilance id → `BeeSpeciesProjector.project` returns null (skipped), and `rebuildSpecies` over a map containing it yields a species set missing only that entry (no crash). Add an empty-tolerance assertion: a fresh `setSpecies(ImmutableMap.of())` followed by `getAllSpecies()` returns empty without throwing.

- [ ] **Step 6:** Compile, `runData` (idempotent), `runGameTestServer` (all green), commit.

---

## Task 10: Final verification + review

**Files:** none (verification only) + any fixes surfaced.

- [ ] **Step 1:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava` — green.
- [ ] **Step 2:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData` — green and **idempotent** (no working-tree diff in `src/generated/.../bee_species/` on a clean second run; ignore the pre-existing `farm_*` lang non-determinism noted in memory).
- [ ] **Step 3:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` — all gametests green, including `GenomeBaselineTest`, `MutationRecipeTest` (114/42/1), and the new Stage-3 tests; logs show `Loaded ~60 bee species`.
- [ ] **Step 4:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew build` — green.
- [ ] **Step 5: Manual smoke (if a dev client is available):** launch the client, spawn a bee, confirm it renders tinted; `/reload` after editing a generated `bee_species/*.json` (e.g. change a `body` color) and confirm the change applies live; confirm a fresh client receives bees on login. (If no client is available, note it and rely on gametests.)
- [ ] **Step 6:** Dispatch a final code-reviewer over the whole Stage-3 diff (`git diff <stage-3-base>..HEAD`) against the spec. Fix any real issues.
- [ ] **Step 7:** Update the project memory file `data-driven-genetics-overhaul.md` with a "Stage 3 COMPLETE" section (mechanism, key files, gotchas), mirroring the Stage 2 section.

---

## Risks & gotchas (read before starting)

- **Generated JSON must exist before Task 9.** Never run Task 9 before Task 7's JSON is committed, or the game has zero bees.
- **Reference vs data chromosome dispatch** (Tasks 5 & 7) is load-bearing for byte-identical genomes: reference chromosomes (`resolver() != null`) use the `ResourceLocation` set-overload (dominance resolved by the resolver); data chromosomes use the `Allele` overload. The recording builder records reference sets as `Allele.reference(id)`. `GenomeBaselineTest` will catch a mistake here.
- **Reload apply ordering** (Task 8): register `BeeSpeciesManager` before the mutation rebuild listener; verify `SimpleJsonResourceReloadListener.apply` runs on the game thread (if not, marshal `rebuildSpecies` onto `gameExecutor`).
- **Client species/mutation ordering** (Task 8): the sync packet and `RecipesUpdatedEvent` can arrive in either order — the packet handler rebuilds species then mutations; `RecipesUpdatedEvent` rebuilds mutations. Both rebuilding mutations is fine (idempotent).
- **Don't break trees/butterflies** (Task 9): use option A so their `handleSpeciesRegistration` still builds species the old way; only bees return empty at setup.
- **`NetworkUtil.sendToAllPlayers` no-ops with no server** — that's why sync uses `OnDatapackSyncEvent` (`getRelevantPlayers` → `sendToPlayer`), which fires after server start with real players.
- **DRY the genome map codec** (Task 2 Step 1): prefer extracting the shared `dispatchedMap` allele-codec helpers so `MutationRecipe` and `BeeSpeciesDefinition` don't duplicate them.
