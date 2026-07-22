# Data-Driven Butterfly Species Implementation Plan (Stage 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 35 built-in butterfly species load exclusively from datapack JSON (`data/<ns>/butterfly_species/*.json`), live-reloadable + client-synced, with no behavior change (golden-master enforced).

**Architecture:** Mirror Stage 4 (trees): a `SimpleJsonResourceReloadListener` loads `ButterflySpeciesDefinition` records → `ButterflySpeciesProjector` builds runtime `ButterflySpecies` → volatile `setSpecies` swap; clientbound sync on `OnDatapackSyncEvent`. Species **types**/karyotypes, the shared entity/cocoon/item features, and the cocoon/effect reference registries stay code-registered. **No per-species bindings table** (butterfly assets are global). Two butterfly-specific concerns: the `ButterflySpawner` moves to one-time setup, and live `EntityButterfly` instances are refreshed on reload.

**Tech Stack:** NeoForge 1.21.1, Java 21, Mojang DataFixerUpper codecs, `SimpleJsonResourceReloadListener`, NeoForge gametests.

**Spec:** `docs/superpowers/specs/2026-07-04-data-driven-butterflies-design.md`.

## Global Constraints

- **Build JDK:** prefix EVERY gradle call with `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9` (system java 26 is rejected by Gradle 9.2.1).
- **Test runner:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` runs all gametests. `build` fails only at `:test` ("no tests discovered") — pre-existing, ignore; use `runGameTestServer` as the gate.
- **Gametest boilerplate:** `@GameTestHolder(ForestryConstants.MOD_ID)` + `@PrefixGameTestTemplate(false)` on the class; `@GameTest(template = "empty")` per method; `helper.succeed()` / `helper.fail(msg)`.
- **Test hygiene (MANDATORY):** any test that calls `rebuildButterflySpecies`/`setSpecies` on the live shared map MUST snapshot it first and restore in a `finally` via `((SpeciesType<IButterflySpecies, ?>) type).setSpecies(snapshot)` + `GeneticsReloadHandler.rebuildMutations(recipeManager)` — else the identity-keyed `MutationManager` is left stale and pollutes `MutationRecipeTest`. See `SpeciesFallbackTest`/`TreeSpeciesFallbackTest` for the exact idiom.
- **Complexity is not byte-comparable:** `Species#getComplexity()` lazily re-derives from the identity-keyed `MutationManager` when authored 0; equivalence tests assert the weaker authored-nonzero invariant (see `TreeSpeciesEquivalenceTest`).
- **Reference-chromosome recording:** reference chromosomes (SPECIES/FLOWER_TYPE/EFFECT/COCOON, `resolver() != null`) record as `Allele.reference(id)`; the datagen provider needs bee-style instance→id inversion via `RecordingGenomeBuilder`.
- **`runData` non-determinism:** `runData` perturbs a few `en_us.json` farm/leaf/letter names + a `.cache` file (pre-existing flake). Revert those; commit only `butterfly_species/*.json`.
- **Do NOT touch** the two untracked `docs/superpowers/*/2026-07-01-data-driven-bee-docs*.md` files (separate project).

## Phasing (why this order)

Foundation (definition, resolvers, projector, reload handler) → butterfly-specific reload-safety (spawner, entity refresh) → client id-keying → datagen+equivalence (JSON must exist before demotion) → loader/sync wiring (both paths active) → demotion (buildAll→empty; only safe once JSON exists + resolvers are fail-soft + spawner/entity are reload-safe) → final verification. Tasks 1–8 are additive/non-breaking; Task 9 activates the loader alongside the old path; Task 10 is the cutover.

**Templates that exist in the repo (read them — the mechanical tasks are faithful ports):** `forestry/arboriculture/genetics/{TreeSpeciesDefinition,TreeSpeciesManager,TreeSpeciesProjector,DefinitionTreeSpeciesBuilder}.java`, `forestry/apiculture/genetics/BeeSpeciesManager.java`, `forestry/core/network/packets/{TreeSpeciesSyncPacket,BeeSpeciesSyncPacket}.java`, `forestry/core/data/{BeeSpeciesProvider,TreeSpeciesProvider}.java`, `forestry/core/genetics/GeneticsReloadHandler.java`, and the tests `src/test/java/forestry/gametest/{TreeSpeciesDefinition,TreeSpeciesProjector,TreeSpeciesReload,TreeSpeciesFallback,TreeSpeciesEquivalence}Test.java`, `BeeSpeciesProjectorTest.java`. The bee provider (`BeeSpeciesProvider`) is the closer template for the datagen task (it does the reference-chromosome instance→id inversion butterflies need).

---

## Task 1: `ButterflySpeciesDefinition` record + lazy codec + stream codec

**Files:**
- Create: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java`
- Test: `src/test/java/forestry/gametest/ButterflySpeciesDefinitionTest.java`

**Interfaces:**
- Produces: `record ButterflySpeciesDefinition(ResourceLocation id, String genus, String species, boolean dominant, boolean glint, boolean secret, int complexity, ResourceLocation authority, int escritoireColor, TemperatureType temperature, HumidityType humidity, boolean nocturnal, boolean moth, float rarity, float flightDistance, int serumColor, Optional<TagKey<Biome>> spawnBiomes, List<IProduct> products, List<IProduct> caterpillarProducts, Map<ResourceLocation, Allele<?>> genome)`; `static Codec<ButterflySpeciesDefinition> codec(IKaryotype karyotype)`; `static StreamCodec<RegistryFriendlyByteBuf, ButterflySpeciesDefinition> streamCodec(IKaryotype karyotype)`. Codecs are built lazily (resolve the karyotype only at first use), mirroring `TreeSpeciesDefinition`.

- [ ] **Step 1: Write the failing test** `ButterflySpeciesDefinitionTest`. Port `TreeSpeciesDefinitionTest` verbatim, swapping `Tree`→`Butterfly`, `TREE_TYPE`→`BUTTERFLY_TYPE`, and the species field set. Two methods: (a) `codecRoundTrip` — build a definition for a known built-in id (`ForestryButterflySpecies.MONARCH.id()`), encode via `codec(karyotype)` to `JsonOps`, decode, assert `decoded.equals(original)`; (b) `streamCodecRoundTrip` — encode/decode via a `RegistryFriendlyByteBuf` (copy the buffer construction idiom from `TreeSpeciesDefinitionTest`), assert equal. Get the karyotype via `SpeciesUtil.BUTTERFLY_TYPE.get().getKaryotype()`.

- [ ] **Step 2: Run the test, verify it fails** — class does not exist. Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileTestJava`. Expected: FAIL (symbol not found).

- [ ] **Step 3: Implement `ButterflySpeciesDefinition`.** Port `TreeSpeciesDefinition.java`. Field deltas vs the tree record: drop tree-only fields; the shared base is genus/species/dominant/glint/secret/complexity/authority/escritoireColor; add `temperature` (`TemperatureType`, codec `ClimateCodecs.TEMPERATURE_CODEC` — confirm the field name in `forestry/api/core/ClimateCodecs.java`), `humidity` (`HumidityType`), `nocturnal`/`moth` (`Codec.BOOL`, default false), `rarity`/`flightDistance` (`Codec.FLOAT`), `serumColor` (`Codec.INT`), `spawnBiomes` (`TagKey.codec(Registries.BIOME)` wrapped `optionalFieldOf`), `products`/`caterpillarProducts` (`IProduct.CODEC.listOf()` — confirm `IProduct` codec name from `BeeSpeciesDefinition`), `genome` (chromosome-id→allele map, copy the exact genome-map codec construction from `TreeSpeciesDefinition`). Use `RecordCodecBuilder.mapCodec(...).codec()` inside a `Suppliers.memoize` lazy so the karyotype is resolved on first use (copy the lazy pattern from `TreeSpeciesDefinition` exactly). ~20 fields exceeds `group()`'s 16-arg limit — group the 4 optional/list tail fields (spawnBiomes, products, caterpillarProducts, genome) into a nested sub-record OR use `RecordCodecBuilder` `.and(...)` chaining as `BeeSpeciesDefinition` does for its larger field set (read `BeeSpeciesDefinition` — it has >16 fields and shows the pattern).

- [ ] **Step 4: Run the test, verify it passes.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`. Expected: PASS (both round-trip methods).

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java src/test/java/forestry/gametest/ButterflySpeciesDefinitionTest.java
git commit -m "feat(lepidopterology): ButterflySpeciesDefinition record + lazy codec + stream codec

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Fail-soft `SPECIES` resolver + `getCocoonSafe`/`getButterflyEffectSafe`

**Files:**
- Modify: `src/main/java/forestry/api/genetics/alleles/ButterflyChromosomes.java` (SPECIES resolver, ~L19)
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesType.java` (add safe getters near `getCocoon`/`getButterflyEffect`, L58-66)
- Modify: `src/main/java/forestry/api/lepidopterology/genetics/IButterflySpeciesType.java` (declare `getCocoonSafe`/`getButterflyEffectSafe`)
- Test: `src/test/java/forestry/gametest/ButterflySpeciesFallbackTest.java` (create; the removed-species case is added in Task 10)

**Interfaces:**
- Produces: `@Nullable IButterflyCocoon getCocoonSafe(ResourceLocation id)`; `@Nullable IButterflyEffect getButterflyEffectSafe(ResourceLocation id)` on `IButterflySpeciesType`/`ButterflySpeciesType`. `ButterflyChromosomes.SPECIES` resolver returns the default species (from the live map, not the throwing `getDefaultSpecies()`) on absent id.

- [ ] **Step 1: Write the failing test** `ButterflySpeciesFallbackTest.speciesResolverFallsSoft`. Port `TreeSpeciesFallbackTest`'s resolver test: build a genome whose SPECIES chromosome references a non-existent id (`ForestryConstants.forestry("does_not_exist")`), resolve it via `ButterflyChromosomes.SPECIES.resolver().get(id)`, assert it returns the default species (`BUTTERFLY_TYPE.get().getDefaultSpecies()`) rather than throwing.

- [ ] **Step 2: Run test, verify it fails** — current resolver `BUTTERFLY_TYPE.get().getSpecies(id)` throws for an unknown id. Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`. Expected: FAIL (RuntimeException, not the default).

- [ ] **Step 3: Implement.** In `ButterflyChromosomes.java`, replace the SPECIES resolver so it uses a fail-soft helper (mirror `TreeChromosomes.SPECIES`, which was migrated in Stage 4 — read it): resolve via `getSpeciesSafe(id)`, fall back to `getDefaultSpecies()`; import `forestry.Forestry` and log a warning once on miss. In `ButterflySpeciesType.java`, add:
```java
@Nullable
@Override
public IButterflyCocoon getCocoonSafe(ResourceLocation id) {
    return this.cocoons.get(id);
}

@Nullable
@Override
public IButterflyEffect getButterflyEffectSafe(ResourceLocation id) {
    return this.butterflyEffects.get(id);
}
```
(The existing `getCocoon`/`getButterflyEffect` keep throwing via `requireValue`; add the `@Nullable` map-get variants alongside, mirroring trees' `getFruit`/`getFruitSafe`.) Declare both on `IButterflySpeciesType`.

- [ ] **Step 4: Run test, verify it passes.** Run `runGameTestServer`. Expected: PASS. `GenomeBaselineTest` unaffected (default genomes never reference a missing id).

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/api/genetics/alleles/ButterflyChromosomes.java src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesType.java src/main/java/forestry/api/lepidopterology/genetics/IButterflySpeciesType.java src/test/java/forestry/gametest/ButterflySpeciesFallbackTest.java
git commit -m "feat(lepidopterology): fail-soft butterfly SPECIES resolver + getCocoonSafe/getButterflyEffectSafe

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `DefinitionButterflySpeciesBuilder` adapter + `ButterflySpeciesProjector`

**Files:**
- Create: `src/main/java/forestry/lepidopterology/genetics/DefinitionButterflySpeciesBuilder.java`
- Create: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesProjector.java`
- Test: `src/test/java/forestry/gametest/ButterflySpeciesProjectorTest.java`

**Interfaces:**
- Consumes: `ButterflySpeciesDefinition` (Task 1); `getCocoonSafe`/`getButterflyEffectSafe` (Task 2).
- Produces: `class DefinitionButterflySpeciesBuilder implements IButterflySpeciesBuilder` (read-only over a definition); `ButterflySpeciesProjector.project(IButterflySpeciesType type, ResourceLocation id, ButterflySpeciesDefinition def) -> @Nullable IButterflySpecies` (fail-soft: null+log on exception).

- [ ] **Step 1: The adapter.** Implement `IButterflySpeciesBuilder` (interface at `forestry/api/plugin/IButterflySpeciesBuilder.java`; its supertype `ISpeciesBuilder` at `forestry/api/plugin/ISpeciesBuilder.java`). Every genetics/metadata getter delegates to `def`; every setter and `buildGenome`/`createSpeciesFactory` (and product-build stubs) throws `UnsupportedOperationException`. Model exactly on `DefinitionTreeSpeciesBuilder` — read it and swap the tree-specific getters for the butterfly field set (temperature/humidity/nocturnal/moth/rarity/flightDistance/serumColor/spawnBiomes/products/caterpillarProducts). Reuse `core/genetics/GenomeProjection.applyOverrides` for the genome (as the tree adapter does).

- [ ] **Step 2: The projector.** Port `BeeSpeciesProjector` (the bee one, because butterflies use reference chromosomes for cocoon/effect just like bees do for their references) OR `TreeSpeciesProjector` — whichever's genome handling matches. Signature:
```java
public static IButterflySpecies project(IButterflySpeciesType type, ResourceLocation id, ButterflySpeciesDefinition def) {
    try {
        DefinitionButterflySpeciesBuilder builder = new DefinitionButterflySpeciesBuilder(type, id, def);
        // build the runtime ButterflySpecies via the same factory the code path uses (see ButterflySpecies ctor / Species factory)
        return new ButterflySpecies(/* ... from builder ... */);
    } catch (Exception e) {
        Forestry.LOGGER.error("Skipping butterfly species {} - projection failed", id, e);
        return null;
    }
}
```
Match the exact `ButterflySpecies` construction to how `ButterflySpeciesBuilder.build()` does it today (read `forestry/apiimpl/plugin/ButterflySpeciesBuilder.java` + `ButterflySpecies.java` constructor). No bindings lookup is needed (butterflies have no per-species bindings; the cocoon/effect registries are consulted lazily by genome resolution, not at projection).

- [ ] **Step 3: Write the failing test** `ButterflySpeciesProjectorTest`. Port `BeeSpeciesProjectorTest`/`TreeSpeciesProjectorTest`: for a known built-in (Monarch), hand-build (or round-trip from the live species') a `ButterflySpeciesDefinition`, project it, and assert the projected species' genetics fields (genus/species/temperature/humidity/nocturnal/moth/rarity/flightDistance/serumColor) and a genome override (e.g. SIZE) equal the live built-in's. Do NOT assert complexity byte-equality (see Global Constraints).

- [ ] **Step 4: Run tests, verify pass.** Run `runGameTestServer`. Fix adapter method-set compile errors against the compiler (the interface may have methods the tree adapter lacks).

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/lepidopterology/genetics/DefinitionButterflySpeciesBuilder.java src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesProjector.java src/test/java/forestry/gametest/ButterflySpeciesProjectorTest.java
git commit -m "feat(lepidopterology): DefinitionButterflySpeciesBuilder + ButterflySpeciesProjector

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: `GeneticsReloadHandler.rebuildButterflySpecies`

**Files:**
- Modify: `src/main/java/forestry/core/genetics/GeneticsReloadHandler.java` (add method next to `rebuildTreeSpecies`, L80-94)
- Test: `src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java`

**Interfaces:**
- Consumes: `ButterflySpeciesProjector.project` (Task 3).
- Produces: `static void rebuildButterflySpecies(Map<ResourceLocation, ButterflySpeciesDefinition> definitions)` — projects each (filtering nulls), calls `((SpeciesType<IButterflySpecies, ?>) type).setSpecies(projected)`.

- [ ] **Step 1: Write the failing test** `ButterflySpeciesReloadTest.rebuildRepopulates`. Port `TreeSpeciesReloadTest`: snapshot the live species (`finally` restore per Global Constraints), round-trip the current live species → definitions (via a small inline `toDefinition` or reuse the provider's builder if available), call `rebuildButterflySpecies`, assert the map is non-empty and contains Monarch. Include `managerLoadedAllSpeciesAtServerStart`-style assertion deferred to Task 9 (manager doesn't exist yet).

- [ ] **Step 2: Run test, verify it fails** — method does not exist: FAIL to compile.

- [ ] **Step 3: Implement.** Add to `GeneticsReloadHandler` (imports: butterfly species/type + `ButterflySpecies`, `ButterflySpeciesDefinition`, `ButterflySpeciesProjector`), copying `rebuildTreeSpecies` structure exactly.

- [ ] **Step 4: Run test, verify it passes.** Run `runGameTestServer`. Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/core/genetics/GeneticsReloadHandler.java src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java
git commit -m "feat(lepidopterology): GeneticsReloadHandler.rebuildButterflySpecies

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Register `ButterflySpawner` once at setup (decouple from species load)

**Files:**
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesType.java` (`onSpeciesRegistered`, L83-90 — remove the spawner line)
- Modify: the one-time butterfly module setup site — `src/main/java/forestry/modules/ModuleLepidopterology.java` (or `DefaultForestryPlugin.registerLepidopterology`; pick the hook that runs exactly once at setup, AFTER the tree type exists). Verify by reading both.
- Test: `src/test/java/forestry/gametest/ButterflySpawnerReloadTest.java`

**Interfaces:**
- Produces: `ButterflySpawner` is registered on `SpeciesUtil.TREE_TYPE.get()` exactly once, independent of butterfly species (re)loads.

- [ ] **Step 1: Write the failing test** `ButterflySpawnerReloadTest.oneSpawnerAfterReload`:
```java
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpawnerReloadTest {
    @GameTest(template = "empty")
    public static void oneSpawnerAfterReload(GameTestHelper helper) {
        var treeType = SpeciesUtil.TREE_TYPE.get();
        long before = treeType.getLeafTickHandlers().stream()
            .filter(h -> h instanceof ButterflySpawner).count();
        var butterflyType = (SpeciesType<IButterflySpecies, ?>) SpeciesUtil.BUTTERFLY_TYPE.get();
        var snapshot = ImmutableMap.copyOf(butterflyType.getAllSpeciesIds().stream()
            .collect(Collectors.toMap(id -> id, butterflyType::getSpecies)));
        try {
            // simulate a butterfly species reload swap
            ((SpeciesType<IButterflySpecies, ?>) SpeciesUtil.BUTTERFLY_TYPE.get()).setSpecies(snapshot);
            long after = treeType.getLeafTickHandlers().stream()
                .filter(h -> h instanceof ButterflySpawner).count();
            if (after != before) {
                helper.fail("Expected the ButterflySpawner count to be unchanged by a butterfly reload (was "
                    + before + ", now " + after + ") - it must be registered once at setup, not per reload");
                return;
            }
        } finally {
            butterflyType.setSpecies(snapshot);
            GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
        }
        helper.assertTrue(before >= 1, "expected the ButterflySpawner to be registered once at setup");
        helper.succeed();
    }
}
```
(Imports: `com.google.common.collect.ImmutableMap`, `java.util.stream.Collectors`, `forestry.core.genetics.SpeciesType`, `forestry.core.genetics.GeneticsReloadHandler`, `forestry.core.utils.SpeciesUtil`, `forestry.lepidopterology.ButterflySpawner`, `forestry.api.lepidopterology.genetics.IButterflySpecies`.)

- [ ] **Step 2: Run test, verify it fails.** Today the spawner is registered in `onSpeciesRegistered`, which routes through `setSpecies` — so the swap in the test re-runs `onSpeciesRegistered`? NO: `setSpecies` does not call `onSpeciesRegistered`. So today the count is whatever setup produced and won't change on the raw `setSpecies` — this test may PASS spuriously today. To make it a true RED, FIRST do Task-10-style demotion? No. Instead, assert the *stronger* invariant that survives the Task-9 change: after Task 9, `rebuildButterflySpecies` (not raw `setSpecies`) is the reload path, and if the spawner line still lived in a reload-driven callback it would duplicate. Since Task 5 precedes Task 9, write the test to drive the future reload path directly: replace the `setSpecies(snapshot)` call in the `try` with `GeneticsReloadHandler.rebuildButterflySpecies(<definitions round-tripped from snapshot>)` and assert the spawner count is unchanged. Before Step 3, the spawner line is still in `onSpeciesRegistered` — but `rebuildButterflySpecies`→`setSpecies` still does not call `onSpeciesRegistered`, so this remains green-if-unmoved. **Therefore make the RED explicit:** the real risk is re-registration if the spawner is ever moved INTO `setSpecies`. Guard it differently — assert `before == 1` exactly (not `>= 1`) AND that after N reloads it is still 1. If setup currently registers it in `onSpeciesRegistered` (called once at setup), `before` is already 1; the test locks that it stays 1. Run and confirm the current count is exactly 1 (if setup double-registers for any reason, this fails RED). Run: `runGameTestServer`.

- [ ] **Step 3: Implement.** Remove the spawner-registration block from `ButterflySpeciesType.onSpeciesRegistered` (leave it a thin `super.onSpeciesRegistered(allSpecies)` delegate). Add a one-time registration at butterfly module setup — in `ModuleLepidopterology`'s setup/`registerLepidopterology` completion (read the module lifecycle; use the same phase where other one-time tree/butterfly wiring runs, AFTER `SpeciesUtil.TREE_TYPE` is available), guarded by `ModuleLepidopterology.spawnButterflysFromLeaves`:
```java
if (ModuleLepidopterology.spawnButterflysFromLeaves) {
    SpeciesUtil.TREE_TYPE.get().registerLeafTickHandler(new ButterflySpawner());
}
```
Ensure this hook runs exactly once (not per reload). If no such once-only hook exists, add one in the module's `setupApi`/common-setup equivalent used by other subsystems.

- [ ] **Step 4: Run test, verify it passes.** Run `runGameTestServer`. Expected: PASS (exactly one spawner, unchanged across reload). Also confirm `GenomeBaselineTest` + existing butterfly behavior unaffected.

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesType.java src/main/java/forestry/modules/ModuleLepidopterology.java src/test/java/forestry/gametest/ButterflySpawnerReloadTest.java
git commit -m "feat(lepidopterology): register ButterflySpawner once at setup, not per species load

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Refresh live `EntityButterfly` species on reload

**Files:**
- Modify: `src/main/java/forestry/lepidopterology/entities/EntityButterfly.java` (add `refreshSpeciesFromReload()`)
- Modify: `src/main/java/forestry/core/genetics/GeneticsReloadHandler.java` (`rebuildButterflySpecies` — after the swap, walk loaded entities)
- Test: `src/test/java/forestry/gametest/ButterflyEntityReloadTest.java`

**Interfaces:**
- Consumes: `rebuildButterflySpecies` (Task 4).
- Produces: `EntityButterfly.refreshSpeciesFromReload()` — re-resolves the cached individual/species from the entity's in-memory genome via the current live map (fail-soft to default); called for every loaded `EntityButterfly` at the end of `rebuildButterflySpecies` when a server is running.

- [ ] **Step 1: Write the failing test** `ButterflyEntityReloadTest.entityRefreshedAfterReload`:
```java
@GameTest(template = "empty")
public static void entityRefreshedAfterReload(GameTestHelper helper) {
    var type = (SpeciesType<IButterflySpecies, ?>) SpeciesUtil.BUTTERFLY_TYPE.get();
    var snapshot = ImmutableMap.copyOf(type.getAllSpeciesIds().stream()
        .collect(Collectors.toMap(id -> id, type::getSpecies)));
    try {
        IButterflySpecies species = SpeciesUtil.BUTTERFLY_TYPE.get().getDefaultSpecies();
        IButterfly individual = species.createIndividual();
        // spawn an EntityButterfly holding this individual at a rel pos
        BlockPos rel = new BlockPos(2, 2, 2);
        EntityButterfly entity = SpeciesUtil.BUTTERFLY_TYPE.get()
            .spawnButterflyInWorld(helper.getLevel(), individual, helper.absolutePos(rel).getX(),
                helper.absolutePos(rel).getY(), helper.absolutePos(rel).getZ());
        IButterflySpecies beforeSpecies = entity.getButterfly().getSpecies();
        // reload: round-trip snapshot -> definitions -> rebuild (rebuilds species instances)
        GeneticsReloadHandler.rebuildButterflySpecies(<definitions from snapshot>);
        IButterflySpecies afterSpecies = entity.getButterfly().getSpecies();
        if (afterSpecies == beforeSpecies) {
            helper.fail("Expected the entity's species to be refreshed to the new instance after a reload");
            return;
        }
        helper.assertTrue(afterSpecies.id().equals(beforeSpecies.id()),
            "refreshed species must be the same id, a fresh instance");
    } finally {
        type.setSpecies(snapshot);
        GeneticsReloadHandler.rebuildMutations(helper.getLevel().getServer().getRecipeManager());
    }
    helper.succeed();
}
```
(Confirm the entity accessor names — `getButterfly()`/`getIndividual()`/`getSpecies()` — against `EntityButterfly.java`; use whatever returns the cached individual. Confirm `spawnButterflyInWorld`'s exact signature and return type from `ButterflySpeciesType.java` L97-100.)

- [ ] **Step 2: Run test, verify it fails** — without a refresh, `afterSpecies == beforeSpecies` (stale cached instance): FAIL.

- [ ] **Step 3: Implement.** In `EntityButterfly`, add:
```java
/** Re-resolve the cached individual/species from the current live species map after a datapack reload. */
public void refreshSpeciesFromReload() {
    IButterfly current = getButterfly(); // the cached individual (adjust accessor name)
    if (current == null) return;
    // rebuild the individual from its genome so getSpecies() resolves against the NEW live map, fail-soft
    IButterfly refreshed = current.getGenome() /* -> reconstruct individual */;
    setIndividual(refreshed); // re-runs the existing setIndividual side effects (size/fireproof/species cache)
}
```
Use the same reconstruction the entity uses on NBT load (`readAdditionalSaveData` path) — i.e. build a new `Butterfly` from the stored genome (`new Butterfly(genome)` or the species type's individual factory), which re-resolves SPECIES via the current map. Reuse `setIndividual` so all derived state refreshes. In `GeneticsReloadHandler.rebuildButterflySpecies`, after the `setSpecies` swap:
```java
MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
if (server != null) {
    for (ServerLevel level : server.getAllLevels()) {
        for (EntityButterfly entity : level.getEntitiesOfClass(EntityButterfly.class, EVERYWHERE)) {
            entity.refreshSpeciesFromReload();
        }
    }
}
```
(`EVERYWHERE` = a world-bounds `AABB`, or use `level.getAllEntities()` filtered by `instanceof`. Import `net.neoforged.neoforge.server.ServerLifecycleHooks`, `net.minecraft.server.MinecraftServer`, `net.minecraft.server.level.ServerLevel`, `forestry.lepidopterology.entities.EntityButterfly`.)

- [ ] **Step 4: Run test, verify it passes.** Run `runGameTestServer`. Expected: PASS (fresh instance, same id).

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/lepidopterology/entities/EntityButterfly.java src/main/java/forestry/core/genetics/GeneticsReloadHandler.java src/test/java/forestry/gametest/ButterflyEntityReloadTest.java
git commit -m "feat(lepidopterology): refresh loaded butterfly entities on species reload

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Id-keyed butterfly client models

**Files:**
- Modify: `src/main/java/forestry/apiimpl/client/ButterflyClientManager.java` (`IdentityHashMap<IButterflySpecies>` → `Map<ResourceLocation>`)
- Modify: `src/main/java/forestry/lepidopterology/render/ButterflyItemModel.java` (`subModels` → id-keyed; `Loader.read` stop iterating `getAllSpecies()`)
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java` (butterfly client block, L286-306 — build id-keyed from `ClientRegistration` + default convention, no live-species iteration)
- Modify: `src/main/java/forestry/api/client/lepidopterology/IButterflyClientManager.java` (only if the lookup key type needs to change on the interface)

**Interfaces:**
- Produces: `ButterflyClientManager.getTextures(...)` resolves by `species.id()`; the id→texture map is built without iterating the live species list.

- [ ] **Step 1: `ButterflyClientManager` → id-keyed.** Rewrite the backing map to `Map<ResourceLocation, Pair<...>>`; `getTextures(IButterflySpecies species)` looks up `species.id()` (fall back to the default species' textures if absent, mirroring `TreeClientManager.getTint`'s render-time fallback). Read `TreeClientManager` for the exact fallback shape.

- [ ] **Step 2: `ButterflyItemModel`.** Change `subModels` to `Map<ResourceLocation, BakedModel>`; in `Loader.read` (L104-114) resolve models by id and stop iterating `SpeciesUtil.getAllButterflySpecies()` at bake if possible — bake the distinct model locations and resolve by id at render (mirror `ModelSapling`'s id-resolution from Stage 4). If the item model genuinely needs the id set at bake, source ids from the client texture map's keys, not the live species instances.

- [ ] **Step 3: `PluginManager` butterfly block (L286-306).** Build the id-keyed texture map directly from `ClientRegistration.butterflyTextures` (explicit overrides) + the default naming convention (`item/butterfly/<path>`, `textures/entity/butterfly/<path>.png`) for the ids known from registration — WITHOUT iterating `SpeciesUtil.BUTTERFLY_TYPE.get().getAllSpecies()` (empty at client-registration time after Task 10). Read how the Stage-4 tree block in `PluginManager` (L260-292) was rewritten to be id-driven and mirror it. Rebuild on client sync (the sync-packet handler in Task 9 triggers this).

- [ ] **Step 4: Compile + regression.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava runGameTestServer`. No gametest covers client render; the gate is green compile + server tests still pass. Manual in-game check deferred to Task 11.

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/apiimpl/client/ButterflyClientManager.java src/main/java/forestry/lepidopterology/render/ButterflyItemModel.java src/main/java/forestry/apiimpl/plugin/PluginManager.java src/main/java/forestry/api/client/lepidopterology/IButterflyClientManager.java
git commit -m "feat(lepidopterology): id-key butterfly client models for reloadable species

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: `ButterflySpeciesProvider` datagen + generated JSON + equivalence test

**Files:**
- Create: `src/main/java/forestry/core/data/ButterflySpeciesProvider.java`
- Modify: `src/main/java/forestry/core/data/Data.java` (add provider in `gatherData` L61-62; seed in `preDataGen` L80-84)
- Test: `src/test/java/forestry/gametest/ButterflySpeciesEquivalenceTest.java`
- Generated: `src/generated/resources/data/forestry/butterfly_species/*.json` (35 files)

**Interfaces:**
- Consumes: `ButterflySpeciesDefinition.codec` (Task 1).
- Produces: `ButterflySpeciesProvider` (a `DataProvider`) + `static void seedLiveSpeciesForDatagen()`.

- [ ] **Step 1: The provider.** Port `BeeSpeciesProvider` (NOT the tree one — bees do the reference-chromosome instance→id inversion via `RecordingGenomeBuilder`, which butterflies need for cocoon/effect/flower_type). Iterate `LepidopterologyRegistration.forEachSpeciesBuilder` (confirm the method exists on the registration; mirror `ArboricultureRegistration.forEachSpeciesBuilder`/the bee equivalent). For each builder, build a `ButterflySpeciesDefinition` (reading the 12+ genetics/metadata fields + the recorded genome overrides) and write `data/forestry/butterfly_species/<path>.json` via `codec(karyotype)`. Omit fields at their defaults (match how `BeeSpeciesProvider`/`TreeSpeciesProvider` decide what to emit).

- [ ] **Step 2: Wire the provider** in `Data.gatherData` next to `new TreeSpeciesProvider(output, lookup)`:
```java
generator.addProvider(event.includeServer(), new ButterflySpeciesProvider(output, lookup));
```

- [ ] **Step 3: Seed live butterfly species for datagen** in `Data.preDataGen`, next to `TreeSpeciesProvider.seedLiveSpeciesForDatagen();`:
```java
ButterflySpeciesProvider.seedLiveSpeciesForDatagen();
```
(butterflies are empty at real server start; datagen fires no reload, so the live type must be seeded for the provider to read builders / any provider baking a butterfly stack.)

- [ ] **Step 4: Generate.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`. Confirm `src/generated/resources/data/forestry/butterfly_species/` has **35** JSON files. Inspect 2-3 (genus/species/genome present; defaults omitted; `moth: true` only for the 4 moths). Revert the pre-existing `en_us.json`/`.cache` `runData` flake (`git checkout --` those; keep only `butterfly_species/*.json`). Re-run `runData` once to confirm `butterfly_species/*.json` is idempotent (no diff).

- [ ] **Step 5: Equivalence test** `ButterflySpeciesEquivalenceTest` (golden master). Port `TreeSpeciesEquivalenceTest`: for each built-in in the live `BUTTERFLY_TYPE.getAllSpecies()` (old path still active until Task 10), load the generated JSON from the test classpath (`/data/forestry/butterfly_species/<path>.json`), decode via `ButterflySpeciesDefinition.codec()`, project via `ButterflySpeciesProjector.project`, and assert equality on ALL genetics/metadata fields + the full `getChromosomes()` map, EXCEPT complexity (weakened authored-nonzero invariant). Read `TreeSpeciesEquivalenceTest` for the classpath-load + field-compare idiom.

- [ ] **Step 6: Run + commit.** Run `runGameTestServer` (equivalence green). Commit:
```bash
git add src/main/java/forestry/core/data/ButterflySpeciesProvider.java src/main/java/forestry/core/data/Data.java src/generated/resources/data/forestry/butterfly_species src/test/java/forestry/gametest/ButterflySpeciesEquivalenceTest.java
git commit -m "feat(lepidopterology): datagen butterfly_species JSON + code==JSON equivalence test

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Loader + sync wiring (both paths active)

**Files:**
- Create: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesManager.java`
- Create: `src/main/java/forestry/core/network/packets/ButterflySpeciesSyncPacket.java`
- Modify: `src/main/java/forestry/core/ModuleCore.java` (reload listener + packet register + `onDatapackSync`)
- Modify: `src/main/java/forestry/core/network/PacketIdClient.java` (add `BUTTERFLY_SPECIES_SYNC`)
- Test: extend `src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java` (add `managerLoadedAllSpeciesAtServerStart`)

**Interfaces:**
- Consumes: `rebuildButterflySpecies` (Task 4); `ButterflySpeciesDefinition.streamCodec` (Task 1).
- Produces: `ButterflySpeciesManager.INSTANCE` (`SimpleJsonResourceReloadListener`, folder `"butterfly_species"`, `getDefinitions()`); `ButterflySpeciesSyncPacket`.

- [ ] **Step 1: `ButterflySpeciesManager`.** Port `TreeSpeciesManager` verbatim (folder `"butterfly_species"`, volatile def map, `getRegistryLookup()` for decode context, delegate `apply` → `GeneticsReloadHandler.rebuildButterflySpecies`). Read `BeeSpeciesManager`/`TreeSpeciesManager` for the `getRegistryLookup()` reload-context idiom (server is null during initial `WorldLoader.load`).

- [ ] **Step 2: `ButterflySpeciesSyncPacket`.** Port `TreeSpeciesSyncPacket` (encode the def map via `streamCodec`; `handle` no-ops on `Minecraft#hasSingleplayerServer()`, else calls `rebuildButterflySpecies` on the client + rebuilds the id-keyed client models from Task 7).

- [ ] **Step 3: Wire `ModuleCore`.** In `registerReloadListeners` add `ButterflySpeciesManager.INSTANCE` AFTER `TreeSpeciesManager.INSTANCE` and BEFORE the mutation-rebuild listener (species before mutations). Register the packet in `registerPackets`. In `onDatapackSync` also send `ButterflySpeciesSyncPacket`. Add `PacketIdClient.BUTTERFLY_SPECIES_SYNC`.

- [ ] **Step 4: Test.** Add `ButterflySpeciesReloadTest.managerLoadedAllSpeciesAtServerStart` asserting `ButterflySpeciesManager.INSTANCE.getDefinitions().size() == 35` (proof the loader ran at cold start), then snapshot/restore hygiene. Run `runGameTestServer` — full suite green; both the old code path (`buildAll`) and the JSON loader are now active and produce identical output; logs show `Loaded 35 butterfly species` at cold start (add that log line in the manager if the tree manager has one).

- [ ] **Step 5: Commit.**
```bash
git add src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesManager.java src/main/java/forestry/core/network/packets/ButterflySpeciesSyncPacket.java src/main/java/forestry/core/ModuleCore.java src/main/java/forestry/core/network/PacketIdClient.java src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java
git commit -m "feat(lepidopterology): datapack loader + client sync for butterfly species

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Demote the runtime `buildAll` path (cutover) + fail-soft life-stage reads

**Files:**
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesType.java` (`handleSpeciesRegistration` — `buildAll()` → `ImmutableMap.of()`)
- Modify: the four life-stage/render read paths with throwing lookups (audit below)
- Test: extend `src/test/java/forestry/gametest/ButterflySpeciesFallbackTest.java` (`bindinglessDefinitionSkippedNoCrash`)

**Interfaces:**
- Consumes: everything from Tasks 1–9.

- [ ] **Step 1: Fail-soft audit (do BEFORE the demotion).** `grep -rn 'getSpecies\|getButterflySpecies\|resolveActive(ButterflyChromosomes.SPECIES)\|getCocoon\|getButterflyEffect' src/main/java/forestry/lepidopterology src/main/java/forestry/plugin/client/ButterflyAnalyzerPlugin.java`. For each saved/synced/player-facing/render call (the four life-stage item forms `ItemButterflyGE`, `EntityButterfly`, `TileCocoon`, JEI `ButterflyAnalyzerPlugin`, `plantCocoon`), change throwing `getSpecies(id)`/`getCocoon`/`getButterflyEffect` to `getSpeciesSafe`/`getCocoonSafe`/`getButterflyEffectSafe` + `getDefaultSpecies()` fallback. `EntityButterfly` already uses `getSpeciesSafe` for its synced client id (leave). Make `getDefaultSpecies()` itself null-tolerant during the empty window if it currently calls throwing `getSpecies` (check `SpeciesType.getDefaultSpecies()` — if it throws, add a null-tolerant path or ensure the karyotype default is always present post-load). Do NOT change `SpeciesUtil`-style fail-fast helpers used by non-render callers.

- [ ] **Step 2: Write the failing test** `ButterflySpeciesFallbackTest.bindinglessDefinitionSkippedNoCrash`. Port `TreeSpeciesFallbackTest.bindinglessDefinitionSkippedNoCrash`: snapshot live species (finally-restore + `rebuildMutations` per Global Constraints); call `rebuildButterflySpecies` with a def map MISSING one built-in (or containing a def that projects to null), assert the live map excludes it and no exception is thrown, and that reading a removed-species stack via the fail-soft path returns the default rather than throwing.

- [ ] **Step 3: Run test, verify it fails** if any audited path still throws (before Step 1's edits are complete): FAIL. After Step 1, it should pass — sequence Step 1 edits to make Step 2 green.

- [ ] **Step 4: The cutover.** In `ButterflySpeciesType.handleSpeciesRegistration`, change the final `return registration.buildAll();` to `return ImmutableMap.of();` (keep the cocoon/effect registry capture + any companion setup above it). Butterflies now exist ONLY via the datapack loader.

- [ ] **Step 5: Run + verify.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`. Expected: full suite green; `Loaded 35 butterfly species` from the real reload cycle (not `buildAll`); equivalence still green; `MutationRecipeTest` butterfly mutation count intact; `GenomeBaselineTest` green. Commit:
```bash
git add src/main/java/forestry/lepidopterology src/main/java/forestry/plugin/client/ButterflyAnalyzerPlugin.java src/test/java/forestry/gametest/ButterflySpeciesFallbackTest.java
git commit -m "feat(lepidopterology): source butterfly species solely from datapack JSON

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Final verification + review + memory update

**Files:** none (verification) + any fixes surfaced.

- [ ] **Step 1:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava` — green.
- [ ] **Step 2:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData` — `butterfly_species/*.json` idempotent (revert the pre-existing farm/leaf/letter/`.cache` flake).
- [ ] **Step 3:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` — all green, logs show `Loaded 35 butterfly species`; `MutationRecipeTest` (bee/tree/butterfly mutation counts) intact; equivalence, reload, fallback, projector, spawner, entity-refresh tests all pass.
- [ ] **Step 4:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew build` — green (or green except the pre-existing `:test` no-tests-discovered quirk).
- [ ] **Step 5: Manual smoke (if a dev client is available):** spawn/hatch a butterfly (renders + textured per species), edit a generated `butterfly_species/*.json` (e.g. `serumColor` or a genome override) and `/reload` to confirm it applies live; a fresh client receives butterflies on login; a butterfly still spawns from tree leaves after a `/reload` (spawner survived); an in-flight butterfly is still valid after a `/reload` (entity refreshed). If no client, note it and rely on gametests.
- [ ] **Step 6:** Dispatch a code-reviewer over the whole Stage-5 diff (`git diff <stage-5-base>..HEAD`) against the spec. Fix any real issues.
- [ ] **Step 7:** Update memory `data-driven-genetics-overhaul.md` with a "Stage 5 COMPLETE & VERIFIED" section (mechanism, no-bindings-table simplification, spawner-once, entity-refresh, fail-soft, gotchas) mirroring the Stage-4 section; mark the roadmap Stage 5 ✅ and note the remaining deferred follow-ups (flower types, recipe-result id-templating).

---

## Risks & gotchas (read before starting)

- **Generated JSON must exist before Task 10.** Never demote (`buildAll`→empty) before Task 8's JSON is committed, or the game has zero butterflies.
- **Spawner must be reload-safe (Task 5) before the demotion.** After demotion, `onSpeciesRegistered` becomes the reload callback; the spawner must already be out of it (registered once at setup) or handlers duplicate on every reload.
- **Entity refresh (Task 6) needs a running server.** Guard on `getCurrentServer() != null`; initial `WorldLoader.load` has null server + no entities. Iterating `getAllLevels()` entities is fine on `/reload` (rare, few entities).
- **Reference-chromosome instance→id inversion:** cocoon/effect/flower_type are REFERENCE chromosomes — the datagen provider must record them as ids (bee-style, `RecordingGenomeBuilder`), unlike the tree provider. `GenomeBaselineTest` + equivalence catch mistakes.
- **No `ButterflyBlockBindings`:** do not invent one. The entity/cocoon/items are global; the cocoon/effect registries are already captured in `handleSpeciesRegistration`.
- **`getDefaultSpecies()` can throw** during the empty window (it calls `getSpecies(MONARCH)`); ensure fail-soft paths don't depend on it before the loader populates the map, or make it null-tolerant.
- **Test hygiene:** every test mutating the live map MUST snapshot + restore + `rebuildMutations` in `finally` (identity-keyed MutationManager pollution → `MutationRecipeTest` failures).
- **`spawnBiomes` optional tag:** no base species sets it, but the codec must handle presence/absence (`Optional<TagKey<Biome>>`).
- **Don't touch** the two stray `2026-07-01-data-driven-bee-docs*.md` files; revert `runData` en_us/.cache flake.
