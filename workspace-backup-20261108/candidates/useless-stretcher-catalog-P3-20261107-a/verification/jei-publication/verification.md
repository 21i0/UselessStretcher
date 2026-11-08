# P3 JEI callback/reentry regression

This runner is standalone Java/JUnit. It creates mocked client/runtime interfaces
and bootstraps vanilla codecs, without starting Minecraft, ModLauncher, a client,
a server, or a world. All generated files are inside the P3 candidate.

```powershell
# Use controller and OwnerRecipePublication directly from the frozen P2 JAR.
& .\verification\jei-publication\run-tests.ps1 -Controller P2Baseline

# Recompile current P3 controller and OwnerRecipePublication, then run the same tests.
& .\verification\jei-publication\run-tests.ps1 -Controller P3Source
```

The runner records the baseline JAR hash, source hashes, compile/runtime arguments,
classpath, exit code, class origins, and class hashes. It preserves complete logs
in a new run directory on every invocation. Expected injected exceptions produce
ERROR log messages; the JUnit summary determines the result.

## Frozen P2 reproduction

- Baseline JAR SHA256:
  `161D9CF701F2C3060DFC17416F9E557111D325C4C11767E0460BA2CBED76EEA4`.
- Controller class SHA256:
  `BF7B3FD6E16B84F375539E2D5306944E60AB74B4A080A6A4B767C4CA3BB77208`.
- OwnerRecipePublication class SHA256:
  `2369FAC486E158ADD1952BD4841404B59E6C3951AAF4491515A68F4770AB8E1C`.
- Initial combination evidence: `runs/P2Baseline-20261106-223256757/tests.log`
  (36 tests, 18 successful, 18 failed as expected).
- Final expanded evidence: `runs/P2Baseline-20261106-223814672/tests.log`
  (54 tests, 18 successful, 36 failed as expected).

The P2 controller and owner classes are loaded directly from the complete frozen
P2 JAR, not recompiled substitutes. The log records their origins and hashes.

Reproduction trace:

| Scenario | P2 actual result |
| --- | --- |
| add partially mutates, invalidates, then throws | old task FAILED; callback count 1 -> 2; four registered entries for three inputs; ready=true |
| hide partially mutates, invalidates, then throws | callback count 1 -> 3 after retries/reset |
| unhide partially mutates, invalidates, then throws | callback count 1 -> 2 |
| same failures after invalidate + request installs a new task | same replay behavior for all three operations |
| old runtime throws after a new runtime is installed, then old runtime returns | old runtime receives another mutation for all three operations |
| callback invokes nested frame before the outer call settles | second callback enters for add/hide/unhide, whether outer callback returns or throws |
| same-runtime registration reset clears the native ledger during callback | normally returned add repopulates the cleared ledger; throwing add and unhide cases replay; all cases lose the required replay barrier |
| one callback observes runtime A -> B -> A before settling | normal add repopulates the obsolete ledger; other cases replay; the failed/uncertain origin runtime becomes ready again |

## Earlier P3 source result

- Evidence: `runs/P3Source-20261106-223853176/tests.log`.
- Result: 54 tests started, 54 successful, zero failed.
- Controller source SHA256:
  `6EF173A38952AABF328D8D69478F3842D3E039031E9A4248946412802E24203F`.
- Controller class SHA256:
  `5479DC4F06952BB014E5F2C578742CA4FA81695B12267DB9C3EDB1848AA5ED82`.
- Test source SHA256 for this earlier 54-case run:
  `C106BF3CFB0C630F6E8BF56239B392CBA285416E4ADEE1B2740EB1B5536889CB`.
- OwnerRecipePublication class SHA256 is identical to the frozen P2 class.

This earlier run independently compiled the exact source hash above. The
source hashes are recorded in its `inputs.json`. Earlier P3 runs remain as
intermediate evidence, including the 36-case pre-registration-epoch version and
the 48-case first registration-epoch version. None of these is the final result.

All six reentrant-throw cases retain callback count 1 -> 1 and remain blocked
after retry/reset. All six nested-frame cases have exactly one callback before
the outer call settles. All 12 registration-reset cases leave the native ledger
empty and block the affected runtime. All six observed A -> B -> A cases block
origin A without repopulating its ledger; healthy B subsequently completes.

## Final 60-case result and old P3 comparison

The final six cases exercise both directions of a mismatch between the native
runtime identity and already observed controller state. They use real mocked
callback sequences; they do not assign failure flags or registry contents.

1. Origin A partially mutates, installs B and calls request (observing B), then
   switches only the native installed identity back to A and throws. B is then
   installed again without being observed anew. A's failure must not block B.
2. Observed B partially mutates, switches only the native installed identity to
   healthy A and throws. Its failure is recorded while B is inactive. Switching
   the native identity back to B without observing A must still fence B.

Each sequence covers add, hide, and unhide. Both rely on request reselecting the
failure state from the actual current runtime identity even when it does not
need to call observeRuntime.

Old P3 comparison:

- Evidence: `runs/P3Previous6EF-20261106-224646036/tests.log`.
- Controller and owner classes are loaded from the preserved successful
  54-case run `P3Source-20261106-223853176/classes`, not from current source.
- That class directory's input manifest records controller source SHA256
  `6EF173A38952AABF328D8D69478F3842D3E039031E9A4248946412802E24203F`.
- Loaded controller class SHA256:
  `5479DC4F06952BB014E5F2C578742CA4FA81695B12267DB9C3EDB1848AA5ED82`.
- Same final test source: 60 tests started, 54 successful, **six failed**.
- In the first sequence, all three healthy B runtimes remain incorrectly
  blocked with zero registrations. In the second, all three failed B runtimes
  receive another mutation and incorrectly become ready.

Final P3 result:

- Evidence: `runs/P3Source-20261106-224715819/tests.log`.
- Independently compiled controller source SHA256:
  `03D9F3E41087BBA6CA41513554C0E5BD6F15DA205FB81A8FEE5BD49862B716EC`.
- Loaded controller class SHA256:
  `6AFA6EF41769F06725B6B62F3E3252F45C8955D972EBFB65F1FE39BC8A7C692E`.
- Final test source SHA256:
  `7AB3C4B42FF102FACA193F27D7F835FDFE1739B377CC7394778CACED12171977`.
- OwnerRecipePublication class SHA256 remains identical to frozen P2:
  `2369FAC486E158ADD1952BD4841404B59E6C3951AAF4491515A68F4770AB8E1C`.
- **60 tests started, 60 successful, zero failed.**
- Healthy B completes with three registered inputs for every operation. Failed
  B's mutation count does not increase when it returns, and it remains blocked.
- `inputs.json` and `result.json` in both run directories preserve their exact
  source hashes, controller origin information, and process exit status.

The intermediate run `P3Previous6EF-20261106-224530203` used an earlier version
of the symmetric test that assigned detached failure flags. It is retained as
intermediate evidence only. The final comparison above uses the pure callback
version, which produced the same six failures without assigning those flags.

## Coverage and isolation

The final 60 test cases include the original 12 lifecycle cases, six cases for each of
the first four groups below, 12 registration-reset cases, and six observed-runtime
excursion cases, and the six native/observed-runtime mismatch cases above:

- Partial add/hide/unhide mutation followed by invalidate (null task) or
  invalidate + request (replacement task), then throw. The same runtime must
  remain blocked through subsequent requests, invalidation, and reset.
- Normally returned add/hide/unhide callbacks with the same two reentry forms.
  Canonical ledger objects and visibility semantics must remain correct.
- Failure in an old runtime after a new runtime is installed, both before and
  after request has observed the new runtime. The replacement runtime must
  recover; the failed old runtime must remain blocked if it reappears.
- Nested frame while an outer add/hide/unhide callback remains unsettled, with
  both normal return and subsequent throw. Nested frame must not advance the
  replacement task.
- Same-runtime registration reset and native ledger clearing during an outer
  add/hide/unhide call, with normal return or throw and with no replacement task
  or a request that installs one. The old callback cannot repopulate the cleared
  native ledger, and the affected runtime cannot replay.
- Observed runtime A -> B -> A excursion inside an add/hide/unhide callback,
  followed by normal return or throw. Returning to A before the call settles
  cannot make the old canonical ledger valid again; healthy B can still recover.

Fixtures save and restore mutable static fields, static container contents,
queued-request state, visible identities, and native JEI ledger contents. Tests
verify lifecycle behavior and unknown-partial-commit replay barriers. They do
not establish receiver counts, frame performance, or full in-game correctness.
