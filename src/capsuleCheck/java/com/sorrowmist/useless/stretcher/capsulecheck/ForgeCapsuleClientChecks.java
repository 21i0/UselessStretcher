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
    private static final String STRUCTURE = System.getProperty("stretcher.capsule.clientStructure", "minecraft:pillager_outpost");
    private static int stage, ticks, frames;
    private static DimensionCapsuleScreen capsule;
    private static int previousProgress;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> returnDimension;
    private static net.minecraft.world.phys.Vec3 returnPosition, firstMirrorEntry;
    private static int journeys;
    private static java.util.concurrent.CompletableFuture<Void> physicalCheck;
    private static CapsuleNetwork.State generatedState;
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
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
            client.resizeDisplay();
            client.options.pauseOnLostFocus = false;
            var settings = new net.minecraft.world.level.LevelSettings("Forge capsule network checks",
                    net.minecraft.world.level.GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                    true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("capsule-ui-" + System.currentTimeMillis(), settings,
                    new net.minecraft.world.level.levelgen.WorldOptions(2L, true, false),
                    access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                            .getHolderOrThrow(Boolean.getBoolean("stretcher.capsule.normalWorld")
                                    ? net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL
                                    : net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
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
                search.setValue("@" + STRUCTURE.replace(':', ' ').replace('_', ' '));
                var rows = (java.util.List<?>)field(capsule, "rows");
                check(rows.size() == 2, "mod and multi-word search narrow to one native structure");
                int left = (int)field(capsule, "left"), top = (int)field(capsule, "top");
                capsule.mouseClicked(left + 35, top + 28 + 18 + 8, 0);
                capsule.mouseReleased(left + 35, top + 54, 0);
                stage = 1; frames = 12; return;
            }
            if (stage == 1) {
                screenshot(client, "capsule-search.png");
                click((net.minecraft.client.gui.components.AbstractWidget) field(capsule, "generate"));
                stage = 2; return;
            }
            if (stage == 2) {
                var state = (CapsuleNetwork.State)field(capsule, "state");
                check(state == null || !java.util.Set.of("below_world", "failed", "too_large", "planning_timeout",
                        "planning_memory", "allocation_conflict", "timeout").contains(state.phase()),
                        "native capsule generation failed for " + STRUCTURE + ": " + (state == null ? "no state" : state.phase()));
                if (state != null) {
                    check(state.progress() >= previousProgress && state.progress() <= 100, "network progress is monotonic and bounded");
                    previousProgress = state.progress();
                }
                if (state == null || !state.phase().equals("ready") || !progressScreenshots.contains("ready")) return;
                check(client.getToasts().getToast(com.sorrowmist.useless.stretcher.client.CapsuleReadyToast.class,
                        STRUCTURE + "|" + state.dimension() + "#0") != null,
                        "actual structure completion packet displays ready toast");
                check(state.progress() == 100 && progressScreenshots.containsAll(java.util.Set.of("cleaning", "placing", "trimming")),
                        "real generation stages displayed before completion");
                check(state.pixels().length == 4096 && java.util.Arrays.stream(state.pixels()).anyMatch(c -> c != 0), "real map payload is nonblank");
                check(((net.minecraft.client.gui.components.Button)field(capsule, "enter")).active, "ready packet enables entry");
                screenshot(client, "capsule-generated-map.png");
                if (STRUCTURE.equals("minecraft:ancient_city")) {
                    if (physicalCheck == null) {
                        var owner = client.player.getUUID();
                        physicalCheck = client.getSingleplayerServer().submit(() ->
                                com.sorrowmist.useless.stretcher.content.capsule.ForgeCapsuleAncientCityChecks.verify(
                                        client.getSingleplayerServer(), owner, state.dimension()));
                        return;
                    }
                    if (!physicalCheck.isDone()) return;
                    physicalCheck.join();
                }
                verifyMapPixels(client, state);
                generatedState = state;
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
                CapsuleNetwork.request(STRUCTURE,
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
                var angle = capsule.getClass().getDeclaredField("angle");
                angle.setAccessible(true); angle.setFloat(capsule, 0);
                client.setScreen(capsule);
                capsule.accept(generatedState);
                stage = 12; frames = 12; return;
            }
            if (stage == 12) {
                verifyRenderIsolation(client, generatedState);
                LogUtils.getLogger().info("CAPSULE CLIENT CHECKS PASSED: {}", STRUCTURE);
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
            double scale = client.getWindow().getGuiScale();
            int py = Math.min(image.getHeight() - 1, (int) Math.floor(y * scale));
            for (int i = (int) ((x + 1) * scale); i < (int) ((x + size - 1) * scale); i++)
                if ((image.getPixelRGBA(i, py) & 0xFFFFFF) == 0xB59870) colored++;
            check(colored > 0, "visible progress fill for " + state.phase());
        }
    }
    private static void verifyMapPixels(Minecraft client, CapsuleNetwork.State state) throws Exception {
        verifyMapPixels(client, state, client.getMainRenderTarget());
    }
    private static void verifyMapPixels(Minecraft client, CapsuleNetwork.State state,
                                        com.mojang.blaze3d.pipeline.RenderTarget target) throws Exception {
        int x = (int) field(capsule, "mapX"), y = (int) field(capsule, "mapY"), size = (int) field(capsule, "mapSize");
        double scale = client.getWindow().getGuiScale();
        int matched = 0, samples = 0;
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            for (int row = 4; row < 60; row += 3) for (int col = 4; col < 60; col += 3) {
                int color = state.pixels()[row * 64 + col];
                if (color == 0 || color == 0xFF171B20) continue;
                int px = (int) Math.floor((x + (col + .5) * size / 64.0) * scale);
                int py = (int) Math.floor((y + (row + .5) * size / 64.0) * scale);
                int actual = image.getPixelRGBA(px, py);
                int expected = color & 0xFF00FF00 | (color >> 16 & 255) | (color & 255) << 16;
                if ((actual & 0xFFFFFF) == (expected & 0xFFFFFF)) matched++;
                samples++;
            }
        }
        check(samples > 0 && matched >= samples * .75, "actual structure map colors visible: " + matched + "/" + samples);
    }
    @SuppressWarnings("deprecation")
    private static void verifyRenderIsolation(Minecraft client, CapsuleNetwork.State original) throws Exception {
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(client.getWindow().getWidth(),
                client.getWindow().getHeight(), true, Minecraft.ON_OSX);
        var modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();
        modelView.pushPose();
        try {
            // Client ticks have no GUI projection. Set a complete 2-D context instead
            // of accidentally checking pixels left over from the previous real frame.
            modelView.setIdentity();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(
                    0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0, -1000, 1000),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            var graphics = new net.minecraft.client.gui.GuiGraphics(client, client.renderBuffers().bufferSource());
            graphics.drawManaged(() -> capsule.render(graphics, -1, -1, 0));
            check(!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST), "map does not leak clip after managed render");
            verifyMapPixels(client, original, target);
            try (NativeImage image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/capsule-managed-render.png"));
            }
            // Model a near-depth foreign panel. The map itself is a 2-D overlay.
            com.mojang.blaze3d.systems.RenderSystem.clearDepth(0);
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            int[] colors = new int[4096];
            java.util.Arrays.fill(colors, 0xFF3377CC);
            capsule.accept(new CapsuleNetwork.State(original.id(), original.dimension(), original.instanceId(),
                    original.instanceCount(), original.originX(), original.originZ(), "ready", 100, colors, 20, 20, 20));
            capsule.render(graphics, -1, -1, 0);
            graphics.flush();
            check(!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST), "offscreen clip fully restored");
            int x = (int) field(capsule, "mapX"), y = (int) field(capsule, "mapY"), size = (int) field(capsule, "mapSize");
            double scale = client.getWindow().getGuiScale();
            try (NativeImage image = Screenshot.takeScreenshot(target)) {
                int pixel = image.getPixelRGBA((int) ((x + size / 2.0) * scale), (int) ((y + size / 2.0) * scale));
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/capsule-depth-isolation.png"));
                check((pixel & 0xFFFFFF) == 0xCC7733, "2-D map ignores foreign panel depth, actual=" + Integer.toHexString(pixel));
            }
            LogUtils.getLogger().info("CAPSULE CLIENT RENDER ISOLATION PASSED: actual map pixels, batched render, nested scissor, foreign depth");
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.clearDepth(1);
            modelView.popPose();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers();
            client.getMainRenderTarget().bindWrite(true);
            capsule.accept(original);
        }
    }
    private static void click(net.minecraft.client.gui.components.AbstractWidget widget) {
        check(widget.active && widget.visible, "capsule button enabled: " + widget.getMessage().getString());
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        check(capsule.mouseClicked(x, y, 0), "capsule button receives real click");
        capsule.mouseReleased(x, y, 0);
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
