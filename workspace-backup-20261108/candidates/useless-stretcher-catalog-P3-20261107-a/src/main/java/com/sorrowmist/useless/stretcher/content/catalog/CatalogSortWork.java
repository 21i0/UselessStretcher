package com.sorrowmist.useless.stretcher.content.catalog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The only worker input is detached immutable strings and integer positions. */
final class CatalogSortWork {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "useless-stretcher-catalog-sort");
        thread.setDaemon(true);
        return thread;
    });

    record Key(String recipeId, String fingerprint, int ordinal) {}

    private CatalogSortWork() {}

    /** Ownership of keys transfers to this task; callers must not edit it afterward. */
    static CompletableFuture<int[]> submit(Key[] keys) {
        return CompletableFuture.supplyAsync(() -> sort(keys), WORKER);
    }

    static int[] sort(Key[] keys) {
        Arrays.sort(keys, Comparator.comparing(Key::recipeId).thenComparing(Key::fingerprint)
                .thenComparingInt(Key::ordinal));
        int[] positions = new int[keys.length];
        for (int i = 0; i < keys.length; i++) positions[i] = keys[i].ordinal();
        return positions;
    }
}
