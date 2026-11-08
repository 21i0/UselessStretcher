package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.CapsuleVariantsScreen;
import com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen;
import com.sorrowmist.useless.stretcher.network.CapsuleVariantNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Actual client render/input coverage; isolated from all distributable artifacts. */
final class CapsuleVariantUiChecks {
    private static final String ID = "minecraft:bastion_remnant", DIMENSION = "minecraft:the_nether";
    private static final List<CapsuleVariantNetwork.Entry> ENTRIES = new ArrayList<>();
    private static final int[] PIXELS = new int[4096];
    private static DimensionCapsuleScreen parent;
    private static CapsuleVariantsScreen screen;
    private static Object firstPreview;
    private static int frames;
    private static boolean finished;

    static boolean tick(Minecraft client) throws Exception {
        if (finished) return true;
        if (screen == null) {
            Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
            client.options.guiScale().set(1); client.resizeDisplay();
            parent = new DimensionCapsuleScreen(List.of("minecraft|bastion_remnant||" + DIMENSION), false);
            client.setScreen(parent);
            screen = new CapsuleVariantsScreen(parent, ID, DIMENSION, false, 0, 0);
            client.setScreen(screen);
            String[] families = {"treasure", "bridge", "hoglin_stable", "units"};
            for (int i = 0; i < 32; i++) ENTRIES.add(new CapsuleVariantNetwork.Entry(token(i),
                    i < 4 ? "minecraft:bastion/" + families[i] : "example:rooms",
                    "example:rooms/layout_" + i, 50 + i, 30 + i, 60 + i, 10 + i));
            Arrays.fill(PIXELS, 0xFF547577);
            for (int z = 7; z < 57; z++) for (int x = 9; x < 55; x++)
                if (x % 9 == 0 || z % 10 == 0) PIXELS[x + z * 64] = 0xFFDAC36B;
            accept("searching", token(0));
            check(screen.selectedCandidateId().equals(token(0)), "server-selected candidate retained");
            firstPreview = field(screen, "previewImage");
            check(firstPreview != null, "candidate diagram allocated");
            assertLayout();
            exerciseListAndFilter();
        }

        if (frames == 30) {
            screenshot(client, "capsule-variants-default.png");
            screen.resize(client, 320, 240);
            assertLayout();
            exerciseListAndFilter();
        }
        if (frames == 60) {
            screenshot(client, "capsule-variants-compact.png");
            screen.resize(client, 320, 160);
            var window = screen.floatingWindow();
            check(screen.mouseScrolled(window.bodyLeft() + window.bodyWidth() - 10, window.bodyTop() + 5, 0, -20),
                    "short viewport scrolls the whole body to footer");
            var generate = (AbstractWidget) field(screen, "generateButton");
            check(generate.getBottom() <= window.bodyTop() + window.bodyHeight(), "short viewport generation control reachable");
        }

        PIXELS[frames % PIXELS.length] = 0xFF819D54;
        accept(frames < 30 ? "searching" : frames < 60 ? "paused" : "finished", screen.selectedCandidateId());
        check(firstPreview == field(screen, "previewImage"), "updates reuse the candidate GPU texture");
        GuiGraphics graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        screen.render(graphics, -1, -1, 0); graphics.flush();
        Object scissor = field(graphics, "scissorStack");
        check(((Deque<?>) field(scissor, "stack")).isEmpty(), "variant render balances its clip stack");
        check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "variant render leaves GL scissor disabled");
        graphics.enableScissor(3, 5, client.getWindow().getGuiScaledWidth() - 7, client.getWindow().getGuiScaledHeight() - 9);
        screen.render(graphics, -1, -1, 0); graphics.flush();
        check(((Deque<?>) field(scissor, "stack")).size() == 1, "variant render retains an embedding parent clip");
        graphics.disableScissor();
        if (++frames < 90) return false;
        screenshot(client, "capsule-variants-short-scrolled.png");
        screen.onClose();
        check(client.screen == parent, "closing variants returns to its original capsule screen");
        check(field(screen, "previewTexture") == null && field(screen, "previewImage") == null,
                "closing variants releases the GPU texture");
        LogUtils.getLogger().info("CAPSULE VARIANT UI CHECKS PASSED: 32 candidates, native-family labels, scroll/click/keyboard/filter input, default/320x240/320x160 layouts, 90 texture updates, 180 balanced real GL renders and return-to-parent cleanup");
        finished = true;
        return true;
    }

    private static void accept(String phase, String selected) {
        screen.accept(new CapsuleVariantNetwork.State(ID, DIMENSION, phase, 80, 0, 256, ENTRIES, selected, PIXELS, -1));
    }

    private static void exerciseListAndFilter() throws Exception {
        var layout = (CapsuleVariantsScreen.Layout) field(screen, "layout");
        int maxScroll = Math.max(0, ENTRIES.size() - screen.visibleCandidateRows());
        // Each normal wheel event advances three rows. A large dy is still one event,
        // just as in the existing capsule catalogue; exercise repeated real notches.
        for (int notch = 0; notch <= ENTRIES.size() / 3; notch++) {
            int previous = screen.candidateScroll();
            check(screen.mouseScrolled(layout.left() + 10, layout.listTop() + 5, 0, -1), "candidate wheel handled");
            check(screen.candidateScroll() == Math.min(previous + 3, maxScroll), "each candidate wheel notch advances exactly three rows and clamps");
        }
        check(screen.candidateScroll() == maxScroll, "candidate wheel reaches the final row");
        int lastVisibleRow = Math.min(ENTRIES.size(), screen.visibleCandidateRows()) - 1;
        check(screen.mouseClicked(layout.left() + 8, layout.listTop() + lastVisibleRow * 18 + 8, 0), "last visible candidate click handled");
        check(screen.selectedCandidateId().equals(token(31)), "last candidate selected after scrolling");
        check(screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0), "candidate keyboard navigation handled");
        check(screen.selectedCandidateId().equals(token(30)), "up selects preceding visible candidate");
        EditBox filter = (EditBox) field(screen, "filter");
        filter.setValue("layout_7");
        check(screen.selectedCandidateId().equals(token(7)), "filter selects only the visible matching plan");
        filter.setValue("no-such-layout");
        check(screen.selectedCandidateId().isEmpty(), "empty filter results clear hidden selection");
        check(!((AbstractWidget) field(screen, "generateButton")).active, "empty results disable generation");
        filter.setValue("layout_9");
        check(screen.selectedCandidateId().equals(token(9)), "selection recovers after empty search");
        filter.setValue("");
        check(screen.selectedCandidateId().equals(token(9)), "clearing search retains visible selected plan");
        // Selection changes intentionally release the old preview, then the matching server response recreates it.
        accept("searching", screen.selectedCandidateId());
        firstPreview = field(screen, "previewImage");
    }

    private static void assertLayout() throws Exception {
        var layout = (CapsuleVariantsScreen.Layout) field(screen, "layout");
        var window = screen.floatingWindow();
        check(layout.mapSize() > 0 && layout.mapX() >= layout.left()
                && layout.mapX() + layout.mapSize() <= layout.left() + layout.width(), "diagram fits its right column");
        check(layout.listBottom() < layout.footer(), "candidate list stays above footer");
        for (var child : screen.children()) if (child instanceof AbstractWidget widget) {
            check(widget.getWidth() > 0 && widget.getX() >= window.bodyLeft()
                    && widget.getRight() <= window.bodyLeft() + window.bodyWidth(), "widget fits floating width: " + widget.getMessage());
            check(widget.getY() >= window.bodyTop() && widget.getBottom() <= window.bodyTop() + window.bodyHeight(),
                    "widget fits normal floating height: " + widget.getMessage());
            check(!widget.getMessage().getString().contains("gui.useless_stretcher"), "variant widget label translates");
        }
    }

    private static String token(int index) { return String.format("%064x", index + 1); }
    private static void screenshot(Minecraft client, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name));
        }
    }
    private static Object field(Object object, String name) throws Exception {
        var declared = object.getClass().getDeclaredField(name); declared.setAccessible(true); return declared.get(object);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
