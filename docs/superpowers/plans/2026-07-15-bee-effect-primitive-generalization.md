# Bee-effect Primitive Generalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generalize the `transform_block` and `damage_entities` effect primitives so HEROIC, SIFTER, GLACIAL and GLOW_BERRY_GROW migrate from code registration to datapack JSON, then delete the 11 primitives that no built-in effect consumes.

**Architecture:** Bee effects load through two merged paths: `DefaultForestryPlugin` code-registers base effects, and `BeeEffectManager` overlays datapack JSON decoded through the `IBeeEffect.CODEC` dispatch codec (keyed on `"type"` against `ForestryRegistries.BEE_EFFECT_TYPE`). `BeeEffectProvider` generates the mod's own `data/forestry/bee_effect/*.json`. This work adds a shared `ThrottleSettings` group-codec so every throttled primitive exposes `dominant`/`throttle`/`requires_working`/`combinable`, widens `transform_block` (tag-based `from`, property-mutation `to`, `attempts`, `max_temperature`, `requires_air_above`) and `damage_entities` (a `target` filter replacing `players_only`), then moves the four bespoke effects into the provider and deletes the dead primitives.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.x (moddev-gradle), Mojang DataFixerUpper codecs, NeoForge GameTest framework.

**Source spec:** `docs/superpowers/specs/2026-07-14-bee-effect-primitive-generalization-design.md`

## Global Constraints

- **Working tree is `src/`.** `ForestryCE-1.20.1/` and `ForestryCE-1.21.1-old/` are reference copies of other branches — **never edit them**.
- **Branch:** `imakebadchoices-pr/data-driven-bee-primitives`. Do not merge or rebase.
- **Indentation is tabs**, matching every file touched here. Match the surrounding comment density and javadoc style — these files are heavily javadoc'd; new public types get a javadoc sentence explaining *why*, not *what*.
- **Comments state constraints, not narrative.** Do not write comments about what changed, what it used to be, or which commit moved it (commit `7fcd9cb0e` and `e6b34ea77` exist specifically to strip that class of comment back out).
- **Behaviour preservation is the acceptance bar for all four migrations.** Every migrated value is read directly off the deleted class's `super(...)` call. The one accepted delta is GLOW_BERRY_GROW's narrowing from "any block with the BERRIES property" to `#minecraft:cave_vines`.
- **Generated JSON is never hand-written.** Emit it with `./gradlew runData` and commit what the generator produces.
- **Verification commands:**
  - Compile: `./gradlew compileJava compileTestJava`
  - Datagen: `./gradlew runData`
  - GameTests: `./gradlew runGameTestServer`
- **This branch has a pre-existing red bar. `runGameTestServer` exits non-zero before this work starts.**
  `everyallelevaluehasatranslation` and `defaultgenomesmatchbaseline` both fail on commit `a2dabdccf` ("Move the
  base taxonomy to datapack JSON"), which renamed the bee species IDs (`forestry:bee_abyssal` → `forestry:abyssal`)
  without updating `en_us.json` or regenerating `src/test/resources/forestry/gametest/genome-baseline.txt`. That
  commit's own message concedes the baseline diff is non-zero "modulo the unrelated id rename".
  **These two failures are out of scope — do not fix them, and do not treat them as a regression.**
  The real gate for every task here is: **`BeeEffectSystemTest` is fully green, and the failure list is exactly
  those two tests and no others.** Wherever a task step says "Expected: BUILD SUCCESSFUL" for
  `runGameTestServer`, read it as that gate.

## Decisions this plan makes that the spec left open

The spec is approved; these four points are implementation-level gaps it did not resolve. They are called out here so a reviewer can reject them explicitly rather than discover them in a diff.

1. **`BeeEffectProvider` must become registry-aware (Task 4).** The spec's `from: HolderSet<Block>` is encoded by `RegistryCodecs.homogeneousList(Registries.BLOCK)`, which requires `RegistryOps`. The provider today encodes with bare `JsonOps.INSTANCE` and its javadoc claims "Encoding needs no registry access". That claim stops being true. `BeeSpeciesProvider` and `TreeSpeciesProvider` already take a `CompletableFuture<HolderLookup.Provider>` and encode via `RegistryOps` — Task 4 mirrors them exactly, and updates the stale javadoc.
2. **`TemperatureType` gains `StringRepresentable` + `CODEC` (Task 2).** The spec's `"max_temperature": "normal"` needs a codec that does not exist. The spec's file inventory does not mention this file. The change is additive to an API enum: no breakage.
3. **`bee_effect_misanthrope.json` is NOT byte-identical.** The spec says "existing generated JSON is byte-identical", which holds for 9 of the 10 files — but §3 also replaces `players_only` with `target`, so misanthrope's `"players_only": true` becomes `"target": "players"`. The spec accepts this ("`players_only` is new in this unmerged PR, so replacing it costs no compat"); the two statements just aren't reconciled. Task 3 expects exactly one changed file.
4. **`transform_block`'s `chance` default stays `0.06`.** The spec's §4 table gives each migration `chance 1.0` but never states the codec default. Keeping today's `0.06` avoids an unspecified behaviour change, and the three migrated files state `"chance": 1.0` explicitly, which is self-documenting.

## Known coverage gap (deliberate)

The spec's Testing section asks for assertions on the identity guard and on `attempts` semantics ("10 attempts can produce up to 10 transforms"). Exercising those end-to-end requires a live `IBeeHousing` — a placed apiary with a queen — which no existing GameTest in this suite builds. Tasks 2 and 4 assert them at the **decision level** instead: that `To.apply` returns the identical `BlockState` instance for the coarse-dirt and already-berried cases (which is exactly what the guard branches on), and that `attempts` survives the codec. End-to-end apiary-driven coverage is **not** included. Do not claim otherwise in commit messages.

## File Structure

| File | Responsibility |
|---|---|
| `apiculture/genetics/effects/ThrottleSettings.java` (new) | The 4 common throttled-effect fields + their per-primitive-defaulted group `MapCodec`. |
| `apiculture/genetics/effects/ThrottledBeeEffect.java` | Holds a `ThrottleSettings`; keeps the 4-arg ctor for the ~12 bespoke subclasses. Loses the two dead position helpers (Task 5). |
| `apiculture/genetics/effects/TransformBlockBeeEffect.java` | Rewritten: `Transform` (HolderSet `from`, `To` target, `requires_air_above`), `To.Fixed`/`To.SetProperties`, attempts loop, identity guard, `max_temperature`. |
| `apiculture/genetics/effects/DamageBeeEffect.java` | Gains a `Target` filter (`Builtin` enum + `TagTarget`) replacing `players_only`. |
| `api/core/TemperatureType.java` | Gains `StringRepresentable` + `CODEC`. |
| `core/data/BeeEffectProvider.java` | Registry-aware; gains the 4 migrated entries. |
| `core/data/Data.java` | Passes the lookup provider to `BeeEffectProvider`. |
| `apiculture/features/ApicultureBeeEffectTypes.java` | Loses 11 registrations. |
| `plugin/DefaultForestryPlugin.java` | Loses 4 `registerBeeEffect` lines. |
| `test/java/forestry/gametest/BeeEffectSystemTest.java` | Rebuilt off `transform_block`; HEROIC folded in; new transform-builtins test. |

---

### Task 1: `ThrottleSettings` foundation

Introduces the shared settings record and threads it through the two primitives whose codecs need no other change (`apply_potion`, `resurrect`). `damage_entities` and `transform_block` adopt it in Tasks 2–3 alongside their own rewrites.

**Files:**
- Create: `src/main/java/forestry/apiculture/genetics/effects/ThrottleSettings.java`
- Modify: `src/main/java/forestry/apiculture/genetics/effects/ThrottledBeeEffect.java:23-33,91-133`
- Modify: `src/main/java/forestry/apiculture/genetics/effects/PotionBeeEffect.java:36-61`
- Modify: `src/main/java/forestry/apiculture/genetics/effects/ResurrectionBeeEffect.java:39-51`
- Test: `src/test/java/forestry/gametest/BeeEffectSystemTest.java`

**Interfaces:**
- Produces:
  - `record ThrottleSettings(boolean dominant, int throttle, boolean requiresWorking, boolean combinable)`
  - `static MapCodec<ThrottleSettings> ThrottleSettings.codec(int defThrottle, boolean defRequiresWorking, boolean defCombinable)`
  - `ThrottleSettings ThrottledBeeEffect.settings()` — public, used by every primitive's `forGetter` and by the tests.
  - `protected ThrottledBeeEffect(ThrottleSettings settings)` — the new canonical ctor.
  - `protected ThrottledBeeEffect(boolean dominant, int throttle, boolean requiresWorking, boolean isCombinable)` — kept, delegates.
  - `PotionBeeEffect(ThrottleSettings, Holder<MobEffect>, int duration, float chance)`
  - `ResurrectionBeeEffect(ThrottleSettings, List<Resurrectable>)`

- [ ] **Step 1: Write the failing tests**

Add both tests to `src/test/java/forestry/gametest/BeeEffectSystemTest.java`, and add the imports `java.util.Optional` is not needed yet; add `forestry.apiculture.genetics.effects.ThrottleSettings` and `net.minecraft.world.effect.MobEffects`.

```java
	/**
	 * Every {@code ThrottledBeeEffect}-derived primitive exposes the four common fields through
	 * {@link ThrottleSettings}, so a pack can retune them. Proven on {@code apply_potion} and {@code resurrect}.
	 */
	@GameTest(template = "empty")
	public static void throttleSettingsRoundTrip(GameTestHelper helper) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		ThrottleSettings tuned = new ThrottleSettings(false, 50, false, true);
		JsonElement json = IBeeEffect.CODEC.encodeStart(ops, new PotionBeeEffect(tuned, MobEffects.REGENERATION, 100, 1.0f)).getOrThrow();
		if (json.getAsJsonObject().get("throttle").getAsInt() != 50
			|| json.getAsJsonObject().get("requires_working").getAsBoolean()
			|| !json.getAsJsonObject().get("combinable").getAsBoolean()
			|| json.getAsJsonObject().get("dominant").getAsBoolean()) {
			helper.fail("apply_potion did not emit the ThrottleSettings fields: " + json);
			return;
		}
		if (!(IBeeEffect.CODEC.parse(ops, json).getOrThrow() instanceof PotionBeeEffect decoded)
			|| !decoded.settings().equals(tuned)) {
			helper.fail("apply_potion did not decode back to the tuned ThrottleSettings");
			return;
		}

		ThrottleSettings resurrectTuned = new ThrottleSettings(false, 7, false, false);
		JsonElement resurrectJson = IBeeEffect.CODEC.encodeStart(ops,
			new ResurrectionBeeEffect(resurrectTuned, ResurrectionBeeEffect.getReanimationList())).getOrThrow();
		if (!(IBeeEffect.CODEC.parse(ops, resurrectJson).getOrThrow() instanceof ResurrectionBeeEffect decodedResurrect)
			|| !decodedResurrect.settings().equals(resurrectTuned)) {
			helper.fail("resurrect did not round-trip its ThrottleSettings");
			return;
		}

		helper.succeed();
	}

	/**
	 * Each primitive's {@link ThrottleSettings} defaults are its historical hardcoded values, so an effect built the
	 * way {@code BeeEffectProvider} builds the built-ins emits none of the four fields it did not already emit. This
	 * is what keeps the generated JSON for the pre-existing built-ins unchanged.
	 */
	@GameTest(template = "empty")
	public static void throttleSettingsDefaultsStayOutOfJson(GameTestHelper helper) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		// BEATIFIC, verbatim from BeeEffectProvider: dominant=false is the only non-default of the four.
		JsonElement potion = IBeeEffect.CODEC.encodeStart(ops, new PotionBeeEffect(false, MobEffects.REGENERATION, 100)).getOrThrow();
		if (potion.getAsJsonObject().has("throttle") || potion.getAsJsonObject().has("requires_working")
			|| potion.getAsJsonObject().has("combinable")) {
			helper.fail("apply_potion emitted a defaulted ThrottleSettings field: " + potion);
			return;
		}

		// REANIMATION, verbatim from BeeEffectProvider: all four are defaults.
		JsonElement resurrect = IBeeEffect.CODEC.encodeStart(ops,
			new ResurrectionBeeEffect(true, 40, ResurrectionBeeEffect.getReanimationList())).getOrThrow();
		if (resurrect.getAsJsonObject().has("throttle") || resurrect.getAsJsonObject().has("requires_working")
			|| resurrect.getAsJsonObject().has("combinable") || resurrect.getAsJsonObject().has("dominant")) {
			helper.fail("resurrect emitted a defaulted ThrottleSettings field: " + resurrect);
			return;
		}

		helper.succeed();
	}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew compileTestJava`

Expected: FAIL — `cannot find symbol: class ThrottleSettings`. In a statically typed GameTest suite the failing state is a compile error; that is the red bar for this task.

- [ ] **Step 3: Create `ThrottleSettings`**

Create `src/main/java/forestry/apiculture/genetics/effects/ThrottleSettings.java`:

```java
package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The four fields every {@link ThrottledBeeEffect}-derived primitive shares. Spliced flat into each primitive's
 * object by {@link #codec} (a group {@code MapCodec} contributes its fields to the parent), so a definition stays a
 * flat JSON object with no nested settings block.
 * <p>
 * {@code AgingBeeEffect} is not covered: it extends {@code NonStackingBeeEffect}, which uses a tracked-owner
 * mechanism rather than throttling and so has no throttle to expose.
 */
public record ThrottleSettings(boolean dominant, int throttle, boolean requiresWorking, boolean combinable) {
	/**
	 * @param defThrottle        the primitive's historical hardcoded throttle.
	 * @param defRequiresWorking the primitive's historical hardcoded requires-working-queen flag.
	 * @param defCombinable      the primitive's historical hardcoded combinable flag.
	 *                           <p>
	 *                           Defaults are per-primitive, and each one equals what that primitive used to hardcode.
	 *                           That is what lets the already-generated built-in JSON stay unchanged: a built-in only
	 *                           emits a field it actually deviates on.
	 */
	public static MapCodec<ThrottleSettings> codec(int defThrottle, boolean defRequiresWorking, boolean defCombinable) {
		return RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("dominant", true).forGetter(ThrottleSettings::dominant),
			Codec.INT.optionalFieldOf("throttle", defThrottle).forGetter(ThrottleSettings::throttle),
			Codec.BOOL.optionalFieldOf("requires_working", defRequiresWorking).forGetter(ThrottleSettings::requiresWorking),
			Codec.BOOL.optionalFieldOf("combinable", defCombinable).forGetter(ThrottleSettings::combinable)
		).apply(instance, ThrottleSettings::new));
	}
}
```

- [ ] **Step 4: Rewire `ThrottledBeeEffect` onto the record**

In `src/main/java/forestry/apiculture/genetics/effects/ThrottledBeeEffect.java`, replace the three fields and the constructor (lines 24-33):

```java
	private final ThrottleSettings settings;

	protected ThrottledBeeEffect(ThrottleSettings settings) {
		super(settings.dominant());
		this.settings = settings;
	}

	/** Kept for the bespoke code-only subclasses, which have no codec and so no reason to name the record. */
	protected ThrottledBeeEffect(boolean dominant, int throttle, boolean requiresWorking, boolean isCombinable) {
		this(new ThrottleSettings(dominant, throttle, requiresWorking, isCombinable));
	}

	public ThrottleSettings settings() {
		return this.settings;
	}
```

Then replace the accessors (lines 91-98):

```java
	public int getThrottle() {
		return this.settings.throttle();
	}

	@Override
	public boolean isCombinable() {
		return this.settings.combinable();
	}
```

And in `isThrottled` (lines 117-133), swap the two field reads:

```java
	private boolean isThrottled(IEffectData storedData, IBeeHousing housing) {
		if (this.settings.requiresWorking() && housing.getErrorLogic().hasErrors()) {
			return true;
		}

		int time = storedData.getInteger(0);
		time++;
		storedData.setInteger(0, time);

		if (time < this.settings.throttle()) {
			return true;
		}

		// Reset since we are done throttling.
		storedData.setInteger(0, 0);
		return false;
	}
```

Leave `getRandomPositionInRange` and `findPositionInRange` alone — they still have callers until Task 5.

- [ ] **Step 5: Move `PotionBeeEffect` onto `ThrottleSettings`**

In `src/main/java/forestry/apiculture/genetics/effects/PotionBeeEffect.java`, replace lines 36-61:

```java
	public static final MapCodec<PotionBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ThrottleSettings.codec(200, true, false).forGetter(ThrottledBeeEffect::settings),
		BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(effect -> effect.potion),
		Codec.INT.fieldOf("duration").forGetter(effect -> effect.duration),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1.0f).forGetter(effect -> effect.chance)
	).apply(instance, PotionBeeEffect::new));

	private final Holder<MobEffect> potion;
	private final int potionFXColor;
	private final int duration;
	private final float chance;

	public PotionBeeEffect(boolean dominant, Holder<MobEffect> potion, int duration) {
		this(dominant, potion, duration, 200, 1.0f);
	}

	/** Kept for {@code AscensionBeeEffect} and {@code PotionBeeEffectExclusive}, which are code-only. */
	public PotionBeeEffect(boolean dominant, Holder<MobEffect> potion, int duration, int throttle, float chance) {
		this(new ThrottleSettings(dominant, throttle, true, false), potion, duration, chance);
	}

	public PotionBeeEffect(ThrottleSettings settings, Holder<MobEffect> potion, int duration, float chance) {
		super(settings);
		this.potion = potion;
		this.duration = duration;
		this.chance = chance;

		Collection<MobEffectInstance> potionEffects = Collections.singleton(new MobEffectInstance(potion, 1, 0));
		this.potionFXColor = PotionContents.getColor(potionEffects);
	}
```

The `IBeeEffect` import becomes unused here — remove it if the compiler warns. Nothing else in the file changes.

- [ ] **Step 6: Move `ResurrectionBeeEffect` onto `ThrottleSettings`**

In `src/main/java/forestry/apiculture/genetics/effects/ResurrectionBeeEffect.java`, replace lines 39-51:

```java
	public static final MapCodec<ResurrectionBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ThrottleSettings.codec(40, true, true).forGetter(ThrottledBeeEffect::settings),
		Resurrectable.CODEC.listOf().fieldOf("entries").forGetter(effect -> effect.resurrectables)
	).apply(instance, ResurrectionBeeEffect::new));

	private final List<Resurrectable> resurrectables;

	public ResurrectionBeeEffect(boolean dominant, int throttle, List<Resurrectable> resurrectables) {
		this(new ThrottleSettings(dominant, throttle, true, true), resurrectables);
	}

	public ResurrectionBeeEffect(ThrottleSettings settings, List<Resurrectable> resurrectables) {
		super(settings);
		// Copied into a mutable list: doEffectThrottled shuffles it in place, and a codec-decoded list is immutable.
		this.resurrectables = new ArrayList<>(resurrectables);
	}
```

The `IBeeEffect` import stays — `codec()`'s return type still uses it.

- [ ] **Step 7: Compile and run the tests**

Run: `./gradlew compileJava compileTestJava`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew runGameTestServer`
Expected: BUILD SUCCESSFUL — `throttleSettingsRoundTrip` and `throttleSettingsDefaultsStayOutOfJson` pass, and every pre-existing `BeeEffectSystemTest` test still passes.

- [ ] **Step 8: Prove the generated JSON is untouched**

Run: `./gradlew runData && git status --porcelain src/generated/resources/data/forestry/bee_effect/`

Expected: **empty output.** All 10 files byte-identical. If any file changed, a default in `ThrottleSettings.codec(...)` does not match that primitive's historical hardcoded value — fix the default, do not accept the new JSON.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/forestry/apiculture/genetics/effects/ThrottleSettings.java \
        src/main/java/forestry/apiculture/genetics/effects/ThrottledBeeEffect.java \
        src/main/java/forestry/apiculture/genetics/effects/PotionBeeEffect.java \
        src/main/java/forestry/apiculture/genetics/effects/ResurrectionBeeEffect.java \
        src/test/java/forestry/gametest/BeeEffectSystemTest.java
git commit -m "Expose throttle settings on the datapack effect primitives"
```

---

### Task 2: Rewrite `forestry:transform_block`

`transform_block` has zero consumers today, so its semantics are free to redefine. This task rewrites it and moves the two `LightningBeeEffect`-based round-trip tests onto it (it now has the richest codec, and `LightningBeeEffect` is deleted in Task 5).

**Files:**
- Modify: `src/main/java/forestry/api/core/TemperatureType.java:17-30`
- Rewrite: `src/main/java/forestry/apiculture/genetics/effects/TransformBlockBeeEffect.java`
- Modify: `src/test/java/forestry/gametest/BeeEffectSystemTest.java:74-99,107-135`

**Interfaces:**
- Consumes: `ThrottleSettings`, `ThrottledBeeEffect.settings()` (Task 1).
- Produces:
  - `Codec<TemperatureType> TemperatureType.CODEC` — serializes lowercase (`"normal"`).
  - `TransformBlockBeeEffect(ThrottleSettings settings, List<Transform> transforms, int attempts, float chance, Optional<TemperatureType> maxTemperature)`
  - `record TransformBlockBeeEffect.Transform(HolderSet<Block> from, To to, boolean requiresAirAbove)`
  - `sealed interface TransformBlockBeeEffect.To { BlockState apply(BlockState current); }` with `record To.Fixed(BlockState state)` and `record To.SetProperties(Map<String, String> properties)`
  - accessors `transforms()`, `attempts()`, `chance()`, `maxTemperature()`

- [ ] **Step 1: Write the failing tests**

In `src/test/java/forestry/gametest/BeeEffectSystemTest.java`, replace the body of `effectDefinitionRoundTrips` (lines 74-99) with:

```java
	@GameTest(template = "empty")
	public static void effectDefinitionRoundTrips(GameTestHelper helper) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		TransformBlockBeeEffect original = new TransformBlockBeeEffect(
			new ThrottleSettings(false, 30, true, false),
			List.of(new TransformBlockBeeEffect.Transform(
				BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.DIRT),
				new TransformBlockBeeEffect.To.Fixed(Blocks.COARSE_DIRT.defaultBlockState()),
				true)),
			10, 0.34f, Optional.of(TemperatureType.NORMAL));

		JsonElement json = IBeeEffect.CODEC.encodeStart(ops, original).getOrThrow();
		if (!json.getAsJsonObject().get("max_temperature").getAsString().equals("normal")) {
			helper.fail("transform_block did not encode max_temperature as a lowercase name: " + json);
			return;
		}
		IBeeEffect fromJson = IBeeEffect.CODEC.parse(ops, json).getOrThrow();
		if (!(fromJson instanceof TransformBlockBeeEffect transform)) {
			helper.fail("transform_block did not decode to TransformBlockBeeEffect (got " + fromJson.getClass().getSimpleName() + ")");
			return;
		}
		if (transform.getThrottle() != 30 || transform.isDominant() || transform.attempts() != 10
			|| !transform.maxTemperature().equals(Optional.of(TemperatureType.NORMAL))) {
			helper.fail("transform_block parameters not preserved through JSON: " + json);
			return;
		}
		TransformBlockBeeEffect.Transform decodedRule = transform.transforms().getFirst();
		if (!decodedRule.requiresAirAbove()
			|| !Blocks.DIRT.defaultBlockState().is(decodedRule.from())
			|| !(decodedRule.to() instanceof TransformBlockBeeEffect.To.Fixed fixed)
			|| !fixed.state().is(Blocks.COARSE_DIRT)) {
			helper.fail("transform_block transform rule not preserved through JSON: " + json);
			return;
		}

		StreamCodec<RegistryFriendlyByteBuf, IBeeEffect> streamCodec = ByteBufCodecs.fromCodecWithRegistries(IBeeEffect.CODEC);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		streamCodec.encode(buf, original);
		IBeeEffect fromBuf = streamCodec.decode(buf);
		if (!(fromBuf instanceof TransformBlockBeeEffect decoded) || decoded.getThrottle() != 30 || decoded.attempts() != 10) {
			helper.fail("transform_block did not survive the network stream codec round-trip");
			return;
		}

		helper.succeed();
	}

	/**
	 * The identity guard's decision function: {@code To.apply} returns the state it was given when the transform is a
	 * no-op, which is exactly what {@code doEffectThrottled} branches on to skip the write. This subsumes SIFTER's
	 * coarse-dirt exclusion (coarse dirt is in {@code #minecraft:dirt}, so the tag matches and only the identity check
	 * stops the rewrite) and GLOW_BERRY_GROW's already-berried check.
	 */
	@GameTest(template = "empty")
	public static void transformIdentityGuardSkipsNoOps(GameTestHelper helper) {
		BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
		if (!coarseDirt.is(BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.DIRT))) {
			helper.fail("#minecraft:dirt no longer contains coarse_dirt; SIFTER's identity guard assumption is void");
			return;
		}
		if (new TransformBlockBeeEffect.To.Fixed(coarseDirt).apply(coarseDirt) != coarseDirt) {
			helper.fail("Fixed.apply did not return the identical state for a coarse dirt no-op");
			return;
		}

		BlockState berried = Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true);
		TransformBlockBeeEffect.To setBerries = new TransformBlockBeeEffect.To.SetProperties(Map.of("berries", "true"));
		if (setBerries.apply(berried) != berried) {
			helper.fail("SetProperties.apply did not return the identical state for an already-berried vine");
			return;
		}
		// ...and it is a real mutation on an unberried vine, preserving the vine's other properties.
		BlockState bare = Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.AGE_25, 7);
		BlockState grown = setBerries.apply(bare);
		if (grown == bare || !grown.getValue(BlockStateProperties.BERRIES) || grown.getValue(BlockStateProperties.AGE_25) != 7) {
			helper.fail("SetProperties.apply did not set berries while preserving the vine's other properties");
			return;
		}

		helper.succeed();
	}
```

Then replace every `LightningBeeEffect` use in `datapackEffectsMergeOntoBuiltins` (lines 107-135) — the test's logic is unchanged, only its subject:

```java
	@GameTest(template = "empty")
	public static void datapackEffectsMergeOntoBuiltins(GameTestHelper helper) {
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		Map<ResourceLocation, IBeeEffect> original = BeeEffectManager.INSTANCE.getEffects();
		ResourceLocation testId = ForestryConstants.forestry("gametest_transform");
		IBeeEffect testEffect = new TransformBlockBeeEffect(
			new ThrottleSettings(true, 30, false, false),
			List.of(new TransformBlockBeeEffect.Transform(
				BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.DIRT),
				new TransformBlockBeeEffect.To.Fixed(Blocks.COARSE_DIRT.defaultBlockState()),
				false)),
			1, 0.34f, Optional.empty());
		try {
			GeneticsReloadHandler.rebuildBeeEffects(Map.of(testId, testEffect));

			if (!(beeType.getBeeEffect(testId) instanceof TransformBlockBeeEffect)) {
				helper.fail("datapack effect " + testId + " did not resolve after rebuildBeeEffects");
				return;
			}
			// A code builtin (registered by DefaultForestryPlugin) must survive the merge.
			if (beeType.getBeeEffect(ForestryBeeEffects.NONE) == null) {
				helper.fail("builtin bee effect NONE was dropped by the datapack merge");
				return;
			}

			// Empty reload: builtins remain (self-resetting when a datapack is removed).
			GeneticsReloadHandler.rebuildBeeEffects(Map.of());
			if (beeType.getBeeEffect(ForestryBeeEffects.NONE) == null) {
				helper.fail("builtin bee effect NONE was dropped after an empty reload");
				return;
			}
		} finally {
			GeneticsReloadHandler.rebuildBeeEffects(original);
		}

		helper.succeed();
	}
```

Update the imports: remove `forestry.apiculture.genetics.effects.LightningBeeEffect`; add
`java.util.List`, `java.util.Optional`, `forestry.api.core.TemperatureType`,
`forestry.apiculture.genetics.effects.TransformBlockBeeEffect`, `net.minecraft.core.registries.BuiltInRegistries`,
`net.minecraft.tags.BlockTags`, `net.minecraft.world.level.block.Blocks`,
`net.minecraft.world.level.block.state.BlockState`,
`net.minecraft.world.level.block.state.properties.BlockStateProperties`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew compileTestJava`
Expected: FAIL — `cannot find symbol: class Transform` / `no suitable constructor found for TransformBlockBeeEffect`.

- [ ] **Step 3: Give `TemperatureType` a codec**

In `src/main/java/forestry/api/core/TemperatureType.java`, change the declaration (line 17) to `public enum TemperatureType implements StringRepresentable {`, add these members next to `VALUES` (line 26), and add the imports `com.mojang.serialization.Codec`, `net.minecraft.util.StringRepresentable`, `java.util.Locale`:

```java
	public static final Codec<TemperatureType> CODEC = StringRepresentable.fromEnum(TemperatureType::values);
```

and add the method next to `up()`:

```java
	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}
```

- [ ] **Step 4: Rewrite `TransformBlockBeeEffect`**

Replace `src/main/java/forestry/apiculture/genetics/effects/TransformBlockBeeEffect.java` wholesale:

```java
package forestry.apiculture.genetics.effects;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.apiculture.genetics.Bee;
import forestry.core.utils.VecUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The {@code forestry:transform_block} primitive: samples {@code attempts} positions in the housing's territory and
 * rewrites every sampled block matching a transform rule.
 * <p>
 * Sampling uses {@link Bee#getParticleArea}, which is where the built-ins expressed through this primitive have always
 * sampled from. They are ground-targeting, so a symmetric box centered on the housing would silently make them far
 * more effective.
 */
public class TransformBlockBeeEffect extends ThrottledBeeEffect {
	/** The state a transform writes, given the state currently at the sampled position. */
	public sealed interface To {
		Codec<To> CODEC = Codec.either(SetProperties.CODEC, Fixed.CODEC)
			.xmap(either -> either.map(set -> (To) set, fixed -> (To) fixed),
				to -> to instanceof SetProperties set ? Either.left(set) : Either.right((Fixed) to));

		BlockState apply(BlockState current);

		/** A fixed state: {@code "to": {"Name": "minecraft:ice"}}. */
		record Fixed(BlockState state) implements To {
			public static final Codec<Fixed> CODEC = BlockState.CODEC.xmap(Fixed::new, Fixed::state);

			@Override
			public BlockState apply(BlockState current) {
				return this.state;
			}
		}

		/**
		 * A property mutation of the matched state: {@code "to": {"set": {"berries": "true"}}}. Every other property
		 * of the current state is preserved. A property the matched block does not have, or a value it cannot parse,
		 * leaves the state untouched — the caller's identity guard then skips the write rather than rewriting the
		 * block with itself.
		 */
		record SetProperties(Map<String, String> properties) implements To {
			public static final Codec<SetProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("set").forGetter(SetProperties::properties)
			).apply(instance, SetProperties::new));

			@Override
			public BlockState apply(BlockState current) {
				StateDefinition<Block, BlockState> definition = current.getBlock().getStateDefinition();
				BlockState state = current;
				for (Map.Entry<String, String> entry : this.properties.entrySet()) {
					Property<?> property = definition.getProperty(entry.getKey());
					if (property != null) {
						state = setValue(state, property, entry.getValue());
					}
				}
				return state;
			}

			private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> property, String value) {
				return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
			}
		}
	}

	/** A single from&rarr;to replacement rule. */
	public record Transform(HolderSet<Block> from, To to, boolean requiresAirAbove) {
		public static final Codec<Transform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("from").forGetter(Transform::from),
			To.CODEC.fieldOf("to").forGetter(Transform::to),
			Codec.BOOL.optionalFieldOf("requires_air_above", false).forGetter(Transform::requiresAirAbove)
		).apply(instance, Transform::new));
	}

	public static final MapCodec<TransformBlockBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ThrottleSettings.codec(20, false, false).forGetter(ThrottledBeeEffect::settings),
		Transform.CODEC.listOf().fieldOf("transforms").forGetter(effect -> effect.transforms),
		Codec.intRange(1, 64).optionalFieldOf("attempts", 1).forGetter(effect -> effect.attempts),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.06f).forGetter(effect -> effect.chance),
		// An upper bound only. A range would be more expressive, but GLACIAL is the only consumer and only needs a
		// ceiling; widening this to a range is a follow-up.
		TemperatureType.CODEC.optionalFieldOf("max_temperature").forGetter(effect -> effect.maxTemperature)
	).apply(instance, TransformBlockBeeEffect::new));

	private final List<Transform> transforms;
	private final int attempts;
	private final float chance;
	private final Optional<TemperatureType> maxTemperature;

	public TransformBlockBeeEffect(ThrottleSettings settings, List<Transform> transforms, int attempts, float chance, Optional<TemperatureType> maxTemperature) {
		super(settings);
		this.transforms = transforms;
		this.attempts = attempts;
		this.chance = chance;
		this.maxTemperature = maxTemperature;
	}

	@Override
	public MapCodec<TransformBlockBeeEffect> codec() {
		return MAP_CODEC;
	}

	public List<Transform> transforms() {
		return this.transforms;
	}

	public int attempts() {
		return this.attempts;
	}

	public float chance() {
		return this.chance;
	}

	public Optional<TemperatureType> maxTemperature() {
		return this.maxTemperature;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		if (level.isClientSide) {
			return storedData;
		}
		// Inclusive: the activation is skipped only when the housing is strictly warmer than the bound.
		if (this.maxTemperature.isPresent() && !housing.temperature().isCoolerOrEqual(this.maxTemperature.get())) {
			return storedData;
		}
		RandomSource rand = level.random;
		// Skip the RNG draw entirely when chance is 1 so a guaranteed effect does not perturb the shared world RNG
		// state.
		if (this.chance < 1.0f && rand.nextFloat() >= this.chance) {
			return storedData;
		}

		Vec3i area = Bee.getParticleArea(genome, housing);
		BlockPos center = housing.getCoordinates().offset(VecUtil.center(area));

		for (int i = 0; i < this.attempts; i++) {
			BlockPos pos = VecUtil.getRandomPositionInArea(rand, area).offset(center);
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState current = level.getBlockState(pos);
			for (Transform transform : this.transforms) {
				if (!current.is(transform.from())) {
					continue;
				}
				if (transform.requiresAirAbove() && !level.isEmptyBlock(pos.above())) {
					break;
				}
				BlockState next = transform.to().apply(current);
				// Identity guard. Block states are interned, so reference equality is state equality. Skipping the
				// no-op write also avoids firing spurious neighbour updates (observers, redstone) on a rewrite that
				// changes nothing.
				if (next != current) {
					level.setBlockAndUpdate(pos, next);
				}
				break;
			}
		}

		return storedData;
	}
}
```

- [ ] **Step 5: Compile and run the tests**

Run: `./gradlew compileJava compileTestJava && ./gradlew runGameTestServer`

Expected: BUILD SUCCESSFUL — `effectDefinitionRoundTrips`, `transformIdentityGuardSkipsNoOps` and `datapackEffectsMergeOntoBuiltins` pass.

- [ ] **Step 6: Confirm no generated JSON moved**

Run: `./gradlew runData && git status --porcelain src/generated/resources/`
Expected: **empty output** — nothing emits `transform_block` yet.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/forestry/api/core/TemperatureType.java \
        src/main/java/forestry/apiculture/genetics/effects/TransformBlockBeeEffect.java \
        src/test/java/forestry/gametest/BeeEffectSystemTest.java
git commit -m "Generalize transform_block into a tag-matching, multi-attempt primitive"
```

---

### Task 3: `damage_entities` gains a target filter; migrate HEROIC

**Files:**
- Modify: `src/main/java/forestry/apiculture/genetics/effects/DamageBeeEffect.java:25-98`
- Delete: `src/main/java/forestry/apiculture/genetics/effects/HeroicBeeEffect.java`
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java:249`
- Modify: `src/main/java/forestry/core/data/BeeEffectProvider.java:68-71`
- Modify: `src/test/java/forestry/gametest/BeeEffectSystemTest.java:249-288`
- Regenerate: `src/generated/resources/data/forestry/bee_effect/bee_effect_heroic.json` (new), `bee_effect_misanthrope.json` (changed)

**Interfaces:**
- Consumes: `ThrottleSettings` (Task 1).
- Produces:
  - `DamageBeeEffect(ThrottleSettings settings, float damage, boolean armorScaling, float chance, ResourceKey<DamageType> damageType, Target target)`
  - `sealed interface DamageBeeEffect.Target { Class<? extends LivingEntity> entityClass(); boolean matches(LivingEntity entity); }`
  - `enum DamageBeeEffect.Target.Builtin { ALL, PLAYERS, MONSTERS }`
  - `record DamageBeeEffect.Target.TagTarget(TagKey<EntityType<?>> tag)`

- [ ] **Step 1: Write the failing test**

In `src/test/java/forestry/gametest/BeeEffectSystemTest.java`, replace `damageEntitiesBuiltinsAreDatapackDefined` (lines 242-288) with:

```java
	/**
	 * AGGRESSIVE, MISANTHROPE and HEROIC are generalized into the {@code forestry:damage_entities} primitive,
	 * differing only by damage, armor scaling, damage type and target filter. The type is registered, all three
	 * built-ins are datapack-defined, and the bees that carry them (SINISTER/AGGRESSIVE, ENDED/MISANTHROPE,
	 * HEROIC/HEROIC) resolve their genome effect to the datapack instance.
	 */
	@GameTest(template = "empty")
	public static void damageEntitiesBuiltinsAreDatapackDefined(GameTestHelper helper) {
		if (!ForestryRegistries.BEE_EFFECT_TYPE.containsKey(ForestryConstants.forestry("damage_entities"))) {
			helper.fail("damage_entities effect type not registered: forestry:damage_entities");
			return;
		}
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		for (ResourceLocation id : new ResourceLocation[]{
			ForestryBeeEffects.AGGRESSIVE, ForestryBeeEffects.MISANTHROPE, ForestryBeeEffects.HEROIC
		}) {
			if (!(beeType.getBeeEffect(id) instanceof DamageBeeEffect)) {
				helper.fail("datapack-defined built-in " + id + " did not resolve to a DamageBeeEffect");
				return;
			}
		}
		// All three are non-combinable; the code default for damage_entities is combinable, so the migrated built-ins
		// must keep that flag off (the JSON sets "combinable": false).
		for (ResourceLocation id : new ResourceLocation[]{
			ForestryBeeEffects.AGGRESSIVE, ForestryBeeEffects.MISANTHROPE, ForestryBeeEffects.HEROIC
		}) {
			if (beeType.getBeeEffect(id).isCombinable()) {
				helper.fail(id + " must resolve as non-combinable");
				return;
			}
		}
		// HEROIC is the migration that needed requires_working exposed: it only fires for a working queen.
		if (!(beeType.getBeeEffect(ForestryBeeEffects.HEROIC) instanceof DamageBeeEffect heroic)
			|| !heroic.settings().requiresWorking() || heroic.settings().dominant() || heroic.getThrottle() != 40) {
			helper.fail("HEROIC did not preserve its throttle settings through the datapack migration");
			return;
		}
		if (!(effectOf(ForestryBeeSpecies.SINISTER) instanceof DamageBeeEffect)
			|| !(effectOf(ForestryBeeSpecies.ENDED) instanceof DamageBeeEffect)
			|| !(effectOf(ForestryBeeSpecies.HEROIC) instanceof DamageBeeEffect)) {
			helper.fail("Sinister/Ended/Heroic bees did not resolve their genome effect to the datapack DamageBeeEffect");
			return;
		}

		// The damage type and target filter survive the dispatch codec. `target` accepts the class-based builtins and
		// an entity-type tag; MONSTERS exists as a builtin because Monster is a class, so it catches modded monsters
		// and no vanilla entity-type tag is equivalent.
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		DamageBeeEffect misanthrope = new DamageBeeEffect(new ThrottleSettings(true, 20, false, false), 4f, true, 1.0f,
			CoreDamageTypes.MISANTHROPE, DamageBeeEffect.Target.Builtin.PLAYERS);
		JsonElement json = IBeeEffect.CODEC.encodeStart(ops, misanthrope).getOrThrow();
		if (!json.getAsJsonObject().get("target").getAsString().equals("players")
			|| !json.getAsJsonObject().get("damage_type").getAsString().equals("forestry:misanthrope")
			|| json.getAsJsonObject().get("combinable").getAsBoolean()
			|| !(IBeeEffect.CODEC.parse(ops, json).getOrThrow() instanceof DamageBeeEffect)) {
			helper.fail("damage_entities damage_type/target/combinable did not round-trip through IBeeEffect.CODEC: " + json);
			return;
		}
		// The default target is every living entity and stays out of the JSON.
		JsonElement aggressive = IBeeEffect.CODEC.encodeStart(ops, new DamageBeeEffect(new ThrottleSettings(true, 40, false, false),
			4f, true, 1.0f, CoreDamageTypes.AGGRESSIVE, DamageBeeEffect.Target.Builtin.ALL)).getOrThrow();
		if (aggressive.getAsJsonObject().has("target")) {
			helper.fail("damage_entities emitted the defaulted target: " + aggressive);
			return;
		}
		// The tag branch round-trips too.
		DamageBeeEffect tagged = new DamageBeeEffect(new ThrottleSettings(true, 40, false, true), 1f, false, 1.0f,
			DamageTypes.GENERIC, new DamageBeeEffect.Target.TagTarget(EntityTypeTags.SKELETONS));
		JsonElement taggedJson = IBeeEffect.CODEC.encodeStart(ops, tagged).getOrThrow();
		if (!taggedJson.getAsJsonObject().getAsJsonObject("target").get("tag").getAsString().equals("#minecraft:skeletons")
			|| !(IBeeEffect.CODEC.parse(ops, taggedJson).getOrThrow() instanceof DamageBeeEffect decodedTag)
			|| !(decodedTag.target() instanceof DamageBeeEffect.Target.TagTarget)) {
			helper.fail("damage_entities tag target did not round-trip: " + taggedJson);
			return;
		}

		helper.succeed();
	}
```

Add the imports `net.minecraft.tags.EntityTypeTags` and `net.minecraft.world.damagesource.DamageTypes`.

(`ForestryBeeSpecies.HEROIC` is the Heroic bee, and it is the one that carries `ForestryBeeEffects.HEROIC` —
`DefaultBeeSpecies.java:453,458`. GLACIAL has no bee carrier, which is why Task 4's test asserts no species for it.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew compileTestJava`
Expected: FAIL — `cannot find symbol: class Target` / `no suitable constructor found for DamageBeeEffect`.

- [ ] **Step 3: Add the `Target` filter to `DamageBeeEffect`**

In `src/main/java/forestry/apiculture/genetics/effects/DamageBeeEffect.java`, update the class javadoc to name HEROIC alongside AGGRESSIVE/MISANTHROPE, then replace lines 33-98 with:

```java
public class DamageBeeEffect extends ThrottledBeeEffect {
	/** Which entities in the territory the damage applies to. */
	public sealed interface Target {
		Codec<Target> CODEC = Codec.either(Builtin.CODEC, TagTarget.CODEC)
			.xmap(either -> either.map(builtin -> (Target) builtin, tag -> (Target) tag),
				target -> target instanceof Builtin builtin ? Either.left(builtin) : Either.right((TagTarget) target));

		/** The narrowest class this target can match; the territory scan is pre-filtered on it. */
		Class<? extends LivingEntity> entityClass();

		/** Further filtering within {@link #entityClass()}. */
		default boolean matches(LivingEntity entity) {
			return true;
		}

		/**
		 * The class-based targets. These exist alongside the tag branch because {@link Monster} is a class rather than
		 * a tag: it catches modded monsters automatically, and no vanilla entity-type tag is equivalent, so a tag-only
		 * filter would silently narrow HEROIC.
		 */
		enum Builtin implements Target, StringRepresentable {
			ALL(LivingEntity.class),
			PLAYERS(Player.class),
			MONSTERS(Monster.class);

			public static final Codec<Builtin> CODEC = StringRepresentable.fromEnum(Builtin::values);

			private final Class<? extends LivingEntity> entityClass;

			Builtin(Class<? extends LivingEntity> entityClass) {
				this.entityClass = entityClass;
			}

			@Override
			public Class<? extends LivingEntity> entityClass() {
				return this.entityClass;
			}

			@Override
			public String getSerializedName() {
				return name().toLowerCase(Locale.ROOT);
			}
		}

		/** An entity-type tag: {@code "target": {"tag": "#c:bosses"}}. */
		record TagTarget(TagKey<EntityType<?>> tag) implements Target {
			public static final Codec<TagTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TagKey.hashedCodec(Registries.ENTITY_TYPE).fieldOf("tag").forGetter(TagTarget::tag)
			).apply(instance, TagTarget::new));

			@Override
			public Class<? extends LivingEntity> entityClass() {
				return LivingEntity.class;
			}

			@Override
			public boolean matches(LivingEntity entity) {
				return entity.getType().is(this.tag);
			}
		}
	}

	public static final MapCodec<DamageBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ThrottleSettings.codec(40, false, true).forGetter(ThrottledBeeEffect::settings),
		Codec.floatRange(0f, Float.MAX_VALUE).fieldOf("damage").forGetter(effect -> effect.damage),
		Codec.BOOL.optionalFieldOf("armor_scaling", true).forGetter(effect -> effect.armorScaling),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1.0f).forGetter(effect -> effect.chance),
		ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypes.GENERIC).forGetter(effect -> effect.damageType),
		Target.CODEC.optionalFieldOf("target", Target.Builtin.ALL).forGetter(effect -> effect.target)
	).apply(instance, DamageBeeEffect::new));

	private final float damage;
	private final boolean armorScaling;
	private final float chance;
	private final ResourceKey<DamageType> damageType;
	private final Target target;

	public DamageBeeEffect(ThrottleSettings settings, float damage, boolean armorScaling, float chance, ResourceKey<DamageType> damageType, Target target) {
		super(settings);
		this.damage = damage;
		this.armorScaling = armorScaling;
		this.chance = chance;
		this.damageType = damageType;
		this.target = target;
	}

	@Override
	public MapCodec<DamageBeeEffect> codec() {
		return MAP_CODEC;
	}

	public Target target() {
		return this.target;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		RandomSource rand = level.random;
		List<? extends LivingEntity> entities = ThrottledBeeEffect.getEntitiesInRange(genome, housing, this.target.entityClass());
		DamageSource source = CoreDamageTypes.source(level, this.damageType);

		for (LivingEntity entity : entities) {
			if (!this.target.matches(entity)) {
				continue;
			}

			// Skip the RNG draw entirely when chance is 1 so a guaranteed effect (e.g. AGGRESSIVE/MISANTHROPE) does
			// not perturb the shared world RNG state.
			if (this.chance < 1.0f && rand.nextFloat() >= this.chance) {
				continue;
			}

			float damage = this.damage;
			if (this.armorScaling) {
				// Entities wearing apiarist's armor take reduced (or no) damage.
				int count = BeeManager.armorApiaristHelper.wearsItems(entity, this, true);
				damage -= count * (this.damage / 4f);
			}
			if (damage <= 0f) {
				continue;
			}

			entity.hurt(source, damage);
		}

		return storedData;
	}
}
```

Update the imports: add `java.util.Locale`, `com.mojang.datafixers.util.Either`, `net.minecraft.tags.TagKey`,
`net.minecraft.util.StringRepresentable`, `net.minecraft.world.entity.EntityType`,
`net.minecraft.world.entity.monster.Monster`; remove `forestry.api.apiculture.genetics.IBeeEffect` if it becomes unused.

- [ ] **Step 4: Find and fix every remaining `DamageBeeEffect` construction**

Run: `grep -rn "new DamageBeeEffect(" src/main src/test`

Expected callers: `BeeEffectProvider` (2) and `BeeEffectSystemTest` (already rewritten in Step 1). Both old
constructors are gone; every call site must pass a `ThrottleSettings` and a `Target`. If the grep turns up a caller not
listed here, port it the same way rather than reinstating a legacy constructor.

- [ ] **Step 5: Migrate HEROIC into the provider**

In `src/main/java/forestry/core/data/BeeEffectProvider.java`, replace the two `damage_entities` lines (68-71) with:

```java
		// The area-damage builtins, expressed through the forestry:damage_entities primitive, differing only by damage,
		// armor scaling, damage type and target filter.
		add(ForestryBeeEffects.AGGRESSIVE, new DamageBeeEffect(new ThrottleSettings(true, 40, false, false), 4f, true, 1.0f, CoreDamageTypes.AGGRESSIVE, DamageBeeEffect.Target.Builtin.ALL));
		add(ForestryBeeEffects.MISANTHROPE, new DamageBeeEffect(new ThrottleSettings(true, 20, false, false), 4f, true, 1.0f, CoreDamageTypes.MISANTHROPE, DamageBeeEffect.Target.Builtin.PLAYERS));
		add(ForestryBeeEffects.HEROIC, new DamageBeeEffect(new ThrottleSettings(false, 40, true, false), 2f, false, 1.0f, CoreDamageTypes.HEROIC, DamageBeeEffect.Target.Builtin.MONSTERS));
```

Add the import `forestry.apiculture.genetics.effects.ThrottleSettings`.

Every value above is read straight off `HeroicBeeEffect`: `super(false, 40, true, false)` gives the settings; the body
calls `mob.hurt(CoreDamageTypes.source(level, CoreDamageTypes.HEROIC), 2)` on `Monster.class` entities with no
`BeeManager.armorApiaristHelper` check, giving `damage 2`, `armor_scaling false`, `target monsters`.

- [ ] **Step 6: Drop the code registration and delete the class**

In `src/main/java/forestry/plugin/DefaultForestryPlugin.java`, delete line 249:

```java
		apiculture.registerBeeEffect(ForestryBeeEffects.HEROIC, new HeroicBeeEffect());
```

Remove the now-unused `HeroicBeeEffect` import, then:

```bash
git rm src/main/java/forestry/apiculture/genetics/effects/HeroicBeeEffect.java
```

- [ ] **Step 7: Regenerate the JSON**

Run: `./gradlew runData && git status --porcelain src/generated/resources/data/forestry/bee_effect/`

Expected: exactly two lines — `?? bee_effect_heroic.json` (new) and `M bee_effect_misanthrope.json` (`players_only`
becomes `target: "players"`). **Any third line is a regression** — the other 8 files must be untouched. Inspect the diff
with `git diff src/generated/resources/data/forestry/bee_effect/` and confirm `bee_effect_heroic.json` carries
`damage: 2.0`, `armor_scaling: false`, `combinable: false`, `dominant: false`, `requires_working: true`,
`target: "monsters"`, `damage_type: "forestry:heroic"`, and no `throttle` (40 is the default) and no `chance`.

- [ ] **Step 8: Run the tests**

Run: `./gradlew runGameTestServer`
Expected: BUILD SUCCESSFUL — `damageEntitiesBuiltinsAreDatapackDefined` passes with HEROIC folded in.

- [ ] **Step 9: Commit**

```bash
git add -A src/main/java/forestry/apiculture/genetics/effects/ \
           src/main/java/forestry/core/data/BeeEffectProvider.java \
           src/main/java/forestry/plugin/DefaultForestryPlugin.java \
           src/test/java/forestry/gametest/BeeEffectSystemTest.java \
           src/generated/resources/data/forestry/bee_effect/
git commit -m "Replace damage_entities players_only with a target filter; migrate HEROIC"
```

---

### Task 4: Migrate SIFTER, GLACIAL and GLOW_BERRY_GROW

**Files:**
- Modify: `src/main/java/forestry/core/data/BeeEffectProvider.java:26-49,55-72,92-101`
- Modify: `src/main/java/forestry/core/data/Data.java:63`
- Delete: `SifterBeeEffect.java`, `GlacialBeeEffect.java`, `GlowBerryGrowEffect.java`
- Modify: `src/main/java/forestry/plugin/DefaultForestryPlugin.java:250,260,262`
- Modify: `src/test/java/forestry/gametest/BeeEffectSystemTest.java`
- Regenerate: `bee_effect_sifter.json`, `bee_effect_glacial.json`, `bee_effect_glow_berry_grow.json` (all new)

**Interfaces:**
- Consumes: `TransformBlockBeeEffect(ThrottleSettings, List<Transform>, int, float, Optional<TemperatureType>)`, `Transform`, `To.Fixed`, `To.SetProperties` (Task 2).
- Produces: `BeeEffectProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider)` — the single-arg constructor is gone.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/forestry/gametest/BeeEffectSystemTest.java`:

```java
	/**
	 * SIFTER, GLACIAL and GLOW_BERRY_GROW are generalized into the {@code forestry:transform_block} primitive: the
	 * type is registered, all three built-ins are datapack-defined, and each preserves the throttle settings and the
	 * knobs that blocked its migration (GLACIAL's temperature ceiling and its 10 attempts; SIFTER's 550-tick throttle).
	 */
	@GameTest(template = "empty")
	public static void transformBlockBuiltinsAreDatapackDefined(GameTestHelper helper) {
		if (!ForestryRegistries.BEE_EFFECT_TYPE.containsKey(ForestryConstants.forestry("transform_block"))) {
			helper.fail("transform_block effect type not registered: forestry:transform_block");
			return;
		}
		IBeeSpeciesType beeType = SpeciesUtil.BEE_TYPE.get();
		for (ResourceLocation id : new ResourceLocation[]{
			ForestryBeeEffects.SIFTER, ForestryBeeEffects.GLACIAL, ForestryBeeEffects.GLOW_BERRY_GROW
		}) {
			if (!(beeType.getBeeEffect(id) instanceof TransformBlockBeeEffect)) {
				helper.fail("datapack-defined built-in " + id + " did not resolve to a TransformBlockBeeEffect");
				return;
			}
		}

		// None of transform_block's codec defaults match what these three effects need — chance in particular
		// defaults to 0.06, and all three must be always-on. Every migrated effect states chance explicitly, so
		// assert it: a silent fallback to the default would drop them to ~6% activation and nothing else would notice.
		for (ResourceLocation id : new ResourceLocation[]{
			ForestryBeeEffects.SIFTER, ForestryBeeEffects.GLACIAL, ForestryBeeEffects.GLOW_BERRY_GROW
		}) {
			float chance = ((TransformBlockBeeEffect) beeType.getBeeEffect(id)).chance();
			if (chance != 1.0f) {
				helper.fail(id + " must be always-on, but its chance is " + chance);
				return;
			}
		}

		// SIFTER: dominant, combinable, 550-tick throttle, requires a working queen, one attempt.
		TransformBlockBeeEffect sifter = (TransformBlockBeeEffect) beeType.getBeeEffect(ForestryBeeEffects.SIFTER);
		if (!sifter.settings().equals(new ThrottleSettings(true, 550, true, true)) || sifter.attempts() != 1) {
			helper.fail("SIFTER did not preserve its settings through the datapack migration: " + sifter.settings());
			return;
		}
		if (!Blocks.DIRT.defaultBlockState().is(sifter.transforms().getFirst().from())
			|| !Blocks.GRASS_BLOCK.defaultBlockState().is(sifter.transforms().getFirst().from())) {
			helper.fail("SIFTER's from tag no longer matches the dirt blocks it used to sift");
			return;
		}

		// GLACIAL: 10 attempts, a NORMAL temperature ceiling, and air required above the water it freezes.
		TransformBlockBeeEffect glacial = (TransformBlockBeeEffect) beeType.getBeeEffect(ForestryBeeEffects.GLACIAL);
		if (!glacial.settings().equals(new ThrottleSettings(false, 200, true, false)) || glacial.attempts() != 10
			|| !glacial.maxTemperature().equals(Optional.of(TemperatureType.NORMAL))) {
			helper.fail("GLACIAL did not preserve its settings/attempts/temperature ceiling: " + glacial.settings());
			return;
		}
		TransformBlockBeeEffect.Transform freeze = glacial.transforms().getFirst();
		if (!freeze.requiresAirAbove() || !Blocks.WATER.defaultBlockState().is(freeze.from())
			|| !(freeze.to() instanceof TransformBlockBeeEffect.To.Fixed ice) || !ice.state().is(Blocks.ICE)) {
			helper.fail("GLACIAL no longer freezes water into ice with air above it");
			return;
		}

		// GLOW_BERRY_GROW: a property mutation over the cave-vine tag, preserving the vine's other properties.
		TransformBlockBeeEffect glow = (TransformBlockBeeEffect) beeType.getBeeEffect(ForestryBeeEffects.GLOW_BERRY_GROW);
		if (!glow.settings().equals(new ThrottleSettings(false, 200, true, true)) || glow.attempts() != 1) {
			helper.fail("GLOW_BERRY_GROW did not preserve its settings through the datapack migration: " + glow.settings());
			return;
		}
		TransformBlockBeeEffect.Transform grow = glow.transforms().getFirst();
		if (!Blocks.CAVE_VINES.defaultBlockState().is(grow.from())
			|| !Blocks.CAVE_VINES_PLANT.defaultBlockState().is(grow.from())
			|| !(grow.to() instanceof TransformBlockBeeEffect.To.SetProperties)) {
			helper.fail("GLOW_BERRY_GROW no longer sets berries on the cave-vine blocks");
			return;
		}
		if (!grow.to().apply(Blocks.CAVE_VINES.defaultBlockState()).getValue(BlockStateProperties.BERRIES)) {
			helper.fail("GLOW_BERRY_GROW's transform did not set berries on a bare cave vine");
			return;
		}

		helper.succeed();
	}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew runGameTestServer`
Expected: FAIL — `datapack-defined built-in forestry:bee_effect_sifter did not resolve to a TransformBlockBeeEffect`
(SIFTER still resolves to the code-registered `SifterBeeEffect`).

- [ ] **Step 3: Make `BeeEffectProvider` registry-aware**

`RegistryCodecs.homogeneousList(Registries.BLOCK)` needs `RegistryOps`, which the provider does not have. Mirror
`BeeSpeciesProvider`. In `src/main/java/forestry/core/data/BeeEffectProvider.java`, correct the javadoc sentence that
claims no registry access is needed:

> Encoding needs no registry access: the only registry-backed field is the mob effect, which lives in the static `BuiltInRegistries.MOB_EFFECT` and encodes to a plain resource location.

becomes

> Encoding needs {@link RegistryOps} because {@code transform_block}'s {@code from} is a {@link HolderSet} of blocks; the other registry-backed fields live in static {@code BuiltInRegistries} and would encode under plain {@code JsonOps}.

Then replace the fields, constructors and `run` (lines 38-49 and 92-101):

```java
	private final PackOutput.PathProvider path;
	private final CompletableFuture<HolderLookup.Provider> lookupProvider;
	private final Map<ResourceLocation, IBeeEffect> pending = new LinkedHashMap<>();

	public BeeEffectProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
		this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "bee_effect");
		this.lookupProvider = lookupProvider;
	}

	// Collector used by seedLiveBeeEffectsForDatagen: gathers the built-ins via addEffects() without needing a
	// PackOutput to write to (it never runs the provider). Never call this to write JSON - both fields are null.
	private BeeEffectProvider() {
		this.path = null;
		this.lookupProvider = null;
	}
```

```java
	@Override
	public CompletableFuture<?> run(CachedOutput output) {
		return this.lookupProvider.thenCompose(provider -> {
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);

			this.pending.clear();
			addEffects();
			var futures = this.pending.entrySet().stream().map(entry -> {
				JsonElement json = IBeeEffect.CODEC.encodeStart(ops, entry.getValue()).getOrThrow();
				return DataProvider.saveStable(output, json, this.path.json(entry.getKey()));
			}).toArray(CompletableFuture[]::new);
			return CompletableFuture.allOf(futures);
		});
	}
```

Add the imports `net.minecraft.core.HolderLookup`, `net.minecraft.core.HolderSet`, `net.minecraft.resources.RegistryOps`.

In `src/main/java/forestry/core/data/Data.java`, line 63 becomes:

```java
		generator.addProvider(event.includeServer(), new BeeEffectProvider(output, lookup));
```

(`lookup` is already in scope — `Data.java:33` reads it from the event for `BeeSpeciesProvider`.)

- [ ] **Step 4: Add the three provider entries**

In `BeeEffectProvider.addEffects()`, append:

```java
		// The three block-transforming builtins, expressed through the forestry:transform_block primitive.
		add(ForestryBeeEffects.SIFTER, new TransformBlockBeeEffect(
			new ThrottleSettings(true, 550, true, true),
			List.of(new TransformBlockBeeEffect.Transform(
				BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.DIRT),
				new TransformBlockBeeEffect.To.Fixed(Blocks.COARSE_DIRT.defaultBlockState()),
				false)),
			1, 1.0f, Optional.empty()));
		// max_temperature is inclusive, so NORMAL is exactly GLACIAL's "skip when WARM or warmer".
		add(ForestryBeeEffects.GLACIAL, new TransformBlockBeeEffect(
			new ThrottleSettings(false, 200, true, false),
			List.of(new TransformBlockBeeEffect.Transform(
				HolderSet.direct(Blocks.WATER.builtInRegistryHolder()),
				new TransformBlockBeeEffect.To.Fixed(Blocks.ICE.defaultBlockState()),
				true)),
			10, 1.0f, Optional.of(TemperatureType.NORMAL)));
		// The tag narrows this to the two vanilla cave-vine blocks. The old check matched any block carrying the
		// BERRIES property, including modded ones; that was accidental scope, and a pack can extend the tag.
		add(ForestryBeeEffects.GLOW_BERRY_GROW, new TransformBlockBeeEffect(
			new ThrottleSettings(false, 200, true, true),
			List.of(new TransformBlockBeeEffect.Transform(
				BuiltInRegistries.BLOCK.getOrCreateTag(BlockTags.CAVE_VINES),
				new TransformBlockBeeEffect.To.SetProperties(Map.of("berries", "true")),
				false)),
			1, 1.0f, Optional.empty()));
```

Add the imports `java.util.List`, `java.util.Map`, `java.util.Optional`, `forestry.api.core.TemperatureType`,
`forestry.apiculture.genetics.effects.TransformBlockBeeEffect`, `net.minecraft.core.registries.BuiltInRegistries`,
`net.minecraft.tags.BlockTags`, `net.minecraft.world.level.block.Blocks`.

- [ ] **Step 5: Drop the code registrations and delete the classes**

In `src/main/java/forestry/plugin/DefaultForestryPlugin.java`, delete these three lines (250, 260, 262):

```java
		apiculture.registerBeeEffect(ForestryBeeEffects.GLACIAL, new GlacialBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.SIFTER, new SifterBeeEffect());
		apiculture.registerBeeEffect(ForestryBeeEffects.GLOW_BERRY_GROW, new GlowBerryGrowEffect());
```

Remove the three now-unused imports, then:

```bash
git rm src/main/java/forestry/apiculture/genetics/effects/SifterBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/GlacialBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/GlowBerryGrowEffect.java
```

- [ ] **Step 6: Regenerate the JSON**

Run: `./gradlew runData && git status --porcelain src/generated/resources/data/forestry/bee_effect/`

Expected: exactly three new files — `bee_effect_sifter.json`, `bee_effect_glacial.json`,
`bee_effect_glow_berry_grow.json` — and **no modifications to the 11 existing files**. Read all three and confirm
`from` encoded as `"#minecraft:dirt"` / `"#minecraft:cave_vines"` and GLACIAL's water as a plain block id.

If `runData` fails encoding the `HolderSet` (the datagen lookup provider not owning `Registries.BLOCK`), **stop and
report** rather than working around it. The fallback is to replace `RegistryCodecs.homogeneousList(Registries.BLOCK)`
with a registry-free `Codec.either(TagKey.hashedCodec(Registries.BLOCK), BuiltInRegistries.BLOCK.byNameCodec())` and
revert Step 3 — that is a design change and needs a decision, not a silent substitution.

- [ ] **Step 7: Run the tests**

Run: `./gradlew runGameTestServer`
Expected: BUILD SUCCESSFUL — `transformBlockBuiltinsAreDatapackDefined` passes, and every other
`BeeEffectSystemTest` test still passes.

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java/forestry/ src/test/java/forestry/ src/generated/resources/data/forestry/bee_effect/
git commit -m "Migrate SIFTER, GLACIAL and GLOW_BERRY_GROW to datapack transform_block effects"
```

---

### Task 5: Delete the 11 unconsumed primitives

With HEROIC/SIFTER/GLACIAL/GLOW_BERRY_GROW migrated, these 11 primitive types have no built-in consumer. Per the
spec's principle — *a primitive earns its place in base Forestry only if a built-in effect consumes it* — they are
addon material, recoverable from git history.

**Files:**
- Delete (11): `SpawnMobBeeEffect`, `FeedBeeEffect`, `FireworkBeeEffect`, `LightningBeeEffect`, `TeleportBeeEffect`, `EntityForceBeeEffect`, `BonemealBeeEffect`, `ProjectileBeeEffect`, `PlaceBlockBeeEffect`, `FillFluidBeeEffect`, `InjectEnergyBeeEffect` — all under `src/main/java/forestry/apiculture/genetics/effects/`
- Modify: `src/main/java/forestry/apiculture/features/ApicultureBeeEffectTypes.java:21,23-29,31-33`
- Modify: `src/main/java/forestry/apiculture/genetics/effects/ThrottledBeeEffect.java:50-89`
- Modify: `src/test/java/forestry/gametest/BeeEffectSystemTest.java:37-43,52-67`

**Interfaces:**
- Consumes: nothing new.
- Produces: `ForestryRegistries.BEE_EFFECT_TYPE` holds exactly 5 entries — `apply_potion`, `damage_entities`, `transform_block`, `resurrect`, `aging`.

- [ ] **Step 1: Write the failing test**

In `src/test/java/forestry/gametest/BeeEffectSystemTest.java`, replace `allEffectPrimitiveTypesRegistered`
(lines 52-67) with:

```java
	/**
	 * Exactly the five primitives a built-in effect consumes are registered, and no more. A primitive with no built-in
	 * consumer is addon material: it is speculative surface area that base Forestry pays for in maintenance and that
	 * no shipped content proves works.
	 */
	@GameTest(template = "empty")
	public static void allEffectPrimitiveTypesRegistered(GameTestHelper helper) {
		String[] types = {"apply_potion", "damage_entities", "transform_block", "resurrect", "aging"};
		for (String type : types) {
			ResourceLocation id = ForestryConstants.forestry(type);
			if (!ForestryRegistries.BEE_EFFECT_TYPE.containsKey(id)) {
				helper.fail("bee effect primitive type not registered: " + id);
				return;
			}
		}
		if (ForestryRegistries.BEE_EFFECT_TYPE.size() != types.length) {
			helper.fail("expected exactly " + types.length + " bee effect primitive types, found "
				+ ForestryRegistries.BEE_EFFECT_TYPE.size() + ": " + ForestryRegistries.BEE_EFFECT_TYPE.keySet());
			return;
		}
		helper.succeed();
	}
```

Also update the class javadoc (lines 37-43): it says "the 14 parameterized effect primitives are registered as
serializer types" — make it 5, and drop the "(migration Module 2)" framing only if it is now wrong; leave it otherwise.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew runGameTestServer`
Expected: FAIL — `expected exactly 5 bee effect primitive types, found 16`.

- [ ] **Step 3: Delete the 11 classes**

```bash
git rm src/main/java/forestry/apiculture/genetics/effects/SpawnMobBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/FeedBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/FireworkBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/LightningBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/TeleportBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/EntityForceBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/BonemealBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/ProjectileBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/PlaceBlockBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/FillFluidBeeEffect.java \
       src/main/java/forestry/apiculture/genetics/effects/InjectEnergyBeeEffect.java
```

- [ ] **Step 4: Remove the 11 registrations**

`src/main/java/forestry/apiculture/features/ApicultureBeeEffectTypes.java` keeps only the five survivors:

```java
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<PotionBeeEffect>> APPLY_POTION = BEE_EFFECT_TYPES.register("apply_potion", () -> PotionBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<DamageBeeEffect>> DAMAGE_ENTITIES = BEE_EFFECT_TYPES.register("damage_entities", () -> DamageBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<TransformBlockBeeEffect>> TRANSFORM_BLOCK = BEE_EFFECT_TYPES.register("transform_block", () -> TransformBlockBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<ResurrectionBeeEffect>> RESURRECT = BEE_EFFECT_TYPES.register("resurrect", () -> ResurrectionBeeEffect.MAP_CODEC);
	public static final DeferredHolder<MapCodec<? extends IBeeEffect>, MapCodec<AgingBeeEffect>> AGING = BEE_EFFECT_TYPES.register("aging", () -> AgingBeeEffect.MAP_CODEC);
```

The file imports `forestry.apiculture.genetics.effects.*`, so no import changes are needed.

Note: the working tree already has uncommitted edits to this file and to `ApicultureFeatures.java` (see
`git status` at branch start). Reconcile with them rather than clobbering — run
`git diff src/main/java/forestry/apiculture/features/` first and keep any unrelated change.

- [ ] **Step 5: Remove the two dead position helpers**

`getRandomPositionInRange` and `findPositionInRange` were written for the primitives just deleted. Confirm they have no
callers left:

Run: `grep -rn "getRandomPositionInRange\|findPositionInRange" src/main src/test`
Expected: only the two declarations in `ThrottledBeeEffect.java`.

Then delete both methods (lines 50-89) from `src/main/java/forestry/apiculture/genetics/effects/ThrottledBeeEffect.java`,
along with the now-unused imports `net.minecraft.util.RandomSource`, `java.util.function.Predicate`,
`javax.annotation.Nullable`, and — if the compiler flags it — `net.minecraft.core.BlockPos`.

- [ ] **Step 6: Verify the whole suite**

Run: `./gradlew compileJava compileTestJava && ./gradlew runGameTestServer`
Expected: BUILD SUCCESSFUL — `allEffectPrimitiveTypesRegistered` now passes at 5, and every other test still passes.

Run: `./gradlew runData && git status --porcelain src/generated/resources/`
Expected: **empty output** — deleting unconsumed primitives emits nothing.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/forestry/ src/test/java/forestry/
git commit -m "Delete the bee-effect primitives with no built-in consumer"
```

---

## Final verification

- [ ] `./gradlew build` — BUILD SUCCESSFUL.
- [ ] `./gradlew runGameTestServer` — BUILD SUCCESSFUL.
- [ ] `./gradlew runData && git status --porcelain src/generated/resources/` — empty (everything already committed).
- [ ] `ls src/generated/resources/data/forestry/bee_effect/` — 14 files.
- [ ] `git diff --stat 474489121..HEAD` — 15 files deleted, and `ForestryBeeEffects.java` is **not** among the modified files (effect IDs are unchanged; only their registration moved).
- [ ] `grep -rn "players_only" src/` — no hits.
- [ ] The four migrated effects still resolve in a real world: `./gradlew runClient`, create a world, place an apiary
      with a Sinister/Heroic/Glacial bee, and confirm no `Skipping bee effect` error in the log.

## Out of scope (spec §"Out of scope / follow-ups")

Do **not** address these here: widening `max_temperature` into a range; `ForestryBeeEffects.PATRIOTIC` being registered
nowhere (pre-existing); an `EXPLORATION` → `give_experience` primitive.
