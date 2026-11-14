package com.sorrowmist.useless.stretcher.content.catalog;

/** Connection-scoped barrier. Counts/events are never used as a recipe-content fingerprint. */
public final class CatalogSyncGate {
    private Object manager;
    private Object connection;
    private boolean recipes;
    private boolean tags;
    private long revision;
    private long quietAfter;
    private final int quietTicks;

    public CatalogSyncGate(int quietTicks) { this.quietTicks = quietTicks; }

    public boolean observe(Object manager, Object connection, long tick) {
        if (this.manager == manager && this.connection == connection) return false;
        // Events may arrive before the first ClientLevel. Preserve only the same
        // RecipeManager's received data when attaching its first play connection.
        boolean preserve = this.manager == manager && manager != null && this.connection == null;
        this.manager = manager;
        this.connection = connection;
        if (!preserve) { recipes = false; tags = false; }
        revision++;
        quietAfter = tick + quietTicks;
        return true;
    }

    public void recipes(Object manager, Object connection, long tick) {
        observe(manager, connection, tick);
        recipes = true;
        changed(tick);
    }

    public void tags(long tick) { tags = true; changed(tick); }
    private void changed(long tick) { revision++; quietAfter = tick + quietTicks; }

    public boolean ready(Object manager, Object connection, long tick, boolean runtimeReady) {
        return manager != null && connection != null && this.manager == manager
                && this.connection == connection && recipes && tags && runtimeReady && tick >= quietAfter;
    }

    public long revision() { return revision; }
    public String waitingFor(long tick, boolean runtimeReady) {
        if (!recipes) return "recipe-packet";
        if (!tags) return "tag-packet";
        if (!runtimeReady) return "jei-runtime";
        if (tick < quietAfter) return "sync-quiet-window";
        return "owner-tick";
    }
    public void reset(long tick) {
        manager = null; connection = null; recipes = false; tags = false;
        revision++; quietAfter = tick + quietTicks;
    }
}
