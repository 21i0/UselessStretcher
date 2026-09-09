package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Draws the current multiplier on the accelerated target, with a small progress bar right
 * below the number so the player can see how much longer the acceleration lasts.
 */
@OnlyIn(Dist.CLIENT)
public class WondrousStaffAccelerationRenderer
        extends EntityRenderer<WondrousStaffAccelerationEntity> {
    private static final float TEXT_SCALE = 0.02F;
    private static final float BAR_WIDTH = 36.0F;
    private static final float BAR_HEIGHT = 3.0F;
    private static final float BAR_Y = 11.0F;

    private final Font font;

    public WondrousStaffAccelerationRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.font = context.getFont();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(WondrousStaffAccelerationEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.level().getBlockState(entity.getTargetPos()).isAir()) return;

        boolean permanent = entity.isPermanent();
        String text = permanent
                ? String.format(Locale.ROOT, "x%d \u221E", entity.getSpeed())
                : String.format(Locale.ROOT, "x%d", entity.getSpeed());

        float fraction = permanent
                ? 1.0F
                : Mth.clamp(entity.getRemainingTime()
                        / (float) WondrousStaffAcceleration.DEFAULT_DURATION_TICKS, 0.0F, 1.0F);
        float fillR = permanent ? 1.0F : 0.25F;
        float fillG = permanent ? 0.7F : 0.8F;
        float fillB = permanent ? 0.1F : 1.0F;

        for (Direction face : Direction.values()) {
            poseStack.pushPose();
            moveToFace(poseStack, face);
            poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            this.font.drawInBatch(text, -this.font.width(text) / 2.0F, 0.0F, 0xFFFFFF, false,
                    poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
            drawProgressBar(poseStack, buffer, fraction, fillR, fillG, fillB);
            poseStack.popPose();
        }
    }

    private static void drawProgressBar(PoseStack poseStack, MultiBufferSource buffer,
                                        float fraction, float r, float g, float b) {
        float x0 = -BAR_WIDTH / 2.0F;
        float x1 = BAR_WIDTH / 2.0F;
        float y0 = BAR_Y;
        float y1 = BAR_Y + BAR_HEIGHT;

        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        addQuad(vc, poseStack, x0, y0, x1, y1, 0.15F, 0.15F, 0.15F, 0.85F);

        float fillX = x0 + BAR_WIDTH * fraction;
        if (fillX > x0) {
            addQuad(vc, poseStack, x0, y0, fillX, y1, r, g, b, 0.9F);
        }
    }

    private static void addQuad(VertexConsumer vc, PoseStack poseStack,
                                float x0, float y0, float x1, float y1,
                                float r, float g, float b, float a) {
        Matrix4f pose = poseStack.last().pose();
        vc.addVertex(pose, x0, y0, 0.0F).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y0, 0.0F).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y1, 0.0F).setColor(r, g, b, a);
        vc.addVertex(pose, x0, y1, 0.0F).setColor(r, g, b, a);
    }

    private static void moveToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case UP -> {
                poseStack.translate(0.0D, 0.506D, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            case DOWN -> {
                poseStack.translate(0.0D, -0.506D, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0D, 0.0D, -0.506D);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.0D, 0.0D, 0.506D);
            case WEST -> {
                poseStack.translate(-0.506D, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            }
            case EAST -> {
                poseStack.translate(0.506D, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(WondrousStaffAccelerationEntity entity) {
        return null;
    }
}
