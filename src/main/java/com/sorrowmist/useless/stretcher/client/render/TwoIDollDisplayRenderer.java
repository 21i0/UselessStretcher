package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.content.block.TwoIDollDisplayBlock;
import com.sorrowmist.useless.stretcher.content.blockentity.TwoIDollDisplayBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** One cached model per renderer; placed dolls do not load chunks or tick on the server. */
public final class TwoIDollDisplayRenderer implements BlockEntityRenderer<TwoIDollDisplayBlockEntity> {
    private final TwoIDollRenderer.Model model;

    public TwoIDollDisplayRenderer(BlockEntityRendererProvider.Context context) {
        model = new TwoIDollRenderer.Model(context.bakeLayer(TwoIDollRenderer.LAYER));
    }

    @Override
    public void render(TwoIDollDisplayBlockEntity doll, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        // A stable position offset makes a shelf of dolls use different gestures.
        float offset = Math.floorMod(doll.getBlockPos().hashCode(), 1600);
        model.setupDecoration(TwoIDollItemRenderer.animationTime() + offset);
        model.applyWeather(TwoIDollWeather.sample(doll, doll.getLevel(), doll.getBlockPos(), partialTick,
                doll.getBlockPos().hashCode()), TwoIDollItemRenderer.animationTime() + offset);
        poseStack.pushPose();
        poseStack.translate(0.5D, TwoIDollRenderer.FEET_Y / 16.0F * TwoIDollRenderer.RENDER_SCALE, 0.5D);
        double now = doll.getLevel() == null ? 0 : doll.getLevel().getGameTime() + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees(-22.5F * doll.getBlockState()
                .getValue(TwoIDollDisplayBlock.ROTATION) + doll.rotationAt(now)));
        poseStack.scale(TwoIDollRenderer.RENDER_SCALE, -TwoIDollRenderer.RENDER_SCALE, -TwoIDollRenderer.RENDER_SCALE);
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),
                light, overlay);
        model.renderAccessories(poseStack, buffer, light, overlay);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
