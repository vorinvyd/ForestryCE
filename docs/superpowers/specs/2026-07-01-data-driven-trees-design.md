# Data-Driven Tree Species — Design Spec (Stage 4)

**Status:** Approved for planning
**Date:** 2026-07-01
**Branch:** `allele-foundation`
**Predecessors:** Stage 1 (allele foundation), Stage 2 (mutations as recipes), Stage 3 (data-driven bees) — all complete on this branch.

## Goal

Make tree **species** loadable from datapack JSON (`data/<namespace>/tree_species/*.json`), live-reloadable on `/reload`, and synced to clients on login and after every reload — while keeping species **types**, **karyotypes**, and all **block/item/worldgen bindings** code-registered (locked roadmap decision: trees are data-driven *at the genetics layer only*). The 50 built-in Forestry trees ship as generated JSON. **No behavior change** for default genomes (golden-master enforced).

This is the direct sibling of Stage 3 (data-driven bees) and reuses that template wholesale. This spec documents only the tree-specific deltas in detail; anywhere it says "mirror bees", follow `docs/superpowers/specs/2026-06-28-data-driven-bees-design.md` and its implementation on this branch.

## Locked decisions (this stage)

1. **Loading & sync mechanism: reuse the Stage-3 pair** — a `SimpleJsonResourceReloadListener` (`TreeSpeciesManager`, folder `tree_species`) registered via `AddReloadListenerEvent` + a clientbound sync packet (`TreeSpeciesSyncPacket`) sent from `OnDatapackSyncEvent`. Gives live add/remove/edit on `/reload` plus login sync. Identical rationale to Stage 3 §1.

2. **The genetics/bindings split (the tree-specific core decision).** A tree species carries two kinds of data:
   - **Genetics-layer data** (trivially serializable) → datapack JSON: genus, species, dominant, glint, secret, complexity, authority, escritoireColor, temperature, humidity, `rarity`, and the sparse `genome` override map.
   - **Block/worldgen bindings** (not JSON-serializable; "code-registered & ID-bound" per the roadmap) → a **code-side table keyed by species id**: the `ITreeGenerator` (a `FeatureXxx::new` worldgen lambda), vanilla leaf `BlockState`s, vanilla sapling `Item`s, the decorative-leaves `ItemStack`, and the `IWoodType`.

   Unlike bees — where `DefaultBeeSpecies.register` was demoted **fully** to datagen-only — `DefaultTreeSpecies.register(...)` **keeps running at runtime**, because the worldgen/block bindings must exist at runtime. It no longer *builds species*; it only populates the code-side bindings table. The projector merges JSON genetics + code-side bindings into a runtime `TreeSpecies`. This mirrors how Stage 3 kept companion registries (hives/jubilance) code-side while the genetics went to JSON.

3. **Missing bindings → fail-soft skip.** A tree definition (e.g. an addon shipping only JSON) with no code-side binding for its id at projection is logged and skipped — it does not appear. Addons that add trees must register their worldgen/blocks in code anyway (inherent to this design; worldgen features and block registration cannot live in a datapack here) and ship the genetics JSON alongside. Consistent with Stage 3's fail-soft posture (unknown jubilance/chromosome/genus → skip).

## Out of scope

- Data-definable worldgen, blocks, items, wood types, leaf types, fruit blocks (stay code-registered & ID-bound — the roadmap decision).
- Data-driven species **types**, karyotypes, or taxonomy (stay code).
- Butterflies (Stage 5) — but this stage must not break the butterfly→tree leaf-tick coupling (see §4).
- Save datafixer / migration (clean break; saved individuals store genome NBT keyed by species id, which resolves or fails soft).
- Custom per-species `IFruit`/`ITreeEffect`/`ITreeGenerator` subclasses beyond what's already code-registered — these stay code (referenced by id from JSON via the FRUIT/EFFECT reference chromosomes; the generator via the bindings table).
- The deferred Stage-3 follow-up (recipe results bake the full genome into the stack) applies equally to sapling/pollen stacks (`ISpecies.createStack`) — **noted, not fixed here** (same deferral as bees; own design pass later).

---

## Architecture overview

```
data/<ns>/tree_species/*.json                     code (DefaultTreeSpecies.register at runtime)
        │  (SimpleJsonResourceReloadListener)              │
        ▼                                                  ▼
TreeSpeciesManager ── parses ──▶ Map<id, TreeSpeciesDefinition>   TreeBlockBindings: Map<id → {generator,
        │                                 │                        leafStates, saplingItems,
        │ OnDatapackSyncEvent →           │                        decorativeLeaves, woodType}>
        │ TreeSpeciesSyncPacket           │                                    │
        ▼                                 ▼                                    │
GeneticsReloadHandler.rebuildSpecies(...) ── TreeSpeciesProjector.project ◀────┘
        │  (1) merge JSON genetics + code-side bindings → TreeSpecies (recompose default genome)
        │  (2) TreeSpeciesType.setSpecies(immutableMap)   [volatile swap]
        │  (3) rebuildMutations from RecipeManager         [species-then-mutations ordering]
        ▼
TreeSpeciesType.getAllSpecies() / getSpecies(id)  (unchanged read API)
```

### Component responsibilities

| Component | Responsibility | Bee analog |
|---|---|---|
| `TreeSpeciesDefinition` (new record) | Pure genetics-layer data: JSON shape + sync payload. Lazy karyotype-bound codec/stream-codec. | `BeeSpeciesDefinition` |
| `TreeSpeciesManager` (new, `SimpleJsonResourceReloadListener`) | Parse `data/<ns>/tree_species/*.json` → `Map<id, TreeSpeciesDefinition>`. Server authoritative. | `BeeSpeciesManager` |
| `TreeSpeciesSyncPacket` (new, clientbound) | Carry the definition map to clients. Sent on `OnDatapackSyncEvent`. | `BeeSpeciesSyncPacket` |
| `TreeBlockBindings` (new, code-side) | Id-keyed table of worldgen/block bindings, captured at `handleSpeciesRegistration`. | *(none — bees have no worldgen)* |
| `DefinitionTreeSpeciesBuilder` (new adapter) | Read-only `ITreeSpeciesBuilder` over `(definition, bindings, jubilance-analogs)`. Setters throw. | `DefinitionBeeSpeciesBuilder` |
| `TreeSpeciesProjector` (new) | Merge JSON + bindings → runtime `TreeSpecies`, fail-soft. | `BeeSpeciesProjector` |
| `GeneticsReloadHandler` (generalized) | Project both bee AND tree definitions → `setSpecies`, then rebuild mutations. | (extend existing) |
| `TreeSpeciesType` (modified) | Capture bindings in `handleSpeciesRegistration`; return empty species map; reload-safe `onSpeciesRegistered`. | `BeeSpeciesType` |
| `ModelSapling` (modified) | Lazy id-keyed bake, empty-tolerant. | `ModelBee` |
| `TreeSpeciesProvider` (new datagen) | Emit `data/forestry/tree_species/*.json` from the builders. | `BeeSpeciesProvider` |

---

## §1 The data model: `TreeSpeciesDefinition`

A new immutable record in `forestry.arboriculture.genetics`, the registry element and sync payload. Fields carry **only the genetics layer** (block/worldgen bindings live in the code-side table, §3). The runtime `TreeSpecies`/`Species` constructors stay unchanged — a definition + bindings are adapted to `ITreeSpeciesBuilder` via `DefinitionTreeSpeciesBuilder` (§4) so `TreeSpecies::new`'s field-copy path is reused verbatim.

Fields (and source getter on the builder):

| JSON field | Type | Builder source | Notes |
|---|---|---|---|
| `genus` | string | `getGenus()` | resolved to `ITaxon` at projection |
| `species` | string | `getSpecies()` | scientific epithet |
| `dominant` | bool | `isDominant()` | species allele dominance |
| `glint` | bool (opt, default false) | `hasGlint()` | |
| `secret` | bool (opt, default false) | `isSecret()` | |
| `complexity` | int (opt, default 0 = auto) | `getComplexity()` | 0 → derived from breeding depth |
| `authority` | string (opt, default "Sengir") | `getAuthority()` | |
| `escritoireColor` | int (opt, default -1) | `getEscritoireColor()` | sentinel preserved |
| `temperature` | enum string | `getTemperature()` | `TemperatureType` (reuse `ClimateCodecs`) |
| `humidity` | enum string | `getHumidity()` | `HumidityType` (reuse `ClimateCodecs`) |
| `rarity` | float (opt, default per builder) | `getRarity()` | worldgen spawn weight, but a trivial float → genetics-layer JSON |
| `genome` | sparse map `chromosomeId → Allele` (opt) | recorded `setGenome` lambda | see §2 |

**Note vs bees:** no `body`/`stripes`/`outline`/`products`/`specialties`/`jubilance` fields. Trees have no sprite palette (leaves are tinted by client-registered sprites keyed by id) and **do not store products on the species** — products resolve at runtime from the `FRUIT` reference chromosome. There is no per-tree Java callback analogous to `IBeeJubilance`, so **no jubilance registry is needed** for trees.

### Codec & stream codec (karyotype-bound, lazy)

Both built lazily against the tree karyotype (not available at mod-bus registration — same pattern as `BeeSpeciesDefinition`/`MutationRecipe`). The `genome` field reuses the shared `core/genetics/GenomeCodecs` allele-map codec/stream-codec (already extracted in Stage 3; no duplication). Tree karyotype = `IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(ForestrySpeciesTypes.TREE).getKaryotype()`.

## §2 Default-genome composition (reuse)

Reuse `SpeciesRegistration.createDefaultGenomeBuilder(karyotype, speciesId, genus, dominant)` (extracted in Stage 3 Task 4, guarded by `GenomeBaselineTest`) verbatim — it is species-type-agnostic. The projector calls it, then applies the JSON's sparse override map via the same reference-vs-value dispatch (`BeeSpeciesProjector.applyOverrides` logic — reference chromosomes `SPECIES`/`FRUIT`/`EFFECT` use the `set(IChromosome<ResourceLocation>, ResourceLocation)` overload; inline value chromosomes `HEIGHT`/`SAPLINGS`/`YIELD`/`SAPPINESS`/`MATURATION`/`GIRTH`/`FIREPROOF` use the `Allele` overload). Store only sparse overrides (JSON minimal; taxon/karyotype default changes propagate) — identical rationale to Stage 3 §2.

## §3 The code-side bindings table (`TreeBlockBindings`)

The tree-specific piece with no bee analog.

- A new immutable record `TreeBlockBindings(ITreeGenerator generator, List<BlockState> vanillaLeafStates, List<Item> vanillaSaplingItems, ItemStack decorativeLeaves, IWoodType woodType)` — every non-serializable field a `TreeSpecies` needs beyond genetics.
- Populated by `DefaultTreeSpecies.register(...)`, which **keeps running at runtime** (still called from `DefaultForestryPlugin.registerArboriculture`). Rather than building species, `TreeSpeciesType.handleSpeciesRegistration` now iterates the registered `ITreeSpeciesBuilder`s and captures a `Map<ResourceLocation, TreeBlockBindings>` from each builder's block/worldgen getters (`getGenerator`/`getVanillaLeafStates`/`getVanillaSaplingItems`/`getDecorativeLeaves`/`getWoodType` — add read-back getters where missing), then returns `ImmutableMap.of()` for species (mirroring bees returning empty).
- The companion reference registries (fruits, tree effects) stay captured exactly as today; they back the `FRUIT`/`EFFECT` reference chromosomes and must be populated before projection (they are — code-registered at setup).
- **Addon path:** an addon adds a tree by (a) code-registering its worldgen/blocks and its `TreeBlockBindings` (via the same builder API, still runtime), and (b) shipping the genetics JSON. A JSON with no matching binding → skipped (§ decision 3).

## §4 Projection (definition + bindings → `TreeSpecies`)

- `DefinitionTreeSpeciesBuilder` implements the getter surface `TreeSpecies`/`Species` read: genetics getters delegate to the `TreeSpeciesDefinition`; block/worldgen getters delegate to the `TreeBlockBindings`; all setters and `buildGenome`/`createSpeciesFactory` throw `UnsupportedOperationException("datapack species builder is read-only")`. (Grounding: enumerate exactly what `TreeSpecies`/`Species` constructors copy and implement only those — the map notes `TreeSpecies` reads generator/vanillaLeafStates/vanillaSaplingItems/decorativeLeaves/rarity/temperature/humidity, `Species` reads genus/species/dominant/complexity/authority/escritoireColor/glint/secret.)
- `TreeSpeciesProjector.project(type, id, def)`: look up `TreeBlockBindings` for `id`; if absent → log + skip (return null). Resolve karyotype, `createDefaultGenomeBuilder` + `applyOverrides`, build genome, `new TreeSpecies(id, type, genome, new DefinitionTreeSpeciesBuilder(def, bindings))`. Wrap in try/catch → log + null on any exception (fail-soft, Stage 3 §7).

## §5 Fail-soft `SPECIES` resolver

`TreeChromosomes.SPECIES`'s resolver currently hard-calls `TREE_TYPE.getSpecies(id)` (throws on unknown). Make it fail-soft with a default fallback exactly like Stage 3 did for `BeeChromosomes.SPECIES` (`resolveSpeciesOrDefault`): unknown/removed id → default tree species (+ log), so a stale saved individual referencing a removed datapack tree resolves gracefully instead of crashing.

## §6 Reload-safety of `onSpeciesRegistered` side effects (tree-specific risk)

Trees become reloadable, so the one-shot setup side effects must become idempotent and rerunnable on every species swap:

- **`TreeSpeciesType.onSpeciesRegistered`** rebuilds `vanillaIndividuals` (BlockState→individual) and `vanillaItems` (Item→individual) from every species' bindings, and wires `ForestryLeafType → ITreeSpecies`. Currently it throws `IllegalStateException` if a leaf type's backing species id is absent. Change: rebuild these maps on every swap (they carry `// todo make reloadable`); the `ForestryLeafType` wiring must **tolerate a temporarily-missing species** (log a warning, leave the back-ref null/last-known) — the species map is empty until the datapack loads, so a hard throw would crash startup. Downstream leaf rendering/behavior must handle a null back-ref (audit; fall back to default species).
- **`ButterflySpeciesType.onSpeciesRegistered`** calls `TREE_TYPE.registerLeafTickHandler(new ButterflySpawner())`. A tree reload must not drop or double-register this. Make `registerLeafTickHandler` **idempotent** (e.g. dedupe by handler class/identity, or keep the handler list separate from anything a tree reload clears). Since butterflies are not data-driven this stage, their registration still happens once at setup — the requirement is only that a *tree* `/reload` preserves the already-registered butterfly handler. Simplest: the leaf-tick handler list is independent of the species swap (never cleared by `setSpecies`/`onSpeciesRegistered`), so it survives untouched.

## §7 Lifecycle surgery (the demotion)

- `TreeSpeciesType` (via base `SpeciesType`): species map already becomes reloadable via the Stage-3 `volatile` map + `@ApiStatus.Internal setSpecies` (that change is in the shared base — verify it applies to trees). `handleSpeciesRegistration` keeps capturing companion reference maps (fruits/effects) + building the `TreeManager`/hive-analog, additionally captures `TreeBlockBindings`, and returns `ImmutableMap.of()` for species.
- `DefaultTreeSpecies.register(...)` **stays called at runtime** (§3) — it is the source of bindings. It becomes datagen input **as well** (§8), reading the same builders for the genetics half.
- `DefaultForestryPlugin.registerArboriculture(...)` unchanged in structure: still registers fruits/effects, still calls `DefaultTreeSpecies.register`. (Contrast bees, where the `DefaultBeeSpecies.register` call was removed.)
- `PluginManager.registerGenetics`: the empty-species throw guard was already removed in Stage 3; confirm trees returning empty at setup is tolerated (it is — bees already do).

## §8 Datagen

`TreeSpeciesProvider` mirrors `BeeSpeciesProvider` (vanilla `DataProvider`, codec-to-JSON, registry-aware `RegistryOps`):
- `preDataGen()` runs real plugin registration, so builders are live with read-back getters. Add any missing genetics read-back getters to `ITreeSpeciesBuilder`/`TreeSpeciesBuilder` (`getRarity` etc.).
- For each registered tree builder: read genetics fields, run `buildGenome` against a `RecordingGenomeBuilder` (Stage-3 shared class) to capture the sparse override map, assemble a `TreeSpeciesDefinition`, encode via the codec, write `data/forestry/tree_species/<id>.json`. **Bindings are not emitted** (code-side).
- Wire into `Data.gatherData` alongside `BeeSpeciesProvider`.
- **Datagen seeding:** if any recipe/loot RESULT bakes a tree stack (Stage-3 found `sapling.createStack` in grafter loot, villagers, pollen), datagen may need the live tree type seeded (mirror `BeeSpeciesProvider.seedLiveSpeciesForDatagen`). **Prefer** id-referencing where already done (Stage 3 refactored bee loot to `OrganismFunction.fromId`); audit tree loot/recipes and reference-by-id where feasible, else seed. Determine the exact set during planning.
- **Lang:** existing manual tree species keys stay manual (manual wins in `generateEnUsLang`).

## §9 Client models (detail)

- **`ModelSapling.bake`** currently iterates `getAllTreeSpecies()` into `IdentityHashMap<ITreeSpecies, BakedModel>` (item + block) and the `Baked` ctor does `getSpecies(OAK)` + `requireNonNull` — the exact Stage-3 startup-bake hazard. Rewrite to **lazy id-keyed bake-on-demand** with a default-species fallback, tolerating an empty species list at model-load time (mirror `ModelBee.Baked.OverrideList.resolve`). Remove/neutralize the `requireNonNull(getSpecies(OAK))`.
- **Leaf sprites & tints** are already client-registered by species id (`DefaultForestryClientRegistration.registerArboriculture` → `setLeafSprite`/`registerSapling`/tints) — they survive as long as ids are stable, no change needed.
- **`ModelLeaves`/`ModelDefaultLeaves`/`ModelDecorativeLeaves`** resolve per-species via `getTreeManager().getLeafSprite(species)` with `getDefaultSpecies()` fallback — audit that the fallback path tolerates the empty window (species-empty until datapack loads); harden if it can NPE.

## §10 Testing

- **Golden master (must stay green):** `GenomeBaselineTest` — default tree genomes built from generated JSON (projected) byte-for-byte identical to the pre-existing baseline. Core no-behavior-change guarantee.
- **New equivalence gametest** `TreeSpeciesEquivalenceTest`: for every built-in tree, assert the JSON-projected `TreeSpecies` equals the code-built one — genus/species/dominant/glint/secret/complexity/authority/escritoireColor/temperature/humidity/rarity, the resolved bindings (generator identity, vanilla leaf states, sapling items, decorative leaves, wood type), and **default genome** (active+inactive per chromosome).
- **New gametests:** species map populated after projection (50); `TreeSpeciesDefinition` codec + stream-codec round-trips; fail-soft skip (definition with no code-side binding, and unknown chromosome, are dropped without crashing); datagen count == code count; `getAllSpecies()` empty-tolerant (never throws); leaf-tick handler survives a species swap (butterfly `ButterflySpawner` still registered after `rebuildSpecies`).
- Stage 2's `MutationRecipeTest` must still pass (114 bee / **42 tree** / 1 butterfly) — verifies species-then-mutations ordering and that tree mutations find their now-data-driven species.
- All under `runGameTestServer` (`env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9`).

## §11 Risks / notes

- **Bindings must be captured before projection.** `DefaultTreeSpecies.register` runs at plugin setup (before any world load), so the bindings table is always populated when the first reload projects. Preserves the "registries before resolution" invariant.
- **`ForestryLeafType` back-ref window.** Between startup and the first datapack load, tree species are empty, so leaf-type back-refs are null. Rendering/behavior touching leaf-type→species must tolerate null (fall back to default). This is the highest-risk audit surface.
- **Butterfly coupling (Stage 5 not done yet).** The butterfly leaf-tick handler registered on the tree type must survive tree reloads (§6). Keep the handler list independent of the species swap.
- **Reload ordering** (server + client): same as Stage 3 — parse/project species before mutation rebuild; client sync packet vs `RecipesUpdatedEvent` race mitigated by idempotent mutation rebuild. `GeneticsReloadHandler` generalization must preserve both orderings for both bees and trees.
- **Single-player identity churn.** Stage 3's `BeeSpeciesSyncPacket.handle` no-ops on an integrated server (`hasSingleplayerServer()`) to avoid rebuilding identity-keyed `MutationManager` state. `TreeSpeciesSyncPacket` must do the same.
- **Addon migration:** code-registering addons that expected `buildAll()` to construct tree species break at runtime (species now come from JSON). Intended clean break; document the split (register bindings in code + ship genetics JSON).

## §12 File map (for planning)

New:
- `forestry/arboriculture/genetics/TreeSpeciesDefinition.java` — record + lazy codec + lazy stream codec.
- `forestry/arboriculture/genetics/TreeBlockBindings.java` — code-side bindings record.
- `forestry/arboriculture/genetics/TreeSpeciesManager.java` — `SimpleJsonResourceReloadListener`.
- `forestry/arboriculture/genetics/DefinitionTreeSpeciesBuilder.java` — read-only builder adapter.
- `forestry/arboriculture/genetics/TreeSpeciesProjector.java` — merge + fail-soft projection.
- `forestry/core/network/packets/TreeSpeciesSyncPacket.java` — clientbound sync packet.
- `forestry/core/data/TreeSpeciesProvider.java` — datagen.
- Gametests in `forestry/gametest/` (equivalence, codec, fail-soft, populated, count, leaf-tick-survival).
- Generated: `src/generated/resources/data/forestry/tree_species/*.json`.

Modified:
- `forestry/core/genetics/GeneticsReloadHandler.java` — generalize `rebuildSpecies` to project trees too (or add a tree-typed sibling), preserving species-then-mutations.
- `forestry/arboriculture/genetics/TreeSpeciesType.java` — capture `TreeBlockBindings` + return empty species in `handleSpeciesRegistration`; reload-safe `onSpeciesRegistered`; idempotent `registerLeafTickHandler`.
- `forestry/api/genetics/alleles/TreeChromosomes.java` — fail-soft `SPECIES` resolver.
- `forestry/api/plugin/ITreeSpeciesBuilder.java` + `forestry/apiimpl/plugin/TreeSpeciesBuilder.java` — read-back getters for bindings + genetics fields (`getGenerator`/`getVanillaLeafStates`/`getVanillaSaplingItems`/`getDecorativeLeaves`/`getWoodType`/`getRarity`).
- `forestry/lepidopterology/genetics/ButterflySpeciesType.java` — ensure `registerLeafTickHandler` idempotent / survives tree reload (may need no change if handler list is swap-independent).
- `forestry/arboriculture/models/ModelSapling.java` — lazy id-keyed bake, empty-tolerant.
- `forestry/arboriculture/models/ModelLeaves.java` / `ModelDefaultLeaves.java` / `ModelDecorativeLeaves.java` — audit empty-window fallback.
- `forestry/core/ModuleCore.java` — register `TreeSpeciesManager`; send `TreeSpeciesSyncPacket` on `OnDatapackSyncEvent`; register packet.
- `forestry/core/network/PacketIdClient.java` — add `TREE_SPECIES_SYNC`.
- `forestry/core/client/CoreClientHandler.java` — client packet rebuild (species then mutations).
- `forestry/core/data/Data.java` — wire `TreeSpeciesProvider`.
- `forestry/plugin/DefaultForestryPlugin.java` — `registerArboriculture` unchanged in structure (keeps calling `DefaultTreeSpecies.register`).

## §13 Success criteria

1. `compileJava` + `compileTestJava` + `./gradlew build` green.
2. `runGameTestServer` green: golden master + new tree gametests + Stage-2 `MutationRecipeTest` (114/42/1); logs `Loaded 50 tree species`.
3. `runData` produces `data/forestry/tree_species/*.json` for all 50 built-ins, idempotent; generated == code-built (equivalence test).
4. In-game: trees load from datapack JSON, render (leaves tinted, saplings modeled), grow via their generator, breed via mutations, survive `/reload` (add/remove/edit live), and a fresh client receives them on login. Butterfly leaf-spawning still works after a `/reload`.
5. No save-data corruption for existing genomes referencing built-in tree species; stale references fail soft.
