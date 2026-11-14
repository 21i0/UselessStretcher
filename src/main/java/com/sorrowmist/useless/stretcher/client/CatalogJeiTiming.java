package com.sorrowmist.useless.stretcher.client;

import com.mojang.logging.LogUtils;

/** Scoped timers around existing owner-thread JEI work; no changes to JEI registration here. */
public final class CatalogJeiTiming {
    private static final ThreadLocal<Long> REFRESH = new ThreadLocal<>();
    private static final ThreadLocal<Long> ADD = new ThreadLocal<>();
    private CatalogJeiTiming() {}
    public static void beginRefresh() { REFRESH.set(System.nanoTime()); }
    public static void endRefresh() { end(REFRESH, "refresh-total"); }
    public static void beginAdd() { ADD.set(System.nanoTime()); }
    public static void endAdd() { end(ADD, "jei-addRecipes"); }
    private static void end(ThreadLocal<Long> timer, String phase) {
        Long start = timer.get(); timer.remove();
        if (start != null) LogUtils.getLogger().info(
                "Stretcher catalog JEI timing: phase={}, elapsed_ms={}, thread={}",
                phase, (System.nanoTime() - start) / 1_000_000.0, Thread.currentThread().getName());
    }
}
