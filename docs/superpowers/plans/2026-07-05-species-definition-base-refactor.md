# Shared `SpeciesDefinition` Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the triplicated structure across the three data-driven species definitions
(`{Bee,Tree,Butterfly}SpeciesDefinition`) — their codecs, read-only builder adapters, projectors, and
test literals — with **zero behavior change**.

**Architecture:** Introduce four shared types in `forestry.core.genetics` — an `ISpeciesDefinition`
interface (base accessors, implemented by all three flat records), a `SpeciesCore` codec-carrier
record (the 10 shared base fields, inlined into each codec via the existing sub-record destructure
idiom), an `AbstractDefinitionSpeciesBuilder` base (the ~22 shared read-only adapter methods), and a
`SpeciesProjection.buildGenome` helper (the shared genome-build skeleton). Records stay flat, so
`def.genus()` consumers and datagen providers are untouched. A light per-type test builder replaces 11
of 14 positional literals.

**Tech Stack:** Java 21 (JBR), NeoForge 21.1.x, Mojang DFU codecs (`Codec`/`MapCodec`/`StreamCodec`),
Minecraft GameTest harness.

## Global Constraints

- **Build JDK:** every gradle call MUST be prefixed with `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9`. The system default `java` (26) is rejected by Gradle 9.2.1.
- **Behavior-preserving refactor.** No change to JSON shape, packet wire bytes, default genomes, or the karyotype-keyed lazy-codec pattern. The pre-existing golden-master gametests are the regression gate — they must stay **GREEN** after every task, never edited to pass (except Task 5, which migrates test *construction* only, preserving each test's asserts + snapshot/restore scaffolding verbatim).
- **Authoritative gate:** `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer` = **55/55 pass**, with `Loaded 69 bee species` / `Loaded 50 tree species` / `Loaded 35 butterfly species` and `114`/`42`/`1` mutation recipes at cold server start, all unchanged.
- **Shared types package:** `forestry.core.genetics` (internal), alongside `GenomeCodecs`/`GenomeProjection`.
- **`READ_ONLY_MESSAGE` value** (unchanged): `"datapack species builder is read-only"`.
- **Codec defaults for the base-10 fields** (unchanged, exact): `dominant`/`glint`/`secret` → `false`; `complexity` → `0`; `authority` → `"Sengir"`; `escritoire_color` → `-1`; `temperature`/`humidity` → `NORMAL`. `genus`/`species` are required (`fieldOf`). Stream order: `writeUtf genus, writeUtf species, writeBoolean dominant, writeBoolean glint, writeBoolean secret, writeVarInt complexity, writeUtf authority, writeInt escritoireColor, TEMPERATURE_STREAM, HUMIDITY_STREAM`.

**Reference — the shared base fields** (identical name/type/order in all three records):
`String genus, String species, boolean dominant, boolean glint, boolean secret, int complexity, String authority, int escritoireColor, TemperatureType temperature, HumidityType humidity`, plus the trailing `Map<ResourceLocation, Allele<?>> genome`.

---

### Task 1: `ISpeciesDefinition` interface + records implement it

**Files:**
- Create: `src/main/java/forestry/core/genetics/ISpeciesDefinition.java`
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java` (declaration + import)
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesDefinition.java` (declaration + import)
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java` (declaration + import)

**Interfaces:**
- Produces: `forestry.core.genetics.ISpeciesDefinition` with accessors `genus()`, `species()`, `dominant()`, `glint()`, `secret()`, `complexity()`, `authority()`, `escritoireColor()`, `temperature()`, `humidity()`, `genome()`. All three definition records implement it (record accessors auto-satisfy). Tasks 3 & 4 read base fields through this interface.

- [ ] **Step 1: Create the interface**

`src/main/java/forestry/core/genetics/ISpeciesDefinition.java`:
```java
package forestry.core.genetics;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.alleles.Allele;

/**
 * The base genetics/metadata shared by every data-driven species definition
 * ({@code BeeSpeciesDefinition}, {@code TreeSpeciesDefinition}, {@code ButterflySpeciesDefinition}).
 * The three records are flat and implement this interface directly (their record accessors satisfy
 * it), letting the shared adapter base ({@link AbstractDefinitionSpeciesBuilder}) and projection
 * helper ({@link SpeciesProjection}) read base fields polymorphically without a common supertype.
 */
public interface ISpeciesDefinition {
	String genus();

	String species();

	boolean dominant();

	boolean glint();

	boolean secret();

	int complexity();

	String authority();

	int escritoireColor();

	TemperatureType temperature();

	HumidityType humidity();

	Map<ResourceLocation, Allele<?>> genome();
}
```

- [ ] **Step 2: Make `BeeSpeciesDefinition` implement it**

In `BeeSpeciesDefinition.java`, add the import (with the other `forestry.core.genetics` import):
```java
import forestry.core.genetics.ISpeciesDefinition;
```
Change the record declaration closing line from:
```java
	Map<ResourceLocation, Allele<?>> genome
) {
```
to:
```java
	Map<ResourceLocation, Allele<?>> genome
) implements ISpeciesDefinition {
```

- [ ] **Step 3: Make `TreeSpeciesDefinition` implement it**

In `TreeSpeciesDefinition.java`, add import `import forestry.core.genetics.ISpeciesDefinition;` and change:
```java
	Map<ResourceLocation, Allele<?>> genome
) {
```
to:
```java
	Map<ResourceLocation, Allele<?>> genome
) implements ISpeciesDefinition {
```

- [ ] **Step 4: Make `ButterflySpeciesDefinition` implement it**

In `ButterflySpeciesDefinition.java`, add import `import forestry.core.genetics.ISpeciesDefinition;` and change:
```java
	Map<ResourceLocation, Allele<?>> genome
) {
```
to:
```java
	Map<ResourceLocation, Allele<?>> genome
) implements ISpeciesDefinition {
```

- [ ] **Step 5: Compile**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`. (Records already expose accessors of the exact names/types, so implementing the interface adds no methods.)

- [ ] **Step 6: Run the golden-master gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: 55/55 pass — behavior unchanged (this task adds only a marker interface).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/forestry/core/genetics/ISpeciesDefinition.java \
        src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java \
        src/main/java/forestry/arboriculture/genetics/TreeSpeciesDefinition.java \
        src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java
git commit -m "refactor(genetics): add ISpeciesDefinition, implemented by the 3 definition records"
```

---

### Task 2: `SpeciesCore` codec carrier + route all three codecs through it

**Files:**
- Create: `src/main/java/forestry/core/genetics/SpeciesCore.java`
- Modify: `src/main/java/forestry/core/genetics/ISpeciesDefinition.java` (add `core()` default)
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java` (codec + stream codec)
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesDefinition.java` (codec + stream codec)
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java` (codec + stream codec; delete nested `Tail`)

**Interfaces:**
- Consumes: `ISpeciesDefinition` (Task 1).
- Produces: `SpeciesCore` record + `SpeciesCore.MAP_CODEC` (`MapCodec<SpeciesCore>`, inlines to top-level JSON keys) + `SpeciesCore.STREAM_CODEC` (`StreamCodec<RegistryFriendlyByteBuf, SpeciesCore>`). `ISpeciesDefinition.core()` default builds a `SpeciesCore` from the base accessors.

- [ ] **Step 1: Create `SpeciesCore`**

`src/main/java/forestry/core/genetics/SpeciesCore.java`:
```java
package forestry.core.genetics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import forestry.api.core.ClimateCodecs;
import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;

/**
 * The 10 base genetics/metadata fields shared by every {@link ISpeciesDefinition}, extracted purely so
 * their codec + stream codec are written once instead of triplicated. {@link #MAP_CODEC} is a
 * {@link MapCodec}, so composing it into a definition codec inlines the same top-level JSON keys the
 * definitions used before — the serialized shape is byte-identical. {@code genome} is NOT part of this
 * record: its codec is karyotype-keyed and stays factored through {@code GenomeCodecs}.
 */
public record SpeciesCore(
	String genus,
	String species,
	boolean dominant,
	boolean glint,
	boolean secret,
	int complexity,
	String authority,
	int escritoireColor,
	TemperatureType temperature,
	HumidityType humidity
) {
	public static final MapCodec<SpeciesCore> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.STRING.fieldOf("genus").forGetter(SpeciesCore::genus),
		Codec.STRING.fieldOf("species").forGetter(SpeciesCore::species),
		Codec.BOOL.optionalFieldOf("dominant", false).forGetter(SpeciesCore::dominant),
		Codec.BOOL.optionalFieldOf("glint", false).forGetter(SpeciesCore::glint),
		Codec.BOOL.optionalFieldOf("secret", false).forGetter(SpeciesCore::secret),
		Codec.INT.optionalFieldOf("complexity", 0).forGetter(SpeciesCore::complexity),
		Codec.STRING.optionalFieldOf("authority", "Sengir").forGetter(SpeciesCore::authority),
		Codec.INT.optionalFieldOf("escritoire_color", -1).forGetter(SpeciesCore::escritoireColor),
		ClimateCodecs.TEMPERATURE.optionalFieldOf("temperature", TemperatureType.NORMAL).forGetter(SpeciesCore::temperature),
		ClimateCodecs.HUMIDITY.optionalFieldOf("humidity", HumidityType.NORMAL).forGetter(SpeciesCore::humidity)
	).apply(instance, SpeciesCore::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeciesCore> STREAM_CODEC = StreamCodec.of(
		(buf, core) -> {
			buf.writeUtf(core.genus());
			buf.writeUtf(core.species());
			buf.writeBoolean(core.dominant());
			buf.writeBoolean(core.glint());
			buf.writeBoolean(core.secret());
			buf.writeVarInt(core.complexity());
			buf.writeUtf(core.authority());
			buf.writeInt(core.escritoireColor());
			ClimateCodecs.TEMPERATURE_STREAM.encode(buf, core.temperature());
			ClimateCodecs.HUMIDITY_STREAM.encode(buf, core.humidity());
		},
		buf -> new SpeciesCore(
			buf.readUtf(),
			buf.readUtf(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readBoolean(),
			buf.readVarInt(),
			buf.readUtf(),
			buf.readInt(),
			ClimateCodecs.TEMPERATURE_STREAM.decode(buf),
			ClimateCodecs.HUMIDITY_STREAM.decode(buf)
		)
	);
}
```

- [ ] **Step 2: Add the `core()` default method to `ISpeciesDefinition`**

In `ISpeciesDefinition.java`, after the `genome()` method (before the closing brace), add:
```java

	/**
	 * @return A {@link SpeciesCore} view of this definition's base fields, used by the definition codecs
	 * to serialize the shared fields through one shared codec.
	 */
	default SpeciesCore core() {
		return new SpeciesCore(genus(), species(), dominant(), glint(), secret(),
			complexity(), authority(), escritoireColor(), temperature(), humidity());
	}
```

- [ ] **Step 3: Route `BeeSpeciesDefinition` codecs through `SpeciesCore`**

In `BeeSpeciesDefinition.java`, add import `import forestry.core.genetics.SpeciesCore;` (next to the existing `forestry.core.genetics.GenomeCodecs` import). Replace the entire `buildCodec()` method body with:
```java
	private static Codec<BeeSpeciesDefinition> buildCodec() {
		Codec<Map<ResourceLocation, Allele<?>>> genomeCodec = GenomeCodecs.alleleMapCodec(karyotype());
		return RecordCodecBuilder.create(instance -> instance.group(
			SpeciesCore.MAP_CODEC.forGetter(BeeSpeciesDefinition::core),
			SpritePalette.CODEC.forGetter(def -> new SpritePalette(def.body(), def.stripes(), def.outline())),
			Product.CODEC.listOf().optionalFieldOf("products", List.of()).forGetter(BeeSpeciesDefinition::products),
			Product.CODEC.listOf().optionalFieldOf("specialties", List.of()).forGetter(BeeSpeciesDefinition::specialties),
			ResourceLocation.CODEC.optionalFieldOf("jubilance", DEFAULT_JUBILANCE).forGetter(BeeSpeciesDefinition::jubilance),
			genomeCodec.optionalFieldOf("genome", Map.of()).forGetter(BeeSpeciesDefinition::genome)
		).apply(instance, (core, palette, products, specialties, jubilance, genome) ->
			new BeeSpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
				core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
				palette.body(), palette.stripes(), palette.outline(), products, specialties, jubilance, genome)));
	}
```
Replace the entire `buildStreamCodec()` method body with:
```java
	private static StreamCodec<RegistryFriendlyByteBuf, BeeSpeciesDefinition> buildStreamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> genomeStreamCodec = GenomeCodecs.alleleMapStreamCodec(karyotype());
		StreamCodec<RegistryFriendlyByteBuf, List<Product>> productListStreamCodec = Product.STREAM_CODEC.apply(ByteBufCodecs.list());
		return StreamCodec.of(
			(buf, def) -> {
				SpeciesCore.STREAM_CODEC.encode(buf, def.core());
				buf.writeInt(def.body);
				buf.writeInt(def.stripes);
				buf.writeInt(def.outline);
				productListStreamCodec.encode(buf, def.products);
				productListStreamCodec.encode(buf, def.specialties);
				ResourceLocation.STREAM_CODEC.encode(buf, def.jubilance);
				genomeStreamCodec.encode(buf, def.genome);
			},
			buf -> {
				SpeciesCore core = SpeciesCore.STREAM_CODEC.decode(buf);
				int body = buf.readInt();
				int stripes = buf.readInt();
				int outline = buf.readInt();
				List<Product> products = productListStreamCodec.decode(buf);
				List<Product> specialties = productListStreamCodec.decode(buf);
				ResourceLocation jubilance = ResourceLocation.STREAM_CODEC.decode(buf);
				Map<ResourceLocation, Allele<?>> genome = genomeStreamCodec.decode(buf);
				return new BeeSpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
					core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
					body, stripes, outline, products, specialties, jubilance, genome);
			}
		);
	}
```
Then remove now-unused imports flagged by the compiler: `com.mojang.serialization.MapCodec` and `forestry.api.core.ClimateCodecs`, `forestry.api.core.HumidityType`, `forestry.api.core.TemperatureType` are still referenced by the `SpritePalette` record and the record header — **do not remove those**; only remove an import if `compileJava` reports it unused. (Expected: `ClimateCodecs` becomes unused here and should be removed; `TemperatureType`/`HumidityType` stay — they are record component types.)

> Note: `SpritePalette` is intentionally kept (`body`/`stripes`/`outline` is a genuine semantic grouping). Its `MapCodec` import stays used.

- [ ] **Step 4: Route `TreeSpeciesDefinition` codecs through `SpeciesCore`**

In `TreeSpeciesDefinition.java`, add import `import forestry.core.genetics.SpeciesCore;`. Replace `buildCodec()` with:
```java
	private static Codec<TreeSpeciesDefinition> buildCodec() {
		Codec<Map<ResourceLocation, Allele<?>>> genomeCodec = GenomeCodecs.alleleMapCodec(karyotype());
		return RecordCodecBuilder.create(instance -> instance.group(
			SpeciesCore.MAP_CODEC.forGetter(TreeSpeciesDefinition::core),
			Codec.FLOAT.optionalFieldOf("rarity", 0.0f).forGetter(TreeSpeciesDefinition::rarity),
			genomeCodec.optionalFieldOf("genome", Map.of()).forGetter(TreeSpeciesDefinition::genome)
		).apply(instance, (core, rarity, genome) -> new TreeSpeciesDefinition(
			core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
			core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
			rarity, genome)));
	}
```
Replace `buildStreamCodec()` with:
```java
	private static StreamCodec<RegistryFriendlyByteBuf, TreeSpeciesDefinition> buildStreamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> genomeStreamCodec = GenomeCodecs.alleleMapStreamCodec(karyotype());
		return StreamCodec.of(
			(buf, def) -> {
				SpeciesCore.STREAM_CODEC.encode(buf, def.core());
				buf.writeFloat(def.rarity);
				genomeStreamCodec.encode(buf, def.genome);
			},
			buf -> {
				SpeciesCore core = SpeciesCore.STREAM_CODEC.decode(buf);
				float rarity = buf.readFloat();
				Map<ResourceLocation, Allele<?>> genome = genomeStreamCodec.decode(buf);
				return new TreeSpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
					core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
					rarity, genome);
			}
		);
	}
```
Remove the now-unused `import forestry.api.core.ClimateCodecs;` if `compileJava` flags it. Keep `TemperatureType`/`HumidityType` (record component types).

- [ ] **Step 5: Route `ButterflySpeciesDefinition` codecs through `SpeciesCore` and delete `Tail`**

In `ButterflySpeciesDefinition.java`, add import `import forestry.core.genetics.SpeciesCore;`. **Delete the entire nested `private record Tail(...) { ... }`** block (the `Tail` record and its `codec(IKaryotype)` method). Keep the `PRODUCTS_CODEC` and `PRODUCTS_STREAM_CODEC` static fields. Replace `buildCodec()` with:
```java
	private static Codec<ButterflySpeciesDefinition> buildCodec() {
		Codec<Map<ResourceLocation, Allele<?>>> genomeCodec = GenomeCodecs.alleleMapCodec(karyotype());
		return RecordCodecBuilder.create(instance -> instance.group(
			SpeciesCore.MAP_CODEC.forGetter(ButterflySpeciesDefinition::core),
			Codec.BOOL.optionalFieldOf("nocturnal", false).forGetter(ButterflySpeciesDefinition::nocturnal),
			Codec.BOOL.optionalFieldOf("moth", false).forGetter(ButterflySpeciesDefinition::moth),
			Codec.FLOAT.optionalFieldOf("rarity", 0.0f).forGetter(ButterflySpeciesDefinition::rarity),
			Codec.FLOAT.optionalFieldOf("flight_distance", 5.0f).forGetter(ButterflySpeciesDefinition::flightDistance),
			Codec.INT.optionalFieldOf("serum_color", 0).forGetter(ButterflySpeciesDefinition::serumColor),
			TagKey.codec(Registries.BIOME).optionalFieldOf("spawn_biomes").forGetter(ButterflySpeciesDefinition::spawnBiomes),
			PRODUCTS_CODEC.optionalFieldOf("products", List.of()).forGetter(ButterflySpeciesDefinition::products),
			PRODUCTS_CODEC.optionalFieldOf("caterpillar_products", List.of()).forGetter(ButterflySpeciesDefinition::caterpillarProducts),
			genomeCodec.optionalFieldOf("genome", Map.of()).forGetter(ButterflySpeciesDefinition::genome)
		).apply(instance, (core, nocturnal, moth, rarity, flightDistance, serumColor, spawnBiomes, products, caterpillarProducts, genome) ->
			new ButterflySpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
				core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
				nocturnal, moth, rarity, flightDistance, serumColor, spawnBiomes, products, caterpillarProducts, genome)));
	}
```
Replace `buildStreamCodec()` with:
```java
	private static StreamCodec<RegistryFriendlyByteBuf, ButterflySpeciesDefinition> buildStreamCodec() {
		StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Allele<?>>> genomeStreamCodec = GenomeCodecs.alleleMapStreamCodec(karyotype());
		StreamCodec<ByteBuf, Optional<TagKey<Biome>>> spawnBiomesStreamCodec = ByteBufCodecs.optional(
			ResourceLocation.STREAM_CODEC.map(location -> TagKey.create(Registries.BIOME, location), TagKey::location));
		return StreamCodec.of(
			(buf, def) -> {
				SpeciesCore.STREAM_CODEC.encode(buf, def.core());
				buf.writeBoolean(def.nocturnal);
				buf.writeBoolean(def.moth);
				buf.writeFloat(def.rarity);
				buf.writeFloat(def.flightDistance);
				buf.writeInt(def.serumColor);
				spawnBiomesStreamCodec.encode(buf, def.spawnBiomes);
				PRODUCTS_STREAM_CODEC.encode(buf, def.products);
				PRODUCTS_STREAM_CODEC.encode(buf, def.caterpillarProducts);
				genomeStreamCodec.encode(buf, def.genome);
			},
			buf -> {
				SpeciesCore core = SpeciesCore.STREAM_CODEC.decode(buf);
				boolean nocturnal = buf.readBoolean();
				boolean moth = buf.readBoolean();
				float rarity = buf.readFloat();
				float flightDistance = buf.readFloat();
				int serumColor = buf.readInt();
				Optional<TagKey<Biome>> spawnBiomes = spawnBiomesStreamCodec.decode(buf);
				List<IProduct> products = PRODUCTS_STREAM_CODEC.decode(buf);
				List<IProduct> caterpillarProducts = PRODUCTS_STREAM_CODEC.decode(buf);
				Map<ResourceLocation, Allele<?>> genome = genomeStreamCodec.decode(buf);
				return new ButterflySpeciesDefinition(core.genus(), core.species(), core.dominant(), core.glint(), core.secret(),
					core.complexity(), core.authority(), core.escritoireColor(), core.temperature(), core.humidity(),
					nocturnal, moth, rarity, flightDistance, serumColor, spawnBiomes, products, caterpillarProducts, genome);
			}
		);
	}
```
Remove the now-unused `import forestry.api.core.ClimateCodecs;` and `import com.mojang.serialization.MapCodec;` if `compileJava` flags them (both were only used by base fields / the deleted `Tail`). Keep `TemperatureType`/`HumidityType` (record component types), `RecordCodecBuilder`, `Codec`, `StreamCodec`, `ByteBufCodecs`, `ByteBuf`, `Registries`, `TagKey`, `Biome`, `Optional`, `IProduct`, `Product`.

- [ ] **Step 6: Compile**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL` (fix any "unused import" only after confirming the type is truly unreferenced).

- [ ] **Step 7: Run the golden-master + round-trip gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: 55/55 pass. The decisive tests here are `BeeSpeciesDefinitionTest`, `TreeSpeciesDefinitionTest`, `ButterflySpeciesDefinitionTest` (JSON + stream round-trips over every field, incl. reference-chromosome genome overrides + the biome tag) and the three `*SpeciesEquivalenceTest` golden masters — all prove the `SpeciesCore`/`Tail`-removal changes are wire-transparent.

- [ ] **Step 8: Prove JSON is byte-identical**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData`
Then: `git status --short src/generated/resources/data`
Expected: **no** changes under `bee_species/`, `tree_species/`, or `butterfly_species/` (the datapack JSON is unchanged; confirms `SpeciesCore` inlines to the identical keys). If unrelated pre-existing datagen flakes appear (`farm_*`/`leaves`/`letter` names, `.cache`), revert them — they are not caused by this task.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/forestry/core/genetics/SpeciesCore.java \
        src/main/java/forestry/core/genetics/ISpeciesDefinition.java \
        src/main/java/forestry/apiculture/genetics/BeeSpeciesDefinition.java \
        src/main/java/forestry/arboriculture/genetics/TreeSpeciesDefinition.java \
        src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesDefinition.java
git commit -m "refactor(genetics): share base-field codec via SpeciesCore; drop butterfly Tail"
```

---

### Task 3: `AbstractDefinitionSpeciesBuilder` base + reparent the three adapters

**Files:**
- Create: `src/main/java/forestry/core/genetics/AbstractDefinitionSpeciesBuilder.java`
- Modify (rewrite): `src/main/java/forestry/apiculture/genetics/DefinitionBeeSpeciesBuilder.java`
- Modify (rewrite): `src/main/java/forestry/arboriculture/genetics/DefinitionTreeSpeciesBuilder.java`
- Modify (rewrite): `src/main/java/forestry/lepidopterology/genetics/DefinitionButterflySpeciesBuilder.java`

**Interfaces:**
- Consumes: `ISpeciesDefinition` (Task 1).
- Produces: `AbstractDefinitionSpeciesBuilder<D extends ISpeciesDefinition, T extends ISpeciesType<S, ?>, S extends ISpecies<?>, B extends ISpeciesBuilder<T, S, B>>` implementing `ISpeciesBuilder<T, S, B>`, holding `protected final D def`, exposing `protected static final String READ_ONLY_MESSAGE`, implementing the 10 base getters + 12 throwing base setters/factory methods. Each concrete adapter `extends` it and adds only its type-specific members.

- [ ] **Step 1: Create the abstract base**

`src/main/java/forestry/core/genetics/AbstractDefinitionSpeciesBuilder.java`:
```java
package forestry.core.genetics;

import java.util.function.Consumer;

import net.minecraft.network.chat.TextColor;

import forestry.api.core.HumidityType;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.plugin.IGenomeBuilder;
import forestry.api.plugin.ISpeciesBuilder;

/**
 * Shared read-only {@link ISpeciesBuilder} adapter over an {@link ISpeciesDefinition}: answers every
 * base getter the {@code Species} constructor reads from the definition, and throws from every base
 * mutator/factory method, since datapack species are immutable data. Concrete per-type subclasses add
 * only their type-specific getters (and their type-specific throwing setters).
 *
 * @param <D> the concrete definition type, so subclasses can read type-specific fields off {@link #def}.
 */
public abstract class AbstractDefinitionSpeciesBuilder<
	D extends ISpeciesDefinition,
	T extends ISpeciesType<S, ?>,
	S extends ISpecies<?>,
	B extends ISpeciesBuilder<T, S, B>>
	implements ISpeciesBuilder<T, S, B> {

	protected static final String READ_ONLY_MESSAGE = "datapack species builder is read-only";

	protected final D def;

	protected AbstractDefinitionSpeciesBuilder(D def) {
		this.def = def;
	}

	// --- base getters (from the definition) ---
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

	// --- base mutators / factory (all throw) ---
	@Override public B setDominant(boolean dominant) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setGenome(Consumer<IGenomeBuilder> genome) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setGlint(boolean glint) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setTemperature(TemperatureType temperature) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setHumidity(HumidityType humidity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setComplexity(int complexity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setEscritoireColor(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setSecret(boolean secret) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setAuthority(String authority) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public B setFactory(ISpeciesBuilder.ISpeciesFactory<T, S, B> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IGenome buildGenome(IGenomeBuilder builder) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ISpeciesBuilder.ISpeciesFactory<T, S, B> createSpeciesFactory() { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
```

- [ ] **Step 2: Rewrite `DefinitionBeeSpeciesBuilder`**

Replace the entire file with:
```java
package forestry.apiculture.genetics;

import java.util.List;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import forestry.api.apiculture.IBeeJubilance;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.IProduct;
import forestry.api.plugin.IBeeSpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link IBeeSpeciesBuilder} adapter over a {@link BeeSpeciesDefinition}: the base
 * getters/mutators come from {@link AbstractDefinitionSpeciesBuilder}; this class adds the bee-specific
 * sprite-color/product/jubilance getters and throws from the bee-specific mutators.
 *
 * @see BeeSpeciesProjector
 */
public class DefinitionBeeSpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<BeeSpeciesDefinition, IBeeSpeciesType, IBeeSpecies, IBeeSpeciesBuilder>
	implements IBeeSpeciesBuilder {

	private final IBeeJubilance jubilance;

	public DefinitionBeeSpeciesBuilder(BeeSpeciesDefinition def, IBeeJubilance jubilance) {
		super(def);
		this.jubilance = jubilance;
	}

	@Override public List<IProduct> buildProducts() { return List.copyOf(def.products()); }
	@Override public List<IProduct> buildSpecialties() { return List.copyOf(def.specialties()); }
	@Override public int getBody() { return def.body(); }
	@Override public int getStripes() { return def.stripes(); }
	@Override public int getOutline() { return def.outline(); }
	@Override public IBeeJubilance getJubilance() { return this.jubilance; }

	@Override public IBeeSpeciesBuilder addProduct(IProduct product) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addProduct(ItemStack stack, float chance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addSpecialty(IProduct specialty) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder addSpecialty(ItemStack stack, float chance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setBody(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setStripes(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setOutline(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IBeeSpeciesBuilder setJubilance(IBeeJubilance jubilance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
```

- [ ] **Step 3: Rewrite `DefinitionTreeSpeciesBuilder`**

Replace the entire file with:
```java
package forestry.arboriculture.genetics;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import forestry.api.arboriculture.ITreeGenData;
import forestry.api.arboriculture.ITreeGenerator;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.IWoodType;
import forestry.api.arboriculture.genetics.ITreeSpeciesType;
import forestry.api.plugin.ITreeSpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link ITreeSpeciesBuilder} adapter over a {@link TreeSpeciesDefinition} + its code-side
 * {@link TreeBlockBindings}: base getters/mutators come from {@link AbstractDefinitionSpeciesBuilder};
 * this class adds the tree-specific rarity/block/worldgen getters (from the definition or the bindings)
 * and throws from the tree-specific mutators.
 *
 * @see TreeSpeciesProjector
 */
public class DefinitionTreeSpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<TreeSpeciesDefinition, ITreeSpeciesType, ITreeSpecies, ITreeSpeciesBuilder>
	implements ITreeSpeciesBuilder {

	private final TreeBlockBindings bindings;

	public DefinitionTreeSpeciesBuilder(TreeSpeciesDefinition def, TreeBlockBindings bindings) {
		super(def);
		this.bindings = bindings;
	}

	@Override public float getRarity() { return def.rarity(); }

	// --- block/worldgen getters (from the code-side bindings) ---
	@Override public ITreeGenerator getGenerator() { return bindings.generator(); }
	@Override public List<BlockState> getVanillaLeafStates() { return bindings.vanillaLeafStates(); }
	@Override public List<Item> getVanillaSaplingItems() { return bindings.vanillaSaplingItems(); }
	@Override public ItemStack getDecorativeLeaves() { return bindings.decorativeLeaves(); }

	// --- tree-specific mutators (all throw) ---
	@Override public ITreeSpeciesBuilder setTreeFeature(Function<ITreeGenData, Feature<NoneFeatureConfiguration>> factory) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setGenerator(ITreeGenerator generator) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaStates(Collection<BlockState> states) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder addVanillaSapling(Item sapling) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setDecorativeLeaves(ItemStack stack) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setWoodType(IWoodType woodType) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public ITreeSpeciesBuilder setRarity(float rarity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
```

- [ ] **Step 4: Rewrite `DefinitionButterflySpeciesBuilder`**

Replace the entire file with:
```java
package forestry.lepidopterology.genetics;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import forestry.api.core.IProduct;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.api.lepidopterology.genetics.IButterflySpeciesType;
import forestry.api.plugin.IButterflySpeciesBuilder;
import forestry.core.genetics.AbstractDefinitionSpeciesBuilder;

/**
 * Read-only {@link IButterflySpeciesBuilder} adapter over a {@link ButterflySpeciesDefinition}: base
 * getters/mutators come from {@link AbstractDefinitionSpeciesBuilder}; this class adds the
 * butterfly-specific getters and throws from the butterfly-specific mutators. Butterflies have no
 * code-side block/worldgen bindings (unlike trees), so there is nothing else to adapt.
 *
 * @see ButterflySpeciesProjector
 */
public class DefinitionButterflySpeciesBuilder
	extends AbstractDefinitionSpeciesBuilder<ButterflySpeciesDefinition, IButterflySpeciesType, IButterflySpecies, IButterflySpeciesBuilder>
	implements IButterflySpeciesBuilder {

	public DefinitionButterflySpeciesBuilder(ButterflySpeciesDefinition def) {
		super(def);
	}

	@Override public boolean isNocturnal() { return def.nocturnal(); }
	@Override public boolean isMoth() { return def.moth(); }
	@Override public float getRarity() { return def.rarity(); }
	@Override public float getFlightDistance() { return def.flightDistance(); }
	@Override public int getSerumColor() { return def.serumColor(); }

	@Nullable
	@Override public TagKey<Biome> getSpawnBiomes() { return def.spawnBiomes().orElse(null); }

	@Override public List<IProduct> buildProducts() { return List.copyOf(def.products()); }
	@Override public List<IProduct> buildCaterpillarProducts() { return List.copyOf(def.caterpillarProducts()); }

	// --- butterfly-specific mutators (all throw) ---
	@Override public IButterflySpeciesBuilder setSerumColor(TextColor color) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setFlightDistance(float flightDistance) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setNocturnal(boolean nocturnal) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setMoth(boolean moth) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setSpawnBiomes(TagKey<Biome> biomeTag) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
	@Override public IButterflySpeciesBuilder setRarity(float rarity) { throw new UnsupportedOperationException(READ_ONLY_MESSAGE); }
}
```

- [ ] **Step 5: Compile**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the golden-master gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: 55/55 pass. The `*SpeciesEquivalenceTest`s exercise every adapter getter (code-built vs JSON-projected species compared field-by-field), so a wrong delegation would fail here.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/forestry/core/genetics/AbstractDefinitionSpeciesBuilder.java \
        src/main/java/forestry/apiculture/genetics/DefinitionBeeSpeciesBuilder.java \
        src/main/java/forestry/arboriculture/genetics/DefinitionTreeSpeciesBuilder.java \
        src/main/java/forestry/lepidopterology/genetics/DefinitionButterflySpeciesBuilder.java
git commit -m "refactor(genetics): share read-only adapter base across the 3 species builders"
```

---

### Task 4: `SpeciesProjection.buildGenome` helper + thread through the three projectors

**Files:**
- Create: `src/main/java/forestry/core/genetics/SpeciesProjection.java`
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesProjector.java`
- Modify: `src/main/java/forestry/arboriculture/genetics/TreeSpeciesProjector.java`
- Modify: `src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesProjector.java`

**Interfaces:**
- Consumes: `ISpeciesDefinition` (Task 1); `SpeciesRegistration.createDefaultGenomeBuilder`, `GenomeProjection.applyOverrides` (existing).
- Produces: `static IGenome SpeciesProjection.buildGenome(IKaryotype karyotype, ResourceLocation id, ISpeciesDefinition def)`.

- [ ] **Step 1: Create the helper**

`src/main/java/forestry/core/genetics/SpeciesProjection.java`:
```java
package forestry.core.genetics;

import net.minecraft.resources.ResourceLocation;

import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.IKaryotype;
import forestry.api.plugin.IGenomeBuilder;
import forestry.apiimpl.plugin.SpeciesRegistration;

/**
 * The genome-build skeleton shared by every species projector: seed the karyotype defaults via
 * {@link SpeciesRegistration#createDefaultGenomeBuilder}, apply the definition's sparse overrides via
 * {@link GenomeProjection#applyOverrides}, and build. The per-type projector keeps its own fail-soft
 * {@code try/catch}, type-specific preflight (jubilance / bindings lookup), and final species
 * construction.
 */
public final class SpeciesProjection {
	private SpeciesProjection() {
	}

	public static IGenome buildGenome(IKaryotype karyotype, ResourceLocation id, ISpeciesDefinition def) {
		IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
		GenomeProjection.applyOverrides(gb, karyotype, def.genome());
		return gb.build();
	}
}
```

- [ ] **Step 2: Thread through `BeeSpeciesProjector`**

In `BeeSpeciesProjector.java`, replace the three genome-build lines:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
				GenomeProjection.applyOverrides(gb, karyotype, def.genome());
				IGenome genome = gb.build();
```
with:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenome genome = SpeciesProjection.buildGenome(karyotype, id, def);
```
Update imports: remove `import forestry.api.plugin.IGenomeBuilder;`, `import forestry.apiimpl.plugin.SpeciesRegistration;`, `import forestry.core.genetics.GenomeProjection;`; add `import forestry.core.genetics.SpeciesProjection;`. Keep `IKaryotype` and `IGenome`.

- [ ] **Step 3: Thread through `TreeSpeciesProjector`**

In `TreeSpeciesProjector.java`, replace:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
				GenomeProjection.applyOverrides(gb, karyotype, def.genome());
				IGenome genome = gb.build();
```
with:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenome genome = SpeciesProjection.buildGenome(karyotype, id, def);
```
Update imports the same way: remove `IGenomeBuilder`, `SpeciesRegistration`, `GenomeProjection`; add `import forestry.core.genetics.SpeciesProjection;`.

- [ ] **Step 4: Thread through `ButterflySpeciesProjector`**

In `ButterflySpeciesProjector.java`, replace:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenomeBuilder gb = SpeciesRegistration.createDefaultGenomeBuilder(karyotype, id, def.genus(), def.dominant());
				GenomeProjection.applyOverrides(gb, karyotype, def.genome());
				IGenome genome = gb.build();
```
with:
```java
				IKaryotype karyotype = type.getKaryotype();
				IGenome genome = SpeciesProjection.buildGenome(karyotype, id, def);
```
Update imports the same way: remove `IGenomeBuilder`, `SpeciesRegistration`, `GenomeProjection`; add `import forestry.core.genetics.SpeciesProjection;`.

- [ ] **Step 5: Compile**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run the golden-master gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: 55/55 pass. `BeeSpeciesProjectorTest`, `TreeSpeciesProjectorTest`, `ButterflySpeciesProjectorTest`, and the three `*SpeciesEquivalenceTest`s all drive projection end-to-end (default genome + overrides), so a broken helper would fail here.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/forestry/core/genetics/SpeciesProjection.java \
        src/main/java/forestry/apiculture/genetics/BeeSpeciesProjector.java \
        src/main/java/forestry/arboriculture/genetics/TreeSpeciesProjector.java \
        src/main/java/forestry/lepidopterology/genetics/ButterflySpeciesProjector.java
git commit -m "refactor(genetics): share genome-build skeleton via SpeciesProjection"
```

---

### Task 5: `TestSpeciesDefinitions` builder + migrate the 11 non-oracle test literals

**Scope note:** The three `*DefinitionTest` codec round-trip oracles (`BeeSpeciesDefinitionTest`,
`TreeSpeciesDefinitionTest`, `ButterflySpeciesDefinitionTest`) are **intentionally left as positional
literals** — each assigns a distinct sentinel to every field, which is the point of a round-trip test;
a builder would obscure that and shorten nothing. Only the 11 seed-from-live / phantom-default sites
migrate.

**Files:**
- Create: `src/test/java/forestry/gametest/TestSpeciesDefinitions.java`
- Modify: `src/test/java/forestry/gametest/BeeSpeciesProjectorTest.java`
- Modify: `src/test/java/forestry/gametest/SpeciesFallbackTest.java`
- Modify: `src/test/java/forestry/gametest/TreeSpeciesProjectorTest.java` (2 sites)
- Modify: `src/test/java/forestry/gametest/TreeSpeciesReloadTest.java`
- Modify: `src/test/java/forestry/gametest/TreeSpeciesFallbackTest.java`
- Modify: `src/test/java/forestry/gametest/ButterflySpawnerReloadTest.java`
- Modify: `src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java`
- Modify: `src/test/java/forestry/gametest/ButterflyEntityReloadTest.java`
- Modify: `src/test/java/forestry/gametest/ButterflySpeciesProjectorTest.java` (2 sites)

**Interfaces:**
- Consumes: the three definition records (unchanged flat constructors).
- Produces test factories: `TestSpeciesDefinitions.bee(String genus, String species)`, `.tree(String genus, String species)`, `.treeFrom(ITreeSpecies)`, `.butterfly(String genus, String species)`, `.butterflyFrom(IButterflySpecies)`; each returns a fluent builder with `.genome(Map)` `.build()` plus the setters listed below.

- [ ] **Step 1: Create the test builder**

`src/test/java/forestry/gametest/TestSpeciesDefinitions.java`:
```java
package forestry.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import forestry.api.apiculture.ForestryBeeJubilances;
import forestry.api.arboriculture.genetics.ITreeSpecies;
import forestry.api.core.HumidityType;
import forestry.api.core.IProduct;
import forestry.api.core.Product;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.alleles.Allele;
import forestry.api.lepidopterology.genetics.IButterflySpecies;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.arboriculture.genetics.TreeSpeciesDefinition;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;

/**
 * Fluent, default-seeded builders for the three species definitions, used by the reload / projector /
 * fallback / spawner gametests to avoid repeating 12-20-arg positional constructors. Base fields default
 * to the same values the definition codecs default to; {@code from(...)} seeds a builder from a live
 * species (copying every field the species interface exposes, leaving {@code glint=false}/
 * {@code complexity=0} as the tests do). NOT used by the {@code *DefinitionTest} codec round-trip
 * oracles, which set every field to a distinct sentinel on purpose.
 */
public final class TestSpeciesDefinitions {
	private TestSpeciesDefinitions() {
	}

	public static Bee bee(String genus, String species) {
		return new Bee(genus, species);
	}

	public static Tree tree(String genus, String species) {
		return new Tree(genus, species);
	}

	public static Tree treeFrom(ITreeSpecies s) {
		Tree b = new Tree(s.getGenusName(), s.getSpeciesName());
		b.dominant = s.isDominant();
		b.secret = s.isSecret();
		b.authority = s.getAuthority();
		b.escritoireColor = s.getEscritoireColor();
		b.temperature = s.getTemperature();
		b.humidity = s.getHumidity();
		b.rarity = s.getRarity();
		return b;
	}

	public static Butterfly butterfly(String genus, String species) {
		return new Butterfly(genus, species);
	}

	public static Butterfly butterflyFrom(IButterflySpecies s) {
		Butterfly b = new Butterfly(s.getGenusName(), s.getSpeciesName());
		b.dominant = s.isDominant();
		b.secret = s.isSecret();
		b.authority = s.getAuthority();
		b.escritoireColor = s.getEscritoireColor();
		b.temperature = s.getTemperature();
		b.humidity = s.getHumidity();
		b.nocturnal = s.isNocturnal();
		b.moth = s.isMoth();
		b.rarity = s.getRarity();
		b.flightDistance = s.getFlightDistance();
		b.serumColor = s.getSerumColor();
		b.spawnBiomes = Optional.ofNullable(s.getSpawnBiomes());
		b.products = s.getButterflyLoot();
		b.caterpillarProducts = s.getCaterpillarProducts();
		return b;
	}

	/** Base fields shared by all three builders, defaulted to the codec defaults. */
	private abstract static class Base {
		final String genus;
		final String species;
		boolean dominant = false;
		boolean glint = false;
		boolean secret = false;
		int complexity = 0;
		String authority = "Sengir";
		int escritoireColor = -1;
		TemperatureType temperature = TemperatureType.NORMAL;
		HumidityType humidity = HumidityType.NORMAL;
		Map<ResourceLocation, Allele<?>> genome = Map.of();

		Base(String genus, String species) {
			this.genus = genus;
			this.species = species;
		}
	}

	public static final class Bee extends Base {
		private int body = 0xffdc16;
		private int stripes = 0;
		private int outline = -1;
		private List<Product> products = List.of();
		private List<Product> specialties = List.of();
		private ResourceLocation jubilance = ForestryBeeJubilances.DEFAULT;

		private Bee(String genus, String species) {
			super(genus, species);
		}

		public Bee dominant(boolean v) { this.dominant = v; return this; }
		public Bee outline(int v) { this.outline = v; return this; }
		public Bee products(List<Product> v) { this.products = v; return this; }
		public Bee jubilance(ResourceLocation v) { this.jubilance = v; return this; }
		public Bee genome(Map<ResourceLocation, Allele<?>> v) { this.genome = v; return this; }

		public BeeSpeciesDefinition build() {
			return new BeeSpeciesDefinition(genus, species, dominant, glint, secret, complexity, authority,
				escritoireColor, temperature, humidity, body, stripes, outline, products, specialties, jubilance, genome);
		}
	}

	public static final class Tree extends Base {
		private float rarity = 0.0f;

		private Tree(String genus, String species) {
			super(genus, species);
		}

		public Tree escritoireColor(int v) { this.escritoireColor = v; return this; }
		public Tree genome(Map<ResourceLocation, Allele<?>> v) { this.genome = v; return this; }

		public TreeSpeciesDefinition build() {
			return new TreeSpeciesDefinition(genus, species, dominant, glint, secret, complexity, authority,
				escritoireColor, temperature, humidity, rarity, genome);
		}
	}

	public static final class Butterfly extends Base {
		private boolean nocturnal = false;
		private boolean moth = false;
		private float rarity = 0.0f;
		private float flightDistance = 5.0f;
		private int serumColor = 0;
		private Optional<net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome>> spawnBiomes = Optional.empty();
		private List<IProduct> products = List.of();
		private List<IProduct> caterpillarProducts = List.of();

		private Butterfly(String genus, String species) {
			super(genus, species);
		}

		public Butterfly rarity(float v) { this.rarity = v; return this; }
		public Butterfly genome(Map<ResourceLocation, Allele<?>> v) { this.genome = v; return this; }

		public ButterflySpeciesDefinition build() {
			return new ButterflySpeciesDefinition(genus, species, dominant, glint, secret, complexity, authority,
				escritoireColor, temperature, humidity, nocturnal, moth, rarity, flightDistance, serumColor,
				spawnBiomes, products, caterpillarProducts, genome);
		}
	}
}
```

- [ ] **Step 2: Migrate `BeeSpeciesProjectorTest`**

Replace the `new BeeSpeciesDefinition(...)` literal (the `BeeSpeciesDefinition def = new BeeSpeciesDefinition(` … `);` block) with:
```java
		BeeSpeciesDefinition def = TestSpeciesDefinitions.bee(ForestryTaxa.GENUS_HONEY, ForestryTaxa.SPECIES_FOREST)
			.dominant(true)
			.outline(0x19d0ec)
			.products(List.of(Product.of(Items.HONEY_BOTTLE, 1, 0.3f)))
			.genome(Map.of(BeeChromosomes.POLLINATION.id(), ForestryAlleles.POLLINATION_SLOWER))
			.build();
```
Remove imports left unused after this change if the compiler flags them (e.g. `TemperatureType`, `HumidityType`, `ForestryBeeJubilances`); keep those still referenced elsewhere in the file.

- [ ] **Step 3: Migrate `SpeciesFallbackTest`**

Replace the `new BeeSpeciesDefinition(...)` literal with:
```java
		BeeSpeciesDefinition badDefinition = TestSpeciesDefinitions.bee("Apis", "nonexistens")
			.jubilance(ForestryConstants.forestry("nonexistent_jubilance"))
			.build();
```

- [ ] **Step 4: Migrate `TreeSpeciesProjectorTest` (both sites)**

First site (the from-oak projection), replace its `new TreeSpeciesDefinition(...)` literal with:
```java
		TreeSpeciesDefinition def = TestSpeciesDefinitions.treeFrom(oak)
			.escritoireColor(oak.getEscritoireColor())
			.genome(Map.of(TreeChromosomes.HEIGHT.id(), ForestryAlleles.HEIGHT_LARGE))
			.build();
```
Second site (the phantom no-bindings def), replace its `new TreeSpeciesDefinition(...)` literal with:
```java
		TreeSpeciesDefinition def = TestSpeciesDefinitions.tree("Quercus", "phantom").build();
```

- [ ] **Step 5: Migrate `TreeSpeciesReloadTest`**

Replace the `new TreeSpeciesDefinition(...)` literal with:
```java
		TreeSpeciesDefinition def = TestSpeciesDefinitions.treeFrom(oak)
			.escritoireColor(oak.getEscritoireColor())
			.genome(Map.of(TreeChromosomes.HEIGHT.id(), ForestryAlleles.HEIGHT_AVERAGE))
			.build();
```

- [ ] **Step 6: Migrate `TreeSpeciesFallbackTest`**

Replace the `new TreeSpeciesDefinition(...)` argument to `defs.put(phantomId, ...)` with the builder. The block:
```java
		defs.put(phantomId, new TreeSpeciesDefinition(
			"Quercus", "phantom", false, false, false, 0, "Sengir", -1,
			TemperatureType.NORMAL, HumidityType.NORMAL, 0.0f, Map.of()));
```
becomes:
```java
		defs.put(phantomId, TestSpeciesDefinitions.tree("Quercus", "phantom").build());
```

- [ ] **Step 7: Migrate `ButterflySpawnerReloadTest`**

Replace the `new ButterflySpeciesDefinition(...)` literal with:
```java
		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();
```

- [ ] **Step 8: Migrate `ButterflySpeciesReloadTest`**

Replace the `new ButterflySpeciesDefinition(...)` literal with:
```java
		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();
```

- [ ] **Step 9: Migrate `ButterflyEntityReloadTest`**

Replace the `new ButterflySpeciesDefinition(...)` literal (seeded from `defaultSpecies`) with:
```java
			ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(defaultSpecies).build();
```

- [ ] **Step 10: Migrate `ButterflySpeciesProjectorTest` (both sites)**

First site (from-monarch), replace its `new ButterflySpeciesDefinition(...)` literal with:
```java
		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterflyFrom(monarch)
			.genome(Map.of(ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE))
			.build();
```
Second site (the phantom bad-override), replace its `new ButterflySpeciesDefinition(...)` literal with:
```java
		ButterflySpeciesDefinition def = TestSpeciesDefinitions.butterfly(monarch.getGenusName(), "phantom")
			.rarity(0.1f)
			.genome(Map.of(ForestryConstants.forestry("no_such_chromosome"), ForestryAlleles.SIZE_AVERAGE))
			.build();
```

- [ ] **Step 11: Remove now-unused imports in the migrated test files**

Compile and let the compiler flag unused imports left behind by the removed literals (commonly `TemperatureType`, `HumidityType`, `Optional`, `Product` in the seed-from-live sites). Remove only those the compiler reports as unused; leave imports still referenced by the surrounding test body.

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileTestJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Run the full gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: 55/55 pass. Every migrated test's assertions + `finally` snapshot/restore scaffolding are unchanged — only the definition construction differs.

- [ ] **Step 13: Commit**

```bash
git add src/test/java/forestry/gametest/TestSpeciesDefinitions.java \
        src/test/java/forestry/gametest/BeeSpeciesProjectorTest.java \
        src/test/java/forestry/gametest/SpeciesFallbackTest.java \
        src/test/java/forestry/gametest/TreeSpeciesProjectorTest.java \
        src/test/java/forestry/gametest/TreeSpeciesReloadTest.java \
        src/test/java/forestry/gametest/TreeSpeciesFallbackTest.java \
        src/test/java/forestry/gametest/ButterflySpawnerReloadTest.java \
        src/test/java/forestry/gametest/ButterflySpeciesReloadTest.java \
        src/test/java/forestry/gametest/ButterflyEntityReloadTest.java \
        src/test/java/forestry/gametest/ButterflySpeciesProjectorTest.java
git commit -m "test(genetics): replace 11 species-definition literals with TestSpeciesDefinitions builder"
```

---

### Task 6: Delete the dead `api/genetics/SpeciesDefinition`

**Files:**
- Delete: `src/main/java/forestry/api/genetics/SpeciesDefinition.java`

- [ ] **Step 1: Confirm it is unreferenced**

Run: `rg -n 'SpeciesDefinition\b' src --glob '*.java' | rg -v '(Bee|Tree|Butterfly)SpeciesDefinition|ISpeciesDefinition|TestSpeciesDefinitions'`
Expected: **no output** (the only `SpeciesDefinition` tokens left are the prefixed record names, the new interface, and the test builder — none reference `forestry.api.genetics.SpeciesDefinition`).

- [ ] **Step 2: Delete the file**

Run: `git rm src/main/java/forestry/api/genetics/SpeciesDefinition.java`

- [ ] **Step 3: Compile**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(genetics): delete dead api SpeciesDefinition (zero references)"
```

---

### Final verification

- [ ] **Full build**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew compileJava compileTestJava build`
Expected: `BUILD SUCCESSFUL`. (If `:test` fails with "no tests discovered", that is the documented pre-existing base-`1.21.1` flake, unrelated to this work.)

- [ ] **Full gametest gate**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: **55/55 pass**; log shows `Loaded 69 bee species`, `Loaded 50 tree species`, `Loaded 35 butterfly species`, and `114`/`42`/`1` mutation recipes.

- [ ] **Datapack byte-identity**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runData` then `git status --short src/generated/resources/data`
Expected: no diff under `bee_species/`, `tree_species/`, `butterfly_species/`.

- [ ] **Update the project memory**

Append a short "deferred follow-up: shared SpeciesDefinition refactor — DONE" note to
`data-driven-genetics-overhaul.md` (the roadmap's remaining-follow-ups line) recording the four shared
types + that the other follow-ups (flower types, recipe-result id-templating, moth flags) remain open.
```

## Coverage check (plan vs spec)

- Spec component 1 (`ISpeciesDefinition`) → Task 1. ✅
- Spec component 2 (`SpeciesCore` + butterfly `Tail` flatten) → Task 2. ✅
- Spec component 3 (`AbstractDefinitionSpeciesBuilder`) → Task 3. ✅
- Spec component 4 (`SpeciesProjection`) → Task 4. ✅
- Spec component 5 (test helper) → Task 5 (refined: 11 sites migrate; 3 codec-oracle sites stay literal, with rationale). ✅
- Spec component 6 (delete dead code) → Task 6. ✅
- Spec success criteria (compile/build/`runGameTestServer` 55/55/`runData` idempotent/species counts) → Final verification. ✅
- Spec "records stay flat" constraint → honored (records unchanged except `implements` + codec bodies; no `core()` component). ✅
- Spec test-hygiene note (snapshot/restore preserved) → Task 5 scope note + Step 12. ✅
