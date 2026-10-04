package com.sorrowmist.useless.stretcher.content.item;

/** Thread-local marker used only while the wondrous staff performs an instant brush action. */
public final class StaffBrushContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private StaffBrushContext() { }

    public static void run(Runnable action) {
        int old = DEPTH.get();
        DEPTH.set(old + 1);
        try { action.run(); }
        finally { if (old == 0) DEPTH.remove(); else DEPTH.set(old); }
    }

    public static boolean active() { return DEPTH.get() > 0; }
}
