package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Flex;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Edits an existing Time Flow's speed and geometry without replacing its persistent field UUID.
 *
 * <p>Rebuilt on Useless Mod's Apple UI API: the location line, the multiplier card, the entity-timer
 * card, the size/offset groups and the back button are declared with {@code Ui.column/section/row}
 * and laid out by {@code FloatingScreen.layoutBody}, so no group rectangle, group offset or
 * wrapped-text height is measured by hand. The wide window still puts the two slider groups side by
 * side and a narrow one stacks them. Sliders, step and toggle buttons stay native
 * {@link SelectableAE2Button}/{@link AE2RangeSlider} instances embedded with
 * {@code FloatingScreen.widgetNode}, so every live save, keyboard focus, narration and hover help is
 * unchanged.</p>
 */
public final class RangeAccelerationHistoryEditScreen extends StaffScreen {
    private static final int[] SPEED_PRESETS = {1, 2, 4, 16, 32, 64, 128, 256, 512, 1024};
    private static final String SPEED_GROUP = "gui.useless_stretcher.range.speed_group";
    private static final String SIZE_GROUP = "gui.useless_stretcher.range.size_group";
    private static final String OFFSET_GROUP = "gui.useless_stretcher.range.offset_group";
    /** Embedded native heights kept from the compact AE2 look. */
    private static final int BUTTON_HEIGHT = 18;
    private static final int STEP_WIDTH = 18;
    private static final int BACK_WIDTH = 64;
    private static final int ROW_GAP = 2;
    /** Both slider groups fit side by side from this body width on; below it they stack. */
    private static final int SIDE_BY_SIDE_MIN = 480;

    private final Screen parent;
    private final RangeAccelerationSavedData.Summary field;
    private int speed;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private boolean entityTimerMode;
    private int entityTimerSpeed;
    private SelectableAE2Button timerButton;
    private SelectableAE2Button timerSpeedButton;
    private SelectableAE2Button speedButton;

    public RangeAccelerationHistoryEditScreen(Screen parent,
                                              RangeAccelerationSavedData.Summary field) {
        super(Component.translatable("gui.useless_stretcher.range.edit_title"), Tab.HISTORY, parent, staffHand(parent));
        this.parent = parent;
        this.field = field;
        speed = RangeAccelerationSavedData.clampSpeed(field.speed());
        sizeX = field.sizeX();
        sizeY = field.sizeY();
        sizeZ = field.sizeZ();
        offsetX = field.offsetX();
        offsetY = field.offsetY();
        offsetZ = field.offsetZ();
        entityTimerMode = field.entityTimerMode();
        entityTimerSpeed = EntityTimerAcceleration.normalizeSpeed(field.entityTimerSpeed());
    }

    @Override
    protected void initContent() {
        // Widget instances are rebuilt on every relayout (resize / scroll), exactly like before.
        speedButton = null;
        timerButton = null;
        timerSpeedButton = null;
        layoutBody(buildPage());
    }

    // ------------------------------------------------------------------ 声明式页面
    private UiNode buildPage() {
        Flex page = Ui.column().gap(4);
        page.add(wrappedText(this::locationText, StretcherScreenStyle.SUBTLE_TEXT_COLOR));
        page.add(Ui.section(Ui.tr(SPEED_GROUP), speedRow()));
        page.add(Ui.card(timerRow()));
        if (contentWidth() >= SIDE_BY_SIDE_MIN) {
            page.add(Ui.row(sizeCard().width(0).grow(), offsetCard().width(0).grow()).gap(4));
        } else {
            page.add(sizeCard());
            page.add(offsetCard());
        }
        page.add(Ui.row(Ui.spacer(), widgetNode(backButton()).width(BACK_WIDTH)).height(BUTTON_HEIGHT));
        return page;
    }

    private UiNode sizeCard() {
        return Ui.section(Ui.tr(SIZE_GROUP), Ui.column(
                axisRow('X', false, sizeX), axisRow('Y', false, sizeY), axisRow('Z', false, sizeZ)).gap(ROW_GAP));
    }

    private UiNode offsetCard() {
        return Ui.section(Ui.tr(OFFSET_GROUP), Ui.column(
                axisRow('X', true, offsetX), axisRow('Y', true, offsetY), axisRow('Z', true, offsetZ)).gap(ROW_GAP));
    }

    /** Multiplier stepper: the read-only value sits between the two step buttons. */
    private UiNode speedRow() {
        SelectableAE2Button slower = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("-"), ignored -> stepSpeed(-1));
        slower.setTooltip(Tooltip.create(ButtonHelp.text("edit_speed")));
        speedButton = new SelectableAE2Button(0, 0, 60, BUTTON_HEIGHT, speedMessage(), ignored -> { });
        speedButton.active = false;
        ButtonHelp.set(speedButton, "edit_speed");
        SelectableAE2Button faster = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("+"), ignored -> stepSpeed(1));
        faster.setTooltip(Tooltip.create(ButtonHelp.text("edit_speed")));
        return Ui.row(fixed(slower, STEP_WIDTH), widgetNode(speedButton).width(0).grow(),
                fixed(faster, STEP_WIDTH)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    /** Entity timer mode and its own multiplier share the width evenly. */
    private UiNode timerRow() {
        timerButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT, timerMessage(), ignored -> {
            entityTimerMode = !entityTimerMode;
            timerButton.setMessage(timerMessage());
            timerSpeedButton.active = entityTimerMode;
            persist();
        });
        timerButton.setTooltip(Tooltip.create(ButtonHelp.text("entity_timer")));
        timerSpeedButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                timerSpeedMessage(), ignored -> stepTimerSpeed(1));
        timerSpeedButton.active = entityTimerMode;
        timerSpeedButton.setTooltip(Tooltip.create(ButtonHelp.text("entity_timer_speed")));
        return Ui.row(flexible(timerButton), flexible(timerSpeedButton)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    private UiNode axisRow(char axis, boolean offset, int initialValue) {
        int min = offset ? RangeAccelerationSavedData.MIN_OFFSET : RangeAccelerationSavedData.MIN_SIZE;
        int max = offset ? RangeAccelerationSavedData.MAX_OFFSET : RangeAccelerationSavedData.MAX_SIZE;
        AE2RangeSlider slider = new AE2RangeSlider(0, 0, 60, BUTTON_HEIGHT, String.valueOf(axis),
                min, max, initialValue, value -> setValue(axis, offset, value));
        SelectableAE2Button decrease = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("-"), ignored -> slider.step(-1));
        decrease.setTooltip(slider.getTooltip());
        SelectableAE2Button increase = new SelectableAE2Button(0, 0, STEP_WIDTH, BUTTON_HEIGHT,
                Component.literal("+"), ignored -> slider.step(1));
        increase.setTooltip(slider.getTooltip());
        return Ui.row(fixed(decrease, STEP_WIDTH), widgetNode(slider).width(0).grow(),
                fixed(increase, STEP_WIDTH)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    private SelectableAE2Button backButton() {
        return new SelectableAE2Button(0, 0, BACK_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
    }

    // ------------------------------------------------------------------ 控件工厂
    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(AbstractWidget widget) {
        return widgetNode(widget).width(0).grow().height(BUTTON_HEIGHT);
    }

    /** Fixed-width native control; its height stays the real widget height. */
    private UiNode fixed(AbstractWidget widget, int width) {
        return widgetNode(widget).width(width);
    }

    private Component locationText() {
        return Component.literal(field.dimension() + "  "
                + field.center().getX() + ", " + field.center().getY() + ", " + field.center().getZ());
    }

    // ------------------------------------------------------------------ 状态
    private void stepSpeed(int delta) {
        int index = 0;
        for (int i = 0; i < SPEED_PRESETS.length; i++) {
            if (SPEED_PRESETS[i] >= speed) {
                index = i;
                break;
            }
            index = i;
        }
        index = Math.max(0, Math.min(SPEED_PRESETS.length - 1, index + delta));
        if (speed == SPEED_PRESETS[index]) return;
        speed = SPEED_PRESETS[index];
        if (speedButton != null) speedButton.setMessage(speedMessage());
        persist();
    }

    private void setValue(char axis, boolean offset, int value) {
        if (offset) {
            switch (axis) {
                case 'X' -> offsetX = value;
                case 'Y' -> offsetY = value;
                default -> offsetZ = value;
            }
        } else {
            switch (axis) {
                case 'X' -> sizeX = value;
                case 'Y' -> sizeY = value;
                default -> sizeZ = value;
            }
        }
        persist();
    }

    private void persist() {
        RangeNetwork.editHistory(field.id(), speed, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                entityTimerMode, entityTimerSpeed);
    }

    private void stepTimerSpeed(int delta) {
        entityTimerSpeed = EntityTimerAcceleration.normalizeSpeed(entityTimerSpeed << delta);
        if (timerSpeedButton != null) timerSpeedButton.setMessage(timerSpeedMessage());
        persist();
    }

    /** Cards, captions and the wrapped location are painted by the shared Apple host. */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        // Edit replies received while this screen is open do not update the history screen.
        RangeNetwork.requestHistory();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component speedMessage() {
        return Component.literal("x" + speed);
    }

    private Component timerMessage() {
        return Component.translatable("gui.useless_stretcher.range.entity_timer_" +
                (entityTimerMode ? "on" : "off"));
    }

    private Component timerSpeedMessage() {
        return Component.literal("T x" + entityTimerSpeed);
    }
}
