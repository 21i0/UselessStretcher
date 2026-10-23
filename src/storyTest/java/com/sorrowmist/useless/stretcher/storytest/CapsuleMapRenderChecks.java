package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Exercises the real GL clipping state, rather than checking a single screenshot. */
final class CapsuleMapRenderChecks {
    private static DimensionCapsuleScreen screen;
    private static int ticks, frames;
    private static boolean finished;
    private static final int[] PIXELS = new int[4096];
    private static Object firstMapImage;
    static void tick(Minecraft client) {
        if (finished) return;
        try {
            check(++ticks < 1200, "render test timeout");
            if (client.getOverlay() != null || client.screen == null) return;
            if (frames >= 360) {
                if (TwoINoseChecks.tick(client)) { finished = true; client.stop(); }
                return;
            }
            if (screen == null) {
                Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
                client.options.guiScale().set(1); client.resizeDisplay();
                screen = new DimensionCapsuleScreen(List.of("ae2|meteorite||useless_stretcher:capsule_overworld"), false);
                client.setScreen(screen);
                Arrays.fill(PIXELS, 0xFF667788);
            }
            String[] phases = {"planning", "cleaning", "placing", "bedrock", "trimming", "ready"};
            String phase = phases[Math.min(phases.length - 1, frames / 60)];
            PIXELS[frames % PIXELS.length] = 0xFF88AA55;
            screen.accept(new CapsuleNetwork.State("ae2:meteorite", "useless_stretcher:capsule_overworld",
                    0, 1, 0, 0, phase, Math.min(100, frames / 3), PIXELS.clone(), 144, 17, 144));
            if (firstMapImage == null) firstMapImage = field(screen, "mapImage");
            check(firstMapImage == field(screen, "mapImage"), "map updates reuse one GPU texture");
            GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
            Object scissor = field(graphics, "scissorStack");
            check(((Deque<?>) field(scissor, "stack")).isEmpty(), "capsule render leaves an unmatched scissor rectangle at frame " + frames);
            check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "capsule render leaks GL scissor into next framebuffer clear");
            // An embedding GUI must retain its own clip; the map must pop only its own clip.
            graphics.enableScissor(3, 5, client.getWindow().getGuiScaledWidth() - 7, client.getWindow().getGuiScaledHeight() - 9);
            screen.render(graphics, -1, -1, 0); graphics.flush();
            check(((Deque<?>) field(scissor, "stack")).size() == 1, "nested parent clipping remains balanced");
            graphics.disableScissor();
            if (frames % 60 == 59) {
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/capsule-stable-" + phase + ".png"));
                }
                LogUtils.getLogger().info("CAPSULE MAP FRAME CHECK PASSED phase={} frames={}", phase, frames + 1);
            }
            if (++frames < 360) return;
            screen.removed();
            check(field(screen, "mapTexture") == null, "map is released when closing");
            LogUtils.getLogger().info("CAPSULE MAP RENDER CHECKS PASSED: 360 texture updates, 720 balanced renders, nested parent clips and GL scissor disabled after every frame");
            TwoINoseChecks.tick(client);
        } catch (Throwable failure) {
            finished = true;
            RenderSystem.disableScissor();
            LogUtils.getLogger().error("CAPSULE MAP RENDER CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
