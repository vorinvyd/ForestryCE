# Data-Driven Tree Species Implementation Plan (Stage 4)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tree species loadable from datapack JSON (`data/<ns>/tree_species/*.json`), live-reloadable on `/reload`, and client-synced on login, with the 50 built-in trees shipped as generated JSON — no behavior change to default genomes.

**Architecture:** A tree species is split into (a) genetics-layer data → a pure-data `TreeSpeciesDefinition` (JSON shape + sync payload, lazy karyotype-bound codec, mirroring `BeeSpeciesDefinition`), and (b) non-serializable block/worldgen bindings → a code-side `TreeBlockBindings` table keyed by species id, captured from the still-running `DefaultTreeSpecies.register` builders. A `TreeSpeciesManager` (`SimpleJsonResourceReloadListener`) loads the JSON server-side; a `TreeSpeciesSyncPacket` syncs it to clients; `GeneticsReloadHandler.rebuildTreeSpecies` merges JSON + bindings via `TreeSpeciesProjector` into runtime `TreeSpecies` and swaps them into `TreeSpeciesType` (the volatile `setSpecies` from Stage 3). Tree side effects (vanilla-membership maps, `ForestryLeafType` wiring) move into an overridden `setSpecies` so they re-run on every reload. Client sapling models switch from a startup per-species bake to a lazy id-keyed bake.

**Tech Stack:** NeoForge 1.21.1, Java 21, Mojang DataFixerUpper codecs, Gradle (JBR 21).

**Spec:** `docs/superpowers/specs/2026-07-01-data-driven-trees-design.md`

## Global Constraints

- **Build JDK:** every gradle command MUST be prefixed with `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9` (system `java` is 26, which Gradle 9.2.1 rejects).
- **Compile gate:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
- **Gametest gate:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` — the golden master `GenomeBaselineTest` and Stage-2 `MutationRecipeTest` (114 bee / 42 tree / 1 butterfly) MUST stay green every task.
- **Datagen:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`
- **Gametests** go in `src/test/java/forestry/gametest/`, self-registered via `@GameTestHolder(ForestryConstants.MOD_ID)` + `@PrefixGameTestTemplate(false)` on the class and `@GameTest(template = "empty")` on each `public static void method(GameTestHelper helper)` (no central registry — copy the annotations from `BeeSpeciesProjectorTest`).
- Commit after each task with a `feat(genetics):` / `wip(genetics):` message ending with the `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` trailer.
- DRY / YAGNI / TDD / frequent commits. Do **not** restructure unrelated code. Reuse the shared Stage-3 plumbing verbatim: `forestry.core.genetics.GenomeCodecs` (allele-map codecs), `forestry.core.data.RecordingGenomeBuilder` (genome override recorder), `SpeciesRegistration.createDefaultGenomeBuilder` (base genome), `SpeciesRegistration.forEachSpeciesBuilder` (datagen builder iteration).

## Phasing (why this order)

- **Phase A (Tasks 1–7):** purely additive machinery. The existing runtime species path (`handleSpeciesRegistration` → `buildAll()`) stays active, so the game works at every step and `GenomeBaselineTest` stays green.
- **Phase B (Task 8):** datagen emits the JSON as a parallel artifact; `TreeSpeciesEquivalenceTest` proves code-built == JSON-projected. Runtime still uses the old path.
- **Phase C (Tasks 9–10):** the cutover. Task 9 wires the loader/sync (both paths active, identical). Task 10 demotes the old path (runtime = JSON only).
- **Phase D (Task 11):** final verification + review + memory update.

---

## Task 1: `TreeBlockBindings` record + capture in `TreeSpeciesType`

The code-side table of non-serializable per-species bindings. `TreeSpecies` reads `generator`/`vanillaLeafStates`/`vanillaSaplingItems`/`decorativeLeaves` from its builder; `woodType` is NOT read by `TreeSpecies` (it is baked into the `DefaultTreeGenerator`), so it is intentionally excluded. This task is additive — `handleSpeciesRegistration` still returns `buildAll()`.

**Files:**
- Create: `src/main/java/forestry/arboriculture/genetics/TreeBlockBindings.java`
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesType.java` (field + capture + getter)

**Interfaces:**
- Produces: `TreeBlockBindings(ITreeGenerator generator, List<BlockState> vanillaLeafStates, List<Item> vanillaSaplingItems, ItemStack decorativeLeaves)`; `TreeSpeciesType.getBindings(ResourceLocation id)` returning `@Nullable TreeBlockBindings`.

- [ ] **Step 1: The record**

```java
package forestry.arboriculture.genetics;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import forestry.api.arboriculture.ITreeGenerator;

/**
 * The non-serializable, code-registered bindings of a tree species, keyed by species id and captured at plugin
 * registration from the {@code DefaultTreeSpecies} builders. A datapack {@link TreeSpeciesDefinition} carries only the
 * genetics layer; these worldgen/block bindings stay code-side ("code-registered & ID-bound" per the Stage-4 roadmap)
 * and are merged back in by {@link TreeSpeciesProjector}. {@code woodType} is intentionally absent: {@code TreeSpecies}
 * never reads it (it is baked into the {@link ITreeGenerator}).
 */
public record TreeBlockBindings(
	ITreeGenerator generator,
	List<BlockState> vanillaLeafStates,
	List<Item> vanillaSaplingItems,
	ItemStack decorativeLeaves
) {
}
```

- [ ] **Step 2: Field + capture in `TreeSpeciesType`.** Add the import `com.google.common.collect.ImmutableMap` (already present) and a field next to the `fruits`/`treeEffects` fields (`TreeSpeciesType.java:60-64`):

```java
// Code-side per-species block/worldgen bindings, keyed by species id. Captured from the DefaultTreeSpecies builders
// at plugin registration (below); merged into runtime TreeSpecies by TreeSpeciesProjector. Never null after
// handleSpeciesRegistration. Volatile: written on the registration thread, read from the reload/sync projection path.
private volatile ImmutableMap<ResourceLocation, TreeBlockBindings> bindings = ImmutableMap.of();
```

- [ ] **Step 3: Capture in `handleSpeciesRegistration`** (`TreeSpeciesType.java:115-130`). After `this.fruits = registration.getFruits();` and before `return registration.buildAll();`, capture the bindings from the registered builders:

```java
ImmutableMap.Builder<ResourceLocation, TreeBlockBindings> bindings = ImmutableMap.builder();
registration.forEachSpeciesBuilder((id, builder) -> bindings.put(id, new TreeBlockBindings(
	builder.getGenerator(),
	builder.getVanillaLeafStates(),
	builder.getVanillaSaplingItems(),
	builder.getDecorativeLeaves()
)));
this.bindings = bindings.build();
```

(`builder` here is a `TreeSpeciesBuilder`, whose `getGenerator()` is `@Nullable`; the built-ins all set a generator, so it is non-null in practice. `forEachSpeciesBuilder` is inherited from `SpeciesRegistration`.)

- [ ] **Step 4: Getter.** Add near `getFruit`:

```java
@Nullable
public TreeBlockBindings getBindings(ResourceLocation id) {
	return this.bindings.get(id);
}
```

- [ ] **Step 5:** Compile (`compileJava compileTestJava`), run `runGameTestServer` (all green — additive), commit.

---

## Task 2: `TreeSpeciesDefinition` record + lazy codec + stream codec

The genetics-layer JSON shape and sync payload. Lazy codecs keyed against the tree karyotype (not available at class-load) — mirror `BeeSpeciesDefinition` (`src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java`). Reuses `GenomeCodecs` and `ClimateCodecs` verbatim.

**Files:**
- Create: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesDefinition.java`
- Test: `src/test/java/forestry/gametest/TreeSpeciesDefinitionTest.java`

**Interfaces:**
- Produces: `TreeSpeciesDefinition(String genus, String species, boolean dominant, boolean glint, boolean secret, int complexity, String authority, int escritoireColor, TemperatureType temperature, HumidityType humidity, float rarity, Map<ResourceLocation, Allele<?>> genome)`; `TreeSpeciesDefinition.codec()`; `TreeSpeciesDefinition.streamCodec()`.

- [ ] **Step 1: Write the failing test** `TreeSpeciesDefinitionTest`:

```java
package forestry.gametest;

import java.util.Map;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import io.netty.buffer.Unpooled;

import forestry.api.ForestryConstants;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestryTaxa;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesDefinitionTest {
	private static TreeSpeciesDefinition sample() {
		return new TreeSpeciesDefinition(
			ForestryTaxa.GENUS_QUERCUS,
			ForestryTaxa.SPECIES_OAK,
			true,
			false,
			false,
			0,
			"Sengir",
			0x619a3c,
			TemperatureType.NORMAL,
			HumidityType.NORMAL,
			0.0f,
			Map.of(
				// one inline-value chromosome
				TreeChromosomes.HEIGHT.id(), ForestryAlleles.HEIGHT_AVERAGE,
				// one reference chromosome
				TreeChromosomes.FRUIT.id(), Allele.reference(forestry.api.arboriculture.ForestryFruits.APPLE)
			)
		);
	}

	@GameTest(template = "empty")
	public static void codecRoundTrips(GameTestHelper helper) {
		TreeSpeciesDefinition def = sample();
		var ops = net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		var json = TreeSpeciesDefinition.codec().encodeStart(ops, def).getOrThrow();
		TreeSpeciesDefinition decoded = TreeSpeciesDefinition.codec().parse(ops, json).getOrThrow();
		if (!decoded.equals(def)) {
			helper.fail("Codec round-trip mismatch: " + decoded + " != " + def);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void streamCodecRoundTrips(GameTestHelper helper) {
		TreeSpeciesDefinition def = sample();
		// Same idiom MutationRecipeTest uses for its stream-codec round trip.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		TreeSpeciesDefinition.streamCodec().encode(buf, def);
		TreeSpeciesDefinition decoded = TreeSpeciesDefinition.streamCodec().decode(buf);
		if (!decoded.equals(def)) {
			helper.fail("Stream codec round-trip mismatch: " + decoded + " != " + def);
			return;
		}
		helper.succeed();
	}
}
```

(Imports for this test: drop the unused `RegistryAccess`/`CustomPacketPayload` lines; keep `io.netty.buffer.Unpooled`, `net.minecraft.network.RegistryFriendlyByteBuf`, `net.minecraft.resources.RegistryOps`, `com.mojang.serialization.JsonOps`. The codec round-trip uses `RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess())` — replace the `RegistryAccess.EMPTY` in `codecRoundTrips` with `helper.getLevel().registryAccess()` so registry-aware fields decode.)

- [ ] **Step 2: Run the test, verify it fails** (class `TreeSpeciesDefinition` does not exist):

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileTestJava`
Expected: FAIL — `cannot find symbol: class TreeSpeciesDefinition`.

- [ ] **Step 3: Implement `TreeSpeciesDefinition`.** Direct port of `BeeSpeciesDefinition` with tree fields (drop body/stripes/outline/products/specialties/jubilance; add `rarity`). 12 fields fit in one `group()` (max 16), so no `SpritePalette`-style sub-record is needed.

```java
package forestry.arboriculture.genetics;

import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.IForestryApi;
import forestry.api.core.ClimateCodecs;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestrySpeciesTypes;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.core.genetics.GenomeCodecs;

/**
 * Pure-data, datapack-loadable genetics layer of a tree species (the block/worldgen bindings live code-side in
 * {@link TreeBlockBindings}). Also the network sync payload. {@link #codec()}/{@link #streamCodec()} are built lazily
 * against the tree karyotype, which only exists once the tree species type is registered - see
 * {@code forestry.apiculture.genetics.BeeSpeciesDefinition} for the same pattern.
 */
public record TreeSpeciesDefinition(
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
	float rarity,
	Map<ResourceLocation, Allele<?>> genome
) {
	@Nullable
	private static Codec<TreeSpeciesDefinition> codec;
	@Nullable
	private static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec;

	public static Codec<TreeSpeciesDefinition> codec() {
		Codec<TreeSpeciesDefinition> codec = TreeSpeciesDefinition.codec;
		if (codec == null) {
			codec = buildCodec();
			TreeSpeciesDefinition.codec = codec;
		}
		return codec;
	}

	public static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> streamCodec = TreeSpeciesDefinition.streamCodec;
		if (streamCodec == null) {
			streamCodec = buildStreamCodec();
			TreeSpeciesDefinition.streamCodec = streamCodec;
		}
		return streamCodec;
	}

	private static IKaryotype karyotype() {
		return IForestryApi.INSTANCE.getGeneticManager().getSpeciesType(ForestrySpeciesTypes.TREE).getKaryotype();
	}

	private static Codec<TreeSpeciesDefinition> buildCodec() {
		Codec<Map<ResourceLocation, Allele<?>>> genomeCodec = GenomeCodecs.alleleMapCodec(karyotype());
		return RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("genus").forGetter(TreeSpeciesDefinition::genus),
			Codec.STRING.fieldOf("species").forGetter(TreeSpeciesDefinition::species),
			Codec.BOOL.optionalFieldOf("dominant", false).forGetter(TreeSpeciesDefinition::dominant),
			Codec.BOOL.optionalFieldOf("glint", false).forGetter(TreeSpeciesDefinition::glint),
			Codec.BOOL.optionalFieldOf("secret", false).forGetter(TreeSpeciesDefinition::secret),
			Codec.INT.optionalFieldOf("complexity", 0).forGetter(TreeSpeciesDefinition::complexity),
			Codec.STRING.optionalFieldOf("authority", "Sengir").forGetter(TreeSpeciesDefinition::authority),
			Codec.INT.optionalFieldOf("escritoire_color", -1).forGetter(TreeSpeciesDefinition::escritoireColor),
			ClimateCodecs.TEMPERATURE.optionalFieldOf("temperature", TemperatureType.NORMAL).forGetter(TreeSpeciesDefinition::temperature),
			ClimateCodecs.HUMIDITY.optionalFieldOf("humidity", HumidityType.NORMAL).forGetter(TreeSpeciesDefinition::humidity),
			Codec.FLOAT.optionalFieldOf("rarity", 0.0f).forGetter(TreeSpeciesDefinition::rarity),
			genomeCodec.optionalFieldOf("genome", Map.of()).forGetter(TreeSpeciesDefinition::genome)
		).apply(instance, TreeSpeciesDefinition::new));
	}

	private static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> buildStreamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> genomeStreamCodec = GenomeCodecs.alleleMapStreamCodec(karyotype());
		return StreamCodec.of(
			(buf, def) -> {
				buf.writeUtf(def.genus);
				buf.writeUtf(def.species);
				buf.writeBoolean(def.dominant);
				buf.writeBoolean(def.glint);
				buf.writeBoolean(def.secret);
				buf.writeVarInt(def.complexity);
				buf.writeUtf(def.authority);
				buf.writeInt(def.escritoireColor);
				ClimateCodecs.TEMPERATURE_STREAM.encode(buf, def.temperature);
				ClimateCodecs.HUMIDITY_STREAM.encode(buf, def.humidity);
				buf.writeFloat(def.rarity);
				genomeStreamCodec.encode(buf, def.genome);
			},
			buf -> new TreeSpeciesDefinition(
				buf.readUtf(),
				buf.readUtf(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readBoolean(),
				buf.readVarInt(),
				buf.readUtf(),
				buf.readInt(),
				ClimateCodecs.TEMPERATURE_STREAM.decode(buf),
				ClimateCodecs.HUMIDITY_STREAM.decode(buf),
				buf.readFloat(),
				genomeStreamCodec.decode(buf)
			)
		);
	}
}
```

- [ ] **Step 4: Run the test, verify it passes.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer --tests "*TreeSpeciesDefinitionTest*"` (or the full `runGameTestServer`). Expected: PASS. Fix the test's registry-buf idiom against `MutationRecipeTest` if the buffer construction doesn't compile.

- [ ] **Step 5:** Compile, full `runGameTestServer` green, commit.

---

## Task 3: Fail-soft `TreeChromosomes.SPECIES` resolver

Mirror the Stage-3 `BeeChromosomes.resolveSpeciesOrDefault` so a saved individual referencing a removed datapack tree resolves to the default instead of throwing.

**Files:**
- Modify: `src/main/java/forestry/api/genetics/alleles/TreeChromosomes.java:18`
- Test: `src/test/java/forestry/gametest/TreeSpeciesFallbackTest.java`

- [ ] **Step 1: Write the failing test** `TreeSpeciesFallbackTest`:

```java
package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.genetics.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesFallbackTest {
	@GameTest(template = "empty")
	public static void unknownSpeciesIdResolvesToDefault(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ITreeSpecies resolved = TreeChromosomes.SPECIES.resolver().apply(ForestryConstants.forestry("does_not_exist"));
		if (resolved != type.getDefaultSpecies()) {
			helper.fail("Expected unknown tree species id to resolve to the default species");
			return;
		}
		helper.succeed();
	}
}
```

> Confirm the resolver accessor name on `IChromosome` — the memo says `IChromosome.resolver()` returns the id→object function. If the invocation shape differs, adapt (e.g. the resolver may take the id and return the object directly).

- [ ] **Step 2: Run test, verify it fails** — the current resolver calls `getSpecies(id)`, which throws for an unknown id: FAIL (exception, not the default).

- [ ] **Step 3: Implement.** In `TreeChromosomes.java`, replace the `SPECIES` line (`:18`) and add the helper (imports: `forestry.Forestry`):

```java
public static final IChromosome<ResourceLocation> SPECIES = ChromosomeFactory.referenceChromosome(ForestrySpeciesTypes.TREE, TreeChromosomes::resolveSpeciesOrDefault, ITreeSpecies::isDominant);

/**
 * Resolves a tree species id stored in a genome to its species, falling back to the default species (instead of
 * throwing) if a datapack has since removed it. Backs every SPECIES chromosome read (tooltips, analyzer, growth,
 * saved items), so a removed id must never crash those paths.
 */
private static ITreeSpecies resolveSpeciesOrDefault(ResourceLocation id) {
	ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
	ITreeSpecies species = type.getSpeciesSafe(id);
	if (species != null) {
		return species;
	}
	Forestry.LOGGER.warn("Tree species {} not found (removed by a datapack?); falling back to the default species", id);
	return type.getDefaultSpecies();
}
```

(Add imports `forestry.Forestry` and `forestry.api.arboriculture.genetics.ITreeSpeciesType`.)

- [ ] **Step 4: Run test, verify it passes.** Expected: PASS.

- [ ] **Step 5:** Compile, `runGameTestServer` green (`GenomeBaselineTest` unaffected — default genomes never hit a missing id), commit.

---

## Task 4: `DefinitionTreeSpeciesBuilder` adapter + `TreeSpeciesProjector`

Turn a `TreeSpeciesDefinition` + `TreeBlockBindings` into a runtime `TreeSpecies` without touching `TreeSpecies`/`Species` constructors. Mirror `DefinitionBeeSpeciesBuilder`/`BeeSpeciesProjector`.

**Files:**
- Create: `src/main/java/forestry/arboriculture/genetics/DefinitionTreeSpeciesBuilder.java`
- Create: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesProjector.java`
- Test: `src/test/java/forestry/gametest/TreeSpeciesProjectorTest.java`

**Interfaces:**
- Consumes: `TreeSpeciesDefinition` (Task 2), `TreeBlockBindings` + `TreeSpeciesType.getBindings` (Task 1), `SpeciesRegistration.createDefaultGenomeBuilder` (existing).
- Produces: `TreeSpeciesProjector.project(ITreeSpeciesType type, ResourceLocation id, TreeSpeciesDefinition def)` returning `@Nullable TreeSpecies`.

- [ ] **Step 1: The adapter.** Implements `ITreeSpeciesBuilder`. Genetics getters delegate to `def`; block/worldgen getters delegate to `bindings`; every setter + `buildGenome`/`createSpeciesFactory` throws. The complete method set is `ISpeciesBuilder`'s (see `src/main/java/forestry/api/plugin/ISpeciesBuilder.java`) plus `ITreeSpeciesBuilder`'s tree-specific methods:

```java
package forestry.arboriculture.genetics;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Collection;

import forestry.api.arboriculture.ITreeGenData;
import forestry.api.arboriculture.ITreeGenerator;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.IWoodType;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.ISpeciesBuilder;
import forestry.api.plugin.ITreeSpeciesBuilder;

/**
 * Read-only {@link ITreeSpeciesBuilder} adapter over a {@link TreeSpeciesDefinition} + its code-side
 * {@link TreeBlockBindings}: every getter the {@code TreeSpecies}/{@code Species} constructors read is answered from
 * the definition or the bindings; every mutator throws, since datapack species are immutable data.
 *
 * @see TreeSpeciesProjector
 */
public class DefinitionTreeSpeciesBuilder implements ITreeSpeciesBuilder {
	private static final String READ_ONLY_MESSAGE = "datapack species builder is read-only";

	private final TreeSpeciesDefinition def;
	private final TreeBlockBindings bindings;

	public DefinitionTreeSpeciesBuilder(TreeSpeciesDefinition def, TreeBlockBindings bindings) {
		this.def = def;
		this.bindings = bindings;
	}

	// --- genetics getters (from the definition) ---
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
	@Override public float getRarity() { return def.rarity(); }

	// --- block/worldgen getters (from the code-side bindings) ---
	@Override public ITreeGenerator getGenerator() { return bindings.generator(); }
	@Override public List<BlockState> getVanillaLeafStates() { return bindings.vanillaLeafStates(); }
	@Override public List<Item> getVanillaSaplingItems() { return bindings.vanillaSaplingItems(); }
	@Override public ItemStack getDecorativeLeaves() { return bindings.decorativeLeaves(); }

	// --- mutators / factory (all throw) ---
	@Override public ITreeSpeciesBuilder setDominant(boolean dominant) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setGenome(Consumer<IGenomeBuilder> genome) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setGlint(boolean glint) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setTemperature(TemperatureType temperature) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setHumidity(HumidityType humidity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setComplexity(int complexity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setEscritoireColor(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setSecret(boolean secret) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setAuthority(String authority) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setFactory(ISpeciesBuilder.ISpeciesFactory<ITreeSpeciesType, ITreeSpecies, ITreeSpeciesBuilder> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setTreeFeature(Function<ITreeGenData, Feature<NoneFeatureConfiguration>> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setGenerator(ITreeGenerator generator) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaStates(Collection<BlockState> states) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaSapling(Item sapling) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setDecorativeLeaves(ItemStack stack) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setWoodType(IWoodType woodType) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setRarity(float rarity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IGenome buildGenome(IGenomeBuilder builder) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ISpeciesBuilder.ISpeciesFactory<ITreeSpeciesType, ITreeSpecies, ITreeSpeciesBuilder> createSpeciesFactory() { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
```

> If `compileJava` reports a missing or extra `@Override`, reconcile against the actual `ITreeSpeciesBuilder`/`ISpeciesBuilder` method set (the compiler is the source of truth). `getGenerator()` returns `@Nullable ITreeGenerator` per the interface — returning `bindings.generator()` (non-null for built-ins) satisfies it.

- [ ] **Step 2: The projector.** Port `BeeSpeciesProjector`, swapping the jubilance lookup for a bindings lookup (fail-soft skip if absent):

```java
package forestry.arboriculture.genetics;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

import forestry.Forestry;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.IChromosome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IGenomeBuilder;
import forestry.arboriculture.TreeSpecies;
import forestry.apiimpl.plugin.SpeciesRegistration;

/**
 * Projects a pure-data {@link TreeSpeciesDefinition} + its code-side {@link TreeBlockBindings} into a runtime
 * {@link TreeSpecies}, reusing the same {@link SpeciesRegistration#createDefaultGenomeBuilder} genome path as the
 * code-registered species. Fails soft: a missing binding, unknown chromosome, or any exception is logged and yields
 * {@code null} rather than crashing species loading.
 */
public final class TreeSpeciesProjector {
	private TreeSpeciesProjector() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void applyOverrides(IGenomeBuilder builder, IKaryotype karyotype, Map<ResourceLocation, Allele<?>> overrides) {
		for (Map.Entry<ResourceLocation, Allele<?>> e : overrides.entrySet()) {
			IChromosome<?> chromosome = karyotype.getChromosome(e.getKey());
			if (chromosome == null) {
				Forestry.LOGGER.warn("Skipping unknown chromosome {} in tree species genome override", e.getKey());
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

	@Nullable
	public static TreeSpecies project(ITreeSpeciesType type, ResourceLocation id, TreeSpeciesDefinition def) {
		try {
			TreeBlockBindings bindings = ((TreeSpeciesType) type).getBindings(id);
			if (bindings == null) {
				Forestry.LOGGER.warn("Skipping tree species {}: no code-side block/worldgen bindings registered for this id", id);
				return null;
			}
			IKaryotype karyotype = type.getKaryotype();
			IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
			applyOverrides(gb, karyotype, def.genome());
			IGenome genome = gb.build();
			return new TreeSpecies(id, type, genome, new DefinitionTreeSpeciesBuilder(def, bindings));
		} catch (Exception e) {
			Forestry.LOGGER.error("Failed to project tree species {}", id, e);
			return null;
		}
	}
}
```

- [ ] **Step 3: Write the failing test** `TreeSpeciesProjectorTest` — hand-build a definition for a known built-in (e.g. oak), project it, assert genetics fields + bindings + a genome override resolve. Model on `BeeSpeciesProjectorTest`:

```java
package forestry.gametest;

import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.TreeSpecies;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesProjector;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesProjectorTest {
	@GameTest(template = "empty")
	public static void projectsMatchingTreeSpecies(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		// Read the genus/species off the real oak so the definition is faithful.
		var oak = type.getSpecies(ForestryTreeSpecies.OAK);

		TreeSpeciesDefinition def = new TreeSpeciesDefinition(
			oak.getGenus(),
			oak.getSpecies(),
			oak.isDominant(),
			false,
			false,
			0,
			oak.getAuthority(),
			oak.getEscritoireColor(),
			TemperatureType.NORMAL,
			HumidityType.NORMAL,
			oak.getRarity(),
			Map.of(TreeChromosomes.HEIGHT.id(), ForestryAlleles.HEIGHT_LARGE)
		);

		// Project against the real oak id so the code-side bindings are found.
		TreeSpecies projected = TreeSpeciesProjector.project(type, ForestryTreeSpecies.OAK, def);
		if (projected == null) {
			helper.fail("Projection returned null for a valid definition with registered bindings");
			return;
		}
		if (projected.getGenerator() != oak.getGenerator()) {
			helper.fail("Expected the projected species to reuse oak's code-side generator binding");
			return;
		}
		IGenome genome = projected.getDefaultGenome();
		if (genome.getActiveValue(TreeChromosomes.HEIGHT) != ForestryAlleles.HEIGHT_LARGE.value()) {
			helper.fail("Expected default genome HEIGHT override to apply");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void missingBindingsSkips(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		TreeSpeciesDefinition def = new TreeSpeciesDefinition(
			"Quercus", "phantom", false, false, false, 0, "Sengir", -1,
			TemperatureType.NORMAL, HumidityType.NORMAL, 0.0f, Map.of()
		);
		TreeSpecies projected = TreeSpeciesProjector.project(type, ForestryConstants.forestry("phantom_tree_no_bindings"), def);
		if (projected != null) {
			helper.fail("Expected projection to skip (null) a species id with no registered bindings");
			return;
		}
		helper.succeed();
	}
}
```

> `ITreeSpecies` exposes `getGenerator()`, `getVanillaLeafStates()`, and `getRarity()` at the interface level (it extends `ITreeGenData`), so no cast is needed. `ForestryAlleles.HEIGHT_LARGE`/`HEIGHT_AVERAGE` are the confirmed height-allele constant names.

- [ ] **Step 4: Run tests, verify pass.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`. Fix compile issues in the adapter method set against the compiler.

- [ ] **Step 5:** Compile, `runGameTestServer` green, commit.

---

## Task 5: `GeneticsReloadHandler.rebuildTreeSpecies`

Add a tree-typed sibling to the existing `rebuildSpecies` (bees). Keep them separate (different definition/projector types) rather than genericizing.

**Files:**
- Modify: `src/main/java/forestry/core/genetics/GeneticsReloadHandler.java`
- Test: `src/test/java/forestry/gametest/TreeSpeciesReloadTest.java`

**Interfaces:**
- Produces: `GeneticsReloadHandler.rebuildTreeSpecies(Map<ResourceLocation, TreeSpeciesDefinition> defs)`.

- [ ] **Step 1: Write the failing test** `TreeSpeciesReloadTest` — drive `rebuildTreeSpecies` with definitions built from the current live species (round-trip through definitions), assert the species map is repopulated and non-empty:

```java
package forestry.gametest;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.core.data.TreeSpeciesProvider; // available after Task 8; until then, build a single definition inline
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesReloadTest {
	@GameTest(template = "empty")
	public static void rebuildRepopulatesSpecies(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		int before = type.getAllSpecies().size();
		if (before == 0) {
			helper.fail("Expected the code-built path to have populated tree species at setup");
			return;
		}
		Map<ResourceLocation, TreeSpeciesDefinition> defs = TreeSpeciesProvider.buildDefinitions();
		GeneticsReloadHandler.rebuildTreeSpecies(defs);
		if (type.getAllSpecies().size() != before) {
			helper.fail("Expected rebuildTreeSpecies to repopulate the same number of species (" + before + "), got " + type.getAllSpecies().size());
			return;
		}
		helper.succeed();
	}
}
```

> **Sequencing note:** this test references `TreeSpeciesProvider.buildDefinitions()` from Task 8. Since subagent-driven execution does one task at a time, write this test's body to build a **single** definition inline for now (mirror `TreeSpeciesProjectorTest`'s oak definition into a one-entry map, project, assert the map contains that id), and expand it to the full round-trip in Task 8. Keep the method name `rebuildTreeSpecies` stable.

- [ ] **Step 2: Run test, verify it fails** — `rebuildTreeSpecies` does not exist: FAIL to compile.

- [ ] **Step 3: Implement.** Add to `GeneticsReloadHandler` (imports: `forestry.api.arboriculture.genetics.ITreeSpecies`, `forestry.api.arboriculture.genetics.ITreeSpeciesType`, `forestry.arboriculture.TreeSpecies`, `forestry.arboriculture.genetics.TreeSpeciesDefinition`, `forestry.arboriculture.genetics.TreeSpeciesProjector`):

```java
/**
 * Projects each tree definition into a {@link TreeSpecies} (fail-soft: a bad/binding-less definition is logged and
 * dropped by {@link TreeSpeciesProjector#project}) and swaps the resulting map into the live tree species type.
 */
@SuppressWarnings("unchecked")
public static void rebuildTreeSpecies(Map<ResourceLocation, TreeSpeciesDefinition> defs) {
	ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
	ImmutableMap.Builder<ResourceLocation, ITreeSpecies> builder = ImmutableMap.builderWithExpectedSize(defs.size());
	for (Map.Entry<ResourceLocation, TreeSpeciesDefinition> entry : defs.entrySet()) {
		ResourceLocation id = entry.getKey();
		TreeSpecies species = TreeSpeciesProjector.project(type, id, entry.getValue());
		if (species != null) {
			builder.put(id, species);
		}
	}
	ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies = builder.build();
	((SpeciesType<ITreeSpecies, ?>) type).setSpecies(allSpecies);
	Forestry.LOGGER.info("Loaded {} tree species", allSpecies.size());
}
```

- [ ] **Step 4: Run test, verify it passes.** Expected: PASS. Note: after this call the live tree species objects are *rebuilt*; `rebuildMutations` reindexes by identity, but this test does not touch mutations. If a later gametest in the same run depends on the original identity, that is handled by the reload ordering in Task 9 — this test stands alone.

- [ ] **Step 5:** Compile, `runGameTestServer` green, commit.

---

## Task 6: Reload-safe tree side effects (`setSpecies` override)

Move the vanilla-membership + `ForestryLeafType` wiring from `onSpeciesRegistered` into an overridden `setSpecies`, so they re-run on every species swap (setup AND reload), and make the `ForestryLeafType` wiring tolerate a temporarily-missing species. Leave `leafTickHandlers` untouched by the swap so the butterfly-registered `ButterflySpawner` survives reloads.

**Files:**
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesType.java:87-112`
- Test: `src/test/java/forestry/gametest/TreeReloadSideEffectsTest.java`

- [ ] **Step 1: Write the failing test** `TreeReloadSideEffectsTest` — after a `rebuildTreeSpecies`, assert (a) the vanilla-membership map is repopulated for oak's leaf states, and (b) a leaf-tick handler registered before the rebuild still present:

```java
package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.ILeafTickHandler;
import forestry.api.arboriculture.genetics.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.core.data.TreeSpeciesProvider;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeReloadSideEffectsTest {
	@GameTest(template = "empty")
	public static void sideEffectsSurviveReload(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		int handlersBefore = type.getLeafTickHandlers().size();

		GeneticsReloadHandler.rebuildTreeSpecies(TreeSpeciesProvider.buildDefinitions());

		// vanilla membership rebuilt: oak's first vanilla leaf state maps back to a tree individual
		ITreeSpecies oak = type.getSpecies(ForestryTreeSpecies.OAK);
		if (oak.getVanillaLeafStates().isEmpty()) {
			helper.succeed(); // oak has no vanilla states in this build; nothing to assert
			return;
		}
		if (type.getVanillaIndividual(oak.getVanillaLeafStates().get(0)) == null) {
			helper.fail("Expected vanilla-membership map to be rebuilt after a tree species reload");
			return;
		}
		// leaf-tick handlers untouched by the swap
		if (type.getLeafTickHandlers().size() != handlersBefore) {
			helper.fail("Expected leaf-tick handlers to survive a tree species reload (was " + handlersBefore + ", now " + type.getLeafTickHandlers().size() + ")");
			return;
		}
		helper.succeed();
	}
}
```

> Uses `TreeSpeciesProvider.buildDefinitions()` (Task 8). Under subagent-driven execution, if Task 8 isn't done yet, build a full definitions map inline the same way `rebuildTreeSpecies` will be fed, or defer this test's `buildDefinitions()` call until Task 8 and assert only the handler-survival half now.

- [ ] **Step 2: Run test, verify it fails** — today the vanilla map is only built in `onSpeciesRegistered`, which `rebuildTreeSpecies`→`setSpecies` does NOT call, so after a rebuild the map is stale/for old objects: FAIL.

- [ ] **Step 3: Implement.** In `TreeSpeciesType`, override `setSpecies` and reduce `onSpeciesRegistered` to a super delegate. Replace the whole `onSpeciesRegistered` method (`:87-112`) with:

```java
@Override
public void onSpeciesRegistered(ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies) {
	// Base delegates to setSpecies (overridden below), which runs the tree side effects. Kept as an override point.
	super.onSpeciesRegistered(allSpecies);
}

@Override
public void setSpecies(ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies) {
	super.setSpecies(allSpecies);
	rebuildVanillaMembership(allSpecies);
	rebuildLeafTypes(allSpecies);
}

private void rebuildVanillaMembership(ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies) {
	this.vanillaIndividuals.clear();
	this.vanillaItems.clear();
	for (ITreeSpecies entry : allSpecies.values()) {
		ITree defaultIndividual = entry.createIndividual();
		for (BlockState state : entry.getVanillaLeafStates()) {
			this.vanillaIndividuals.put(state, defaultIndividual);
		}
		for (Item item : entry.getVanillaSaplingItems()) {
			this.vanillaItems.put(item, defaultIndividual);
		}
	}
}

private void rebuildLeafTypes(ImmutableMap<ResourceLocation, ITreeSpecies> allSpecies) {
	for (ForestryLeafType type : ForestryLeafType.allValues()) {
		ITreeSpecies species = allSpecies.get(type.getSpeciesId());
		if (species != null) {
			type.setSpecies(species);
		} else {
			// Tolerant: the species may not have loaded yet (empty at setup before the datapack loads, or removed by a
			// datapack). Leave the last-known back-ref and warn instead of throwing, so startup/reload never crashes.
			Forestry.LOGGER.warn("ForestryLeafType {} has no tree species with id {} (not yet loaded or removed by a datapack)", type.getSerializedName(), type.getSpeciesId());
		}
	}
}
```

Add the import `forestry.Forestry`. (`super.setSpecies` is the `@ApiStatus.Internal` volatile swap from Stage 3; overriding it is allowed. Signature: `setSpecies(ImmutableMap<ResourceLocation, ITreeSpecies>)` — confirm the generic against `SpeciesType.setSpecies`.)

- [ ] **Step 4: Run test, verify it passes.** Also re-run the full `runGameTestServer` — `GenomeBaselineTest` unaffected; `onSpeciesRegistered` at setup now routes through the overridden `setSpecies` and does the same work as before (behavior-preserving because at setup the map is the full code-built set).

- [ ] **Step 5:** Compile, `runGameTestServer` green, commit.

---

## Task 7: Lazy id-keyed tree client models

Make the tree client manager id-keyed (like `BeeClientManager`) and `ModelSapling` bake distinct model *locations* resolved by species id at render, tolerating an empty species list at bake time. Item/leaf tints and sprites are already registered by id — only the identity-map indirection and the `requireNonNull(getSpecies(OAK))` need to go.

**Files:**
- Modify: `src/main/java/forestry/api/client/arboriculture/ITreeClientManager.java`
- Modify: `src/main/java/forestry/apiimpl/client/TreeClientManager.java`
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java:260-292`
- Modify: `src/main/java/forestry/arboriculture/models/ModelSapling.java`

**Interfaces:**
- Produces: `ITreeClientManager.getSaplingModels(ResourceLocation speciesId)`, `ITreeClientManager.getDefaultSaplingModels()`, `ITreeClientManager.getAllSaplingModels()` (unchanged signature, still returns all pairs). `getLeafSprite`/`getTint` keep taking `@Nullable ITreeSpecies` but resolve internally by `species.id()`.

- [ ] **Step 1: `TreeClientManager` → id-keyed.** Rewrite the three `IdentityHashMap<ITreeSpecies, …>` fields to id-keyed `Map<ResourceLocation, …>`, and resolve `getLeafSprite`/`getTint`/`getSaplingModels` by `species.id()`. Add a default (oak) sapling-model accessor for the fallback. New file body:

```java
package forestry.apiimpl.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import forestry.api.arboriculture.ForestryTreeSpecies;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.client.arboriculture.ILeafSprite;
import forestry.api.client.arboriculture.ILeafTint;
import forestry.api.client.arboriculture.ITreeClientManager;

public class TreeClientManager implements ITreeClientManager {
	private final Map<ResourceLocation, ILeafSprite> sprites;
	private final Map<ResourceLocation, ILeafTint> tints;
	private final Map<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> models;

	public TreeClientManager(Map<ResourceLocation, ILeafSprite> sprites, Map<ResourceLocation, ILeafTint> tints, Map<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> models) {
		this.sprites = sprites;
		this.tints = tints;
		this.models = models;
	}

	@Override
	public ILeafSprite getLeafSprite(@Nullable ITreeSpecies species) {
		return species == null ? null : this.sprites.get(species.id());
	}

	@Override
	public Collection<ILeafSprite> getAllLeafSprites() {
		return new HashSet<>(this.sprites.values());
	}

	@Override
	public ILeafTint getTint(@Nullable ITreeSpecies species) {
		return species == null ? ILeafTint.DEFAULT : this.tints.getOrDefault(species.id(), ILeafTint.DEFAULT);
	}

	@Override
	public Pair<ResourceLocation, ResourceLocation> getSaplingModels(ITreeSpecies species) {
		Pair<ResourceLocation, ResourceLocation> pair = this.models.get(species.id());
		return pair != null ? pair : getDefaultSaplingModels();
	}

	@Override
	public Pair<ResourceLocation, ResourceLocation> getDefaultSaplingModels() {
		Pair<ResourceLocation, ResourceLocation> oak = this.models.get(ForestryTreeSpecies.OAK);
		if (oak != null) {
			return oak;
		}
		// last-resort literal fallback so bake never NPEs before any species/models are registered
		return Pair.of(ResourceLocation.fromNamespaceAndPath("forestry", "block/oak_sapling"), ResourceLocation.fromNamespaceAndPath("forestry", "item/oak_sapling"));
	}

	@Override
	public Collection<Pair<ResourceLocation, ResourceLocation>> getAllSaplingModels() {
		return Collections.unmodifiableCollection(this.models.values());
	}
}
```

- [ ] **Step 2: `ITreeClientManager`** — add `getDefaultSaplingModels()`:

```java
/**
 * @return The block+item sapling model pair used when a species has no registered pair (defaults to oak's). Never
 * requires the species list, so it is safe before/after a datapack species reload.
 */
Pair<ResourceLocation, ResourceLocation> getDefaultSaplingModels();
```

- [ ] **Step 3: `PluginManager.registerClient`** (tree block, `:260-292`) — build the id-keyed maps directly from `ClientRegistration` **without** iterating `SpeciesUtil.getAllTreeSpecies()`. Replace the whole tree block with:

```java
// Trees
// id-keyed: resolving a species happens at render time by id, so the (datapack-driven) species list is not needed here.
HashMap<ResourceLocation, ILeafSprite> spritesById = registration.getLeafSprites();
HashMap<ResourceLocation, ILeafTint> tintsById = registration.getTints();
HashMap<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> modelsById = registration.getSaplingModels();

// For any species id that has a leaf sprite but no explicit sapling model, synthesize the default-path pair
// (removing the "tree_" prefix), exactly as the old per-species loop did.
Map<ResourceLocation, Pair<ResourceLocation, ResourceLocation>> models = new HashMap<>(modelsById);
for (ResourceLocation id : spritesById.keySet()) {
	models.computeIfAbsent(id, sid -> {
		String path = sid.getPath().replace("tree_", "");
		return Pair.of(
			ResourceLocation.fromNamespaceAndPath(sid.getNamespace(), "block/" + path + "_sapling"),
			ResourceLocation.fromNamespaceAndPath(sid.getNamespace(), "item/" + path + "_sapling")
		);
	});
}

((ForestryClientApiImpl) IForestryClientApi.INSTANCE).setTreeManager(new TreeClientManager(
	new HashMap<>(spritesById), new HashMap<>(tintsById), models
));
```

(Drop the now-unused `List<ITreeSpecies> treeSpecies`, the `IdentityHashMap` locals, and the `FixedLeafTint(species.getEscritoireColor())` fallback — tints missing for a species now fall through to `ILeafTint.DEFAULT` in `getTint`. If `FixedLeafTint`/`ITreeSpecies` imports become unused, remove them. Verify remaining imports.)

> **Behavior note:** the old code used `new FixedLeafTint(species.getEscritoireColor())` as the per-species tint fallback; the new code uses `ILeafTint.DEFAULT`. Check whether any built-in relied on the escritoire-color fallback (grep `FixedLeafTint`). If some do, preserve it by pre-seeding `tintsById` for those ids during client registration instead — but the built-ins register explicit tints, so `DEFAULT` should be reached only for species that never had one. Confirm during review.

- [ ] **Step 4: `ModelSapling`** — bake distinct locations, resolve by id. Rewrite `bake` + `Baked`:

```java
@Override
public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
	ITreeClientManager treeManager = IForestryClientApi.INSTANCE.getTreeManager();
	Map<ResourceLocation, BakedModel> baked = new HashMap<>();

	for (Pair<ResourceLocation, ResourceLocation> pair : treeManager.getAllSaplingModels()) {
		bakeInto(baked, baker, spriteGetter, pair.getFirst());
		bakeInto(baked, baker, spriteGetter, pair.getSecond());
	}
	// ensure the default pair is baked even if no species registered a model
	Pair<ResourceLocation, ResourceLocation> def = treeManager.getDefaultSaplingModels();
	bakeInto(baked, baker, spriteGetter, def.getFirst());
	bakeInto(baked, baker, spriteGetter, def.getSecond());

	return new Baked(treeManager, baked);
}

private static void bakeInto(Map<ResourceLocation, BakedModel> baked, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation location) {
	if (!baked.containsKey(location)) {
		BakedModel model = baker.bake(location, BlockModelRotation.X0_Y0, spriteGetter);
		if (model != null) {
			baked.put(location, model);
		}
	}
}
```

And the `Baked` class keyed by location + resolving by species id (block model from `TileSapling.TREE_SPECIES`, item model from the stack's individual):

```java
public static class Baked implements BakedModel {
	private final ITreeClientManager treeManager;
	private final Map<ResourceLocation, BakedModel> baked;
	@Nullable
	private ItemOverrides overrideList;

	public Baked(ITreeClientManager treeManager, Map<ResourceLocation, BakedModel> baked) {
		this.treeManager = treeManager;
		this.baked = baked;
	}

	private BakedModel blockModelFor(@Nullable ITreeSpecies species) {
		Pair<ResourceLocation, ResourceLocation> pair = species != null ? this.treeManager.getSaplingModels(species) : this.treeManager.getDefaultSaplingModels();
		BakedModel model = this.baked.get(pair.getFirst());
		return model != null ? model : this.baked.get(this.treeManager.getDefaultSaplingModels().getFirst());
	}

	private BakedModel itemModelFor(ITreeSpecies species) {
		Pair<ResourceLocation, ResourceLocation> pair = this.treeManager.getSaplingModels(species);
		BakedModel model = this.baked.get(pair.getSecond());
		return model != null ? model : this.baked.get(this.treeManager.getDefaultSaplingModels().getSecond());
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
		return blockModelFor(extraData.get(TileSapling.TREE_SPECIES)).getQuads(state, side, rand);
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
		return getQuads(state, side, rand, ModelData.EMPTY, null);
	}

	@Override public boolean useAmbientOcclusion() { return blockModelFor(null).useAmbientOcclusion(); }
	@Override public boolean isGui3d() { return blockModelFor(null).isGui3d(); }
	@Override public boolean usesBlockLight() { return false; }
	@Override public boolean isCustomRenderer() { return false; }
	@Override public TextureAtlasSprite getParticleIcon() { return blockModelFor(null).getParticleIcon(); }

	@Override
	public TextureAtlasSprite getParticleIcon(ModelData data) {
		return blockModelFor(data.get(TileSapling.TREE_SPECIES)).getParticleIcon();
	}

	@Override
	public ItemOverrides getOverrides() {
		if (this.overrideList == null) {
			this.overrideList = new OverrideList();
		}
		return this.overrideList;
	}

	public class OverrideList extends ItemOverrides {
		@Nullable
		@Override
		public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
			IIndividual individual = IIndividualHandlerItem.getIndividual(stack);
			if (individual == null) {
				return model;
			}
			return itemModelFor((ITreeSpecies) individual.getSpecies());
		}
	}
}
```

Update imports (`java.util.HashMap`, `java.util.Map`, drop `IdentityHashMap`/`Objects`/`ForestryTreeSpecies`/`ITreeSpeciesType`/`SpeciesUtil` if now unused). `individual.getSpecies()` returns `ISpecies` — cast to `ITreeSpecies` (a sapling individual is always a tree).

- [ ] **Step 5: Leaf-model empty-tolerance audit.** Check `ModelDefaultLeaves.java:83`, `ModelDecorativeLeaves.java:51`, `ModelLeaves.java:106` — they call `getTreeManager().getLeafSprite(species)`. With `getLeafSprite` now returning `null` for an unknown/absent species (unchanged behavior — it returned null before too for unmapped species), confirm each already guards null or falls back to `getDefaultSpecies()`. If any dereferences a null sprite without a guard, add `if (sprite == null) { species = SpeciesUtil.TREE_TYPE.get().getDefaultSpecies(); sprite = ...getLeafSprite(species); }`. Only add a guard where one is missing; do not restructure.

- [ ] **Step 6:** Compile (`compileJava` covers client). There is no gametest for client rendering; verify `compileJava compileTestJava` green and `runGameTestServer` (server-only) still passes. Commit. (Manual in-game render check deferred to Task 11.)

---

## Task 8: `TreeSpeciesProvider` datagen + generated JSON + equivalence test

Generate `data/forestry/tree_species/*.json` for all 50 built-ins from the `DefaultTreeSpecies` builders (mirror `BeeSpeciesProvider`), and prove code-built == JSON-projected. Trees need **no** companion instance→id inversion (fruit/effect are reference chromosomes recorded as `Allele.reference(id)`), so `buildDefinitions` is simpler than the bee one.

**Files:**
- Create: `src/main/java/forestry/core/data/TreeSpeciesProvider.java`
- Modify: `src/main/java/forestry/core/data/Data.java:61` (add provider) and `:66-79` (seed live tree species)
- Generated: `src/generated/resources/data/forestry/tree_species/*.json`
- Test: `src/test/java/forestry/gametest/TreeSpeciesEquivalenceTest.java`

**Interfaces:**
- Produces: `TreeSpeciesProvider.buildDefinitions()` → `Map<ResourceLocation, TreeSpeciesDefinition>`; `TreeSpeciesProvider.seedLiveSpeciesForDatagen()`.

- [ ] **Step 1: The provider.** Port `BeeSpeciesProvider`, dropping the jubilance reverse-map:

```java
package forestry.core.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.plugin.ITreeSpeciesBuilder;
import forestry.apiimpl.plugin.ArboricultureRegistration;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.utils.SpeciesUtil;
import forestry.plugin.DefaultTreeSpecies;

/**
 * Generates {@code data/forestry/tree_species/*.json} for every built-in tree, read directly from the
 * {@code DefaultTreeSpecies} builders via {@link ArboricultureRegistration#forEachSpeciesBuilder} - the same builders
 * the code-registration path uses, so the generated definitions are a faithful parallel artifact (proven by
 * {@code TreeSpeciesEquivalenceTest}).
 */
public class TreeSpeciesProvider implements DataProvider {
	private final PackOutput.PathProvider pathProvider;
	private final CompletableFuture<HolderLookup.Provider> lookupProvider;

	public TreeSpeciesProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "tree_species");
		this.lookupProvider = lookupProvider;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		return this.lookupProvider.thenCompose(provider -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
			List<CompletableFuture<?>> futures = new ArrayList<>();
			buildDefinitions().forEach((id, def) -> {
				JsonElement json = TreeSpeciesDefinition.codec().encodeStart(ops, def).getOrThrow();
				futures.add(DataProvider.saveStable(cache, json, this.pathProvider.json(id)));
			});
			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		});
	}

	public static Map<ResourceLocation, TreeSpeciesDefinition> buildDefinitions() {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		ArboricultureRegistration reg = new ArboricultureRegistration(type);
		DefaultTreeSpecies.register(reg);

		Map<ResourceLocation, TreeSpeciesDefinition> definitions = new LinkedHashMap<>();
		reg.forEachSpeciesBuilder((id, builder) -> definitions.put(id, buildDefinition(builder)));
		return definitions;
	}

	/**
	 * Populates the live tree species type directly from {@link #buildDefinitions()}, bypassing the datapack JSON
	 * round trip. Only for the standalone data generator ({@code Data#preDataGen}), which never fires the datapack
	 * reload that loads species at real server start. Species built here come from the identical {@code
	 * DefaultTreeSpecies} source the generated JSON is derived from.
	 */
	public static void seedLiveSpeciesForDatagen() {
		GeneticsReloadHandler.rebuildTreeSpecies(buildDefinitions());
	}

	private static TreeSpeciesDefinition buildDefinition(ITreeSpeciesBuilder builder) {
		RecordingGenomeBuilder rec = new RecordingGenomeBuilder();
		builder.buildGenome(rec);
		return new TreeSpeciesDefinition(
			builder.getGenus(),
			builder.getSpecies(),
			builder.isDominant(),
			builder.hasGlint(),
			builder.isSecret(),
			builder.getComplexity(),
			builder.getAuthority(),
			builder.getEscritoireColor(),
			builder.getTemperature(),
			builder.getHumidity(),
			builder.getRarity(),
			rec.overrides
		);
	}

	@Override
	public String getName() {
		return "Forestry Tree Species";
	}
}
```

> `buildDefinition` takes `ITreeSpeciesBuilder` because `getRarity()` is on that interface; `forEachSpeciesBuilder` yields the concrete `TreeSpeciesBuilder` (a subtype), which is assignable. `RecordingGenomeBuilder` is the shared Stage-3 class (`forestry.core.data.RecordingGenomeBuilder`).

- [ ] **Step 2: Wire the provider** in `Data.gatherData` (`Data.java:61`, next to `BeeSpeciesProvider`):

```java
generator.addProvider(event.includeServer(), new TreeSpeciesProvider(output, lookup));
```

- [ ] **Step 3: Seed live tree species for datagen** in `Data.preDataGen` (`:66-79`), next to `BeeSpeciesProvider.seedLiveSpeciesForDatagen();`:

```java
// Tree species come from datapack JSON at real server start; datagen never fires that reload, so seed the live
// tree type from the same DefaultTreeSpecies source any stack-baking provider/loot needs.
TreeSpeciesProvider.seedLiveSpeciesForDatagen();
```

> At this task the old code path still builds trees at setup, so this seed is redundant-but-harmless (it rebuilds the same species from the same source). It becomes load-bearing after Task 10. `rebuildTreeSpecies` rebuilds species objects — run this seed **before** any provider that bakes tree stacks; placing it in `preDataGen` (which runs before `gatherData` wires providers) satisfies that.

- [ ] **Step 4: Generate.** Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`. Confirm `src/generated/resources/data/forestry/tree_species/` has 50 JSON files. Inspect 2-3 (genus/species/genome present; defaults like `dominant`/`temperature` omitted when default; `rarity` present only when non-zero).

- [ ] **Step 5: Equivalence test** `TreeSpeciesEquivalenceTest` — for each built-in in the live `TREE_TYPE.getAllSpecies()` (old path, still active), load the generated JSON from the test classpath, decode via `TreeSpeciesDefinition.codec()`, project via `TreeSpeciesProjector.project`, and assert equality on genetics fields + resolved bindings + default genome. Model on `BeeSpeciesEquivalenceTest`:

```java
package forestry.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.TreeSpecies;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesProjector;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeSpeciesEquivalenceTest {
	@GameTest(template = "empty")
	public static void generatedJsonMatchesCodeBuilt(GameTestHelper helper) {
		ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		for (ITreeSpecies expected : type.getAllSpecies()) {
			ResourceLocation id = expected.id();
			String path = "/data/" + id.getNamespace() + "/tree_species/" + id.getPath() + ".json";
			try (InputStream in = TreeSpeciesEquivalenceTest.class.getResourceAsStream(path)) {
				if (in == null) {
					helper.fail("Missing generated JSON for tree species " + id + " at " + path);
					return;
				}
				JsonElement json = JsonParser.parseReader(new InputStreamReader(in));
				TreeSpeciesDefinition def = TreeSpeciesDefinition.codec().parse(ops, json).getOrThrow();
				TreeSpecies projected = TreeSpeciesProjector.project(type, id, def);
				if (projected == null) {
					helper.fail("Projection returned null for " + id);
					return;
				}
				if (!equivalent(expected, projected)) {
					helper.fail("Projected tree species " + id + " does not match the code-built one");
					return;
				}
			} catch (Exception e) {
				helper.fail("Equivalence check threw for " + id + ": " + e);
				return;
			}
		}
		helper.succeed();
	}

	private static boolean equivalent(ITreeSpecies a, ITreeSpecies b) {
		if (!a.getGenus().equals(b.getGenus()) || !a.getSpecies().equals(b.getSpecies())) return false;
		if (a.isDominant() != b.isDominant() || a.hasGlint() != b.hasGlint() || a.isSecret() != b.isSecret()) return false;
		if (a.getComplexity() != b.getComplexity() || a.getEscritoireColor() != b.getEscritoireColor()) return false;
		if (!a.getAuthority().equals(b.getAuthority())) return false;
		if (a.getTemperature() != b.getTemperature() || a.getHumidity() != b.getHumidity()) return false;
		if (Float.compare(a.getRarity(), b.getRarity()) != 0) return false;
		if (a.getGenerator() != b.getGenerator()) return false; // same code-side binding instance
		return genomeEquals(a.getDefaultGenome(), b.getDefaultGenome());
	}

	private static boolean genomeEquals(IGenome a, IGenome b) {
		// Compare the full chromosome->AllelePair maps, exactly as BeeSpeciesEquivalenceTest does.
		Map<IChromosome<?>, AllelePair<?>> ca = a.getChromosomes();
		Map<IChromosome<?>, AllelePair<?>> cb = b.getChromosomes();
		if (!ca.keySet().equals(cb.keySet())) return false;
		for (Map.Entry<IChromosome<?>, AllelePair<?>> e : ca.entrySet()) {
			if (!e.getValue().equals(cb.get(e.getKey()))) return false;
		}
		return true;
	}
}
```

> Imports for `genomeEquals`: `java.util.Map`, `forestry.api.genetics.alleles.IChromosome`, `forestry.api.genetics.alleles.AllelePair`. This is the same `getChromosomes()`-map comparison `BeeSpeciesEquivalenceTest` uses (`src/test/java/forestry/gametest/BeeSpeciesEquivalenceTest.java:274`) — copy its exact helper and drop the per-chromosome `TreeChromosomes` import if it becomes unused. `AllelePair` equality covers both active and inactive alleles per chromosome, so the default-genome guarantee (§10) holds.

- [ ] **Step 6:** Compile, `runData` (idempotent — no diff on a clean second run in `tree_species/`), `runGameTestServer` green (including the new equivalence test), commit **including the generated JSON**.

---

## Task 9: Loader + sync wiring (cutover part 1 — both paths active)

Add the tree species manager + sync packet and wire both sides. After this, tree species are set by BOTH the old setup path AND the new load/sync path — identical (JSON exists from Task 8), so everything stays green.

**Files:**
- Create: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesManager.java`
- Create: `src/main/java/forestry/core/network/packets/TreeSpeciesSyncPacket.java`
- Modify: `src/main/java/forestry/core/network/PacketIdClient.java` (add id)
- Modify: `src/main/java/forestry/core/ModuleCore.java:223` (register manager), `:240-242` (send tree packet too), `:290` (register packet)
- Modify: `src/main/java/forestry/core/client/CoreClientHandler.java:310-313` (no change needed — see Step 6)

- [ ] **Step 1: `TreeSpeciesManager`** — port `BeeSpeciesManager` (folder `"tree_species"`, delegate to `rebuildTreeSpecies`):

```java
package forestry.arboriculture.genetics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import forestry.Forestry;
import forestry.core.genetics.GeneticsReloadHandler;

/**
 * Datapack loader for tree species: a {@link SimpleJsonResourceReloadListener} over the {@code tree_species} folder.
 * Mirrors {@code forestry.apiculture.genetics.BeeSpeciesManager}. Decodes each entry via
 * {@link TreeSpeciesDefinition#codec()} (fail-soft), stores the map, and hands it to
 * {@link GeneticsReloadHandler#rebuildTreeSpecies}. The client reuses this singleton as a plain data holder for the
 * definitions delivered by {@code TreeSpeciesSyncPacket} (see {@link #setDefinitions}).
 */
public final class TreeSpeciesManager extends SimpleJsonResourceReloadListener {
	public static final TreeSpeciesManager INSTANCE = new TreeSpeciesManager();

	private static final String FOLDER = "tree_species";

	private volatile Map<ResourceLocation, TreeSpeciesDefinition> definitions = Map.of();

	private TreeSpeciesManager() {
		super(new Gson(), FOLDER);
	}

	public Map<ResourceLocation, TreeSpeciesDefinition> getDefinitions() {
		return this.definitions;
	}

	public void setDefinitions(Map<ResourceLocation, TreeSpeciesDefinition> definitions) {
		this.definitions = definitions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		// getRegistryLookup() (NeoForge ContextAwareReloadListener) carries the current reload's registry access,
		// populated before apply() for both the cold server start and every /reload - NOT ServerLifecycleHooks
		// (null on cold start). See BeeSpeciesManager's apply() for the full rationale.
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, TreeSpeciesDefinition> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<TreeSpeciesDefinition> result = TreeSpeciesDefinition.codec().parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping tree species {}: {}", id, error))
				.ifPresent(def -> parsed.put(id, def));
		}

		this.definitions = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildTreeSpecies(this.definitions);
	}
}
```

- [ ] **Step 2: `TreeSpeciesSyncPacket`** — port `BeeSpeciesSyncPacket`:

```java
package forestry.core.network.packets;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesManager;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;

/**
 * Server -&gt; client sync of the loaded tree species definitions, sent on login/reload. Mirrors
 * {@code BeeSpeciesSyncPacket}. No-ops on an integrated server (shared singletons already authoritative). The
 * client-side mutation index is rebuilt from the already-synced recipe manager, species-before-mutations.
 */
public record TreeSpeciesSyncPacket(Map<ResourceLocation, TreeSpeciesDefinition> definitions) implements CustomPacketPayload {
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.TREE_SPECIES_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, TreeSpeciesSyncPacket msg) {
		definitionsStreamCodec().encode(buffer, msg.definitions);
	}

	public static TreeSpeciesSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new TreeSpeciesSyncPacket(definitionsStreamCodec().decode(buffer));
	}

	public static void handle(TreeSpeciesSyncPacket msg, Player player) {
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		TreeSpeciesManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildTreeSpecies(msg.definitions);
		GeneticsReloadHandler.rebuildMutations(Minecraft.getInstance().getConnection().getRecipeManager());
	}

	private static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, TreeSpeciesDefinition>> definitionsStreamCodec() {
		return ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, TreeSpeciesDefinition.streamCodec());
	}
}
```

- [ ] **Step 3: Packet id** in `PacketIdClient` (next to `BEE_SPECIES_SYNC`, use the existing `type(...)` helper):

```java
public static final CustomPacketPayload.Type<TreeSpeciesSyncPacket> TREE_SPECIES_SYNC = type("tree_species_sync");
```

(import `forestry.core.network.packets.TreeSpeciesSyncPacket`.)

- [ ] **Step 4: Register the packet** in `ModuleCore.registerPackets` (after the `BEE_SPECIES_SYNC` line, `:290`):

```java
registry.clientbound(PacketIdClient.TREE_SPECIES_SYNC, TreeSpeciesSyncPacket::encode, TreeSpeciesSyncPacket::decode, TreeSpeciesSyncPacket::handle);
```

(import `forestry.core.network.packets.TreeSpeciesSyncPacket`.)

- [ ] **Step 5: Server reload listener** in `ModuleCore.registerReloadListeners` — register `TreeSpeciesManager.INSTANCE` right after `BeeSpeciesManager.INSTANCE` (`:223`), BEFORE the mutation-rebuild listener (apply order = registration order; mutations must resolve species that exist):

```java
event.addListener(TreeSpeciesManager.INSTANCE);
```

(import `forestry.arboriculture.genetics.TreeSpeciesManager`.)

- [ ] **Step 6: Datapack sync** in `ModuleCore.onDatapackSync` (`:240-242`) — send the tree packet alongside the bee packet:

```java
private static void onDatapackSync(OnDatapackSyncEvent event) {
	BeeSpeciesSyncPacket beePacket = new BeeSpeciesSyncPacket(BeeSpeciesManager.INSTANCE.getDefinitions());
	TreeSpeciesSyncPacket treePacket = new TreeSpeciesSyncPacket(TreeSpeciesManager.INSTANCE.getDefinitions());
	event.getRelevantPlayers().forEach(player -> {
		NetworkUtil.sendToPlayer(beePacket, player);
		NetworkUtil.sendToPlayer(treePacket, player);
	});
}
```

(imports: `forestry.core.network.packets.TreeSpeciesSyncPacket`, `forestry.arboriculture.genetics.TreeSpeciesManager`.) **Client `CoreClientHandler.onRecipesUpdated` needs NO change** — it already rebuilds mutations only; tree species arrive via the sync packet, exactly like bees.

- [ ] **Step 7: Extend `TreeSpeciesReloadTest`** (from Task 5) to assert the manager loaded species at server start: `TreeSpeciesManager.INSTANCE.getDefinitions().size()` is 50 and `rebuildTreeSpecies(getDefinitions())` yields the full built-in set. Confirm the server log prints `Loaded 50 tree species`.

- [ ] **Step 8:** Compile, `runGameTestServer` green (species now loaded from JSON at server start AND the old setup path — identical), confirm the `Loaded 50 tree species` log line, commit.

---

## Task 10: Demote the runtime `buildAll` path (cutover part 2 — JSON only)

Stop building tree species at setup so datapacks are the sole runtime source. Keep the bindings capture and companion registries.

**Files:**
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesType.java:129` (return empty instead of `buildAll()`)
- Modify: missing-species fallback audit (tree `getSpecies` call sites on player-facing/saved paths)
- Test: `src/test/java/forestry/gametest/TreeSpeciesFallbackTest.java` (extend)

- [ ] **Step 1: Return empty species at setup.** In `TreeSpeciesType.handleSpeciesRegistration` (`:129`), change the final line from `return registration.buildAll();` to:

```java
// Tree species are no longer built at setup; they come exclusively from the tree_species datapack loader. The
// companion reference registries (fruits/effects), the TreeManager, and the block/worldgen bindings above are
// still captured here so projection can resolve them.
return ImmutableMap.of();
```

(Keep everything above: `treeEffects`/`fruits` capture, `setTreeManager`, and the Task-1 bindings capture. `PluginManager.registerGenetics` already calls `onSpeciesRegistered(empty)` → the overridden `setSpecies(empty)` runs the tolerant leaf-type wiring from Task 6 without throwing. The empty-species throw guard was already removed in Stage 3.)

- [ ] **Step 2: Fallback audit.** Grep tree `getSpecies` call sites that handle stale/saved/player-facing data:

Run: `grep -rn "TREE_TYPE.get().getSpecies(\|getTreeSpecies(\|TREE_TYPE).get().getSpecies(" src/main/java`

For each site that could now see a removed/absent id (analyzer, tooltips, the germling/pollen item, `ModelSapling`/leaf models already handled in Task 7), switch to `getSpeciesSafe(...)` + fall back to `getDefaultSpecies()` where the code handles saved or player-facing data. The `TreeChromosomes.SPECIES` resolver (Task 3) already covers genome-decode paths; this step covers direct-lookup paths. Keep changes minimal and behavior-preserving for present species. If a site is guaranteed a live id (e.g. iterating `getAllSpecies()`), leave it.

- [ ] **Step 3: Extend `TreeSpeciesFallbackTest`** — assert a definitions map containing one binding-less id yields a species set missing only that entry (no crash), and that `setSpecies(ImmutableMap.of())` followed by `getAllSpecies()` returns empty without throwing:

```java
@GameTest(template = "empty")
public static void bindinglessDefinitionSkippedNoCrash(GameTestHelper helper) {
	ITreeSpeciesType type = SpeciesUtil.TREE_TYPE.get();
	java.util.Map<ResourceLocation, forestry.arboriculture.genetics.TreeSpeciesDefinition> defs =
		new java.util.LinkedHashMap<>(forestry.core.data.TreeSpeciesProvider.buildDefinitions());
	defs.put(ForestryConstants.forestry("phantom_no_bindings"), new forestry.arboriculture.genetics.TreeSpeciesDefinition(
		"Quercus", "phantom", false, false, false, 0, "Sengir", -1,
		forestry.api.core.TemperatureType.NORMAL, forestry.api.core.HumidityType.NORMAL, 0.0f, java.util.Map.of()));
	forestry.core.genetics.GeneticsReloadHandler.rebuildTreeSpecies(defs);
	if (type.getSpeciesSafe(ForestryConstants.forestry("phantom_no_bindings")) != null) {
		helper.fail("Expected the binding-less phantom species to be skipped");
		return;
	}
	if (type.getAllSpecies().isEmpty()) {
		helper.fail("Expected the real built-ins to still load");
		return;
	}
	helper.succeed();
}
```

- [ ] **Step 4:** Compile, `runData` (idempotent), `runGameTestServer` — now the REAL proof: trees exist only because the manager loaded the generated JSON at server start. `GenomeBaselineTest` (default genomes from JSON projection) and `MutationRecipeTest` (114/42/1 — tree mutations find their data-driven species) MUST be green. Commit.

---

## Task 11: Final verification + review + memory update

**Files:** none (verification) + any fixes surfaced.

- [ ] **Step 1:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava` — green.
- [ ] **Step 2:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData` — green and **idempotent** (no working-tree diff in `src/generated/.../tree_species/` on a clean second run; ignore the pre-existing `farm_*` lang non-determinism noted in memory).
- [ ] **Step 3:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` — all gametests green, including `GenomeBaselineTest`, `MutationRecipeTest` (114/42/1), and the new Stage-4 tests; logs show `Loaded 50 tree species`.
- [ ] **Step 4:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew build` — green.
- [ ] **Step 5: Manual smoke (if a dev client is available):** launch the client, place a Forestry sapling, confirm it renders and grows; confirm leaves render tinted; `/reload` after editing a generated `tree_species/*.json` (e.g. change `rarity` or a genome override) and confirm the change applies live; confirm a fresh client receives trees on login. Confirm a butterfly still spawns on leaves after a `/reload` (leaf-tick handler survived). If no client is available, note it and rely on gametests.
- [ ] **Step 6:** Dispatch a final code-reviewer over the whole Stage-4 diff (`git diff <stage-4-base>..HEAD`) against the spec. Fix any real issues.
- [ ] **Step 7:** Update the project memory file `data-driven-genetics-overhaul.md` with a "Stage 4 COMPLETE & VERIFIED" section (mechanism, key files, the genetics/bindings split, the reload-safe `setSpecies` override, gotchas), mirroring the Stage 3 section. Note Stage 5 (butterflies) is next.

---

## Risks & gotchas (read before starting)

- **Generated JSON must exist before Task 10.** Never demote (`buildAll` → empty) before Task 8's JSON is committed, or the game has zero trees.
- **Reload-safe side effects (Task 6) must precede the demotion (Task 10).** Once trees are empty at setup, the `ForestryLeafType` wiring would throw the old `IllegalStateException`; the tolerant `rebuildLeafTypes` fixes that. It also must run on the reload swap — hence moving it into the overridden `setSpecies`.
- **Leaf-tick handlers survive reloads because `setSpecies` never touches `leafTickHandlers`.** Do not clear that list in `setSpecies`; the butterfly-registered `ButterflySpawner` (registered once at setup by `ButterflySpeciesType.onSpeciesRegistered`) must persist across tree `/reload`s.
- **Reference vs data chromosome dispatch** (Tasks 4 & 8) is load-bearing for byte-identical genomes: reference chromosomes (`SPECIES`/`FRUIT`/`EFFECT`, `resolver() != null`) use the `ResourceLocation` set-overload; inline-value chromosomes use the `Allele` overload; `RecordingGenomeBuilder` records reference sets as `Allele.reference(id)`. `GenomeBaselineTest` catches mistakes here.
- **`woodType` is NOT a `TreeBlockBindings` field** — `TreeSpecies` never reads it (it is baked into the `DefaultTreeGenerator`). Do not add it; YAGNI.
- **Client model bake tolerates empty species** (Task 7): `ModelSapling` bakes distinct model *locations* (from `getAllSaplingModels()` + the default pair), never iterating species, and resolves by id at render with an oak default fallback — so bake at client startup (before the sync packet) is fine.
- **Reload apply ordering** (Task 9): register `TreeSpeciesManager` before the mutation-rebuild listener. `SimpleJsonResourceReloadListener.apply` already runs on the game executor (confirmed for `BeeSpeciesManager`), so no extra marshalling.
- **Single-player identity churn** (Task 9): `TreeSpeciesSyncPacket.handle` no-ops on `hasSingleplayerServer()` — the shared server singletons are already authoritative; re-projecting would desync the identity-keyed `MutationManager`.
- **Datagen seed** (Task 8): `seedLiveSpeciesForDatagen` in `preDataGen` — trees are empty at real server start until the datapack loads, but datagen never fires that reload, so any provider/loot baking a tree stack needs the live type seeded. Prefer id-referencing in loot where possible (audit grafter/villager/pollen sites).
- **Don't break butterflies** (Stage 5 not done): butterflies still build species at setup and register their leaf-tick handler on the tree type — unaffected by this stage as long as the tree species swap leaves `leafTickHandlers` alone.
