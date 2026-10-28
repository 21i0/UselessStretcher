package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Flex;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.network.ApotheosisNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Optional world-tier selector and virtual-bookshelf editor for Apotheosis. */
public final class WondrousStaffApotheosisScreen extends StaffScreen {
    private static final String[] TIERS = {"haven", "frontier", "ascent", "summit", "pinnacle"};
    private static final String[] TIER_KEYS = {"haven", "frontier", "ascent", "summit", "pinnacle"};

    private final Screen parent;
    private final InteractionHand hand;
    private int eterna;
    private int quanta;
    private int arcana;
    private int clues;
    private String selectedTier;
    private boolean initialized;
    private boolean selectionMode;
    private boolean dirty;
    private int saveDelay;
    private ApotheosisTableData.Boost lastSent;
    private boolean edited;
    private boolean tierEdited;

    public WondrousStaffApotheosisScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_apotheosis.title"), Tab.APOTHEOSIS, parent, hand);
        this.parent = parent;
        this.hand = hand;
    }

    @Override protected void refreshStaffSettings() {
        initialized = false;
        edited = false;
        tierEdited = false;
    }

    @Override
    protected void initContent() {
        if (!initialized) {
            initialized = true;
            ItemStack staff = currentStaff();
            loadBoost(ApotheosisStaffSettings.boost(staff));
            lastSent = currentBoost();
            selectionMode = ApotheosisStaffSettings.selectionMode(staff);
            selectedTier = ApotheosisStaffSettings.currentTier(minecraft.player);
            if (minecraft.getConnection() != null) ApotheosisNetwork.requestState(hand);
        }
        var tiers = Ui.column().gap(3);
        if (ApotheosisStaffSettings.isWorldTierAvailable()) {
            int columns = contentWidth() < 260 ? 3 : 5;
            for (int start = 0; start < TIERS.length; start += columns) {
                Flex row = Ui.row().gap(3);
                row.height(20);
                for (int offset = 0; offset < columns; offset++) {
                    int index = start + offset;
                    if (index >= TIERS.length) { row.add(Ui.spacer().width(0).grow()); continue; }
                    String tier = TIERS[index];
                    var button = new SelectableAE2Button(0, 0, 60, 20,
                            Component.translatable("gui.useless_stretcher.staff_apotheosis.tier." + TIER_KEYS[index]),
                            ignored -> {
                                selectedTier = tier;
                                tierEdited = true;
                                Network.setApotheosisTier(tier);
                                updateTierSelection();
                            });
                    ButtonHelp.set(button, "apotheosis_tier", button.getMessage());
                    button.setSelected(tier.equals(selectedTier));
                    row.add(widgetNode(button).width(0).grow());
                }
                tiers.add(row);
            }
        } else {
            tiers.add(wrappedText(() -> Component.translatable("gui.useless_stretcher.staff_apotheosis.tier_unavailable"),
                    StretcherScreenStyle.SUBTLE_TEXT_COLOR));
        }
        var select = new SelectableAE2Button(0, 0, 100, 20,
                Component.translatable("gui.useless_stretcher.staff_apotheosis.select_table"), ignored -> {
                    selectionMode = !selectionMode;
                    edited = true;
                    save(true);
                    if (selectionMode) minecraft.setScreen(null);
                    else rebuildWidgets();
                });
        select.setSelected(selectionMode);
        ButtonHelp.set(select, "apotheosis_select_table");
        var save = new SelectableAE2Button(0, 0, 100, 20,
                Component.translatable("gui.useless_stretcher.staff_apotheosis.save"), ignored -> save(false));
        ButtonHelp.set(save, "apotheosis_save");
        var back = new SelectableAE2Button(0, 0, 100, 20,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        layoutBody(Ui.column(
                Ui.section(Ui.tr("gui.useless_stretcher.staff_apotheosis.world_tier"), tiers),
                Ui.section(Ui.tr("gui.useless_stretcher.staff_apotheosis.stats"),
                        valueRow("eterna", 100, eterna, value -> eterna = value),
                        valueRow("quanta", 100, quanta, value -> quanta = value),
                        valueRow("arcana", 100, arcana, value -> arcana = value),
                        valueRow("clues", 15, clues, value -> clues = value)).gap(4),
                Ui.row(widgetNode(select).width(0).grow(), widgetNode(save).width(0).grow(),
                        widgetNode(back).width(0).grow()).gap(3).height(20)
        ).gap(6));
    }

    private UiNode valueRow(String key, int max, int initial, java.util.function.IntConsumer changed) {
        var slider = new AE2RangeSlider(0, 0, 60, 22,
                Component.translatable("gui.useless_stretcher.staff_apotheosis.stat." + key).getString(),
                0, max, initial, value -> {
                    changed.accept(value);
                    edited = true;
                    if (!dirty) saveDelay = 4;
                    dirty = true;
                });
        var decrease = new SelectableAE2Button(0, 0, 20, 22, Component.literal("-"), ignored -> slider.step(-1));
        var increase = new SelectableAE2Button(0, 0, 20, 22, Component.literal("+"), ignored -> slider.step(1));
        ButtonHelp.set(decrease, "decrease");
        ButtonHelp.set(increase, "increase");
        ButtonHelp.set(slider, "apotheosis_stat", Component.translatable("gui.useless_stretcher.staff_apotheosis.stat." + key));
        return Ui.row(widgetNode(decrease), widgetNode(slider).width(0).grow(), widgetNode(increase)).gap(3).height(22);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    private void updateTierSelection() {
        for (var child : children()) {
            if (child instanceof SelectableAE2Button button) {
                for (int i = 0; i < TIERS.length; i++) {
                    if (button.getMessage().equals(Component.translatable(
                            "gui.useless_stretcher.staff_apotheosis.tier." + TIER_KEYS[i]))) {
                        button.setSelected(TIERS[i].equals(selectedTier));
                    }
                }
            }
        }
    }

    private ApotheosisTableData.Boost currentBoost() {
        return new ApotheosisTableData.Boost(eterna, quanta, arcana, clues).normalized();
    }

    private void loadBoost(ApotheosisTableData.Boost boost) {
        eterna = boost.eterna();
        quanta = boost.quanta();
        arcana = boost.arcana();
        clues = boost.clues();
    }

    public void acceptState(ApotheosisNetwork.SettingsPayload state) {
        if (state.offhand() != (hand == InteractionHand.OFF_HAND)) return;
        if (!tierEdited) selectedTier = state.tier();
        updateTierSelection();
        // Do not let a delayed server acknowledgment overwrite a newer slider edit.
        if (!edited) {
            var boost = state.boost();
            boolean rebuild = !boost.equals(currentBoost()) || selectionMode != state.selecting();
            loadBoost(boost);
            lastSent = boost;
            selectionMode = state.selecting();
            if (rebuild) rebuildWidgets();
        }
    }

    private void save(boolean force) {
        var boost = currentBoost();
        if (!force && (!dirty || boost.equals(lastSent))) { dirty = false; return; }
        ItemStack staff = currentStaff();
        if (staff.isEmpty()) return;
        ApotheosisStaffSettings.setBoost(staff, boost);
        ApotheosisStaffSettings.setSelectionMode(staff, selectionMode);
        if (selectionMode) {
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, com.sorrowmist.useless.stretcher.init.StretcherComponents.RANGE_FILTER_MARKING_MODE.get(), false);
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, com.sorrowmist.useless.stretcher.init.StretcherComponents.RANGE_PLACEMENT_MODE.get(), false);
        }
        Network.sendApotheosisOptions(selectionMode, boost, hand);
        lastSent = boost;
        dirty = false;
    }

    @Override public void tick() {
        super.tick();
        if (dirty && --saveDelay <= 0) save(false);
    }

    @Override public void removed() {
        save(false);
        super.removed();
    }

    private ItemStack currentStaff() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        ItemStack held = minecraft.player.getItemInHand(hand);
        return held.is(ModItems.WONDROUS_STAFF.get()) ? held : ItemStack.EMPTY;
    }

    @Override public void onClose() { returnToPage(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
