package com.sorrowmist.useless.stretcher.content.acceleration;

import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-wide allowance for expensive virtual ticks produced by every staff accelerator.
 *
 * <p>At a healthy MSPT the allowance matches the range accelerator's previous one-million tick
 * ceiling, so ordinary setups retain their existing throughput. When the server is already late,
 * the allowance is reduced in steps and unspent work remains in each target's pending counter.
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

    /**
     * Claims up to {@code requested} virtual ticks for one stable runtime target key.
     * During degraded operation the previous tick's active-target count supplies a fair per-target
     * slice. A single x1024 target therefore keeps its complete 1024 virtual ticks even at the
     * emergency 4096 global budget; only enough simultaneous targets to exceed the server-wide
     * allowance are shared down. This matches JDTE's observable single-target throughput while
     * preventing the first machine in tick order from consuming the complete allowance.
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
        private final Map<Object, Integer> activeTargets = new IdentityHashMap<>();

        private void begin(int serverTick, int budget) {
            int previousTargetCount = Math.max(1, activeTargets.size());
            activeTargets.clear();
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
            activeTargets.put(targetKey, alreadyGranted + granted);
            remaining -= granted;
            return granted;
        }
    }
}
