package com.sorrowmist.useless.stretcher.capsulecheck;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Real integrated-server catalogue, progress, map packets, notification and repeated travel. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "useless_stretcher", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ForgeCapsuleClientChecks {
    private static int stage, ticks, frames;
    private static DimensionCapsuleScreen capsule;
    private static int previousProgress;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> returnDimension;
    private static net.minecraft.world.phys.Vec3 returnPosition, firstMirrorEntry;
    private static int journeys;
    private static final java.util.Set<String> progressScreenshots = new java.util.HashSet<>();
    private ForgeCapsuleClientChecks() { }
    private static boolean requested, started;
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void bootstrap(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("stretcher.capsule.clientCheck") || event.phase != TickEvent.Phase.END || started) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null || client.screen == null) return;
        if (!requested) {
            requested = true;
            client.options.guiScale().set(1);
            client.resizeDisplay();
            client.options.pauseOnLostFocus = false;
            var settings = new net.minecraft.world.level.LevelSettings("Forge capsule network checks",
                    net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                    true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("capsule-ui-" + System.currentTimeMillis(), settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(2L, false, false),
                    access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            return;
        }
        if (client.player == null || client.getSingleplayerServer() == null) return;
        started = true;
        try { java.nio.file.Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots")); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        start(client);
    }

    static void start(Minecraft client) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeCapsuleClientChecks::tick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ForgeCapsuleClientChecks::rendered);
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
            Network.sendDimensionCapsuleCatalog(player, false);
        });
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 9) return;
        Minecraft client = Minecraft.getInstance();
        try {
            check(++ticks < 16000, "capsule client timed out at stage " + stage);
            if (client.getOverlay() != null) return;
            if (frames > 0) { frames--; return; }
            if (stage == 0) {
                if (!(client.screen instanceof DimensionCapsuleScreen screen)) return;
                if (capsule == null) { capsule = screen; frames = 12; return; }
                check(!((java.util.List<?>)field(capsule, "entries")).isEmpty(), "live server catalogue received");
                for (String phase : java.util.List.of("empty", "planning", "cleaning", "placing", "bedrock", "trimming", "generating", "paused", "ready"))
                    check(net.minecraft.locale.Language.getInstance().has("gui.useless_stretcher.dimension_capsule.phase." + phase),
                            "translated capsule phase: " + phase);
                screenshot(client, "capsule-catalogue.png");
                EditBox search = (EditBox)field(capsule, "search");
                search.setValue("@minecraft pillager outpost");
                var rows = (java.util.List<?>)field(capsule, "rows");
                check(rows.size() == 2, "mod and multi-word search narrow to one native structure");
                int left = (int)field(capsule, "left"), top = (int)field(capsule, "top");
                capsule.mouseClicked(left + 35, top + 28 + 18 + 8, 0);
                capsule.mouseReleased(left + 35, top + 54, 0);
                stage = 1; frames = 12; return;
            }
            if (stage == 1) {
                screenshot(client, "capsule-search.png");
                CapsuleNetwork.request("minecraft:pillager_outpost",
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD, 1, 0, false);
                stage = 2; return;
            }
            if (stage == 2) {
                var state = (CapsuleNetwork.State)field(capsule, "state");
                check(state == null || !java.util.Set.of("below_world", "failed", "too_large", "planning_timeout",
                        "planning_memory", "allocation_conflict", "timeout").contains(state.phase()),
                        "native flat-world generation failed: " + (state == null ? "no state" : state.phase()));
                if (state != null) {
                    check(state.progress() >= previousProgress && state.progress() <= 100, "network progress is monotonic and bounded");
                    previousProgress = state.progress();
                }
                if (state == null || !state.phase().equals("ready") || !progressScreenshots.contains("ready")) return;
                check(client.getToasts().getToast(com.sorrowmist.useless.stretcher.client.CapsuleReadyToast.class,
                        "minecraft:pillager_outpost|" + state.dimension() + "#0") != null,
                        "actual structure completion packet displays ready toast");
                check(state.progress() == 100 && progressScreenshots.containsAll(java.util.Set.of("cleaning", "placing", "trimming")),
                        "real generation stages displayed before completion");
                check(state.pixels().length == 4096 && java.util.Arrays.stream(state.pixels()).anyMatch(c -> c != 0), "real map payload is nonblank");
                check(((net.minecraft.client.gui.components.Button)field(capsule, "enter")).active, "ready packet enables entry");
                screenshot(client, "capsule-generated-map.png");
                int x = (int)field(capsule, "mapX") + 20, y = (int)field(capsule, "mapY") + 20;
                capsule.mouseClicked(x, y, 0); capsule.mouseDragged(x + 53, y, 0, 53, 0); capsule.mouseReleased(x + 53, y, 0);
                check(Math.abs((float)field(capsule, "angle")) > 40, "map responds to mouse rotation");
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 600);
                stage = 3; frames = 12; return;
            }
            if (stage == 3) {
                screenshot(client, "capsule-map-compact.png");
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
                returnDimension = client.level.dimension();
                returnPosition = client.player.position();
                client.setScreen(null);
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
                });
                stage = 7; return;
            }
            if (stage == 7) {
                if (!client.player.getMainHandItem().is(ModItems.DIMENSION_CAPSULE.get())) return;
                CapsuleNetwork.request("minecraft:pillager_outpost",
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD, 2, 0, false);
                stage = 8; return;
            }
            if (stage == 8) {
                if (!client.level.dimension().location().toString().equals(
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD)) return;
                stage = 10; frames = 20; return;
            }
            if (stage == 10) {
                if (!client.level.hasChunkAt(client.player.blockPosition())) return;
                check(client.level.getMinBuildHeight() == -64 && client.level.getHeight() == 384,
                        "client receives registered mirror environment type");
                if (firstMirrorEntry == null) firstMirrorEntry = client.player.position();
                else check(client.player.position().distanceToSqr(firstMirrorEntry) < 4,
                        "repeated client entry returns to the same saved room");
                screenshot(client, "capsule-mirror-client-entry-" + journeys + ".png");
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                stage = 11; return;
            }
            if (stage == 11) {
                if (!client.level.dimension().equals(returnDimension)) return;
                check(client.player.position().distanceToSqr(returnPosition) < 16,
                        "actual item-use packet returns client to its original position");
                if (++journeys < 2) { stage = 7; frames = 10; return; }
                LogUtils.getLogger().info("CAPSULE CLIENT ROUND TRIPS PASSED: registered mirror type, actual dimension packets/chunks, two entries into the same room, two item-use returns");
                stage = 9; client.stop();
            }
        } catch (Throwable failure) {
            stage = 9;
            LogUtils.getLogger().error("CAPSULE CLIENT CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage != 2) return;
        var client = Minecraft.getInstance();
        if (client.screen != capsule || client.getOverlay() != null) return;
        try {
            var state = (CapsuleNetwork.State)field(capsule, "state");
            if (state == null || progressScreenshots.contains(state.phase())) return;
            // A client tick can run twice without rendering. Inspect only after the real frame.
            screenshot(client, "capsule-progress-" + state.phase() + ".png");
            verifyProgressPixels(client, state);
            progressScreenshots.add(state.phase());
            LogUtils.getLogger().info("CAPSULE CLIENT PROGRESS: {} {}%", state.phase(), state.progress());
        } catch (Throwable failure) {
            stage = 9;
            LogUtils.getLogger().error("CAPSULE CLIENT CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static void verifyProgressPixels(Minecraft client, CapsuleNetwork.State state) throws Exception {
        if (state.progress() == 0 && !state.phase().equals("planning")) return;
        int x = (int)field(capsule, "mapX"), size = (int)field(capsule, "mapSize");
        int y = (int)field(capsule, "mapY") + size + 16;
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int colored = 0;
            for (int i = x + 1; i < x + size - 1; i++)
                if ((image.getPixelRGBA(i, y) & 0xFFFFFF) == 0xB59870) colored++;
            check(colored > 0, "visible progress fill for " + state.phase());
        }
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void screenshot(Minecraft client, String file) throws Exception {
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(file));
        }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
