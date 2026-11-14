# 1.6.4 source publication plan (read-only review)

Maintenance HEAD: `f6536f457e807c5ae64781a12dd6e45e6caa1dd8`. Do not stage the dirty maintenance worktree.

Overlay order:
1. `D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\src\main`
2. `D:\AI\candidates\useless-stretcher-assembly-lock-20261107-a\src\main`
3. `D:\AI\candidates\useless-stretcher-jei-localization-20261107-a\src\main`
4. `D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\src\main`
5. `D:\AI\candidates\useless-stretcher-upstream-2465-20261107-a\src\main`
6. `D:\AI\candidates\useless-stretcher-jei-sponge-fix-20261108-a\src\main`
7. `D:\AI\candidates\useless-stretcher-capsule-gravity-pause-shards-20261111-e\src\main`
8. `D:\AI\verification\stretcher-release-1.6.4-20261114\production\src\main`

Use P4 as the full historical src/main snapshot; every later layer is an overlay. E1 directly overlays sponge1 and already includes its complete C-derived production changes: do not apply C/D source trees over E1.

Final source inventory: 304 Java + 137 resources. Receipt source hash mismatches: 0. Every primary Java class exists in E1; extra generated resource only META-INF/MANIFEST.MF. E1 metadata is the only intentional resource difference after the formal layer.

Resource adds/deletes against maintenance HEAD:

- none

Resource replacements against maintenance HEAD:

- src/main/resources/META-INF/neoforge.mods.toml
- src/main/resources/assets/ae2/ae2guide/useless_stretcher/stretcher.md
- src/main/resources/assets/useless_stretcher/lang/en_us.json
- src/main/resources/assets/useless_stretcher/lang/zh_cn.json
- src/main/resources/assets/useless_stretcher/lang/zh_tw.json
- src/main/resources/useless_stretcher.mixins.json

Git/source archive procedure:

1. Read committed files from f6536f4 into a fresh publication directory or separate release branch/worktree. Never copy the maintenance working tree wholesale or use git add -A there.
2. Replace only src/main using the full P4 snapshot and ordered overlays in SOURCE-OVERLAY-PLAN.json. Preserve committed license/notices/wrapper files; separately record the P4 build.gradle and frozen compiler/packager provenance.
3. Set gradle.properties mod_version to 1.21.1-1.6.4 in the new release tree; metadata is already formal. Build script dependency versions are historical and need an explicit release build plan, not a claim that Gradle was rerun.
4. Include frozen layer source/compile/package receipts, E1 native smoke report, final gate inputs/compile log/package receipt, and a source manifest in the source archive. Keep tests separate from the production JAR.
5. Create a new source commit only from the reviewed release tree. Tag that source commit for 1.6.4; never tag old main/HEAD as if it already contained E1. Do not move the existing v1.6.3 tag.

Boundary: structural source closure and all listed overlay source hashes were checked. A clean full build reproducing every inherited P3/P4 class has not been run. A provenance source archive can accurately accompany the exact binary now; a GitHub tag must identify the actual assembled source commit, or be deferred until the source commit is prepared.

Detailed per-file origins, hashes, all new/changed/deleted Java/resources are in SOURCE-OVERLAY-PLAN.json.
