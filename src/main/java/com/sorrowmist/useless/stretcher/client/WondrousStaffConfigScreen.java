package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Button-based acceleration selector styled after Useless Mod's current G configuration screen. */
public final class WondrousStaffConfigScreen extends FloatingScreen {
    private static final int[] GEARS = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};

    private final InteractionHand hand;
    private final List<ChoiceButton> speedButtons = new ArrayList<>();
    private final List<ChoiceButton> modeButtons = new ArrayList<>();
    private SelectableAE2Button accelerationButton;
    private SelectableAE2Button lootRefreshButton;
    private SelectableAE2Button summonOpenButton;
    private PlainTextButton extrasFoldText;
    private int panelLeft;
    private int panelTop;
    private int selectedSpeed;
    private int selectedMode;
    private boolean accelerationEnabled;
    private boolean summonEnabled;
    private boolean lootRefreshEnabled;
    private boolean extrasCollapsed = true;
    private boolean settingsLoaded;
    private int speedTop, speedHeight, modeTop, modeHeight, featuresTop, featuresHeight;

    public WondrousStaffConfigScreen(InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_config.title"), "staff", 282, 310, 244, 150);
        this.hand = hand;
    }

    @Override
    protected void initContent() {
        panelLeft = contentLeft();
        panelTop = contentTop();
        int panelWidth = contentWidth();
        if (!settingsLoaded) {
            ItemStack staff = currentStaff();
            selectedSpeed = WondrousStaffAcceleration.getSpeed(staff);
            selectedMode = WondrousStaffAcceleration.getMode(staff);
            accelerationEnabled = WondrousStaffAcceleration.isEnabled(staff);
            summonEnabled = WondrousStaffAcceleration.isSummonEnabled(staff);
            lootRefreshEnabled = WondrousStaffAcceleration.isLootRefreshEnabled(staff);
            settingsLoaded = true;
        }

        speedButtons.clear();
        modeButtons.clear();
        accelerationButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 104, panelTop, 104, 18,
                accelerationMessage(), ignored -> toggleAcceleration()));

        int cardLeft = panelLeft;
        int cardWidth = panelWidth;
        int gap = 3;
        int columns = Math.max(2, Math.min(GEARS.length, (cardWidth - 10 + gap) / 46));
        int buttonWidth = (cardWidth - 10 - gap * (columns - 1)) / columns;
        speedTop = panelTop + 23;
        speedHeight = 23 + ((GEARS.length + columns - 1) / columns) * 20;
        for (int i = 0; i < GEARS.length; i++) {
            int speed = GEARS[i];
            int x = cardLeft + 5 + (i % columns) * (buttonWidth + gap);
            int y = speedTop + 18 + (i / columns) * 20;
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
        modeTop = speedTop + speedHeight + 4;
        int modeColumns = cardWidth >= 490 ? 3 : 1;
        modeHeight = 23 + (3 / modeColumns) * 20;
        int modeButtonWidth = (cardWidth - 10 - (modeColumns - 1) * gap) / modeColumns;
        for (int mode = 0; mode < modeKeys.length; mode++) {
            int value = mode;
            SelectableAE2Button button = addRenderableWidget(new SelectableAE2Button(
                    cardLeft + 5 + (mode % modeColumns) * (modeButtonWidth + gap),
                    modeTop + 18 + (mode / modeColumns) * 20, modeButtonWidth, 17,
                    Component.translatable(modeKeys[mode]), ignored -> selectMode(value)));
            modeButtons.add(new ChoiceButton(mode, button));
        }
        Component foldText = Component.translatable(extrasCollapsed
                ? "gui.useless_stretcher.staff_summon.expand"
                : "gui.useless_stretcher.staff_summon.collapse");
        int foldWidth = font.width(foldText);
        featuresTop = modeTop + modeHeight + 4;
        featuresHeight = extrasCollapsed ? 42 : 83;
        addRenderableWidget(new SelectableAE2Button(cardLeft + 5, featuresTop + 18, cardWidth - foldWidth - 20, 17,
                Component.translatable("gui.useless_stretcher.entity_acceleration.title"),
                ignored -> minecraft.setScreen(new EntityAccelerationConfigScreen(this, hand))))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                        "gui.useless_stretcher.entity_acceleration.open_tooltip")));
        extrasFoldText = addRenderableWidget(new PlainTextButton(
                cardLeft + cardWidth - foldWidth - 6, featuresTop + 22, foldWidth, 10,
                foldText, ignored -> toggleExtrasFold(), font));
        com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.set(extrasFoldText, "fold");
        if (!extrasCollapsed) {
            lootRefreshButton = addRenderableWidget(new SelectableAE2Button(
                    cardLeft + 5, featuresTop + 39, cardWidth - 10, 17,
                    lootRefreshMessage(), ignored -> toggleLootRefresh()));
            summonOpenButton = addRenderableWidget(new SelectableAE2Button(
                    cardLeft + 5, featuresTop + 60, cardWidth - 10, 17,
                    summonOpenMessage(),
                    ignored -> minecraft.setScreen(new WondrousStaffSummonScreen(this, hand))));
        } else {
            lootRefreshButton = null;
            summonOpenButton = null;
        }

        int footerWidth = (cardWidth - 3) / 2;
        int footerY = Math.max(featuresTop + featuresHeight + 5, panelTop + contentHeight() - 20);
        addRenderableWidget(new SelectableAE2Button(
                cardLeft, footerY, footerWidth, 17,
                Component.translatable("gui.useless_stretcher.range.open"),
                ignored -> minecraft.setScreen(new RangeAccelerationConfigScreen(this, hand))));
        addRenderableWidget(new SelectableAE2Button(
                cardLeft + footerWidth + 3, footerY, cardWidth - footerWidth - 3, 17,
                Component.translatable("gui.useless_stretcher.range.history"),
                ignored -> minecraft.setScreen(new RangeAccelerationHistoryScreen(this))));
        updateSelection();
        setContentExtent(footerY - panelTop + 20);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cardLeft = panelLeft;
        int cardRight = panelLeft + contentWidth();
        StretcherScreenStyle.drawInset(graphics, cardLeft, speedTop, cardRight, speedTop + speedHeight);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_config.speed"),
                cardLeft + 5, speedTop + 5, StretcherScreenStyle.TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, cardLeft, modeTop, cardRight, modeTop + modeHeight);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_config.duration"),
                cardLeft + 5, modeTop + 5, StretcherScreenStyle.TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, cardLeft, featuresTop, cardRight, featuresTop + featuresHeight);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_config.features"),
                cardLeft + 5, featuresTop + 5, StretcherScreenStyle.TEXT_COLOR, false);
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

    private void toggleLootRefresh() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get()) || !StretcherConfig.enableStaffLootRefresh()) return;
        summonEnabled = WondrousStaffAcceleration.isSummonEnabled(staff);
        lootRefreshEnabled = !WondrousStaffAcceleration.isLootRefreshEnabled(staff);
        staff.set(StretcherComponents.WONDROUS_STAFF_LOOT_REFRESH.get(), lootRefreshEnabled);
        Network.sendWondrousStaffFeatures(summonEnabled, lootRefreshEnabled, hand);
        updateSelection();
    }

    private void sendSelection() {
        updateSelection();
        Network.sendWondrousStaffSpeed(selectedSpeed, selectedMode, accelerationEnabled, hand);
    }

    private void updateSelection() {
        boolean allowAcceleration = StretcherConfig.serverStaffAcceleration();
        speedButtons.forEach(entry -> entry.button().active = allowAcceleration);
        modeButtons.forEach(entry -> entry.button().active = allowAcceleration);
        speedButtons.forEach(entry -> entry.button().setSelected(entry.value() == selectedSpeed));
        modeButtons.forEach(entry -> entry.button().setSelected(entry.value() == selectedMode));
        if (accelerationButton != null) {
            accelerationButton.active = allowAcceleration;
            accelerationButton.setSelected(accelerationEnabled);
            accelerationButton.setMessage(accelerationMessage());
        }
        if (lootRefreshButton != null) {
            boolean configEnabled = StretcherConfig.enableStaffLootRefresh();
            lootRefreshButton.active = configEnabled;
            lootRefreshButton.setSelected(configEnabled && lootRefreshEnabled);
            lootRefreshButton.setMessage(configEnabled
                    ? lootRefreshMessage()
                    : Component.translatable("gui.useless_stretcher.staff_config.loot_refresh_disabled"));
        }
        if (summonOpenButton != null) {
            boolean configEnabled = StretcherConfig.enableStaffSummon();
            summonOpenButton.visible = true;
            summonOpenButton.active = configEnabled;
            summonOpenButton.setMessage(configEnabled
                    ? summonOpenMessage()
                    : Component.translatable("gui.useless_stretcher.staff_summon.disabled"));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateSelection(); // Reflect live server config updates while this screen stays open.
    }

    private Component accelerationMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.master",
                Component.translatable(accelerationEnabled
                        ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private Component summonOpenMessage() {
        return Component.translatable("gui.useless_stretcher.staff_summon.open");
    }

    private Component lootRefreshMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.loot_refresh",
                Component.translatable(lootRefreshEnabled ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private void toggleExtrasFold() {
        extrasCollapsed = !extrasCollapsed;
        rebuildWidgets();
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
