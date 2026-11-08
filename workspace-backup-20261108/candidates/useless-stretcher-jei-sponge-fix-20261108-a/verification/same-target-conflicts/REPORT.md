# Same-target Mixin / coremod inventory

This is an offline inventory of the exact runtime-c `client/mods` directory. It reads ZIP entries and parses class metadata with ASM. It does not load a mod class, execute a coremod, initialize Sponge, or launch Minecraft / ModLauncher. The runtime directory and original candidate were read only.

## Scope and counts

Source: `D:\AI\sources\DollChronicle\verification\mod-updates-20261107\runtime-c\client\mods`

- 416 primary JARs, 180 nested JARs (596 total).
- 173,141 classes and 216,321 JSON / JavaScript / TOML / config / manifest entries inspected.
- 511 Mixin configuration JSONs; 4,959 classes carrying `@Mixin` metadata.
- 8 Mixin classes target one of the five requested exact class names.
- 62 entries contain exact dotted or internal-name references to a requested class.
- 27 coremod metadata / JavaScript entries; none contain any of the five exact target names.
- Zero scan errors. The primary stretcher JAR matches frozen SHA-256 `9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7`.

Nested dependencies are included. Every class was checked for literal target references and `@Mixin`, so the exact-target inventory is not restricted to the classes listed in static Mixin configurations.

## Exact target inventory

All eight classes have effective class priority **1000** (the annotation omits an explicit priority); all associated JSONs omit configuration priority (default **1000**). All registrations are in the client list. No matching class declares `@Overwrite`.

| Target | Mixin | Owner |
|---|---|---|
| `IngredientSupplierHelper` | `com.sorrowmist.useless.stretcher.mixin.CatalogJeiBuildTimingMixin` | Stretcher |
| `RecipeManagerInternal` | `com.sorrowmist.useless.stretcher.mixin.CatalogJeiIndexTimingMixin` | Stretcher |
| `RecipeLookup` | `com.sorrowmist.useless.stretcher.mixin.CatalogJeiLookupMixin` | Stretcher |
| `com.sorrowmist.useless.compat.jei.JEIPlugin` | `com.sorrowmist.useless.stretcher.mixin.CatalogJeiTimingMixin` | Stretcher |
| `RecipesGui` | `com.sorrowmist.useless.stretcher.mixin.CatalogJeiGuiMixin` | Stretcher |
| `RecipesGui` | `com.lhy.ae2utility.mixin.RecipesGuiAccessor` | AE2Utility |
| `RecipesGui` | `giselle.jei_mekanism_multiblocks.client.mixin.jei.RecipesGuiMixin` | Just Enough Mekanism Multiblocks |
| `RecipesGui` | `cn.dancingsnow.neoecoae.mixins.client.jei.MultiblockRecipesGuiMixin` | NeoEcoAE |

No other direct `@Mixin` target match was found for `IngredientSupplierHelper`, `RecipeManagerInternal`, `RecipeLookup`, or the Useless Mod `JEIPlugin`.

## External RecipesGui hooks

### AE2Utility

- JAR: `ae2utility-1.8.0-mygenesis-compat.2.jar`
- SHA-256: `27BC943197FBCFEDF644499D93DD013E83F2C767FDEA3D85DE70211ACF8C4CD2`
- Configuration: `ae2utility.mixins.json`, package `com.lhy.ae2utility.mixin`, client entry `RecipesGuiAccessor`; `required=true`, `injectors.defaultRequire=1`.
- Mixin: `com.lhy.ae2utility.mixin.RecipesGuiAccessor`.
- `ae2utility$layouts()Lmezz/jei/gui/recipes/RecipeGuiLayouts;` uses `@Accessor("layouts")`.
- `ae2utility$recipeLogic()Lmezz/jei/gui/recipes/IRecipeGuiLogic;` uses `@Accessor("logic")`.
- No `Inject`, `Redirect`, `Overwrite`, or MixinExtras injection on the requested target.

### Just Enough Mekanism Multiblocks

- JAR: `JustEnoughMekanismMultiblocks-1.21.1-7.23.jar`
- SHA-256: `69BA91372EED3AD69CAFCC6BD8E2AFEC85E2A009A13E4E4814C4F474812985D6`
- Configuration: `jei_mekanism_multiblocks.mixin.client.json`, package `giselle.jei_mekanism_multiblocks.client.mixin`, client entry `jei.RecipesGuiMixin`; `required=true`, `injectors.defaultRequire=0`.
- Mixin: `giselle.jei_mekanism_multiblocks.client.mixin.jei.RecipesGuiMixin`.
- Implements `giselle.jei_mekanism_multiblocks.client.IRecipeLogicStateListener` and adds public `jei_mekanism_multiblocks$onStateChange()V`.
- `@Shadow private IRecipeGuiLogic logic`; `@Unique private boolean jei_mekanism_multiblocks$onStateChangeWasCaught`.
- The new listener method uses `RecipeGuiLogicAccessor.getState()` and `invokeSetState(state,false)`, catches `Throwable`, and latches a local failure boolean.
- No `Inject`, `Redirect`, or `Overwrite` annotation. The merged method and interface are still part of the same-target combination to verify.

### NeoEcoAE

- JAR: `neoecoae-21.2.15.jar`
- SHA-256: `64248CBFBED64C9A552BD67BF13BF8091A408ADF608301C9A7122F744C4D9082`
- Configuration: `neoecoae.mixins.json`, package `cn.dancingsnow.neoecoae.mixins`, client entry `client.jei.MultiblockRecipesGuiMixin`; `required=true`, `injectors.defaultRequire=1`, MixinExtras minimum `0.5.3`.
- Mixin: `cn.dancingsnow.neoecoae.mixins.client.jei.MultiblockRecipesGuiMixin`.
- `neoecoae$selectedCategory(IRecipeCategory)IRecipeCategory`: `@ModifyExpressionValue(method="updateLayout", at=@At(value="INVOKE", target="Lmezz/jei/gui/recipes/IRecipeGuiLogic;getSelectedRecipeCategory()Lmezz/jei/api/recipe/category/IRecipeCategory;"))`.
- `neoecoae$compactHeight(I)I`: `@ModifyArg(method={"updateSize","init","m_7856_"}, at=@At(value="INVOKE", target="Lmezz/jei/gui/recipes/RecipeGuiSizing;calculateInitialSize(IZI)Lmezz/jei/gui/recipes/RecipeGuiSizing$Size;"), index=2, require=0)`.
- `neoecoae$resizeOnCategoryChange(CallbackInfo)V`: `@Inject(method="updateLayout", at=@At("RETURN"))`, default require inherited as 1.
- No explicit ordinals, cancellable flags, or overwrite annotations.

## Stretcher hooks in the frozen runtime JAR

- `CatalogJeiBuildTimingMixin`: `@Redirect(method="getIngredientSupplier", @At(INVOKE IRecipeCategory.setRecipe(IRecipeLayoutBuilder,Object,IFocusGroup)V), require=1)`; selector is a bare method name in the frozen class. This inventory records the bytes; it does not explain the already-reported Sponge zero-target failure.
- `CatalogJeiIndexTimingMixin`: `@Redirect(method="addRecipe", @At(INVOKE RecipeMap.addRecipe(RecipeType,Object,IIngredientSupplier)V), require=1)`.
- `CatalogJeiLookupMixin`: `@Inject(method="get", @At("HEAD"), cancellable=true, require=1)`.
- `CatalogJeiGuiMixin`: `@Inject(method={"show","showTypes","showRecipes","back","forward","open","updateLayout"}, @At("HEAD"), cancellable=true, require=1)`.
- `CatalogJeiTimingMixin`: `@Inject registerRecipes HEAD`; `@Redirect registerRecipes -> AlloyFurnaceRecipeCatalog.isReady(Level)Z`; `@Inject onRuntimeAvailable RETURN`; `@Inject onRuntimeUnavailable RETURN`; `@Inject refreshAlloyFurnaceRecipes HEAD, cancellable=true`; all require 1.
- Associated configuration: `useless_stretcher.mixins.json`, `required=true`, plugin `com.sorrowmist.useless.stretcher.mixin.UselessStretcherMixinPlugin`.

## Coremods and non-Mixin references

All `META-INF/coremods.json` files and `.js` entries were recorded in `coremod-inventory.json`. All registered scripts are present in the scan. Apothic Enchanting has a JEI coremod, but its declared JEI target is `mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker$EnchantmentData`, outside the five requested classes.

AE2LT, AE2Utility, and Data Energistics contain ordinary client classes referencing `RecipesGui`; these are in `target-references.json`. They do not carry `@Mixin` metadata targeting the requested classes. The report does not classify an ordinary reference as an injection conflict.

## Evidence and limits

- `SUMMARY.json`: counts and exact scan method.
- `jar-inventory.json`: all primary/nested JAR names, sizes, hashes, and primary absolute paths.
- `mixin-configurations.json`: complete discovered configurations and source text.
- `all-mixin-targets.json`: all classes with `@Mixin`, exact target metadata, priorities.
- `same-target-mixins.json`: eight matches, full annotations, method descriptors, field annotations, registration contexts, extracted-class hashes.
- `matched-classes/`: exact matched class bytes extracted for independent Sponge/ASM checks.
- `*.javap.txt`: verbose bytecode/annotation evidence for all eight matched classes.
- `target-references.json`: all literal dotted/internal-name hits.
- `coremod-inventory.json`: complete coremod metadata and JavaScript content.
- `scan-errors.json`: empty array.
- `SameTargetScan.java` and `scan-console.log`: reproducible scanner and execution output.

This is direct metadata and literal-reference evidence. It does not execute dynamically generated Mixin registrations, plugins, arbitrary ASM transformers, or runtime code that computes class names. It cannot prove that no other transformer affects the target. Same priority and same target are ordering candidates, not evidence of a conflict by themselves. Actual Sponge transformation is the separate reviewer’s validation.
