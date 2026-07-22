# IFluidProduct — data-driven Squeezer fluid output

**Date:** 2026-07-18
**Branch:** `imakebadchoices-pr/squeezer-fluid-output-dispatch`
**Status:** Approved for implementation

## Problem

The Squeezer's recipe output is a fixed `FluidStack`. This ties every recipe to one
concrete fluid, so an addon that wants a mod-agnostic output (e.g. "molten bronze",
provided by any of several mods via a fluid tag) must ship a separate recipe per mod's
fluid.

The in-flight PR generalized the output to a NeoForge `SizedFluidIngredient` and resolved
it at runtime via `getFluids()[0].copy()`. That is the wrong abstraction:

- **Ingredients are matchers, not producers.** Using one as an output means the "real"
  output is whichever fluid lands first in the tag's resolved list — an order that is not
  a stable contract (it depends on registration/tag load order and how many mods filled
  the tag). "Produce molten bronze" silently becomes "produce whatever sits at index 0."
- **It cannot express variable amounts or probabilistic output.** A `SizedFluidIngredient`
  has nowhere to put "1000–3000 mB" or "70% of the time".

## Goal

Introduce a fluid analog of the existing `forestry.api.core.IProduct` system — a small,
dispatch-codec-backed product interface — so that:

- Forestry ships one simple reference implementation (a fixed fluid output), preserving
  today's behavior byte-for-byte.
- Addon authors can implement their own `IFluidProduct` types (tag resolution, random
  amounts, probabilistic output) and register them, with **no Forestry code changes**.

This mirrors how `IProduct` / `ProductType` / `ProductTypes` already work for bee item
drops (Forestry ships `Product` + `FireworkProduct`; addons may register more).

## Non-goals

- Forestry does **not** implement tag-resolution, random-amount, or chance-based fluid
  products. Those are left entirely to addon authors as the extension point.
- `ISqueezerContainerRecipe` has no fluid output and is untouched (beyond the
  Forge→NeoForge import churn already present in the PR).
- No `chance()` or `fluid()` accessor on the interface (see Design decisions).

## Design

### New API — package `forestry.api.core` (beside `IProduct`)

**`IFluidProduct`** — the interface:

```java
public interface IFluidProduct {
    /**
     * The representative stack for this product: used for display identity (fluids may
     * carry data components, e.g. liquid potion effects) and as the MAXIMAL amount this
     * product can produce. The Squeezer reserves space for this amount before it will
     * begin processing. Returning FluidStack.EMPTY signals the product is not currently
     * producible (e.g. an addon tag product whose tag no loaded mod fills), and the
     * machine refuses the recipe.
     */
    FluidStack createFluidStack();

    /**
     * The actual fluid produced in a single work cycle. May be a smaller amount than
     * createFluidStack(), or FluidStack.EMPTY if the product's own probability roll fails.
     * Any per-cycle randomness (variable amount, chance of nothing) is folded in here; the
     * machine is not responsible for rolling it. Defaults to createFluidStack().
     */
    default FluidStack createRandomFluidStack(RandomSource random) {
        return createFluidStack();
    }

    /** The type of this product, used to (de)serialize it via FluidProductTypes. */
    FluidProductType<?> type();
}
```

**`FluidProductType<T extends IFluidProduct>`** — literal mirror of `ProductType`:

```java
public record FluidProductType<T extends IFluidProduct>(
    MapCodec<T> codec,
    StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
```

**`FluidProduct`** — the reference implementation:

```java
public record FluidProduct(FluidStack stack) implements IFluidProduct {
    MapCodec<FluidProduct>  = FluidStack.CODEC.fieldOf("stack").xmap(...)
    StreamCodec<...>        built on FluidStack.STREAM_CODEC
    FluidProductType<FluidProduct> TYPE   // the dispatch default

    createFluidStack()             -> stack.copy()
    createRandomFluidStack(random) -> stack.copy()   // no randomness in the reference impl
    type()                         -> TYPE

    static FluidProduct of(FluidStack stack)
    static FluidProduct of(Fluid fluid, int amount)
}
```

This repo's pinned NeoForge (21.1.230) has no `MapCodec<FluidStack>` — only `FluidStack.CODEC`
(a `Codec<FluidStack>` serializing as `{"amount": N, "id": "<fluid id>"}`). So the MAP_CODEC
wraps it under a single `"stack"` field, matching the codebase's `FabricatorRecipe` /
`HygroregulatorRecipe` pattern. A plain product therefore serializes **nested**:
`{"stack": {"amount": 1000, "id": "modid:molten_bronze"}}` (components come for free, no bespoke
fluid parsing). The nested form was chosen deliberately over a flat hand-rolled codec.

### Dispatch registry — `forestry.core.FluidProductTypes`

An exact mirror of `forestry.core.genetics.ProductTypes`, including the optional-`type`-key
trick:

- `register(ResourceLocation, FluidProductType<?>)` / `byId(...)` with the two backing maps.
- `MAP_CODEC` / `CODEC`: when the `type` key is absent on decode, resolve to
  `FluidProduct.TYPE`; when encoding a `FluidProduct`, omit the `type` key entirely. Any
  other type reads/writes its `type` key.
- `STREAM_CODEC`: id-prefixed dispatch, same shape as `ProductTypes.STREAM_CODEC`.
- `registerBuiltins()` (idempotent, synchronized): registers
  `ForestryConstants.forestry("fluid") -> FluidProduct.TYPE`.

**Call site:** invoked from `PluginManager` alongside `ProductTypes.registerBuiltins()`
(currently `PluginManager.java:165`), so it runs before any datapack parse or network sync.
Add an idempotent safety-net call if the genetics types have one in a comparable reload
path; the squeezer recipe path only requires the `PluginManager` call.

### Recipe changes

**`forestry.api.recipes.ISqueezerRecipe`**

```java
- FluidStack getFluidOutput();
+ IFluidProduct getFluidOutput();
```

**`forestry.factory.recipes.SqueezerRecipe`**

- Field `SizedFluidIngredient fluidOutput` → `IFluidProduct fluidOutput`.
- Delete the `getFluids()[0].copy()` resolution in `getFluidOutput()` and the
  `getFluidOutputIngredient()` accessor.
- `CODEC`: the `"output"` field uses `FluidProductTypes.CODEC.fieldOf("output")` (dispatch,
  default type omits `type`), nesting the product under `"output"`.
- `STREAM_CODEC`: uses `FluidProductTypes.STREAM_CODEC` for the output field.
- Constructor and `Preconditions` updated to take/validate `IFluidProduct`.

**`forestry.core.data.builder.SqueezerRecipeBuilder`**

- `setFluidOutput(FluidStack)` retained, now wraps into `FluidProduct.of(fluidStack)` — so
  all existing datagen callers compile and emit identical JSON.
- Add `setFluidOutput(IFluidProduct)` for dynamic/addon outputs.
- Drop the `setFluidOutput(SizedFluidIngredient)` overload the PR introduced.
- Field type `SizedFluidIngredient fluidOutput` → `IFluidProduct fluidOutput`.

### Consumer changes

**`forestry.factory.tiles.TileSqueezer`**

- Fulfillability gate (`~:182`): `matchingRecipe.getFluidOutput().createFluidStack().isEmpty()`.
- Buffer-space pre-check (`~:213-214`): simulate-fill using `createFluidStack()` (the
  maximal amount), unchanged logic otherwise.
- `workCycle` (`~:139-140`): fill the tank with
  `this.currentRecipe.getFluidOutput().createRandomFluidStack(this.level.random)`. If it
  returns EMPTY, `fillInternal` is a no-op — inputs are still consumed. (For all Forestry
  recipes, `FluidProduct` returns the full stack, so behavior is unchanged.)

**`forestry.factory.recipes.jei.squeezer.SqueezerRecipeCategory`**

- `~:71`: render from `recipe.getFluidOutput().createFluidStack()` (its `.getFluid()` /
  `.getAmount()`), i.e. the representative/max stack.

## Design decisions

- **Interface trimmed to three methods.** No `chance()` and no `fluid()`:
  - `chance()` was dropped because probability is per-cycle behavior an addon folds into
    `createRandomFluidStack`; exposing it separately would invite double-rolling and give
    the machine a responsibility that belongs to the product.
  - `fluid()` (a bare `Fluid`) was dropped because `createFluidStack()` already serves
    display identity and, unlike a bare `Fluid`, preserves data components (liquid potion
    effects).
- **No `ITEM_ONLY_STRATEGY` analog.** `IProduct`'s hashing strategy exists only to dedup
  shared products between parent bees during hybridization. The Squeezer has no analog.
- **`FluidProduct` wraps `FluidStack` rather than mirroring `Product`'s field layout.**
  `FluidStack` already bundles fluid + amount + components, so `record FluidProduct(FluidStack)`
  avoids re-implementing that and gets NeoForge's `FluidStack.CODEC` / `STREAM_CODEC` for free.
- **`createFluidStack()` doubles as the "not producible" sentinel.** Returning
  `FluidStack.EMPTY` reuses the tile's existing refuse-the-recipe path with no new method.

## Behavioral compatibility

- Every existing Forestry recipe uses a fixed `FluidProduct`, whose `createFluidStack` and
  `createRandomFluidStack` both return the full stack. Machine behavior is identical to
  today.
- Squeezer recipe JSON **shape changes** at the `"output"` field: from the PR's in-flight
  `SizedFluidIngredient` form `{"amount": N, "fluid": "<id>"}` to the nested `FluidProduct`
  form `{"stack": {"amount": N, "id": "<id>"}}` (no `type` key — the dispatch default). Fluid
  ids and amounts are preserved. This is not a compatibility break: the `SizedFluidIngredient`
  form was unreleased in-flight PR work, so no datapack depends on it.

## Testing

- **Codec round-trip:** `FluidProduct` through `FluidProductTypes` — JSON and network, both
  directions — asserting the default type omits/absorbs the `type` key.
- **Recipe serialization:** a `SqueezerRecipe` with a `FluidProduct` output round-trips
  through its `CODEC` and `STREAM_CODEC`.
- **Reference-impl sanity:** `createFluidStack()` and `createRandomFluidStack(random)` both
  return the wrapped stack (independent copies).

## Out of scope / future work

- Addon-provided `IFluidProduct` types (tag resolution, random amount, chance). These are
  the extension point this design exists to enable but are not built here.
- Applying `IFluidProduct` to other fluid-producing machines. If desired later, the
  interface and registry are already machine-agnostic.
