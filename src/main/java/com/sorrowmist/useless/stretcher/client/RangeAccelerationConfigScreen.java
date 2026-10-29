package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Button-based range setup using the same visual language as the upstream G screen.
 *
 * <p>Rebuilt on Useless Mod's Apple UI API: the live new-range speed caption, the placement and
 * marking switches, the three size/offset steppers, the acceleration/sleep list switches and the
 * footer are declared with {@code Ui.column/section/row} and laid out by
 * {@code FloatingScreen.layoutBody}, so no card rectangle or row coordinate is measured by hand.
 * Switches, sliders and step buttons stay native {@link SelectableAE2Button}/{@link AE2RangeSlider}
 * instances embedded with {@code FloatingScreen.widgetNode}: their callbacks, keyboard focus,
 * narration, axis/list hover help and the {@link #updateButtons()} refresh from the staff
 * components are unchanged.</p>
 */
public final class RangeAccelerationConfigScreen extends StaffScreen {
    /** Mode switches keep the compact 17px AE2 height; the remaining controls are 18px. */
    private static final int MODE_HEIGHT = 17;
    private static final int BUTTON_HEIGHT = 18;
    /** Step buttons are square; the offset readout is a disabled three-digit button. */
    private static final int STEP_WIDTH = 18;
    private static final int OFFSET_VALUE_WIDTH = 34;
    private static final int ROW_GAP = 2;
    private static final int AXIS_MIN = 1;
    private static final int AXIS_MAX = 15;

    private final Screen parent;
    private final InteractionHand hand;
    private boolean placementMode;
    private boolean filterMarkingMode;
    private boolean markSleepList;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private boolean accelerationWhitelistMode;
    private boolean sleepWhitelistMode;
    private int accelerationMarkCount;
    private int sleepMarkCount;
    private SelectableAE2Button placementButton;
    private SelectableAE2Button accelerationListButton;
    private SelectableAE2Button sleepListButton;
    private SelectableAE2Button markingButton;
    private SelectableAE2Button offsetXButton;
    private SelectableAE2Button offsetYButton;
    private SelectableAE2Button offsetZButton;
    private boolean settingsLoaded;

    public RangeAccelerationConfigScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.range.title"), Tab.RANGE, parent, hand);
        this.parent = parent;
        this.hand = hand;
    }

    @Override protected void refreshStaffSettings() { settingsLoaded = false; }

    @Override
    protected void initContent() {
        if (!settingsLoaded) {
            ItemStack staff = currentStaff();
            placementMode = RangeAccelerationSettings.placementMode(staff);
            filterMarkingMode = RangeAccelerationSettings.filterMarkingMode(staff);
            markSleepList = RangeAccelerationSettings.markSleepList(staff);
            sizeX = RangeAccelerationSettings.sizeX(staff);
            sizeY = RangeAccelerationSettings.sizeY(staff);
            sizeZ = RangeAccelerationSettings.sizeZ(staff);
            offsetX = RangeAccelerationSettings.offsetX(staff);
            offsetY = RangeAccelerationSettings.offsetY(staff);
            offsetZ = RangeAccelerationSettings.offsetZ(staff);
            accelerationWhitelistMode = RangeAccelerationSettings.whitelistMode(staff);
            sleepWhitelistMode = RangeAccelerationSettings.sleepWhitelistMode(staff);
            accelerationMarkCount = RangeAccelerationSettings.accelerationMarkCount(staff);
            sleepMarkCount = RangeAccelerationSettings.sleepMarkCount(staff);
            settingsLoaded = true;
        }
        // Widget instances are rebuilt on every relayout (resize / scroll), exactly like before.
        placementButton = null;
        markingButton = null;
        accelerationListButton = null;
        sleepListButton = null;
        offsetXButton = null;
        offsetYButton = null;
        offsetZButton = null;
        layoutBody(buildPage());
        updateButtons();
    }

    // ------------------------------------------------------------------ 声明式页面
    private UiNode buildPage() {
        return Ui.column(
                Ui.section(this::speedCaption, modeRow()),
                Ui.card(axisRow('X', sizeX, offsetX), axisRow('Y', sizeY, offsetY),
                        axisRow('Z', sizeZ, offsetZ)).gap(ROW_GAP),
                Ui.card(listRow()),
                footerRow()
        ).gap(4);
    }

    /** The live new-range multiplier replaces the hand drawn speed line. */
    private Component speedCaption() {
        return Component.translatable("gui.useless_stretcher.range.speed",
                WondrousStaffAcceleration.getSpeed(currentStaff()));
    }

    /** Placement and list marking share the width evenly as two parallel mode switches. */
    private UiNode modeRow() {
        placementButton = new SelectableAE2Button(0, 0, 100, MODE_HEIGHT, placementMessage(), ignored -> {
            placementMode = !placementMode;
            if (placementMode) filterMarkingMode = false;
            apply();
        });
        markingButton = new SelectableAE2Button(0, 0, 100, MODE_HEIGHT, markingMessage(), ignored -> {
            filterMarkingMode = !filterMarkingMode;
            if (filterMarkingMode) placementMode = false;
            apply();
        });
        return Ui.row(flexible(placementButton, MODE_HEIGHT), flexible(markingButton, MODE_HEIGHT))
                .gap(ROW_GAP).height(MODE_HEIGHT);
    }

    /**
     * One axis per row: size stepper, then the offset stepper group. The slider grows into the free
     * space, so the same row works in the compact and the widest window without a measured width.
     */
    private UiNode axisRow(char axis, int initialSize, int initialOffset) {
        AE2RangeSlider slider = new AE2RangeSlider(0, 0, 60, BUTTON_HEIGHT, String.valueOf(axis),
                AXIS_MIN, AXIS_MAX, initialSize, value -> setAxis(axis, value));
        SelectableAE2Button sizeDecrease = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("-"), ignored -> slider.step(-1));
        sizeDecrease.setTooltip(slider.getTooltip());
        SelectableAE2Button sizeIncrease = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("+"), ignored -> slider.step(1));
        sizeIncrease.setTooltip(slider.getTooltip());

        SelectableAE2Button offsetDecrease = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("-"), ignored -> stepOffset(axis, -1));
        ButtonHelp.set(offsetDecrease, "offset_decrease", String.valueOf(axis));
        SelectableAE2Button offsetValue = new SelectableAE2Button(0, 0, OFFSET_VALUE_WIDTH, BUTTON_HEIGHT,
                offsetMessage(initialOffset), ignored -> { });
        offsetValue.active = false;
        ButtonHelp.set(offsetValue, "offset_axis", String.valueOf(axis));
        SelectableAE2Button offsetIncrease = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("+"), ignored -> stepOffset(axis, 1));
        ButtonHelp.set(offsetIncrease, "offset_increase", String.valueOf(axis));
        switch (axis) {
            case 'X' -> offsetXButton = offsetValue;
            case 'Y' -> offsetYButton = offsetValue;
            default -> offsetZButton = offsetValue;
        }
        return Ui.row(
                fixed(sizeDecrease, STEP_WIDTH),
                widgetNode(slider).width(0).grow(),
                fixed(sizeIncrease, STEP_WIDTH),
                fixed(offsetDecrease, STEP_WIDTH),
                fixed(offsetValue, OFFSET_VALUE_WIDTH),
                fixed(offsetIncrease, STEP_WIDTH)
        ).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    /** Acceleration and sleep lists stay one tap apart; the count is part of their message. */
    private UiNode listRow() {
        accelerationListButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                accelerationListMessage(), ignored -> {
                    if (markSleepList) markSleepList = false;
                    else accelerationWhitelistMode = !accelerationWhitelistMode;
                    apply();
                });
        sleepListButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                sleepListMessage(), ignored -> {
                    if (!markSleepList) markSleepList = true;
                    else sleepWhitelistMode = !sleepWhitelistMode;
                    apply();
                });
        ButtonHelp.set(accelerationListButton, "acceleration_list");
        ButtonHelp.set(sleepListButton, "sleep_list");
        return Ui.row(flexible(accelerationListButton, BUTTON_HEIGHT),
                flexible(sleepListButton, BUTTON_HEIGHT)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    private UiNode footerRow() {
        SelectableAE2Button history = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.range.history"),
                ignored -> navigate(Tab.HISTORY));
        SelectableAE2Button back = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        return Ui.row(flexible(history, BUTTON_HEIGHT), flexible(back, BUTTON_HEIGHT))
                .gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    // ------------------------------------------------------------------ 控件工厂
    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(AbstractWidget widget, int height) {
        return widgetNode(widget).width(0).grow().height(height);
    }

    /** Fixed-width native control; its height stays the real widget height. */
    private UiNode fixed(AbstractWidget widget, int width) {
        return widgetNode(widget).width(width);
    }

    // ------------------------------------------------------------------ 状态
    private void setAxis(char axis, int value) {
        switch (axis) {
            case 'X' -> sizeX = value;
            case 'Y' -> sizeY = value;
            default -> sizeZ = value;
        }
        apply();
    }

    private void stepOffset(char axis, int delta) {
        switch (axis) {
            case 'X' -> offsetX = RangeAccelerationSavedData.clampOffset(offsetX + delta);
            case 'Y' -> offsetY = RangeAccelerationSavedData.clampOffset(offsetY + delta);
            default -> offsetZ = RangeAccelerationSavedData.clampOffset(offsetZ + delta);
        }
        apply();
    }

    private void apply() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        RangeAccelerationSettings.set(staff, placementMode, filterMarkingMode, markSleepList,
                sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                accelerationWhitelistMode, sleepWhitelistMode);
        RangeNetwork.sendSettings(hand, placementMode, filterMarkingMode, markSleepList,
                sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                accelerationWhitelistMode, sleepWhitelistMode);
        updateButtons();
    }

    private void updateButtons() {
        if (placementButton != null) {
            placementButton.setSelected(placementMode);
            placementButton.setMessage(placementMessage());
        }
        if (accelerationListButton != null) {
            accelerationListButton.setSelected(!markSleepList);
            accelerationListButton.setMessage(accelerationListMessage());
        }
        if (sleepListButton != null) {
            sleepListButton.setSelected(markSleepList);
            sleepListButton.setMessage(sleepListMessage());
        }
        if (markingButton != null) {
            markingButton.setSelected(filterMarkingMode);
            markingButton.setMessage(markingMessage());
        }
        if (offsetXButton != null) offsetXButton.setMessage(offsetMessage(offsetX));
        if (offsetYButton != null) offsetYButton.setMessage(offsetMessage(offsetY));
        if (offsetZButton != null) offsetZButton.setMessage(offsetMessage(offsetZ));
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack staff = currentStaff();
        int accelerationCount = RangeAccelerationSettings.accelerationMarkCount(staff);
        int sleepCount = RangeAccelerationSettings.sleepMarkCount(staff);
        if (accelerationCount != accelerationMarkCount || sleepCount != sleepMarkCount) {
            accelerationMarkCount = accelerationCount;
            sleepMarkCount = sleepCount;
            updateButtons();
        }
    }

    /** Cards, captions and help come from the shared Apple host; nothing is drawn by hand here. */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override
    public void onClose() {
        returnToPage(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack currentStaff() {
        return minecraft == null || minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private Component placementMessage() {
        return Component.translatable("gui.useless_stretcher.range.placement",
                Component.translatable(placementMode
                        ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private Component accelerationListMessage() {
        return Component.translatable("gui.useless_stretcher.range.list_with_count",
                Component.translatable(accelerationWhitelistMode
                        ? "gui.useless_stretcher.range.acceleration_whitelist"
                        : "gui.useless_stretcher.range.acceleration_blacklist"), accelerationMarkCount);
    }

    private Component sleepListMessage() {
        return Component.translatable("gui.useless_stretcher.range.list_with_count",
                Component.translatable(sleepWhitelistMode
                        ? "gui.useless_stretcher.range.sleep_whitelist"
                        : "gui.useless_stretcher.range.sleep_blacklist"), sleepMarkCount);
    }

    private Component markingMessage() {
        return Component.translatable("gui.useless_stretcher.range.marking",
                Component.translatable(filterMarkingMode
                        ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private static Component offsetMessage(int value) {
        return Component.translatable("gui.useless_stretcher.range.offset_short", value);
    }
}
