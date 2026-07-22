# Data-Driven Butterfly Species — Design Spec (Stage 5)

Final stage of the genetics overhaul. Butterfly **species** become datapack JSON
(`data/<ns>/butterfly_species/*.json`), loaded at world load, live-reloadable on `/reload`, and
client-synced on login — while species **types**/karyotypes and all entity/block/item bindings stay
code-registered and id-bound. Mirrors Stage 4 (trees); see
`docs/superpowers/specs/2026-07-01-data-driven-trees-design.md` and the memory
`data-driven-genetics-overhaul.md`.

## Goal

Make the 35 built-in butterfly species (31 butterflies + 4 moths) load exclusively from generated
datapack JSON with **no behavior change** (golden-master enforced), reloadable and client-synced,
using the same `SimpleJsonResourceReloadListener` + clientbound sync mechanism as bees/trees.

## Locked decisions (this stage)

- **Genetics → JSON, everything else code-registered by id.** JSON carries only the serializable
  genetics/metadata of a species (`ButterflySpeciesDefinition`). The shared entity type, cocoon
  block, life-stage items, and the `cocoon`/`butterfly_effect` reference registries stay in code.
- **No `ButterflyBlockBindings` record.** Unlike trees (per-species generator/leaf blockstates/
  saplings), butterfly non-JSON assets are **global singletons**, not per-species. The projector
  needs only the definition + the per-type cocoon/effect reference registries (already captured in
  `handleSpeciesRegistration`, like trees' fruits/effects). No per-species bindings table exists.
- **`ButterflySpawner` registered once at setup** (not per reload) — see §6.
- **Live butterfly entities are refreshed on reload** (not left stale) — see §7.
- Reference chromosomes (SPECIES/FLOWER_TYPE/EFFECT/COCOON) datagen'd as ids via bee-style
  instance→id inversion (trees didn't need this; butterflies do — `RecordingGenomeBuilder`).
- Missing bindings / removed species → **fail-soft** (log + default fallback), never throw on
  saved/synced/render paths.

## Out of scope (strict genetics migration; keep pre-existing TODOs as-is)

- Implementing the stubbed `products` / `caterpillarProducts` loot (builder returns `List.of()`
  today) — they round-trip faithfully through JSON as empty lists; **no new loot behavior**.
- Wiring the `COCOON` chromosome into `plantCocoon` (currently hardcodes `LepidopterologyBlocks.COCOON`
  with a `// todo`) — behavior stays identical.
- Making the tree type's `leafTickHandlers` list itself reloadable (§6 avoids needing it).
- Any save datafixer (clean break, consistent with Stages 1–4).

## Architecture overview

```
data/<ns>/butterfly_species/<path>.json
        │  (SimpleJsonResourceReloadListener, folder "butterfly_species")
        ▼
ButterflySpeciesManager.apply ──► GeneticsReloadHandler.rebuildButterflySpecies(Map<id,def>)
        │                                   │
        │                                   ├─ ButterflySpeciesProjector.project(type, id, def)
        │                                   │     = DefinitionButterflySpeciesBuilder → ButterflySpecies
        │                                   │       (fail-soft: null+log on bad def/missing refs)
        │                                   ├─ ((SpeciesType) type).setSpecies(projected)   // volatile swap
        │                                   ├─ refresh loaded EntityButterfly species (§7)
        │                                   └─ (client) rebuild id-keyed ButterflyClientManager / item models
        ▼
OnDatapackSyncEvent ──► ButterflySpeciesSyncPacket (clientbound; no-op on single-player)
```

### Component responsibilities

- **`ButterflySpeciesDefinition`** — immutable genetics record + lazy karyotype-bound codec/stream codec.
- **`ButterflySpeciesManager`** — reload listener over `butterfly_species`, volatile def map, delegates.
- **`ButterflySpeciesProjector`** — def (+ captured cocoon/effect registries) → runtime `ButterflySpecies`; fail-soft.
- **`DefinitionButterflySpeciesBuilder`** — read-only `IButterflySpeciesBuilder` adapter over a definition (genetics getters → def; all setters throw).
- **`ButterflySpeciesSyncPacket`** — clientbound sync on login/reload; no-ops when `Minecraft#hasSingleplayerServer()`.
- **`ButterflySpeciesProvider`** — datagen: 35 JSON from `LepidopterologyRegistration.forEachSpeciesBuilder`, with reference-chromosome instance→id inversion.

## §1 The data model: `ButterflySpeciesDefinition`

Record fields (all serializable): base `Species` fields (genus, species, dominant, glint, secret,
complexity, authority, escritoireColor) + `temperature` (`TemperatureType`), `humidity`
(`HumidityType`), `nocturnal` (bool), `moth` (bool), `rarity` (float), `flightDistance` (float),
`serumColor` (int), `spawnBiomes` (`Optional<TagKey<Biome>>`), `products` (`List<IProduct>`),
`caterpillarProducts` (`List<IProduct>`), and sparse `genome` overrides (chromosome id → allele).
Codec mirrors `TreeSpeciesDefinition`/`BeeSpeciesDefinition` (shared base fields duplicated per the
YAGNI decision until a 3rd type — this **is** the 3rd type, so factor a shared
`SpeciesDefinitionBase` if it reduces real duplication; otherwise leave inline). `spawnBiomes` uses
`TagKey.codec(Registries.BIOME)` wrapped in `Optional` (no base species sets it, but the field exists).

## §2 Default-genome composition (reuse)

Reuse `core/genetics/GenomeProjection.applyOverrides` (already shared by bee + tree projectors).
Reference chromosomes (SPECIES/FLOWER_TYPE/EFFECT/COCOON) dispatch via the `ResourceLocation`
set-overload; inline-value chromosomes via the `Allele` overload. `GenomeBaselineTest` guards this.

## §3 No bindings table

The projector takes `(type, id, definition)` and produces a `ButterflySpecies` via the read-only
builder. The only code-side, id-keyed data it consults are the `cocoon`/`butterfly_effect` reference
registries, which `ButterflySpeciesType.handleSpeciesRegistration` already captures into
`this.cocoons` / `this.butterflyEffects` before returning. No new record is introduced.

## §4 Fail-soft resolvers (Task-10 analogue)

- `ButterflyChromosomes.SPECIES` resolver → fail-soft (mirror Stage-3 bee / Stage-4 tree §5): return
  the default species (resolved from the reload map, not the throwing `getDefaultSpecies()`) when an
  id is absent, so `resolveActive(SPECIES)` never throws during the empty-at-setup window.
- Add `getCocoonSafe(id)` / `getButterflyEffectSafe(id)` on `ButterflySpeciesType` (mirror trees'
  `getFruitSafe`); route genome-driven cocoon/effect resolution and any saved/synced/render consumer
  through them with a default fallback.
- Audit the **four life-stage** read/render/JEI paths (butterfly/serum/caterpillar/cocoon items +
  `EntityButterfly` + `ButterflyAnalyzerPlugin`) that call `getSpecies`/`resolveActive(SPECIES)`;
  convert throwing lookups to `getSpeciesSafe` + `getDefaultSpecies()` fallback. `EntityButterfly`
  already uses `getSpeciesSafe` for its synced client id (keep). Make default-species resolution
  itself null-tolerant during the empty window.

## §5 Lifecycle surgery (the demotion)

`ButterflySpeciesType.handleSpeciesRegistration` returns `ImmutableMap.of()` (was
`registration.buildAll()`) after capturing the cocoon/effect registries — butterflies then exist
ONLY via the datapack loader. **No `setSpecies` override needed** (unlike trees): butterflies have no
per-species block/leaf side effects, so the base volatile swap suffices. `onSpeciesRegistered` drops
the spawner-registration line (moved to §6) and becomes a thin `super` delegate.

## §6 `ButterflySpawner` reload-safety (butterfly-specific risk #1)

Today `onSpeciesRegistered` registers a `ButterflySpawner` (an `ILeafTickHandler`) onto the **tree**
type's plain `LinkedList` `leafTickHandlers` (`TreeSpeciesType` L56-57, `// todo make reloadable`).
Once butterfly setup is reload-driven, that would register a **new** spawner every reload → duplicate
handlers. Fix: register the spawner **exactly once at module setup** (a one-time hook, guarded by
`ModuleLepidopterology.spawnButterflysFromLeaves`), decoupled from species loading — the spawner only
needs the live butterfly map populated at *tick* time. This leaves the tree `leafTickHandlers` list
untouched (it already survives tree reloads; we simply never re-add). **Test:** assert exactly one
`ButterflySpawner` on the tree type after a butterfly `/reload`.

## §7 Live-entity staleness (butterfly-specific risk #2)

`EntityButterfly` caches `this.species` + `this.individual` at `setIndividual`. A species reload swaps
the map to fresh instances → loaded entities hold stale ones (breaks identity-keyed `MutationManager`
for a butterfly that mates after a `/reload`). Fix: in the server-side reload path (after the swap),
walk loaded `EntityButterfly` across `getCurrentServer().getAllLevels()` and refresh each one's
individual/species by re-resolving from its in-memory genome (the SPECIES value is a stable id) via
the new map, fail-soft to default if the species was removed. Guard on `getCurrentServer() != null`
(initial world-load has no entities + null server → no-op). Entities loaded *after* a reload already
re-resolve on `readAdditionalSaveData`, so only currently-loaded entities need refreshing. **Test:**
spawn a butterfly, reload species, assert the entity holds the FRESH instance (identity changed) and
is still valid.

## §8 Client models (id-keying)

Mirror `TreeClientManager`: `ButterflyClientManager` moves from `IdentityHashMap<IButterflySpecies>`
to `Map<ResourceLocation>`; `ButterflyItemModel.subModels` becomes id-keyed. `PluginManager` stops
iterating `getAllSpecies()` at client registration and builds an id-keyed texture map from explicit
`ClientRegistration.butterflyTextures` + the default naming convention (`item/butterfly/<path>`,
`textures/entity/butterfly/<path>.png`); rebuilt on client sync. Entity/item render resolves textures
by id (the entity already syncs `DATAWATCHER_ID_SPECIES` and uses `getSpeciesSafe` client-side).

## §9 Datagen

`core/data/ButterflySpeciesProvider` (mirror `BeeSpeciesProvider` — needs the reference-chromosome
instance→id inversion via `RecordingGenomeBuilder`, unlike the tree provider) writes
`data/forestry/butterfly_species/*.json` (35 files, idempotent). Wire into `Data.gatherData` +
`ButterflySpeciesProvider.seedLiveSpeciesForDatagen()` in `preDataGen` (butterflies are empty at real
server start until the datapack loads; datagen fires no reload, so anything baking a butterfly stack
needs the live type seeded).

## §10 Wiring

- `ModuleCore.registerReloadListeners`: add `ButterflySpeciesManager.INSTANCE` after
  `TreeSpeciesManager.INSTANCE` and **before** the mutation-rebuild listener (species before mutations).
- `ModuleCore.onDatapackSync`: also send `ButterflySpeciesSyncPacket`.
- `PacketIdClient.BUTTERFLY_SPECIES_SYNC`; register the packet.
- `GeneticsReloadHandler.rebuildButterflySpecies(Map<id,def>)` alongside `rebuildSpecies`/`rebuildTreeSpecies`.

## §11 Testing

Mirror the tree suite (all `@GameTestHolder(MOD_ID)` `@GameTest(template="empty")`):
- **`ButterflySpeciesEquivalenceTest`** — golden master: code-built (from `DefaultButterflySpecies`)
  == JSON-projected across every field + full `getChromosomes()` map, EXCEPT `complexity` (weakened
  authored-nonzero invariant, bee/tree-precedented).
- **`ButterflySpeciesReloadTest`** — `getDefinitions().size()==35` at cold start; snapshot/restore hygiene.
- **`ButterflySpeciesFallbackTest`** — bindingless/removed species skipped, no crash; the 4 life-stage
  read paths fail-soft.
- **`ButterflySpeciesProjectorTest`** — projection equivalence.
- **`ButterflySpawnerReloadTest`** — exactly one `ButterflySpawner` on the tree type after a butterfly
  reload (§6).
- **`ButterflyEntityReloadTest`** — a spawned entity's species is refreshed to the fresh instance after
  reload (§7).
- **Test hygiene (recurring):** any test mutating the live shared map MUST snapshot + restore in a
  `finally` via `setSpecies(snapshot)` + `rebuildMutations(recipeManager)` (identity-keyed
  MutationManager pollution). Complexity is not byte-comparable (lazy re-derive from MutationManager).

## §12 File map (for planning)

New: `lepidopterology/genetics/{ButterflySpeciesDefinition,ButterflySpeciesManager,ButterflySpeciesProjector,DefinitionButterflySpeciesBuilder}.java`,
`core/network/packets/ButterflySpeciesSyncPacket.java`, `core/data/ButterflySpeciesProvider.java`,
`src/test/java/forestry/gametest/ButterflySpecies{Equivalence,Reload,Fallback,Projector}Test.java` +
`ButterflySpawnerReloadTest.java` + `ButterflyEntityReloadTest.java`.
Edit: `ButterflySpeciesType` (demote + fail-soft resolvers + getCocoonSafe/getButterflyEffectSafe +
entity-refresh + drop spawner line), `ButterflyChromosomes.SPECIES` resolver, `ButterflySpawner`
registration site (module setup), `GeneticsReloadHandler`, `ModuleCore`, `PacketIdClient`,
`ButterflyClientManager`, `ButterflyItemModel`, `PluginManager` (client id-keying), `EntityButterfly`
(refresh + fail-soft), the 4 life-stage item/JEI read paths, `Data.java`, `EntityButterfly`/`ItemButterflyGE`
species reads.

## §13 Success criteria

`compileJava` + `compileTestJava` + `build` green; `runGameTestServer` all green with
`Loaded 35 butterfly species` at cold server start; `runData` idempotent for `butterfly_species/*.json`;
golden-master equivalence proves the 35 built-ins unchanged; one spawner after reload; entities
refreshed after reload; the existing `Loaded 42 tree_species mutation recipes` / bee/tree tests stay green.
