package com.sorrowmist.useless.stretcher.guidetest;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.*;
import com.sorrowmist.useless.stretcher.client.gui.FloatingWindow;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.UUID;

/** Real widgets, shared bounds, tab navigation and a fresh disk reload; preview needs no game connection. */
final class StaffTabsChecks {
    private static StaffScreen screen;
    private static FloatingWindow shared;
    private static int stage, frames;
    private static boolean ready;

    static boolean tick(Minecraft client) throws Exception {
        if (ready) return true;
        if (frames > 0) {
            if (--frames > 0) return false;
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/staff-tabs-" + stage + ".png"));
            }
            stage++;
        }
        if (stage == 0) {
            screen = new WondrousStaffConfigScreen(InteractionHand.OFF_HAND);
            preview(client, screen, false);
            shared = screen.floatingWindow();
            shared.reset();
            screen.resize(client, 640, 400);
            check(!screen.sidebarCollapsed(), "first open sidebar expanded");
            var fold = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(w -> w.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.expand")))
                    .findFirst().orElseThrow();
            click(screen, fold);
            check(shared.preference("extrasCollapsed", "true").equals("false"), "real expand click persists preference");
            int x = shared.left() + 45, y = shared.top() + 10;
            check(screen.mouseClicked(x, y, 0), "title drag starts");
            screen.mouseDragged(x - 18, y - 11, 0, -18, -11);
            screen.mouseReleased(x - 18, y - 11, 0);
            int right = shared.left() + shared.width() - 1, bottom = shared.top() + shared.height() - 1;
            check(screen.mouseClicked(right, bottom, 0), "resize starts");
            screen.mouseDragged(right + 22, bottom + 35, 0, 22, 35);
            screen.mouseReleased(right + 22, bottom + 35, 0);
        } else if (stage <= 7) {
            var wanted = StaffScreen.Tab.values()[stage];
            var label = Component.translatable("gui.useless_stretcher.staff_tabs." + wanted.name().toLowerCase(java.util.Locale.ROOT));
            var tab = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(w -> w.getMessage().equals(label)).findFirst().orElse(null);
            if (tab == null && wanted == StaffScreen.Tab.APOTHEOSIS) {
                // Optional integration absent from this fixture; still render its actual page layout.
                screen = new WondrousStaffApotheosisScreen(screen, InteractionHand.OFF_HAND);
            } else {
                check(tab != null, "tab present " + wanted);
                int previousWidth = shared.width();
                click(screen, tab);
                check(client.screen instanceof StaffScreen, "sidebar navigates to a real staff page");
                screen = (StaffScreen) client.screen;
                check(shared.width() == previousWidth, "tab switch retains bounds");
            }
            preview(client, screen, false);
            check(screen.staffTab() == wanted && screen.floatingWindow() == shared, "every tab shares exactly one window");
        } else if (stage == 8) {
            screen.navigate(StaffScreen.Tab.SUMMON);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            var search = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            search.setValue("minecraft");
            screen.navigate(StaffScreen.Tab.MAIN);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            screen.navigate(StaffScreen.Tab.SUMMON);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            check(screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .anyMatch(box -> box.getValue().equals("minecraft")), "tab search survives round trip");
            screen.onClose();
            check(client.screen instanceof WondrousStaffConfigScreen, "footer returns inside the same staff session");
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            screen.navigate(StaffScreen.Tab.SUMMON);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            click(screen, (AbstractWidget) screen.children().getFirst());
            check(screen.sidebarCollapsed(), "sidebar actual collapse click");
            screen.floatingWindow().save();
            var json = JsonParser.parseString(Files.readString(client.gameDirectory.toPath().resolve("config/useless_stretcher-windows.json")))
                    .getAsJsonObject().getAsJsonObject("staff").getAsJsonObject("preferences");
            check(json.get("activeTab").getAsString().equals("SUMMON") && json.get("sidebarCollapsed").getAsBoolean()
                    && !json.get("extrasCollapsed").getAsBoolean(), "bounds and all three preferences written in one JSON");
            // Force a fresh disk read instead of trusting the static in-process cache.
            var path = FloatingWindow.class.getDeclaredField("loadedPath"); path.setAccessible(true); path.set(null, null);
            StaffScreen.open(InteractionHand.OFF_HAND);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            check(screen.staffTab() == StaffScreen.Tab.SUMMON && screen.sidebarCollapsed(), "reopen restores tab and sidebar from disk");
            check(!Boolean.parseBoolean(screen.floatingWindow().preference("extrasCollapsed", "true")), "reopen restores expanded extras");
            check(screen.floatingWindow().width() == shared.width() && screen.floatingWindow().height() == shared.height()
                    && screen.floatingWindow().left() == shared.left() && screen.floatingWindow().top() == shared.top(), "reopen restores dragged and resized bounds");
            screen.navigate(StaffScreen.Tab.MAIN);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            check(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .anyMatch(w -> w.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.collapse"))), "restored main page actually displays expanded controls");
            screen.navigate(StaffScreen.Tab.SUMMON);
            screen = (StaffScreen) client.screen;
            preview(client, screen, false);
            shared = screen.floatingWindow();
        } else if (stage == 9) {
            screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
            screen = (StaffScreen) client.screen;
            check(screen.staffTab() == StaffScreen.Tab.FLUIDS, "Ctrl Shift Tab previous page");
            preview(client, screen, true);
            check(screen.floatingWindow().left() >= 0 && shared.left() + shared.width() <= 320, "narrow viewport clamps common bounds");
            check(!screen.mouseScrolled(shared.bodyLeft() + 5, shared.bodyTop() + 45, 0, -20), "sidebar wheel never scrolls page");
            if (!screen.mouseScrolled(shared.bodyLeft() + 60, shared.bodyTop() + 45, 0, -20))
                check(screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .allMatch(w -> w.getBottom() <= shared.bodyTop() + shared.bodyHeight()), "unscrolled fluid page fits narrow viewport");
        } else if (stage == 10) {
            var history = new RangeAccelerationHistoryScreen(screen);
            var summary = new RangeAccelerationSavedData.Summary(UUID.randomUUID(), ResourceLocation.parse("minecraft:overworld"),
                    BlockPos.ZERO, 0, true, 16, 3, 3, 3, 0, 0, 0, "Preview", false, 1);
            screen = new RangeAccelerationHistoryEditScreen(history, summary);
            preview(client, screen, true);
            check(screen.floatingWindow() == shared && screen.staffTab() == StaffScreen.Tab.HISTORY, "history edit stays in shared window");
            check(screen.mouseScrolled(shared.bodyLeft() + 60, shared.bodyTop() + 45, 0, -20), "long history editor scrolls in narrow viewport");
        } else if (stage == 11) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            check(client.screen == null || client.screen instanceof net.minecraft.client.gui.screens.TitleScreen,
                    "Escape closes entire staff window (preview returns to title without a loaded world)");
            LogUtils.getLogger().info("STAFF TABS UI CHECKS PASSED: all pages/history edit, sidebar mouse and Ctrl Tab, shared drag/resize, disk reload, extras, search retention, 320x240 clipping/scroll");
            ready = true;
            return true;
        }
        layout(screen);
        frames = 12;
        return false;
    }

    private static void preview(Minecraft client, StaffScreen target, boolean narrow) {
        client.setScreen(new Preview(target));
        target.resize(client, narrow ? 320 : 640, narrow ? 240 : 400);
    }
    private static void click(StaffScreen target, AbstractWidget widget) {
        check(target.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0), "real widget click handled: " + widget.getMessage().getString());
        target.mouseReleased(widget.getX() + 2, widget.getY() + 2, 0);
    }
    private static void layout(StaffScreen target) {
        var win = target.floatingWindow();
        for (var child : target.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            check(widget.getWidth() > 0 && widget.getX() >= win.bodyLeft() && widget.getRight() <= win.bodyLeft() + win.bodyWidth(), "widget stays in shared horizontal bounds: " + widget.getMessage().getString());
            check(!widget.getMessage().getString().contains("gui.useless_stretcher."), "translated tab/page labels");
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Preview extends Screen {
        final StaffScreen target;
        Preview(StaffScreen target) { super(Component.empty()); this.target = target; }
        @Override protected void init() { target.init(minecraft, width, height); }
        @Override public void render(GuiGraphics g, int x, int y, float partial) {
            g.fill(0, 0, width, height, 0xFF181B22);
            target.renderWithTooltip(g, x, y, partial);
        }
        @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) { }
    }
}
