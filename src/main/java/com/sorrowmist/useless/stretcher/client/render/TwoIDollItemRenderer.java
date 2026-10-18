package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Every item display context uses the same real 3-D model, not a flat icon. */
public final class TwoIDollItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static TwoIDollItemRenderer instance;
    private TwoIDollRenderer.Model model;

    private TwoIDollItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static TwoIDollItemRenderer get() {
        if (instance == null) instance = new TwoIDollItemRenderer();
        return instance;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Re-bake lazily after the model-set reload has finished.
        model = null;
    }

    /** Client clock keeps inventory/paused-menu decorations moving too. */
    public static float animationTime() {
        return (Util.getMillis() % 8_000_000L) / 50.0F;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int light, int overlay) {
        // The player head layer anchors the doll on top of the actual head bone.
        // Vanilla CustomHeadLayer also calls the item renderer; suppress only that
        // duplicate path, never ordinary handheld, inventory or block rendering.
        if (context == ItemDisplayContext.HEAD) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        renderAtFeet(stack, poseStack, buffer, light, overlay);
        poseStack.popPose();
    }

    /** Local origin sits underneath the doll, for actual palm-supported rendering. */
    public void renderAtFeet(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        renderCarried(stack, poseStack, buffer, light, overlay, TwoIDollCarryMotion.Pose.STILL);
    }

    public void renderCarried(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                              TwoIDollCarryMotion.Pose motion) {
        renderCarried(stack, poseStack, buffer, light, overlay, motion, TwoIDollWeather.Pose.CLEAR);
    }

    public void renderCarried(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                              TwoIDollCarryMotion.Pose motion, TwoIDollWeather.Pose weather) {
        if (model == null) model = new TwoIDollRenderer.Model(
                Minecraft.getInstance().getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
        model.setupDecoration(animationTime());
        model.applyCarriedMotion(motion, animationTime());
        model.applyWeather(weather, animationTime());
        poseStack.pushPose();
        motion.apply(poseStack);
        poseStack.translate(0.0D, TwoIDollRenderer.FEET_Y / 16.0F * TwoIDollRenderer.RENDER_SCALE, 0.0D);
        poseStack.scale(TwoIDollRenderer.RENDER_SCALE, -TwoIDollRenderer.RENDER_SCALE, -TwoIDollRenderer.RENDER_SCALE);
        var vertices = ItemRenderer.getFoilBufferDirect(buffer,
                RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink())), false, stack.hasFoil());
        model.renderToBuffer(poseStack, vertices, light, overlay);
        model.renderAccessories(poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
