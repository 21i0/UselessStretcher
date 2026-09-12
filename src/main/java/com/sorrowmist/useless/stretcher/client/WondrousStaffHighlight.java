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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
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
import org.joml.Matrix4f;

import java.util.List;

/**
 * While the wondrous staff is held (and its time-acceleration switch is on): outlines the
 * animal under the crosshair and every accelerated machine. Machine outlines are a single
 * thick box: a flowing rainbow while the target is running at full speed, and gold while
 * the target is in dynamic idle throttling.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class WondrousStaffHighlight {
    private static final float EDGE_THICKNESS = 0.035F;
    private static final int RAINBOW_PERIOD_TICKS = 72;
    private static final int RAINBOW_TICK_SPEED = 2;

    private WondrousStaffHighlight() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        ItemStack held = findHeldStaff(player);
        if (held.isEmpty()) return;

        boolean seeThrough = StretcherConfig.highlightSeeThrough();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer quads = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.debugQuads());

        if (minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof AgeableMob ageable) {
            drawThickBox(poseStack, quads, ageable.getBoundingBox().inflate(0.05D), camPos,
                    0.0F, 1.0F, 0.4F, 0.9F, seeThrough);
        }

        List<WondrousStaffAccelerationEntity> machines = minecraft.level.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                new AABB(player.blockPosition()).inflate(64.0D),
                entity -> entity.getMode() == WondrousStaffAccelerationEntity.MODE_BLOCK);
        for (WondrousStaffAccelerationEntity machine : machines) {
            drawFlowingBox(poseStack, quads, new AABB(machine.getTargetPos()), camPos,
                    minecraft.level.getGameTime(), machine.isIdleThrottled(), 0.9F, seeThrough);
        }
    }

    /** Returns an enabled staff from either hand, preferring the main hand when both contain one. */
    private static ItemStack findHeldStaff(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof WondrousStaffItem
                && WondrousStaffAcceleration.isEnabled(mainHand)) return mainHand;

        ItemStack offHand = player.getOffhandItem();
        return offHand.getItem() instanceof WondrousStaffItem
                && WondrousStaffAcceleration.isEnabled(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static void drawThickBox(PoseStack poseStack, VertexConsumer quads, AABB box, Vec3 camPos,
                                     float r, float g, float b, float a, boolean seeThrough) {
        drawBox(poseStack, quads, box, camPos, r, g, b, a, seeThrough, false, 0L, false);
    }

    private static void drawFlowingBox(PoseStack poseStack, VertexConsumer quads, AABB box, Vec3 camPos,
                                       long gameTime, boolean throttled, float alpha, boolean seeThrough) {
        drawBox(poseStack, quads, box, camPos, 0.0F, 0.0F, 0.0F, alpha, seeThrough,
                true, gameTime, throttled);
    }

    private static void drawBox(PoseStack poseStack, VertexConsumer quads, AABB box, Vec3 camPos,
                                float solidR, float solidG, float solidB, float alpha, boolean seeThrough,
                                boolean flowing, long gameTime, boolean throttled) {
        Vec3[] corners = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ)
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        if (seeThrough) RenderSystem.disableDepthTest();
        Matrix4f pose = poseStack.last().pose();

        for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
            int[] edge = edges[edgeIndex];
            Vec3 from = corners[edge[0]];
            Vec3 to = corners[edge[1]];
            Vec3 dir = to.subtract(from);
            if (dir.lengthSqr() < 1.0E-8D) continue;
            dir = dir.normalize();

            Vec3 mid = from.add(to).scale(0.5D);
            Vec3 view = camPos.subtract(mid);
            if (view.lengthSqr() < 1.0E-8D) continue;
            view = view.normalize();

            Vec3 side = dir.cross(view);
            if (side.lengthSqr() < 1.0E-8D) {
                side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
                if (side.lengthSqr() < 1.0E-8D) side = dir.cross(new Vec3(0.0D, 0.0D, 1.0D));
            }
            side = side.normalize().scale(EDGE_THICKNESS * 0.5D);

            float r = solidR;
            float g = solidG;
            float b = solidB;
            if (flowing) {
                if (throttled) {
                    // Gold is deliberately static: it is an immediate visual indication that
                    // the server has entered its reduced idle-tick path.
                    r = 1.0F;
                    g = 0.65F;
                    b = 0.05F;
                } else {
                    long phase = Math.floorMod(gameTime * RAINBOW_TICK_SPEED
                            + edgeIndex * (long) (RAINBOW_PERIOD_TICKS / edges.length),
                            (long) RAINBOW_PERIOD_TICKS);
                    int rgb = Mth.hsvToRgb(phase / (float) RAINBOW_PERIOD_TICKS, 0.9F, 1.0F);
                    r = ((rgb >> 16) & 0xFF) / 255.0F;
                    g = ((rgb >> 8) & 0xFF) / 255.0F;
                    b = (rgb & 0xFF) / 255.0F;
                }
            }
            addQuad(quads, pose, from.add(side), from.subtract(side), to.subtract(side), to.add(side), r, g, b, alpha);
        }

        if (seeThrough) RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void addQuad(VertexConsumer vc, Matrix4f pose,
                                Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                                float r, float g, float bl, float alpha) {
        vc.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, alpha);
        vc.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, alpha);
    }
}
