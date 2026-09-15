package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.content.entity.StaffLeafRewardEntity;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Renders the protected carrier exactly like a single, slowly spinning dropped staff. */
@OnlyIn(Dist.CLIENT)
public final class StaffLeafRewardRenderer extends EntityRenderer<StaffLeafRewardEntity> {
    private final ItemRenderer itemRenderer;

    public StaffLeafRewardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    public void render(StaffLeafRewardEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack stack = entity.getDisplayStack();
        BakedModel model = itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        float animationTime = entity.tickCount + partialTick;
        float bob = Mth.sin(animationTime / 10.0F + entity.getId()) * 0.1F + 0.1F;

        poseStack.pushPose();
        poseStack.translate(0.0F, bob + 0.25F * model.getTransforms().ground.scale.y(), 0.0F);
        poseStack.mulPose(Axis.YP.rotation(animationTime / 20.0F + entity.getId()));
        itemRenderer.render(stack, ItemDisplayContext.GROUND, false, poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(StaffLeafRewardEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
