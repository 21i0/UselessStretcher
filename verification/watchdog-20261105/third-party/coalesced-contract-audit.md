# Productive Bees Genesis / JDTE contract audit — 2026-11-05

This is a source and bytecode audit, not a runtime reproduction of the user's server.

## Exact old artifact

- Official project: https://github.com/Ayoshiko/productive-bees-genesis
- Release: https://github.com/Ayoshiko/productive-bees-genesis/releases/tag/v1.0.9
- Artifact: https://github.com/Ayoshiko/productive-bees-genesis/releases/download/v1.0.9/productivebeesgenesis-1.0.9.jar
- Local file: `productivebeesgenesis-1.0.9.jar` (2,793,872 bytes)
- SHA256: `CBD6280828A5AF08A92A9F857FB886B4CBC3E1C05773765AE80A12675E1FA2E5`
- The downloaded artifact hash matches the official GitHub release asset digest.
- Source tag `v1.0.9`: commit `6b7c48a4000475452f396161ba92f5c17c8d1977`.
- The selected original Java sources are preserved in `source-1.0.9/`. Their URLs are `https://raw.githubusercontent.com/Ayoshiko/productive-bees-genesis/v1.0.9/src/main/java/com/ayoshiko/productivebeesgenesis/` followed by their package-relative names.
- Bytecode evidence: `productivebeesgenesis-1.0.9-javap.txt`.

The user's actual server JAR hash is unavailable. Matching reported version and the official artifact is not proof that a locally modified server JAR is byte-identical.

## Existing API in 1.0.9

`TileEntityExtraMekCentrifugeFactory` already implements the optional-dependency-free interface `com.ayoshiko.productivebeesgenesis.mek.IJdteCentrifugeFactory`.

```java
void productivebeesgenesis$accumulateAcceleratedTicks(int ticks);
void productivebeesgenesis$flushAcceleratedTicks();
```

The first method only credits `TickAccelTracker.addVirtualTicks(ticks)`. The second checks `TickBatchSkipState.tryBeginGameTick(level.getGameTime())`, takes one shared batch, and calls the full factory pipeline once. The normal ticker uses the same per-real-game-tick gate. See `source-1.0.9/TileEntityExtraMekCentrifugeFactory.java:393` and `:402`.

When JDTE is installed, `JdteExtraCentrifugeFactoryCoalescedMixin` supplies `com.jdte.common.blockentities.CoalescedAcceleratedMachine`, with these public methods:

```java
void accumulateAcceleratedTicks(int ticks);
void flushAcceleratedTicks();
```

They delegate to the PBG methods above. The PBG interface can therefore also be used when JDTE is absent, guarded and reflected without introducing a hard dependency.

## Recommended staff integration

1. Detect a known coalescing interface before discovering ordinary ticker/AE endpoints.
2. Credit only work granted by the staff's existing execution budget, once per contribution.
3. Queue targets by object identity under the server. Multiple staff/range contributions share one real-tick flush per target.
4. Flush at the server's real-tick completion, with loaded/current-target validation. Do not put the flush inside the incrementing AE virtual-clock scope.
5. For these targets, do not also invoke their ordinary ticker or AE ticking service. The coalesced implementation owns its complete production and IO pipeline.
6. If reflection execution throws after it begins, do not replay the contribution through the ordinary ticker. A void method may already have credited or processed part of the work.
7. Preserve normal machine ticks and normal IO; no IO-cancellation mixin is required merely to support official PBG 1.0.9.

Credit is a request accepted into the target's bounded native bank, not a claim that all production completes immediately. The PBG 1.0.9 bank caps at 4,096 virtual ticks and takes at most its adaptive batch budget (default 1,024) per real game tick.

## Loop and IO audit

The exact 1.0.9 source already has per-real-tick IO quotas, an insertion cost tracker, and backoff. These protections must not be described as new in 1.2.1.

- `Ae2FluidPusher.java:380`: iterates a drained map snapshot. `recordAttempt` changes a different map, `lastAttemptRemaining`.
- `Ae2FluidPusher.java:571`: at most 8 insert calls per key; decreases `remaining`, and breaks on zero or partial acceptance.
- `Ae2FluidPusher.java:668`: decreases the remaining shrink amount and breaks on zero progress or an exception.
- The original constants already include direct inserts per tick = 16, additional local drains per tick = 64, and one batch request at most `1L << 40` mB.

No obvious non-progress infinite loop was found in these loops. A slow `MEStorage.insert` remains synchronous and cannot be interrupted by the staff's outer deadline. Moving world mutations to another thread or forcibly interrupting it is not a valid fix.

The source audit supports integrating the existing batching contract. It does not prove the user's particular oversized/blocked AE network is healthy, nor that an upgrade to 1.2.1 alone resolves the watchdog. Runtime verification must cover actual official versions, repeated contributions, blocked/full storage, normal and accelerated production, and server progress.
