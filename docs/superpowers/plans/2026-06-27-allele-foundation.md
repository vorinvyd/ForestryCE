# Allele Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ForestryCE's interned singleton allele system with inline, self-describing value genomes — the foundation for later data-driven species stages — with **no change to built-in species behavior**.

**Architecture:** An allele becomes `record Allele<V>(V value, boolean dominant)` serialized directly into the genome (no global registry, no freeze). The five chromosome subtypes collapse into one generic `IChromosome<V>`. **Genomes store only serializable data:** primitives, enums, `Vec3i`, and — for "reference" chromosomes (species, flower type, bee effect, activity, fruit, tree effect, cocoon, butterfly effect) — the value's `ResourceLocation`. Behavior objects are resolved from those ids on demand through code registries. Genome/karyotype codecs are built per-karyotype from chromosome value codecs. Value validity becomes fully permissive.

**Tech Stack:** Java 21, NeoForge 21.1 (MC 1.21.1), Mojang `Codec`/`StreamCodec` (DataFixerUpper 9.0.19), Forestry GameTest harness (`./gradlew runGameTestServer`).

**Spec:** `docs/superpowers/specs/2026-06-27-allele-foundation-design.md`

---

## ⚠️ Read before starting

### This is atomic type-surgery
Swapping `IAllele` (a sealed hierarchy) for `Allele<V>` (a record) **breaks compilation across the
whole genetics package and its consumers until the swap is complete.** Therefore:

- **Phases B → C → D → E do not compile individually.** Do all of them, *then* run `./gradlew compileJava`
  and iterate to green. Treat the end of Phase E as the first compile gate.
- Commit at phase boundaries anyway (even non-compiling) **on this feature branch** so review is granular
  and revert/bisect stays useful; mark them `wip:`. The *final* commits (Phase E onward) must compile.
- The **golden-master test (Phase A)** and the existing suite are the behavioral safety net: built-in
  species' default genomes must be identical before and after.
- Branch: `allele-foundation` (already created off `1.21.1`). Do not work on `1.21.1` directly.

### Core model decision: genomes store data, not behavior objects (READ THIS)
This drove the plan's shape; the reviewer flagged the alternative (storing resolved objects) as circular
and crash-prone. The rules:

- **`Allele<V>` is always an eager record** — never lazy. No `Supplier`, no null-value placeholder.
- **Data chromosomes** (speed, lifespan, fertility, tolerance, territory, booleans, …): `V` is the literal
  value type (`Float`, `Integer`, `Boolean`, `ToleranceType`, `Vec3i`). The genome stores the value.
- **Reference chromosomes** (SPECIES, FLOWER_TYPE, EFFECT/bee, ACTIVITY, FRUIT, EFFECT/tree, COCOON,
  EFFECT/butterfly): **`V = ResourceLocation`.** The genome stores the **id**. The chromosome carries a
  `resolver()` (id → behavior object) used by typed accessors. Naming keys off the id.
- **No resolution happens at chromosome-creation time** (`registerGenetics`), so the not-yet-populated
  registries are never touched then. Reference *defaults* are declared by **id** and only resolved later
  (at default-genome build time, in `handleSpeciesRegistration`, after the registries are populated).
- **Dominance for reference alleles** comes from the referenced value's `isDominant()` (kept as a plain
  method — see §D2), except the **species** chromosome, whose dominance comes from
  `ISpeciesBuilder.isDominant()` (available at build time, avoiding the species ← genome ← species cycle).
- **Consumers that need the behavior object** resolve the stored id via the species-type registries
  (e.g. `genome.getActiveSpecies()`, or `beeType.getFlowerType(genome.getActiveValue(FLOWER_TYPE))`).
  `getActiveValue(referenceChromosome)` returns the `ResourceLocation`.

Build/verify commands: compile `./gradlew compileJava`; tests compile `./gradlew compileTestJava`;
gametests `./gradlew runGameTestServer`; full `./gradlew build`.

---

## File Structure

**New files**
- `src/main/java/forestry/api/genetics/alleles/Allele.java` — `record Allele<V>(V value, boolean dominant)`.
- `src/main/java/forestry/core/genetics/alleles/Chromosome.java` — the single generic `IChromosome<V>` impl (data + reference).
- `src/main/java/forestry/core/genetics/alleles/ChromosomeFactory.java` — static factories replacing `IAlleleManager` chromosome creation.
- `src/test/java/forestry/gametest/GenomeBaselineTest.java` — golden-master dump + assert.
- `src/test/java/forestry/gametest/AlleleFoundationTest.java` — codec/dominance/inheritance gametests.
- `src/test/resources/forestry/gametest/genome-baseline.txt` — committed golden-master snapshot.

**Heavily rewritten (API — `forestry/api/genetics/alleles/`)**
- `IChromosome.java` → generic `<V>` + optional resolver; `AllelePair.java` → generic; `IKaryotype.java` → trim.
- **Delete:** `IAllele`, `IBooleanAllele`, `IFloatAllele`, `IIntegerAllele`, `IValueAllele`, `IRegistryAllele`, `IBooleanChromosome`, `IFloatChromosome`, `IIntegerChromosome`, `IValueChromosome`, `IRegistryChromosome`, `IRegistryAlleleValue`, `IAlleleManager`, `IAlleleNaming`.
- **Rewrite:** `ForestryAlleles.java`, `BeeChromosomes.java`, `TreeChromosomes.java`, `ButterflyChromosomes.java`.

**Heavily rewritten (impl — `forestry/core/genetics/`)**
- **Delete:** `alleles/AlleleManager`, `alleles/AbstractChromosome`, `alleles/{Boolean,Float,Integer,Value,Registry}Allele`, `alleles/{Boolean,Float,Integer,Value,Registry}Chromosome`.
- **Rewrite:** `Karyotype.java`, `Genome.java` (+ `Genome.Builder`), `IGenome` (api) accessors.

**Modified (registration / plugin)**
- `api/plugin/IGenomeBuilder.java`, `api/plugin/IKaryotypeBuilder.java`, `api/plugin/IChromosomeBuilder.java`, `core/genetics/ChromosomeBuilder.java`, `apiimpl/plugin/SpeciesRegistration.java`, `apiimpl/plugin/PluginManager.java`, `apiimpl/ForestryApiImpl.java`, `api/IForestryApi.java`.

**Modified (reference-resolver backing — replaces deleted `IRegistryChromosome.populate`)**
- `apiculture/genetics/BeeSpeciesType.java`, `arboriculture/genetics/TreeSpeciesType.java`, `lepidopterology/genetics/ButterflySpeciesType.java` (store the id→value maps + expose getters), `apiimpl/plugin/ArboricultureRegistration.java`, `apiimpl/plugin/LepidopterologyRegistration.java` (already-built maps; `ApicultureRegistration` already exposes `getFlowerTypes`/`getBeeEffects`/`getActivityTypes`).

**Modified (marker drop, keep `isDominant()` — 8 interfaces)**
- `api/genetics/ISpecies.java`, `api/apiculture/IFlowerType.java`, `api/apiculture/genetics/IBeeEffect.java`, `api/apiculture/IActivityType.java`, `api/arboriculture/genetics/IFruit.java`, `api/arboriculture/genetics/ITreeEffect.java`, `api/lepidopterology/IButterflyCocoon.java`, `api/lepidopterology/IButterflyEffect.java`.

**Modified (registration data + serialization + commands + consumers — compiler-guided; see Phase E)**
- `plugin/DefaultForestryPlugin.java`, `plugin/DefaultBeeSpecies.java`, `plugin/DefaultTreeSpecies.java`, `plugin/DefaultButterflySpecies.java`, `core/CoreDataComponents.java`, `core/commands/DiagnosticsCommand.java`, `core/commands/ModifyGenomeCommand.java`, and the consumer files in Phase E.

---

## Phase A — Golden-master safety net (runs on CURRENT code)

### Task A1: Capture the default-genome baseline

**Files:**
- Create: `src/test/java/forestry/gametest/GenomeBaselineTest.java`
- Create (generated): `src/test/resources/forestry/gametest/genome-baseline.txt`

A representation-agnostic dump of every built-in species' default genome, so we can prove the refactor
preserves genetics. The dump renders identically under both old and new allele models because it uses
**canonical value strings**:

```
// One line per (species, chromosome):
//   <speciesTypeId> <speciesId> <chromosomeId> = <active> | <inactive>
// Each allele renders as "<canonicalValue>:<dominant>".
// canonicalValue (MUST be reproduced byte-for-byte by the new-API dumper in Phase G):
//   float   -> Float.toString(v)
//   int     -> Integer.toString(v)
//   boolean -> Boolean.toString(v)
//   enum    -> ((Enum<?>) v).name()
//   Vec3i   -> v.getX()+","+v.getY()+","+v.getZ()
//   reference value -> the ResourceLocation string
//                      OLD: allele.alleleId();  NEW: the stored ResourceLocation
// dominant: OLD allele.dominant();  NEW Allele.dominant().
// Species sorted by id; chromosomes in karyotype order.
```

- [ ] **Step 1: Write the dumper gametest** (against current API), mirroring existing tests in
  `src/test/java/forestry/gametest/`. Iterate `IForestryApi.INSTANCE.getGeneticManager().getSpeciesTypes()`;
  for each species in `type.getAllSpeciesIds()` (sorted) read `species.getDefaultGenome().getChromosomes()`
  and emit canonical lines. Switch on `-Dforestry.genomeBaseline=generate`: generate-mode writes
  `run/genome-baseline.txt`; assert-mode compares to the committed resource and fails with a diff.

- [ ] **Step 2: Generate the baseline on current code**

Run: `./gradlew runGameTestServer -Dforestry.genomeBaseline=generate`
Expected: PASS; `run/genome-baseline.txt` produced.

- [ ] **Step 3: Commit the baseline + test**

```bash
mkdir -p src/test/resources/forestry/gametest
cp run/genome-baseline.txt src/test/resources/forestry/gametest/genome-baseline.txt
git add src/test/java/forestry/gametest/GenomeBaselineTest.java src/test/resources/forestry/gametest/genome-baseline.txt
git commit -m "test: capture default-genome golden-master baseline (pre-refactor)"
```

- [ ] **Step 4: Verify assert-mode passes on current code**

Run: `./gradlew runGameTestServer`
Expected: PASS — proves the oracle is stable before any change.

---

## Phase B — New core API types

> No compilation expected until end of Phase E. Commit `wip:` at the end of each task.

### Task B1: `Allele<V>` and generic `AllelePair<V>`

**Files:** Create `api/genetics/alleles/Allele.java`; modify `api/genetics/alleles/AllelePair.java`; delete `api/genetics/alleles/IAllele.java`.

- [ ] **Step 1: Create `Allele<V>` (eager record)**

```java
package forestry.api.genetics.alleles;

/** An inline allele: a value plus its dominance. Always eager. Replaces the old interned IAllele hierarchy. */
public record Allele<V>(V value, boolean dominant) {
    public static <V> Allele<V> dominant(V value) { return new Allele<>(value, true); }
    public static <V> Allele<V> recessive(V value) { return new Allele<>(value, false); }
}
```

- [ ] **Step 2: Rewrite `AllelePair` generic over the value type, preserving dominance ordering exactly**

Preserve the semantics of the current `create`/`getActiveAllele`/`getInactiveAllele`/`inheritOther`/
`inheritHaploid`/`both`/`isSameAlleles`. Remove the static `CODEC` (moved to the per-karyotype codec).

```java
package forestry.api.genetics.alleles;

import net.minecraft.util.RandomSource;

public record AllelePair<V>(Allele<V> active, Allele<V> inactive) {
    public static <V> AllelePair<V> both(Allele<V> allele) { return new AllelePair<>(allele, allele); }

    public AllelePair<V> inheritOther(RandomSource rand, AllelePair<V> other) {
        Allele<V> a = rand.nextBoolean() ? this.active : this.inactive;
        Allele<V> b = rand.nextBoolean() ? other.active : other.inactive;
        return rand.nextBoolean() ? create(a, b) : create(b, a);
    }
    public AllelePair<V> inheritHaploid(RandomSource rand) {
        Allele<V> c = rand.nextBoolean() ? this.active : this.inactive;
        return new AllelePair<>(c, c);
    }
    public boolean isSameAlleles() { return this.active.equals(this.inactive); }

    /** Orders by dominance: first dominant wins; both-recessive -> first; both-dominant -> second inactive. */
    public static <V> AllelePair<V> create(Allele<V> first, Allele<V> second) {
        return new AllelePair<>(activeOf(first, second), inactiveOf(first, second));
    }
    private static <V> Allele<V> activeOf(Allele<V> a, Allele<V> b) {
        if (a.dominant()) return a;
        if (b.dominant()) return b;
        return a;
    }
    private static <V> Allele<V> inactiveOf(Allele<V> a, Allele<V> b) {
        if (!b.dominant()) return b;
        if (!a.dominant()) return a;
        return b;
    }
}
```

> The `isSameAlleles` change from `==` (old; relied on interning) to `.equals` (records) is required and
> behavior-preserving here. Covered by the inheritance gametest.

- [ ] **Step 3: Delete `IAllele.java`; commit WIP.**

```bash
git rm src/main/java/forestry/api/genetics/alleles/IAllele.java
git add -A && git commit -m "wip: add Allele record, make AllelePair generic (non-compiling)"
```

### Task B2: Generic `IChromosome<V>` (+ optional resolver) and delete subtypes

**Files:** Modify `api/genetics/alleles/IChromosome.java`; delete the subtype/manager/naming interfaces listed in File Structure.

- [ ] **Step 1: Rewrite `IChromosome<V>`**

```java
package forestry.api.genetics.alleles;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

public interface IChromosome<V> {
    ResourceLocation id();

    /** Codec for the value stored INLINE in the genome. Reference chromosomes have V = ResourceLocation. */
    Codec<V> valueCodec();
    StreamCodec<RegistryFriendlyByteBuf, V> valueStreamCodec();

    Allele<V> defaultAllele();           // resolved by build time; see ChromosomeFactory for reference defaults
    boolean weaklyInherited();

    String chromosomeTranslationKey();   // e.g. "chromosome.forestry.speed"
    String translationKey(V value);      // naming RULE; no Component ever stored/returned

    /** Non-null only for reference chromosomes (V == ResourceLocation): resolves the id to a behavior object. */
    @Nullable IReferenceResolver<?> resolver();

    interface IReferenceResolver<R> {
        R get(ResourceLocation id);
        ResourceLocation getId(R value);
    }
}
```

- [ ] **Step 2: Delete subtype/manager/naming interfaces; commit WIP.**

```bash
git rm src/main/java/forestry/api/genetics/alleles/IBooleanAllele.java \
       src/main/java/forestry/api/genetics/alleles/IFloatAllele.java \
       src/main/java/forestry/api/genetics/alleles/IIntegerAllele.java \
       src/main/java/forestry/api/genetics/alleles/IValueAllele.java \
       src/main/java/forestry/api/genetics/alleles/IRegistryAllele.java \
       src/main/java/forestry/api/genetics/alleles/IBooleanChromosome.java \
       src/main/java/forestry/api/genetics/alleles/IFloatChromosome.java \
       src/main/java/forestry/api/genetics/alleles/IIntegerChromosome.java \
       src/main/java/forestry/api/genetics/alleles/IValueChromosome.java \
       src/main/java/forestry/api/genetics/alleles/IRegistryChromosome.java \
       src/main/java/forestry/api/genetics/alleles/IRegistryAlleleValue.java \
       src/main/java/forestry/api/genetics/alleles/IAlleleManager.java \
       src/main/java/forestry/api/genetics/alleles/IAlleleNaming.java
git add -A && git commit -m "wip: collapse chromosome subtypes into generic IChromosome<V> (non-compiling)"
```

### Task B3: Trim `IKaryotype`; collapse `IGenome` accessors; add reference resolution helpers

**Files:** Modify `api/genetics/alleles/IKaryotype.java`, `api/genetics/IGenome.java`.

- [ ] **Step 1: `IKaryotype`** — remove `getAlleles(IChromosome)` and the value-checking `isAlleleValid`
  overload (keep `contains`). Change `getSpeciesChromosome()` return type from `IRegistryChromosome<? extends
  ISpecies<?>>` to `IChromosome<ResourceLocation>` (the species chromosome stores ids; its resolver yields
  `ISpecies`). Change `getDefaultAlleles()` → `ImmutableMap<IChromosome<?>, Allele<?>>` and
  `getDefaultAllele(IChromosome<V>)` → `Allele<V>`. Add `StreamCodec<RegistryFriendlyByteBuf, IGenome>
  getGenomeStreamCodec()`. Keep `Codec<IKaryotype> CODEC` unchanged.

- [ ] **Step 2: `IGenome`** — collapse typed accessors into generics; `getActiveValue(referenceChromosome)`
  returns the `ResourceLocation`; add typed reference resolution that goes through the chromosome resolver.

```java
default <V> V getActiveValue(IChromosome<V> chromosome) { return getActiveAllele(chromosome).value(); }
default <V> V getInactiveValue(IChromosome<V> chromosome) { return getInactiveAllele(chromosome).value(); }
default <V> Allele<V> getActiveAllele(IChromosome<V> chromosome) { return getAllelePair(chromosome).active(); }
default <V> Allele<V> getInactiveAllele(IChromosome<V> chromosome) { return getAllelePair(chromosome).inactive(); }
<V> AllelePair<V> getAllelePair(IChromosome<V> chromosome);
ImmutableMap<IChromosome<?>, AllelePair<?>> getChromosomes();

// Reference resolution (id -> behavior object) via the chromosome's resolver:
@SuppressWarnings("unchecked")
default <R> R resolveActive(IChromosome<ResourceLocation> chromosome) {
    return (R) ((IChromosome.IReferenceResolver<R>) chromosome.resolver()).get(getActiveValue(chromosome));
}
default <S extends ISpecies<?>> S getActiveSpecies() { return resolveActive(getKaryotype().getSpeciesChromosome()); }
```

  Remove the `getActiveName`/`getInactiveName` `Component` helpers (naming moves to the UI edge — Task E4).
  Update `copyWith(Map<IChromosome<?>, Allele<?>>)` to wrap each value as `AllelePair.both`.

- [ ] **Step 3: Commit WIP.**

```bash
git add -A && git commit -m "wip: trim IKaryotype, collapse IGenome accessors, add reference resolution (non-compiling)"
```

### Task B4: Builder API signatures

**Files:** Modify `api/plugin/IGenomeBuilder.java`, `api/plugin/IKaryotypeBuilder.java`, `api/plugin/IChromosomeBuilder.java`.

- [ ] **Step 1: `IGenomeBuilder`** — `set/setActive/setInactive(IChromosome<V>, Allele<V>)`. Add a
  reference-by-id convenience for species genome overrides: `set(IChromosome<ResourceLocation>,
  ResourceLocation id)` that builds `Allele.dominant(...)`/recessive from the resolved value's `isDominant()`
  at apply time (apply happens during `buildAll`, after registries are populated). Keep `setUnchecked`,
  `isEmpty`, `setRemainingDefault`.
- [ ] **Step 2: `IKaryotypeBuilder`** — `set(IChromosome<V>, Allele<V>)` for data chromosomes; for reference
  chromosomes `set(IChromosome<ResourceLocation>, ResourceLocation defaultId)` (deferred — see C1 default
  resolution). Keep `setSpecies(IChromosome<ResourceLocation> species, ResourceLocation defaultId)`. **Keep a
  boolean convenience** `set(IChromosome<Boolean> chromosome, boolean value)` building
  `Allele.dominant(value)` (the karyotype defs use bare `false`/`true` literals). **Remove `addAlleles`.**
- [ ] **Step 3: `IChromosomeBuilder`** — remove `addAlleles`; keep `setDefault`, `setWeaklyInherited`. Commit WIP.

```bash
git add -A && git commit -m "wip: builder signatures to Allele<V>, reference-by-id, drop addAlleles (non-compiling)"
```

---

## Phase C — New impl

### Task C1: `Chromosome<V>` impl + `ChromosomeFactory`; delete old impl classes

**Files:** Create `core/genetics/alleles/Chromosome.java`, `core/genetics/alleles/ChromosomeFactory.java`; delete the old impl classes listed in File Structure.

- [ ] **Step 1:** `Chromosome<V>` (final class) implementing `IChromosome<V>`: id, value codec + stream codec,
  default-allele supplier (eager for data chromosomes; for reference chromosomes a memoized resolve of
  `defaultId` → `new Allele<>(defaultId, resolver.get(defaultId).isDominant())`, evaluated lazily on first
  `defaultAllele()` call — which only happens at/after `buildAll`), `weaklyInherited`, naming rule, nullable
  resolver.
- [ ] **Step 2:** `ChromosomeFactory` static helpers used by the chromosome holders:
  - `floatChromosome(id, Allele<Float> def, namingRule)` (codec `Codec.FLOAT`, stream `ByteBufCodecs.FLOAT`);
  - `intChromosome(...)`, `booleanChromosome(...)`;
  - `valueChromosome(id, Codec<V>, StreamCodec, Allele<V> def, namingRule)` for enums/`Vec3i`;
  - `referenceChromosome(id, ResourceLocation defaultId, IReferenceResolver<R> resolver, namingRule)` —
    **V = ResourceLocation**; value codec = `ResourceLocation.CODEC`; stream codec = `ResourceLocation.STREAM_CODEC`;
    default resolved lazily as in Step 1. The resolver itself looks up the code registry at call time
    (post-population), so no registry access occurs at creation.
- [ ] **Step 3:** Default naming rules: data chromosomes → map Forestry presets to suffix keys, otherwise
  `chromosomeKeyBase + "." + sanitizedCanonical(value)`; reference chromosomes → key from the id (e.g. species
  keep their existing translation key; others `<chromosome>.<namespace>.<path>`).
- [ ] **Step 4:** Delete old impl classes; commit WIP.

```bash
git rm src/main/java/forestry/core/genetics/alleles/AlleleManager.java \
       src/main/java/forestry/core/genetics/alleles/AbstractChromosome.java \
       src/main/java/forestry/core/genetics/alleles/BooleanAllele.java \
       src/main/java/forestry/core/genetics/alleles/FloatAllele.java \
       src/main/java/forestry/core/genetics/alleles/IntegerAllele.java \
       src/main/java/forestry/core/genetics/alleles/ValueAllele.java \
       src/main/java/forestry/core/genetics/alleles/RegistryAllele.java \
       src/main/java/forestry/core/genetics/alleles/BooleanChromosome.java \
       src/main/java/forestry/core/genetics/alleles/FloatChromosome.java \
       src/main/java/forestry/core/genetics/alleles/IntegerChromosome.java \
       src/main/java/forestry/core/genetics/alleles/ValueChromosome.java \
       src/main/java/forestry/core/genetics/alleles/RegistryChromosome.java
git add -A && git commit -m "wip: generic Chromosome impl + factory; delete old allele/chromosome impls (non-compiling)"
```

### Task C2: `Karyotype` impl — per-karyotype inline codec, permissive validity

**Files:** Modify `core/genetics/Karyotype.java`.

- [ ] **Step 1: Build the genome `Codec<IGenome>`** from the karyotype's ordered chromosomes using
  **`Codec.dispatchedMap`** (NOT `simpleMap` — `simpleMap` cannot select a per-key value codec;
  `dispatchedMap` is present in DFU 9.0.19):

```java
// pairCodecFor(chromosome) builds the AllelePair codec from that chromosome's value codec:
static <V> Codec<AllelePair<V>> pairCodecFor(IChromosome<V> c) {
    Codec<Allele<V>> alleleCodec = RecordCodecBuilder.create(i -> i.group(
        c.valueCodec().fieldOf("value").forGetter(Allele::value),
        Codec.BOOL.optionalFieldOf("dominant", false).forGetter(Allele::dominant)
    ).apply(i, Allele::new));
    return RecordCodecBuilder.create(i -> i.group(
        alleleCodec.fieldOf("active").forGetter(AllelePair::active),
        alleleCodec.fieldOf("inactive").forGetter(AllelePair::inactive)
    ).apply(i, AllelePair::new));
}

// genome codec (chromosomeKeyCodec = ResourceLocation-based key resolved within THIS karyotype):
Codec<IGenome> genomeCodec = Codec.dispatchedMap(chromosomeKeyCodec, c -> pairCodecFor((IChromosome) c))
    .xmap(map -> Genome.sanitizeAlleles(this, map), IGenome::getChromosomes);
```

  `chromosomeKeyCodec` maps a `ResourceLocation`/id to the karyotype's own `IChromosome<?>` (look up in this
  karyotype's chromosome list — no global registry needed). Preserve `sanitizeAlleles` backfill behavior.
- [ ] **Step 2:** Build the matching `StreamCodec<RegistryFriendlyByteBuf, IGenome>` by iterating chromosomes
  in order and encoding each `AllelePair` via `chromosome.valueStreamCodec()` + `ByteBufCodecs.BOOL`.
- [ ] **Step 3:** Replace `isAlleleValid` with `contains` (membership only); delete the `validAlleles` field,
  its population, and `getAlleles`. `getDefaultAlleles()` returns `ImmutableMap<IChromosome<?>, Allele<?>>`.
  Commit WIP.

```bash
git add -A && git commit -m "wip: Karyotype dispatchedMap genome codec + permissive validity (non-compiling)"
```

### Task C3: `Genome` + `Genome.Builder`

**Files:** Modify `core/genetics/Genome.java`.

- [ ] **Step 1:** `Genome` — `getAllelePair` returns `AllelePair<V>`. In `sanitizeAlleles`, the missing-species
  fallback uses the species chromosome's stored id: read `speciesPair.active().value()` (a `ResourceLocation`)
  or fall back to `karyotype.getDefaultSpecies()`; resolve the default genome via
  `karyotype.getSpeciesChromosome().resolver().get(id).getDefaultGenome()`.
- [ ] **Step 2:** `Genome.Builder` — `active`/`inactive` maps become `IdentityHashMap<IChromosome<?>, Allele<?>>`;
  `set/setActive/setInactive(IChromosome<V>, Allele<V>)`; **drop `isAlleleValid` guards** (permissive — keep
  an optional `karyotype.contains(chromosome)` assert); the reference-by-id `set` overload resolves dominance
  from the value's `isDominant()`; `setRemainingDefault` reads `getDefaultAlleles()` (`Allele<?>`); `build()`
  constructs `new AllelePair<>(active, inactive)`. Commit WIP.

```bash
git add -A && git commit -m "wip: Genome + Builder on Allele<V>, permissive set, id-based species fallback (non-compiling)"
```

### Task C4: Rewrite `ForestryAlleles` + chromosome holders

**Files:** Modify `ForestryAlleles.java`, `BeeChromosomes.java`, `TreeChromosomes.java`, `ButterflyChromosomes.java`.

- [ ] **Step 1: `ForestryAlleles`** — **data-value** constants become `Allele<V>`
  (`SPEED_FAST = Allele.dominant(1.2f)`, `SPEED_NORMAL = Allele.recessive(1.0f)`, `TRUE = Allele.dominant(true)`,
  `TOLERANCE_BOTH_1 = Allele.dominant(ToleranceType.BOTH_1)`, `TERRITORY_AVERAGE = Allele.recessive(new Vec3i(9,6,9))`,
  etc.). **Delete all `DEFAULT_*` aggregate lists.** **Delete the reference-allele constants**
  (`ACTIVITY_*`, `FLOWER_TYPE_*`, `EFFECT_*`, `FRUIT_*`, `COCOON_*`, `*_EFFECT_NONE`, `BUTTERFLY_EFFECT_NONE`,
  `TREE_EFFECT_*`) — reference values are now used **by id** directly (`ForestryActivityTypes.DIURNAL`,
  `ForestryFlowerTypes.VANILLA`, `ForestryBeeEffects.NONE`, `ForestryFruits.APPLE`, cocoon/effect ids), which
  already exist as `ResourceLocation` constants. Preserve every data value + dominance exactly (cross-check the
  current file; the golden master catches drift).
- [ ] **Step 2: chromosome holders** — create each chromosome via `ChromosomeFactory`:
  - data chromosomes (SPEED/LIFESPAN/FERTILITY/TEMPERATURE_TOLERANCE/HUMIDITY_TOLERANCE/CAVE_DWELLING/
    TOLERATES_RAIN/POLLINATION/TERRITORY/SIZE/METABOLISM/HEIGHT/SAPLINGS/YIELD/SAPPINESS/MATURATION/GIRTH/
    FIREPROOF/NEVER_SLEEPS …) via the typed factories with their value codecs + naming rules;
  - reference chromosomes (SPECIES, FLOWER_TYPE, EFFECT, ACTIVITY, FRUIT, COCOON, butterfly EFFECT) via
    `referenceChromosome(id, defaultId, resolver, namingRule)`. Resolvers (lazy, called post-population):
    - SPECIES: `id -> speciesType.getSpecies(id)`, `species -> species.id()`;
    - FLOWER_TYPE/EFFECT/ACTIVITY: the bee species-type registries populated in `handleSpeciesRegistration`;
    - FRUIT / tree EFFECT: arboriculture registries; COCOON / butterfly EFFECT: lepidopterology registries.
  Commit WIP.

```bash
git add -A && git commit -m "wip: ForestryAlleles data constants; chromosome holders via factory + reference resolvers (non-compiling)"
```

---

## Phase D — Registration & lifecycle

### Task D1: Species registration, plugin manager, API wiring

**Files:** Modify `apiimpl/plugin/SpeciesRegistration.java`, `apiimpl/plugin/PluginManager.java`, `core/genetics/ChromosomeBuilder.java`, `apiimpl/ForestryApiImpl.java`, `api/IForestryApi.java`.

- [ ] **Step 1:** `SpeciesRegistration.buildAll` — set the species chromosome to an **id-based** allele using
  the builder's declared dominance (breaks the species ← genome ← species cycle; no `registryAllele`, no
  value-populate needed):

```java
defaultGenomeBuilder.setUnchecked(
    speciesChromosome,
    AllelePair.both(new Allele<>(id, builder.isDominant()))   // id = species ResourceLocation
);
```

  Keep the species map for runtime resolution (`speciesType.getSpecies(id)`), but it no longer needs to
  back-fill allele *values*. Replace the `karyotype.isAlleleValid(...)` taxon-default guard with
  `karyotype.contains(chromosome)`.
- [ ] **Step 2:** `PluginManager.registerGenetics` — delete the two `alleleManager.setRegistrationState(...)`
  calls and the `AlleleManager` import/cast.
- [ ] **Step 3:** `ChromosomeBuilder` — drop `addAlleles`/`validAlleles`; keep `setDefault`/`setWeaklyInherited`.
- [ ] **Step 4:** `IForestryApi`/`ForestryApiImpl` — remove `getAlleleManager()`/`setAlleleManager()` and the
  field. Commit WIP.

```bash
git add -A && git commit -m "wip: species chromosome id-based via builder dominance; drop freeze + AlleleManager wiring (non-compiling)"
```

### Task D2: Drop the `IRegistryAlleleValue` marker, keep `isDominant()`

**Files:** Modify `ISpecies`, `IFlowerType`, `IBeeEffect`, `IActivityType`, `IFruit`, `ITreeEffect`, `IButterflyCocoon`, `IButterflyEffect`.

- [ ] **Step 1:** Remove `extends IRegistryAlleleValue` from each, but **declare `boolean isDominant()`
  directly** on each interface (it provided the default dominance for reference defaults and is read when
  building default genomes). Confirm existing implementations still satisfy it. Commit WIP.

```bash
git add -A && git commit -m "wip: drop IRegistryAlleleValue marker, keep isDominant() on value interfaces (non-compiling)"
```

### Task D3: Reference-resolver backing (replaces deleted `IRegistryChromosome.populate`)

The old reference values were stored on `RegistryChromosome` via `populate(ImmutableMap)`, called inside each
`handleSpeciesRegistration` (e.g. `BeeSpeciesType.java:134-136` populates `EFFECT`/`FLOWER_TYPE`/`ACTIVITY`).
Those chromosome types are deleted, so the late-populated maps need a new home and the reference chromosome
resolvers must read from it.

**Files:** `apiculture/genetics/BeeSpeciesType.java`, `arboriculture/genetics/TreeSpeciesType.java`, `lepidopterology/genetics/ButterflySpeciesType.java`; `apiimpl/plugin/ArboricultureRegistration.java`, `apiimpl/plugin/LepidopterologyRegistration.java`.

- [ ] **Step 1:** Store the registry maps on the species type. In each `handleSpeciesRegistration`, replace the
  `<chromosome>.populate(map)` calls with storing the `ImmutableMap<ResourceLocation, V>` on the species type
  (bee: flower types, bee effects, activity — sources already exist as `ApicultureRegistration.getFlowerTypes()`
  /`getBeeEffects()`/`getActivityTypes()`; tree: fruits, tree effects from `ArboricultureRegistration`;
  butterfly: cocoons, butterfly effects from `LepidopterologyRegistration`). Add typed getters
  (e.g. `IBeeSpeciesType.getFlowerType(ResourceLocation)`, `getBeeEffect(...)`, `getActivityType(...)`).
- [ ] **Step 2:** Wire the reference chromosome resolvers (Task C4 Step 2) to those getters, e.g.
  `referenceChromosome(FLOWER_TYPE_ID, defaultId, new IReferenceResolver<>() { get = id -> SpeciesUtil.BEE_TYPE.get().getFlowerType(id); getId = IFlowerType::id-or-reverse-map; }, naming)`.
  The species resolver delegates to `speciesType.getSpecies(id)` / `species.id()`. (If a value type lacks an
  `id()`, keep a reverse `IdentityHashMap<V, ResourceLocation>` built alongside the forward map.)
- [ ] **Step 3:** Commit WIP. (Compiles only at the Phase E gate.)

```bash
git add -A && git commit -m "wip: store reference registries on species types; wire chromosome resolvers (non-compiling)"
```

---

## Phase E — Compiler-guided fixups to the first GREEN build

> Also expect a few local-variable type changes the compiler will flag, e.g.
> `SpeciesRegistration.buildAll`'s `IRegistryChromosome<? extends ISpecies<?>> speciesChromosome` →
> `IChromosome<ResourceLocation>` (follows from Task B3).

### Task E1: Registration data (the largest mechanical churn)

**Files:** `plugin/DefaultForestryPlugin.java`, `plugin/DefaultBeeSpecies.java`, `plugin/DefaultTreeSpecies.java`, `plugin/DefaultButterflySpecies.java`.

- [ ] **Step 1:** In `DefaultForestryPlugin.registerGenetics`, remove all `.addAlleles(...)` calls from the
  karyotype setup; keep `.set(chromosome, default)` and `.setWeaklyInherited(...)`. Reference defaults use ids
  (`karyotype.set(BeeChromosomes.ACTIVITY, ForestryActivityTypes.DIURNAL)`), boolean defaults use literals.
- [ ] **Step 2:** In `DefaultBeeSpecies`/`DefaultTreeSpecies`/`DefaultButterflySpecies`, replace
  `ForestryAlleles.<DATA>` with the surviving `Allele<V>` constants, and `ForestryAlleles.<REFERENCE>` with
  the id form via the genome-builder reference-by-id overload
  (`genome.set(BeeChromosomes.EFFECT, ForestryBeeEffects.BEATIFIC)`). No `addAlleles`.
- [ ] **Step 2 note:** these are also exercised by the golden master — do not change any value or dominance.

### Task E2: Serialization (data components + codecs)

**Files:** `core/CoreDataComponents.java`; audit references to `AllelePair.CODEC`, `IAllele.CODEC`, `alleleCodec()`, `chromosomeCodec()`.

- [ ] **Step 1:** Point the GENOME `DataComponentType` at `IGenome.CODEC` (persistent) and the new
  `getGenomeStreamCodec()` (network). Remove dead references to deleted global codecs.

### Task E3: Migrate direct allele/chromosome/AllelePair consumers

**Files (from the blast-radius inventory — fix what the compiler flags):**
- Direct allele access (`getActiveAllele(...).value()`, reference object expectations): `apiculture/ApicultureFilterRule.java`,
  `apiculture/BeeSpecies.java`, `arboriculture/genetics/TreeGrowthHelper.java`, `arboriculture/tiles/TileLeaves.java`,
  `arboriculture/TreeSpecies.java`, `plugin/DefaultFarms.java`, `apiimpl/client/genetics/AnalyzerScreenGraphics.java`.
- Typed-chromosome-variable declarations → `IChromosome<V>`: `apiculture/genetics/Bee.java`,
  `lepidopterology/genetics/Butterfly.java`, `core/genetics/IndividualLiving.java`, `core/genetics/Species.java`,
  `core/utils/SpeciesUtil.java`, `core/utils/JeiUtil.java`, `core/gui/GuiNaturalistInventory.java`,
  `api/client/genetics/IAnalyzerGraphics.java`.
- `AllelePair` generic-type sites: `apiculture/genetics/Bee.java`, `arboriculture/genetics/Tree.java`,
  `lepidopterology/genetics/Butterfly.java`, `core/genetics/mutations/Mutation.java`, `apiculture/compat/MutationRecipe.java`,
  `api/genetics/IMutation.java`, `api/genetics/ISpecies.java`, `api/genetics/filter/IFilterLogic.java`,
  `sorting/FilterLogic.java`, `lepidopterology/LepidopterologyFilterRule.java`.

- [ ] **Step 1:** Run `./gradlew compileJava`; fix errors in batches. Prefer `getActiveValue(IChromosome<V>)`.
  For reference chromosomes, get the behavior object via `genome.resolveActive(chromosome)` /
  `genome.getActiveSpecies()` / the species-type registries — `getActiveValue` now returns a `ResourceLocation`.
  Replace any `allele == other` identity checks with `.equals`.
- [ ] **Step 2:** When `compileJava` is green, commit.

```bash
./gradlew compileJava   # iterate until BUILD SUCCESSFUL
git add -A && git commit -m "refactor: migrate allele/chromosome/genome consumers to inline-value model"
```

### Task E4: Commands compute candidates from registered species

**Files:** `core/commands/DiagnosticsCommand.java` (~63), `core/commands/ModifyGenomeCommand.java` (~56, ~103).

- [ ] **Step 1:** Replace `karyotype.getAlleles(chromosome)` with a helper gathering distinct values from
  `speciesType.getAllSpecies()` default genomes (`getActiveValue` + `getInactiveValue`), de-duplicated and
  **sorted by value** (natural order for numbers; id/name for reference/enum). Use for `DiagnosticsCommand`
  listing and `ModifyGenomeCommand` suggestions.
- [ ] **Step 2:** Replace the `isAlleleValid` guard in `ModifyGenomeCommand` with permissive parsing: accept
  any value the chromosome's `valueCodec` can parse; require only `karyotype.contains(chromosome)`.
- [ ] **Step 3:** `compileJava` green; commit.

```bash
git add -A && git commit -m "refactor: commands compute allele candidates from registered species (permissive)"
```

### Task E5: Naming at the UI edge (no Component in the model)

**Files:** analyzer/tooltip consumers using the removed `getActiveName`/`getDisplayName`
(`apiimpl/client/genetics/AnalyzerScreenGraphics.java`, `core/gui/GuiNaturalistInventory.java`, + compiler-flagged).

- [ ] **Step 1:** Build `Component`s at the call site via
  `Component.translatableWithFallback(chromosome.translationKey(value), rawString)`. No Component is added to
  the genetics API surface.
- [ ] **Step 2:** `./gradlew compileJava` && `./gradlew compileTestJava` green; commit.

```bash
git add -A && git commit -m "refactor: render allele names from translation keys at the UI edge"
```

---

## Phase F — New behavioral GameTests

### Task F1: Codec round-trip + permissive value

**Files:** Create `src/test/java/forestry/gametest/AlleleFoundationTest.java`.

- [ ] **Step 1:** Tests (impl exists; these lock behavior):
  - Persistent round-trip: non-default bee genome → `IGenome.CODEC` (Nbt/JsonOps) → decode → `isSameAlleles`.
  - Network round-trip: `getGenomeStreamCodec()` encode/decode → equal.
  - Permissive value: set a chromosome to an off-preset value (speed `1.23f`), build, round-trip, assert
    `getActiveValue` reads `1.23f`.
  - Reference resolution: a genome's `getActiveSpecies()` and a flower-type/effect `resolveActive(...)` return
    the correct behavior object for the stored id.
- [ ] **Step 2:** `./gradlew runGameTestServer` → PASS. Commit.

### Task F2: Dominance, inheritance, default-genome construction

**Files:** Modify `AlleleFoundationTest.java`.

- [ ] **Step 1:** Tests:
  - `AllelePair.create` ordering for all four dominance combinations (matches old semantics).
  - `inheritOther`/`inheritHaploid` draw from parents (seeded `RandomSource`).
  - Default-genome construction: Forest bee default genome equals karyotype defaults + taxon defaults +
    species overrides (spot-check several chromosomes incl. the species id).
- [ ] **Step 2:** `./gradlew runGameTestServer` → PASS. Commit.

```bash
git add -A && git commit -m "test: allele foundation gametests (codec, reference resolution, dominance, inheritance, defaults)"
```

---

## Phase G — Verify golden master + full build

### Task G1: Prove built-in species are unchanged

**Files:** Modify `src/test/java/forestry/gametest/GenomeBaselineTest.java` (rewrite the dumper for the new API, reproducing the Task A1 canonical format exactly).

- [ ] **Step 1:** Update the dumper: values via `getActiveValue`/`getInactiveValue` (reference chromosomes
  already yield the `ResourceLocation`), dominance via `getActiveAllele().dominant()`. Render identical
  canonical strings to Phase A.
- [ ] **Step 2:** Assert-mode against the committed baseline.

Run: `./gradlew runGameTestServer`
Expected: PASS — every built-in default genome byte-identical to the pre-refactor baseline. On failure, diff
the report and fix the offending `ForestryAlleles`/registration constant; **do not edit the baseline.**

- [ ] **Step 3:** Full verification.

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (main + tests compile, all gametests pass). Commit.

```bash
git add -A && git commit -m "test: verify default genomes unchanged after allele foundation refactor"
```

---

## Phase H — Optional cleanup

### Task H1: Remove orphan Imprinter assets

**Files:** Delete `src/main/resources/assets/forestry/models/item/imprinter.json` and the two `imprinter.png` textures (verify exact paths first).

- [ ] **Step 1:** `git rm` the orphan assets (no Java references). `./gradlew build` → SUCCESSFUL. Commit.
  Skip if you prefer to keep them.

---

## Done criteria

- `./gradlew build` green (main + tests compile, all gametests pass).
- `GenomeBaselineTest` proves built-in default genomes unchanged (clean break is serialization-format-only).
- No references remain to `IAllele`, `AlleleManager`, `IRegistryAllele(Value)`, the chromosome subtypes,
  `getAlleles`, `addAlleles`, or `AllelePair.CODEC`.
- Genomes serialize values inline (references as ids); no global allele registry or freeze exists.
- Naming uses translation keys only; no `Component` in the genetics API surface.
