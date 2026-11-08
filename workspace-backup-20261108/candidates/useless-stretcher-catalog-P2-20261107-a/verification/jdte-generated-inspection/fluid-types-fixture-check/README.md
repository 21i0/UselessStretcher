# Offline native-fluid fixture repair

The first P2 full run compiled successfully and ran 84 tests, with only three `GeneratedRecipeSlicesTest` methods failing because standalone vanilla bootstrap had not registered NeoForge's `neoforge:fluid_type` registry. The native baseline failed at the same `CommonHooks.getVanillaFluidType(minecraft:water)` call.

The test-only `BeforeAll` now attaches the actual `NeoForgeRegistries.FLUID_TYPES` registry to the standalone root and invokes NeoForge's own `VANILLA_FLUID_TYPES` `DeferredRegister.addEntries(RegisterEvent)` callback. This uses NeoForge's original suppliers for empty, water, and lava. Assertions verify that the real source/flowing fluids resolve to the expected bound native holders. No `CommonHooks`, JDTE classification, native recipe helper, or cursor logic is mocked for this repair.

`compile.args` recompiles only `GeneratedRecipeSlicesTest` into this evidence directory using the first full build's exact classpath. `run.args` places this repaired test first and runs the complete first-build test class set through the unchanged `OfflineTestLauncher`; original first-run logs are preserved in their original location.

Result in `unit-tests.log`: **84 tests started, 84 successful, 0 failed; 11 containers successful**. Native baseline and sliced implementation therefore pass together under the same real fluid-type environment. The container-capability fixture still deliberately controls the capability offered by its synthetic item, while native recipe construction, IDs, stack codecs, order, and fluid classification remain real. This is not a Minecraft client/server run.
