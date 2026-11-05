# PBG 1.0.9 compatibility fixture and live-regression contract

Prepared on 2026-11-05. Preparation and source implementation alone are **not** a passed live test. The owning agent records compilation, server execution and restart results separately.

## Immutable evidence and dependency fixture

- Original stack evidence: `D:\AI\错误报告\20261105-watchdog-135746\2026-11-05-4.log`, lines 3212797–3212808. It includes the PBG 1.0.9 Extra centrifuge fluid-output pipeline inside the staff's ordinary repeated ticker.
- Original crash: `D:\AI\错误报告\20261105-watchdog-135746\crash-2026-11-05_13.57.46-server.txt`, line 383 explicitly identifies `neoforge@21.1.264`. The development runtime is 21.1.249; 21.1.219 was an earlier transcription error.
- Fixture: `build/fixture-watchdog-20261105`. Main JSON manifest and per-JAR TOML metadata are in that directory. `fixture-manifest.json` is also preserved beside this document. Additional MBD2/AE2LT dependencies have their own supplemental manifest.
- Three downloaded release artifacts were checked against their official SHA-256 release digests: PBG 1.0.9, Mekanism Extras 1.4.1 and JDTE 0.6.4.3. Release metadata is in `third-party/`.
- The `jdte-ae` 0.6.4.3 release is preserved in `third-party/` but deliberately absent from the fixture because the production crash lists JDTE core only.
- The main fixture deliberately excludes our own distributable JAR: ModDev supplies the development source set.

The exact Extra centrifuge registrations are:

```
productivebeesgenesis:absolute_extra_mek_centrifuge_factory
productivebeesgenesis:supreme_extra_mek_centrifuge_factory
productivebeesgenesis:cosmic_extra_mek_centrifuge_factory
productivebeesgenesis:infinite_extra_mek_centrifuge_factory
```

See the exact-tag `source-1.0.9/MEBlockRegistration.java` registration loop. The live tests use the first ID to cover the same actual block-entity implementation as the recorded Extra factory stack.

## Live suite

Call `PbgCentrifugeLiveChecks.tick(ServerLevel)` once per real server tick until it returns true.

Four actual Extra factories process four iron ores to four iron ingots using the installed native smelting recipe: native-only, single-target staff entry point, a one-block range, and both acceleration entry points during one real tick. These machines use PBG's per-tile smelting toggle, which defaults **off**, in addition to the globally enabled `mek_centrifuge.smelting_compat.smeltingCompatEnabled` setting. The native-only factory provides a real-time baseline.

A fifth actual Extra factory processes eight vanilla honeycombs through the installed Productive Bees recipe (`productivebees:centrifuge/honeycomb`): eight wax and 800mB honey without upgrades. Its own native AE node is connected to a real creative energy cell and its real AE grid storage service mounts an instrumented `MEStorage` backend. That backend accepts only 250mB initially; 550mB must stay in the machine. Every observed tick checks input/item and fluid conservation. After the native recipe finishes, the node and provider stay connected at the full 250mB capacity for 40 additional real ticks, with both acceleration routes continuing. Real `MODULATE` partial acceptance and zero acceptance must both have occurred and their counts are logged. Only then is the factory unloaded and restored with its native block-entity NBT, after which storage capacity opens to 800mB. Additional acceleration must not replay output. These checks use the production natural tick-start budget reset; they do not reset a server's global execution budget internally.

The backend is a test storage implementation, **not** an AE disk cell or a reproduction of the user's entire storage network. The suite reports measured maximum submission/flush time and insert calls per tick; it does not impose a hardware-independent latency guarantee.

## Separate two-process restart suite

Call `PbgCentrifugeLiveChecks.tickRestart(ServerLevel)` once per real server tick instead of the ordinary live suite. The method itself does not read a system property; the external watchdog regression entry point selects it. Use one new isolated world and launch it twice with the same path.

The first process creates an Extra factory at `(224,180,32)`, runs the same eight-honeycomb native recipe and retains 550mB locally, eight wax, no remaining honeycomb, and 250mB in the instrumented backend. It disables fluid export and returns true with `PBG PROCESS RESTART SEED READY`. The external entry point must then halt with normal world saving.

The second process loads the original block and chunk data. It must log `PBG PROCESS RESTART LOADED`. This branch does not place the factory again or insert any recipe input. It restores the backend's persisted balance and remounts it through the native grid storage service, raises capacity to 800mB, drains the remaining 550mB and observes 40 additional real ticks without item or fluid replay. Success logs `PBG PROCESS RESTART CHECKS PASSED`.

SavedData ID: `useless_stretcher_regression_pbg_restart`. Stored fields: `phase`, `amount`, `capacity`, `input_inserted`, `machine_pos`, `power_pos`.

| Phase | Meaning |
| --- | --- |
| 0 | Fresh isolated test world; create an empty fixture once |
| 1 | Original recipe in progress; do not recreate a partially processed fixture |
| 2 | Seed process complete; await an actual server-process restart |
| 3 | Restarted process restoring/draining the original balance |
| 4 | Verified final balance, 800mB external and zero local, eight wax |

This tests a normal saved shutdown and process restart. It is not a claim of atomic recovery from an arbitrary OS crash between chunk and SavedData persistence. Both optional regression classes are under `src/regression` and excluded from the distributed JAR.

## Actual execution evidence

The owner ran the integrated fixture and then a second server process against the same world. These are observed results, separate from fixture preparation:

- `server-integrated-second-console.log`, lines 95–109: AE2 19.2.18, JDTE 0.6.4.3, Mekanism Extras 1.4.1, NeoForge 21.1.249, PBG 1.0.9 were actually loaded.
- Line 398: the full connected backend remained at 250mB with 550mB local for 40 real ticks. Actual `MODULATE` counters were `partialAccept=1`, `zeroReject=4`, and maximum network insertion calls during one real tick was 1.
- Line 399: native factory NBT reload preserved eight wax, 250mB external and 550mB local honey.
- Line 401: live native/single/range/combined smelting completion times were `800/2/3/2` real ticks. Native honeycomb outputs remained eight wax and 800mB total honey. Observed maximum submission and flush time was `1.593ms` and `0.2676ms`, respectively, on this test machine.
- Line 402: the first process left the original factory at `(224,180,32)` with external 250mB, local 550mB, eight wax, and no unconsumed honeycomb for an actual process restart. Line 405 reports overall watchdog compatibility checks passed.
- `server-integrated-restart-console.log`, line 389: the next process loaded SavedData phase 2 with external 250mB, capacity 250mB, local 550mB, eight wax and zero honeycomb.
- Line 391: the original factory was reused without reseeding any recipe. Final external honey was 800mB, local honey zero, wax eight; 40 additional real ticks did not replay output. Line 395 reports overall watchdog compatibility checks passed.

`expected regression failure after accumulator mutation` and `expected regression failure after output mutation` in those logs are deliberately injected counting-fixture exception paths. They are not native PBG failures.

Earlier failed attempts are retained. In particular, `server-integrated-first-console.log` exposed a test-lifecycle error from manually calling AE2 `onReady` before its scheduled lifecycle; the two suites now wait for real world lifecycle ticks. The final successful runs used a new world for the corrected fixture. The earlier build success alone was never treated as a passed in-game test.
