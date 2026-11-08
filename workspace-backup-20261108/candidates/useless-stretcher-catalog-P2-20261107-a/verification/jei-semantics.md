# JEI 19.57 owner-batched registration audit

Date: 2026-11-07. Read-only examination of the actual R dependency; no Minecraft launch and no edits to R, P1, or the production checkout.

Examined JAR:

`D:\AI\sources\DollChronicle\build\mygenesis-native-R-20261107-a\mods\jei-1.21.1-neoforge-19.57.0.451.jar`

SHA256: `44FCDA225B26D14502BB1890512F4356DB0A2577A6C63757B92948A6B951180A`.

Evidence: `jei-inspection/*-javap.txt`, extracted classes and `jei-inspection/decompiled/` (CFR 0.152, checked against the corresponding bytecode). Decompiled files are diagnostic evidence, not production sources.

## ABI and thread ownership

The public manager is `mezz.jei.api.recipe.IRecipeManager`, not `mezz.jei.api.runtime.IRecipeManager`. Relevant erased descriptors:

```text
addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V
hideRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/Collection;)V
unhideRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/Collection;)V
hideRecipeCategory(Lmezz/jei/api/recipe/RecipeType;)V
unhideRecipeCategory(Lmezz/jei/api/recipe/RecipeType;)V
```

All five entry points in `mezz.jei.library.recipes.RecipeManager` call `ErrorUtil.assertMainThread()` before touching `RecipeManagerInternal`. Registration, visibility changes, capability-backed recipe layout extraction, and GUI operations must remain on the client owner thread. Asynchronous work may prepare immutable sort keys only; it cannot register recipes.

The API has no remove operation. It also has no public query that uniquely identifies whether `hiddenRecipeTypes` contains a category; a category lookup can be empty because there are no recipes, because ingredients/catalysts are hidden, or because user category settings hide it.

## Add is not atomic and provides no acknowledgement count

`RecipeManagerInternal.addRecipes` loops in supplied list order. For each recipe it calls `addRecipe`, stores focus links, and accumulates successful recipes locally. Only after the loop finishes does it append the accumulated list to `RecipeTypeData.recipes`. Each `addRecipe` updates each role's `RecipeMap` first, so index mutation can precede the final list append.

`addRecipe` catches `RuntimeException | LinkageError` from role-map updates and continues after logging a broken recipe; some earlier role maps may already contain that recipe. `isHandled`, debug preparation, and focus-link insertion have other uncaught failure paths. `IngredientSupplierHelper` also catches exceptions in `category.setRecipe` and may return a partially populated supplier. Consequently:

- A thrown batch can have unknown partial effects. Do not replay that batch automatically, even after epoch cancellation; stop this runtime's publication with an observable failure state.
- A normally returning batch means submitted, not proof that every recipe was fully indexed. Label progress/log counts accordingly and preserve JEI's error logs.
- Keep previously normal-returning batch records through job cancellation. Clearing them and submitting the same list again can duplicate internal storage.
- Single-recipe calls would narrow the uncertain portion but would not make JEI internally atomic.

## Identity, ordering, and retention

`RecipeTypeData.recipes` is an `ArrayList`; `addRecipes` is plain `addAll` with no deduplication. `hiddenRecipes` uses `Collections.newSetFromMap(new IdentityHashMap())`, and focus links use an `IdentityHashMap`. Hiding an equal-but-different recipe object does not hide the registered original. Preserve original submitted object references for later hide/unhide operations.

`PluginManager.getRecipes` ultimately uses `Stream.distinct()` with normal equals semantics. That may suppress repeated references in lookup results, but it does not undo duplicate list/index allocation. New wrapper instances without a content-based equals method can remain visibly duplicated.

Sequential batches append in the original list order. Preserve that order and use one in-flight owner batch. Do not sort each batch independently, consume unordered sets, or let two epochs interleave. For a new generation, a runtime-local content-key-to-canonical-object ledger can reuse the original object for identical recipes; a simple task-local identity set does not deduplicate regenerated wrappers.

Hidden recipes and old indices remain strongly retained for the lifetime of this manager. Hide is not deletion. Frequent distinct generations will grow storage; clear application-held ledgers on runtime replacement/unavailability and do not retain the old manager from static state. `RecipeManagerInternal.onRuntimeStopped` only removes the category-sorting listener; it does not clear its recipe collections. Actual JEI manager disposal is what releases them.

## Category hiding and the GUI barrier

`hideRecipeCategory` adds the type to a plain `HashSet` and invalidates `recipeCategoriesVisibleCache`; unhide removes it. It is not reference-counted or owned by the caller. Do not remove a hide known to predate this job. Public API alone cannot guarantee independent hide ownership if another mod hides the same category during the interval. If using an exact-version private read, guard that ABI and fail closed on mismatch; do not treat an empty visible lookup as proof that a hide belongs to this job. User category-sorting visibility is a separate condition and remains in force after unhide.

Category hiding correctly removes the type from fresh normal category/recipe lookups. It does not invalidate `RecipeGuiLogic`'s current state, navigation history, `IngredientLookupState.focusedRecipes`, or cached layout list. An already open screen can continue displaying old or partial layouts.

`RecipesGui.showRecipes(category, recipes, focuses)` creates `StaticFocusedRecipes` directly. `RecipeGuiLogic.filterVisibleRecipes` calls `RecipeManager.isRecipeVisible`, which tests recipe identity hiding and focus links, but does **not** test `hiddenRecipeTypes`. Thus hide-category alone does not block explicit recipe opens or bookmarks.

Required barrier behavior during incomplete/failed publication:

1. Handle a `RecipesGui` already open when work begins: close it through its normal close path before the next render/interaction, returning to its parent. `onClose` clears navigation history. Do not cancel arbitrary screens or leave the input layer active behind a loading screen.
2. Gate `show`, `showTypes`, and `showRecipes` early where possible, so expensive queries or state mutation do not run while blocked. A final guard at the private `RecipesGui.open()V` path covers all those opens and internal reopen; it must be cancellable at HEAD.
3. Guard against direct `Minecraft.setScreen(existingRecipesGui)` callers via the normal screen-opening lifecycle as needed. A private `open()` injection cannot intercept an external direct `setScreen`.
4. Keep the barrier across cancellation until a complete replacement is published, or keep a clear terminal failure presentation. Release it only on the current runtime/epoch after all batches complete and visibility is restored successfully.
5. Recreate a fresh lookup when reopening after completion; do not restore a cached partial state/history.

Do not hide each pending recipe before calling add: `addRecipe` checks `hiddenRecipes.contains(recipe)` and skips it entirely. Use category hiding plus the GUI barrier to cover the batch phase; use recipe hiding only for already-submitted stale objects.

## Recommended publication sequence

On the owner thread, validate current runtime and epoch; acquire this job's visibility gate and close any current recipe GUI; hide the target category if safe to own; hide prior-generation registered objects in bounded owner work; append new canonical objects in stable bounded batches; record each normal-returning batch immediately; publish readiness and unhide only the active complete object set; restore only this job's category hide; then release the screen barrier. At every boundary, cancellation must preserve the runtime ledger and prevent stale work from unhiding or registering into a replacement runtime.

Use measured per-batch duration with an adaptive count cap. A timer is a cooperative soft budget: one JEI call, one category callback, or one ingredient helper can still overrun the target. Record maximum batch duration and do not claim a hard per-tick cap. Exceptions should log runtime/epoch, phase, batch index/range, and attempted count without retrying ambiguous mutations.

## Focused regression cases

- All mutation calls occur on owner; off-owner attempts fail before registration.
- Ordered batches produce exactly the original recipe order, without re-adding completed batches after cancellation.
- A receiver that mutates then throws is never automatically replayed; earlier successful object identities remain in the ledger.
- New wrappers representing unchanged recipe content reuse canonical registered objects rather than appending duplicates.
- Recipe hide/unhide receives original object references; pre-hiding pending recipes is disallowed.
- Existing GUI, explicit `showRecipes`, bookmark open, internal reopen, direct screen-open lifecycle, and back/forward cannot expose partial recipes.
- Already-hidden categories are not accidentally revealed; runtime replacement does not unhide or retain its predecessor.
- Progress reaches ready only after the current epoch's complete successful publication; logs distinguish submitted counts from verified results.

This audit establishes bytecode semantics and design constraints. It does not substitute for the candidate's unit tests, Mixin application checks, or a later real-client run.
