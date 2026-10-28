package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import com.sorrowmist.useless.client.gui.ldl.api.Flex;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff main page rebuilt on Useless Mod's Apple UI API.
 *
 * <p>Everything visual is declarative: cards, captions, the master switch and the server-config
 * notice are produced by {@code Ui.column/row/section/settingsGroup} and laid out by
 * {@code FloatingScreen.layoutBody}, so no card coordinate is measured by hand any more.
 * Stateful controls (multiplier gears, duration modes and the extra-feature switches) stay native
 * and are embedded with {@code FloatingScreen.widgetNode}: they keep their selected paint, their
 * hover help and the live {@link #updateSelection()} refresh from the synchronized server config.
 * All controls retain native keyboard focus, narration and translated hover help.</p>
 */
public final class WondrousStaffConfigScreen extends StaffScreen {
    private static final int[] GEARS = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};
    private static final String[] MODE_KEYS = {
            "gui.useless_stretcher.mode_normal",
            "gui.useless_stretcher.mode_permanent",
            "gui.useless_stretcher.mode_no_throttle_short"
    };
    /** Row height of an embedded native button: matches the compact AE2 look while staying clickable. */
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    private static final String TITLE_SPEED = "gui.useless_stretcher.staff_config.speed";
    private static final String TITLE_DURATION = "gui.useless_stretcher.staff_config.duration";
    private static final String TITLE_FEATURES = "gui.useless_stretcher.staff_config.features";

    private final InteractionHand hand;
    private final List<ChoiceButton> speedButtons = new ArrayList<>();
    private final List<ChoiceButton> modeButtons = new ArrayList<>();
    private SelectableAE2Button lootRefreshButton;
    private SelectableAE2Button summonOpenButton;
    private SelectableAE2Button apotheosisOpenButton;
    private SelectableAE2Button phaseButton;
    private SelectableAE2Button phaseInteractionButton;
    private SelectableAE2Button radiationButton;
    private SelectableAE2Button extrasFoldText;
    private SelectableAE2Button accelerationButton;
    private int selectedSpeed;
    private int selectedMode;
    private boolean accelerationEnabled;
    private boolean summonEnabled;
    private boolean lootRefreshEnabled;
    private boolean phaseEnabled;
    private boolean phaseInteractionEnabled;
    private boolean radiationEnabled;
    private boolean extrasCollapsed = true;
    private boolean settingsLoaded;

    public WondrousStaffConfigScreen(InteractionHand hand) {
        this(null, hand);
    }

    public WondrousStaffConfigScreen(StaffScreen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_config.title"), Tab.MAIN, parent, hand);
        this.hand = hand;
        extrasCollapsed = Boolean.parseBoolean(floatingWindow().preference("extrasCollapsed", "true"));
    }

    @Override protected void refreshStaffSettings() { settingsLoaded = false; }

    @Override
    protected void initContent() {
        if (!settingsLoaded) loadStaffSettings();
        // Widget instances are rebuilt on every relayout (resize / scroll), exactly like before.
        speedButtons.clear();
        modeButtons.clear();
        lootRefreshButton = null;
        summonOpenButton = null;
        apotheosisOpenButton = null;
        phaseButton = null;
        phaseInteractionButton = null;
        radiationButton = null;
        layoutBody(buildPage());
        updateSelection();
    }

    private void loadStaffSettings() {
        ItemStack staff = currentStaff();
        selectedSpeed = WondrousStaffAcceleration.getSpeed(staff);
        selectedMode = WondrousStaffAcceleration.getMode(staff);
        accelerationEnabled = WondrousStaffAcceleration.isEnabled(staff);
        summonEnabled = WondrousStaffAcceleration.isSummonEnabled(staff);
        lootRefreshEnabled = WondrousStaffAcceleration.isLootRefreshEnabled(staff);
        phaseEnabled = ComponentAccess.getOrDefault(staff, StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), false);
        radiationEnabled = ComponentAccess.getOrDefault(staff, StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), false);
        phaseInteractionEnabled = ComponentAccess.getOrDefault(staff,
                StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), true);
        settingsLoaded = true;
    }

    // ------------------------------------------------------------------ 声明式页面
    /** One column of Apple cards; every width/height is derived from the content width by the API. */
    private UiNode buildPage() {
        Flex page = Ui.column().gap(4);
        page.add(masterCard());
        if (!StretcherConfig.serverStaffAcceleration())
            page.add(wrappedText(() -> ButtonHelp.text("disabled"), AppleStyle.ORANGE_TEXT));
        page.add(speedCard());
        page.add(durationCard());
        page.add(foldRow());
        page.add(featuresCard());
        page.add(footerRow());
        return page;
    }

    /** Master switch: Apple setting row, bound live to the component field and to the server option. */
    private UiNode masterCard() {
        accelerationButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                accelerationMessage(), ignored -> toggleAcceleration());
        return Ui.settingsGroup().row(Ui.row(
                Ui.iconTile(AppleUi.Icon.BOLT, AppleStyle.ORANGE),
                flexible(accelerationButton)
        ).gap(6).height(BUTTON_HEIGHT));
    }

    /**
     * Multiplier gears: the column count follows the content width, so the compact 340px window
     * (96px sidebar) still fits ten gears without clipping, and the last row is padded with equal
     * spacers to keep the grid aligned.
     */
    private UiNode speedCard() {
        int inner = innerWidth();
        int columns = Math.max(2, Math.min(GEARS.length, inner >= 192 ? 5 : 4));
        Flex grid = Ui.column().gap(ROW_GAP);
        for (int first = 0; first < GEARS.length; first += columns) {
            Flex line = Ui.row().gap(ROW_GAP).height(BUTTON_HEIGHT);
            for (int offset = 0; offset < columns; offset++) {
                int index = first + offset;
                line.add(index < GEARS.length ? speedNode(GEARS[index]) : Ui.spacer().width(0).grow());
            }
            grid.add(line);
        }
        return Ui.section(Ui.tr(TITLE_SPEED), grid);
    }

    /** Duration modes: three columns when there is room, two compact rows in a narrow window. */
    private UiNode durationCard() {
        Flex rows = Ui.column().gap(ROW_GAP);
        if (contentWidth() < 240) {
            rows.add(Ui.row(modeNode(0), modeNode(1)).gap(ROW_GAP).height(BUTTON_HEIGHT));
            rows.add(Ui.row(modeNode(2)).gap(ROW_GAP).height(BUTTON_HEIGHT));
        } else {
            rows.add(Ui.row(modeNode(0), modeNode(1), modeNode(2)).gap(ROW_GAP).height(BUTTON_HEIGHT));
        }
        return Ui.section(Ui.tr(TITLE_DURATION), rows);
    }

    /** Keep the persisted extras fold accessible on the shared light surface. */
    private UiNode foldRow() {
        Component text = Component.translatable(extrasCollapsed
                ? "gui.useless_stretcher.staff_summon.expand"
                : "gui.useless_stretcher.staff_summon.collapse");
        int width = Math.min(contentWidth(), Math.max(40, font.width(text) + 16));
        extrasFoldText = new SelectableAE2Button(0, 0, width, BUTTON_HEIGHT, text, ignored -> toggleExtrasFold());
        ButtonHelp.set(extrasFoldText, "fold");
        return Ui.row(Ui.spacer(), widgetNode(extrasFoldText).width(width)).height(BUTTON_HEIGHT);
    }

    private UiNode featuresCard() {
        Flex section = Ui.section(Ui.tr(TITLE_FEATURES),
                Ui.row(entityAccelerationNode()).height(BUTTON_HEIGHT));
        if (!extrasCollapsed) {
            section.add(buttonPair(lootRefreshNode(), summonNode()));
            section.add(buttonPair(phaseNode(), phaseInteractionNode()));
            section.add(StretcherConfig.hasApotheosisConfig()
                    ? buttonPair(apotheosisNode(), radiationNode())
                    : buttonPair(radiationNode(), null));
            section.add(Ui.row(fluidNode()).height(BUTTON_HEIGHT));
        }
        return section.gap(ROW_GAP);
    }

    private UiNode footerRow() {
        SelectableAE2Button range = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.range.open"), ignored -> navigate(Tab.RANGE));
        SelectableAE2Button history = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.range.history"), ignored -> navigate(Tab.HISTORY));
        return Ui.row(flexible(range), flexible(history)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    // ------------------------------------------------------------------ 控件工厂
    private UiNode speedNode(int speed) {
        Component label = speed == 0
                ? Component.translatable("gui.useless_stretcher.speed_off")
                : Component.literal("x" + speed);
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 40, BUTTON_HEIGHT, label,
                ignored -> selectSpeed(speed));
        speedButtons.add(new ChoiceButton(speed, button));
        return flexible(button);
    }

    private UiNode modeNode(int mode) {
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 60, BUTTON_HEIGHT,
                Component.translatable(MODE_KEYS[mode]), ignored -> selectMode(mode));
        ButtonHelp.set(button, mode == 0 ? "normal" : mode == 1 ? "permanent" : "no_throttle");
        modeButtons.add(new ChoiceButton(mode, button));
        return flexible(button);
    }

    private UiNode entityAccelerationNode() {
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 120, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.entity_acceleration.title"),
                ignored -> navigate(Tab.ENTITY));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.useless_stretcher.entity_acceleration.open_tooltip")));
        return flexible(button);
    }

    private UiNode lootRefreshNode() {
        lootRefreshButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                lootRefreshMessage(), ignored -> toggleLootRefresh());
        return flexible(lootRefreshButton);
    }

    private UiNode summonNode() {
        summonOpenButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                summonOpenMessage(), ignored -> navigate(Tab.SUMMON));
        return flexible(summonOpenButton);
    }

    private UiNode phaseNode() {
        phaseButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                phaseMessage(), ignored -> togglePhase());
        return flexible(phaseButton);
    }

    private UiNode phaseInteractionNode() {
        phaseInteractionButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                phaseInteractionMessage(), ignored -> togglePhaseInteraction());
        return flexible(phaseInteractionButton);
    }

    private UiNode apotheosisNode() {
        apotheosisOpenButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                apotheosisOpenMessage(), ignored -> navigate(Tab.APOTHEOSIS));
        return flexible(apotheosisOpenButton);
    }

    private UiNode radiationNode() {
        radiationButton = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                radiationMessage(), ignored -> toggleRadiation());
        return flexible(radiationButton);
    }

    private UiNode fluidNode() {
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 120, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.fluid.title"),
                ignored -> navigate(Tab.FLUIDS));
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.useless_stretcher.fluid.open_help")));
        return flexible(button);
    }

    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(AbstractWidget widget) {
        return widgetNode(widget).width(0).grow().height(BUTTON_HEIGHT);
    }

    /** Two equal columns; a null cell keeps the grid width without leaving an unstyled gap. */
    private UiNode buttonPair(UiNode left, UiNode right) {
        Flex row = Ui.row().gap(ROW_GAP).height(BUTTON_HEIGHT);
        row.add(left == null ? Ui.spacer().width(0).grow() : left);
        row.add(right == null ? Ui.spacer().width(0).grow() : right);
        return row;
    }

    private int innerWidth() {
        return Math.max(60, contentWidth() - Ui.t().cardPad * 2);
    }

    // ------------------------------------------------------------------ 渲染
    /** Cards, captions and text are painted by the shared Apple host; nothing is drawn by hand here. */
    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // ------------------------------------------------------------------ 状态
    private void selectSpeed(int speed) {
        selectedSpeed = speed;
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
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
        ComponentAccess.set(staff, UComponents.BeefTimeAccelerationEnabledComponent.get(), accelerationEnabled);
        sendSelection();
    }

    private void toggleLootRefresh() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get()) || !StretcherConfig.enableStaffLootRefresh()) return;
        summonEnabled = WondrousStaffAcceleration.isSummonEnabled(staff);
        lootRefreshEnabled = !WondrousStaffAcceleration.isLootRefreshEnabled(staff);
        ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_LOOT_REFRESH.get(), lootRefreshEnabled);
        Network.sendWondrousStaffFeatures(summonEnabled, lootRefreshEnabled, hand);
        updateSelection();
    }

    private void sendSelection() {
        updateSelection();
        Network.sendWondrousStaffSpeed(selectedSpeed, selectedMode, accelerationEnabled, hand);
    }

    private void updateSelection() {
        boolean allowAcceleration = StretcherConfig.serverStaffAcceleration();
        if (accelerationButton != null) {
            accelerationButton.active = allowAcceleration;
            accelerationButton.setSelected(accelerationEnabled);
            accelerationButton.setMessage(accelerationMessage());
        }
        for (ChoiceButton entry : speedButtons) {
            entry.button().active = allowAcceleration;
            entry.button().setSelected(entry.value() == selectedSpeed);
        }
        for (ChoiceButton entry : modeButtons) {
            entry.button().active = allowAcceleration;
            entry.button().setSelected(entry.value() == selectedMode);
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
        if (apotheosisOpenButton != null) {
            boolean configEnabled = StretcherConfig.enableApotheosisCompat();
            apotheosisOpenButton.active = configEnabled && ApotheosisStaffSettings.isAvailable();
            apotheosisOpenButton.setMessage(configEnabled
                    ? apotheosisOpenMessage()
                    : Component.translatable("gui.useless_stretcher.staff_apotheosis.disabled"));
        }
        if (phaseButton != null) {
            phaseButton.setSelected(phaseEnabled);
            phaseButton.setMessage(phaseMessage());
            phaseButton.setTooltip(Tooltip.create(Component.translatable(
                    "help.useless_stretcher.staff_config.phase")));
        }
        if (radiationButton != null) {
            radiationButton.active = ModList.get().isLoaded("mekanism");
            radiationButton.setSelected(radiationEnabled);
            radiationButton.setMessage(radiationMessage());
            radiationButton.setTooltip(Tooltip.create(Component.translatable(
                    "help.useless_stretcher.staff_config.radiation")));
        }
        if (phaseInteractionButton != null) {
            phaseInteractionButton.setSelected(phaseInteractionEnabled);
            phaseInteractionButton.setMessage(phaseInteractionMessage());
            phaseInteractionButton.active = phaseEnabled;
            phaseInteractionButton.setTooltip(Tooltip.create(Component.translatable(
                    "help.useless_stretcher.staff_config.phase_interaction")));
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

    private Component apotheosisOpenMessage() {
        return Component.translatable("gui.useless_stretcher.staff_apotheosis.open");
    }

    private Component lootRefreshMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.loot_refresh",
                Component.translatable(lootRefreshEnabled ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private Component phaseMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.phase",
                Component.translatable(phaseEnabled ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private Component phaseInteractionMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.phase_interaction",
                Component.translatable(phaseInteractionEnabled ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private void togglePhase() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        phaseEnabled = !phaseEnabled;
        ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), phaseEnabled);
        Network.sendWondrousStaffPhase(phaseEnabled, phaseInteractionEnabled, hand);
        updateSelection();
    }

    private void togglePhaseInteraction() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get()) || !phaseEnabled) return;
        phaseInteractionEnabled = !phaseInteractionEnabled;
        ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), phaseInteractionEnabled);
        Network.sendWondrousStaffPhase(phaseEnabled, phaseInteractionEnabled, hand);
        updateSelection();
    }

    private void toggleExtrasFold() {
        extrasCollapsed = !extrasCollapsed;
        floatingWindow().setPreference("extrasCollapsed", Boolean.toString(extrasCollapsed));
        rebuildWidgets();
    }

    private Component radiationMessage() {
        return Component.translatable("gui.useless_stretcher.staff_config.radiation", Component.translatable(
                radiationEnabled ? "gui.useless_stretcher.staff_config.on" : "gui.useless_stretcher.staff_config.off"));
    }

    private void toggleRadiation() {
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get()) || !ModList.get().isLoaded("mekanism")) return;
        radiationEnabled = !radiationEnabled;
        ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), radiationEnabled);
        Network.sendWondrousStaffRadiation(radiationEnabled, hand);
        updateSelection();
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
