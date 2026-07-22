# Allele Foundation — Design Spec

- **Date:** 2026-06-27
- **Status:** Approved (design)
- **Scope:** Stage 1 of the data-driven genetics overhaul
- **Branch target:** new branch off `1.21.1`
- **Backwards compatibility:** clean break (no save/itemstack migration)

---

## 1. Background & motivation

ForestryCE registers all bee/tree/butterfly **species** in code at mod setup
(`FMLCommonSetupEvent`), through `IForestryPlugin.registerApiculture/Arboriculture/Lepidopterology`.
The long-term goal is to make **species data-driven** (datapack JSON, loaded at world load,
synced to client, reloadable) while keeping **species *types*** code-driven
(`IForestryPlugin#registerGenetics`).

The blocker for that goal is the **allele system**. Today:

- Alleles are interned singletons (`AlleleManager`, `ForestryAlleles`) compared by **reference
  equality**, living in one global registry that **freezes** in two phases
  (`REGISTRATION_CHROMOSOMES_COMPLETE`, then `REGISTRATION_ALLELES_COMPLETE`).
- Genomes serialize alleles **by ID** into that frozen registry, so a genome cannot be
  deserialized without the registry being fully populated and frozen at startup.
- Species are themselves alleles (`IRegistryChromosome<IBeeSpecies>`), populated after species
  registration.

This pretends alleles are registry objects and makes genomes **not self-describing**, which is
fundamentally incompatible with runtime datapacks and clean client sync. It also boxes datapack
authors into the fixed set of values listed on each karyotype.

This spec redesigns the allele system into **inline, self-describing value genomes** — the
foundation every later stage depends on.

## 2. Goals

- Replace interned singleton alleles with **plain inline values + a dominance flag**, serialized
  directly into the genome.
- Make genomes **fully self-describing** (deserializable without a shared frozen allele registry).
- Collapse the five chromosome subtypes into **one generic `IChromosome<V>`**.
- Make allele values **permissive** — any in-type value is valid in a genome (frees datapack
  authors from the karyotype's preset list).
- Name allele values via **translation keys only** (no `Component` stored in the genetics model).
- **Migrate the existing code-registered species and mutations onto the new model with no
  behavior change.** Built-in species must be byte-for-byte identical in their default genomes.
- Delete the allele registration-state machine and interning.

## 3. Non-goals (explicitly out of scope for this stage)

- No datapack loading, no `/reload`, no species registry, no client sync packet — that is
  **Stage 3**.
- No mutation recipes — mutations stay **code-defined**, just ported onto the new allele types.
  Recipe modeling is **Stage 2**.
- Trees/butterflies: only their **genome read/write call sites** are migrated. Blocks, items,
  entities, and worldgen are untouched.
- `IRegistryAllele` reloadability / the final data-driven registry-value API is **deferred to
  Stage 3**. In this stage, registry values stay code-registered and non-reloadable.
- No datafixer. Existing serialized genomes are not migrated (clean break).

## 4. North-star roadmap (context only)

Built foundation-first; each stage is its own spec → plan → PR.

| # | Sub-project | Delivers | Datapack |
|---|---|---|---|
| **1** | **Allele foundation** *(this spec)* | Inline-value self-describing genomes; delete singleton registry + freeze; ID-referenced values; translation-key naming; existing species/mutations migrated. No behavior change. | — |
| 2 | Mutations as recipes | Custom `RecipeType` for mutations (parent + parent → result, chance, conditions); reload + client sync + JEI. Existing mutations emitted as recipe JSON via datagen. | ✅ |
| 3 | Data-driven bees | Bee species as a datapack registry, login sync, dynamic client model resolution, data-definable basic flower types; `registerSpecies` builders repurposed as datagen. | ✅ |
| 4 | Data-driven trees (genetics layer) | Tree genome/climate/products/effects data-driven; blocks/items/worldgen stay code-registered & ID-bound. | ✅ (genetics) |
| 5 | Data-driven butterflies (genetics layer) | Same shape as trees. | ✅ (genetics) |

## 5. Design

### 5.1 Allele model

```java
// Replaces the entire IAllele sealed hierarchy
// (IBooleanAllele/IFloatAllele/IIntegerAllele/IValueAllele/IRegistryAllele).
public record Allele<V>(V value, boolean dominant) {}
```

- Generic, not sealed, not interned, **no id, no registry**.
- An allele is just a value plus its dominance.

```java
public record AllelePair<V>(Allele<V> first, Allele<V> second) {
    // Expressed (active) allele computed from dominance with a deterministic tiebreak:
    //   - if exactly one is dominant, it is expressed;
    //   - otherwise `first` is expressed (preserves current "stored active" behavior).
    Allele<V> active();
    Allele<V> inactive();
}
```

- This preserves the inheritance/recombination semantics Forestry has today (where an active and
  inactive allele are tracked), but each side is now a plain `Allele<V>`.

### 5.2 Chromosome model — one generic type

Collapse `IFloatChromosome`/`IIntegerChromosome`/`IBooleanChromosome`/`IValueChromosome`/
`IRegistryChromosome` into a single:

```java
public interface IChromosome<V> {
    ResourceLocation id();
    Codec<V> valueCodec();
    StreamCodec<RegistryFriendlyByteBuf, V> valueStreamCodec();
    Allele<V> defaultAllele();          // karyotype-level default
    boolean weaklyInherited();
    String translationKey(V value);     // naming RULE — see 5.6 (no Component)
    String chromosomeTranslationKey();  // name of the chromosome itself
    // For ID-reference value types only (species, flower_type, effect, activity, fruit, cocoon):
    // a resolver mapping ResourceLocation <-> V against the existing code registries.
    // Non-reference chromosomes return null / identity.
    @Nullable IValueResolver<V> resolver();
}
```

Value kinds carried by `V`:
- **Primitives** — `int`, `float`, `boolean`.
- **Enums / records** — `ToleranceType`, `Vec3i` (territory).
- **ID-reference** — `IBeeSpecies`, `IFlowerType`, `IBeeEffect`, `IActivityType`, `IFruit`,
  butterfly cocoon/effect. The **genome stores the `ResourceLocation` inline** and resolves it at
  read time through the existing code-registered value maps. This is the interim treatment of
  today's `IRegistryAllele`; the value codec is
  `ResourceLocation.CODEC.xmap(resolver::get, resolver::getId)`.

### 5.3 Genome & codec

- `IKaryotype` builds the genome codec **dynamically**: one field per chromosome, keyed by
  `chromosome.id()`, encoding that chromosome's `AllelePair<V>` as
  `{ value: chromosome.valueCodec(), dominant: Codec.BOOL }` for each of the two alleles.
  Same per-karyotype dispatch Forestry already uses (`Karyotype#getGenomeCodec`), **minus the
  global `IAllele.CODEC` allele-id lookup**.
- A matching `StreamCodec<RegistryFriendlyByteBuf, IGenome>` is built the same way for network
  sync. Because values are inline, **no synced allele registry is required client-side** to
  decode a genome. (Behavior-value registries themselves still live in code on both sides.)
- `IGenome` gains value-oriented accessors that replace the pervasive
  `getActiveAllele(chromosome).value()` pattern:
  - `V getActiveValue(IChromosome<V>)`, `V getInactiveValue(IChromosome<V>)`
  - `Allele<V> getActiveAllele(IChromosome<V>)` (now returns `Allele<V>`, not the old `IAllele`)

### 5.4 Karyotype

Stays **code-defined per species type** (species types remain code-driven; unchanged).
A karyotype defines:
- the ordered chromosome set,
- each chromosome's default `Allele<V>`,
- `weaklyInherited` flags,
- the species chromosome,
- the dynamically-built genome codec + stream codec.

Removed from the karyotype:
- `getAlleles(chromosome)` (valid-allele enumeration) — **deleted**.
- the value-whitelist half of `isAlleleValid` — **deleted**. Membership is checked via
  `contains(chromosome)` only.

### 5.5 Validity — fully permissive

- **No `knownValues`, no valid-allele lists, no whitelist.** (In code today this whitelist is the
  `validAlleles` field on `core/genetics/Karyotype.java`, surfaced via `IKaryotype#getAlleles` and
  the value-checking branch of `isAlleleValid`; all of that is deleted.)
- The only checks are **chromosome membership** (`karyotype.contains` — e.g. a tree karyotype has
  no `FLOWER_TYPE`) and codec type-safety.
- Rationale (confirmed): alleles only ever originate from **two parents**, or from a **mutation's
  default genome** (usually the resultant species' default genome). There is no pool to roll from,
  no UI selector over valid values, and the analyzer does not iterate valid alleles. So a
  valid-value list has no consumer.

### 5.6 Naming — translation keys only

- The genetics model exposes **translation keys (`String`)**; it never stores or returns a
  `Component`. Consumers build `Component.translatable(key)` only at the render edge.
- Each chromosome provides a **naming rule** `String translationKey(V value)`:
  - **ID-reference chromosomes:** key derives from the value's `ResourceLocation`
    (e.g. species/flower-type keep their existing key convention). Authors localize that key.
  - **Primitive/enum/record chromosomes:** the rule maps Forestry's preset values to friendly
    suffixes (e.g. `1.7 → allele.forestry.speed.fastest`); off-preset values resolve to a
    **deterministic key from the canonical value** (e.g. `allele.forestry.speed.1_23`).
  - **Graceful fallback:** if no translation exists for the resolved key, the UI renders the
    **raw value** (`1.23`) instead of a raw key string.
- Datapack authors name custom values purely by shipping a resource pack translation for the
  (deterministic, predictable) key.

### 5.7 Commands

`DiagnosticsCommand` (line ~63) and `ModifyGenomeCommand` (line ~103, argument suggestions) are
the **only** consumers of valid-allele enumeration. They are rewritten to **compute candidate
values from the currently-registered species' default genomes** for the chromosome, **sorted by
value** (by id/enum name for reference types). No stored list is needed.

### 5.8 Lifecycle / cleanup

- Delete `AlleleManager`'s registration-state machine
  (`REGISTRATION_OPEN/CHROMOSOMES_COMPLETE/ALLELES_COMPLETE`), interning maps, and value/codec
  plumbing. Chromosomes are still created at `registerGenetics` time (code).
- Behavior-value registries (flower types, effects, activity, species map) stay — now plain
  `ResourceLocation → V` maps, no longer "alleles".
- **Species stop being alleles:** the SPECIES chromosome becomes an ID-reference chromosome
  resolved through the species map. `ISpecies` and `IFlowerType` drop `extends
  IRegistryAlleleValue`. A value's **default dominance** moves into its definition and is used
  only when building the default genome.
- `ForestryAlleles` **survives** as plain `Allele<V>` constants
  (e.g. `SPEED_FAST = new Allele<>(1.2f, true)`), so `DefaultBeeSpecies` / karyotype setup stay
  readable and become the Stage-3 datagen source with minimal churn. The `DEFAULT_*` aggregate
  lists are **deleted** (they only fed `addAlleles`).
- Karyotype builder keeps `.set(chromosome, defaultAllele)` and `.setWeaklyInherited(...)`; drops
  `.addAlleles(...)`. `IKaryotypeBuilder` / `IChromosomeBuilder` / `ChromosomeBuilder` updated
  accordingly.

## 6. Affected code inventory

**Replace / collapse (API — `forestry/api/genetics/alleles/`):**
- `IAllele.java` → `Allele<V>` record. Remove sealed permits.
- Delete `IBooleanAllele`, `IFloatAllele`, `IIntegerAllele`, `IValueAllele`, `IRegistryAllele`.
- Delete `IBooleanChromosome`, `IFloatChromosome`, `IIntegerChromosome`, `IValueChromosome`,
  `IRegistryChromosome`; fold into generic `IChromosome<V>`.
- `IRegistryAlleleValue` — remove (dominance moves to value definitions / default genome).
- `AllelePair.java` → generic record + dominance-based `active()/inactive()`.
- `IKaryotype.java` — remove `getAlleles`, value-whitelist `isAlleleValid`; keep `contains`,
  defaults, codecs, species chromosome.
- `IAlleleManager.java` / `IAlleleNaming.java` — remove or reduce to naming helpers (no interning).
- `BeeChromosomes` / `TreeChromosomes` / `ButterflyChromosomes` — rebuild on generic `IChromosome<V>`.
- `ForestryAlleles.java` — keep value constants as `Allele<V>`; delete `DEFAULT_*` lists and the
  registry/manager references.

**Replace (impl — `forestry/core/genetics/alleles/`):**
- Delete `AlleleManager`, `BooleanAllele`, `FloatAllele`, `IntegerAllele`, `ValueAllele`,
  `RegistryAllele`, `BooleanChromosome`, `FloatChromosome`, `IntegerChromosome`,
  `ValueChromosome`, `RegistryChromosome`, `AbstractChromosome` — replaced by the generic
  `Chromosome<V>` impl + `Allele<V>` record.

**Genome / builder:**
- `core/genetics/Genome.java`, `core/genetics/Karyotype.java`, `api/genetics/IGenome.java`,
  `api/plugin/IGenomeBuilder.java` + impl — value-oriented accessors; codec rebuild;
  `isAlleleValid` → `contains`.
- `api/plugin/IKaryotypeBuilder.java`, `api/plugin/IChromosomeBuilder.java`,
  `core/genetics/ChromosomeBuilder.java` — drop `addAlleles`.
- `apiimpl/plugin/SpeciesRegistration.java` — `buildAll` no longer creates species alleles via
  `registryAllele`; sets the species value; taxon-default guard uses `contains`.

**Serialization:** `CoreDataComponents` genome component codec; remove `IAllele.CODEC` /
`AllelePair.CODEC` global-registry lookups.

**Consumers:** every `getActiveAllele(...).value()` / reference-equality allele comparison across
`apiculture`, `arboriculture`, `lepidopterology`, `sorting`, `core/genetics` — migrate to value
accessors. (Largest mechanical surface; compiler-guided.)

**Commands:** `DiagnosticsCommand`, `ModifyGenomeCommand` — compute candidates from registered
species.

**Markers:** delete `IRegistryAlleleValue` and drop `extends IRegistryAlleleValue` from **all**
implementors — `ISpecies`, `IFlowerType`, `IBeeEffect`, `IActivityType`, `IFruit`, `ITreeEffect`,
`IButterflyCocoon`, `IButterflyEffect` (8 interfaces). A value's default dominance moves into its
definition. Compiler-guided.

**Imprinter:** confirmed already absent from the **source** — no Java action. Orphan resources
remain (`assets/forestry/models/item/imprinter.json` + two `imprinter.png` textures); leave them
or delete in a trivial cleanup commit (no functional impact).

## 7. Testing strategy

- **Golden-master:** before the refactor, snapshot every built-in species' default genome
  (serialized form) for all three types. After the refactor, assert the rebuilt default genomes
  are **identical** — the clean break is about the *save format*, not species *content*.
- **GameTests** (reuse `runGameTestServer`):
  - genome codec round-trip (encode → decode → equals), persistent and network codecs;
  - dominance / expression resolution (`active()/inactive()` with all dominance combinations);
  - inheritance recombination from two parents;
  - default-genome construction (karyotype defaults + taxon defaults + species overrides);
  - permissive-value acceptance (a genome holding an off-preset value round-trips and reads back).
- **Build:** full compile (the consumer migration is compiler-guided) + existing test suite green.

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Broad blast radius across all genetics consumers | Compiler-guided migration; golden-master + gametests as the safety net. |
| Subtle change to dominance/expression vs today | `AllelePair.active()` tiebreak chosen to preserve current "stored active" behavior; covered by gametests. |
| Genome codec field-order / key changes break round-trip | Codec built deterministically from the karyotype's ordered chromosome list; round-trip gametest. |
| Clean break surprises a user with existing worlds | Confirmed acceptable (1.21.1 in development); documented in changelog/PR. |

## 9. Deferred questions (revisit at the named stage)

- **Stage 3:** the final `IRegistryAllele` replacement API and *which* registry values
  (flower types, effects, activity, species) become reloadable datapack content + client sync.
- **Stage 2:** mutation recipe schema (conditions, chance, discoverability) and JEI category.
