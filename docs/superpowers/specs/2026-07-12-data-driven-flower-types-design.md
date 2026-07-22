# Data-driven flower types — design

**Date:** 2026-07-12
**Branch base:** `1.21.1` (genetics overhaul stages 1–5 complete)
**Status:** approved design, pre-implementation

## Goal

Make bee **flower types** data-definable from datapack JSON — loaded at world load,
client-synced, reloadable — while keeping the flower-type **serializers** (the behavioural
kinds) code-driven. This is the deferred follow-up called out in the data-driven-genetics
overhaul ("Basic data-definable flower types deferred to a follow-up").

Deliver exactly three serializers, as requested:

- `forestry:tag_flower_type`
- `forestry:water_tag_flower_type`
- `forestry:photosynthesis_flower_type`

## Principle

Mirror the overhaul's split — **types code-driven, instances data-driven**:

- The three **serializers** are registered in code (like `MutationConditionType` /
  species types).
- The ~15 forestry flower-type **instances** (`vanilla`, `nether`, … with their tag
  parameters) become datapack JSON under `data/<namespace>/flower_type/`.
- Flower-type IDs are **unchanged** (`forestry:flower_type_vanilla`, …), so existing
  `FLOWER_TYPE` reference-chromosome values in every bee species genome stay valid with no
  migration.

## Architecture

### 1. Serializers (code) — mirrors `MutationConditionType`

New API record (next to `IFlowerType` in `forestry.api.apiculture`):

```java
public record FlowerTypeType<T extends IFlowerType>(
    MapCodec<T> codec,
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

`IFlowerType` gains one method:

```java
FlowerTypeType<?> type();   // default: throw UnsupportedOperationException
```

The default throw is deliberate: **only serializable (datapack/synced) flower types need a
`type()`.** Code-only flower types that hold behaviour as lambdas (`KubeFlowerType`) are never
encoded, so they never call it.

A static registry `FlowerTypeTypes` (in `forestry.apiculture.genetics`), copied structurally
from `MutationConditionTypes`:

- `BY_ID` / `ID_OF` concurrent maps, `register(id, type)`, `byId(id)`.
- `Codec<IFlowerType> CODEC` = `ResourceLocation.CODEC.dispatch("type", IFlowerType::type-id, id -> byId(id).codec())`.
- `StreamCodec<RegistryFriendlyByteBuf, IFlowerType> STREAM_CODEC` (id + dispatched value), same shape as the mutation-condition stream codec.
- `synchronized registerBuiltins()` (idempotent) registering the three types, called from
  `PluginManager` (before any parse) and from `GeneticsReloadHandler` as an idempotent safety net.

`IFlowerType.CODEC` / `IFlowerType.STREAM_CODEC` delegate to `FlowerTypeTypes`.

### 2. The three serializers

| type id | class | fields | behaviour |
|---|---|---|---|
| `forestry:tag_flower_type` | `TagFlowerType` (replaces `FlowerType`, absorbs `EndFlowerType`) | `flowers`: block `TagKey`; `dominant`: bool; **optional** `biomes`: biome `TagKey` (default absent) | accept if `(biomes present && biome ∈ biomes) OR block ∈ flowers`; plant in an empty position (current `FlowerType.plantRandomFlower`) |
| `forestry:water_tag_flower_type` | `WaterTagFlowerType extends TagFlowerType` | same as tag | same accept logic; plantable position overridden to "block is water" |
| `forestry:photosynthesis_flower_type` | `PhotosynthesisFlowerType` (kept) | none (`MapCodec.unit`) | accept if day && sky-light ≥ 15; never plants; `dominant` = false |

`TagKey` codecs: `TagKey.codec(Registries.BLOCK)` / `TagKey.codec(Registries.BIOME)`; stream
via `ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(reg, rl), TagKey::location)`.

Merging `EndFlowerType` into `TagFlowerType` (via the optional `biomes` tag) is the approved
resolution for the `END` flower — it becomes:

```json
{ "type": "forestry:tag_flower_type", "flowers": "forestry:end_flowers", "dominant": false, "biomes": "minecraft:is_end" }
```

Other examples:

```json
{ "type": "forestry:water_tag_flower_type", "flowers": "forestry:sea_flowers", "dominant": false }
{ "type": "forestry:photosynthesis_flower_type" }
```

### 3. Runtime: load, layer, resolve

**Datapack loader** — `FlowerTypeManager extends SimpleJsonResourceReloadListener` over the
`flower_type` folder (structural copy of `BeeSpeciesManager`):

- singleton `INSTANCE`; `volatile Map<ResourceLocation, IFlowerType> definitions = Map.of()`.
- `apply()` decodes each entry via `IFlowerType.CODEC` under a `RegistryOps` built from
  `getRegistryLookup()` (same reasoning as `BeeSpeciesManager`), fail-soft (log + skip a bad
  file), then `GeneticsReloadHandler.rebuildFlowerTypes(definitions)`.
- `setDefinitions(map)` client mirror, populated by the sync packet.
- registered as a **server** reload listener in `ModuleCore#registerReloadListeners`; the client
  never registers it (no datapack access), it only holds synced data.

**Code / datapack layering (the KubeJS accommodation):**

- `registerFlowerType(id, IFlowerType)` **stays**, but is now reserved for *code-registered*
  flower types — addon and **KubeJS** (`ApicultureEventJS.registerFlowerType` →
  `KubeFlowerType`). These form a **code base map**, captured once at
  `handleSpeciesRegistration`. They run on both client and server (plugin phase), are never
  serialized, and are never synced.
- Forestry's own 15 built-ins are **removed** from `DefaultForestryPlugin` and become
  **datapack-only** (datagen'd — see §4).
- The effective runtime map used by `BeeSpeciesType.getFlowerType` =
  **code base ∪ datapack, datapack wins** on ID conflict. `rebuildFlowerTypes(dataMap)`
  recomputes and installs this union via `BeeSpeciesType.setFlowerTypes(effective)`.
- Bootstrap: `handleSpeciesRegistration` installs the code base alone as the initial effective
  map (so resolution never sees an empty map before the first datapack load); the first
  `rebuildFlowerTypes` then replaces it with base ∪ datapack.
- Result: forestry built-ins are fully pack-overridable/removable (datapack-only, like
  species); KubeJS/addon code types survive `/reload` (they live in the code base, which the
  datapack layers over, not replaces).

**Client sync** — `FlowerTypeSyncPacket(Map<ResourceLocation, IFlowerType> definitions)`
(structural copy of `BeeSpeciesSyncPacket`):

- stream codec = `ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, IFlowerType.STREAM_CODEC)`.
- sent from `ModuleCore`'s `OnDatapackSyncEvent` listener **before** `BeeSpeciesSyncPacket`
  (so flower types exist before any client-side genome materialisation reads
  `IFlowerType::isDominant`).
- `handle()` skips on an integrated server (`hasSingleplayerServer()`) — shared singletons are
  already authoritative — then on a remote client: `FlowerTypeManager.INSTANCE.setDefinitions`
  + `GeneticsReloadHandler.rebuildFlowerTypes`.
- new client packet id in `PacketIdClient` (`FLOWER_TYPE_SYNC`).

**Resolution** — unchanged: `BeeChromosomes.FLOWER_TYPE` still resolves
`id -> SpeciesUtil.BEE_TYPE.get().getFlowerType(id)` with dominance `IFlowerType::isDominant`.
`getFlowerType` / `getFlowerTypeSafe` read the swapped effective map.

### 4. Datagen

New `FlowerTypeProvider` (registered in `Data.java`'s `GatherDataEvent`):

- Holds a single static list of the 15 built-in definitions (id → `IFlowerType`), constructed
  directly (not read from registration, since built-ins no longer register at runtime).
- Encodes each via `IFlowerType.CODEC` (registry-aware ops) to
  `data/forestry/flower_type/flower_type_<name>.json` (file name = the ID path, preserving IDs
  such as `forestry:flower_type_vanilla`).

The 15 built-ins reproduce today's `DefaultForestryPlugin` table exactly (same tags, same
dominance), with `END` expressed as `tag_flower_type` + `biomes = #minecraft:is_end`, and
`SEA` / `CORAL` as `water_tag_flower_type`.

## Migration & compatibility

- **Delete** `EndFlowerType`; **rename/refactor** `FlowerType` → `TagFlowerType` and
  `WaterFlowerType` → `WaterTagFlowerType` (both now carry `type()` + a `MapCodec`).
- `DefaultForestryPlugin`: **remove** the 15 `registerFlowerType` calls (moved to datagen).
- `BeeSpeciesType`: `flowerTypes` becomes settable (`setFlowerTypes`); stop seeding it from
  registration in `handleSpeciesRegistration` (the reload/union fills it); keep the code base
  from registration for the union.
- `BeeAnalyzerPlugin`: `instanceof FlowerType` → `instanceof TagFlowerType`.
- `KubeFlowerType`: compiles unchanged against the new interface (inherits the throwing
  `type()` default; it is code-only and never serialized). No KubeJS behaviour change.
- IDs unchanged → **no genome/save migration**, consistent with the overhaul's clean-break-but-ID-stable stance.

## Testing

Reuse the existing `runGameTestServer` harness (same one that guards the overhaul):

- **`FlowerTypeTest`** gametest:
  - codec round-trips (JSON **and** stream) for one instance of each serializer, including
    `END`'s optional `biomes` field (present) and a `tag_flower_type` without `biomes` (absent).
  - all 15 built-in IDs resolve through `getFlowerType` after load, with the expected
    `isDominant()` and serializer class.
  - behaviour spot-checks: a `tag_flower_type` accepts a tagged block; `water_tag_flower_type`
    reports a water position plantable and a non-water one not; `photosynthesis_flower_type`
    accepts under day+skylight and rejects otherwise; `END` accepts a block in an End biome
    regardless of the block tag.
  - datapack layering: a synthetic `data/.../flower_type` entry overrides a built-in ID and
    wins; a code-registered (KubeJS-style) ID survives a `rebuildFlowerTypes` call.
- **Datagen check:** `runData` emits 15 `flower_type/*.json`; each decodes back to an instance
  equal in tag/dominance to the pre-change code table.
- `compileJava` + `compileTestServer` green; full `runGameTestServer` green.

## File inventory

**New**
- `api/apiculture/FlowerTypeType.java`
- `apiculture/genetics/FlowerTypeTypes.java`
- `apiculture/TagFlowerType.java`, `apiculture/WaterTagFlowerType.java`
- `apiculture/genetics/FlowerTypeManager.java`
- `core/network/packets/FlowerTypeSyncPacket.java`
- `core/data/FlowerTypeProvider.java`
- `src/test/.../FlowerTypeTest.java`
- `data/forestry/flower_type/*.json` (generated, 15)

**Modified**
- `api/apiculture/IFlowerType.java` (add `CODEC`, `STREAM_CODEC`, `type()`)
- `apiculture/PhotosynthesisFlowerType.java` (add `type()` + unit codec)
- `apiculture/genetics/BeeSpeciesType.java` (`setFlowerTypes`, union, stop seeding from registration)
- `apiimpl/plugin/ApicultureRegistration.java` (code-base semantics for `getFlowerTypes`)
- `core/genetics/GeneticsReloadHandler.java` (`rebuildFlowerTypes`, `FlowerTypeTypes.registerBuiltins` safety net)
- `core/ModuleCore.java` (register reload listener; send `FlowerTypeSyncPacket` before bee species)
- `core/network/PacketIdClient.java` (`FLOWER_TYPE_SYNC`)
- `apiimpl/plugin/PluginManager.java` (`FlowerTypeTypes.registerBuiltins()`)
- `core/data/Data.java` (register `FlowerTypeProvider`)
- `plugin/DefaultForestryPlugin.java` (remove built-in registrations)
- `plugin/client/BeeAnalyzerPlugin.java` (`instanceof TagFlowerType`)

**Deleted**
- `apiculture/EndFlowerType.java`
- `apiculture/FlowerType.java`, `apiculture/WaterFlowerType.java` (renamed to the `*Tag*` classes)

## Out of scope / follow-ups

- Data-driving KubeJS flower types (they remain code/lambda-based, unserializable — unchanged).
- Tree/butterfly flower analogues (bees only carry `FLOWER_TYPE`).
- Adding new serializer kinds beyond the three requested.
