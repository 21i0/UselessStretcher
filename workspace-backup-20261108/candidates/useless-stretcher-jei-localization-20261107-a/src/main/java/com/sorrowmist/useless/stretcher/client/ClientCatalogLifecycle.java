package com.sorrowmist.useless.stretcher.client;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.catalog.BudgetedRecipeCatalog;
import com.sorrowmist.useless.stretcher.content.catalog.CatalogPatchSupport;
import com.sorrowmist.useless.stretcher.content.catalog.CatalogSyncGate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** All callbacks run on the client owner thread; no world or JEI calls go to a worker. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class ClientCatalogLifecycle {
    private static final CatalogSyncGate GATE = new CatalogSyncGate(5);
    private static long tick;
    private static long requestedRevision = -1;
    private static long refreshedRevision = -1;
    private static Object notifiedEntries;
    private static Level observed;
    private static boolean pendingConfigurationTags;
    private static boolean jeiReady;
    private static String lastWait = "";
    private static String lastFailure = "";

    private ClientCatalogLifecycle() {}

    @SubscribeEvent public static void recipes(RecipesUpdatedEvent event) {
        if (!CatalogPatchSupport.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        GATE.recipes(event.getRecipeManager(), mc.getConnection(), tick);
        if (pendingConfigurationTags) { GATE.tags(tick); pendingConfigurationTags = false; }
        invalidate(mc.level, "recipes-updated");
    }

    @SubscribeEvent public static void tags(TagsUpdatedEvent event) {
        if (!CatalogPatchSupport.enabled()
                || event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) pendingConfigurationTags = true;
        else {
            GATE.observe(mc.getConnection().getRecipeManager(), mc.getConnection(), tick);
            GATE.tags(tick);
        }
        invalidate(mc.level, "tags-updated");
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!CatalogPatchSupport.enabled()) return;
        if (observed != null) { BudgetedRecipeCatalog.cancel(observed); AlloyFurnaceRecipeCatalog.invalidate(observed); }
        if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.invalidate();
        GATE.reset(tick);
        observed = null;
        pendingConfigurationTags = false;
        requestedRevision = refreshedRevision = -1;
        notifiedEntries = null;
        lastWait = "";
        lastFailure = "";
    }

    private static void invalidate(Level level, String reason) {
        if (level != null) AlloyFurnaceRecipeCatalog.invalidate(level);
        if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.invalidate();
        requestedRevision = refreshedRevision = -1;
        notifiedEntries = null;
        LogUtils.getLogger().info("Stretcher catalog sync: reason={}, revision={}, thread={}",
                reason, GATE.revision(), Thread.currentThread().getName());
    }

    public static void runtimeAvailable() { jeiReady = true; }
    public static void runtimeUnavailable() { jeiReady = false; refreshedRevision = -1; }

    /** The client advances once per rendered frame, never again in ClientTick. */
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!CatalogPatchSupport.enabled() || requestedRevision < 0 || requestedRevision != GATE.revision()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || observed == null || mc.level.getRecipeManager() != observed.getRecipeManager()) return;
        if (!AlloyFurnaceRecipeCatalog.isReady(mc.level)) BudgetedRecipeCatalog.tick(mc.level, 4_000_000L, 32768);
        else if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.frame();
    }

    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if (CatalogPatchSupport.jeiPublicationEnabled() && CatalogJeiPublication.blocked()
                && CatalogJeiPublication.isRecipeScreen(event.getNewScreen())) event.setCanceled(true);
    }

    @SubscribeEvent public static void progress(RenderGuiEvent.Post event) {
        if (!CatalogPatchSupport.jeiPublicationEnabled() || !CatalogJeiPublication.blocked()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Component text = CatalogJeiPublication.progressComponent();
        int width = Math.min(mc.getWindow().getGuiScaledWidth() - 12, mc.font.width(text) + 12);
        event.getGuiGraphics().fill(6, 6, width + 6, 24, 0xB0000000);
        event.getGuiGraphics().drawString(mc.font, text, 12, 11, 0xFFFFFF);
    }

    /** Replaces the base's synchronous prewarm, preserving its ready callback once per final revision. */
    public static void tick() {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.getConnection() == null) return;
        boolean sessionChanged = GATE.observe(level.getRecipeManager(), mc.getConnection(), tick);
        if (observed != null && observed.getRecipeManager() != level.getRecipeManager())
            BudgetedRecipeCatalog.cancel(observed);
        observed = level;
        if (sessionChanged) invalidate(level, "recipe-manager-or-connection");
        boolean runtime = !ModList.get().isLoaded("jei") || jeiReady;
        if (!GATE.ready(level.getRecipeManager(), mc.getConnection(), tick, runtime)) {
            String reason = GATE.waitingFor(tick, runtime);
            if (!reason.equals(lastWait)) LogUtils.getLogger().info(
                    "Stretcher catalog waiting: reason={}, revision={}", reason, GATE.revision());
            lastWait = reason;
            return;
        }
        lastWait = "";
        if (requestedRevision != GATE.revision()) {
            BudgetedRecipeCatalog.request(level);
            requestedRevision = GATE.revision();
            LogUtils.getLogger().info("Stretcher catalog sync settled: revision={}, thread={}",
                    requestedRevision, Thread.currentThread().getName());
        }
        // An independent native invalidation may occur without a new sync packet.
        if (!AlloyFurnaceRecipeCatalog.isReady(level) && !BudgetedRecipeCatalog.pending(level)
                && BudgetedRecipeCatalog.failure(level).isEmpty()) {
            BudgetedRecipeCatalog.request(level);
            refreshedRevision = -1;
        }
        String failure = BudgetedRecipeCatalog.failure(level);
        if (!failure.equals(lastFailure)) {
            lastFailure = failure;
            if (!failure.isEmpty()) LogUtils.getLogger().error(
                    "Stretcher catalog stopped after bounded retries: revision={}, failure={}", requestedRevision, failure);
        }
        if (AlloyFurnaceRecipeCatalog.isReady(level)
                && (refreshedRevision != requestedRevision || notifiedEntries != AlloyFurnaceRecipeCatalog.entries(level))) {
            if (CatalogPatchSupport.jeiPublicationEnabled()) {
                CatalogJeiPublication.request();
                if (!CatalogJeiPublication.ready()) return;
            }
            refreshedRevision = requestedRevision;
            long started = System.nanoTime();
            invokeReadyCallback();
            notifiedEntries = AlloyFurnaceRecipeCatalog.entries(level);
            LogUtils.getLogger().info("Stretcher catalog ready callback: revision={}, elapsed_ms={}, thread={}",
                    refreshedRevision, (System.nanoTime() - started) / 1_000_000.0,
                    Thread.currentThread().getName());
        }
    }

    private static void invokeReadyCallback() {
        try {
            // Retains both the base JEI refresh and the base held-item damage refresh.
            Method ready = Class.forName("com.sorrowmist.useless.init.ClientSetup")
                    .getDeclaredMethod("onRecipeCatalogReady");
            ready.setAccessible(true);
            ready.invoke(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Base catalog ready callback failed", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Verified base catalog callback ABI changed", exception);
        }
    }
}
