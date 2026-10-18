package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** All accessory slots use the same animated head perch; Curios owns visibility/cosmetic selection. */
public final class TwoIDollCurioRenderer implements ICurioRenderer {
    public static void register() {
        CuriosRendererRegistry.register(ModItems.TWO_I_DOLL.get(), TwoIDollCurioRenderer::new);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        if (!(parent.getModel() instanceof HeadedModel model) || !isDisplayed(context)) return;
        pose.pushPose();
        TwoIDollHeadLayer.transformToHead(model.getHead(), pose);
        var wearer = context.entity();
        TwoIDollItemRenderer.get().renderCarried(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                TwoIDollCarryMotion.sample(wearer, partialTick),
                TwoIDollWeather.sample(wearer, wearer.level(), wearer.blockPosition(), partialTick,
                        wearer.getUUID().hashCode()));
        pose.popPose();
    }

    /** Select one visible doll in native slot order, avoiding overlapping dolls and the vanilla head layer. */
    public static boolean isDisplayed(SlotContext context) {
        var wearer = context.entity();
        if (wearer.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.TWO_I_DOLL.get())) return false;
        return CuriosApi.getCuriosInventory(wearer).map(inventory -> {
            for (var entry : inventory.getCurios().entrySet()) {
                var handler = entry.getValue();
                for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                    ItemStack cosmetic = handler.getCosmeticStacks().getStackInSlot(i);
                    boolean useCosmetic = !cosmetic.isEmpty();
                    boolean visible = handler.getRenders().size() > i && handler.getRenders().get(i);
                    ItemStack displayed = useCosmetic ? cosmetic : visible ? handler.getStacks().getStackInSlot(i) : ItemStack.EMPTY;
                    if (displayed.is(ModItems.TWO_I_DOLL.get()))
                        return context.identifier().equals(entry.getKey()) && context.index() == i
                                && context.cosmetic() == useCosmetic;
                }
            }
            return false;
        }).orElse(false);
    }
}
