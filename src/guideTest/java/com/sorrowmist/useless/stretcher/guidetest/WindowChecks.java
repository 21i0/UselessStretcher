package com.sorrowmist.useless.stretcher.guidetest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.EntityAccelerationConfigScreen;
import com.sorrowmist.useless.stretcher.client.RangeAccelerationConfigScreen;
import com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryEditScreen;
import com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryScreen;
import com.sorrowmist.useless.stretcher.client.ReclaimerScreen;
import com.sorrowmist.useless.stretcher.client.StretcherConfigScreen;
import com.sorrowmist.useless.stretcher.client.WondrousStaffConfigScreen;
import com.sorrowmist.useless.stretcher.client.WondrousStaffSummonScreen;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.FloatingWindow;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen;
import com.sorrowmist.useless.stretcher.content.mold.MoldCatalog;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exercises real screen input in the isolated preview client, without a connection or gameplay packets. */
final class WindowChecks {
    private static final String[] NAMES = {"staff", "range", "entity", "range-edit", "history", "reclaimer",
            "summon", "myriad", "config", "confirm"};
    private static final String[] VARIANTS = {"default", "wide", "narrow"};
    private static final ResourceLocation VILLAGER = ResourceLocation.parse("minecraft:villager");
    private static int stage, variant, frames;
    private static Screen screen;
    private static int defaultWidth, defaultHeight;

    static boolean tick(Minecraft client) throws Exception {
        if (frames == 0) {
            if (variant == 0) {
                screen = createScreen(stage, client);
                client.setScreen(new WindowPreview(screen));
                clickReset(screen);
                defaultWidth = window(screen).width();
                defaultHeight = window(screen).height();
                exerciseEvents(client, screen, NAMES[stage]);
                prepareScreenshot(client, 0);
            } else prepareScreenshot(client, variant);
            assertLayout(screen, NAMES[stage] + " " + VARIANTS[variant]);
            frames = 8;
            return false;
        }
        if (--frames != 0) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots")
                    .resolve("window-" + NAMES[stage] + "-" + VARIANTS[variant] + ".png"));
        }
        if (++variant == VARIANTS.length) {
            variant = 0;
            if (++stage == NAMES.length) {
                LogUtils.getLogger().info("WINDOW CHECKS PASSED: all 10 windows, real title drag/corner resize/reset, saved bounds, "
                        + "800x600 and 320x240 clamps, form state, search caret/selection, history rename draft/UUID, "
                        + "list capacities, hover help and non-overlap");
                return true;
            }
        }
        return false;
    }

    private static Screen createScreen(int index, Minecraft client) throws Exception {
        return switch (index) {
            case 0 -> new WondrousStaffConfigScreen(InteractionHand.MAIN_HAND);
            case 1 -> new RangeAccelerationConfigScreen(null, InteractionHand.MAIN_HAND);
            case 2 -> new EntityAccelerationConfigScreen(null, InteractionHand.MAIN_HAND);
            case 3 -> new RangeAccelerationHistoryEditScreen(null, summary(0));
            case 4 -> {
                var history = new RangeAccelerationHistoryScreen(null);
                set(history, "requested", true);
                set(history, "fields", java.util.stream.IntStream.range(0, 30).mapToObj(WindowChecks::summary).toList());
                yield history;
            }
            case 5 -> new ReclaimerScreen(null, reclaimerEntries(), false);
            case 6 -> new WondrousStaffSummonScreen(null, InteractionHand.MAIN_HAND);
            case 7 -> myriad(client);
            case 8 -> new StretcherConfigScreen(null);
            default -> new StretcherConfirmScreen(ignored -> {}, Component.literal("回收确认"),
                    Component.literal("测试工厂的永久加速将被回收。取消或关闭窗口不会改变任何加速状态。"));
        };
    }

    private static void exerciseEvents(Minecraft client, Screen target, String label) throws Exception {
        target.resize(client, 800, 600);
        clickReset(target);
        var bounds = window(target);
        int initialLeft = bounds.left(), initialTop = bounds.top();
        int initialWidth = bounds.width(), initialHeight = bounds.height();
        drag(target, bounds.left() + 30, bounds.top() + 12, 20, 16);
        check(bounds.left() == initialLeft + 20 && bounds.top() == initialTop + 16, label + " title moves window");
        assertLayout(target, label + " moved");
        drag(target, bounds.left() + bounds.width() - 1, bounds.top() + bounds.height() - 1, 64, 48);
        check(bounds.width() == initialWidth + 64 && bounds.height() == initialHeight + 48, label + " southeast resize");
        int right = bounds.left() + bounds.width(), bottom = bounds.top() + bounds.height();
        drag(target, bounds.left() + 1, bounds.top() + 1, -16, -12);
        check(bounds.width() == initialWidth + 80 && bounds.height() == initialHeight + 60
                && bounds.left() + bounds.width() == right && bounds.top() + bounds.height() == bottom,
                label + " northwest resize preserves opposite corner");
        assertLayout(target, label + " enlarged");
        String key = (String) field(bounds, "key");
        var restored = new FloatingWindow(key, 1, 1, 1, 1);
        restored.init(800, 600);
        check(restored.left() == bounds.left() && restored.top() == bounds.top()
                && restored.width() == bounds.width() && restored.height() == bounds.height(), label + " saved geometry");
        check(Files.isRegularFile(client.gameDirectory.toPath().resolve("config/useless_stretcher-windows.json")),
                label + " layout file persisted");

        if (target instanceof WondrousStaffSummonScreen summon) seedSummonSelection(summon);
        if (target instanceof EntityAccelerationConfigScreen) {
            set(target, "timers", true);
            set(target, "speed", 8192);
        }
        if (target instanceof OmniversalMyriadScreen) {
            EditBox search = (EditBox) field(target, "search");
            search.setValue("iron");
            search.setFocused(true);
            target.setFocused(search);
        }
        if (target instanceof RangeAccelerationHistoryScreen history) beginHistoryRename(history);
        EditorState editor = prepareEditorState(target);
        target.resize(client, 800, 600);
        assertEditorState(target, editor, label + " same viewport resize");
        maximize(target);
        assertEditorState(target, editor, label + " corner resize");
        int largeRows = rowCapacity(target);
        assertLayout(target, label + " large");
        target.resize(client, 320, 240);
        assertLayout(target, label + " small viewport");
        check(bounds.left() >= 4 && bounds.top() >= 4 && bounds.left() + bounds.width() <= 316
                && bounds.top() + bounds.height() <= 236, label + " viewport clamp");
        int smallRows = rowCapacity(target);
        if (largeRows >= 0) check(largeRows > smallRows && smallRows >= 1, label + " list capacity responds to height");
        assertRetainedState(target, label);
        assertEditorState(target, editor, label + " small viewport resize");
        if (target instanceof RangeAccelerationHistoryScreen history) {
            check(history.keyPressed(256, 0, 0), "history rename cancels without a gameplay packet");
            check(field(history, "renameBox") == null && field(history, "renameId") == null,
                    "history rename cancel clears the draft editor and target");
            check(((List<RangeAccelerationSavedData.Summary>) field(history, "fields")).get(0).name().equals(summary(0).name()),
                    "history rename cancel leaves the saved name unchanged");
        }
        if (target instanceof EntityAccelerationConfigScreen) {
            set(target, "timers", false);
            set(target, "speed", 256);
        }
        target.resize(client, 800, 600);
        clickReset(target);
        check(bounds.width() == initialWidth && bounds.height() == initialHeight
                && bounds.left() == initialLeft && bounds.top() == initialTop, label + " reset restores defaults");
        target.resize(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        clickReset(target);
    }

    private static void prepareScreenshot(Minecraft client, int variant) throws Exception {
        screen.resize(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        clickReset(screen);
        if (screen instanceof OmniversalMyriadScreen) ((EditBox) field(screen, "search")).setValue("");
        if (variant == 1) maximize(screen);
        if (variant == 2) {
            screen.resize(client, 320, 240);
            clickReset(screen);
            if (screen instanceof FloatingScreen floating) {
                int previousScroll = (int) field(screen, "contentScroll");
                int extent = (int) field(screen, "contentExtent");
                if (extent > floating.floatingWindow().bodyHeight()) {
                    var bounds = floating.floatingWindow();
                    check(screen.mouseScrolled(bounds.bodyLeft() + 8, bounds.bodyTop() + 8, 0, -100),
                            NAMES[stage] + " compact content scrolls");
                    check((int) field(screen, "contentScroll") > previousScroll, NAMES[stage] + " scroll advances");
                    assertLayout(screen, NAMES[stage] + " scrolled compact content");
                    screen.mouseScrolled(bounds.bodyLeft() + 8, bounds.bodyTop() + 8, 0, 100);
                    check((int) field(screen, "contentScroll") == 0, NAMES[stage] + " scroll returns to start");
                }
            }
        }
    }

    private static void seedSummonSelection(WondrousStaffSummonScreen summon) throws Exception {
        EditBox search = (EditBox) field(summon, "searchBox");
        search.setValue("minecraft:villager");
        search.setFocused(true);
        summon.setFocused(search);
        int left = (int) field(summon, "panelLeft"), top = (int) field(summon, "panelTop");
        check(summon.mouseClicked(left + 40, top + 45 + 18 + 8, 0), "summon selects visible villager row");
        check(((Set<?>) field(summon, "selected")).contains(VILLAGER), "villager selected without sending summon request");
        search.setValue(EntityType.VILLAGER.getDescription().getString());
        check(((List<?>) field(summon, "rows")).size() > 1, "localized entity search");
        search.setValue("minecraft:villager");
        search.setFocused(true);
        summon.setFocused(search);
    }

    private static void assertRetainedState(Screen target, String label) throws Exception {
        if (target instanceof WondrousStaffSummonScreen) {
            EditBox search = (EditBox) field(target, "searchBox");
            check(search.getValue().equals("minecraft:villager") && search.isFocused(), label + " search and focus retained");
            check(((Set<?>) field(target, "selected")).contains(VILLAGER), label + " selection retained");
        }
        if (target instanceof EntityAccelerationConfigScreen)
            check((boolean) field(target, "timers") && (int) field(target, "speed") == 8192, label + " mode and speed retained");
        if (target instanceof RangeAccelerationHistoryEditScreen)
            check((int) field(target, "speed") == 128 && (int) field(target, "sizeX") == 5
                    && (int) field(target, "sizeY") == 7 && (int) field(target, "sizeZ") == 9
                    && (int) field(target, "offsetX") == 3 && (int) field(target, "offsetY") == -2
                    && (int) field(target, "offsetZ") == 4, label + " live edit values retained");
        if (target instanceof OmniversalMyriadScreen) {
            EditBox search = (EditBox) field(target, "search");
            check(search.getValue().equals("iron") && search.isFocused(), label + " search and focus retained");
            check(((Set<?>) field(target, "enabled")).contains(ResourceLocation.parse("minecraft:iron_ingot")),
                    label + " mold selection retained");
        }
    }

    private static void beginHistoryRename(RangeAccelerationHistoryScreen history) throws Exception {
        int left = (int) field(history, "panelLeft"), top = (int) field(history, "panelTop");
        check(history.mouseClicked(left + 8, top + 6, 0), "history first name click");
        check(history.mouseClicked(left + 8, top + 6, 0), "history double-click opens rename");
        check(summary(0).id().equals(field(history, "renameId")), "history rename targets the clicked UUID");
        EditBox editor = (EditBox) field(history, "renameBox");
        check(editor != null && editor.isFocused(), "history rename editor initially focused");
        editor.setValue("重命名草稿 draft");
    }

    private static EditorState prepareEditorState(Screen target) throws Exception {
        String name = target instanceof WondrousStaffSummonScreen ? "searchBox"
                : target instanceof OmniversalMyriadScreen ? "search"
                : target instanceof RangeAccelerationHistoryScreen ? "renameBox" : null;
        if (name == null) return null;
        EditBox editor = (EditBox) field(target, name);
        editor.setCursorPosition(1);
        editor.setHighlightPos(Math.min(4, editor.getValue().length()));
        editor.setFocused(true);
        target.setFocused(editor);
        UUID renameId = target instanceof RangeAccelerationHistoryScreen ? (UUID) field(target, "renameId") : null;
        return new EditorState(name, editor, editor.getValue(), editor.getCursorPosition(), editor.getHighlighted(), renameId);
    }

    private static void assertEditorState(Screen target, EditorState expected, String label) throws Exception {
        if (expected == null) return;
        EditBox actual = (EditBox) field(target, expected.fieldName());
        check(actual == expected.editor(), label + " retains the same editor instance");
        check(actual.getValue().equals(expected.value()), label + " retains draft text");
        check(actual.getCursorPosition() == expected.cursor(), label + " retains caret position");
        check(actual.getHighlighted().equals(expected.highlighted()), label + " retains selected text");
        check(actual.isFocused() && target.getFocused() == actual, label + " retains editor input focus");
        if (expected.renameId() != null) {
            check(expected.renameId().equals(field(target, "renameId")), label + " retains rename target UUID");
            int index = (int) field(target, "renameIndex");
            var fields = (List<RangeAccelerationSavedData.Summary>) field(target, "fields");
            check(index >= 0 && expected.renameId().equals(fields.get(index).id()), label + " rename index matches UUID");
        }
    }

    private record EditorState(String fieldName, EditBox editor, String value, int cursor, String highlighted, UUID renameId) { }

    static void assertLayout(Screen screen, String label) throws Exception {
        FloatingWindow window = windowOrNull(screen);
        int left = window == null ? 0 : window.bodyLeft();
        int top = window == null ? 0 : window.bodyTop();
        int right = window == null ? screen.width : left + window.bodyWidth();
        int bottom = window == null ? screen.height : top + window.bodyHeight();
        if (window != null) check(window.left() >= 4 && window.top() >= 4
                && window.left() + window.width() <= screen.width - 4
                && window.top() + window.height() <= screen.height - 4, label + " frame inside viewport");
        List<AbstractWidget> visible = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget) {
            if (widget instanceof AbstractButton)
                check(widget.getTooltip() != null, label + " hover help: " + widget.getMessage().getString());
            if (!widget.visible) continue;
            check(widget.getWidth() > 0 && widget.getHeight() > 0, label + " positive widget bounds");
            check(widget.getX() >= left && widget.getRight() <= right,
                    label + " widget horizontal bounds: " + widget.getMessage().getString());
            if (widget.getBottom() > top && widget.getY() < bottom) visible.add(widget);
        }
        for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
            var first = visible.get(i); var second = visible.get(j);
            int overlapWidth = Math.min(first.getRight(), second.getRight()) - Math.max(first.getX(), second.getX());
            int overlapHeight = Math.min(bottom, Math.min(first.getBottom(), second.getBottom()))
                    - Math.max(top, Math.max(first.getY(), second.getY()));
            check(overlapWidth <= 0 || overlapHeight <= 0, label + " overlapping controls: "
                    + first.getMessage().getString() + " / " + second.getMessage().getString());
        }
    }

    private static int rowCapacity(Screen target) throws Exception {
        if (target instanceof ReclaimerScreen || target instanceof WondrousStaffSummonScreen) return (int) field(target, "visibleRows");
        if (target instanceof RangeAccelerationHistoryScreen) return (int) field(target, "rowsPerPage");
        if (target instanceof OmniversalMyriadScreen) return ((int) field(target, "listBottom") - (int) field(target, "listTop")) / 18;
        return -1;
    }

    private static void maximize(Screen target) throws Exception {
        var bounds = window(target);
        drag(target, bounds.left() + 1, bounds.top() + 1, 4 - bounds.left(), 4 - bounds.top());
        drag(target, bounds.left() + bounds.width() - 1, bounds.top() + bounds.height() - 1,
                target.width - 4 - bounds.left() - bounds.width(), target.height - 4 - bounds.top() - bounds.height());
    }

    private static void drag(Screen target, double x, double y, double dx, double dy) {
        check(target.mouseClicked(x, y, 0), "frame drag started");
        check(target.mouseDragged(x + dx, y + dy, 0, dx, dy), "frame drag handled");
        check(target.mouseReleased(x + dx, y + dy, 0), "frame drag released");
    }

    private static void clickReset(Screen target) throws Exception {
        var bounds = window(target);
        check(target.mouseClicked(bounds.left() + bounds.width() - 31, bounds.top() + 12, 0), "reset button handled");
    }

    private static FloatingWindow window(Screen target) throws Exception {
        FloatingWindow result = windowOrNull(target);
        check(result != null, target.getClass().getSimpleName() + " has floating window");
        return result;
    }
    private static FloatingWindow windowOrNull(Screen target) throws Exception {
        if (target instanceof FloatingScreen floating) return floating.floatingWindow();
        return target instanceof OmniversalMyriadScreen ? (FloatingWindow) field(target, "window") : null;
    }

    private static RangeAccelerationSavedData.Summary summary(int index) {
        return new RangeAccelerationSavedData.Summary(new UUID(0, index + 1), ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(20 + index, 64, 100), index, true, 128, 5, 7, 9, 3, -2, 4, "测试工厂 " + index);
    }
    private static List<Network.ReclaimerEntry> reclaimerEntries() {
        return java.util.stream.IntStream.range(0, 30).mapToObj(i -> new Network.ReclaimerEntry(i % 2 == 0,
                new UUID(1, i), new UUID(2, i), "Player_" + String.format("%02d", i), ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(i, 64, 0), "工厂设备 " + i)).toList();
    }

    private static OmniversalMyriadScreen myriad(Minecraft client) throws Exception {
        Inventory inventory = new Inventory(null);
        var screen = new OmniversalMyriadScreen(new OmniversalMyriadMenu(0, inventory, BlockPos.ZERO), inventory,
                Component.translatable("block.useless_stretcher.omniversal_myriad"));
        // The real menu has no slots; supplying initialized widgets avoids requestState without faking a world.
        set(screen, "initialized", true);
        EditBox search = new EditBox(client.font, 0, 0, 100, 18, Component.empty());
        search.setResponder(value -> {
            try { set(screen, "rowCache", null); set(screen, "scroll", 0); }
            catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        });
        set(screen, "search", search);
        set(screen, "clearButton", new SelectableAE2Button(0, 0, 86, 18,
                Component.translatable("gui.useless_stretcher.clear"), ignored -> {}));
        var entries = java.util.stream.IntStream.range(0, 36).mapToObj(i -> new MoldCatalog.MoldEntry("minecraft",
                ResourceLocation.fromNamespaceAndPath("minecraft", "sample_" + i), new ItemStack(i % 2 == 0 ? Items.IRON_INGOT : Items.GOLD_INGOT))).toList();
        set(screen, "catalog", Map.of("minecraft", entries));
        ((Set<String>) field(screen, "expandedMods")).add("minecraft");
        ((Set<ResourceLocation>) field(screen, "enabled")).add(ResourceLocation.parse("minecraft:iron_ingot"));
        return screen;
    }

    private static Field reflected(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(target.getClass() + ": " + name);
    }
    private static Object field(Object target, String name) throws ReflectiveOperationException { return reflected(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws ReflectiveOperationException { reflected(target, name).set(target, value); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    /** The fixture owns the viewport clear while rendering real screens without world-dependent ticks. */
    private static final class WindowPreview extends Screen {
        private final Screen target;
        private WindowPreview(Screen target) { super(Component.empty()); this.target = target; }
        @Override protected void init() { target.init(minecraft, width, height); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF181B22);
            target.renderWithTooltip(graphics, x, y, partial);
        }
        @Override public void removed() { target.removed(); }
        @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) { }
    }
}
