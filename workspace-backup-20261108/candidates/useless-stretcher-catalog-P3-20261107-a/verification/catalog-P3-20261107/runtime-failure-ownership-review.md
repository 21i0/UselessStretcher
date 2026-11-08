# P3 review: JEI runtime failure ownership under re-entry

Read-only production/test review, 2026-11-07. No Minecraft launch, no production edits, and no change to P2 or S.

## Frozen evidence and confirmed defect

`baseline-catalog-P2.jar` SHA256 was read and confirmed:

`161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4`.

The frozen `p2-evidence/frozen-P2-jei-publication-javap.txt` proves that `frame` calls `advancing.advance` at offset **177**, compares the old task to global `task` at **185**, returns at **205** when different, and first reads a failure at **209**. `OwnerRecipePublication.advance` stores an exception from the sink in the *old* task even if re-entry cancelled that task. The old failure therefore exists but is lost by the early return. This is independent of compilation or successful normal-re-entry tests.

The existing lifecycle tests cover normal-returning invalidation/replacement, non-reentrant partial failure, and recovery on a new runtime. They do not yet cover a callback that both replaces/cancels a task and throws after partial mutation, or a failed runtime returning after another runtime was installed.

## Minimum complete failure rule

Every task must carry the exact `IJeiRuntime` object targeted by its sink. This association is immutable for that task. Store it in a small task context/envelope or capture a paired `taskRuntime` field when creating the task; do not infer it from mutable global `runtime` after a callback. The sink's `targetManager` is also fixed and belongs to that runtime.

After `advance` returns, inspect `advancing.failure()` **before** comparing `advancing` to global `task`. An old native failure is significant even when a replacement task is now active. Consume that result against the old task's target runtime; then perform the pointer/epoch comparison before reading any global task status or readiness.

Split stopping into two responsibilities:

1. Remember a failure for its actual target runtime.
2. Fence/cancel only a currently active publication owned by that same runtime.

Remembering failure for A must not set B's active failure flag, cancel B's task, clear B's ready state, or hide B indefinitely. Remembering a failure for A while another task for A was installed re-entrantly must cancel that replacement, clear readiness, and retain the pending/failure barrier. This remains true if the failure is already recorded: duplicate-record suppression must not return before enforcing the current fence.

Illustrative control flow (not a production patch):

```text
advancing = currentTaskContext
advancing.work.advance(...)
error = advancing.work.failure()
if error exists:
    if error is exclusively a safe pre-call stale marker:
        cancel/replan only if advancing is still the active context
    else:
        rememberFailure(advancing.targetRuntime, detachedFailureInfo)
        fenceAnyCurrentTaskFor(advancing.targetRuntime)
    return
if advancing != currentTaskContext:
    return
inspect advancing.work.ready() / completion logging
```

The exact code may record the native exception immediately in the sink and consume it again idempotently in `frame`; the required property is that task replacement cannot erase the fault. A broad catch outside the sink must retain a previously captured operation owner rather than calling `stop(globalRuntime, oldError)`.

## Required transition matrix

| Situation inside a native add/hide/unhide call | Old task result | Required outcome |
| --- | --- | --- |
| A mutates partially, invalidates, returns normally | Normal returned batch | Keep A's original objects in its ledger; no unsafe-failure latch solely for invalidation. |
| A mutates partially, invalidates, throws | Native failure | Remember A; block A; no automatic replay. |
| A mutates partially, installs a new task for A, throws | Native failure in old task | Remember A; cancel the new A task, including one already at DONE; set ready false. |
| A mutates partially, installs B and a B task, throws | Native failure in old task | Remember A only; B continues and can become ready. |
| A fails; B later runs; A is later reinstalled | Persistent failure record for A | A is blocked before any new mutation. B's success must not erase A's fault. |
| Source/epoch changes before native invocation | Safe pre-call stale marker | Cancel/replan without poisoning the runtime. |
| Runtime changes after a normally returned native invocation, before ledger update | Post-call runtime switch | Do not write A's result into B's shared native ledger. Either preserve A's own result ledger or conservatively mark A non-replayable; B remains usable. |

## Weak identity fault records

The current single weak reference cannot implement A → B → A: observing B clears A's record. `reset`, invalidation, runtime unavailability, and observing B must not erase historical faults for still-live A.

The actual JEI 19.57 `mezz.jei.library.runtime.JeiRuntime` was examined with `javap -p`; it does not override `equals` or `hashCode`. A plain `WeakHashMap<IJeiRuntime, ...>` therefore happens to behave as identity for that concrete implementation. The API is an interface and the class is not final, however; proxies/subclasses can define value equality. If the contract says runtime **identity**, a plain WeakHashMap is not a robust encoding of it.

The smallest suitable structure for the few runtime switches expected here is an owner-only list of weak references with detached failure metadata. On remember/lookup, remove cleared references and compare non-null referents using `==`. Retain a local strong reference while comparing. If a hash map is preferred, use a weak identity key with cached `System.identityHashCode`, `ReferenceQueue` cleanup, and equality that is true only for the same key object or the same non-null referent. `IdentityHashMap<WeakReference<...>, ...>` alone is insufficient because it compares weak-reference wrappers rather than their referents.

Fault metadata should be strings/operation/epoch/counters only. Do not store a Throwable, manager, task, sink, supplier, recipe Entry, or closure in the value: such objects may strongly reference the runtime and defeat weak-key collection. Log the original Throwable immediately without retaining it in the fault registry. Do not clear live weak entries merely to impose an arbitrary capacity: that would permit an old unsafe runtime to replay. Dead-key purging bounds retention naturally.

A null runtime is not a meaningful native mutation owner. Setup/ABI failures before any runtime exists need a separate observable status; do not let a weak null referent be mistaken for a matching failed runtime.

## Stale markers must describe where the failure occurred

Only a marker thrown by this implementation's **pre-call** guard, before invoking the native mutating method, is safely replayable. A callback exception must retain its native-failure classification even if the callback already changed epoch, entries, or runtime. Never rewrite `catch (RuntimeException)` into a stale marker merely because the active context changed.

The P2 sink also throws `StaleRevision` *after* a native method returned when `JEIPlugin.getRuntime() != targetRuntime`. That guard prevents old `flush` code from appending its ledger into B's shared native map, but its documentation claiming no invocation occurred is inaccurate. Treat it separately from pre-call stale. A minimal conservative fix records A as non-replayable, without blocking B. Alternatively preserve a distinct canonical ledger for A and record the normal-returned batch there; that is a broader change.

A useful narrow boundary is `checkBeforeCall(); try nativeCall(); catch nativeError -> NativeCallbackFailure(targetRuntime, operation, nativeError)`. A post-call context switch uses its own marker. The native error cannot then be confused with a guard that prevented a call. Wrapping must not lose the original exception in logs.

Cancellation itself is not proof that the old mutation failed safely. `OwnerRecipePublication` can be CANCELLED by re-entry and then become FAILED when the native method throws back into its `advance` catch. Always inspect that final stored result.

The unrelated final `ClientSetup.onRecipeCatalogReady` notification is a separate callback. Its exception must not be labelled an unknown JEI add/hide/unhide mutation unless the actual publication operation recorded that failure. Keep its logging/retry policy separate from the native-mutation fence.

## Additional re-entry constraints to retain

- Same-runtime invalidation deliberately keeps the canonical map and visible set, so a normally returned old batch can finish recording its original Entry objects before the old frame exits.
- If a callback can call `frame` recursively, a newly installed same-runtime task must not submit work while an older native mutation is still in flight; otherwise the replacement can re-add the unrecorded old batch before the outer call returns. An owner-only advancing/in-native-call guard can defer nested frame advancement. Merely queueing a new task is safe.
- Registration reset is stronger than ordinary invalidation because the native static map may be cleared. Do not assume a post-call epoch mismatch means safe invalidation if a reset cleared the same runtime's ledger. Either distinguish the registration generation or fence the affected runtime conservatively.
- `observeRuntime` derives the active stopped/ready status from the fault record for the newly observed object. It must not clear the global fault collection.
- The current active task, visibility gate, and ready state must all remain associated with one runtime. A stale A error may be logged while B is active, but status shown to B must describe B.

## Regression cases required before freezing P3

1. Callback mutates one item, invalidates, then throws; subsequent invalidate/reset/request/frame cannot replay into A.
2. Callback mutates one item, creates a replacement task for A, then throws; the replacement is cancelled and no ready callback is emitted.
3. Callback mutates one item, installs B and schedules B, then throws; B publishes successfully, while A's unknown batch is attempted once.
4. Reinstall the exact failed A after B succeeds, including a reset in between; A never mutates again.
5. Distinct runtime objects with value-equal `equals/hashCode` do not share faults; identity A is blocked and identity B remains usable.
6. Normal-returning same-runtime re-entry still preserves the original object ledger and avoids duplicate additions.
7. Pre-call stale entries/runtime guard performs no JEI mutation and may safely replan; a native callback exception after the same changes is always fenced.
8. Normally returned call that switches runtime never pollutes B's ledger; if A's ledger was not preserved, returning A is fenced.
9. Partial hide and unhide failures get the same ownership behavior as add, and historical fault records survive reset without retaining dead runtimes strongly.

This review supplies the defect evidence, minimum design constraints, and missing-test matrix. It is not a claim that the P3 production fix or its new tests have already passed.

## Follow-up review of the implemented P3 combination

Read-only review of the subsequently updated `CatalogJeiPublication.java`: **no must-fix defect found within this runtime-failure/re-entry combination**.

- `taskRuntime` is assigned from the sink's captured target when creating the task. `frame` captures that paired runtime before advancing, reads the old task's stored failure before checking task identity, and attributes it to that target.
- `stop` first remembers the target in `runtimeFailures`, then fences only when the actual installed runtime is that target (or cancels a remaining task owned by that target). It does not poison an already observed replacement runtime. Duplicate failure records suppress duplicate logging only after the fence has been applied.
- `runtimeFailures` stores a weak reference plus detached message string, compares referents with `==`, purges dead entries, and survives reset. `observeRuntime` consults the history, so A → B → A cannot replay A's unknown batch.
- `frameInProgress` defers recursive frame advancement until the outer mutation returns and its result is settled. It is released in `finally` and is not reset by a reentrant registration reset.
- `registrationEpoch` advances on reset, not ordinary invalidation. A normally returning ordinary invalidation can still let the old flush record its original Entry objects. A registration reset or runtime replacement after a normal native return produces `RuntimeChangedAfterCall` before the old flush can populate the cleared/shared ledger; that old target is fenced rather than replayed.
- A native method that throws skips the post-call check and leaves its original exception in the old task. Only the pre-call check creates `StaleRevision`; a changed runtime does not relabel the native exception as safe cancellation.

The additional registration-reset test source now covers add/hide/unhide × normal return/throw × no replacement/replacement task, asserting a single native call, no old-ledger pollution, and a persistent stopped state. This reviewer did not run that new test set and does not replace the testing agent's execution evidence. No production or test source was changed by this review.
