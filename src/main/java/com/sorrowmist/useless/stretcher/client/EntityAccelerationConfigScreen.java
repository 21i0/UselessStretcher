package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Flex;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Separate entity controls never change machine or range multipliers.
 *
 * <p>Rebuilt on Useless Mod's Apple UI API: the two mode choices, the timer-multiplier card and the
 * wrapped explanation are declared with {@code Ui.row/section/column} and laid out by
 * {@code FloatingScreen.layoutBody}, and the explanation grows with its real line count through
 * {@code FloatingScreen.wrappedText}, so no card coordinate or wrapped-text height is measured by
 * hand any more. The stateful choices stay native {@link SelectableAE2Button}s and are embedded with
 * {@code FloatingScreen.widgetNode}: they keep their selected paint, their hover help and the live
 * refresh from the synchronized staff components.</p>
 */
public final class EntityAccelerationConfigScreen extends StaffScreen {
    /** Biological timer gears offered by the page: x2 … x32768 (fifteen steps). */
    private static final int[] TIMER_SPEEDS = {
            2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
    private static final String KEY_PREFIX = "gui.useless_stretcher.entity_acceleration.";
    private static final String TITLE_SPEED = KEY_PREFIX + "speed";
    /** Row height of an embedded native button: compact AE2 look while staying clickable. */
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    /** Narrowest useful gear cell; it only picks the gear grid column count (the API owns the layout). */
    private static final int MIN_CELL = 48;
    private static final int BACK_WIDTH = 80;

    private final Screen parent;
    private final InteractionHand hand;
    private final List<ModeChoice> modeButtons = new ArrayList<>();
    private final List<SpeedChoice> speedButtons = new ArrayList<>();
    private boolean timers;
    private int speed;
    private boolean settingsLoaded;

    public EntityAccelerationConfigScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable(KEY_PREFIX + "title"), Tab.ENTITY, parent, hand);
        this.parent = parent;
        this.hand = hand;
    }

    private ItemStack staff() { return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand); }

    @Override protected void refreshStaffSettings() { settingsLoaded = false; }

    @Override protected void initContent() {
        if (!settingsLoaded) {
            timers = WondrousStaffAcceleration.usesEntityTimers(staff());
            speed = WondrousStaffAcceleration.getEntityTimerSpeed(staff());
            settingsLoaded = true;
        }
        // Widget instances are rebuilt on every relayout (resize / scroll), exactly like before.
        modeButtons.clear();
        speedButtons.clear();
        layoutBody(buildPage());
        updateSelection();
    }

    // ------------------------------------------------------------------ 声明式页面
    /** One column of Apple controls; every width is derived from the content width by the API. */
    private UiNode buildPage() {
        Flex page = Ui.column().gap(4);
        page.add(modeRow());
        page.add(speedCard());
        page.add(wrappedText(this::hint, StretcherScreenStyle.SUBTLE_TEXT_COLOR));
        page.add(backRow());
        return page;
    }

    /** Full-tick mode and biological timer mode share the width evenly as two parallel choices. */
    private UiNode modeRow() {
        return Ui.row(modeNode(false), modeNode(true)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    /**
     * Timer gears in an adaptive grid: the column count follows the content width, so even the
     * narrowest supported window keeps a readable multi-column grid, and a short last line is padded
     * with equal spacers to stay aligned.
     */
    private UiNode speedCard() {
        int columns = Math.max(2, Math.min(TIMER_SPEEDS.length, Math.max(1, (innerWidth() + ROW_GAP) / MIN_CELL)));
        Flex grid = Ui.column().gap(ROW_GAP);
        for (int first = 0; first < TIMER_SPEEDS.length; first += columns) {
            Flex line = Ui.row().gap(ROW_GAP).height(BUTTON_HEIGHT);
            for (int offset = 0; offset < columns; offset++) {
                int index = first + offset;
                line.add(index < TIMER_SPEEDS.length ? speedNode(TIMER_SPEEDS[index]) : Ui.spacer().width(0).grow());
            }
            grid.add(line);
        }
        // The caption replaces the hand-drawn "speed" line: card + grey group title come from the API.
        return Ui.section(Ui.tr(TITLE_SPEED), grid);
    }

    /** Trailing back button: the growing spacer pushes it right without a hand-computed offset. */
    private UiNode backRow() {
        SelectableAE2Button back = new SelectableAE2Button(0, 0, BACK_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        return Ui.row(Ui.spacer(), widgetNode(back).width(BACK_WIDTH).height(BUTTON_HEIGHT)).height(BUTTON_HEIGHT);
    }

    // ------------------------------------------------------------------ 控件工厂
    private UiNode modeNode(boolean timerMode) {
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 120, BUTTON_HEIGHT,
                Component.translatable(KEY_PREFIX + (timerMode ? "timers" : "ticks")),
                ignored -> { timers = timerMode; save(); });
        button.setTooltip(Tooltip.create(Component.translatable(KEY_PREFIX
                + (timerMode ? "timer_tooltip" : "tick_tooltip"))));
        modeButtons.add(new ModeChoice(timerMode, button));
        return flexible(button);
    }

    private UiNode speedNode(int gear) {
        SelectableAE2Button button = new SelectableAE2Button(0, 0, MIN_CELL, BUTTON_HEIGHT,
                Component.literal("x" + gear), ignored -> { speed = gear; save(); });
        button.setTooltip(Tooltip.create(Component.translatable(KEY_PREFIX + "speed_tooltip", gear)));
        speedButtons.add(new SpeedChoice(gear, button));
        return flexible(button);
    }

    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(AbstractWidget widget) {
        return widgetNode(widget).width(0).grow().height(BUTTON_HEIGHT);
    }

    /** Card interior used only for the gear-grid heuristic; the card itself pads by the theme value. */
    private int innerWidth() {
        return Math.max(60, contentWidth() - Ui.t().cardPad * 2);
    }

    /** Cards, captions and the wrapped hint are painted by the shared Apple host; nothing is drawn by hand. */
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private Component hint() {
        return Component.translatable(KEY_PREFIX + (timers ? "timer_hint" : "tick_hint"));
    }

    // ------------------------------------------------------------------ 状态
    private void save() {
        ItemStack held = staff();
        if (!held.is(ModItems.WONDROUS_STAFF.get())) return;
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(held, StretcherComponents.ENTITY_TIMER_MODE.get(), timers);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(held, StretcherComponents.ENTITY_TIMER_SPEED.get(), speed);
        Network.sendEntityTimerSettings(timers, speed, hand);
        rebuildWidgets();
    }

    /** Timer gears also depend on the mode and on the server option, so both bindings are re-applied live. */
    private void updateSelection() {
        boolean allowed = StretcherConfig.serverStaffAcceleration();
        for (ModeChoice entry : modeButtons) {
            entry.button().active = allowed;
            entry.button().setSelected(entry.timerMode() == timers);
        }
        for (SpeedChoice entry : speedButtons) {
            entry.button().active = allowed && timers;
            entry.button().setSelected(entry.gear() == speed);
        }
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.player != null && !staff().is(ModItems.WONDROUS_STAFF.get())) { onClose(); return; }
        boolean currentTimers = WondrousStaffAcceleration.usesEntityTimers(staff());
        int currentSpeed = WondrousStaffAcceleration.getEntityTimerSpeed(staff());
        if (timers != currentTimers || speed != currentSpeed) {
            timers = currentTimers;
            speed = currentSpeed;
            rebuildWidgets();
        }
        updateSelection(); // Reflect live server config updates while this page stays open.
    }

    @Override public void onClose() { returnToPage(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private record ModeChoice(boolean timerMode, SelectableAE2Button button) { }

    private record SpeedChoice(int gear, SelectableAE2Button button) { }
}
