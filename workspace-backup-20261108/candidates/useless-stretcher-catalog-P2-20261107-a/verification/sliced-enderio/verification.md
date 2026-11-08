# P2 Ender IO Vat conversion slicing

Scope: only the cooperative `SlicedRecipeConversions` helper and its new tests. No game was launched. No P1, R runtime, production mod, config or world was modified. The unrelated Detector investigation was stopped without a source change or JAR output.

## Exact inputs

- Runtime base: `useless_mod-1.21.1-2.4.6.2.jar`, SHA256 `9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17`.
- R Ender IO: `enderio-8.2.12-beta.jar`, SHA256 `5FE098153DDDC2E7CCD9D38B0341DBC61F3072150958C916E5EF63F395E4EAB1`.
- Its nested EnderCore JAR was copied out only for standalone tests, SHA256 `B5B3A7B0D971DCD3D9DBCEC029A36D450EE72600182C565CB29264DE79171A7F`.
- The 2.4.6 compile base and R 2.4.6.2 have identical class bytes for VatFermentingRecipeAdapter, PairResult and EnderIOAdapterUtils. See `vat-abi-class-hashes.json`.

## Cursor contract and preserved rules

`tryCreate(adapter, holder, level)` performs reflection-only ABI probing and returns null for an unsupported or incompatible adapter; the caller retains the original conversion path. It invokes no native validation, tag, multiplier or conversion helper. A supported cursor starts at VALIDATE and must advance exactly once per owner catalog step using `advance(outputConsumer)`. It returns true when complete and emits at most one recipe per call. Dropping the cursor cancels unpublished work. It captures and checks its creating thread, and performs no worker dispatch.

The Vat cursor preserves native validation, first-tag outer/second-tag inner order, conditional reverse candidates, component fingerprint deduplication, exact component ingredients, variant IDs, floor/overflow handling, output component copying, mold and process time. Private native helpers perform those operations. Every candidate must validate before any output is emitted: a late invalid candidate still drops the complete conversion.

Native tag candidate `distinctStacks` used a nested `noneMatch` scan. The cursor visits one candidate per step with `ItemStack.hashItemAndComponents` and `isSameItemSameComponents`, retaining native first-occurrence ordering, copy semantics and count-insensitive equality. The all-pairs equal multiplier/amount decision is maintained incrementally. It does not perform a final full-list scan.

Single native calls, including `Ingredient.of(tag).getItems()`, component hashing, one `addPairResult`, class initialization and one output constructor, remain indivisible owner operations. This removes the explicit Cartesian and nested distinct loops from a single conversion callback; it does not establish a strict 2 ms upper bound or a measured R frame-time improvement.

## Verification

Run `run-vat-tests.ps1` from this folder or its candidate root. It writes each attempt into a new `runs/<UTC timestamp>` directory and runs only standalone JUnit, with a 512 MiB heap.

Final run after ABI fallback adjustment: `runs/20261106-220041000/compile.log` and `tests.log`: **8 tests found, 8 passed, 0 failed**. The preceding six conversion-equivalence tests also passed in `runs/20261106-220010447` after the factory change.

The native adapter and cursor both construct real AdvancedAlloyFurnaceRecipe objects. Every emitted tuple is compared in encounter order by ID and the complete native recipe Codec NBT, using strict `getOrThrow()` results. Tests cover:

- Overlapping tags, conditional reverse pairs, deduplication order, IDs, exact ingredients and preserved fluid components.
- Native one-recipe tag compression for identical multipliers and amounts.
- Equal rounded amounts with distinct double multipliers remaining separate variants.
- A late invalid multiplier dropping the entire recipe before publication.
- Count-insensitive component-aware candidate deduplication against native distinctStacks.
- Invalid sources, the native empty-tag display behavior, owner thread enforcement and no repeat emission after completion.
- A factory probe invoking no native conversion helpers, and an incompatible private ABI returning null for the native fallback instead of creating a later failing cursor.

The standalone fixture supplies real vanilla items, holder tags, fluids and codecs. It substitutes the unregistered VAT block holder with a vanilla iron block, temporarily aliases two Ender IO feature flags while initializing native block declarations, registers the real NeoForge component-ingredient codec, and applies controlled getModifier results equally to the native and sliced paths. The recipe conversion methods under comparison and their output constructors are not mocked. These fixtures verify conversion equivalence; they do not replace full mod registration or game regression.

## Remaining work

SoulBinding remains on the unmodified path. Its displayVials includes entity enumeration and an additional quadratic distinctFilledVials scan; hasDynamicOutput can scan and transform components again. Calling those monolithic helpers in a named stage would not remove the observed 296 ms indivisible work. No SoulBinding performance fix or runtime verification is claimed here.

Root owns catalog-stage integration, cancellation/generation tests, candidate packaging and the final R validation. This subtask does not produce or install a separate project JAR.
