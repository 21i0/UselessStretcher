package com.sorrowmist.useless.stretcher.guidetest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.StaffFluidCollectionScreen;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Renders the actual floating screen, tests slider drag and narrow-window scrolling. */
final class FluidUiChecks {
    private static StaffFluidCollectionScreen screen;
    private static int stage, frames;
    static boolean tick(Minecraft client) throws Exception {
        if (frames == 0) {
            screen = new StaffFluidCollectionScreen(null, InteractionHand.MAIN_HAND);
            client.setScreen(new Preview(screen));
            if (stage == 1) screen.resize(client, 320, 240);
            WindowChecks.assertLayout(screen, "fluid " + stage);
            var sliders = screen.children().stream().filter(AE2RangeSlider.class::isInstance).map(AE2RangeSlider.class::cast).toList();
            check(sliders.size() == 2, "radius and rate sliders present");
            for (var slider : sliders) {
                int x = slider.getX(), y = slider.getY() + 8;
                check(screen.mouseClicked(x + 6, y, 0), "slider mouse press handled");
                screen.mouseDragged(x + slider.getWidth() - 4, y, 0, slider.getWidth() - 10, 0);
                screen.mouseReleased(x + slider.getWidth() - 4, y, 0);
            }
            check(sliders.get(0).getMessage().getString().contains("16") && sliders.get(1).getMessage().getString().contains("64"), "real drag reaches clamped max radius and rate");
            check(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .noneMatch(widget -> widget.getMessage().getString().contains("gui.useless_stretcher.fluid")), "all new UI labels translate");
            frames = 12;
            return false;
        }
        if (--frames != 0) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/fluid-settings-" + stage + ".png"));
        }
        if (stage == 1) {
            var window = screen.floatingWindow();
            check(screen.mouseScrolled(window.bodyLeft() + 10, window.bodyTop() + 10, 0, -20), "small viewport scrolls to footer");
            frames = 12; stage++;
            return false;
        }
        if (stage++ >= 2) {
            LogUtils.getLogger().info("STAFF FLUID UI CHECKS PASSED: default/narrow floating window, real slider drag, translated labels, scrolling");
            return true;
        }
        return false;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Preview extends Screen {
        private final Screen target;
        Preview(Screen target) { super(Component.empty()); this.target = target; }
        protected void init() { target.init(minecraft, width, height); }
        public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF181B22);
            target.renderWithTooltip(graphics, x, y, partial);
        }
        public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    }
}
