# 2026-11-08 workspace backup — not a release

This branch is an off-machine backup of the Useless Stretcher 1.21.1 workspace. It is not a production release, does not update `main`, and is not a CurseForge upload.

- Last published NeoForge release: **1.6.3**, GitHub `v1.6.3`, CurseForge file `9451528`.
- Root source tree: current **1.6.4 development workspace**, based on `f6536f457e807c5ae64781a12dd6e45e6caa1dd8`, including uncommitted assembly snapshot work and its authored tests.
- `legacy-main-1.6.2/tracked-changes.patch`: tracked differences preserved from the separate old main worktree. Original worktrees and their uncommitted content remain in place.
- `candidates/`: P/P2/P3/P4 source snapshots, later source overlays, authored build/audit/test harnesses and concise receipts. P4 is the full catalog source snapshot. Later assembly-lock, localization, HUD, upstream-2465 and Sponge overlays must not be mistaken for standalone complete source trees. Exact input paths and hashes are recorded in the copied input manifest and original receipts.
- `experimental-artifacts/`: this project's own final `sponge1` candidate and its fixed `9556…` build input/negative control. They are backup assets. The negative control has a known JEI injection error and is not a usable rollback release.

Final experimental JAR SHA256: `3aeb59b7aad8a2bf50d203bb220192ff45d90d0c50f80f6d8878514249f2f6ef`.

The final candidate passed the recorded Sponge merge checks and the MyGenesis runtime-e JEI smoke checks: 42,213 catalog entries published, GUI and sampled coil R/U pages visible, normal save and exit 0. GUI operations were performed by the source integration thread; this closeout verified receipt/evidence hashes, not a new game run. See `candidates/useless-stretcher-jei-sponge-fix-20261108-a/verification/runtime-e-20261108/`.

## Release blockers and upgrade constraints

The assembly snapshot changes SavedData to format 3. Old 1.6.3 cannot safely read this format; rollback requires a full pre-upgrade save backup. Legacy installed stretchers can only capture the source library still available at migration time, not reconstruct a historical install-time state.

The current runtime-e acceptance does not cover actual assembly slot/shift/drag/automation interactions, isolation after source library edits, format-3 migration and same-save process restart, or valid Utility pattern upload to a real provider. These storage-sensitive checks are still required before promoting the candidate. The long candidate version identifier has not been converted into an accepted formal release. No new Minecraft process was started during this cleanup.

The metadata requires Useless Mod >=1.21.1-2.4.6 and AE2 >=19.2; GuideME and Curios are optional. The performance mixins have narrower exact-version gates documented in the receipts; a broad dependency range is not evidence of full future-version compatibility. A roughly 128 ms maximum JEI publication frame slice was observed, so this backup makes no global smooth-performance claim.

Downloaded third-party libraries, extracted third-party sources, launch accounts/tokens, private saves, full game runtimes and raw game logs are intentionally excluded from new backup content. Older tracked dependency objects may already exist in the parent commit; this backup does not add new copies of those libraries.

Local originals of source, user assets, saves, concise verification and the latest valid JAR are retained. Cleanup is limited to individually inventoried obsolete generated dependencies, caches and old project JAR copies after remote backup confirmation.
