package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.item.StaffFluidCollector;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Base-mod Apple layout; the original G-menu binding and network settings stay unchanged. */
public final class StaffFluidCollectionScreen extends StaffScreen {
    private final Screen parent;
    private final InteractionHand hand;
    private boolean loaded, enabled;
    private int radius, rate;
    private SelectableAE2Button toggle;
    private java.util.List<Component> statusText = java.util.List.of();

    public StaffFluidCollectionScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.fluid.title"), Tab.FLUIDS, parent, hand);
        this.parent = parent;
        this.hand = hand;
    }
    private ItemStack staff() { return minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand); }
    @Override protected void refreshStaffSettings() { loaded = false; }
    @Override protected void initContent() {
        if (!loaded) {
            enabled = StaffFluidCollector.enabled(staff());
            radius = StaffFluidCollector.radius(staff());
            rate = StaffFluidCollector.rate(staff());
            loaded = true;
        }
        toggle = new SelectableAE2Button(0, 0, 100, 22, toggleText(), ignored -> {
            enabled = !enabled;
            save();
            toggle.setMessage(toggleText());
            toggle.setSelected(enabled);
        });
        toggle.setSelected(enabled);
        toggle.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.open_help")));
        var rangeSlider = new AE2RangeSlider(0, 0, 100, 24,
                Component.translatable("gui.useless_stretcher.fluid.radius").getString(), 1, StaffFluidCollector.MAX_RADIUS,
                radius, value -> { radius = value; save(); });
        rangeSlider.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.radius_help")));
        var rateSlider = new AE2RangeSlider(0, 0, 100, 24,
                Component.translatable("gui.useless_stretcher.fluid.rate").getString(), 1, StaffFluidCollector.MAX_RATE,
                rate, value -> { rate = value; save(); });
        rateSlider.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.rate_help")));
        var back = new SelectableAE2Button(0, 0, 80, 20,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        statusText = java.util.List.of(bindingText(), priorityText(), pendingText());
        layoutBody(Ui.column(
                Ui.card(widgetNode(toggle), widgetNode(rangeSlider), widgetNode(rateSlider)).gap(6),
                Ui.card(wrappedText(() -> Component.translatable("gui.useless_stretcher.fluid.hint"), StretcherScreenStyle.SUBTLE_TEXT_COLOR),
                        wrappedText(this::bindingText, StretcherScreenStyle.TEXT_COLOR),
                        wrappedText(this::priorityText, StretcherScreenStyle.WARNING_COLOR),
                        wrappedText(this::pendingText, StretcherScreenStyle.WARNING_COLOR)).gap(5),
                Ui.row(Ui.spacer(), widgetNode(back)).height(20)
        ).gap(6));
    }
    private Component toggleText() {
        return Component.translatable("gui.useless_stretcher.fluid.toggle", Component.translatable(enabled
                ? "gui.useless_stretcher.staff_config.on" : "gui.useless_stretcher.staff_config.off"));
    }
    private void save() {
        if (!staff().is(ModItems.WONDROUS_STAFF.get())) return;
        StaffFluidCollector.configure(staff(), enabled, radius, rate);
        Network.sendStaffFluidSettings(enabled, radius, rate, hand);
    }
    private Component bindingText() {
        var binding = ComponentAccess.get(staff(), UComponents.WIRELESS_LINK_TARGET.get());
        return binding == null ? Component.translatable("gui.useless_stretcher.fluid.unbound")
                : Component.translatable("gui.useless_stretcher.fluid.bound", binding.dimension().location().toString(), binding.pos().toShortString());
    }
    private Component priorityText() {
        return Component.translatable(ComponentAccess.getOrDefault(staff(), UComponents.AEStoragePriorityComponent.get(), false)
                ? "gui.useless_stretcher.fluid.priority_on" : "gui.useless_stretcher.fluid.priority_off");
    }
    private Component pendingText() {
        var pending = ComponentAccess.get(staff(), StretcherComponents.STAFF_FLUID_PENDING.get());
        return pending == null ? Component.empty() : Component.translatable(pending.uncertain()
                ? "gui.useless_stretcher.fluid.uncertain" : "gui.useless_stretcher.fluid.pending", pending.amount());
    }
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    @Override public void tick() {
        super.tick();
        if (minecraft.player == null || !staff().is(ModItems.WONDROUS_STAFF.get())) { onClose(); return; }
        if (!statusText.equals(java.util.List.of(bindingText(), priorityText(), pendingText()))) rebuildWidgets();
    }
    @Override public void onClose() { returnToPage(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
