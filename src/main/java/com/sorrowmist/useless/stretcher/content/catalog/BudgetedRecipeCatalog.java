package com.sorrowmist.useless.stretcher.content.catalog;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeConversionUtils;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2cs.CrystalGrowthRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatLoader;
import com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture.SeedEssenceRecipeAdapter;
import com.sorrowmist.useless.init.ModRecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Cooperative owner-thread replacement for the native catalog's synchronous build.
 * Adapters, recipes, codecs, registries and publication never run on a worker. Only
 * detached string sorting does. A single third-party callback cannot be preempted;
 * its actual duration is retained in the per-adapter timings instead of being hidden.
 */
public final class BudgetedRecipeCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_BUDGET_NANOS = 8_000_000L;
    private static final int DEFAULT_STEPS = 32768;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_NANOS = 5_000_000_000L;
    private static final Map<Object, Build> BUILDS = new IdentityHashMap<>();
    private static final Map<Object, PendingRequest> DISPATCHED = new IdentityHashMap<>();
    private static final Map<Object, AtomicBoolean> EVIDENCE_TOKENS = new IdentityHashMap<>();
    private static long cancellationEpoch;

    private BudgetedRecipeCatalog() {}

    /** Queue only. Call on this level's owner thread after the recipe/tag readiness gate. */
    public static void request(Level level) {
        if (level == null) return;
        if (!isOwner(level)) { dispatchRequest(level); return; }
        Object manager = level.getRecipeManager();
        if (NativeCatalogAccess.ready(manager) != null) return;
        long generation = NativeCatalogAccess.generation(manager);
        synchronized (BUILDS) {
            Build old = BUILDS.get(manager);
            if (old != null && !old.cancelled && old.generation == generation) return;
            if (old != null) old.cancel();
            BUILDS.put(manager, new Build(level, manager, generation));
        }
    }

    /** Empty pending snapshots are never inserted into native CACHE, so isReady stays false. */
    public static Object snapshotOrSchedule(Level level) {
        return NativeAdapterGuards.guardLazy("alloy-furnace recipe catalog build",
                () -> snapshotOrScheduleUnchecked(level),
                () -> NativeCatalogAccess.empty(level == null ? 0 : NativeCatalogAccess.generation(level.getRecipeManager())));
    }

    private static Object snapshotOrScheduleUnchecked(Level level) {
        if (level == null) return NativeCatalogAccess.empty(0);
        Object ready = NativeCatalogAccess.ready(level.getRecipeManager());
        if (ready != null) return ready;
        request(level);
        Build build;
        synchronized (BUILDS) { build = BUILDS.get(level.getRecipeManager()); }
        return build == null ? NativeCatalogAccess.empty(NativeCatalogAccess.generation(level.getRecipeManager())) : build.emptySnapshot;
    }

    public static void tick(Level level) { tick(level, DEFAULT_BUDGET_NANOS, DEFAULT_STEPS); }

    /** Call once per owner tick for each recipe manager, normally overworld and current client level. */
    public static void tick(Level level, long budgetNanos, int maxSteps) {
        if (level == null || budgetNanos <= 0 || maxSteps <= 0) return;
        requireOwner(level);
        Object manager = level.getRecipeManager();
        Build build;
        synchronized (BUILDS) { build = BUILDS.get(manager); }
        if (build == null || build.cancelled) return;
        if (build.owner != Thread.currentThread()) throw new IllegalStateException("Catalog owner thread changed");
        if (NativeCatalogAccess.generation(manager) != build.generation) { cancel(level); return; }
        if (build.failed) {
            if (build.attempts >= MAX_ATTEMPTS || System.nanoTime() < build.retryAt) return;
            Build replacement = new Build(level, manager, build.generation);
            replacement.attempts = build.attempts + 1;
            synchronized (BUILDS) {
                if (BUILDS.get(manager) != build) return;
                BUILDS.put(manager, replacement);
            }
            build = replacement;
        }
        long start = System.nanoTime();
        try {
            for (int steps = 0; steps < maxSteps && !build.cancelled && System.nanoTime() - start < budgetNanos; steps++) {
                Stage before = build.stage;
                long operationStart = System.nanoTime();
                boolean progressed;
                try {
                    Build advancing = build;
                    Boolean result = NativeAdapterGuards.guard("alloy-furnace recipe catalog build", advancing::step, null);
                    if (result == null) throw new IllegalStateException("Native catalog guard skipped a failed step; see the preceding cause log");
                    progressed = result;
                }
                finally {
                    long elapsed = System.nanoTime() - operationStart;
                    build.phaseNanos.merge(before, elapsed, Long::sum);
                    build.maxOperationNanos = Math.max(build.maxOperationNanos, elapsed);
                }
                if (build.stage == Stage.DONE) {
                    synchronized (BUILDS) { if (BUILDS.get(manager) == build) BUILDS.remove(manager); }
                    build.maxTickNanos = Math.max(build.maxTickNanos, System.nanoTime() - start);
                    build.logTimings(build.completionResult);
                    break;
                }
                if (!progressed) break;
            }
        } catch (RuntimeException error) {
            if (build.cancelled) return;
            build.failed = true;
            build.failureMessage = error.toString();
            build.retryAt = System.nanoTime() + RETRY_DELAY_NANOS;
            build.logTimings("failed");
            LOGGER.error("Cooperative catalog failed: generation={}, stage={}, owner={}",
                    build.generation, build.stage, build.owner.getName(), error);
        } finally {
            long elapsed = System.nanoTime() - start;
            build.maxTickNanos = Math.max(build.maxTickNanos, elapsed);
            build.ticks++;
        }
    }

    /** Native invalidate must execute first; this only cancels the in-flight work. */
    public static void cancel(Level level) {
        if (level == null) { cancelAll(); return; }
        Object manager = level.getRecipeManager();
        synchronized (BUILDS) {
            DISPATCHED.remove(manager);
            AtomicBoolean evidence = EVIDENCE_TOKENS.remove(manager);
            if (evidence != null) evidence.set(false);
            Build old = BUILDS.remove(manager);
            if (old != null) old.cancel();
        }
    }

    public static void cancelAll() {
        synchronized (BUILDS) {
            cancellationEpoch++;
            DISPATCHED.clear();
            EVIDENCE_TOKENS.values().forEach(token -> token.set(false));
            EVIDENCE_TOKENS.clear();
            for (Build build : BUILDS.values()) build.cancel();
            BUILDS.clear();
        }
    }

    public static boolean pending(Level level) {
        if (level == null) return false;
        synchronized (BUILDS) {
            Build build = BUILDS.get(level.getRecipeManager());
            return DISPATCHED.containsKey(level.getRecipeManager()) || build != null && !build.cancelled
                    && (!build.failed || build.attempts < MAX_ATTEMPTS);
        }
    }

    /** A permanent failure is visible instead of leaving consumers in an infinite pending state. */
    public static String failure(Level level) {
        if (level == null) return "";
        synchronized (BUILDS) {
            Build build = BUILDS.get(level.getRecipeManager());
            return build != null && build.failed && build.attempts >= MAX_ATTEMPTS ? build.failureMessage : "";
        }
    }

    public record Status(String phase, long generation, int attempts, boolean retrying, String failure) {}

    public static Status status(Level level) {
        if (level == null) return new Status("idle", 0, 0, false, "");
        synchronized (BUILDS) {
            Build build = BUILDS.get(level.getRecipeManager());
            return build == null ? new Status("idle", 0, 0, false, "")
                    : new Status(build.stage.name(), build.generation, build.attempts,
                    build.failed && build.attempts < MAX_ATTEMPTS, build.failureMessage);
        }
    }

    private record PendingRequest(long generation, long epoch) {}

    private static void dispatchRequest(Level level) {
        Object manager = level.getRecipeManager();
        long generation = NativeCatalogAccess.generation(manager);
        PendingRequest request;
        synchronized (BUILDS) {
            PendingRequest old = DISPATCHED.get(manager);
            if (old != null && old.generation == generation && old.epoch == cancellationEpoch) return;
            request = new PendingRequest(generation, cancellationEpoch);
            DISPATCHED.put(manager, request);
        }
        WeakReference<Level> weakLevel = new WeakReference<>(level);
        try { executeOnOwner(level, () -> {
            synchronized (BUILDS) {
                if (DISPATCHED.get(manager) != request || request.epoch != cancellationEpoch) return;
                DISPATCHED.remove(manager);
            }
            Level target = weakLevel.get();
            if (target == null || NativeCatalogAccess.generation(manager) != request.generation) return;
            if (target.isClientSide) {
                Level current = ClientOwner.currentLevel();
                if (current == null || current.getRecipeManager() != manager) return;
                target = current;
            }
            request(target);
        }); } catch (RuntimeException rejected) {
            synchronized (BUILDS) { DISPATCHED.remove(manager, request); }
            LOGGER.warn("Catalog owner rejected queued request; no pending request retained: {}", rejected.toString());
        }
    }

    private static void requireOwner(Level level) {
        if (!isOwner(level)) throw new IllegalStateException("Catalog advancement called off owner thread");
    }

    private static boolean isOwner(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().isSameThread();
        } else if (level.isClientSide) {
            return ClientOwner.isSameThread();
        } else throw new IllegalStateException("Unsupported catalog level owner");
    }

    private static void executeOnOwner(Level level, Runnable operation) {
        if (level instanceof ServerLevel serverLevel) serverLevel.getServer().execute(operation);
        else if (level.isClientSide) ClientOwner.execute(operation);
        else throw new IllegalStateException("Unsupported catalog owner executor");
    }

    /** Resolve client-only classes lazily, never on a dedicated server. */
    private static final class ClientOwner {
        private static final Class<?> MINECRAFT;
        private static final Method INSTANCE, SAME_THREAD, EXECUTE;
        private static final java.lang.reflect.Field LEVEL;
        static {
            try {
                MINECRAFT = Class.forName("net.minecraft.client.Minecraft");
                INSTANCE = MINECRAFT.getMethod("getInstance");
                SAME_THREAD = MINECRAFT.getMethod("isSameThread");
                EXECUTE = MINECRAFT.getMethod("execute", Runnable.class);
                LEVEL = MINECRAFT.getField("level");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot establish client owner API", error); }
        }
        static boolean isSameThread() {
            try {
                return (boolean) SAME_THREAD.invoke(INSTANCE.invoke(null));
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot establish client owner thread", error); }
        }
        static void execute(Runnable operation) {
            try { EXECUTE.invoke(INSTANCE.invoke(null), operation); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot dispatch client catalog", error); }
        }
        static Level currentLevel() {
            try { return (Level) LEVEL.get(INSTANCE.invoke(null)); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read current client session", error); }
        }
    }

    private enum Stage { CAPTURE, CORE, ADAPTERS, CONVERTED, AE2LT, AE2LT_OUTPUT, FINGERPRINT, SORT_KEYS, SORT_WAIT, ORDERED, INDEX, FREEZE, PUBLISH, DONE }
    private record Collected(AdvancedAlloyFurnaceRecipe recipe, String source) {}
    private record Adapter(IRecipeAdapter<?> adapter, String source) {}
    private static final class Timing {
        long calls, nanos, maximum;
        void add(long elapsed) { calls++; nanos += elapsed; maximum = Math.max(maximum, elapsed); }
    }

    private static final class Build {
        final Level level;
        final Object manager;
        final long generation;
        final Thread owner = Thread.currentThread();
        final long started = System.nanoTime();
        final Object emptySnapshot;
        final Path tupleDirectory = tupleDirectory();
        final CatalogTupleEvidence.Accumulator tupleEvidence = new CatalogTupleEvidence.Accumulator(tupleDirectory != null);
        final AtomicBoolean evidenceCurrent = new AtomicBoolean(true);
        final Map<Stage, Long> phaseNanos = new java.util.EnumMap<>(Stage.class);
        final Map<String, Timing> adapterNanos = new LinkedHashMap<>();
        final List<Adapter> converting = new ArrayList<>();
        final Map<Class<?>, List<Adapter>> resolved = new HashMap<>();
        final LinkedHashMap<AlloyFurnaceRecipeIdentity, AlloyFurnaceRecipeCatalog.Entry> unique = new LinkedHashMap<>();
        final Map<ResourceLocation, List<AlloyFurnaceRecipeCatalog.Entry>> byId = new LinkedHashMap<>();
        final Map<String, List<AlloyFurnaceRecipeCatalog.Entry>> bySource = new LinkedHashMap<>();
        final Map<Object, List<AlloyFurnaceRecipeCatalog.Entry>> bySourceAndId = new LinkedHashMap<>();
        List<Collected> collected = new ArrayList<>();
        List<AlloyFurnaceRecipeCatalog.Entry> unsorted = new ArrayList<>();
        final List<AlloyFurnaceRecipeCatalog.Entry> ordered = new ArrayList<>();
        Iterator<? extends RecipeHolder<?>> core = Collections.emptyIterator();
        Iterator<IRecipeAdapter<?>> adapters = Collections.emptyIterator();
        Iterator<? extends RecipeHolder<?>> generated = Collections.emptyIterator();
        Iterator<AdvancedAlloyFurnaceRecipe> outputs = Collections.emptyIterator();
        GeneratedRecipeSlices.Cursor generatedSlice;
        SlicedRecipeConversions.Cursor conversionSlice;
        List<AdvancedAlloyFurnaceRecipe> conversionBuffer;
        Iterator<? extends RecipeHolder<?>> holders = Collections.emptyIterator();
        Iterator<Adapter> matches = Collections.emptyIterator();
        Iterator<AlloyFurnaceRecipeCatalog.Entry> uniqueEntries;
        Iterator<? extends Map.Entry<?, List<AlloyFurnaceRecipeCatalog.Entry>>> freezeEntries;
        Adapter active;
        RecipeHolder<?> holder;
        CatalogSortWork.Key[] sortKeys;
        CompletableFuture<int[]> sorting;
        int[] positions;
        int cursor, freezePhase, generatedStart = -1;
        long ticks, maxTickNanos, maxOperationNanos, sortStarted, sortNanos, retryAt;
        volatile Stage stage = Stage.CAPTURE;
        volatile boolean cancelled;
        volatile boolean failed;
        volatile int attempts = 1;
        volatile String failureMessage = "";
        String completionResult;

        Build(Level level, Object manager, long generation) {
            this.level = level; this.manager = manager; this.generation = generation;
            emptySnapshot = NativeCatalogAccess.empty(generation);
            LOGGER.info("Cooperative catalog queued: generation={}, owner={}", generation, owner.getName());
        }

        boolean step() {
            switch (stage) {
                case CAPTURE -> {
                    core = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ADVANCED_ALLOY_FURNACE_TYPE.get()).iterator();
                    adapters = List.copyOf(AlloyFurnaceRecipeManager.getInstance().getRegisteredAdapters()).iterator();
                    stage = Stage.CORE;
                }
                case CORE -> {
                    if (core.hasNext()) collected.add(new Collected((AdvancedAlloyFurnaceRecipe) core.next().value(), "core"));
                    else { core = Collections.emptyIterator(); stage = Stage.ADAPTERS; }
                }
                case ADAPTERS -> adapterStep();
                case CONVERTED -> convertedStep();
                case AE2LT -> {
                    if (RecipeAdapterCompatRegistry.isLoaded(RecipeAdapterCompatRegistry.AE2LT)) {
                        active = new Adapter(null, "ae2lt");
                        outputs = NativeAdapterGuards.guard("AE2LT JEI recipes", () ->
                                timed("ae2lt.getJeiRecipes", () -> AELightningTechCompatLoader
                                        .getJeiRecipes(level.getRecipeManager(), level)), List.<AdvancedAlloyFurnaceRecipe>of()).iterator();
                    }
                    stage = Stage.AE2LT_OUTPUT;
                }
                case AE2LT_OUTPUT -> {
                    // Lightning recipes are last in the native encounter order too.
                    if (outputs.hasNext()) collected.add(new Collected(outputs.next(), "ae2lt"));
                    else stage = Stage.FINGERPRINT;
                }
                case FINGERPRINT -> {
                    if (cursor < collected.size()) {
                        Collected item = collected.set(cursor++, null);
                        AlloyFurnaceRecipeIdentity identity = NativeCatalogAccess.fingerprint(item.recipe, item.source, level);
                        if (identity != null) unique.putIfAbsent(identity,
                                new AlloyFurnaceRecipeCatalog.Entry(identity, item.recipe, item.source));
                    } else {
                        collected = null;
                        uniqueEntries = unique.values().iterator();
                        sortKeys = new CatalogSortWork.Key[unique.size()];
                        cursor = 0;
                        stage = Stage.SORT_KEYS;
                    }
                }
                case SORT_KEYS -> {
                    if (uniqueEntries.hasNext()) {
                        AlloyFurnaceRecipeCatalog.Entry entry = uniqueEntries.next();
                        unsorted.add(entry);
                        sortKeys[cursor] = new CatalogSortWork.Key(entry.identity().recipeId().toString(), entry.identity().fingerprint(), cursor++);
                    } else {
                        sortStarted = System.nanoTime();
                        sorting = CatalogSortWork.submit(sortKeys);
                        sortKeys = null;
                        stage = Stage.SORT_WAIT;
                    }
                }
                case SORT_WAIT -> {
                    if (!sorting.isDone()) return false;
                    positions = sorting.getNow(null); // Already complete: never blocks or waits.
                    sortNanos = System.nanoTime() - sortStarted;
                    cursor = 0;
                    stage = Stage.ORDERED;
                }
                case ORDERED -> {
                    if (cursor < positions.length) ordered.add(unsorted.get(positions[cursor++]));
                    else { unsorted = null; positions = null; cursor = 0; stage = Stage.INDEX; }
                }
                case INDEX -> {
                    if (cursor < ordered.size()) {
                        AlloyFurnaceRecipeCatalog.Entry entry = ordered.get(cursor++);
                        tupleEvidence.append(entry);
                        byId.computeIfAbsent(entry.identity().recipeId(), ignored -> new ArrayList<>()).add(entry);
                        bySource.computeIfAbsent(entry.sourceId(), ignored -> new ArrayList<>()).add(entry);
                        bySourceAndId.computeIfAbsent(NativeCatalogAccess.sourceKey(entry.sourceId(), entry.identity().recipeId()),
                                ignored -> new ArrayList<>()).add(entry);
                    } else { freezeEntries = byId.entrySet().iterator(); stage = Stage.FREEZE; }
                }
                case FREEZE -> {
                    if (freezeEntries.hasNext()) {
                        var entry = freezeEntries.next();
                        entry.setValue(Collections.unmodifiableList(entry.getValue()));
                    } else if (freezePhase++ == 0) freezeEntries = bySource.entrySet().iterator();
                    else if (freezePhase == 2) freezeEntries = bySourceAndId.entrySet().iterator();
                    else stage = Stage.PUBLISH;
                }
                case PUBLISH -> {
                    CatalogTupleEvidence.Completed evidence = tupleEvidence.finish();
                    Object snapshot = NativeCatalogAccess.snapshot(ordered, unique, byId, bySource, bySourceAndId, generation);
                    boolean published = !cancelled && NativeCatalogAccess.publish(manager, generation, snapshot, ordered.size());
                    if (published) {
                        synchronized (BUILDS) {
                            AtomicBoolean previous = EVIDENCE_TOKENS.put(manager, evidenceCurrent);
                            if (previous != null) previous.set(false);
                        }
                        LOGGER.info("Cooperative catalog ordered evidence: generation={}, owner={}, {}", generation, owner.getName(), evidence.summary().logLine());
                        if (tupleDirectory != null) {
                            Path destination = tupleDirectory.resolve("catalog-" + generation + "-" + java.util.UUID.randomUUID() + ".tuples");
                            CatalogTupleEvidence.exportAsync(evidence, destination, evidenceCurrent::get)
                                    .whenComplete((path, error) -> {
                                        if (error == null) LOGGER.info("Catalog ordered tuple export: {}", path);
                                        else LOGGER.warn("Catalog tuple export not published: {}", error.toString());
                                    });
                        }
                    }
                    stage = Stage.DONE;
                    completionResult = published ? "published" : "stale-discarded";
                }
                case DONE -> { return false; }
            }
            return true;
        }

        private void adapterStep() {
            if (outputs.hasNext()) { collected.add(new Collected(outputs.next(), active.source)); return; }
            if (conversionSlice != null) { conversionStep(); return; }
            if (generated.hasNext()) {
                startConversion(generated.next());
                return;
            }
            if (generatedSlice != null) {
                try {
                    RecipeHolder<?> next = NativeAdapterGuards.call(active.adapter, "getGeneratedRecipes", () -> {
                        if (generatedSlice.hasOutput()) return generatedSlice.poll();
                        if (generatedSlice.done()) {
                            LOGGER.info("Cooperative generated slices: source={}, pairs={}, steps={}, outputs={}",
                                    active.source, generatedSlice.attemptedPairs(), generatedSlice.steps(), generatedSlice.emitted());
                            generatedSlice = null;
                            generatedStart = -1;
                        } else timed(label(active, "generated-slice:" + generatedSlice.phase()), generatedSlice::advance);
                        return null;
                    }, null);
                    if (NativeAdapterGuards.isDisabled(active.adapter, "getGeneratedRecipes")) discardGenerated();
                    // Conversion is a distinct action: never attribute its failure to generation.
                    else if (next != null) startConversion(next);
                } catch (RuntimeException error) {
                    // getGeneratedRecipes is atomic in the native contract. A late helper
                    // failure must discard this adapter's earlier generated conversions too.
                    discardGenerated();
                    LOGGER.warn("Skipping generated recipes: adapter={}", active.adapter.getClass().getName(), error);
                }
                return;
            }
            if (!adapters.hasNext()) {
                holders = level.getRecipeManager().getRecipes().iterator();
                active = null;
                stage = Stage.CONVERTED;
                return;
            }
            IRecipeAdapter<?> adapter = adapters.next();
            if (adapter.getClass().getPackageName().contains(".ae.ae2lt")) return;
            active = new Adapter(adapter, AlloyFurnaceRecipeManager.getInstance().getAdapterSourceId(adapter));
            NativeAdapterGuards.rememberSource(adapter, active.source);
            if (adapter instanceof SeedEssenceRecipeAdapter synthetic) {
                List<AdvancedAlloyFurnaceRecipe> values = NativeAdapterGuards.call(adapter, "getAllRecipes",
                        () -> timed(label(active, "getAllRecipes"), synthetic::getAllRecipes), null);
                outputs = values == null ? Collections.emptyIterator() : values.iterator();
            } else if (adapter instanceof CrystalGrowthRecipeAdapter synthetic) {
                List<AdvancedAlloyFurnaceRecipe> values = NativeAdapterGuards.call(adapter, "getAllRecipes",
                        () -> timed(label(active, "getAllRecipes"), synthetic::getAllRecipes), null);
                outputs = values == null ? Collections.emptyIterator() : values.iterator();
            } else {
                try {
                    boolean prepared = NativeAdapterGuards.call(adapter, "getGeneratedRecipes", () -> {
                        generatedSlice = GeneratedRecipeSlices.tryCreate(adapter, level);
                        if (generatedSlice != null) generatedStart = collected.size();
                        if (generatedSlice == null) {
                            List<? extends RecipeHolder<?>> values = timed(label(active, "getGeneratedRecipes"), () -> adapter.getGeneratedRecipes(level));
                            generated = values == null ? Collections.emptyIterator() : values.iterator();
                        }
                        return true;
                    }, false);
                    if (!prepared) discardGenerated();
                } catch (RuntimeException error) {
                    generated = Collections.emptyIterator();
                    LOGGER.warn("Skipping generated recipes: adapter={}", adapter.getClass().getName(), error);
                }
                converting.add(active);
            }
        }

        private void convertedStep() {
            if (outputs.hasNext()) { collected.add(new Collected(outputs.next(), active.source)); return; }
            if (conversionSlice != null) { conversionStep(); return; }
            if (matches.hasNext()) { active = matches.next(); startConversion(holder); return; }
            if (!holders.hasNext()) { holders = Collections.emptyIterator(); holder = null; stage = Stage.AE2LT; return; }
            holder = holders.next();
            Object value = holder.value();
            if (value == null) return;
            matches = resolved.computeIfAbsent(value.getClass(), type -> {
                List<Adapter> result = new ArrayList<>();
                for (Adapter candidate : converting) {
                    Class<?> declared = NativeAdapterGuards.call(candidate.adapter, "getRecipeClass",
                            () -> timed(label(candidate, "getRecipeClass"), candidate.adapter::getRecipeClass), null);
                    if (declared != null && declared.isAssignableFrom(type)) result.add(candidate);
                }
                return List.copyOf(result);
            }).iterator();
        }

        private void startConversion(RecipeHolder<?> value) {
            boolean prepared = NativeAdapterGuards.callEach(active.adapter, "convertAll", () -> {
                conversionSlice = SlicedRecipeConversions.tryCreate(active.adapter, value, level);
                if (conversionSlice == null) outputs = convert(active, value).iterator();
                else conversionBuffer = new ArrayList<>();
                return true;
            }, false);
            if (!prepared) discardConversion();
        }

        private void conversionStep() {
            try {
                Boolean done = NativeAdapterGuards.callEach(active.adapter, "convertAll", () ->
                        timed(label(active, "conversion-slice:" + conversionSlice.phase()), () -> conversionSlice.advance(conversionBuffer::add)), null);
                if (done == null) discardConversion();
                else if (done) {
                    outputs = conversionBuffer.iterator();
                    conversionBuffer = null;
                    conversionSlice = null;
                }
            } catch (RuntimeException error) {
                // Native RecipeConversionUtils discards the entire conversion on failure,
                // including outputs produced before a later variant throws.
                discardConversion();
                LOGGER.warn("Skipping failed sliced recipe conversion: adapter={}", active.adapter.getClass().getName(), error);
            }
        }

        private List<AdvancedAlloyFurnaceRecipe> convert(Adapter adapter, RecipeHolder<?> value) {
            return timed(label(adapter, "convertAll"), () -> RecipeConversionUtils.convertAll(adapter.adapter, value, level));
        }

        private void discardGenerated() {
            if (generatedStart >= 0) collected.subList(generatedStart, collected.size()).clear();
            generatedSlice = null;
            generatedStart = -1;
            generated = Collections.emptyIterator();
            discardConversion();
        }

        private void discardConversion() {
            conversionBuffer = null;
            conversionSlice = null;
            outputs = Collections.emptyIterator();
        }

        private String label(Adapter adapter, String operation) { return adapter.source + ":" + adapter.adapter.getClass().getName() + ":" + operation; }

        private <T> T timed(String name, Supplier<T> operation) {
            long start = System.nanoTime();
            try { return operation.get(); }
            finally { adapterNanos.computeIfAbsent(name, ignored -> new Timing()).add(System.nanoTime() - start); }
        }

        void cancel() {
            if (cancelled) return;
            cancelled = true;
            evidenceCurrent.set(false);
            if (sorting != null) sorting.cancel(false);
            if (Thread.currentThread() == owner) logTimings("cancelled");
            else {
                try { executeOnOwner(level, () -> logTimings("cancelled")); }
                catch (RuntimeException rejected) {
                    // The owner may already have stopped. Do not inspect its mutable timing maps here.
                    LOGGER.info("Cooperative catalog cancelled after owner stopped: generation={}, owner={}",
                            generation, owner.getName());
                }
            }
        }

        private static Path tupleDirectory() {
            String value = System.getProperty("useless_stretcher.catalogTupleDirectory", "");
            if (value.isBlank()) return null;
            try {
                Path path = Path.of(value).normalize();
                if (path.isAbsolute() && path.getRoot().toString().equalsIgnoreCase("D:\\")) return path;
            } catch (RuntimeException ignored) { }
            LOGGER.warn("Catalog tuple export requires an absolute D drive directory; export disabled");
            return null;
        }

        void logTimings(String result) {
            LOGGER.info("Cooperative catalog {}: generation={}, recipes={}, sources={}, wall_ms={}, ticks={}, max_tick_ms={}, owner={}",
                    result, generation, ordered.size(), bySource.size(), (System.nanoTime() - started) / 1_000_000L,
                    ticks, maxTickNanos / 1_000_000.0, owner.getName());
            phaseNanos.forEach((phase, nanos) -> LOGGER.info("Cooperative catalog phase: generation={}, phase={}, active_ms={}", generation, phase, nanos / 1_000_000.0));
            LOGGER.info("Cooperative catalog detached sorting: generation={}, wall_ms={}", generation, sortNanos / 1_000_000.0);
            LOGGER.info("Cooperative catalog atomic operation: generation={}, max_operation_ms={}", generation, maxOperationNanos / 1_000_000.0);
            adapterNanos.forEach((name, timing) -> LOGGER.info("Cooperative catalog adapter: generation={}, operation={}, calls={}, active_ms={}, max_callback_ms={}",
                    generation, name, timing.calls, timing.nanos / 1_000_000.0, timing.maximum / 1_000_000.0));
        }
    }
}
