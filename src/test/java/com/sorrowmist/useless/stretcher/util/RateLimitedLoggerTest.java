package com.sorrowmist.useless.stretcher.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitedLoggerTest {
    @Test
    void repeatsAreSuppressedUntilTheIntervalExpires() {
        AtomicLong clock = new AtomicLong();
        var gate = new RateLimitedLogger.Gate(60, 8, clock::get);
        assertTrue(gate.allow("catalog", "WARN", "failed {}"));
        clock.set(59);
        assertFalse(gate.allow("catalog", "WARN", "failed {}"));
        clock.set(60);
        assertTrue(gate.allow("catalog", "WARN", "failed {}"));
    }

    @Test
    void unrelatedMessagesRemainIndependent() {
        var gate = new RateLimitedLogger.Gate(60, 8, () -> 0);
        assertTrue(gate.allow("one", "WARN", "failure"));
        assertTrue(gate.allow("one", "ERROR", "failure"));
        assertTrue(gate.allow("two", "WARN", "failure"));
        assertTrue(gate.allow("one", "WARN", "other failure"));
        assertFalse(gate.allow("one", "WARN", "failure"));
    }

    @Test
    void storageCannotEvictAnActiveSuppressionWindow() {
        AtomicLong clock = new AtomicLong();
        var gate = new RateLimitedLogger.Gate(60, 2, clock::get);
        assertTrue(gate.allow("one", "WARN", "first"));
        assertTrue(gate.allow("one", "WARN", "second"));
        for (int i = 0; i < 100; i++) assertFalse(gate.allow("one", "WARN", "overflow " + i));
        assertFalse(gate.allow("one", "WARN", "first"));
        clock.set(60);
        assertTrue(gate.allow("one", "WARN", "new category"));
        assertTrue(gate.allow("one", "WARN", "first"));
    }

    @Test
    void concurrentFailuresOnlyLogOnce() {
        var gate = new RateLimitedLogger.Gate(60, 8, () -> 0);
        AtomicInteger allowed = new AtomicInteger();
        IntStream.range(0, 1000).parallel().forEach(i -> {
            if (gate.allow("catalog", "ERROR", "failure")) allowed.incrementAndGet();
        });
        assertEquals(1, allowed.get());
    }

    @Test
    void changingArgumentsDoesNotBypassTheLimitAndTheCauseIsRetained() {
        Logger logger = mock(Logger.class);
        when(logger.getName()).thenReturn("StretcherRateLimitedLoggerTest.arguments");
        when(logger.isWarnEnabled()).thenReturn(true);
        RuntimeException cause = new RuntimeException("failure");
        RateLimitedLogger.warn(logger, "Failed recipe {}", "one", cause);
        RateLimitedLogger.warn(logger, "Failed recipe {}", "two", cause);
        verify(logger).warn(eq("Failed recipe {}"), aryEq(new Object[]{"one", cause}));
    }

    @Test
    void integrationErrorsAcrossPlayersAreThrottledAndKeepTheFirstCause() {
        Logger logger = mock(Logger.class);
        when(logger.getName()).thenReturn("StretcherRateLimitedLoggerTest.integrationErrors");
        when(logger.isErrorEnabled()).thenReturn(true);
        RuntimeException cause = new RuntimeException("external storage failure");
        for (int player = 0; player < 100; player++)
            RateLimitedLogger.error(logger, "Integration failed for {}", player, cause);
        verify(logger).error(eq("Integration failed for {}"), aryEq(new Object[]{0, cause}));
    }

    @Test
    void disabledLogLevelDoesNotConsumeTheFirstDiagnostic() {
        Logger logger = mock(Logger.class);
        when(logger.getName()).thenReturn("StretcherRateLimitedLoggerTest.disabled");
        RateLimitedLogger.warn(logger, "Storage unavailable");
        verify(logger, never()).warn(eq("Storage unavailable"), any(Object[].class));
        when(logger.isWarnEnabled()).thenReturn(true);
        RateLimitedLogger.warn(logger, "Storage unavailable");
        verify(logger).warn(eq("Storage unavailable"), aryEq(new Object[0]));
    }
}
