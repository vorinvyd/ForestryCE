# IFluidProduct — Squeezer Fluid Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Squeezer recipe's fixed `FluidStack` (and the PR's stopgap `SizedFluidIngredient`) output with a dispatch-codec-backed `IFluidProduct`, so addons can supply dynamic fluid outputs while Forestry ships one fixed-fluid reference impl with byte-for-byte unchanged behavior.

**Architecture:** Introduce `IFluidProduct` / `FluidProductType` / `FluidProduct` in `forestry.api.core` (a fluid analog of the existing `IProduct` / `ProductType` / `Product`), plus a `FluidProductTypes` dispatch registry that mirrors `forestry.core.genetics.ProductTypes` exactly (optional `type` key, default resolves to `FluidProduct.TYPE`). Migrate `ISqueezerRecipe`, `SqueezerRecipe`, the recipe builder, `TileSqueezer`, and the JEI category to the new type.

**Tech Stack:** Java, Minecraft 1.21 / NeoForge, Mojang DataFixerUpper codecs (`MapCodec`, `StreamCodec`), NeoForge `FluidStack`. Tests are NeoForge **GameTests** in `src/test/java/forestry/gametest/`, run via `./gradlew runGameTestServer` (this repo has no JUnit source set).

## Global Constraints

- Package for the new public API: `forestry.api.core` (beside `IProduct`).
- Dispatch registry package: `forestry.core` (class `FluidProductTypes`).
- `FluidStack` is NeoForge's: `net.neoforged.neoforge.fluids.FluidStack`. This repo's pinned NeoForge (21.1.230) exposes `FluidStack.CODEC` (a `Codec<FluidStack>` serializing as `{"amount":N,"id":"<fluid id>"}`) and `FluidStack.STREAM_CODEC` — but **NO `MapCodec<FluidStack>`**. Reuse these; do not hand-roll fluid parsing. `FluidProduct.MAP_CODEC` is `FluidStack.CODEC.fieldOf("stack").xmap(...)`, matching the codebase's `FabricatorRecipe`/`HygroregulatorRecipe` pattern.
- The dispatch registry MUST mirror `forestry.core.genetics.ProductTypes`: `type` key optional, absent → `FluidProduct.TYPE`, and a `FluidProduct` encodes with **no** `type` key.
- Serialized shape (decided): a `FluidProduct` serializes as `{"stack": {"amount":N, "id":"<fluid id>"}}`; the squeezer recipe output is `"output": {"stack": {...}}`. Datagen regeneration WILL change existing squeezer recipe JSON from the PR's in-flight `SizedFluidIngredient` shape to this — expected, since that shape is unreleased. Fluid ids and amounts must be preserved; only the wrapper shape changes.
- **Test gate:** `./gradlew runGameTestServer` ALWAYS exits non-zero / `BUILD FAILED` on this branch due to two pre-existing unrelated failures (`everyallelevaluehasatranslation`, `defaultgenomesmatchbaseline`). Judge success by grepping the run log: the task's new test(s) pass and the failure set is EXACTLY those two names. Never judge by exit code.
- GameTest classes: annotate `@GameTestHolder(ForestryConstants.MOD_ID)` + `@PrefixGameTestTemplate(false)`, methods `@GameTest(template = "empty")`, in package `forestry.gametest`.
- Commit messages end with:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## File Structure

**Create:**
- `src/main/java/forestry/api/core/IFluidProduct.java` — the interface (3 methods).
- `src/main/java/forestry/api/core/FluidProductType.java` — `record(MapCodec, StreamCodec)` serializer holder.
- `src/main/java/forestry/api/core/FluidProduct.java` — reference impl wrapping a `FluidStack`.
- `src/main/java/forestry/core/FluidProductTypes.java` — dispatch registry (mirror of `ProductTypes`).
- `src/test/java/forestry/gametest/FluidProductCodecTest.java` — reference-impl + dispatch round-trip GameTests.
- `src/test/java/forestry/gametest/SqueezerRecipeCodecTest.java` — recipe round-trip GameTest.

**Modify:**
- `src/main/java/forestry/api/recipes/ISqueezerRecipe.java` — return type of `getFluidOutput()`.
- `src/main/java/forestry/factory/recipes/SqueezerRecipe.java` — field, codecs, accessor.
- `src/main/java/forestry/core/data/builder/SqueezerRecipeBuilder.java` — field + overloads.
- `src/main/java/forestry/apiimpl/plugin/PluginManager.java` — call `FluidProductTypes.registerBuiltins()`.
- `src/main/java/forestry/factory/tiles/TileSqueezer.java` — three call sites.
- `src/main/java/forestry/factory/recipes/jei/squeezer/SqueezerRecipeCategory.java` — display call site.

---

## Task 1: `IFluidProduct` interface, `FluidProductType`, and the `FluidProduct` reference impl

**Files:**
- Create: `src/main/java/forestry/api/core/IFluidProduct.java`
- Create: `src/main/java/forestry/api/core/FluidProductType.java`
- Create: `src/main/java/forestry/api/core/FluidProduct.java`
- Test: `src/test/java/forestry/gametest/FluidProductCodecTest.java`

**Interfaces:**
- Consumes: NeoForge `FluidStack.MAP_CODEC`, `FluidStack.STREAM_CODEC`.
- Produces:
  - `interface IFluidProduct { FluidStack createFluidStack(); FluidStack createRandomFluidStack(RandomSource random); FluidProductType<?> type(); }`
  - `record FluidProductType<T extends IFluidProduct>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)`
  - `record FluidProduct(FluidStack stack) implements IFluidProduct` with `public static final MapCodec<FluidProduct> MAP_CODEC`, `public static final StreamCodec<RegistryFriendlyByteBuf, FluidProduct> STREAM_CODEC`, `public static final FluidProductType<FluidProduct> TYPE`, and statics `of(FluidStack)` / `of(Fluid, int)`.

- [ ] **Step 1: Create the interface `IFluidProduct`**

`src/main/java/forestry/api/core/IFluidProduct.java`:

```java
package forestry.api.core;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A fluid analog of {@link IProduct}: some fluid output that a machine (currently the Squeezer) produces.
 * Serialized via the dispatch codec in {@code forestry.core.FluidProductTypes}.
 *
 * @see FluidProduct The default fixed-fluid implementation used by Forestry's own recipes.
 */
public interface IFluidProduct {
	/**
	 * The representative stack for this product. Used for display identity (a {@link FluidStack} carries data
	 * components, e.g. liquid potion effects) and as the MAXIMAL amount this product can produce. A machine reserves
	 * space for this amount before it begins processing. Returning {@link FluidStack#EMPTY} signals the product is not
	 * currently producible (e.g. an addon tag product whose tag no loaded mod fills), and the machine refuses the
	 * recipe.
	 *
	 * @return A new representative stack, or {@link FluidStack#EMPTY} if not currently producible.
	 */
	FluidStack createFluidStack();

	/**
	 * The actual fluid produced in one work cycle. May be a smaller amount than {@link #createFluidStack()}, or
	 * {@link FluidStack#EMPTY} if this product's own probability roll failed. Any per-cycle randomness (variable
	 * amount, chance of nothing) is folded in here; the machine does not roll it separately.
	 *
	 * @param random The random source. If no randomness is desired, this defaults to {@link #createFluidStack()}.
	 * @return A new stack for this cycle, possibly empty or smaller than the representative stack.
	 */
	default FluidStack createRandomFluidStack(RandomSource random) {
		return createFluidStack();
	}

	/**
	 * The type of this product, used to (de)serialize it via the dispatch codec in
	 * {@code forestry.core.FluidProductTypes}. Plain {@link FluidProduct} instances return {@link FluidProduct#TYPE},
	 * which the dispatch codec treats as the default: it serializes without a {@code "type"} key.
	 *
	 * @return The type of this product.
	 */
	FluidProductType<?> type();
}
```

- [ ] **Step 2: Create the serializer holder `FluidProductType`**

`src/main/java/forestry/api/core/FluidProductType.java`:

```java
package forestry.api.core;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The serializer for a kind of {@link IFluidProduct}, used to (de)serialize it via the dispatch codec built in
 * {@code forestry.core.FluidProductTypes}. Fluid analog of {@link ProductType}.
 * <p>
 * A {@link MapCodec} is required (rather than a plain {@link com.mojang.serialization.Codec}) so the product's fields
 * serialize inline alongside the optional {@code "type"} key instead of nesting under it.
 *
 * @param codec       The map codec for this product type's fields.
 * @param streamCodec The network codec for this product type.
 * @param <T>         The concrete {@link IFluidProduct} implementation this type serializes.
 */
public record FluidProductType<T extends IFluidProduct>(
	MapCodec<T> codec,
	StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

- [ ] **Step 3: Create the reference impl `FluidProduct`**

`src/main/java/forestry/api/core/FluidProduct.java`:

```java
package forestry.api.core;

import com.google.common.base.Preconditions;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Default implementation of {@link IFluidProduct}: a fixed fluid output. Used by all of Forestry's own squeezer
 * recipes. Wraps a {@link FluidStack} so fluid, amount, and data components are captured together.
 *
 * @param stack The fluid stack this product always produces. Must not be empty.
 */
public record FluidProduct(FluidStack stack) implements IFluidProduct {
	public FluidProduct {
		Preconditions.checkNotNull(stack);
		Preconditions.checkArgument(!stack.isEmpty(), "FluidProduct stack must not be empty");
	}

	public static final MapCodec<FluidProduct> MAP_CODEC = FluidStack.MAP_CODEC.xmap(FluidProduct::new, FluidProduct::stack);
	public static final StreamCodec<RegistryFriendlyByteBuf, FluidProduct> STREAM_CODEC = FluidStack.STREAM_CODEC.map(FluidProduct::new, FluidProduct::stack);
	/**
	 * The default product type. The dispatch codec in {@code forestry.core.FluidProductTypes} treats this type
	 * specially: products of this type serialize without a {@code "type"} key, and a missing {@code "type"} key on
	 * decode resolves back to it.
	 */
	public static final FluidProductType<FluidProduct> TYPE = new FluidProductType<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public FluidStack createFluidStack() {
		return this.stack.copy();
	}

	@Override
	public FluidStack createRandomFluidStack(RandomSource random) {
		return this.stack.copy();
	}

	@Override
	public FluidProductType<?> type() {
		return TYPE;
	}

	public static FluidProduct of(FluidStack stack) {
		return new FluidProduct(stack);
	}

	public static FluidProduct of(Fluid fluid, int amount) {
		return new FluidProduct(new FluidStack(fluid, amount));
	}
}
```

- [ ] **Step 4: Write the failing GameTest for the reference impl**

Create `src/test/java/forestry/gametest/FluidProductCodecTest.java` with just the reference-impl test for now (the dispatch test is added in Task 2):

```java
package forestry.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryConstants;
import forestry.api.core.FluidProduct;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class FluidProductCodecTest {
	@GameTest(template = "empty")
	public static void referenceImplProducesStack(GameTestHelper helper) {
		FluidProduct product = FluidProduct.of(Fluids.WATER, 1000);
		FluidStack stack = product.createFluidStack();
		FluidStack random = product.createRandomFluidStack(helper.getLevel().random);
		if (stack.getFluid() != Fluids.WATER || stack.getAmount() != 1000) {
			helper.fail("createFluidStack did not return the wrapped fluid/amount: " + stack);
			return;
		}
		if (random.getFluid() != Fluids.WATER || random.getAmount() != 1000) {
			helper.fail("createRandomFluidStack did not return the wrapped fluid/amount: " + random);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void referenceImplRoundTrips(GameTestHelper helper) {
		FluidProduct original = FluidProduct.of(Fluids.LAVA, 500);

		// Stream round-trip
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		FluidProduct.STREAM_CODEC.encode(buf, original);
		FluidProduct streamDecoded = FluidProduct.STREAM_CODEC.decode(buf);
		if (streamDecoded.stack().getFluid() != Fluids.LAVA || streamDecoded.stack().getAmount() != 500) {
			helper.fail("Stream round-trip lost fluid/amount: " + streamDecoded.stack());
			return;
		}

		// JSON round-trip (fluid holder codec needs a registry-backed ops)
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		var json = FluidProduct.MAP_CODEC.codec().encodeStart(jsonOps, original).getOrThrow();
		FluidProduct jsonDecoded = FluidProduct.MAP_CODEC.codec().parse(jsonOps, json).getOrThrow();
		if (jsonDecoded.stack().getFluid() != Fluids.LAVA || jsonDecoded.stack().getAmount() != 500) {
			helper.fail("JSON round-trip lost fluid/amount: " + json);
			return;
		}
		helper.succeed();
	}
}
```

- [ ] **Step 5: Compile and run the reference-impl GameTests**

Run: `./gradlew runGameTestServer --tests "*" 2>&1 | tail -40`
(Note: `runGameTestServer` runs the whole GameTest suite; there is no per-method filter. Confirm the run reports `FluidProductCodecTest.referenceImplProducesStack` and `referenceImplRoundTrips` as passed and the server exits 0.)
Expected: build succeeds; both new tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/forestry/api/core/IFluidProduct.java \
        src/main/java/forestry/api/core/FluidProductType.java \
        src/main/java/forestry/api/core/FluidProduct.java \
        src/test/java/forestry/gametest/FluidProductCodecTest.java
git commit -m "feat: add IFluidProduct API with FluidProduct reference impl

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: `FluidProductTypes` dispatch registry

**Files:**
- Create: `src/main/java/forestry/core/FluidProductTypes.java`
- Modify: `src/main/java/forestry/apiimpl/plugin/PluginManager.java` (near line 165)
- Test: `src/test/java/forestry/gametest/FluidProductCodecTest.java` (add dispatch tests)

**Interfaces:**
- Consumes: `IFluidProduct`, `FluidProductType`, `FluidProduct.TYPE` (Task 1); `ForestryConstants.forestry(String)`.
- Produces:
  - `FluidProductTypes.register(ResourceLocation, FluidProductType<?>)`
  - `FluidProductTypes.MAP_CODEC` (`MapCodec<IFluidProduct>`), `FluidProductTypes.CODEC` (`Codec<IFluidProduct>`)
  - `FluidProductTypes.STREAM_CODEC` (`StreamCodec<RegistryFriendlyByteBuf, IFluidProduct>`)
  - `FluidProductTypes.registerBuiltins()`

- [ ] **Step 1: Create the dispatch registry**

`src/main/java/forestry/core/FluidProductTypes.java` — a direct mirror of `forestry.core.genetics.ProductTypes`:

```java
package forestry.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.core.FluidProduct;
import forestry.api.core.FluidProductType;
import forestry.api.core.IFluidProduct;

/**
 * Registry and dispatch codec for {@link IFluidProduct} types. Mirrors {@code forestry.core.genetics.ProductTypes}:
 * the {@code "type"} key is optional. When absent, the product decodes as the default {@link FluidProduct#TYPE}, and a
 * {@link FluidProduct} encodes without writing a {@code "type"} key at all. This keeps the common case (a fixed fluid)
 * as clean, backwards-compatible JSON, while dynamic products (addon-provided tag/random/chance outputs) round-trip
 * through their own type by declaring {@code "type"}.
 */
public final class FluidProductTypes {
	private static final Map<ResourceLocation, FluidProductType<?>> BY_ID = new ConcurrentHashMap<>();
	private static final Map<FluidProductType<?>, ResourceLocation> ID_OF = new ConcurrentHashMap<>();

	private static final String TYPE_KEY = "type";

	private static boolean builtinsRegistered = false;

	public static void register(ResourceLocation id, FluidProductType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate fluid product type: " + id);
		}
		ID_OF.put(type, id);
	}

	private static DataResult<FluidProductType<?>> byId(ResourceLocation id) {
		FluidProductType<?> type = BY_ID.get(id);
		if (type == null) {
			return DataResult.error(() -> "Unknown fluid product type: " + id);
		}
		return DataResult.success(type);
	}

	/**
	 * The dispatch codec. Unlike a stock {@link Codec#dispatch}, the {@code "type"} key is optional and defaults to
	 * {@link FluidProduct#TYPE}; encoding a {@link FluidProduct} omits the key entirely.
	 */
	public static final MapCodec<IFluidProduct> MAP_CODEC = new MapCodec<>() {
		@Override
		public <T> DataResult<IFluidProduct> decode(DynamicOps<T> ops, MapLike<T> input) {
			T typeValue = input.get(TYPE_KEY);
			DataResult<FluidProductType<?>> type = typeValue == null
				? DataResult.success(FluidProduct.TYPE)
				: ResourceLocation.CODEC.parse(ops, typeValue).flatMap(FluidProductTypes::byId);
			return type.flatMap(t -> t.codec().decode(ops, input).map(product -> (IFluidProduct) product));
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public <T> RecordBuilder<T> encode(IFluidProduct input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
			FluidProductType<?> type = input.type();
			if (type != FluidProduct.TYPE) {
				ResourceLocation id = ID_OF.get(type);
				if (id == null) {
					return prefix.withErrorsFrom(DataResult.error(() -> "Unregistered fluid product type: " + type));
				}
				prefix.add(TYPE_KEY, ResourceLocation.CODEC.encodeStart(ops, id));
			}
			return ((MapCodec) type.codec()).encode(input, ops, prefix);
		}

		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Stream.of(ops.createString(TYPE_KEY));
		}
	};

	public static final Codec<IFluidProduct> CODEC = MAP_CODEC.codec();

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final StreamCodec<RegistryFriendlyByteBuf, IFluidProduct> STREAM_CODEC = StreamCodec.of(
		(buf, product) -> {
			ResourceLocation id = ID_OF.get(product.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			((StreamCodec) product.type().streamCodec()).encode(buf, product);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return byId(id).getOrThrow().streamCodec().decode(buf);
		});

	/**
	 * Registers the built-in fluid product types under the {@code forestry} namespace.
	 * <p>
	 * Must be called before any datapack parse or network sync. Idempotent: repeated calls are no-ops.
	 */
	public static synchronized void registerBuiltins() {
		if (builtinsRegistered) {
			return;
		}
		builtinsRegistered = true;

		register(ForestryConstants.forestry("fluid"), FluidProduct.TYPE);
	}

	private FluidProductTypes() {}
}
```

- [ ] **Step 2: Wire `registerBuiltins()` into `PluginManager`**

In `src/main/java/forestry/apiimpl/plugin/PluginManager.java`, directly after the existing `forestry.core.genetics.ProductTypes.registerBuiltins();` call (currently line 165), add:

```java
		// Register the built-in fluid product types so the optional `type` key on machine fluid outputs (e.g. the
		// squeezer) resolves before any recipe JSON parse or network sync.
		forestry.core.FluidProductTypes.registerBuiltins();
```

- [ ] **Step 3: Add the failing dispatch GameTests**

Append two methods to `src/test/java/forestry/gametest/FluidProductCodecTest.java`. Add these imports at the top of the file:

```java
import net.minecraft.world.level.material.Fluids;   // already present from Task 1
import forestry.api.core.IFluidProduct;
import forestry.core.FluidProductTypes;
```

Add the methods inside the class body:

```java
	@GameTest(template = "empty")
	public static void dispatchDefaultTypeOmitsTypeKey(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		IFluidProduct product = FluidProduct.of(Fluids.WATER, 1000);

		var json = FluidProductTypes.CODEC.encodeStart(jsonOps, product).getOrThrow();
		if (json.isJsonObject() && json.getAsJsonObject().has("type")) {
			helper.fail("Default FluidProduct must serialize without a 'type' key, got: " + json);
			return;
		}
		IFluidProduct decoded = FluidProductTypes.CODEC.parse(jsonOps, json).getOrThrow();
		if (!(decoded instanceof FluidProduct fp) || fp.stack().getFluid() != Fluids.WATER || fp.stack().getAmount() != 1000) {
			helper.fail("Dispatch JSON round-trip failed for default FluidProduct: " + json);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void dispatchStreamRoundTrip(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		IFluidProduct product = FluidProduct.of(Fluids.LAVA, 500);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		FluidProductTypes.STREAM_CODEC.encode(buf, product);
		IFluidProduct decoded = FluidProductTypes.STREAM_CODEC.decode(buf);
		if (!(decoded instanceof FluidProduct fp) || fp.stack().getFluid() != Fluids.LAVA || fp.stack().getAmount() != 500) {
			helper.fail("Dispatch stream round-trip failed for default FluidProduct");
			return;
		}
		helper.succeed();
	}
```

- [ ] **Step 4: Compile and run the GameTests**

Run: `./gradlew runGameTestServer 2>&1 | tail -40`
Expected: build succeeds; `dispatchDefaultTypeOmitsTypeKey` and `dispatchStreamRoundTrip` pass alongside the Task 1 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/forestry/core/FluidProductTypes.java \
        src/main/java/forestry/apiimpl/plugin/PluginManager.java \
        src/test/java/forestry/gametest/FluidProductCodecTest.java
git commit -m "feat: add FluidProductTypes dispatch registry

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Migrate the recipe layer to `IFluidProduct`

> **SCOPE NOTE (execution):** Changing `ISqueezerRecipe.getFluidOutput()` from `FluidStack` to
> `IFluidProduct` breaks compilation in `TileSqueezer` and `SqueezerRecipeCategory` (they call
> `FluidStack` methods on the result). Because `runGameTestServer` compiles the whole mod, this task
> cannot build or run its recipe codec gametest until those consumers are migrated too. Therefore
> **Task 3 also performs Task 4's code Steps 1–4** (the `TileSqueezer` 3 sites + JEI change) in the
> same commit. Task 4 then only regenerates and verifies datagen. Do Task 4 Steps 1–4 alongside the
> Task 3 steps below before running the gametest.

**Files:**
- Modify: `src/main/java/forestry/api/recipes/ISqueezerRecipe.java`
- Modify: `src/main/java/forestry/factory/recipes/SqueezerRecipe.java`
- Modify: `src/main/java/forestry/core/data/builder/SqueezerRecipeBuilder.java`
- Test: `src/test/java/forestry/gametest/SqueezerRecipeCodecTest.java`

**Interfaces:**
- Consumes: `IFluidProduct`, `FluidProduct` (Task 1); `FluidProductTypes.CODEC` / `STREAM_CODEC` (Task 2).
- Produces:
  - `ISqueezerRecipe.getFluidOutput()` now returns `IFluidProduct`.
  - `SqueezerRecipe(ResourceLocation id, int processingTime, List<Ingredient> resources, IFluidProduct fluidOutput, ItemStack remnants, float remnantsChance)` constructor.
  - `SqueezerRecipeBuilder.setFluidOutput(FluidStack)` and `SqueezerRecipeBuilder.setFluidOutput(IFluidProduct)`.

- [ ] **Step 1: Change the API return type in `ISqueezerRecipe`**

In `src/main/java/forestry/api/recipes/ISqueezerRecipe.java`, replace the `FluidStack` import and the `getFluidOutput` declaration:

Replace:
```java
import net.neoforged.neoforge.fluids.FluidStack;
```
with:
```java
import forestry.api.core.IFluidProduct;
```

Replace:
```java
	/**
	 * @return {@link FluidStack} representing the output of this recipe.
	 */
	FluidStack getFluidOutput();
```
with:
```java
	/**
	 * @return the {@link IFluidProduct} describing this recipe's fluid output. Call
	 * {@link IFluidProduct#createFluidStack()} for the representative/max stack (display + buffer reservation) or
	 * {@link IFluidProduct#createRandomFluidStack} for a single cycle's actual output.
	 */
	IFluidProduct getFluidOutput();
```

- [ ] **Step 2: Migrate `SqueezerRecipe` field, constructor, accessor, and codecs**

In `src/main/java/forestry/factory/recipes/SqueezerRecipe.java`:

Replace the two fluid imports:
```java
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
```
with:
```java
import forestry.api.core.IFluidProduct;
```
and add:
```java
import forestry.core.FluidProductTypes;
```

Replace the `CODEC` output-field line:
```java
		// A fluid ingredient, not a fixed FluidStack: a fluid tag resolves at runtime to whatever fluid a loaded
		// mod fills (e.g. some other mod's molten iron), so a recipe isn't tied to one mod's fluid.
		SizedFluidIngredient.FLAT_CODEC.fieldOf("output").forGetter(SqueezerRecipe::getFluidOutputIngredient),
```
with:
```java
		// A fluid product nested under "output" (dispatch codec): a plain FluidProduct serializes with no "type" key;
		// addon-provided dynamic products (tag resolution, random amount, chance) declare their own "type".
		FluidProductTypes.CODEC.fieldOf("output").forGetter(SqueezerRecipe::getFluidOutput),
```

Replace the field declaration:
```java
	private final SizedFluidIngredient fluidOutput;
```
with:
```java
	private final IFluidProduct fluidOutput;
```

Replace the constructor signature:
```java
	public SqueezerRecipe(ResourceLocation id, int processingTime, List<Ingredient> resources, SizedFluidIngredient fluidOutput, ItemStack remnants, float remnantsChance) {
```
with:
```java
	public SqueezerRecipe(ResourceLocation id, int processingTime, List<Ingredient> resources, IFluidProduct fluidOutput, ItemStack remnants, float remnantsChance) {
```

Replace the `getFluidOutput()` method and the `getFluidOutputIngredient()` accessor:
```java
	@Override
	public FluidStack getFluidOutput() {
		// Resolve the ingredient to a concrete fluid: take the first matching stack (its amount already applied).
		// An unfulfilled tag matches nothing, so we return EMPTY and TileSqueezer refuses the recipe.
		FluidStack[] fluids = this.fluidOutput.getFluids();
		return fluids.length == 0 ? FluidStack.EMPTY : fluids[0].copy();
	}

	/** The output as a fluid ingredient, for serialization. Consumers wanting the actual fluid use {@link #getFluidOutput()}. */
	public SizedFluidIngredient getFluidOutputIngredient() {
		return this.fluidOutput;
	}
```
with:
```java
	@Override
	public IFluidProduct getFluidOutput() {
		return this.fluidOutput;
	}
```

Replace the stream-codec output line in `fromNetwork`:
```java
			SizedFluidIngredient fluidOutput = SizedFluidIngredient.STREAM_CODEC.decode(buffer);
```
with:
```java
			IFluidProduct fluidOutput = FluidProductTypes.STREAM_CODEC.decode(buffer);
```

Replace the stream-codec output line in `toNetwork`:
```java
			SizedFluidIngredient.STREAM_CODEC.encode(buffer, recipe.fluidOutput);
```
with:
```java
			FluidProductTypes.STREAM_CODEC.encode(buffer, recipe.fluidOutput);
```

- [ ] **Step 3: Migrate `SqueezerRecipeBuilder`**

In `src/main/java/forestry/core/data/builder/SqueezerRecipeBuilder.java`:

Replace the two fluid imports:
```java
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
```
with:
```java
import net.neoforged.neoforge.fluids.FluidStack;
import forestry.api.core.FluidProduct;
import forestry.api.core.IFluidProduct;
```

Replace the field:
```java
	private SizedFluidIngredient fluidOutput;
```
with:
```java
	private IFluidProduct fluidOutput;
```

Replace the two setter overloads:
```java
	public SqueezerRecipeBuilder setFluidOutput(FluidStack fluidOutput) {
		this.fluidOutput = SizedFluidIngredient.of(fluidOutput);
		return this;
	}

	/** Sets a fluid ingredient output, e.g. a fluid tag resolved at runtime to whatever a loaded mod fills. */
	public SqueezerRecipeBuilder setFluidOutput(SizedFluidIngredient fluidOutput) {
		this.fluidOutput = fluidOutput;
		return this;
	}
```
with:
```java
	public SqueezerRecipeBuilder setFluidOutput(FluidStack fluidOutput) {
		this.fluidOutput = FluidProduct.of(fluidOutput);
		return this;
	}

	/** Sets a custom fluid product output, e.g. an addon's dynamic tag/random/chance product. */
	public SqueezerRecipeBuilder setFluidOutput(IFluidProduct fluidOutput) {
		this.fluidOutput = fluidOutput;
		return this;
	}
```

- [ ] **Step 4: Write the failing recipe round-trip GameTest**

Create `src/test/java/forestry/gametest/SqueezerRecipeCodecTest.java`:

```java
package forestry.gametest;

import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryConstants;
import forestry.api.core.FluidProduct;
import forestry.core.FluidProductTypes;
import forestry.factory.recipes.SqueezerRecipe;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SqueezerRecipeCodecTest {
	private static SqueezerRecipe sample() {
		return new SqueezerRecipe(
			ResourceLocation.fromNamespaceAndPath("forestry", "test_squeeze"),
			20,
			List.of(Ingredient.of(Items.APPLE)),
			FluidProduct.of(Fluids.WATER, 1000),
			new ItemStack(Items.STICK),
			0.5f
		);
	}

	@GameTest(template = "empty")
	public static void jsonRoundTrip(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		SqueezerRecipe.Serializer serializer = new SqueezerRecipe.Serializer();
		SqueezerRecipe original = sample();
		var json = serializer.codec().codec().encodeStart(jsonOps, original).getOrThrow();
		SqueezerRecipe decoded = serializer.codec().codec().parse(jsonOps, json).getOrThrow();
		var fluid = decoded.getFluidOutput().createFluidStack();
		if (fluid.getFluid() != Fluids.WATER || fluid.getAmount() != 1000) {
			helper.fail("Recipe JSON round-trip lost fluid output: " + json);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void streamRoundTrip(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		SqueezerRecipe.Serializer serializer = new SqueezerRecipe.Serializer();
		SqueezerRecipe original = sample();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		serializer.streamCodec().encode(buf, original);
		SqueezerRecipe decoded = serializer.streamCodec().decode(buf);
		var fluid = decoded.getFluidOutput().createFluidStack();
		if (fluid.getFluid() != Fluids.WATER || fluid.getAmount() != 1000) {
			helper.fail("Recipe stream round-trip lost fluid output");
			return;
		}
		helper.succeed();
	}
}
```

No production changes are needed to expose the codecs: `SqueezerRecipe.Serializer` is `public` with a public no-arg constructor and implements `RecipeSerializer<SqueezerRecipe>`, whose `codec()` (returns `MapCodec<SqueezerRecipe>`) and `streamCodec()` (returns `StreamCodec<RegistryFriendlyByteBuf, SqueezerRecipe>`) are public. `serializer.codec().codec()` turns the `MapCodec` into a `Codec` for `encodeStart`/`parse`.

- [ ] **Step 5: Compile and run the GameTests**

Run: `./gradlew runGameTestServer 2>&1 | tail -40`
Expected: build succeeds; `SqueezerRecipeCodecTest.jsonRoundTrip` and `streamRoundTrip` pass, and all earlier tests still pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/forestry/api/recipes/ISqueezerRecipe.java \
        src/main/java/forestry/factory/recipes/SqueezerRecipe.java \
        src/main/java/forestry/core/data/builder/SqueezerRecipeBuilder.java \
        src/test/java/forestry/gametest/SqueezerRecipeCodecTest.java
git commit -m "feat: migrate SqueezerRecipe fluid output to IFluidProduct

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Migrate consumers (`TileSqueezer`, JEI) and verify unchanged datagen

> **SCOPE NOTE (execution):** Steps 1–4 below (the `TileSqueezer` + JEI code changes) are executed as
> part of **Task 3** — they must land in the same commit as the `getFluidOutput()` API change for the
> mod to compile. When executing, Task 4 is **only Step 6 (regenerate + verify datagen) and committing
> `src/generated/resources`**. Steps 1–4 remain here as the authoritative spec for the consumer edits
> that Task 3 applies; verify they are present when reviewing.

**Files:**
- Modify: `src/main/java/forestry/factory/tiles/TileSqueezer.java` (call sites ~139-140, ~182, ~213-214)
- Modify: `src/main/java/forestry/factory/recipes/jei/squeezer/SqueezerRecipeCategory.java` (~71)

**Interfaces:**
- Consumes: `ISqueezerRecipe.getFluidOutput()` now returns `IFluidProduct` (Task 3); `IFluidProduct.createFluidStack()` / `createRandomFluidStack(RandomSource)`.
- Produces: no new API; behavior-preserving migration.

- [ ] **Step 1: Migrate `TileSqueezer` — actual per-cycle fill**

In `src/main/java/forestry/factory/tiles/TileSqueezer.java`, in `workCycle()` (around lines 139-140), replace:
```java
		FluidStack resultFluid = this.currentRecipe.getFluidOutput();
		this.productTank.fillInternal(resultFluid, IFluidHandler.FluidAction.EXECUTE);
```
with:
```java
		// createRandomFluidStack folds in any addon-defined per-cycle randomness (variable amount / chance of nothing).
		// It may return less than createFluidStack() (the amount reserved below) or EMPTY, in which case fill is a no-op.
		FluidStack resultFluid = this.currentRecipe.getFluidOutput().createRandomFluidStack(this.level.random);
		this.productTank.fillInternal(resultFluid, IFluidHandler.FluidAction.EXECUTE);
```

- [ ] **Step 2: Migrate `TileSqueezer` — fulfillability gate**

Around lines 180-182, replace these exact three lines:
```java
			// A dynamic output (e.g. a fluid tag no loaded mod fills) resolves to empty; refuse the recipe rather
			// than consuming the input for no fluid.
			if (matchingRecipe != null && matchingRecipe.getFluidOutput().isEmpty()) {
```
with:
```java
			// A dynamic output (e.g. an addon tag product no loaded mod fills) resolves to an empty representative
			// stack; refuse the recipe rather than consuming the input for no fluid.
			if (matchingRecipe != null && matchingRecipe.getFluidOutput().createFluidStack().isEmpty()) {
```

- [ ] **Step 3: Migrate `TileSqueezer` — buffer-space pre-check**

Around lines 213-214, replace:
```java
				FluidStack resultFluid = this.currentRecipe.getFluidOutput();
				canFill = this.productTank.fillInternal(resultFluid, IFluidHandler.FluidAction.SIMULATE) == resultFluid.getAmount();
```
with:
```java
				// Reserve worst-case space: createFluidStack() is the MAX amount this product can emit, so the machine
				// only starts if the tank can hold it, even if a given cycle later produces less.
				FluidStack resultFluid = this.currentRecipe.getFluidOutput().createFluidStack();
				canFill = this.productTank.fillInternal(resultFluid, IFluidHandler.FluidAction.SIMULATE) == resultFluid.getAmount();
```

- [ ] **Step 4: Migrate the JEI category**

In `src/main/java/forestry/factory/recipes/jei/squeezer/SqueezerRecipeCategory.java`, around line 71, the current line is:
```java
			.addFluidStack(recipe.getFluidOutput().getFluid(), recipe.getFluidOutput().getAmount());
```
`getFluidOutput()` now returns an `IFluidProduct`, so resolve its representative stack once into a local and pass its fluid + amount. Find the enclosing statement/method and introduce the local just before the builder call; the display line becomes:
```java
			FluidStack displayFluid = recipe.getFluidOutput().createFluidStack();
			// ... existing builder chain up to the fluid slot ...
			.addFluidStack(displayFluid.getFluid(), displayFluid.getAmount());
```
Add `import net.neoforged.neoforge.fluids.FluidStack;` if not already present. (Read the surrounding method first — the exact placement of the local depends on how the slot builder chain is structured; the requirement is a single `createFluidStack()` call feeding both `getFluid()` and `getAmount()`.)

> **EXECUTION NOTE:** Steps 1–4 above were already applied in Task 3 (the `getFluidOutput()` API change forced the consumers to migrate in the same commit for the mod to compile). Do NOT re-apply them. Task 4's actual work is the three steps below: regenerate datagen, restore the end-to-end recipe-loading test that Task 3 had to delete, and commit.

- [ ] **Step 5: Regenerate datagen and verify the new squeezer recipe JSON shape**

The output format **changes** here: the PR's in-flight `SizedFluidIngredient` shape (`"output": {"amount":100,"fluid":"forestry:honey"}`) becomes the nested `FluidProduct` shape (`"output": {"stack": {"amount":100,"id":"forestry:honey"}}`). This is expected and acceptable — the `SizedFluidIngredient` format was itself unreleased in-flight PR work, so there is no compatibility baseline to preserve. (Decision: the nested `{"stack": ...}` form was chosen deliberately over a flat hand-rolled codec, reusing `FluidStack.CODEC` per the codebase's `FabricatorRecipe`/`HygroregulatorRecipe` pattern.)

Run:
```bash
./gradlew runData 2>&1 | tail -20
git --no-pager diff --stat src/generated/resources
git --no-pager diff src/generated/resources/data/forestry/recipe/squeezer/honey_drop.json
```
Expected:
- `runData` succeeds (`BUILD SUCCESSFUL`).
- Every changed file is a squeezer **recipe** JSON under `data/forestry/recipe/squeezer/` (NOT the `container/` subfolder — those recipes have no fluid output and must be byte-identical). Container recipes, blockstates, and all non-squeezer recipes MUST be unchanged.
- Each changed recipe's `"output"` is now `{"stack": {"amount": <n>, "id": "<fluid id>"}}` with the **same fluid id and amount** as before (only the wrapper shape changed), and no `"type"` key. Spot-check `honey_drop.json` (amount 100, `forestry:honey`) and `lava.json` (amount 500, `minecraft:lava`) via the diff.
- If any NON-squeezer-output file changed, or a fluid id/amount value changed, stop and reconcile.

- [ ] **Step 6: Restore the end-to-end recipe-loading test (new API)**

Task 3 deleted `SqueezerFluidOutputTest`; three of its four tests were `SizedFluidIngredient`-specific and are correctly gone (superseded by `SqueezerRecipeCodecTest`), but its `squeezerRecipesResolveOutput` test was genuine end-to-end coverage — it loaded the real built-in squeezer recipes and asserted their fluid output resolves correctly. That coverage only works once datagen (Step 5) has regenerated the on-disk JSON to the parseable nested shape. Restore it, rewritten to the `IFluidProduct` API (`getFluidOutput().createFluidStack()` instead of the old `FluidStack`-returning `getFluidOutput()`).

Create `src/test/java/forestry/gametest/SqueezerRecipeLoadTest.java`:

```java
package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.recipes.ISqueezerRecipe;
import forestry.core.config.Constants;
import forestry.core.fluids.ForestryFluids;
import forestry.core.utils.RecipeUtils;
import forestry.factory.features.FactoryRecipeTypes;

/**
 * End-to-end oracle: the real built-in squeezer recipes load from datapack JSON (the new nested
 * {@code "output": {"stack": {...}}} shape) and resolve to the expected non-empty fluid output.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SqueezerRecipeLoadTest {
	@GameTest(template = "empty")
	public static void squeezerRecipesResolveOutput(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getRecipeManager();

		long count = RecipeUtils.getRecipes(manager, FactoryRecipeTypes.SQUEEZER).count();
		if (count == 0) {
			helper.fail("No squeezer recipes loaded");
			return;
		}
		boolean anyEmpty = RecipeUtils.getRecipes(manager, FactoryRecipeTypes.SQUEEZER)
			.anyMatch(recipe -> recipe.getFluidOutput().createFluidStack().isEmpty());
		if (anyEmpty) {
			helper.fail("A built-in squeezer recipe resolved to an empty fluid output");
			return;
		}

		ISqueezerRecipe honeyBlock = RecipeUtils.getRecipes(manager, FactoryRecipeTypes.SQUEEZER)
			.filter(recipe -> recipe.getId().equals(ForestryConstants.forestry("squeezer/honey_block")))
			.findFirst()
			.orElse(null);
		if (honeyBlock == null) {
			helper.fail("Missing built-in squeezer/honey_block recipe");
			return;
		}
		FluidStack expected = ForestryFluids.HONEY.getFluid(Constants.FLUID_PER_HONEY_DROP * 8);
		FluidStack actual = honeyBlock.getFluidOutput().createFluidStack();
		if (!(FluidStack.isSameFluidSameComponents(actual, expected) && actual.getAmount() == expected.getAmount())) {
			helper.fail("honey_block output changed: " + actual + ", expected " + expected);
			return;
		}

		helper.succeed();
	}
}
```

This is a faithful port of the deleted `squeezerRecipesResolveOutput` (same `RecipeUtils`/`FactoryRecipeTypes` plumbing, same `honey_block` = `HONEY x (FLUID_PER_HONEY_DROP * 8)` expectation), with only the output accessor changed to `.createFluidStack()`. If `ForestryFluids.HONEY.getFluid(int)` or `Constants.FLUID_PER_HONEY_DROP` no longer resolve, read the deleted original via `git show 017c87310^:src/test/java/forestry/gametest/SqueezerFluidOutputTest.java` and match whatever it referenced.

- [ ] **Step 7: Run the full GameTest suite and confirm the gate**

Run: `./gradlew runGameTestServer > /tmp/t4.log 2>&1; grep -iE "GAME TESTS COMPLETE|required tests failed|squeezerRecipesResolveOutput" /tmp/t4.log`
Expected (do NOT judge by exit code): the mod compiles, `squeezerRecipesResolveOutput` PASSES (proving the regenerated JSON loads and resolves), total test count is 68 (67 after Task 3 + 1 restored), and the ONLY failures are the two pre-existing unrelated ones (`everyallelevaluehasatranslation`, `defaultgenomesmatchbaseline`). If `squeezerRecipesResolveOutput` fails to load recipes, the datagen from Step 5 is wrong — reconcile before committing.

- [ ] **Step 8: Commit**

```bash
git add src/generated/resources \
        src/test/java/forestry/gametest/SqueezerRecipeLoadTest.java
git commit -m "feat: regenerate squeezer recipe JSON for IFluidProduct output

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Final verification

- [ ] Run the full GameTest suite one last time: `./gradlew runGameTestServer 2>&1 | tail -40`. Note: this task **always exits non-zero / BUILD FAILED** because of two pre-existing, unrelated failures on this branch (`everyallelevaluehasatranslation`, `defaultgenomesmatchbaseline`). Success = the new IFluidProduct/Squeezer tests pass and the failure list is EXACTLY those two names (grep the log — do not judge by exit code).
- [ ] Confirm `git grep -n "SizedFluidIngredient" src/main/java/forestry/factory src/main/java/forestry/core/data/builder/SqueezerRecipeBuilder.java` returns nothing (the ingredient-as-output stopgap is fully removed).
- [ ] Confirm `git grep -n "getFluidOutputIngredient"` returns nothing.
