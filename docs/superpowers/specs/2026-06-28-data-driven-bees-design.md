# Data-Driven Bee Species — Design Spec (Stage 3)

**Status:** Approved for planning
**Date:** 2026-06-28
**Branch:** `allele-foundation`
**Predecessors:** Stage 1 (allele foundation), Stage 2 (mutations as recipes) — both complete on this branch.

## Goal

Make bee **species** loadable from datapack JSON (`data/<namespace>/bee_species/*.json`), live-reloadable on `/reload`, and synced to clients on login and after every reload — while keeping species **types** and **karyotypes** code-registered (locked roadmap decision). The ~60 built-in Forestry bees ship as generated JSON. **No behavior change** for default genomes (golden-master enforced).

## Locked decisions (this stage)

1. **Loading & sync mechanism: a custom reloadable manager + a sync packet — NOT a datapack registry, NOT a RecipeType.**
   - Reload: a `SimpleJsonResourceReloadListener` registered via `AddReloadListenerEvent` (runs at world load *and* every `/reload`).
   - Sync: a clientbound packet sent from a NeoForge `OnDatapackSyncEvent` handler (fires on player login *and* after every `/reload`).
   - Result: live add / remove / edit of bee species on `/reload`, plus login sync. `OnDatapackSyncEvent` fires in the **play phase**, so the existing play-phase packet system suffices — no configuration-phase packet support needed.
   - Rationale: a bee species is not recipe-shaped (unlike a mutation), so the Stage 2 RecipeType piggyback is semantically wrong here; and a datapack registry is frozen at world load (no live `/reload`, the same limitation enchantments have in 1.21). The reload-listener + `OnDatapackSyncEvent` pair is the general-purpose tool for reloadable, client-synced, data-driven objects.

2. **The code `registerSpecies` / `registerApiculture` species path is demoted to datagen-only.** At runtime, bee species come **only** from datapacks. The ~60 built-ins ship as generated JSON. Addons add bees by shipping datapack JSON (or by running the public builder API in their own datagen). Clean break — mirrors Stage 2 removing the runtime mutation-registration API. (Companion registries — hives, village bees, flower/effect/activity types, jubilance, swarmer materials — stay code-registered at runtime; see §6.)

3. **Client bee item models: shared tinted models + lazy, id-keyed, bake-on-demand.** Item *colors* are already fully dynamic (resolved per-stack from the species) and need no change. The model bake pipeline is rewritten to resolve/bake per species on demand keyed by species id, cached, with the shared per-stage default model as the universal fallback — replacing the three startup `getAllBeeSpecies()` iterations. No forced client resource reload. Custom non-tinted per-species *textures* remain a resource-pack concern (cannot enter the texture atlas at login without a re-stitch).

4. **Flower types are OUT OF SCOPE for Stage 3** (deferred to a follow-up mini-stage). Bees keep referencing the existing code-registered flower types by `ResourceLocation` (the `FLOWER_TYPE` reference chromosome already stores an id — this already works unchanged).

## Out of scope

- Data-definable flower types (deferred).
- Trees (Stage 4) and butterflies (Stage 5) — but this stage sets the reusable template for them.
- Data-driven species **types**, karyotypes, or taxonomy (stay code).
- Data-driven life stages (`BeeLifeStage` stays a fixed enum).
- Save datafixer / migration (clean break; saved individuals already store genome NBT keyed by species id, which still resolves or fails soft — see §7).
- Custom per-species `IProduct` subclasses (e.g. `FireworkProduct`) and custom per-species `ISpeciesFactory` — not expressible in datapack JSON; datapack bees use plain `Product` and the default `BeeSpecies`.

---

## Architecture overview

```
data/<ns>/bee_species/*.json
        │  (SimpleJsonResourceReloadListener, AddReloadListenerEvent)
        ▼
BeeSpeciesManager  ── parses ──▶  Map<ResourceLocation, BeeSpeciesDefinition>   (server authoritative)
        │                                          │
        │ OnDatapackSyncEvent → BeeSpeciesSyncPacket (StreamCodec<Map<id,Definition>>)
        │                                          ▼
        │                              client receives same Map
        ▼                                          ▼
GeneticsReloadHandler.rebuild(...)  ── projects definitions ──▶ BeeSpecies runtime objects
        │  (1) build BeeSpecies from each Definition (recompose default genome)
        │  (2) BeeSpeciesType.setSpecies(immutableMap)   [volatile swap, like setMutations]
        │  (3) rebuild mutations from RecipeManager       [species-then-mutations ordering]
        ▼
BeeSpeciesType.getAllSpecies() / getSpecies(id)  (unchanged read API)
```

### Component responsibilities

| Component | Responsibility |
|---|---|
| `BeeSpeciesDefinition` (new record) | Pure data: the JSON shape and the sync-packet payload. Carries every builder field + a sparse genome override map. |
| `BeeSpeciesManager` (new, `SimpleJsonResourceReloadListener`) | Parse `data/<ns>/bee_species/*.json` → `Map<id, BeeSpeciesDefinition>`. Server-side authoritative source. Registered via `AddReloadListenerEvent`. |
| `BeeSpeciesSyncPacket` (new, clientbound) | Carries `Map<id, BeeSpeciesDefinition>` to clients. Sent on `OnDatapackSyncEvent`. Sets the client's definition map. |
| `GeneticsReloadHandler` (rename/extend `MutationReloadHandler`) | Single entrypoint, both sides: project definitions → `BeeSpecies` → `BeeSpeciesType.setSpecies`, then rebuild mutations. Guarantees species-before-mutations. |
| `BeeSpeciesType` (modified) | `volatile` reloadable species map + `@ApiStatus.Internal setSpecies(...)`; jubilance registry getter; tolerant `checkSpecies`. |
| `IBeeJubilance` registry (new) | Id-keyed registry of jubilance singletons (`forestry:default`, `forestry:hermit`). |
| `ModelBee` / `ModelBee.Baked.OverrideList` (modified) | Lazy id-keyed per-species bake with default-model fallback. |
| `BeeSpeciesProvider` (new datagen) | Emit `data/forestry/bee_species/*.json` from the existing builders via the codec (Stage-2 `MutationProvider` style). |

---

## §1 The data model: `BeeSpeciesDefinition`

A new immutable record in `forestry.apiculture.genetics` (or `forestry.api.apiculture.genetics`), the registry element and sync payload. Fields mirror the builder. **The runtime `BeeSpecies`/`Species` constructors stay unchanged**: a definition is adapted to `IBeeSpeciesBuilder` so the existing `BeeSpecies::new` field-copy path is reused verbatim (see §3, *Projection detail*).

Fields (and source field on the builder):

| JSON field | Type | Builder source | Notes |
|---|---|---|---|
| `genus` | string | `getGenus()` | resolved to `ITaxon` via `GeneticManager` at projection time |
| `species` | string | `getSpecies()` | scientific epithet |
| `dominant` | bool | `isDominant()` | dominance of the species allele |
| `glint` | bool (opt, default false) | `hasGlint()` | |
| `secret` | bool (opt, default false) | `isSecret()` | |
| `complexity` | int (opt, default 0 = auto) | `getComplexity()` | 0 → derived from breeding-tree depth (existing semantics) |
| `authority` | string (opt, default "Sengir") | `getAuthority()` | |
| `escritoireColor` | int (opt, default -1 = use outline) | `getEscritoireColor()` | sentinel preserved |
| `temperature` | enum string | `getTemperature()` | `TemperatureType` |
| `humidity` | enum string | `getHumidity()` | `HumidityType` |
| `body` | int color (opt) | `getBody()` | default `0xffdc16` |
| `stripes` | int color (opt) | `getStripes()` | default `0` |
| `outline` | int color | `getOutline()` | from `registerSpecies` param |
| `products` | `List<Product>` (opt) | `buildProducts()` | reuses `Product.CODEC` |
| `specialties` | `List<Product>` (opt) | `buildSpecialties()` | reuses `Product.CODEC` |
| `jubilance` | id (opt, default `forestry:default`) | `getJubilance()` → id | see §5 |
| `genome` | sparse map `chromosomeId → Allele` (opt) | flattened `setGenome` lambda | see §2 |

### Codec & stream codec (karyotype-bound, lazy)

Both the `Codec<BeeSpeciesDefinition>` and `StreamCodec<RegistryFriendlyByteBuf, BeeSpeciesDefinition>` are built **lazily** (resolved on first use), because the bee karyotype is not available at mod-bus registration time — the exact pattern `MutationRecipe.Serializer` uses in Stage 2. The `genome` field reuses the proven `Codec.dispatchedMap(karyotype.chromosomeKeyCodec(), chrom -> Allele.codec(chrom.valueCodec()))` and its stream-codec analogue.

## §2 Default-genome composition (refactor + reuse)

Today `SpeciesRegistration.buildAll()` composes each species' default genome in order:
1. `karyotype.createGenomeBuilder()`
2. apply each parent taxon's default alleles (reference chromosomes by id; data chromosomes inline)
3. set the `SPECIES` chromosome to `AllelePair.both(new Allele(id, dominant))`
4. `setRemainingDefault()`
5. apply the per-species `setGenome(Consumer<IGenomeBuilder>)` overrides

**Refactor:** extract steps 1–4 + "apply an override map" into a reusable method (e.g. `Karyotype`/a genetics util) that takes `(speciesType, speciesId, dominant, Map<chromosome, Allele> overrides)` and returns an `IGenome`. The JSON loader (projection) calls it with the JSON's sparse override map; datagen flattening (§8) and the existing buildAll both route through it.

**Why store only the sparse overrides** (not the full genome): keeps JSON minimal and lets taxon/karyotype default changes propagate. Unspecified chromosomes fall back to taxon then karyotype defaults — identical to today.

## §3 Loading lifecycle (server)

- `BeeSpeciesManager` (a `SimpleJsonResourceReloadListener` over folder `bee_species`) is registered through `AddReloadListenerEvent` in `ModuleCore`. Its `apply` populates the server's `Map<id, BeeSpeciesDefinition>`.
- The existing Stage 2 mutation reload listener (also `AddReloadListenerEvent`, apply phase, game executor) is generalized into `GeneticsReloadHandler.rebuild(...)`, which:
  1. reads the loaded definition map,
  2. projects each definition → `BeeSpecies` (via §2), collects an `ImmutableMap<id, IBeeSpecies>`, validates references (§7), and calls `BeeSpeciesType.setSpecies(map)`,
  3. rebuilds mutations from the `RecipeManager` (Stage 2 logic), which reads `getAllSpecies()` — now guaranteed populated first.
- Both the species-manager listener and the rebuild must be ordered so the definition map is parsed before the rebuild projects it. Simplest: one combined apply step (parse definitions, then project, then mutations) or two listeners whose apply phases are sequenced on the game executor. (Reload listeners' apply phases run in registration order behind the prep barrier — register the species manager before the mutation-rebuild listener.)

**Projection detail (definition → `BeeSpecies`):** `BeeSpecies`'s only constructor is `BeeSpecies(id, IBeeSpeciesType type, IGenome defaultGenome, IBeeSpeciesBuilder builder)` and both it and `Species` copy every remaining field from the builder. To keep those constructors untouched, projection wraps a `BeeSpeciesDefinition` (plus the composed default genome from §2) in a small **`DefinitionBeeSpeciesBuilder` adapter** that implements the getter surface of `IBeeSpeciesBuilder`/`ISpeciesBuilder` (delegating to the definition) and throws/no-ops the setters. `BeeSpecies::new` then runs verbatim. (A dedicated definition-taking constructor is an acceptable alternative; the adapter is preferred because it reuses the existing field-copy logic with zero edits to `BeeSpecies`/`Species`.)

## §4 Sync lifecycle (client)

- Server: an `OnDatapackSyncEvent` handler sends `BeeSpeciesSyncPacket(Map<id, BeeSpeciesDefinition>)` to `event.getRelevantPlayers()` (the joining player on login; all players after `/reload`).
- Client: the packet handler stores the definition map, then runs `GeneticsReloadHandler.rebuild(...)` client-side: project → `setSpecies` → rebuild mutations (the mutation index already arrives via vanilla recipe sync / `RecipesUpdatedEvent`).
- **Ordering robustness:** the client may receive `BeeSpeciesSyncPacket` and `RecipesUpdatedEvent` in either order. Make mutation rebuild idempotent and re-runnable; both the species packet handler (after `setSpecies`) and `RecipesUpdatedEvent` trigger a mutation rebuild. Whichever lands last yields the correct state (one redundant cheap rebuild at worst).
- The genome stream codec already encodes the species chromosome as a `ResourceLocation`; with definitions synced before play interactions, client-side species resolution matches the server.

## §5 Jubilance registry

`IBeeJubilance` becomes id-addressable (the single per-species Java callback):
- Add an id-keyed registry on the apiculture registration path: `registerBeeJubilance(ResourceLocation, IBeeJubilance)` + `BeeSpeciesType.getJubilance(id)` (mirroring `getBeeEffect`/`getFlowerType`/`getActivityType`).
- Built-ins: `forestry:default` (`DefaultBeeJubilance`, temp/humidity match) and `forestry:hermit` (`HermitBeeJubilance`). Both stateless singletons → referenced in JSON by id only.
- Species JSON: `"jubilance": "forestry:hermit"`, defaulting to `forestry:default`.
- One **parameterized** jubilance already exists in code — `RequiresResourceBeeJubilance` (built per-instance via the public `IJubilanceFactory.getRequiresResource(BlockState...)`) — but **no built-in bee uses it** (built-ins use only the two stateless singletons). Under the clean-break posture it stays code-only/unused API; an addon that constructed one in code breaks (consistent with §6). An id-keyed singleton registry is therefore correct for the no-behavior-change goal.
- A full dispatch-codec (à la `MutationConditionTypes`) is **not** needed now and is the future extension **if** a parameterized jubilance ever needs to be datapack-expressible (e.g. exposing `requires_resource` with its block states in JSON).

## §6 Lifecycle surgery (the demotion)

- `BeeSpeciesType` (via base `SpeciesType`): species map becomes `volatile`, empty until projected, swapped by an `@ApiStatus.Internal setSpecies(...)` — exactly how `mutations` already works. `checkSpecies()`/`getDefaultSpecies()` tolerate "not yet projected" gracefully (no hard throw during the window before first projection; callers already have safe variants from Stage 1).
- `DefaultBeeSpecies.register(...)` (the ~60 `registerSpecies` chains) is **no longer called at runtime** — it becomes input to datagen only (§8).
- `DefaultForestryPlugin.registerApiculture(...)` keeps registering the **companion datasets** at runtime (hives, village bees, flower/effect/activity types, the new jubilance registry, swarmer materials) — these are code-registered by id and referenced by species JSON. Ordering note: these must be registered before/independently of species projection so reference lookups succeed.
- `handleSpeciesRegistration` keeps capturing the companion reference-value maps and the hive manager, but no longer builds species (returns nothing for species, or is split so the species-building half is gone).
- `onSpeciesRegistered` is reworked: it no longer freezes a one-shot immutable species map at setup. (Anything previously triggered there — e.g. for butterflies, leaf-tick handler registration — is reviewed and moved to a setup hook independent of species population. Bees have no such side effect today.)
- The unused `ForestryRegistries.SPECIES_TYPE` registry is left as-is or removed (decide during planning; not load-bearing).

## §7 Validation & fallback

- **Per-species validation at projection:** a definition referencing a missing `jubilance` id, an undefined genus taxon, or an unknown chromosome in its genome map is **logged and skipped** (fail-soft, like Stage 2 mutations skip bad recipes) rather than crashing the load. The species simply doesn't appear.
- **Missing default species:** if `karyotype.getDefaultSpecies()` is absent from the projected map, log an error; downstream default-individual creation uses the safe path. (Built-in datagen guarantees the default exists; this guards only broken datapacks.)
- **Stale saved references:** a saved individual whose species id was removed from datapacks resolves via `getSpeciesSafe()` → null; display/analyzer paths fall back to the default bee species (extends Stage 1's nullable-getter hardening). `getSpecies()` (throwing) callers are audited and switched to safe+fallback where they handle player-facing/saved data.
- **Empty species (no datapack present in dev/test):** the system tolerates zero bee species (empty map) without crashing; gameplay simply has no bees until JSON loads.

## §8 Datagen

A new `BeeSpeciesProvider` following the Stage-2 `MutationProvider` shape (plain codec-to-JSON, **not** `RegistrySetBuilder`/`DatapackBuiltinEntriesProvider`):
- `preDataGen()` already runs the real plugin registration, so the builders are live and expose read-back getters (`getGenus`/`getSpecies`/`isDominant`/`getTemperature`/`getHumidity`/`getBody`/`getStripes`/`getOutline`/`buildProducts`/`buildSpecialties`/`getAuthority`/`isSecret`/`hasGlint`/`getJubilance` + `buildGenome(IGenomeBuilder)`).
- For each registered bee builder: read fields, run `buildGenome` against a **recording `IGenomeBuilder`** to capture the sparse override map (the lambda only issues `set()` calls), assemble a `BeeSpeciesDefinition`, and write `data/forestry/bee_species/<id>.json` via the codec.
- Wire into `Data.gatherData` (alongside the existing providers).
- **Lang:** existing manual keys (`allele.forestry.bee_species.bee_*` and `.desc`) stay manual (they already exist; manual wins in `generateEnUsLang`). Datapack-only species ship their own lang. (Optionally generate them later via `ForestryEnglishProvider`; not required this stage.)
- **No per-species item-model/blockstate datagen** is needed — bees are 4 life-stage items keyed by `BeeLifeStage`, tinted dynamically.

## §9 Client models (detail)

- **Unchanged:** the single shared `FORESTRY_ITEM_COLOR` handler and `ItemBeeGE.getColorFromItemStack` (reads `body`/`stripes`/`outline` from the stack's species per render). Datapack bees tint correctly with zero new registration.
- **Rewritten:** `ModelBee.Baked.OverrideList.resolve(...)` resolves the model for the stack's species id, **baking on demand and caching** (keyed by species id), using the shared per-stage default model as the universal fallback. The three startup iterations over `getAllBeeSpecies()` (`PluginManager.registerClient`, `CoreClientHandler.additionalBakedModels`, `ModelBee.bake`) are removed/neutralized so an empty species list at model-load time is fine.
- `BeeClientManager` / `ForestryClientApiImpl.getBeeManager()` are adapted so they no longer require a fully-populated species list at client setup; custom per-species model overrides (today only `VANILLA → *_cube`) are resolved by id from the (still code-registered) client model registration, independent of the species objects.
- Transient appearance before the species sync packet arrives: the existing hardcoded fallback color path remains acceptable.

## §10 Testing

- **Golden master (must stay green):** `GenomeBaselineTest` — default genomes built from generated JSON (projected) are byte-for-byte identical to the pre-existing baseline. This is the core no-behavior-change guarantee.
- **New equivalence gametest:** for every built-in bee, assert the JSON-projected `BeeSpecies` equals the code-built one (colors, climate, products, specialties, jubilance id, dominant, glint, secret, complexity, authority, escritoire, default genome).
- **New gametests:** species map populated after projection (~60); `BeeSpeciesDefinition` codec + stream-codec round-trips; missing-species fail-soft (skip) + fallback-to-default for a stale reference; datagen count == code count; `getAllSpecies()` never throws (empty-tolerant).
- All run under `runGameTestServer` (`env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9`).
- Stage 2's `MutationRecipeTest` must still pass (114/42/1) — verifies the species-then-mutations ordering and that mutations still find their (now data-driven) species.

## §11 Risks / notes

- **Reload ordering (server):** parsing definitions and projecting must precede mutation rebuild within the same reload. Sequence the apply phases on the game executor.
- **Reload ordering (client):** species packet vs `RecipesUpdatedEvent` race — mitigated by idempotent, re-runnable mutation rebuild triggered from both (§4).
- **`AddReloadListenerEvent.getRegistryAccess()`** availability is used by the mutation path already; the species manager needs no registry access (pure JSON).
- **Reference registries must be populated before projection:** flower/effect/activity/jubilance are code-registered at setup (before any world load), so they're always present when projection runs — preserves the "registries before resolution" invariant.
- **Addon migration:** code-registering addons break at runtime (species API demoted). This is the intended clean break; document the datapack-JSON path for addons.

## §12 File map (for planning)

New:
- `forestry/apiculture/genetics/BeeSpeciesDefinition.java` — record + lazy codec + lazy stream codec.
- `forestry/apiculture/genetics/BeeSpeciesManager.java` — `SimpleJsonResourceReloadListener`.
- `forestry/apiculture/network/BeeSpeciesSyncPacket.java` (or under `core/network`) — clientbound sync packet.
- `forestry/core/data/BeeSpeciesProvider.java` — datagen.
- Jubilance registry plumbing (registration method + `BeeSpeciesType.getJubilance`).
- Gametests in `forestry/gametest/` (equivalence, codec, fallback, populated, count).

Modified:
- `forestry/core/genetics/mutations/MutationReloadHandler.java` → `GeneticsReloadHandler` (species-then-mutations).
- `forestry/apiculture/genetics/BeeSpeciesType.java` + `forestry/core/genetics/SpeciesType.java` — `setSpecies`, volatile map, tolerant checks.
- `forestry/apiimpl/plugin/SpeciesRegistration.java` — extract reusable genome compose.
- `forestry/plugin/DefaultForestryPlugin.java` / `DefaultBeeSpecies.java` — demote species building to datagen; keep companion registries.
- `forestry/apiimpl/plugin/PluginManager.java` — `registerGenetics` (~L186–188): drop/relocate the `onSpeciesRegistered` setup call and remove the `if (getAllSpecies().isEmpty()) throw …` guard (datapack species aren't loaded at setup — this would otherwise throw); plus `registerClient` (lazy id-keyed bake, below).
- `forestry/core/ModuleCore.java` — register `BeeSpeciesManager`; `OnDatapackSyncEvent` handler.
- `forestry/core/client/CoreClientHandler.java` — client packet rebuild; neutralize startup species iteration.
- `forestry/apiculture/models/ModelBee.java` + `forestry/apiimpl/client/BeeClientManager.java` + `forestry/apiimpl/plugin/PluginManager.java` (`registerClient`) — lazy id-keyed bake.
- `forestry/core/data/Data.java` + recipe/english providers — wire datagen.
- `forestry/api/apiculture/IBeeJubilance.java` + impls — id-registry hooks.

## §13 Success criteria

1. `compileJava` + `compileTestJava` + `./gradlew build` green.
2. `runGameTestServer` green: golden master + new gametests + Stage 2's `MutationRecipeTest` (114/42/1).
3. `runData` produces `data/forestry/bee_species/*.json` for all ~60 built-ins; generated == code-built (equivalence test).
4. In-game: bees load from datapack JSON, render correctly (tinted), breed via mutations, survive `/reload` (add/remove/edit live), and a fresh client receives them on login.
5. No save-data corruption for existing genomes referencing built-in species; stale references fail soft.
