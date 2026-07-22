# Shared `SpeciesDefinition` Foundation — Design Spec (deferred follow-up)

A pure-refactor, deferred follow-up to the data-driven genetics overhaul (Stages 1–5 complete on
`allele-foundation`). The three data-driven species definitions
(`{Bee,Tree,Butterfly}SpeciesDefinition`) and their supporting classes were intentionally written
inline/triplicated during the migration; the whole-branch review flagged the triplication cost as
now high. This spec factors out the shared structure. **No behavior change** — golden-master
(`*EquivalenceTest`, `GenomeBaselineTest`), byte-identical JSON, and byte-identical sync packets are
enforced throughout.

## Goal

Remove the four triplicated structures across bees/trees/butterflies while keeping the three
definition records **flat** (so `def.genus()` and the datagen providers are untouched) and preserving
serialization bytes exactly:

1. **Definition codecs** — 10 identical base `fieldOf(...)` lines in each `buildCodec`, repeated twice
   more in each stream codec (encode + decode).
2. **Read-only builder adapters** — ~22 identical method bodies each (10 base getters + 12 throwing
   base setters/factory methods).
3. **Projectors** — the identical `createDefaultGenomeBuilder → applyOverrides → build` skeleton.
4. **Test literals** — 14 positional 12–20-arg constructor sites across the gametest suite.

## Locked decisions (this refactor)

- **Records stay flat.** No `core()` component on the records — that would churn every `def.genus()`
  consumer and all 14 test literals. Sharing comes from a common interface + a codec-only carrier
  record + an abstract adapter base + a projection helper, which compose independently.
- **Shared types live in `forestry.core.genetics`** (internal), alongside `GenomeCodecs`,
  `GenomeProjection`, `GeneticsReloadHandler`. The definition shape is an implementation detail of the
  datapack loader, not an addon-facing API contract.
- **Naming: `ISpeciesDefinition`** (per the `I`-prefix API/interface convention: `ISpecies`,
  `ISpeciesType`, `ISpeciesBuilder`). The dead `api/genetics/SpeciesDefinition.java` (id-only, **zero
  references**) is deleted, freeing the name.
- **Flatten butterfly `Tail` only.** Collapsing the base-10 into `SpeciesCore` frees enough
  `RecordCodecBuilder.group()` slots that butterfly's `Tail` field-limit workaround can be inlined.
  Bee's `SpritePalette` is kept — `body`/`stripes`/`outline` are a genuine semantic grouping, not a
  field-limit hack.
- **The base-10 field list is deliberately named in two places** (`ISpeciesDefinition` for runtime
  polymorphic reads; `SpeciesCore` for codec grouping). They drive different mechanisms and cannot
  merge without giving records a `core()` component (rejected above) or codec-by-reflection. Accepted
  trade — still removes far more duplication than it introduces.

## Out of scope

- Any change to JSON shape, packet wire format, default genomes, or the karyotype-keyed lazy-codec
  pattern. This is a structural refactor only.
- Datagen providers' internal structure (`RecordingGenomeBuilder`, per-type reference inversion) —
  they construct definitions via the flat positional constructors, which are unchanged.
- The other still-open deferred follow-ups (data-definable flower types; recipe-result id-templating;
  `DefaultButterflySpecies.setMoth`). Tracked separately in `data-driven-genetics-overhaul.md`.

## The shared base fields (the thing being factored)

Identical name / type / default / order in all three records:

| field | type | codec key | default |
|---|---|---|---|
| genus | `String` | `genus` | *(required)* |
| species | `String` | `species` | *(required)* |
| dominant | `boolean` | `dominant` | `false` |
| glint | `boolean` | `glint` | `false` |
| secret | `boolean` | `secret` | `false` |
| complexity | `int` | `complexity` | `0` |
| authority | `String` | `authority` | `"Sengir"` |
| escritoireColor | `int` | `escritoire_color` | `-1` |
| temperature | `TemperatureType` | `temperature` | `NORMAL` |
| humidity | `HumidityType` | `humidity` | `NORMAL` |

`genome` (`Map<ResourceLocation, Allele<?>>`, trailing, default `Map.of()`) is base-shared for the
**interface** and the **projection helper**, but *not* part of `SpeciesCore` — its codec is
karyotype-keyed and already factored through `GenomeCodecs.alleleMapCodec/StreamCodec`.

## Component design

### 1. `ISpeciesDefinition` (interface) — `forestry.core.genetics`

The 11 base accessors (10 above + `genome()`). All three records add `implements ISpeciesDefinition`;
the compiler-generated record accessors satisfy it with **no other change**. Consumers that only need
base fields (the abstract adapter, the projection helper) can read them polymorphically.

### 2. `SpeciesCore` (record) — `forestry.core.genetics`

```java
record SpeciesCore(String genus, String species, boolean dominant, boolean glint, boolean secret,
                   int complexity, String authority, int escritoireColor,
                   TemperatureType temperature, HumidityType humidity)
```

Exposes:
- `static final MapCodec<SpeciesCore> MAP_CODEC` — the 10 `optionalFieldOf`/`fieldOf` lines with the
  exact keys/defaults above. Being a `MapCodec`, it **inlines to the same top-level JSON keys** — no
  nesting, JSON byte-identical.
- `static final StreamCodec<RegistryFriendlyByteBuf, SpeciesCore> STREAM_CODEC` — the 10 `writeX`/
  `readX` calls in the exact current order (genus, species, 3× bool, varInt complexity, authority,
  int escritoireColor, `ClimateCodecs.TEMPERATURE_STREAM`, `HUMIDITY_STREAM`) — packet byte-identical.

Each definition's `buildCodec` composes `SpeciesCore.MAP_CODEC.forGetter(def -> new SpeciesCore(...))`
in the leading group slot and reconstructs flat fields in `apply(...)` — identical to the existing
`SpritePalette`/`Tail` destructure/reconstruct idiom. Same for the stream codecs.

### 3. `AbstractDefinitionSpeciesBuilder<D, T, S, B>` — `forestry.core.genetics`

```java
public abstract class AbstractDefinitionSpeciesBuilder<
        D extends ISpeciesDefinition,
        T extends ISpeciesType<S, ?>,
        S extends ISpecies<?>,
        B extends ISpeciesBuilder<T, S, B>>
    implements ISpeciesBuilder<T, S, B> {

    protected static final String READ_ONLY_MESSAGE = "datapack species builder is read-only";
    protected final D def;
    protected AbstractDefinitionSpeciesBuilder(D def) { this.def = def; }

    // 10 base getters: getGenus()->def.genus(), ... getHumidity()->def.humidity()
    // 12 throwing base members: setDominant/setGenome/setGlint/setTemperature/setHumidity/
    //   setComplexity/setEscritoireColor/setSecret/setAuthority/setFactory/buildGenome/
    //   createSpeciesFactory  → throw new UnsupportedOperationException(READ_ONLY_MESSAGE)
}
```

The throwing setters return `B` and satisfy any covariant narrowing the per-type builder interfaces
declare (a `throw` needs no `return`). Each concrete adapter becomes:

```java
public class DefinitionBeeSpeciesBuilder
        extends AbstractDefinitionSpeciesBuilder<BeeSpeciesDefinition, IBeeSpeciesType, IBeeSpecies, IBeeSpeciesBuilder>
        implements IBeeSpeciesBuilder {
    private final IBeeJubilance jubilance;
    public DefinitionBeeSpeciesBuilder(BeeSpeciesDefinition def, IBeeJubilance jubilance) {
        super(def); this.jubilance = jubilance;
    }
    // ONLY bee-specific members: getBody/getStripes/getOutline/buildProducts/buildSpecialties/
    //   getJubilance + the bee-specific throwing setters (setBody/setStripes/setOutline/
    //   setJubilance/addProduct×2/addSpecialty×2)
}
```

`def` is typed `D` (= the concrete definition), so type-specific getters read it directly
(`def.body()`, `def.rarity()`, `def.spawnBiomes()`) with no cast. Bee ≈ 40 lines (was 217), tree
similar, butterfly similar.

### 4. `SpeciesProjection` (static helper) — `forestry.core.genetics`

```java
public static IGenome buildGenome(IKaryotype karyotype, ResourceLocation id, ISpeciesDefinition def) {
    IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
    GenomeProjection.applyOverrides(gb, karyotype, def.genome());
    return gb.build();
}
```

Each projector's fail-soft `try/catch`, its type-specific preflight (bee's `getJubilanceSafe`, tree's
bindings lookup), and its genuinely-per-type final `new XSpecies(id, type, genome, adapter)` line stay
in place — only the 3-line genome skeleton is shared.

### 5. Test helper — `src/test/.../gametest/TestSpeciesDefinitions`

A fluent per-type builder (`bee()` / `tree()` / `butterfly()`), base fields pre-seeded to the codec
defaults, with:
- `.from(liveSpecies)` — copies every base + type-specific field off a live `IBeeSpecies`/
  `ITreeSpecies`/`IButterflySpecies` (the `monarch.getGenusName()`-style seed the reload/projector
  tests do by hand today);
- fluent overrides for the fields a given test varies (`.genus(...).species(...).jubilance(...)`);
- `.genome(map).build()`.

Collapses each 12–20-line positional literal to 1–4 fluent lines and removes positional-arg fragility.
Lives in test sources only.

### 6. Delete dead code

Remove `src/main/java/forestry/api/genetics/SpeciesDefinition.java` (id-only holder, zero references
found via `rg 'SpeciesDefinition'` + import scan).

## File map (for planning)

**New (main):** `core/genetics/{ISpeciesDefinition, SpeciesCore, AbstractDefinitionSpeciesBuilder,
SpeciesProjection}.java`.
**New (test):** `gametest/TestSpeciesDefinitions.java`.
**Edit (main):** the 3 `*SpeciesDefinition` records (implement interface; use `SpeciesCore` in both
codecs; butterfly drops `Tail`), the 3 `Definition*SpeciesBuilder` adapters (extend the base), the 3
`*SpeciesProjector`s (use `SpeciesProjection.buildGenome`).
**Delete (main):** `api/genetics/SpeciesDefinition.java`.
**Edit (test):** the 14 construction sites → `TestSpeciesDefinitions` (`BeeSpeciesProjectorTest`,
`SpeciesFallbackTest`, `BeeSpeciesDefinitionTest`, `TreeSpeciesProjectorTest` ×2, `TreeSpeciesDefinitionTest`,
`TreeSpeciesReloadTest`, `TreeSpeciesFallbackTest`, `ButterflySpeciesProjectorTest` ×2,
`ButterflySpeciesDefinitionTest`, `ButterflySpeciesReloadTest`, `ButterflySpawnerReloadTest`,
`ButterflyEntityReloadTest`).

## Suggested task order (staged, each independently compilable + testable)

1. Add `ISpeciesDefinition` + make the 3 records `implements` it (no other change). Compiles, tests pass.
2. Add `SpeciesCore`; route the 3 definition codecs/stream-codecs through it; flatten butterfly `Tail`.
   Golden-master + `runData` idempotence prove bytes unchanged.
3. Add `AbstractDefinitionSpeciesBuilder`; reparent the 3 adapters.
4. Add `SpeciesProjection.buildGenome`; thread it through the 3 projectors.
5. Add `TestSpeciesDefinitions`; migrate the 14 literals.
6. Delete dead `api/genetics/SpeciesDefinition.java`.

Stages 1–4 each keep `compileJava`/`compileTestJava` green and are behavior-preserving in isolation;
5 touches only tests; 6 is a pure deletion.

## Testing / success criteria

- `compileJava` + `compileTestJava` + `build` green.
- `./gradlew runGameTestServer` = **55/55** (env `JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9`),
  including `GenomeBaselineTest` (default genomes byte-identical), all three `*SpeciesEquivalenceTest`
  golden masters, and the reload/fallback/spawner/entity suites the test-helper migration touches.
- `./gradlew runData` idempotent for `bee_species`, `tree_species`, `butterfly_species/*.json` (no
  JSON diff → confirms `SpeciesCore`/`Tail` changes are wire-transparent).
- `Loaded 69 bee species` / `Loaded 50 tree species` / `Loaded 35 butterfly species` +
  `114`/`42`/`1` mutation recipes at cold server start, unchanged.

## Test hygiene (recurring, carried from Stages 3–5)

Any migrated test that mutates the live shared species map via `setSpecies`/`rebuild*Species` MUST
still snapshot + restore in a `finally` (`setSpecies(snapshot)` + `rebuildMutations(recipeManager)`) —
the `TestSpeciesDefinitions` migration must preserve each test's existing snapshot/restore scaffolding
verbatim; it only replaces the definition-literal construction.
