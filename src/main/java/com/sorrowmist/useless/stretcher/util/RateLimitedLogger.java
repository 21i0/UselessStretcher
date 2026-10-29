package com.sorrowmist.useless.stretcher.util;

import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Logs the first failure, then limits repeats across jobs, players and recipe IDs. */
public final class RateLimitedLogger {
    private static final Gate GATE = new Gate(60_000_000_000L, 256, System::nanoTime);

    private RateLimitedLogger() { }

    public static void warn(Logger logger, String message, Object... arguments) {
        if (logger.isWarnEnabled() && GATE.allow(logger.getName(), "WARN", message)) {
            logger.warn(message, arguments);
        }
    }

    public static void error(Logger logger, String message, Object... arguments) {
        if (logger.isErrorEnabled() && GATE.allow(logger.getName(), "ERROR", message)) {
            logger.error(message, arguments);
        }
    }

    static final class Gate {
        private record Key(String logger, String level, String message) { }

        private final long interval;
        private final int capacity;
        private final LongSupplier clock;
        private final Map<Key, Long> emitted = new LinkedHashMap<>();

        Gate(long interval, int capacity, LongSupplier clock) {
            if (interval <= 0 || capacity <= 0) throw new IllegalArgumentException("Log limits must be positive");
            this.interval = interval;
            this.capacity = capacity;
            this.clock = clock;
        }

        synchronized boolean allow(String logger, String level, String message) {
            Key key = new Key(logger, level, message);
            long now = clock.getAsLong();
            Long previous = emitted.get(key);
            if (previous != null && now - previous < interval) return false;
            if (previous == null && emitted.size() >= capacity) {
                emitted.entrySet().removeIf(entry -> now - entry.getValue() >= interval);
                if (emitted.size() >= capacity) return false;
            }
            emitted.put(key, now);
            return true;
        }
    }
}
