package com.sorrowmist.useless.stretcher.client;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory;
import com.sorrowmist.useless.compat.jei.JEIPlugin;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI 19.57 owner-thread publication; the outer compatibility guard selects this implementation.
 * The matching lookup and screen mixins return pending/empty results until the final commit.
 * Category hide/unhide is deliberately unused: JEI's global hide set has no caller ownership.
 */
public final class CatalogJeiPublication {
    private static final long FRAME_NANOS = 4_000_000L;
    private static final int FRAME_STEPS = 8192;
    private static final int BATCH_LIMIT = 16;
    private static final String RECIPE_SCREEN = "mezz.jei.gui.recipes.RecipesGui";
    private static final Set<AlloyFurnaceRecipeIdentity> VISIBLE = new HashSet<>();
    private static final AtomicBoolean REQUEST_QUEUED = new AtomicBoolean();

    private static Field registeredField;
    private static IJeiRuntime runtime;
    private static IRecipeManager recipeManager;
    private static Map<AlloyFurnaceRecipeIdentity, Entry> known;
    private static Iterator<AlloyFurnaceRecipeIdentity> seedVisible;
    private static Object requestedManager;
    private static List<Entry> requestedEntries;
    private static OwnerRecipePublication<AlloyFurnaceRecipeIdentity, Entry> task;
    private static long epoch;
    private static long maximumFrameNanos;
    private static long publicationStarted;
    private static boolean completionLogged;
    private static boolean unsafeFailure;
    private static WeakReference<IJeiRuntime> failedRuntime = new WeakReference<>(null);
    private static String failure = "";

    // Off-owner consumers only read these detached status snapshots.
    private static volatile boolean blocking;
    private static volatile boolean complete;
    private static volatile String progress = "JEI waiting: runtime";

    private CatalogJeiPublication() {}

    /** Called for registration reset or runtime teardown; the native registration owns map clearing. */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(CatalogJeiPublication::reset); return; }
        cancelTask();
        blocking = true;
        complete = false;
        try { closeRecipeScreen(mc); }
        catch (RuntimeException error) { stop(runtime, error); }
        runtime = null;
        recipeManager = null;
        known = null;
        seedVisible = null;
        requestedEntries = null;
        requestedManager = null;
        VISIBLE.clear();
        maximumFrameNanos = 0;
        completionLogged = false;
        // Keep the failed runtime identity across a registration reset. Only its replacement is safe.
        // registerRecipes may reset the ledger while the same runtime is still installed.
        // onRuntimeUnavailable is hooked after the native runtime has become null.
        blocking = JEIPlugin.getRuntime() != null;
        progress = unsafeFailure ? "JEI stopped: " + failure : "JEI waiting: runtime";
    }

    public static void runtimeAvailable() { request(); }

    /** Cancels this revision but retains every normally returned batch's canonical Entry objects. */
    public static void invalidate() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(CatalogJeiPublication::invalidate); return; }
        cancelTask();
        requestedEntries = null;
        requestedManager = null;
        complete = false;
        blocking = runtime != null;
        progress = unsafeFailure ? "JEI stopped: " + failure : "JEI pending: waiting for catalog";
        try { closeRecipeScreen(mc); }
        catch (RuntimeException error) { stop(runtime, error); }
    }

    /** Off-owner callers only queue owner work and never inspect the level, runtime, or entries. */
    public static void request() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            if (REQUEST_QUEUED.compareAndSet(false, true)) mc.execute(() -> {
                REQUEST_QUEUED.set(false);
                request();
            });
            return;
        }
        IJeiRuntime current = null;
        try {
            current = JEIPlugin.getRuntime();
            if (current == null) {
                if (runtime != null) reset();
                return;
            }
            if (runtime != current) observeRuntime(current);
            if (unsafeFailure) { blocking = true; complete = false; return; }
            if (mc.level == null || !AlloyFurnaceRecipeCatalog.isReady(mc.level)) {
                if (task != null) {
                    cancelTask();
                    requestedManager = null;
                    requestedEntries = null;
                }
                blocking = true;
                complete = false;
                progress = "JEI pending: waiting for catalog";
                closeRecipeScreen(mc);
                return;
            }
            Object manager = mc.level.getRecipeManager();
            List<Entry> entries = AlloyFurnaceRecipeCatalog.entries(mc.level);
            if (manager == requestedManager && entries == requestedEntries && task != null) return;

            cancelTask();
            requestedManager = manager;
            requestedEntries = entries;
            publicationStarted = System.nanoTime();
            complete = false;
            blocking = true;
            completionLogged = false;
            closeRecipeScreen(mc);
            long revision = epoch;
            IJeiRuntime targetRuntime = runtime;
            IRecipeManager targetManager = recipeManager;
            task = new OwnerRecipePublication<>(entries, known, VISIBLE, Entry::identity,
                    new OwnerRecipePublication.Sink<>() {
                        private void check() {
                            if (epoch != revision || runtime != targetRuntime
                                    || JEIPlugin.getRuntime() != targetRuntime
                                    || mc.level == null || mc.level.getRecipeManager() != manager
                                    || !AlloyFurnaceRecipeCatalog.isReady(mc.level)
                                    || AlloyFurnaceRecipeCatalog.entries(mc.level) != entries)
                                throw new StaleRevision();
                        }
                        @Override public void add(List<Entry> batch) {
                            check();
                            targetManager.addRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, batch);
                            if (JEIPlugin.getRuntime() != targetRuntime) throw new StaleRevision();
                        }
                        @Override public void hide(List<Entry> batch) {
                            check();
                            targetManager.hideRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, batch);
                            if (JEIPlugin.getRuntime() != targetRuntime) throw new StaleRevision();
                        }
                        @Override public void unhide(List<Entry> batch) {
                            check();
                            targetManager.unhideRecipes(AdvancedAlloyFurnaceRecipeCategory.TYPE, batch);
                            if (JEIPlugin.getRuntime() != targetRuntime) throw new StaleRevision();
                        }
                        @Override public void publish() {
                            check();
                            complete = true;
                            blocking = false;
                        }
                    });
            updateProgress();
        } catch (RuntimeException error) { stop(current != null ? current : runtime, error); }
    }

    /** Called once per rendered frame. The budget also includes the initial native-ledger scan. */
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(CatalogJeiPublication::frame); return; }
        long start = System.nanoTime();
        try {
            request();
            if (runtime == null || unsafeFailure || task == null || task.terminal()) return;
            int steps = 0;
            while (seedVisible != null && steps < FRAME_STEPS && System.nanoTime() - start < FRAME_NANOS) {
                if (!seedVisible.hasNext()) { seedVisible = null; break; }
                VISIBLE.add(seedVisible.next());
                steps++;
            }
            if (seedVisible == null) {
                long remaining = FRAME_NANOS - (System.nanoTime() - start);
                var advancing = task;
                advancing.advance(remaining, FRAME_STEPS - steps, BATCH_LIMIT);
                if (advancing != task) return; // A third-party callback can re-enter invalidation.
            }
            if (task.failure() instanceof StaleRevision) { invalidate(); return; }
            if (task.failure() != null) { stop(runtime, task.failure()); return; }
            if (task.ready() && !completionLogged) {
                completionLogged = true;
                LogUtils.getLogger().info(
                        "Stretcher JEI publication complete: epoch={}, planned={}, submitted={}, hidden={}, unhidden={}, calls={}, max_call_ms={}, max_slice_ms={}, wall_ms={}, max_frame_ms={}, thread={}",
                        epoch, task.planned(), task.added(), task.hidden(), task.unhidden(), task.calls(),
                        task.maximumCallNanos() / 1_000_000.0, task.maximumSliceNanos() / 1_000_000.0,
                        (System.nanoTime() - publicationStarted) / 1_000_000.0,
                        Math.max(maximumFrameNanos, System.nanoTime() - start) / 1_000_000.0,
                        Thread.currentThread().getName());
            }
        } catch (RuntimeException error) { stop(runtime, error); }
        finally {
            maximumFrameNanos = Math.max(maximumFrameNanos, System.nanoTime() - start);
            updateProgress();
        }
    }

    public static boolean blocked() { return blocking; }
    public static boolean ready() { return complete; }
    public static String progress() { return progress; }

    public static boolean isTargetRecipeType(Object type) {
        return AdvancedAlloyFurnaceRecipeCategory.TYPE.equals(type);
    }

    public static boolean isRecipeScreen(Object screen) {
        if (screen == null) return false;
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals(RECIPE_SCREEN)) return true;
        return false;
    }

    private static void observeRuntime(IJeiRuntime current) {
        cancelTask();
        runtime = current;
        recipeManager = current.getRecipeManager();
        known = nativeLedger();
        VISIBLE.clear();
        seedVisible = known.keySet().iterator();
        requestedManager = null;
        requestedEntries = null;
        complete = false;
        blocking = true;
        maximumFrameNanos = 0;
        completionLogged = false;
        if (!unsafeFailure || failedRuntime.get() != current) {
            unsafeFailure = false;
            failedRuntime.clear();
            failure = "";
        }
        closeRecipeScreen(Minecraft.getInstance());
    }

    @SuppressWarnings("unchecked")
    private static Map<AlloyFurnaceRecipeIdentity, Entry> nativeLedger() {
        try {
            if (registeredField == null) {
                Field field = JEIPlugin.class.getDeclaredField("registeredAlloyFurnaceRecipes");
                if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType()))
                    throw new IllegalStateException("Verified native JEI ledger ABI changed");
                field.setAccessible(true);
                registeredField = field;
            }
            Object value = registeredField.get(null);
            if (!(value instanceof Map<?, ?>)) throw new IllegalStateException("Native JEI ledger is unavailable");
            return (Map<AlloyFurnaceRecipeIdentity, Entry>) value;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Verified native JEI ledger ABI changed", error);
        }
    }

    private static void cancelTask() {
        epoch++;
        if (task != null) task.cancel();
        task = null;
    }

    private static void closeRecipeScreen(Minecraft mc) {
        Screen screen = mc.screen;
        if (!isRecipeScreen(screen)) return;
        Screen parent = runtime == null ? null : runtime.getRecipesGui().getParentScreen().orElse(null);
        if (parent == screen || isRecipeScreen(parent)) parent = null;
        screen.onClose();
        if (mc.screen == screen) mc.setScreen(parent);
    }

    private static void stop(IJeiRuntime failed, RuntimeException error) {
        // An external add/hide/unhide may mutate before throwing. Never replay in this runtime.
        if (unsafeFailure && failedRuntime.get() == failed) return;
        unsafeFailure = true;
        failedRuntime = new WeakReference<>(failed);
        failure = error.toString();
        blocking = true;
        complete = false;
        progress = "JEI stopped: " + failure;
        if (task != null && !task.terminal()) task.cancel();
        LogUtils.getLogger().error(
                "Stretcher JEI publication stopped without replay: epoch={}, phase={}, submitted={}, thread={}",
                epoch, task == null ? "setup" : task.phase(), task == null ? 0 : task.added(),
                Thread.currentThread().getName(), error);
    }

    private static void updateProgress() {
        if (unsafeFailure) { progress = "JEI stopped: " + failure; return; }
        if (runtime == null || task == null) return;
        progress = "JEI " + (complete ? "ready" : "pending") + ": "
                + (seedVisible != null ? "LEDGER" : task.phase())
                + ", planned=" + task.planned() + "/" + task.total()
                + ", submitted=" + task.added() + ", hidden=" + task.hidden()
                + ", unhidden=" + task.unhidden() + ", max_frame_ms=" + maximumFrameNanos / 1_000_000.0;
    }

    /** Guard failed before a call, or its entire JEI runtime has been replaced. */
    private static final class StaleRevision extends IllegalStateException {
        StaleRevision() { super("JEI publication revision changed before callback"); }
    }
}
