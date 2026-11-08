# P1 ordered tuple evidence diagnostic

This is a separately prepared instrumentation candidate for **correctness evidence only**. It is not the P2 delivery and is not a performance baseline. The original P1 source, JAR and runtime directories remain unchanged. No game was started during preparation, and no R runtime directory was read or modified for this preparation.

The frozen baseline is `baseline-catalog-P1.jar`, SHA256 `004AE477920BAA6ADF890417FF11431771A52D2F2733E818B49C446484549BD3`. The unmodified source in `baseline-source/BudgetedRecipeCatalog.java` was compiled independently; **all nine resulting class-family entries match this exact baseline byte for byte**. The proof is in `attempts/20261106-221047331/build-report.json`. The instrumented source and frozen `CatalogTupleEvidence` helper also compile successfully. **No diagnostic JAR has yet been built, no same-input comparison has yet been run, and no gameplay result is claimed.**

Only the copied source in `source/` is instrumented:

- The final ordered entry is appended to the tuple accumulator during INDEX, on the owner.
- After a successful native PUBLISH, it logs the full ordered digest and every source count/digest. Failed/stale publication does not export evidence.
- An optional D-drive directory enables full tuple retention and bounded asynchronous export of immutable string tuples. The worker's token checks only the build's volatile cancellation flag; it never accesses a level, registry, recipe or ItemStack.
- Export filenames include owner, generation and build start token and never overwrite existing files. A completed file describes that published generation; it is not proof that no later generation was requested. Select the final settled generation from the matching log.

P1's native capture, adapters, conversion, fingerprinting, stable sort, indexes, cache publication, retry and ready behavior are retained. The original limits remain **2,000,000 ns per slice, 8192 maximum steps and 128 fingerprints per tick**. Hashing and diagnostic logging add work, so timing numbers from this diagnostic must not be compared as an uninstrumented P1 performance result.

## Prepare or build without starting Minecraft

All files written by these commands stay below this D-drive directory. The script reads an already recorded build classpath, rejects R runtime entries, and invokes only `javac` plus ZIP operations. It does not invoke Gradle, Minecraft, a server, a loader, or an attach tool.

```powershell
# Repeat the source-to-exact-JAR bytecode proof only:
& .\build-p1-evidence.ps1 -VerifySourceOnly

# Explicitly build the separate diagnostic; refuses to overwrite an existing diagnostic:
& .\build-p1-evidence.ps1
```

The optional build produces `diagnostic/useless_stretcher-1.21.1-1.6.4-catalog-P1-evidence.jar`, with diagnostic metadata version `1.21.1-1.6.4-catalog-P1-evidence`. Its audit permits only the BudgetedRecipeCatalog class family, new CatalogTupleEvidence class family, and the diagnostic version in mod metadata to differ from P1; other entries must remain byte-identical.

## Later evidence collection, in independently authorized isolated runs

Use the same mod versions, configurations, recipe/data packs, tag inputs and relevant world inputs on both sides. Counts alone cannot establish input equality or output equivalence. Keep client and server exports distinct and compare corresponding owners and settled generations.

For the P1 diagnostic, set a unique D-drive output directory using the JVM option:

```text
-Duseless_stretcher.catalog.p1EvidenceDirectory=D:/AI/tuple-comparison/P1-run-unique
```

For P2, its separate setting is `-Duseless_stretcher.catalogTupleDirectory=D:/AI/tuple-comparison/P2-run-unique`. A directory setting enables complete tuple retention; without it, P1 logs digests without writing full tuple files. These instructions do not authorize or start either run.

After both full files actually exist, run the independent streaming verifier:

```powershell
& ..\compare-tuples.ps1 -Left 'D:\AI\tuple-comparison\P1-run-unique\actual-file.tsv' `
    -Right 'D:\AI\tuple-comparison\P2-run-unique\actual-file.tuples' `
    -Report 'D:\AI\tuple-comparison\comparison.json'
```

It recomputes the versioned, UTF-8 length-prefixed ordered SHA256, checks tuple indices/count, validates every source digest, and reports the first differing tuple. Invalid or incomplete files are rejected.

**R currently has no full tuple export. Its tuple order and SHA256 are unavailable and cannot be reconstructed from the recorded counts, durations or source summaries.** This diagnostic provides a way to collect future same-input P1/P2 evidence; it does not turn existing R logs into an oracle and does not prove P2 equivalence before that comparison is performed.
