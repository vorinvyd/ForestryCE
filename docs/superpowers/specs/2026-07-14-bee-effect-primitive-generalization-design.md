# Bee-effect primitive generalization — design

**Date:** 2026-07-14
**Branch:** `imakebadchoices-pr/data-driven-bee-primitives` (MC 1.21.1)
**Status:** approved design, pre-implementation

## Goal

The data-driven-bee-primitives work registers 16 bee-effect primitive types, but only **4 of
them have a built-in consumer** (`apply_potion`, `damage_entities`, `resurrect`, `aging` — the
10 generated JSON files). The other 12 are constructed nowhere outside `BeeEffectSystemTest`.

Meanwhile four bespoke effects that are near-misses for those very primitives stayed
code-registered in `DefaultForestryPlugin`, each blocked on one or two missing knobs.

This design closes both gaps:

1. **Generalize** `transform_block` and `damage_entities` so HEROIC, SIFTER, GLACIAL and
   GLOW_BERRY_GROW migrate to datapack JSON.
2. **Delete** the 11 primitive types that still have no built-in consumer afterwards. They are
   addon material, recoverable from git history.

## Principle

- **A primitive earns its place in base Forestry only if a built-in effect consumes it.**
  Speculative primitives are addon material.
- **Data-driven means configurable.** A no-property singleton type (a `MapCodec.unit` wrapper
  plus a JSON file containing only `"type"`) exposes no knobs, adds a datapack load-order
  dependency, and is not data-driven in any useful sense. Genuinely bespoke effects
  (REPULSION, GUARDIAN, SCULK, CREEPER, RADIOACTIVE, PHASING, FERTILE, IGNITION, SNOWING,
  MYCOPHILIC, EXPLORATION, ASCENSION, HAKUNA_MATATA, NONE, EASTER) **stay code-registered**.
- The dual load path is deliberate and stays. `GeneticsReloadHandler.rebuildBeeEffects` merges
  the code-registered base under the datapack overlay, and that merge is needed for
  KubeJS/addons regardless. `IBeeEffect.codec()` already documents this: *"code-only base
  effects never pass through the datapack registry, so they inherit the throwing default."*

## Architecture

### 1. `ThrottleSettings` — the common fields become a requirement

Audit of what the 16 primitive codecs expose today:

| Field | Exposed to codec |
|---|---|
| `dominant` | 16 of 16 |
| `throttle` | 15 of 16 |
| `combinable` | **1 of 16** (only `DamageBeeEffect`) |
| `requires_working` | **0 of 16** — every primitive hardcodes it |

`requires_working` being hardcoded everywhere is precisely why HEROIC could not migrate. Making
these fields mandatory for every `ThrottledBeeEffect`-derived primitive both unblocks the
migrations and lets packs retune existing effects.

New record, alongside `ThrottledBeeEffect`:

```java
public record ThrottleSettings(boolean dominant, int throttle, boolean requiresWorking, boolean combinable) {
    /** Per-primitive defaults: each primitive's historical hardcoded values. */
    public static MapCodec<ThrottleSettings> codec(int defThrottle, boolean defRequiresWorking, boolean defCombinable) { ... }
}
```

Used as a **group entry**, a `MapCodec` splices its fields flat into the parent object, so JSON
stays flat — no nested `"settings"` block:

```java
public static final MapCodec<PotionBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    ThrottleSettings.codec(200, true, false).forGetter(ThrottledBeeEffect::settings),
    MobEffect.CODEC.fieldOf("effect").forGetter(...),
    ...
).apply(instance, PotionBeeEffect::new));
```

Defaults are per-primitive because the historical hardcoded values differ:

| Primitive | `throttle` | `requires_working` | `combinable` |
|---|---|---|---|
| `apply_potion` | 200 | `true` | `false` |
| `resurrect` | 40 | `true` | `true` |
| `damage_entities` | 40 | `false` | (already exposed, default `true`) |
| `transform_block` | 20 | `false` | `false` |

**Because each default equals today's hardcoded value, existing generated JSON stays
byte-identical.** Only the four new effects emit the new fields.

`ThrottledBeeEffect`'s existing 4-arg constructor is kept, delegating to the record, so the ~12
surviving bespoke code-only subclasses need no changes. `PotionBeeEffect`'s convenience
constructors are likewise kept for `AscensionBeeEffect` and `PotionBeeEffectExclusive`.

`aging` is **out of scope for this rule**: `AgingBeeEffect` extends `NonStackingBeeEffect`,
which implements `IBeeEffect` directly and carries only `dominant` — it uses a tracked-owner
mechanism rather than throttling. It has no throttle to expose.

### 2. `forestry:transform_block`

Rewritten. It has zero consumers today, so its semantics are free to redefine.

```json
{
  "type": "forestry:transform_block",
  "dominant": false, "throttle": 200,
  "requires_working": true, "combinable": false,
  "attempts": 10, "chance": 1.0,
  "max_temperature": "normal",
  "transforms": [
    { "from": "minecraft:water", "to": { "Name": "minecraft:ice" }, "requires_air_above": true }
  ]
}
```

- **`from`** becomes a `HolderSet<Block>` — a block or a tag (was a single `Block`).
- **`to`** becomes either a `BlockState` (as today) or `{"set": {"berries": "true"}}`, a
  property mutation applied to the *current* state, preserving its other properties.
- **`attempts`** (default 1) is the number of positions sampled per activation; **every**
  sampled match is transformed. The old "search up to 16 positions, transform the first match"
  semantic is dropped — nothing consumes it, and GLACIAL needs 10 independent transforms.
- **`max_temperature`** (optional) skips the activation when the housing is warmer than the
  given `TemperatureType`. Inclusive.
- **`requires_air_above`** (per-transform, default false) requires the block above the sampled
  position to be air.

**Sampling.** `transform_block` adopts the `Bee.getParticleArea` + `VecUtil.center(area)`
pattern that SIFTER, GLACIAL and GLOW_BERRY_GROW already share (all three sample identically
today; GLACIAL merely hoists the centre out of its loop). This makes all three migrations
bit-identical.

It does **not** use `ThrottledBeeEffect.getRandomPositionInRange`, whose javadoc notes it
samples a symmetric box to avoid `getBounding`'s upward skew. That helper was written for the
speculative primitives being deleted here; adopting it would silently make SIFTER and GLACIAL
(both ground-targeting) far more effective — a balance change riding along inside a refactor.
With the dead primitives gone, `getRandomPositionInRange` and `findPositionInRange` lose their
last callers and are removed.

**The identity guard.** A single check — *skip when the resulting state equals the current
state* — subsumes two bespoke exclusions for free:

- SIFTER's `block != Blocks.COARSE_DIRT`: `#minecraft:dirt` contains `coarse_dirt` (verified
  against `minecraft_1.21.1_client.jar`), but coarse dirt → coarse dirt is the identity.
- GLOW_BERRY_GROW's `!state.getValue(BERRIES)`: setting `berries=true` on an already-berried
  vine is the identity.

It also avoids a redundant `setBlockAndUpdate` firing spurious neighbour updates (observers,
redstone) on a no-op write.

### 3. `forestry:damage_entities`

- **`players_only` is replaced by `target`**, either a builtin enum or a tag:
  - `"target": "all"` → `LivingEntity` (default)
  - `"target": "players"` → `Player`
  - `"target": "monsters"` → `Monster`
  - `"target": {"tag": "#c:bosses"}` → entity-type tag
  
  The enum branch is required because `Monster` is a *class*: it catches modded monsters
  automatically, and no vanilla entity-type tag is equivalent. A tag-only filter would silently
  narrow HEROIC. `players_only` is new in this unmerged PR, so replacing it costs no compat.
- **`requires_working`** arrives via `ThrottleSettings`.

### 4. The four migrations

Every value is read directly off the existing `super(...)` call, so each migration is
behaviour-preserving.

| Effect | dominant | throttle | requires_working | combinable | rest |
|---|---|---|---|---|---|
| HEROIC | false | 40 | true | false | `damage 2, armor_scaling false, chance 1.0, target monsters, damage_type forestry:heroic` |
| SIFTER | true | 550 | true | true | `attempts 1, chance 1.0, from #minecraft:dirt, to coarse_dirt` |
| GLACIAL | false | 200 | true | false | `attempts 10, chance 1.0, max_temperature normal, water→ice, requires_air_above` |
| GLOW_BERRY_GROW | false | 200 | true | true | `attempts 1, chance 1.0, from #minecraft:cave_vines, to {set:{berries:true}}` |

`max_temperature: "normal"` is exactly equivalent to GLACIAL's `isWarmerOrEqual(WARM)` skip,
given `ICY(0) COLD(1) NORMAL(2) WARM(3) HOT(4) HELLISH(5)`.

`#minecraft:cave_vines` = `[cave_vines_plant, cave_vines]`, verified against
`minecraft_1.21.1_client.jar`.

### 5. Deletions

**11 primitives with no built-in consumer**, class file plus registry line each:
`spawn_mob`, `feed`, `firework`, `strike_lightning`, `teleport`, `entity_force`, `bonemeal`,
`spawn_projectile`, `place_block`, `fill_fluid`, `inject_energy`.

Blast radius is contained: these classes are referenced only by their own files, the 11
registration lines in `ApicultureBeeEffectTypes`, and 8 `LightningBeeEffect` references in
`BeeEffectSystemTest`. Nothing else in the codebase touches them.

**4 bespoke classes now expressed as JSON:** `HeroicBeeEffect`, `SifterBeeEffect`,
`GlacialBeeEffect`, `GlowBerryGrowEffect`, plus their four `registerBeeEffect` lines.

Surviving primitives: `apply_potion`, `damage_entities`, `transform_block`, `resurrect`,
`aging`.

## Migration & compatibility

- **`ForestryBeeEffects` is unchanged.** HEROIC, GLACIAL, SIFTER and GLOW_BERRY_GROW remain
  valid effect IDs referenced by bee species JSON; only their *registration* moves from code to
  datapack. No genome or species migration.
- **Existing generated JSON is byte-identical** — new codec fields default to the historical
  hardcoded values. Generated file count goes 10 → 14.
- **No public API breakage.** The effect implementations live in
  `forestry.apiculture.genetics.effects` (impl); `IBeeEffect` and
  `ForestryRegistries.Keys.BEE_EFFECT_TYPE` are API and unaffected.
- **One accepted behaviour delta:** GLOW_BERRY_GROW currently matches
  `state.hasProperty(BERRIES)`, so it affects *any* block carrying that property, including
  modded ones. Tag-based `from` narrows it to the two vanilla cave-vine blocks. Accepted: the
  old check was accidental scope rather than intent, and packs can extend the tag.

## Testing

`BeeEffectSystemTest`:

- `allEffectPrimitiveTypesRegistered` — update to the 5 survivors.
- `effectDefinitionRoundTrips` and `datapackEffectsMergeOntoBuiltins` — currently built on
  `LightningBeeEffect`. Rebuild both on `transform_block`, which now has the richest codec
  (HolderSet `from`, either-branch `to`, property-set) and is the better round-trip subject.
- `damageEntitiesBuiltinsAreDatapackDefined` — fold in HEROIC.
- **New** `transformBlockBuiltinsAreDatapackDefined` — SIFTER, GLACIAL, GLOW_BERRY_GROW,
  mirroring the existing `*BuiltinsAreDatapackDefined` pattern.

Worth asserting explicitly, since they are the subtle parts:

- the identity guard (coarse dirt is not rewritten; an already-berried vine is not rewritten);
- `attempts` semantics (10 attempts can produce up to 10 transforms);
- that `ThrottleSettings` defaults leave existing JSON byte-identical.

## File inventory

**Deleted (15):**
`SpawnMobBeeEffect`, `FeedBeeEffect`, `FireworkBeeEffect`, `LightningBeeEffect`,
`TeleportBeeEffect`, `EntityForceBeeEffect`, `BonemealBeeEffect`, `ProjectileBeeEffect`,
`PlaceBlockBeeEffect`, `FillFluidBeeEffect`, `InjectEnergyBeeEffect`, `HeroicBeeEffect`,
`SifterBeeEffect`, `GlacialBeeEffect`, `GlowBerryGrowEffect`.

**Modified:**
- `ThrottledBeeEffect` — add `ThrottleSettings`; remove `getRandomPositionInRange` and
  `findPositionInRange`.
- `TransformBlockBeeEffect` — rewrite per §2.
- `DamageBeeEffect` — `target`, `ThrottleSettings`.
- `PotionBeeEffect`, `ResurrectionBeeEffect` — `ThrottleSettings`.
- `ApicultureBeeEffectTypes` — remove 11 registrations.
- `DefaultForestryPlugin` — remove 4 `registerBeeEffect` lines.
- `BeeEffectProvider` — add 4 entries.
- `BeeEffectSystemTest` — per Testing.

**New generated JSON (4):** `bee_effect_heroic.json`, `bee_effect_sifter.json`,
`bee_effect_glacial.json`, `bee_effect_glow_berry_grow.json`.

**Unchanged:** `AgingBeeEffect` (`NonStackingBeeEffect` line), `ForestryBeeEffects`.

## Out of scope / follow-ups

- **`max_temperature` should become a range.** Deliberately deferred as extra code; a comment
  on the field will record the intent. GLACIAL only needs an upper bound.
- **`ForestryBeeEffects.PATRIOTIC` is registered nowhere**, in neither the plugin nor the
  provider. Pre-existing, unrelated to this work.
- **EXPLORATION** (grant 2 XP to players in range) could become a small `give_experience`
  primitive with an `amount` field. Not included: one consumer, and no near-miss to justify it.
- The deleted primitives are recoverable from git history for an addon.
