# CatalogJeiPublication offline lifecycle regression

The independent runner recompiles the current `CatalogJeiPublication` and
`OwnerRecipePublication` source files before running the tests. It uses the real
Useless Mod 2.4.6.2 API, JEI 19.57.0.451 API, vanilla codec bootstrap, and Mockito
client/runtime interfaces. No Minecraft client, server, world, or ModLauncher is
started. All generated files remain inside this candidate directory.

Run from the candidate root:

```powershell
& .\verification\jei-publication\run-tests.ps1
```

Final successful evidence: `runs/20261106-221333711/tests.log` — 12 tests started,
12 successful, zero failed. `compile.log`, compiler arguments, runtime arguments,
the classpath, and `source-and-class-hashes.json` are in the same directory. The
expected injected callback exceptions produce ERROR log entries; the JUnit
summary determines the result.

Coverage:

- Repeated requests with the same entries object keep the existing task and do
  not add the same native Entry object twice.
- A normally returned batch is retained in the native ledger across cancellation
  and invalidation; remaining work resumes without re-adding its Entry objects.
- An add, hide, or unhide callback that changes external state and then throws
  stops automatic replay in that runtime. Invalidation and registration reset do
  not remove the replay barrier. A replacement runtime can recover.
- Replacing the entries object under the same recipe manager triggers a new plan.
  Existing identities retain their canonical native Entry objects for hide and
  unhide operations.
- The pre-call entries guard prevents a stale revision from calling JEI. A
  catalog becoming pending also cancels safely and can recover.
- Callback reentry can invalidate the current task, or invalidate and install a
  new task. The outer frame does not dereference the cancelled task or inspect
  the new task as though it were the old task.
- Replacing the runtime inside a callback prevents the old revision from
  committing readiness and allows publication in the new runtime.

Each fixture saves and restores every mutable publication static field, the
VISIBLE set, the queued-request flag, and the original native JEI ledger contents.
The tests measure lifecycle correctness only. They do not establish receiver
counts, runtime frame performance, or full in-game JEI correctness.
