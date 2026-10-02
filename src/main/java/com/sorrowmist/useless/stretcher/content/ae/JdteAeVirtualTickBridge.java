package com.sorrowmist.useless.stretcher.content.ae;

import java.lang.reflect.Method;

/**
 * Optional bridge for jdte-ae's logical AE clock. It never calls JDTE's whole-grid manager;
 * it only borrows the public per-call clock scope for a target already selected by this mod.
 */
public final class JdteAeVirtualTickBridge {
    private static final Method ENTER = findEnter();

    private JdteAeVirtualTickBridge() {
    }

    public static Scope enter(long virtualTick) {
        if (ENTER == null) return Scope.NONE;
        try {
            Object scope = ENTER.invoke(null, virtualTick, 1);
            if (scope instanceof AutoCloseable closeable) return new Scope(closeable);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional compatibility only. A missing or changed jdte-ae must not affect ticking.
        }
        return Scope.NONE;
    }

    private static Method findEnter() {
        try {
            Class<?> context = Class.forName("com.jdte.ae.AEVirtualTickContext", false,
                    JdteAeVirtualTickBridge.class.getClassLoader());
            return context.getMethod("enter", long.class, int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NONE = new Scope(null);
        private final AutoCloseable delegate;

        private Scope(AutoCloseable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            if (delegate == null) return;
            try {
                delegate.close();
            } catch (Exception ignored) {
                // Do not let an optional scope failure break the target tick.
            }
        }
    }
}
