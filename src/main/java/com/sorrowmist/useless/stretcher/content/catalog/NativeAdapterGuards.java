package com.sorrowmist.useless.stretcher.content.catalog;

import java.util.function.Supplier;

/** Retains the official session-wide guards when present, without linking old bases to the new class. */
final class NativeAdapterGuards {
    private static final boolean AVAILABLE = present();
    private static final Object FAILED = new Object();

    private NativeAdapterGuards() {}

    static boolean available() { return AVAILABLE; }

    static <T> T call(Object adapter, String action, Supplier<T> work, T fallback) {
        return AVAILABLE ? Native.call(adapter, action, work, fallback) : work.get();
    }

    static <T> T callEach(Object adapter, String action, Supplier<T> work, T fallback) {
        return AVAILABLE ? Native.callEach(adapter, action, work, fallback) : work.get();
    }

    static <T> T guard(String scope, Supplier<T> work, T fallback) {
        return AVAILABLE ? Native.guard(scope, work, fallback) : work.get();
    }

    /** Empty snapshots are only allocated on a failed query, never on the ready hot path. */
    @SuppressWarnings("unchecked")
    static <T> T guardLazy(String scope, Supplier<T> work, Supplier<T> fallback) {
        if (!AVAILABLE) return work.get();
        Object value = Native.guard(scope, work::get, FAILED);
        return value == FAILED ? fallback.get() : (T) value;
    }

    static boolean isDisabled(Object adapter, String action) {
        return AVAILABLE && Native.isDisabled(adapter, action);
    }

    static void rememberSource(Object adapter, String source) {
        if (AVAILABLE) Native.rememberSource(adapter, source);
    }

    private static boolean present() {
        try {
            Class.forName("com.sorrowmist.useless.content.recipe.AdapterGuards", false,
                    NativeAdapterGuards.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absentOnOldBase) { return false; }
    }

    /** Resolve direct references only after the official class was found. */
    private static final class Native {
        static <T> T call(Object adapter, String action, Supplier<T> work, T fallback) {
            return com.sorrowmist.useless.content.recipe.AdapterGuards.call(adapter, action, work, fallback);
        }
        static <T> T callEach(Object adapter, String action, Supplier<T> work, T fallback) {
            return com.sorrowmist.useless.content.recipe.AdapterGuards.callEach(adapter, action, work, fallback);
        }
        static <T> T guard(String scope, Supplier<T> work, T fallback) {
            return com.sorrowmist.useless.content.recipe.AdapterGuards.guard(scope, work, fallback);
        }
        static boolean isDisabled(Object adapter, String action) {
            return com.sorrowmist.useless.content.recipe.AdapterGuards.isDisabled(adapter, action);
        }
        static void rememberSource(Object adapter, String source) {
            com.sorrowmist.useless.content.recipe.AdapterGuards.rememberSource(adapter, source);
        }
    }
}
