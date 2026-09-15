package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/** A translucent, full-bright vanilla clock that floats and rotates without occupying a block. */
@OnlyIn(Dist.CLIENT)
public final class TimeFlowRenderer extends EntityRenderer<TimeFlowEntity> {
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
    private final ItemRenderer itemRenderer;

    public TimeFlowRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
        shadowRadius = 0.0F;
    }

    @Override
    public void render(TimeFlowEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        float bob = Mth.sin(age / 10.0F + entity.getId()) * 0.08F;
        float alpha = entity.isEnabled() ? 0.62F : 0.28F;
        BakedModel model = itemRenderer.getModel(CLOCK, entity.level(), null, entity.getId());
        MultiBufferSource translucent = ignored -> new AlphaVertexConsumer(
                buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS)), alpha);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.55F + bob, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(age / (entity.isEnabled() ? 16.0F : 48.0F)));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.85F, 0.85F, 0.85F);
        itemRenderer.render(CLOCK, ItemDisplayContext.GROUND, false, poseStack, translucent,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TimeFlowEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static final class AlphaVertexConsumer extends VertexConsumerWrapper {
        private final float alpha;

        private AlphaVertexConsumer(VertexConsumer parent, float alpha) {
            super(parent);
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            parent.setColor(red, green, blue, Math.round(sourceAlpha * alpha));
            return this;
        }
    }
}
