package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Button-based range setup using the same visual language as the upstream G screen. */
public final class RangeAccelerationConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 276;
    private static final int PANEL_HEIGHT = 180;

    private final Screen parent;
    private final InteractionHand hand;
    private int panelLeft;
    private int panelTop;
    private boolean placementMode;
    private boolean filterMarkingMode;
    private boolean markSleepList;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private boolean accelerationWhitelistMode;
    private boolean sleepWhitelistMode;
    private int accelerationMarkCount;
    private int sleepMarkCount;
    private SelectableAE2Button placementButton;
    private SelectableAE2Button accelerationListButton;
    private SelectableAE2Button sleepListButton;
    private SelectableAE2Button markingButton;

    public RangeAccelerationConfigScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.range.title"));
        this.parent = parent;
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - PANEL_HEIGHT) / 2);
        ItemStack staff = currentStaff();
        placementMode = RangeAccelerationSettings.placementMode(staff);
        filterMarkingMode = RangeAccelerationSettings.filterMarkingMode(staff);
        markSleepList = RangeAccelerationSettings.markSleepList(staff);
        sizeX = RangeAccelerationSettings.sizeX(staff);
        sizeY = RangeAccelerationSettings.sizeY(staff);
        sizeZ = RangeAccelerationSettings.sizeZ(staff);
        accelerationWhitelistMode = RangeAccelerationSettings.whitelistMode(staff);
        sleepWhitelistMode = RangeAccelerationSettings.sleepWhitelistMode(staff);
        accelerationMarkCount = RangeAccelerationSettings.accelerationMarkCount(staff);
        sleepMarkCount = RangeAccelerationSettings.sleepMarkCount(staff);

        placementButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 103, panelTop + 5, 95, 17,
                placementMessage(), ignored -> {
                    placementMode = !placementMode;
                    if (placementMode) filterMarkingMode = false;
                    apply();
                }));
        markingButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 103, panelTop + 25, 95, 17,
                markingMessage(), ignored -> {
                    filterMarkingMode = !filterMarkingMode;
                    if (filterMarkingMode) placementMode = false;
                    apply();
                }));

        int cardLeft = panelLeft + 7;
        int cardWidth = panelWidth - 14;
        addAxisSlider(cardLeft + 5, panelTop + 48, 'X', sizeX);
        addAxisSlider(cardLeft + 5, panelTop + 69, 'Y', sizeY);
        addAxisSlider(cardLeft + 5, panelTop + 90, 'Z', sizeZ);

        int half = (cardWidth - 13) / 2;
        accelerationListButton = addRenderableWidget(new SelectableAE2Button(
                cardLeft + 5, panelTop + 121, half, 18,
                accelerationListMessage(), ignored -> {
                    if (markSleepList) markSleepList = false;
                    else accelerationWhitelistMode = !accelerationWhitelistMode;
                    apply();
                }));
        sleepListButton = addRenderableWidget(new SelectableAE2Button(
                cardLeft + 8 + half, panelTop + 121, cardWidth - 13 - half, 18,
                sleepListMessage(), ignored -> {
                    if (!markSleepList) markSleepList = true;
                    else sleepWhitelistMode = !sleepWhitelistMode;
                    apply();
                }));
        addRenderableWidget(new SelectableAE2Button(
                cardLeft + 5, panelTop + 153, (cardWidth - 13) / 2, 18,
                Component.translatable("gui.useless_stretcher.range.history"),
                ignored -> minecraft.setScreen(new RangeAccelerationHistoryScreen(this))));
        addRenderableWidget(new SelectableAE2Button(
                cardLeft + 8 + (cardWidth - 13) / 2, panelTop + 153,
                cardWidth - 13 - (cardWidth - 13) / 2, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
        updateButtons();
    }

    private void addAxisSlider(int left, int y, char axis, int initialValue) {
        AE2RangeSlider slider = addRenderableWidget(new AE2RangeSlider(
                left + 21, y, 208, 18, String.valueOf(axis),
                1, 15, initialValue, value -> setAxis(axis, value)));
        addRenderableWidget(new SelectableAE2Button(left, y, 18, 18,
                Component.literal("-"), ignored -> slider.step(-1)));
        addRenderableWidget(new SelectableAE2Button(left + 232, y, 18, 18,
                Component.literal("+"), ignored -> slider.step(1)));
    }

    private void setAxis(char axis, int value) {
        switch (axis) {
            case 'X' -> sizeX = value;
            case 'Y' -> sizeY = value;
            default -> sizeZ = value;
        }
        apply();
    }

    private void apply() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        RangeAccelerationSettings.set(staff, placementMode, filterMarkingMode, markSleepList,
                sizeX, sizeY, sizeZ, accelerationWhitelistMode, sleepWhitelistMode);
        RangeNetwork.sendSettings(hand, placementMode, filterMarkingMode, markSleepList,
                sizeX, sizeY, sizeZ, accelerationWhitelistMode, sleepWhitelistMode);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        StretcherScreenStyle.drawInset(graphics, panelLeft + 7, panelTop + 24,
                panelLeft + panelWidth - 7, panelTop + 112);
        StretcherScreenStyle.drawInset(graphics, panelLeft + 7, panelTop + 116,
                panelLeft + panelWidth - 7, panelTop + 145);
        graphics.drawString(font, title, panelLeft + 8, panelTop + 9,
                StretcherScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font,
                Component.translatable("gui.useless_stretcher.range.speed",
                        WondrousStaffAcceleration.getSpeed(currentStaff())),
                panelLeft + 12, panelTop + 29, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
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
}
