# 1.6.4 production source provenance

This tree starts from the committed Git tree f6536f457e807c5ae64781a12dd6e45e6caa1dd8,
never the maintenance working directory. The reviewed source overlays contain
304 Java files and 137 main resources. SOURCE-OVERLAY-PLAN.json records every origin
and SHA256. E1 directly overlays sponge1; no additional C/D overlay is required.
The formal overlay changes only two exact-version gates and the mod metadata version.
gradle.properties also declares 1.21.1-1.6.4.

The exact delivered binary uses the frozen inherited production entries and two
root-compiled gate classes; see production-artifact-receipt.json. The production
packager changes exactly two class entries plus META-INF/neoforge.mods.toml and
retains all 786 other E1 entries. Compilation and runtime execution are performed
by the root controller; these preparation scripts never start Java.

The original committed build.gradle and vendor binaries remain unchanged. Its
default libs/compile selection is historical Useless Mod 2.4.6 / AE2 19.2.17,
without an explicit JEI dependency; that default is not a validated clean build
of this release. NativeAdapterGuards needs the newer official base API. The exact
frozen compiler/package plans provide provenance; a clean full Gradle rebuild is
pending an explicit dependency configuration. No newer vendor JAR is bundled here.

All new evidence here is source or small UTF-8 text, not classes, mod JARs or logs.
Old tracked vendor JARs, assets and historical evidence elsewhere in this tree are
preserved from the original commit. Existing working-directory changes, unknown
files, player saves and test instances are never copied.

E1-NATIVE-SMOKE-REPORT.md describes only its original single-room server smoke.
The final actual staff smoke passed 18 ordinary checks in runtime-client-d, with
a separately accepted artificial hand-bound identity observation. See
RUNTIME-D-REVIEW.md, staff-compat-result.json and release-approval.json. This is
an integrated-client scope, not remote multiplayer, a zero-tick race guarantee
or full-pack acceptance. Refer to the final publication receipt for posted identity.

No commit, tag, index, branch or remote was changed by assembly. A release tag must
point at an assembled source commit, not at the historical parent commit.
