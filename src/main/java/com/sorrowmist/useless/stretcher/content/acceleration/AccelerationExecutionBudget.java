package com.sorrowmist.useless.stretcher.content.acceleration;

import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-wide allowance for expensive virtual ticks produced by every staff accelerator.
 *
 * <p>At a healthy MSPT the allowance matches the range accelerator's previous one-million tick
 * ceiling. A cooperative elapsed-time guard also limits expensive callbacks; when either budget
 * is exhausted, unspent work remains in each target's bounded pending counter.
 * This protects the next real server tick without changing the selected multiplier or any saved
 * staff/range state.
 */
public final class AccelerationExecutionBudget {
    public static final int HEALTHY_BUDGET = 1_000_000;
    private static final int ELEVATED_BUDGET = 262_144;
    private static final int STRAINED_BUDGET = 65_536;
    private static final int OVERLOADED_BUDGET = 16_384;
    private static final int EMERGENCY_BUDGET = 4_096;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long MAX_WORK_NANOS = 5_000_000L;

    /** Weak server keys avoid retaining an integrated server after returning to the title screen. */
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private AccelerationExecutionBudget() {
    }

    /** Starts a fresh allowance immediately before the server processes the next real tick. */
    public static void beginTick(MinecraftServer server) {
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        state.begin(server.getTickCount(), currentBudget(server));
    }

    /** Releases runtime-only accounting when a dedicated or integrated server stops. */
    public static void removeServer(MinecraftServer server) {
        STATES.remove(server);
    }

    /** A count budget alone cannot protect against an expensive modded tick. */
    public static long deadline(MinecraftServer server) {
        ServerState state = STATES.get(server);
        if (state == null) return System.nanoTime() + MAX_WORK_NANOS;
        return System.nanoTime() + Math.max(0L, Math.min(state.sliceNanos, MAX_WORK_NANOS - state.workNanos));
    }

    public static void recordWork(MinecraftServer server, long started) {
        ServerState state = STATES.get(server);
        if (state != null) state.workNanos += Math.max(0L, System.nanoTime() - started);
    }

    /**
     * Claims up to {@code requested} virtual ticks for one stable runtime target key.
     * During degraded operation the previous tick's active-target count supplies a fair per-target
     * slice. The count allowance can grant a single x1024 target all 1024 ticks even at the
     * emergency 4096 budget. Actual execution also obeys the elapsed-time deadline, so expensive
     * targets yield without claiming that their configured multiplier is guaranteed throughput.
     */
    public static int take(MinecraftServer server, Object targetKey, int requested) {
        if (requested <= 0 || targetKey == null) return 0;
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        int serverTick = server.getTickCount();
        if (state.tick != serverTick) {
            // Also works in unusual test/server environments that do not post ServerTickEvent.Pre.
            state.begin(serverTick, currentBudget(server));
        }
        return state.take(targetKey, requested);
    }

    private static int currentBudget(MinecraftServer server) {
        long[] samples = server.getTickTimesNanos();
        long total = 0L;
        int count = 0;
        for (long sample : samples) {
            if (sample <= 0L) continue;
            // Saturating accumulation keeps corrupted/extreme profiler samples harmless.
            if (Long.MAX_VALUE - total < sample) {
                total = Long.MAX_VALUE;
            } else {
                total += sample;
            }
            count++;
        }
        if (count == 0) return HEALTHY_BUDGET;
        long average = total == Long.MAX_VALUE ? Long.MAX_VALUE : total / count;
        long target = Math.max(NANOS_PER_MILLISECOND,
                (long) (server.tickRateManager().millisecondsPerTick() * NANOS_PER_MILLISECOND));
        return adaptiveBudget(average, target);
    }

    /** Pure policy method kept separate so each threshold can be verified without a running world. */
    public static int adaptiveBudget(long averageTickNanos, long targetTickNanos) {
        long average = Math.max(0L, averageTickNanos);
        long target = Math.max(1L, targetTickNanos);
        if (average <= target) return HEALTHY_BUDGET;
        if ((double) average <= (double) target * 1.25D) return ELEVATED_BUDGET;
        if ((double) average <= (double) target * 1.50D) return STRAINED_BUDGET;
        if ((double) average <= (double) target * 2.00D) return OVERLOADED_BUDGET;
        return EMERGENCY_BUDGET;
    }

    private static final class ServerState {
        private int tick = Integer.MIN_VALUE;
        private int remaining;
        private int perTargetLimit = Integer.MAX_VALUE;
        private long workNanos;
        private long sliceNanos = MAX_WORK_NANOS;
        private final Map<Object, Integer> activeTargets = new IdentityHashMap<>();

        private void begin(int serverTick, int budget) {
            int previousTargetCount = Math.max(1, activeTargets.size());
            activeTargets.clear();
            workNanos = 0L;
            sliceNanos = Math.max(1L, MAX_WORK_NANOS / previousTargetCount);
            tick = serverTick;
            remaining = Math.max(0, budget);
            if (budget >= HEALTHY_BUDGET) {
                perTargetLimit = Integer.MAX_VALUE;
                return;
            }
            perTargetLimit = Math.max(1,
                    (budget + previousTargetCount - 1) / previousTargetCount);
        }

        private int take(Object targetKey, int requested) {
            int alreadyGranted = activeTargets.getOrDefault(targetKey, 0);
            int targetRemaining = perTargetLimit == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : Math.max(0, perTargetLimit - alreadyGranted);
            int granted = Math.min(Math.max(0, requested), Math.min(remaining, targetRemaining));
            if (workNanos >= MAX_WORK_NANOS) granted = 0;
            activeTargets.put(targetKey, alreadyGranted + granted);
            remaining -= granted;
            return granted;
        }
    }
}
