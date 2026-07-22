# Data-driven Flower Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bee flower types data-definable from datapack JSON (loaded, reloadable, client-synced) via three code-registered serializers, mirroring the existing data-driven bee-species infrastructure.

**Architecture:** Three flower-type *serializers* (`tag_flower_type`, `water_tag_flower_type`, `photosynthesis_flower_type`) register `FlowerTypeType(MapCodec, StreamCodec)` records in a static `FlowerTypeTypes` dispatch registry (copied from `MutationConditionTypes`). A `FlowerTypeManager` reload listener (copied from `BeeSpeciesManager`) loads `data/<ns>/flower_type/*.json`, and `GeneticsReloadHandler.rebuildFlowerTypes` installs *code-base ∪ datapack* (datapack wins) into `BeeSpeciesType`. Forestry's 15 built-ins are datagen'd and shipped as JSON; `registerFlowerType` is reserved for code/KubeJS types.

**Tech Stack:** Java 21, NeoForge 21.1.x, Mojang DataFixerUpper codecs, Minecraft gametests (`runGameTestServer`).

**Design spec:** `docs/superpowers/specs/2026-07-12-data-driven-flower-types-design.md`

## Global Constraints

- Gradle uses a JBR-21 toolchain configured in `build.gradle`; run `./gradlew <task>` directly. If a task fails with "Unsupported class file major version 70", prefix with `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew <task>`.
- Flower-type IDs are **unchanged** (`forestry:flower_type_vanilla`, …). Never rename them — bee species genomes reference them.
- Gametests live in `src/test/java/forestry/gametest/`, class-annotated `@GameTestHolder(ForestryConstants.MOD_ID)` + `@PrefixGameTestTemplate(false)`, methods `@GameTest(template = "empty") public static void name(GameTestHelper helper)`, ending in `helper.succeed()` or `helper.fail(msg)`.
- Run a single gametest class: `./gradlew runGameTestServer` runs the whole suite (there is no per-class filter); expect it to take a few minutes. Use `./gradlew compileJava` / `./gradlew compileTestJava` as the fast per-step check.
- Keep behavior identical for the 15 built-ins: same tags, same dominance, `END` = tag + `#minecraft:is_end` biomes, `SEA`/`CORAL` = water.

---

### Task 1: Refactor flower-type classes (rename + merge `EndFlowerType`), no serialization yet

Pure behavior-preserving refactor. `FlowerType` → `TagFlowerType` (gains an optional `biomes` biome-tag, absorbing `EndFlowerType`); `WaterFlowerType` → `WaterTagFlowerType`. Existing gametests must stay green.

**Files:**
- Create: `src/main/java/forestry/apiculture/TagFlowerType.java`
- Create: `src/main/java/forestry/apiculture/WaterTagFlowerType.java`
- Delete: `src/main/java/forestry/apiculture/FlowerType.java`, `src/main/java/forestry/apiculture/WaterFlowerType.java`, `src/main/java/forestry/apiculture/EndFlowerType.java`
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java` (lines 253–267 — new class names)
- Modify: `src/main/java/forestry/plugin/client/BeeAnalyzerPlugin.java:56` (`instanceof FlowerType` → `TagFlowerType`)

**Interfaces:**
- Produces: `TagFlowerType(TagKey<Block> flowers, boolean dominant, @Nullable TagKey<Biome> biomes)` implements `IFlowerType`; `TagFlowerType(TagKey<Block>, boolean)` convenience ctor (biomes=null); `boolean isPlantablePosition(Level, BlockPos)` (protected-overridable); getters `acceptableFlowers()`, `dominant()`, `biomes()`. `WaterTagFlowerType(TagKey<Block>, boolean, @Nullable TagKey<Biome>)` extends `TagFlowerType`.

- [ ] **Step 1: Create `TagFlowerType`** (moves `FlowerType`'s body verbatim, adds the `biomes` OR-clause from `EndFlowerType`)

```java
package forestry.apiculture;

import java.util.HashSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import forestry.api.ForestryTags;
import forestry.api.apiculture.IFlowerType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class TagFlowerType implements IFlowerType {
	protected final TagKey<Block> acceptableFlowers;
	protected final boolean dominant;
	@Nullable
	protected final TagKey<Biome> biomes;

	public TagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant) {
		this(acceptableFlowers, dominant, null);
	}

	public TagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant, @Nullable TagKey<Biome> biomes) {
		this.acceptableFlowers = acceptableFlowers;
		this.dominant = dominant;
		this.biomes = biomes;
	}

	@Override
	public boolean isAcceptableFlower(Level level, BlockPos pos) {
		if (this.biomes != null && level.getBiome(pos).is(this.biomes)) {
			return true;
		}
		return level.getBlockState(pos).is(this.acceptableFlowers);
	}

	@Override
	public boolean plantRandomFlower(Level level, BlockPos pos, List<BlockState> nearbyFlowers) {
		if (level.hasChunkAt(pos) && isPlantablePosition(level, pos)) {
			ObjectArrayList<BlockState> uniqueNearbyFlowers = new ObjectArrayList<>(new HashSet<>(nearbyFlowers));
			Util.shuffle(uniqueNearbyFlowers, level.random);

			for (BlockState state : uniqueNearbyFlowers) {
				if (state.is(ForestryTags.Blocks.PLANTABLE_FLOWERS) && state.canSurvive(level, pos)) {
					if (state.hasProperty(DoublePlantBlock.HALF)) {
						BlockPos topPos = pos.above();
						if (level.isEmptyBlock(topPos)) {
							return level.setBlockAndUpdate(pos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER))
								&& level.setBlockAndUpdate(topPos, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
						}
					} else {
						return level.setBlockAndUpdate(pos, state);
					}
				}
			}
		}
		return false;
	}

	public boolean isPlantablePosition(Level level, BlockPos pos) {
		return level.isEmptyBlock(pos);
	}

	@Override
	public boolean isDominant() {
		return this.dominant;
	}

	public TagKey<Block> acceptableFlowers() {
		return this.acceptableFlowers;
	}

	public boolean dominant() {
		return this.dominant;
	}

	@Nullable
	public TagKey<Biome> biomes() {
		return this.biomes;
	}
}
```

- [ ] **Step 2: Create `WaterTagFlowerType`** (moves `WaterFlowerType`'s body)

```java
package forestry.apiculture;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class WaterTagFlowerType extends TagFlowerType {
	public WaterTagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant) {
		super(acceptableFlowers, dominant);
	}

	public WaterTagFlowerType(TagKey<Block> acceptableFlowers, boolean dominant, @Nullable TagKey<Biome> biomes) {
		super(acceptableFlowers, dominant, biomes);
	}

	@Override
	public boolean isPlantablePosition(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() == Blocks.WATER;
	}
}
```

- [ ] **Step 3: Delete the old classes**

```bash
git rm src/main/java/forestry/apiculture/FlowerType.java \
       src/main/java/forestry/apiculture/WaterFlowerType.java \
       src/main/java/forestry/apiculture/EndFlowerType.java
```

- [ ] **Step 4: Update `DefaultForestryPlugin` registrations** (lines 253–267). Replace the block with (note `END` now uses `TagFlowerType` + `BiomeTags.IS_END`, `SEA`/`CORAL` use `WaterTagFlowerType`):

```java
apiculture.registerFlowerType(ForestryFlowerTypes.VANILLA, new TagFlowerType(ForestryTags.Blocks.VANILLA_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.NETHER, new TagFlowerType(ForestryTags.Blocks.NETHER_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.CACTI, new TagFlowerType(ForestryTags.Blocks.CACTI_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.MUSHROOMS, new TagFlowerType(ForestryTags.Blocks.MUSHROOMS_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.END, new TagFlowerType(ForestryTags.Blocks.END_FLOWERS, false, BiomeTags.IS_END));
apiculture.registerFlowerType(ForestryFlowerTypes.JUNGLE, new TagFlowerType(ForestryTags.Blocks.JUNGLE_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.SNOW, new TagFlowerType(ForestryTags.Blocks.SNOW_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.WHEAT, new TagFlowerType(ForestryTags.Blocks.WHEAT_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.GOURD, new TagFlowerType(ForestryTags.Blocks.GOURD_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.CAVE, new TagFlowerType(ForestryTags.Blocks.CAVE_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.PHOTOSYNTHESIS, new PhotosynthesisFlowerType());
apiculture.registerFlowerType(ForestryFlowerTypes.ANCIENT, new TagFlowerType(ForestryTags.Blocks.ANCIENT_FLOWERS, true));
apiculture.registerFlowerType(ForestryFlowerTypes.SEA, new WaterTagFlowerType(ForestryTags.Blocks.SEA_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.CORAL, new WaterTagFlowerType(ForestryTags.Blocks.CORAL_FLOWERS, false));
apiculture.registerFlowerType(ForestryFlowerTypes.SCULK, new TagFlowerType(ForestryTags.Blocks.SCULK_FLOWERS, false));
```

Fix imports: remove `forestry.apiculture.FlowerType/WaterFlowerType/EndFlowerType`; add `forestry.apiculture.TagFlowerType`, `forestry.apiculture.WaterTagFlowerType`, `net.minecraft.tags.BiomeTags`. (`PhotosynthesisFlowerType` import stays.)

- [ ] **Step 5: Update `BeeAnalyzerPlugin.java:56`** — change `instanceof FlowerType type` to `instanceof TagFlowerType type` and its import `forestry.apiculture.FlowerType` → `forestry.apiculture.TagFlowerType`. (It reads `type.getAcceptableFlowers()`; rename that call to `type.acceptableFlowers()`.)

- [ ] **Step 6: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Regression gametests still green**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL` (all existing gametests pass; behavior unchanged).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: rename FlowerType->TagFlowerType, merge EndFlowerType into optional biomes tag"
```

---

### Task 2: Serializers' codecs + `FlowerTypeType` + `FlowerTypeTypes` dispatch registry

Add serialization: each of the three classes gets a `MapCodec`, `StreamCodec`, and `TYPE`; `IFlowerType` gets `type()`; a `FlowerTypeTypes` registry provides the dispatch `CODEC`/`STREAM_CODEC`.

**Files:**
- Create: `src/main/java/forestry/api/apiculture/FlowerTypeType.java`
- Modify: `src/main/java/forestry/api/apiculture/IFlowerType.java` (add `type()`)
- Modify: `src/main/java/forestry/apiculture/TagFlowerType.java` (add `CODEC`, `STREAM_CODEC`, `TYPE`, `type()`)
- Modify: `src/main/java/forestry/apiculture/WaterTagFlowerType.java` (same)
- Modify: `src/main/java/forestry/apiculture/PhotosynthesisFlowerType.java` (add `INSTANCE`, `CODEC`, `STREAM_CODEC`, `TYPE`, `type()`)
- Create: `src/main/java/forestry/apiculture/genetics/FlowerTypeTypes.java`
- Test: `src/test/java/forestry/gametest/FlowerTypeCodecTest.java`

**Interfaces:**
- Consumes: `TagFlowerType`, `WaterTagFlowerType`, `PhotosynthesisFlowerType` from Task 1.
- Produces: `FlowerTypeType<T extends IFlowerType>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf,T> streamCodec)`; `IFlowerType.type()`; `TagFlowerType.TYPE`, `WaterTagFlowerType.TYPE`, `PhotosynthesisFlowerType.TYPE`; `FlowerTypeTypes.register(id, type)`, `FlowerTypeTypes.registerBuiltins()`, `FlowerTypeTypes.CODEC` (`Codec<IFlowerType>`), `FlowerTypeTypes.STREAM_CODEC` (`StreamCodec<RegistryFriendlyByteBuf, IFlowerType>`).

- [ ] **Step 1: Write the failing codec round-trip gametest**

```java
package forestry.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;
import forestry.apiculture.genetics.FlowerTypeTypes;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class FlowerTypeCodecTest {
	@GameTest(template = "empty")
	public static void streamRoundTrip(GameTestHelper helper) {
		FlowerTypeTypes.registerBuiltins();
		IFlowerType[] samples = {
			new TagFlowerType(BlockTags.FLOWERS, true),
			new TagFlowerType(BlockTags.FLOWERS, false, BiomeTags.IS_END), // END-style
			new WaterTagFlowerType(BlockTags.FLOWERS, false),
			new PhotosynthesisFlowerType(),
		};
		for (IFlowerType original : samples) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			FlowerTypeTypes.STREAM_CODEC.encode(buf, original);
			IFlowerType decoded = FlowerTypeTypes.STREAM_CODEC.decode(buf);
			if (decoded.getClass() != original.getClass() || decoded.isDominant() != original.isDominant()) {
				helper.fail("Stream round-trip mismatch for " + original.getClass().getSimpleName());
				return;
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void jsonRoundTrip(GameTestHelper helper) {
		FlowerTypeTypes.registerBuiltins();
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		IFlowerType end = new TagFlowerType(BlockTags.FLOWERS, false, BiomeTags.IS_END);
		var json = FlowerTypeTypes.CODEC.encodeStart(jsonOps, end).getOrThrow();
		IFlowerType decoded = FlowerTypeTypes.CODEC.parse(jsonOps, json).getOrThrow();
		if (!(decoded instanceof TagFlowerType t) || t.biomes() == null || t.isDominant()) {
			helper.fail("JSON round-trip lost biomes/dominant on END-style tag flower type: " + json);
			return;
		}
		helper.succeed();
	}
}
```

- [ ] **Step 2: Verify it fails to compile (red)**

Run: `./gradlew compileTestJava`
Expected: FAIL — `FlowerTypeType`, `FlowerTypeTypes`, `TagFlowerType.TYPE` etc. do not exist yet.

- [ ] **Step 3: Create `FlowerTypeType` record**

```java
package forestry.api.apiculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A code-registered serializer for a kind of {@link IFlowerType}. Mirrors {@code MutationConditionType}: the
 * dispatch key ("type" field in JSON) selects one of these, and its codecs (de)serialize the instance for
 * datapacks and network sync. Registered in {@code FlowerTypeTypes}.
 */
public record FlowerTypeType<T extends IFlowerType>(
	MapCodec<T> codec,
	StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

- [ ] **Step 4: Add `type()` to `IFlowerType`**

Add to `IFlowerType`:

```java
	/**
	 * @return The serializer for this flower type, used to encode it for datapacks/network. Only serializable
	 * (datapack-backed or synced) flower types override this; purely code-registered types that hold behaviour
	 * as lambdas (e.g. KubeJS) are never serialized and keep the throwing default.
	 */
	default FlowerTypeType<?> type() {
		throw new UnsupportedOperationException(getClass().getName() + " is not a serializable flower type");
	}
```

- [ ] **Step 5: Add codecs + `TYPE` + `type()` to `TagFlowerType`**

Add these imports: `com.mojang.serialization.MapCodec`, `com.mojang.serialization.codecs.RecordCodecBuilder`, `com.mojang.serialization.Codec`, `net.minecraft.core.registries.Registries`, `net.minecraft.network.RegistryFriendlyByteBuf`, `net.minecraft.network.codec.ByteBufCodecs`, `net.minecraft.network.codec.StreamCodec`, `net.minecraft.resources.ResourceLocation`, `forestry.api.apiculture.FlowerTypeType`, `java.util.Optional`. Add fields (before the constructors):

```java
	static final StreamCodec<io.netty.buffer.ByteBuf, TagKey<Block>> BLOCK_TAG_STREAM_CODEC =
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.BLOCK, rl), TagKey::location);
	static final StreamCodec<io.netty.buffer.ByteBuf, TagKey<Biome>> BIOME_TAG_STREAM_CODEC =
		ResourceLocation.STREAM_CODEC.map(rl -> TagKey.create(Registries.BIOME, rl), TagKey::location);

	public static final MapCodec<TagFlowerType> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		TagKey.codec(Registries.BLOCK).fieldOf("flowers").forGetter(t -> t.acceptableFlowers),
		Codec.BOOL.fieldOf("dominant").forGetter(t -> t.dominant),
		TagKey.codec(Registries.BIOME).optionalFieldOf("biomes").forGetter(t -> Optional.ofNullable(t.biomes))
	).apply(inst, (flowers, dominant, biomes) -> new TagFlowerType(flowers, dominant, biomes.orElse(null))));

	public static final StreamCodec<RegistryFriendlyByteBuf, TagFlowerType> STREAM_CODEC = StreamCodec.composite(
		BLOCK_TAG_STREAM_CODEC, t -> t.acceptableFlowers,
		ByteBufCodecs.BOOL, t -> t.dominant,
		ByteBufCodecs.optional(BIOME_TAG_STREAM_CODEC), t -> Optional.ofNullable(t.biomes),
		(flowers, dominant, biomes) -> new TagFlowerType(flowers, dominant, biomes.orElse(null)));

	public static final FlowerTypeType<TagFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);
```

Add the override:

```java
	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}
```

- [ ] **Step 6: Add codecs + `TYPE` + `type()` to `WaterTagFlowerType`**

Add imports mirroring Step 5 (`MapCodec`, `RecordCodecBuilder`, `Codec`, `Registries`, `RegistryFriendlyByteBuf`, `ByteBufCodecs`, `StreamCodec`, `FlowerTypeType`, `Optional`, `TagKey`, `Biome`, `Block`). Add:

```java
	public static final MapCodec<WaterTagFlowerType> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
		TagKey.codec(Registries.BLOCK).fieldOf("flowers").forGetter(TagFlowerType::acceptableFlowers),
		Codec.BOOL.fieldOf("dominant").forGetter(TagFlowerType::dominant),
		TagKey.codec(Registries.BIOME).optionalFieldOf("biomes").forGetter(t -> Optional.ofNullable(t.biomes()))
	).apply(inst, (flowers, dominant, biomes) -> new WaterTagFlowerType(flowers, dominant, biomes.orElse(null))));

	public static final StreamCodec<RegistryFriendlyByteBuf, WaterTagFlowerType> STREAM_CODEC = StreamCodec.composite(
		TagFlowerType.BLOCK_TAG_STREAM_CODEC, TagFlowerType::acceptableFlowers,
		ByteBufCodecs.BOOL, TagFlowerType::dominant,
		ByteBufCodecs.optional(TagFlowerType.BIOME_TAG_STREAM_CODEC), t -> Optional.ofNullable(t.biomes()),
		(flowers, dominant, biomes) -> new WaterTagFlowerType(flowers, dominant, biomes.orElse(null)));

	public static final FlowerTypeType<WaterTagFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);

	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}
```

- [ ] **Step 7: Add codecs + `TYPE` + `type()` to `PhotosynthesisFlowerType`**

Add imports `com.mojang.serialization.MapCodec`, `net.minecraft.network.RegistryFriendlyByteBuf`, `net.minecraft.network.codec.StreamCodec`, `forestry.api.apiculture.FlowerTypeType`. Add:

```java
	public static final PhotosynthesisFlowerType INSTANCE = new PhotosynthesisFlowerType();
	public static final MapCodec<PhotosynthesisFlowerType> CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, PhotosynthesisFlowerType> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final FlowerTypeType<PhotosynthesisFlowerType> TYPE = new FlowerTypeType<>(CODEC, STREAM_CODEC);

	@Override
	public FlowerTypeType<?> type() {
		return TYPE;
	}
```

- [ ] **Step 8: Create `FlowerTypeTypes` registry** (structural copy of `MutationConditionTypes`)

```java
package forestry.apiculture.genetics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.FlowerTypeType;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;

/**
 * Code registry of flower-type serializers ({@link FlowerTypeType}). The three built-ins are registered by
 * {@link #registerBuiltins()}; {@link #CODEC}/{@link #STREAM_CODEC} dispatch on a {@code "type"} field, exactly
 * like {@code MutationConditionTypes}.
 */
public final class FlowerTypeTypes {
	private static final Map<ResourceLocation, FlowerTypeType<?>> BY_ID = new ConcurrentHashMap<>();
	private static final Map<FlowerTypeType<?>, ResourceLocation> ID_OF = new ConcurrentHashMap<>();

	private static boolean builtinsRegistered = false;

	public static void register(ResourceLocation id, FlowerTypeType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate flower type serializer: " + id);
		}
		ID_OF.put(type, id);
	}

	private static FlowerTypeType<?> byId(ResourceLocation id) {
		FlowerTypeType<?> type = BY_ID.get(id);
		if (type == null) {
			throw new IllegalArgumentException("Unknown flower type serializer: " + id);
		}
		return type;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final Codec<IFlowerType> CODEC = ResourceLocation.CODEC
		.<IFlowerType>dispatch("type", c -> ID_OF.get(c.type()), id -> ((FlowerTypeType) byId(id)).codec());

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final StreamCodec<RegistryFriendlyByteBuf, IFlowerType> STREAM_CODEC = StreamCodec.of(
		(buf, flowerType) -> {
			ResourceLocation id = ID_OF.get(flowerType.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			((StreamCodec) flowerType.type().streamCodec()).encode(buf, flowerType);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return byId(id).streamCodec().decode(buf);
		});

	/**
	 * Registers the three built-in flower-type serializers. Idempotent; must run before any datapack parse.
	 */
	public static synchronized void registerBuiltins() {
		if (builtinsRegistered) {
			return;
		}
		builtinsRegistered = true;

		register(ForestryConstants.forestry("tag_flower_type"), TagFlowerType.TYPE);
		register(ForestryConstants.forestry("water_tag_flower_type"), WaterTagFlowerType.TYPE);
		register(ForestryConstants.forestry("photosynthesis_flower_type"), PhotosynthesisFlowerType.TYPE);
	}

	private FlowerTypeTypes() {}
}
```

- [ ] **Step 9: Compile main + test**

Run: `./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Run the codec gametests (green)**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL`; `FlowerTypeCodecTest.streamRoundTrip` and `jsonRoundTrip` pass.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: add flower type serializers + FlowerTypeTypes dispatch registry"
```

---

### Task 3: Datapack loader + reload handler + BeeSpeciesType union/bootstrap + wiring

Load `flower_type/*.json` on server reload and install *code-base ∪ datapack* into the live bee species type. Built-ins are still code-registered here (datagen comes in Task 4), so the datapack folder is empty and the union = code base — behavior unchanged, verified by existing gametests.

**Files:**
- Create: `src/main/java/forestry/apiculture/genetics/FlowerTypeManager.java`
- Modify: `src/main/java/forestry/core/genetics/GeneticsReloadHandler.java` (add `rebuildFlowerTypes`; call `FlowerTypeTypes.registerBuiltins()` in the safety-net path)
- Modify: `src/main/java/forestry/apiculture/genetics/BeeSpeciesType.java` (code-base field, `setFlowerTypes`, `getCodeFlowerTypes`, bootstrap install)
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java` (call `FlowerTypeTypes.registerBuiltins()` next to `MutationConditionTypes.registerBuiltins()`)
- Modify: `src/main/java/forestry/core/ModuleCore.java` (register `FlowerTypeManager.INSTANCE` as a reload listener)

**Interfaces:**
- Consumes: `FlowerTypeTypes.CODEC`, `FlowerTypeTypes.registerBuiltins` (Task 2).
- Produces: `FlowerTypeManager.INSTANCE`, `FlowerTypeManager.getDefinitions()`, `FlowerTypeManager.setDefinitions(map)`; `GeneticsReloadHandler.rebuildFlowerTypes(Map<ResourceLocation, IFlowerType>)`; `BeeSpeciesType.setFlowerTypes(ImmutableMap)`, `BeeSpeciesType.getCodeFlowerTypes()`.

- [ ] **Step 1: Create `FlowerTypeManager`** (structural copy of `BeeSpeciesManager`)

```java
package forestry.apiculture.genetics;

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
import forestry.api.apiculture.IFlowerType;
import forestry.core.genetics.GeneticsReloadHandler;

/**
 * Datapack loader for flower types: a {@link SimpleJsonResourceReloadListener} over the {@code flower_type} folder.
 * Decodes each entry via {@link FlowerTypeTypes#CODEC} (fail-soft), stores the last-parsed map, and hands it to
 * {@link GeneticsReloadHandler#rebuildFlowerTypes} which installs code-base union datapack into the bee species type.
 * Server-only reload listener; the client reuses the instance as a data holder for {@code FlowerTypeSyncPacket}.
 */
public final class FlowerTypeManager extends SimpleJsonResourceReloadListener {
	public static final FlowerTypeManager INSTANCE = new FlowerTypeManager();

	private static final String FOLDER = "flower_type";

	private volatile Map<ResourceLocation, IFlowerType> definitions = Map.of();

	private FlowerTypeManager() {
		super(new Gson(), FOLDER);
	}

	public Map<ResourceLocation, IFlowerType> getDefinitions() {
		return this.definitions;
	}

	public void setDefinitions(Map<ResourceLocation, IFlowerType> definitions) {
		this.definitions = definitions;
	}

	@Override
	protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
		FlowerTypeTypes.registerBuiltins();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, getRegistryLookup());

		Map<ResourceLocation, IFlowerType> parsed = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
			ResourceLocation id = entry.getKey();
			DataResult<IFlowerType> result = FlowerTypeTypes.CODEC.parse(ops, entry.getValue());
			result.resultOrPartial(error -> Forestry.LOGGER.error("Skipping flower type {}: {}", id, error))
				.ifPresent(type -> parsed.put(id, type));
		}

		this.definitions = Map.copyOf(parsed);
		GeneticsReloadHandler.rebuildFlowerTypes(this.definitions);
	}
}
```

- [ ] **Step 2: Add `rebuildFlowerTypes` to `GeneticsReloadHandler`**

Add imports if missing: `com.google.common.collect.ImmutableMap`, `java.util.LinkedHashMap`, `java.util.Map`, `net.minecraft.resources.ResourceLocation`, `forestry.api.apiculture.IFlowerType`, `forestry.api.apiculture.genetics.IBeeSpeciesType`, `forestry.apiculture.genetics.BeeSpeciesType`, `forestry.apiculture.genetics.FlowerTypeTypes`. Add method:

```java
	/**
	 * Installs the effective flower-type map into the live bee species type: the code-registered base
	 * (KubeJS/addons) overlaid by the datapack-loaded (or sync-delivered) definitions, datapack winning on id.
	 */
	public static void rebuildFlowerTypes(Map<ResourceLocation, IFlowerType> dataDefinitions) {
		FlowerTypeTypes.registerBuiltins(); // idempotent safety net
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		Map<ResourceLocation, IFlowerType> effective = new LinkedHashMap<>(((BeeSpeciesType) type).getCodeFlowerTypes());
		effective.putAll(dataDefinitions);
		((BeeSpeciesType) type).setFlowerTypes(ImmutableMap.copyOf(effective));
	}
```

(`SpeciesUtil` is already imported in this file per `rebuildSpecies`.)

- [ ] **Step 3: Update `BeeSpeciesType`** — add the code-base field + accessors + bootstrap.

Change the field (line ~40) and add a second field:

```java
	private ImmutableMap<ResourceLocation, IFlowerType> flowerTypes = ImmutableMap.of();
	private ImmutableMap<ResourceLocation, IFlowerType> codeFlowerTypes = ImmutableMap.of();
```

In `handleSpeciesRegistration` replace `this.flowerTypes = registration.getFlowerTypes();` (line ~179) with:

```java
		this.codeFlowerTypes = registration.getFlowerTypes();
		this.flowerTypes = this.codeFlowerTypes; // bootstrap: code base alone until the first datapack load
```

Add accessors:

```java
	public void setFlowerTypes(ImmutableMap<ResourceLocation, IFlowerType> flowerTypes) {
		this.flowerTypes = flowerTypes;
	}

	public ImmutableMap<ResourceLocation, IFlowerType> getCodeFlowerTypes() {
		return this.codeFlowerTypes;
	}
```

- [ ] **Step 4: Wire `registerBuiltins` in `PluginManager`** — next to `MutationConditionTypes.registerBuiltins();` (line ~160) add:

```java
		forestry.apiculture.genetics.FlowerTypeTypes.registerBuiltins();
```

- [ ] **Step 5: Register the reload listener in `ModuleCore`** — find `registerReloadListeners` (where `BeeSpeciesManager.INSTANCE` is registered) and add the flower-type manager immediately before it:

```java
		event.addListener(forestry.apiculture.genetics.FlowerTypeManager.INSTANCE);
```

(Match the exact `event.addListener(...)` call shape used for `BeeSpeciesManager.INSTANCE` in that method.)

- [ ] **Step 6: Compile**

Run: `./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Regression gametests green (union == code base, behavior unchanged)**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: flower type datapack loader + reload handler + code/datapack union"
```

---

### Task 4: Datagen provider + make built-ins datapack-only

Ship the 15 built-ins as generated JSON and stop registering them in code, so they become fully datapack-driven.

**Files:**
- Create: `src/main/java/forestry/core/data/FlowerTypeProvider.java`
- Modify: `src/main/java/forestry/core/data/Data.java` (register the provider in `GatherDataEvent`)
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java` (remove the 15 `registerFlowerType` calls added in Task 1)
- Test: `src/test/java/forestry/gametest/FlowerTypeTest.java` (built-ins-resolve check)
- Generated: `src/generated/resources/data/forestry/flower_type/*.json` (15)

**Interfaces:**
- Consumes: `FlowerTypeTypes.CODEC`, the three flower-type classes, `ForestryFlowerTypes`, `ForestryTags.Blocks`.
- Produces: `data/forestry/flower_type/flower_type_<name>.json` for all 15 ids.

- [ ] **Step 1: Write the failing built-ins-resolve gametest**

```java
package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.ForestryFlowerTypes;
import forestry.api.apiculture.IFlowerType;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;
import forestry.core.utils.SpeciesUtil;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class FlowerTypeTest {
	private static final ResourceLocation[] ALL = {
		ForestryFlowerTypes.VANILLA, ForestryFlowerTypes.NETHER, ForestryFlowerTypes.CACTI,
		ForestryFlowerTypes.MUSHROOMS, ForestryFlowerTypes.END, ForestryFlowerTypes.JUNGLE,
		ForestryFlowerTypes.SNOW, ForestryFlowerTypes.WHEAT, ForestryFlowerTypes.GOURD,
		ForestryFlowerTypes.CAVE, ForestryFlowerTypes.PHOTOSYNTHESIS, ForestryFlowerTypes.ANCIENT,
		ForestryFlowerTypes.SEA, ForestryFlowerTypes.CORAL, ForestryFlowerTypes.SCULK,
	};

	@GameTest(template = "empty")
	public static void allBuiltinsResolve(GameTestHelper helper) {
		IBeeSpeciesType bees = SpeciesUtil.BEE_TYPE.get();
		for (ResourceLocation id : ALL) {
			IFlowerType type = bees.getFlowerTypeSafe(id);
			if (type == null) {
				helper.fail("Built-in flower type did not load from datapack: " + id);
				return;
			}
		}
		// Spot-check serializer classes + dominance survived the JSON round-trip.
		if (!(bees.getFlowerType(ForestryFlowerTypes.END) instanceof TagFlowerType end) || end.biomes() == null) {
			helper.fail("END should be a TagFlowerType with a biomes tag");
			return;
		}
		if (!(bees.getFlowerType(ForestryFlowerTypes.SEA) instanceof WaterTagFlowerType)) {
			helper.fail("SEA should be a WaterTagFlowerType");
			return;
		}
		if (!(bees.getFlowerType(ForestryFlowerTypes.PHOTOSYNTHESIS) instanceof PhotosynthesisFlowerType)) {
			helper.fail("PHOTOSYNTHESIS should be a PhotosynthesisFlowerType");
			return;
		}
		if (!bees.getFlowerType(ForestryFlowerTypes.VANILLA).isDominant()) {
			helper.fail("VANILLA flower type should be dominant");
			return;
		}
		helper.succeed();
	}
}
```

- [ ] **Step 2: Create `FlowerTypeProvider`**

```java
package forestry.core.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryTags;
import forestry.api.apiculture.ForestryFlowerTypes;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;
import forestry.apiculture.genetics.FlowerTypeTypes;

/**
 * Generates {@code data/forestry/flower_type/*.json} for the 15 built-in flower types. This list is the single
 * source of truth for the built-ins (they are no longer code-registered at runtime); it must stay in sync with
 * the tags/dominance the mod ships. No registry access is needed — flower-type fields are block/biome tags, which
 * encode as plain resource locations.
 */
public class FlowerTypeProvider implements DataProvider {
	private final PackOutput.PathProvider path;

	public FlowerTypeProvider(PackOutput output) {
		this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "flower_type");
	}

	private static Map<ResourceLocation, IFlowerType> builtins() {
		Map<ResourceLocation, IFlowerType> map = new LinkedHashMap<>();
		map.put(ForestryFlowerTypes.VANILLA, new TagFlowerType(ForestryTags.Blocks.VANILLA_FLOWERS, true));
		map.put(ForestryFlowerTypes.NETHER, new TagFlowerType(ForestryTags.Blocks.NETHER_FLOWERS, false));
		map.put(ForestryFlowerTypes.CACTI, new TagFlowerType(ForestryTags.Blocks.CACTI_FLOWERS, false));
		map.put(ForestryFlowerTypes.MUSHROOMS, new TagFlowerType(ForestryTags.Blocks.MUSHROOMS_FLOWERS, false));
		map.put(ForestryFlowerTypes.END, new TagFlowerType(ForestryTags.Blocks.END_FLOWERS, false, BiomeTags.IS_END));
		map.put(ForestryFlowerTypes.JUNGLE, new TagFlowerType(ForestryTags.Blocks.JUNGLE_FLOWERS, false));
		map.put(ForestryFlowerTypes.SNOW, new TagFlowerType(ForestryTags.Blocks.SNOW_FLOWERS, true));
		map.put(ForestryFlowerTypes.WHEAT, new TagFlowerType(ForestryTags.Blocks.WHEAT_FLOWERS, true));
		map.put(ForestryFlowerTypes.GOURD, new TagFlowerType(ForestryTags.Blocks.GOURD_FLOWERS, true));
		map.put(ForestryFlowerTypes.CAVE, new TagFlowerType(ForestryTags.Blocks.CAVE_FLOWERS, true));
		map.put(ForestryFlowerTypes.PHOTOSYNTHESIS, new PhotosynthesisFlowerType());
		map.put(ForestryFlowerTypes.ANCIENT, new TagFlowerType(ForestryTags.Blocks.ANCIENT_FLOWERS, true));
		map.put(ForestryFlowerTypes.SEA, new WaterTagFlowerType(ForestryTags.Blocks.SEA_FLOWERS, false));
		map.put(ForestryFlowerTypes.CORAL, new WaterTagFlowerType(ForestryTags.Blocks.CORAL_FLOWERS, false));
		map.put(ForestryFlowerTypes.SCULK, new TagFlowerType(ForestryTags.Blocks.SCULK_FLOWERS, false));
		return map;
	}

	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		FlowerTypeTypes.registerBuiltins();
		var futures = builtins().entrySet().stream().map(entry -> {
			JsonElement json = FlowerTypeTypes.CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow();
			return DataProvider.saveStable(output, json, this.path.json(entry.getKey()));
		}).toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(futures);
	}

	@Override
	public String getName() {
		return "Forestry Flower Types";
	}
}
```

- [ ] **Step 3: Register the provider in `Data.java`** — mirror how `BeeSpeciesProvider` is added in the `GatherDataEvent` handler. Add:

```java
		generator.addProvider(event.includeServer(), new forestry.core.data.FlowerTypeProvider(output));
```

(Use the same `output`/`generator.addProvider(...)` expressions the neighbouring providers use in this method; `FlowerTypeProvider` needs only `PackOutput`.)

- [ ] **Step 4: Remove the 15 built-in registrations from `DefaultForestryPlugin`** — delete the whole `registerFlowerType(...)` block (the 15 lines edited in Task 1, Step 4). Remove now-unused imports (`TagFlowerType`, `WaterTagFlowerType`, `PhotosynthesisFlowerType`, `BiomeTags`, `ForestryFlowerTypes` if unused elsewhere in the file, `ForestryTags` if unused elsewhere). Keep the surrounding `registerApiculture` method intact.

- [ ] **Step 5: Generate the JSON**

Run: `./gradlew runData`
Expected: `BUILD SUCCESSFUL`; 15 files under `src/generated/resources/data/forestry/flower_type/`.

- [ ] **Step 6: Verify the generated output**

Run: `ls src/generated/resources/data/forestry/flower_type/ | wc -l` → `15`
Run: `cat src/generated/resources/data/forestry/flower_type/flower_type_end.json`
Expected: contains `"type": "forestry:tag_flower_type"`, `"flowers": "forestry:end_flowers"`, `"dominant": false`, `"biomes": "minecraft:is_end"`.

- [ ] **Step 7: Compile + run gametests (built-ins now load from datapack)**

Run: `./gradlew compileTestJava`
Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL`; `FlowerTypeTest.allBuiltinsResolve` passes (proves the datapack JSONs load and resolve).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: datagen the 15 built-in flower types; make them datapack-only"
```

---

### Task 5: Client sync packet + `OnDatapackSyncEvent` wiring

Send the loaded flower-type map to clients on login/reload, so client-side genome resolution (`IFlowerType::isDominant`, analyzer) sees the same flower types.

**Files:**
- Create: `src/main/java/forestry/core/network/packets/FlowerTypeSyncPacket.java`
- Modify: `src/main/java/forestry/core/network/PacketIdClient.java` (add `FLOWER_TYPE_SYNC` id + register the payload)
- Modify: `src/main/java/forestry/core/ModuleCore.java` (send `FlowerTypeSyncPacket` in the `OnDatapackSyncEvent` listener, before the bee-species packet)

**Interfaces:**
- Consumes: `FlowerTypeManager` (Task 3), `FlowerTypeTypes.STREAM_CODEC` (Task 2), `GeneticsReloadHandler.rebuildFlowerTypes` (Task 3).
- Produces: `FlowerTypeSyncPacket(Map<ResourceLocation, IFlowerType>)` with `type()`/`encode`/`decode`/`handle`; `PacketIdClient.FLOWER_TYPE_SYNC`.

- [ ] **Step 1: Create `FlowerTypeSyncPacket`** (structural copy of `BeeSpeciesSyncPacket`)

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

import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.genetics.FlowerTypeManager;
import forestry.apiculture.genetics.FlowerTypeTypes;
import forestry.core.genetics.GeneticsReloadHandler;
import forestry.core.network.PacketIdClient;

/**
 * Server -&gt; client sync of the loaded flower-type definitions, sent on player login/reload (before
 * {@code BeeSpeciesSyncPacket}, so flower resolution is ready when genomes materialise). The client has no
 * datapack access to {@code flower_type} JSON, so this packet is its only source.
 */
public record FlowerTypeSyncPacket(Map<ResourceLocation, IFlowerType> definitions) implements CustomPacketPayload {
	private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, IFlowerType>> MAP_STREAM_CODEC =
		ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, FlowerTypeTypes.STREAM_CODEC);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return PacketIdClient.FLOWER_TYPE_SYNC;
	}

	public static void encode(RegistryFriendlyByteBuf buffer, FlowerTypeSyncPacket msg) {
		MAP_STREAM_CODEC.encode(buffer, msg.definitions);
	}

	public static FlowerTypeSyncPacket decode(RegistryFriendlyByteBuf buffer) {
		return new FlowerTypeSyncPacket(MAP_STREAM_CODEC.decode(buffer));
	}

	public static void handle(FlowerTypeSyncPacket msg, Player player) {
		// Integrated server shares the server's already-authoritative singletons; re-applying is redundant.
		if (Minecraft.getInstance().hasSingleplayerServer()) {
			return;
		}
		FlowerTypeManager.INSTANCE.setDefinitions(msg.definitions);
		GeneticsReloadHandler.rebuildFlowerTypes(msg.definitions);
	}
}
```

- [ ] **Step 2: Register the payload id in `PacketIdClient`** — mirror the `BEE_SPECIES_SYNC` entry. Add a `Type` constant `FLOWER_TYPE_SYNC` (id `forestry:flower_type_sync`) and register it with `FlowerTypeSyncPacket::encode` / `::decode` / `::handle` in the same place/way `BEE_SPECIES_SYNC` is registered.

```java
	public static final CustomPacketPayload.Type<FlowerTypeSyncPacket> FLOWER_TYPE_SYNC = type("flower_type_sync");
```

(Match the existing `type(...)` helper and the `registrar.playToClient(...)` / handler-registration pattern used for `BEE_SPECIES_SYNC` in this class; add the analogous line for `FlowerTypeSyncPacket`.)

- [ ] **Step 3: Send in `ModuleCore`'s `OnDatapackSyncEvent` listener** — find where `BeeSpeciesSyncPacket` is sent (to `event.getPlayer()` and/or all players) and send the flower-type packet **immediately before** it, using the same send call and player targeting:

```java
		// Flower types must arrive before species (genome dominance resolution reads IFlowerType).
		<sameSendCall>(new FlowerTypeSyncPacket(forestry.apiculture.genetics.FlowerTypeManager.INSTANCE.getDefinitions()), <sameTarget>);
```

(Replace `<sameSendCall>`/`<sameTarget>` with the exact API used for `BeeSpeciesSyncPacket` right below it — e.g. `PacketManager.INSTANCE.sendToPlayer(...)` or `player.connection.send(...)`.)

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava compileTestJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run gametests (no regression; server path unchanged)**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: client sync of flower types on login/reload"
```

---

### Task 6: Behavioral gametests + final verification

Prove the three serializers behave correctly and the datapack override/union works, then run the full suite + datagen + build.

**Files:**
- Modify: `src/test/java/forestry/gametest/FlowerTypeTest.java` (add behavioral + override tests)

**Interfaces:**
- Consumes: everything from Tasks 1–5.

- [ ] **Step 1: Add behavioral + override tests to `FlowerTypeTest`**

```java
	@GameTest(template = "empty")
	public static void tagFlowerTypeAcceptsTaggedBlock(GameTestHelper helper) {
		net.minecraft.core.BlockPos abs = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
		helper.getLevel().setBlockAndUpdate(abs, net.minecraft.world.level.block.Blocks.DANDELION.defaultBlockState());
		TagFlowerType tag = new TagFlowerType(net.minecraft.tags.BlockTags.FLOWERS, true);
		if (!tag.isAcceptableFlower(helper.getLevel(), abs)) {
			helper.fail("tag_flower_type should accept a #minecraft:flowers block");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void waterFlowerTypePlantablePositionIsWaterOnly(GameTestHelper helper) {
		net.minecraft.core.BlockPos water = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
		net.minecraft.core.BlockPos air = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 0));
		helper.getLevel().setBlockAndUpdate(water, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(air, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		WaterTagFlowerType type = new WaterTagFlowerType(net.minecraft.tags.BlockTags.FLOWERS, false);
		if (!type.isPlantablePosition(helper.getLevel(), water) || type.isPlantablePosition(helper.getLevel(), air)) {
			helper.fail("water_tag_flower_type should be plantable only in water");
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void datapackOverrideWinsAndUnionSwaps(GameTestHelper helper) {
		IBeeSpeciesType bees = SpeciesUtil.BEE_TYPE.get();
		ResourceLocation probe = ForestryConstants.forestry("gametest_probe_flower");
		// A synthetic datapack map that overlays the code base.
		java.util.Map<ResourceLocation, IFlowerType> data = new java.util.HashMap<>();
		data.put(probe, new PhotosynthesisFlowerType());
		data.put(ForestryFlowerTypes.VANILLA, new TagFlowerType(net.minecraft.tags.BlockTags.FLOWERS, false)); // override: recessive
		forestry.core.genetics.GeneticsReloadHandler.rebuildFlowerTypes(data);
		try {
			if (!(bees.getFlowerTypeSafe(probe) instanceof PhotosynthesisFlowerType)) {
				helper.fail("datapack-only flower type should resolve after rebuild");
				return;
			}
			if (bees.getFlowerType(ForestryFlowerTypes.VANILLA).isDominant()) {
				helper.fail("datapack entry should override the built-in VANILLA dominance");
				return;
			}
		} finally {
			// Restore the real datapack-loaded map so later tests/gameplay are unaffected.
			forestry.core.genetics.GeneticsReloadHandler.rebuildFlowerTypes(FlowerTypeManager.INSTANCE.getDefinitions());
		}
		helper.succeed();
	}
```

Add imports used above if not already present: `forestry.apiculture.genetics.FlowerTypeManager`.

- [ ] **Step 2: Run the full gametest suite**

Run: `env JAVA_HOME=/home/thedarkcolour/.jdks/jbr-21.0.9 ./gradlew runGameTestServer`
Expected: `BUILD SUCCESSFUL`; all `FlowerTypeTest` + `FlowerTypeCodecTest` methods pass, and every pre-existing gametest still passes.

- [ ] **Step 3: Datagen + build**

Run: `./gradlew runData`
Expected: no changes vs Task 4 (15 files stable).
Run: `./gradlew build -x test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: behavioral + datapack-override gametests for flower types"
```

---

## Self-review notes

- **Spec coverage:** serializers+`type()` (T2) · dispatch registry (T2) · three types incl. END biomes (T1/T2) · reload manager (T3) · code∪datapack union + bootstrap (T3) · sync packet (T5) · datagen 15 built-ins (T4) · unchanged ids (T1/T4) · migration/deletes + consumer updates (T1) · KubeJS unaffected (T2 default `type()`) · tests (T2/T4/T6). All covered.
- **Fragile edits** (`Data.java`, `PacketIdClient`, `ModuleCore`): exact line numbers aren't given because those files' internals weren't fully read; each step instead says "mirror the `BeeSpecies*` equivalent adjacent to it" and gives the exact code to add. The implementer must read the neighbouring bee-species wiring in that file first.
- **KubeJS:** `KubeFlowerType` compiles unchanged (inherits throwing `type()`); it is never serialized/synced, so it needs no codec.
