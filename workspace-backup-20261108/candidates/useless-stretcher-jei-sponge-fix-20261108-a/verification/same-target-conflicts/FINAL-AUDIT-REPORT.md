# Minimal Sponge selector revision: independent audit

**PASS for the stated offline checks.** The new delivery JAR is:

`D:\AI\candidates\useless-stretcher-jei-sponge-fix-20261108-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970-sponge1.jar`

SHA-256: `3AEB59B7AAD8A2BF50D203BB220192FF45D90D0C50F80F6D8878514249F2F6EF`; size **12,817,555 bytes**.

Independent ZIP member comparison against frozen `9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7` found **2 changed, 680 byte-identical, 0 added, 0 removed** members. Duplicate member names are rejected by the audit reader.

1. `META-INF/neoforge.mods.toml`: only the declared version gains `-sponge1`.
2. `CatalogJeiBuildTimingMixin.class`: the method-name selector becomes exact three-argument and four-argument descriptors; `allow=1` is added. `require=1`, the `setRecipe` invocation target, class annotations, method bodies, method signatures, and other normalized class metadata remain identical. Handler source line numbers move from 20/21 to 23/24.

ASM canonical serialization with debug skipped and only method-level visible/invisible annotations removed produces identical before/after bytes, SHA-256 `E0FF0F8B8A4572F04773F15C645419A22579BB2566CA0CAAB41D4AAB2C5E788D`. The JAR contains the same complete member set as the baseline; no test harness class or other member was added. UUID lock/snapshot, HUD, localization, publication, and every other business class are among the 680 unchanged members.

The independent audit is `final-jar-audit/FINAL-JAR-AUDIT.json`, SHA-256 `89B0B9DCC134E8233546214DEC155D5E83C52874900DF467D95903867C17C995`. Both raw classes, normalized classes, TOMLs, and verbose `javap` evidence are retained alongside it.

The separate reviewer reports real Sponge offline transformation of the final JAR: both JEI variants passed all seven selected hooks; the JEI 19.70 combination with AE2Utility, Just Enough Mekanism Multiblocks, and NeoEcoAE passed 7/7 targets with ten applied Mixins at priority 1000, using official MixinExtras 0.5.5. The frozen JAR still reproduces the zero-target negative control. See `../sponge-merge/final-combined-1970/result.json`, `../sponge-merge/final-1970/result.json`, `../sponge-merge/final-1957/result.json`, and `../sponge-merge/final-negative-1970/result.json`; use the reviewer's final receipt for their hashes and complete methodology.

Those transformations use a reduced seven-own / three-external Mixin configuration and omit the original plugin metadata gates. They consume class resources without defining or initializing game target classes. They do **not** establish a successful full modpack launch, working UI, or runtime performance. The full 147/184 suites were not rerun. No Minecraft / server / ModLauncher was launched by this audit.

The all-mod conflict inventory is `REPORT.md`. It covers 416 primary and 180 nested JARs, identifies the exact three external `RecipesGui` Mixins, and found no other direct Mixin target for the remaining four requested classes. This metadata inventory does not exclude arbitrary dynamic ASM transformations.
