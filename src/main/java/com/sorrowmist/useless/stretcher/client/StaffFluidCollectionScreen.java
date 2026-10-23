package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
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

/** Uses the existing G-menu AE preference and wireless access point binding. */
public final class StaffFluidCollectionScreen extends StaffScreen {
    private final Screen parent;
    private final InteractionHand hand;
    private boolean loaded, enabled;
    private int radius, rate, hintTop;
    private SelectableAE2Button toggle;

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
        int left = contentLeft(), top = contentTop(), width = contentWidth();
        toggle = addRenderableWidget(new SelectableAE2Button(left, top, width, 19, toggleText(), ignored -> {
            enabled = !enabled;
            save();
            toggle.setMessage(toggleText());
            toggle.setSelected(enabled);
        }));
        toggle.setSelected(enabled);
        toggle.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.open_help")));
        var rangeSlider = addRenderableWidget(new AE2RangeSlider(left, top + 27, width, 19,
                Component.translatable("gui.useless_stretcher.fluid.radius").getString(), 1, StaffFluidCollector.MAX_RADIUS,
                radius, value -> { radius = value; save(); }));
        rangeSlider.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.radius_help")));
        var rateSlider = addRenderableWidget(new AE2RangeSlider(left, top + 52, width, 19,
                Component.translatable("gui.useless_stretcher.fluid.rate").getString(), 1, StaffFluidCollector.MAX_RATE,
                rate, value -> { rate = value; save(); }));
        rateSlider.setTooltip(Tooltip.create(Component.translatable("gui.useless_stretcher.fluid.rate_help")));
        hintTop = top + 80;
        int hintHeight = font.split(Component.translatable("gui.useless_stretcher.fluid.hint"), width).size() * (font.lineHeight + 2);
        int statusHeight = font.split(bindingText(), width).size() * (font.lineHeight + 2);
        int priorityHeight = font.split(priorityText(), width).size() * (font.lineHeight + 2);
        int pendingHeight = font.split(Component.translatable("gui.useless_stretcher.fluid.pending", 1000), width).size() * (font.lineHeight + 2);
        int footer = hintTop + hintHeight + statusHeight + priorityHeight + pendingHeight + 14;
        addRenderableWidget(new SelectableAE2Button(left, footer, width, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
        setContentExtent(footer - top + 23);
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
        var binding = staff().get(UComponents.WIRELESS_LINK_TARGET.get());
        return binding == null ? Component.translatable("gui.useless_stretcher.fluid.unbound")
                : Component.translatable("gui.useless_stretcher.fluid.bound", binding.dimension().location().toString(), binding.pos().toShortString());
    }
    private Component priorityText() {
        return Component.translatable(staff().getOrDefault(UComponents.AEStoragePriorityComponent.get(), false)
                ? "gui.useless_stretcher.fluid.priority_on" : "gui.useless_stretcher.fluid.priority_off");
    }
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = hintTop;
        for (var line : font.split(Component.translatable("gui.useless_stretcher.fluid.hint"), contentWidth())) {
            graphics.drawString(font, line, contentLeft(), y, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
            y += font.lineHeight + 2;
        }
        y += 5;
        for (var line : font.split(bindingText(), contentWidth())) {
            graphics.drawString(font, line, contentLeft(), y, StretcherScreenStyle.TEXT_COLOR, false);
            y += font.lineHeight + 2;
        }
        boolean priority = staff().getOrDefault(UComponents.AEStoragePriorityComponent.get(), false);
        y += 3;
        for (var line : font.split(priorityText(), contentWidth())) {
            graphics.drawString(font, line, contentLeft(), y, priority ? 0x88CC88 : 0xFFBB66, false);
            y += font.lineHeight + 2;
        }
        var pending = staff().get(StretcherComponents.STAFF_FLUID_PENDING.get());
        if (pending != null) for (var line : font.split(Component.translatable("gui.useless_stretcher.fluid.pending", pending.amount()), contentWidth())) {
            graphics.drawString(font, line, contentLeft(), y, 0xFFBB66, false);
            y += font.lineHeight + 2;
        }
    }
    @Override public void tick() {
        if (minecraft.player == null || !staff().is(ModItems.WONDROUS_STAFF.get())) onClose();
    }
    @Override public void onClose() { returnToPage(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
