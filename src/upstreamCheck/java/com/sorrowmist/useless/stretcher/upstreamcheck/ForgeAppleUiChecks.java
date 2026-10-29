package com.sorrowmist.useless.stretcher.upstreamcheck;

import appeng.api.stacks.AEItemKey;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.client.gui.AppleEditBox;
import com.sorrowmist.useless.stretcher.client.*;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.screen.MyriadPatternRepositoryScreen;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Real native inputs/GL; no connected player, production fixture hooks, or world writes. */
final class ForgeAppleUiChecks {
    static void validate(Minecraft client) throws Exception {
        check(client.getConnection() == null, "fixture must stay disconnected");
        StaffScreen page = new WondrousStaffConfigScreen(InteractionHand.MAIN_HAND);
        show(client, page, 640, 400);
        page.floatingWindow().setPreference("sidebarCollapsed", "false");
        for (var tab : StaffScreen.Tab.values()) {
            if (tab == StaffScreen.Tab.APOTHEOSIS) page = new WondrousStaffApotheosisScreen(page, InteractionHand.MAIN_HAND);
            else if (tab != page.staffTab()) { page.navigate(tab); page = (StaffScreen) client.screen; }
            show(client, page, 320, 240);
            bounds(page); render(client, page);
            var window = page.floatingWindow();
            int x = window.bodyLeft() + window.bodyWidth() - 12, y = window.bodyTop() + 5;
            for (int i = 0; i < 30; i++) page.mouseScrolled(x, y, -1);
            bounds(page); render(client, page);
            for (int i = 0; i < 30; i++) page.mouseScrolled(x, y, 1);
            if (tab == StaffScreen.Tab.SUMMON) {
                var search = page.children().stream().filter(AppleEditBox.class::isInstance).map(AppleEditBox.class::cast).findFirst().orElseThrow();
                page.mouseClicked(search.getX() + 7, search.getY() + 8, 0);
                page.mouseReleased(search.getX() + 7, search.getY() + 8, 0);
                search.setValue("");
                check(page.charTyped('x', 0) && search.getValue().equals("x"), "native Apple search char input");
                check(page.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0) && search.getValue().isEmpty(), "native Apple search backspace");
                page.resize(client, 360, 260);
                check(page.getFocused() == search && search.isFocused(), "search retains keyboard focus after resize");
            }
        }
        var field = new RangeAccelerationSavedData.Summary(UUID.randomUUID(), new ResourceLocation("minecraft:overworld"),
                BlockPos.ZERO, 1, true, 16, 3, 5, 7, 0, 1, -1, "Apple UI fixture");
        var edit = new RangeAccelerationHistoryEditScreen(page, field);
        show(client, edit, 320, 240); bounds(edit); render(client, edit);
        var config = new StretcherConfigScreen(null);
        show(client, config, 320, 240); bounds(config); render(client, config);

        var owner = UUID.randomUUID();
        var reclaim = new ReclaimerScreen(null, List.of(new Network.ReclaimerEntry(false, UUID.randomUUID(), owner,
                "Fixture owner", new ResourceLocation("minecraft:overworld"), BlockPos.ZERO, "Fixture machine")), false);
        show(client, reclaim, 320, 240); bounds(reclaim);
        button(reclaim, Component.literal("Fixture owner (1)")).onPress();
        bounds(reclaim);
        button(reclaim, Component.translatable("gui.useless_stretcher.reclaimer.category_count",
                Component.translatable("gui.useless_stretcher.reclaimer.permanent"), 1L)).onPress();
        bounds(reclaim); render(client, reclaim);

        int[] answers = {0};
        var confirm = new StretcherConfirmScreen(answer -> answers[0] += answer ? 100 : 1,
                Component.literal("Confirm"), Component.literal("A long wrapped confirmation message for a narrow Apple window."));
        show(client, confirm, 320, 240); bounds(confirm); render(client, confirm);
        check(confirm.isPauseScreen() && confirm.getNarrationMessage().getString().contains("wrapped confirmation"), "confirmation narration and pause");
        confirm.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); confirm.onClose();
        check(answers[0] == 1, "Esc cancels exactly once");
        var yes = new StretcherConfirmScreen(answer -> answers[0] += answer ? 100 : 1, Component.literal("Confirm"), Component.literal("Question"));
        show(client, yes, 320, 240);
        yes.setFocused(yes.children().get(0));
        check(yes.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "native confirm keyboard activation");
        yes.onClose(); check(answers[0] == 101, "confirmation answers exactly once");
        windowInput(client, config);
        ForgeConfigTranslationChecks.validate(client);
        patterns(client);
        check(OmniversalMyriadScreen.class.getSuperclass() == AbstractContainerScreen.class, "native Menu and JEI container base preserved");
        LogUtils.getLogger().info("FORGE APPLE UI CHECKS PASSED: all eight narrow staff tabs, native search focus/typing, range edit, config, owner/category/target rows, confirmation keyboard/idempotence/narration, window move/resize/reset, pattern drag/erase/stale response and balanced clips");
    }

    private static void windowInput(Minecraft client, FloatingScreen screen) {
        show(client, screen, 640, 400);
        var w = screen.floatingWindow(); w.reset(); screen.resize(client, 640, 400);
        int left = w.left(), top = w.top();
        check(screen.mouseClicked(left + 40, top + 12, 0), "window title captures");
        screen.mouseDragged(left + 60, top + 24, 0, 20, 12); screen.mouseReleased(left + 60, top + 24, 0);
        check(w.left() == left + 20 && w.top() == top + 12 && !w.capturing(), "window move");
        int width = w.width(), height = w.height(), x = w.left() + width - 1, y = w.top() + height - 1;
        screen.mouseClicked(x, y, 0); screen.mouseDragged(x + 20, y + 10, 0, 20, 10); screen.mouseReleased(x + 20, y + 10, 0);
        check(w.width() == width + 20 && w.height() == height + 10, "window resize");
        screen.mouseClicked(w.left() + w.width() - 32, w.top() + 10, 0);
        check(w.width() == width && w.height() == height, "window reset");
        bounds(screen); render(client, screen);
    }

    private static void patterns(Minecraft client) throws Exception {
        var screen = new MyriadPatternRepositoryScreen(null, BlockPos.ZERO, InteractionHand.OFF_HAND);
        show(client, screen, 640, 400);
        int request = (int) field(screen, "queryId");
        var keys = List.of(AEItemKey.of(Items.STONE), AEItemKey.of(Items.DIRT), AEItemKey.of(Items.COBBLESTONE));
        screen.onPage(new Network.PatternPagePayload(BlockPos.ZERO, 0, 1, true, true, true, keys, request, ""));
        int x = (int) field(screen, "gridLeft") + 8, y = (int) field(screen, "gridTop") + 8;
        check(screen.mouseClicked(x, y, 0), "pattern selection press");
        screen.mouseDragged(x + 36, y, 0, 36, 0); screen.mouseReleased(x + 36, y, 0);
        check(((Set<?>) field(screen, "selected")).size() == 3, "pattern drag interpolates selection");
        screen.mouseClicked(x + 18, y, 1); screen.mouseReleased(x + 18, y, 1);
        check(((Set<?>) field(screen, "selected")).size() == 2, "pattern right drag erases");
        var search = screen.children().stream().filter(AppleEditBox.class::isInstance).map(AppleEditBox.class::cast).findFirst().orElseThrow();
        search.setValue("minecraft");
        screen.onPage(new Network.PatternPagePayload(BlockPos.ZERO, 0, 1, true, true, true, List.of(), request, ""));
        check(((List<?>) field(screen, "patterns")).size() == 3 && (boolean) field(screen, "pending"), "stale pattern response ignored");
        bounds(screen); render(client, screen);
        screen.resize(client, 320, 240); bounds(screen); render(client, screen);
    }

    private static net.minecraft.client.gui.components.Button button(Screen screen, Component text) {
        return screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(w -> w.getMessage().equals(text)).findFirst().orElseThrow();
    }
    private static void show(Minecraft client, Screen screen, int width, int height) {
        client.setScreen(new Screen(Component.empty()) { });
        screen.init(client, width, height);
    }
    private static void bounds(FloatingScreen screen) {
        var w = screen.floatingWindow();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) {
            check(widget.getWidth() > 0 && widget.getX() >= w.bodyLeft() && widget.getX() + widget.getWidth() <= w.bodyLeft() + w.bodyWidth(),
                    "horizontal bounds: " + screen.getClass().getSimpleName() + " / " + widget.getMessage().getString());
        }
    }
    private static void render(Minecraft client, Screen screen) {
        var g = new GuiGraphics(client, client.renderBuffers().bufferSource());
        g.enableScissor(1, 2, 620, 390);
        screen.render(g, -1, -1, 0); g.flush();
        try { check(((Deque<?>) field(field(g, "scissorStack"), "stack")).size() == 1, "outer clip preserved"); }
        catch (Exception failure) { throw new AssertionError(failure); }
        g.disableScissor(); check(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "no clip leak");
    }
    private static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
