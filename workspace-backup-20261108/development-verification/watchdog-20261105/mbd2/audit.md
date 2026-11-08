# MBD2 / LDLib2 / AE2LT thread compatibility audit

## Evidence

The preserved production log `D:\AI\错误报告\20261105-watchdog-135746\2026-11-05-4.log`, lines 3643–3666, records this independent repeating failure:

`LDLib Async Thread-0 -> AsyncThreadData.searchingTask -> ISyncMangedHolder.asyncTick/passivelySync/sync -> Ref.update -> ReadOnlyRef.readOnlyUpdate -> INBTSerializableReadOnlyAccessor -> MBD2 SerializableManagedGridNode.serializeNBT -> AE2 ManagedGridNode/GridNode.saveToNBT -> AE2LT WirelessEnergyGridService.saveNodeData -> WirelessEnergyLedger.get -> IllegalStateException("Server thread required")`.

No Stretcher acceleration invocation is present in this execution chain. It is distinct from the PBG centrifuge/AE fluid acceleration watchdog path. Capsule class-transformation annotations in a crash report do not establish an executing capsule call.

Production versions: MBD2 21.1.2, LDLib2 2.2.44, AE2 19.2.18, AE2LT 2.1.3, Thunderbolt 2.0.3, NeoForge **21.1.264**. NeoForge is confirmed by the production log line 7 and crash line 383. An earlier audit note carried an incorrect 21.1.219 value; it has been corrected. The development fixture runs NeoForge 21.1.249, so this remains a declared environment difference.

## Implementation contract

- Apply the optional mixin only when all four relevant mods are present and LDLib2 is exactly 2.2.44. Unknown LDLib2 versions log a warning and are not patched.
- Wrap the single `IAsyncLogic.asyncTick(J)V` dispatch call in `AsyncThreadData.searchingTask`; only real MBD2 block entities inheriting `ISyncMangedHolder`'s standard default callback are handed off. Custom callback overrides retain their original path.
- The exact 2.2.44 callback does not use its `period` argument. It performs server-safety/useAsyncThread/isAsyncValid checks and requests passive sync. Coalescing this callback does not drop a custom simulation period or counter.
- Workers inspect class metadata and the dispatcher's immutable level reference only; no world block lookup, managed field serialization, AE node or SavedData access occurs there.
- One outstanding request per holder identity; main-thread FIFO drain, at most 128 scanned tasks / 2 ms per tick. A single serialization cannot be preempted by this budget.
- Main-thread execution rechecks that the same live block entity remains at the same position in an already loaded chunk. Unloaded or replaced machines are discarded, not revived.
- A failure retains pending work, rotates it behind peers, delays retries by at least 20 ticks, and logs the first failure and at most every 1200 ticks afterwards. A retry marks all sync fields dirty before `sync(true)`: LDLib clears fields incrementally and even `sync(true)` is gated by `hasDirtySyncFields()`, so retrying only a passive sync or only `sync(true)` can otherwise lose the partial snapshot.
- Shutdown and new queue publication share a short lifecycle lock. Weak stopped-server keys prevent a late worker from recreating a queue after `ServerStoppedEvent` without retaining every stopped server permanently.
- AE2LT's thread guard and complete claim/budget serialization remain intact.

## Verification scope

`mbd2-queue-unit.log` records actual plain-Java queue regressions, including 5000 merged requests, execution thread/current state, identity keys, FIFO fairness, retry delay/log limits, complete retry after partial dirty clearing, unload discard, task/time budgets and cleanup.

`Mbd2LiveSyncChecks` is an opt-in real-server regression fixture (`useless_stretcher.watchdog_mbd2=true`). It registers a genuine MBD2 machine definition with the genuine AE interface trait and uses the real transformed LDLib dispatcher. It checks 5000 worker dispatches merge into one request without changing the managed-field snapshot on the worker, then checks native AE2LT claim/budget NBT on the main thread. Its SavedData allows a second process on the same save to verify the same claim, 37 interface diamonds, 431 confirmed FE and 7 uncertain FE survive restart without duplication. **Its presence in source is not a test pass; parent-run server logs are required.**

Official fixture hashes, source URLs and bytecode are preserved in this directory. Published version numbers match the report; original production JAR checksums are unavailable, so byte-for-byte identity with the user's installation is not claimed. LDLib2 was copied from the existing local pack and has a recorded SHA256, without an independently verified official checksum.
