package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Button-based acceleration selector styled after Useless Mod's current G configuration screen. */
public final class WondrousStaffConfigScreen extends Screen {
    private static final int[] GEARS = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 193;

    private final InteractionHand hand;
    private final List<ChoiceButton> speedButtons = new ArrayList<>();
    private final List<ChoiceButton> modeButtons = new ArrayList<>();
    private SelectableAE2Button accelerationButton;
    private int panelLeft;
    private int panelTop;
    private int selectedSpeed;
    private int selectedMode;
    private boolean accelerationEnabled;

    public WondrousStaffConfigScreen(InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_config.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        panelLeft = (width - Math.min(PANEL_WIDTH, width - 12)) / 2;
        panelTop = Math.max(6, (height - PANEL_HEIGHT) / 2);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        ItemStack staff = currentStaff();
        selectedSpeed = WondrousStaffAcceleration.getSpeed(staff);
        selectedMode = WondrousStaffAcceleration.getMode(staff);
        accelerationEnabled = WondrousStaffAcceleration.isEnabled(staff);

        speedButtons.clear();
        modeButtons.clear();
        accelerationButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 96, panelTop + 5, 88, 16,
                accelerationMessage(), ignored -> toggleAcceleration()));

        int cardLeft = panelLeft + 7;
        int cardWidth = panelWidth - 14;
        int gap = 3;
        int buttonWidth = (cardWidth - 10 - gap * 4) / 5;
        for (int i = 0; i < GEARS.length; i++) {
            int speed = GEARS[i];
            int x = cardLeft + 5 + (i % 5) * (buttonWidth + gap);
            int y = panelTop + 41 + (i / 5) * 19;
            Component label = speed == 0
                    ? Component.translatable("gui.useless_stretcher.speed_off")
                    : Component.literal("x" + speed);
            SelectableAE2Button button = addRenderableWidget(new SelectableAE2Button(
                    x, y, buttonWidth, 17, label, ignored -> selectSpeed(speed)));
            speedButtons.add(new ChoiceButton(speed, button));
        }

        String[] modeKeys = {
                "gui.useless_stretcher.mode_normal",
                "gui.useless_stretcher.mode_permanent",
                "gui.useless_stretcher.mode_permanent_no_throttle"
        };
        for (int mode = 0; mode < modeKeys.length; mode++) {
            int value = mode;
            SelectableAE2Button button = addRenderableWidget(new SelectableAE2Button(
                    cardLeft + 5, panelTop + 102 + mode * 19, cardWidth - 10, 17,
                    Component.translatable(modeKeys[mode]), ignored -> selectMode(value)));
            modeButtons.add(new ChoiceButton(mode, button));
        }
        int footerWidth = (cardWidth - 3) / 2;
        addRenderableWidget(new SelectableAE2Button(
                cardLeft, panelTop + 169, footerWidth, 17,
                Component.translatable("gui.useless_stretcher.range.open"),
                ignored -> minecraft.setScreen(new RangeAccelerationConfigScreen(this, hand))));
        addRenderableWidget(new SelectableAE2Button(
                cardLeft + footerWidth + 3, panelTop + 169, cardWidth - footerWidth - 3, 17,
                Component.translatable("gui.useless_stretcher.range.history"),
                ignored -> minecraft.setScreen(new RangeAccelerationHistoryScreen(this))));
        updateSelection();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, panelLeft + 8, panelTop + 8,
                StretcherScreenStyle.TEXT_COLOR, false);

        int cardLeft = panelLeft + 7;
        int cardRight = panelLeft + panelWidth - 7;
        StretcherScreenStyle.drawInset(graphics, cardLeft, panelTop + 24, cardRight, panelTop + 80);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_config.speed"),
                cardLeft + 5, panelTop + 29, StretcherScreenStyle.TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, cardLeft, panelTop + 84, cardRight, panelTop + 163);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_config.duration"),
                cardLeft + 5, panelTop + 89, StretcherScreenStyle.TEXT_COLOR, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The panel is drawn before Screen.render; allowing the default background here would blur the UI itself.
    }

    private void selectSpeed(int speed) {
        selectedSpeed = speed;
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        staff.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        sendSelection();
    }

    private void selectMode(int mode) {
        selectedMode = mode;
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        WondrousStaffAcceleration.setMode(staff, mode);
        sendSelection();
    }

    private void toggleAcceleration() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        accelerationEnabled = !accelerationEnabled;
        staff.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), accelerationEnabled);
        sendSelection();
    }

    private void sendSelection() {
        updateSelection();
        Network.sendWondrousStaffSpeed(selectedSpeed, selectedMode, accelerationEnabled, hand);
    }

    private void updateSelection() {
        speedButtons.forEach(entry -> entry.button().setSelected(entry.value() == selectedSpeed));
        modeButtons.forEach(entry -> entry.button().setSelected(entry.value() == selectedMode));
        if (accelerationButton != null) {
            accelerationButton.setSelected(accelerationEnabled);
            accelerationButton.setMessage(accelerationMessage());
        }
    }

    private Component accelerationMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.master",
                Component.translatable(accelerationEnabled
                        ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private ItemStack currentStaff() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return minecraft.player.getItemInHand(hand);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ChoiceButton(int value, SelectableAE2Button button) {
    }
}
