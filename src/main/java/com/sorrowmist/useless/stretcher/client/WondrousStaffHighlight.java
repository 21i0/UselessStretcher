package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * While the wondrous staff is held (and its time-acceleration switch is on): outlines the
 * animal under the crosshair and every accelerated machine. The outline is drawn as a doubled
 * box for visibility; whether it is occluded by walls or see-through is configurable.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class WondrousStaffHighlight {
    private WondrousStaffHighlight() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof WondrousStaffItem)) return;
        if (!WondrousStaffAcceleration.isEnabled(held)) return;

        boolean seeThrough = StretcherConfig.highlightSeeThrough();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        if (minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof AgeableMob ageable) {
            drawBox(poseStack, lines, ageable.getBoundingBox().inflate(0.05D), camPos,
                    0.0F, 1.0F, 0.4F, 0.9F, seeThrough);
        }

        List<WondrousStaffAccelerationEntity> machines = minecraft.level.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                new AABB(player.blockPosition()).inflate(64.0D),
                entity -> entity.getMode() == WondrousStaffAccelerationEntity.MODE_BLOCK);
        for (WondrousStaffAccelerationEntity machine : machines) {
            if (machine.isPermanent()) {
                drawBox(poseStack, lines, new AABB(machine.getTargetPos()), camPos,
                        1.0F, 0.7F, 0.1F, 0.95F, seeThrough);
            } else {
                drawBox(poseStack, lines, new AABB(machine.getTargetPos()), camPos,
                        0.2F, 0.8F, 1.0F, 0.9F, seeThrough);
            }
        }
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer lines, AABB box,
                                Vec3 camPos, float r, float g, float b, float a, boolean seeThrough) {
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        if (seeThrough) RenderSystem.disableDepthTest();
        // Draw twice (base + slightly inflated) so the outline reads as a thicker box.
        LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, a);
        LevelRenderer.renderLineBox(poseStack, lines, box.inflate(0.04D), r, g, b, a);
        if (seeThrough) RenderSystem.enableDepthTest();
        poseStack.popPose();
    }
}
