package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Structure loading completion in the vanilla top-right toast queue. */
public final class CapsuleReadyToast implements Toast {
    private final Component structure;
    private final Object token;
    private CapsuleReadyToast(String id, String dimension, int instanceId) {
        ResourceLocation key = new ResourceLocation(id);
        String translation = "structure." + key.getNamespace() + "." + key.getPath().replace('/', '.');
        structure = net.minecraft.locale.Language.getInstance().has(translation) ? Component.translatable(translation)
                : Component.literal(key.getPath().replace('_', ' ').replace('/', ' '));
        token = id + "|" + dimension + "#" + instanceId;
    }
    public static void show(String id, String dimension, int instanceId) {
        if (ResourceLocation.tryParse(id) == null) return;
        var toasts = Minecraft.getInstance().getToasts();
        String token = id + "|" + dimension + "#" + instanceId;
        if (toasts.getToast(CapsuleReadyToast.class, token) == null) toasts.addToast(new CapsuleReadyToast(id, dimension, instanceId));
    }
    @Override public Object getToken() { return token; }
    @Override public int width() { return 230; }
    @Override public int height() { return 48; }
    @Override public Visibility render(GuiGraphics graphics, ToastComponent toasts, long time) {
        StretcherScreenStyle.drawPanel(graphics, 0, 0, width(), height());
        graphics.renderItem(new ItemStack(ModItems.DIMENSION_CAPSULE.get()), 7, 15);
        var font = toasts.getMinecraft().font;
        graphics.drawString(font, Component.translatable("toast.useless_stretcher.capsule_ready"), 29, 6,
                StretcherScreenStyle.SUCCESS_COLOR, false);
        graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                font.substrByWidth(structure, width() - 36)), 29, 19, StretcherScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("toast.useless_stretcher.capsule_enter"), 29, 33,
                StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        return time < 7000L * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
    }
}
