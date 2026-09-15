package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stable world-stage placement preview and label-gun-style range-list overlays. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class RangeAccelerationPreview {
    private static final float LABEL_SCALE = 0.018F;
    private static final double LABEL_FACE_OFFSET = 0.516D;
    private static final int BLACKLIST_COLOR = 0xF06A7A;
    private static final int WHITELIST_COLOR = 0x62E795;

    private RangeAccelerationPreview() {
    }

    /** Hide the vanilla one-block outline while one of the staff's world interaction modes is active. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !interactionModeStaff(minecraft.player).isEmpty()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        ItemStack staff = heldStaff(player);
        if (staff.isEmpty()) return;

        List<TimeFlowEntity> fields = minecraft.level.getEntitiesOfClass(TimeFlowEntity.class,
                player.getBoundingBox().inflate(96.0D));
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer outlines = buffers.getBuffer(RenderType.debugQuads());
        boolean seeThrough = StretcherConfig.highlightSeeThrough();
        long gameTime = minecraft.level.getGameTime();

        renderPlacedRangeBounds(event.getPoseStack(), fields, outlines, cameraPos,
                gameTime, seeThrough);

        if (RangeAccelerationSettings.placementMode(staff)
                && WondrousStaffAcceleration.isEnabled(staff)) {
            renderPlacementPreview(minecraft, event.getPoseStack(), staff, outlines,
                    cameraPos, gameTime, seeThrough);
        }
        if (RangeAccelerationSettings.filterMarkingMode(staff)) {
            renderMarkingOverlays(minecraft, event.getPoseStack(), player, staff, fields,
                    outlines, buffers, cameraPos, gameTime, seeThrough);
        }
    }

    private static void renderPlacedRangeBounds(PoseStack poseStack, List<TimeFlowEntity> fields,
                                                VertexConsumer outlines, Vec3 cameraPos,
                                                long gameTime, boolean seeThrough) {
        for (TimeFlowEntity field : fields) {
            AABB bounds = field.coveredBounds().inflate(0.003D);
            if (field.isEnabled()) {
                WondrousStaffHighlight.drawFlowingOutline(poseStack, outlines, bounds,
                        cameraPos, gameTime, field.isIdleThrottled(), 0.82F, seeThrough);
            } else {
                WondrousStaffHighlight.drawSolidOutline(poseStack, outlines, bounds,
                        cameraPos, 0.52F, 0.16F, 0.18F, 0.52F, seeThrough);
            }
        }
    }

    /** Always renders while placement mode is active, even if another mod suppresses block highlights. */
    private static void renderPlacementPreview(Minecraft minecraft, PoseStack poseStack, ItemStack staff,
                                               VertexConsumer outlines, Vec3 cameraPos,
                                               long gameTime, boolean seeThrough) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return;
        BlockPos center = RangeNetwork.placementCenter(
                minecraft.level, hit.getBlockPos(), hit.getDirection());
        AABB bounds = RangeAccelerationSavedData.bounds(center,
                RangeAccelerationSettings.sizeX(staff),
                RangeAccelerationSettings.sizeY(staff),
                RangeAccelerationSettings.sizeZ(staff)).inflate(0.008D);
        WondrousStaffHighlight.drawFlowingOutline(poseStack, outlines, bounds, cameraPos,
                gameTime, false, 0.96F, seeThrough, 0x66C8FF);
    }

    private static void renderMarkingOverlays(Minecraft minecraft, PoseStack poseStack, Player player,
                                              ItemStack staff, List<TimeFlowEntity> fields,
                                              VertexConsumer outlines, MultiBufferSource buffers,
                                              Vec3 cameraPos, long gameTime, boolean seeThrough) {
        List<TimeFlowEntity> ownedFields = fields.stream()
                .filter(TimeFlowEntity::isEnabled)
                .filter(field -> player.getUUID().equals(field.getOwner()))
                .toList();
        Set<Long> rendered = new HashSet<>();
        for (TimeFlowEntity field : ownedFields) {
            Set<Long> allMarks = new HashSet<>(field.getAccelerationMarks());
            allMarks.addAll(field.getSleepMarks());
            for (long packedPos : allMarks) {
                if (!rendered.add(packedPos)) continue;
                BlockPos pos = BlockPos.of(packedPos);
                if (!field.coveredBounds().contains(Vec3.atCenterOf(pos))
                        || !minecraft.level.isLoaded(pos)
                        || !WondrousStaffAcceleration.isValidTarget(minecraft.level, pos)) continue;
                List<LabelLine> labels = labelsFor(field, packedPos);
                int tint = labels.isEmpty() ? 0xFFFFFF : labels.getFirst().color();
                WondrousStaffHighlight.drawFlowingOutline(poseStack, outlines,
                        new AABB(pos).inflate(0.006D), cameraPos,
                        gameTime, false, 0.78F, seeThrough, tint);
                drawAttachedLabels(minecraft.font, poseStack, buffers, pos, cameraPos, labels);
            }
        }

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || !WondrousStaffAcceleration.isValidTarget(minecraft.level, hit.getBlockPos())) return;
        BlockPos target = hit.getBlockPos();
        TimeFlowEntity field = newestContaining(ownedFields, target);
        if (field == null) {
            WondrousStaffHighlight.drawSolidOutline(poseStack, outlines,
                    new AABB(target).inflate(0.006D), cameraPos,
                    0.95F, 0.18F, 0.18F, 0.9F, seeThrough);
            drawAttachedLabels(minecraft.font, poseStack, buffers, target, cameraPos,
                    List.of(new LabelLine(Component.translatable(
                            "gui.useless_stretcher.range.outside_range"), BLACKLIST_COLOR)));
            return;
        }

        boolean sleepList = RangeAccelerationSettings.markSleepList(staff);
        boolean whitelist = sleepList
                ? RangeAccelerationSettings.sleepWhitelistMode(staff)
                : RangeAccelerationSettings.whitelistMode(staff);
        int tint = whitelist ? WHITELIST_COLOR : BLACKLIST_COLOR;
        WondrousStaffHighlight.drawFlowingOutline(poseStack, outlines,
                new AABB(target).inflate(0.009D), cameraPos,
                gameTime, false, 0.98F, seeThrough, tint);
        if (!rendered.contains(target.asLong())) {
            drawAttachedLabels(minecraft.font, poseStack, buffers, target, cameraPos,
                    List.of(new LabelLine(Component.translatable(
                            listNameKey(sleepList, whitelist)), tint)));
        }
    }

    private static TimeFlowEntity newestContaining(List<TimeFlowEntity> fields, BlockPos pos) {
        return fields.stream()
                .filter(field -> field.coveredBounds().contains(Vec3.atCenterOf(pos)))
                .max(java.util.Comparator.comparingLong(TimeFlowEntity::getCreatedAt))
                .orElse(null);
    }

    private static List<LabelLine> labelsFor(TimeFlowEntity field, long packedPos) {
        List<LabelLine> labels = new ArrayList<>(2);
        if (field.getAccelerationMarks().contains(packedPos)) {
            labels.add(new LabelLine(Component.translatable(listNameKey(false,
                    field.isAccelerationWhitelist())), field.isAccelerationWhitelist()
                    ? WHITELIST_COLOR : BLACKLIST_COLOR));
        }
        if (field.getSleepMarks().contains(packedPos)) {
            labels.add(new LabelLine(Component.translatable(listNameKey(true,
                    field.isSleepWhitelist())), field.isSleepWhitelist()
                    ? WHITELIST_COLOR : BLACKLIST_COLOR));
        }
        return labels;
    }

    /** Draws labels against every machine face, matching the existing attached acceleration text. */
    private static void drawAttachedLabels(Font font, PoseStack poseStack, MultiBufferSource buffers,
                                           BlockPos pos, Vec3 cameraPos, List<LabelLine> labels) {
        if (labels.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5D - cameraPos.x,
                pos.getY() + 0.5D - cameraPos.y,
                pos.getZ() + 0.5D - cameraPos.z);
        for (Direction face : Direction.values()) {
            poseStack.pushPose();
            moveToFace(poseStack, face);
            poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
            float firstY = labels.size() == 1 ? 0.0F : -5.5F;
            for (int index = 0; index < labels.size(); index++) {
                LabelLine line = labels.get(index);
                font.drawInBatch(line.text(), -font.width(line.text()) / 2.0F,
                        firstY + index * 11.0F, line.color(), true,
                        poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                        0, LightTexture.FULL_BRIGHT);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void moveToFace(PoseStack poseStack, Direction face) {
        switch (face) {
            case UP -> {
                poseStack.translate(0.0D, LABEL_FACE_OFFSET, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            }
            case DOWN -> {
                poseStack.translate(0.0D, -LABEL_FACE_OFFSET, 0.0D);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case NORTH -> {
                poseStack.translate(0.0D, 0.0D, -LABEL_FACE_OFFSET);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.0D, 0.0D, LABEL_FACE_OFFSET);
            case WEST -> {
                poseStack.translate(-LABEL_FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            }
            case EAST -> {
                poseStack.translate(LABEL_FACE_OFFSET, 0.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
        }
    }

    private static String listNameKey(boolean sleepList, boolean whitelist) {
        if (sleepList) return whitelist
                ? "gui.useless_stretcher.range.sleep_whitelist"
                : "gui.useless_stretcher.range.sleep_blacklist";
        return whitelist
                ? "gui.useless_stretcher.range.acceleration_whitelist"
                : "gui.useless_stretcher.range.acceleration_blacklist";
    }

    private static ItemStack heldStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.WONDROUS_STAFF.get())) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(ModItems.WONDROUS_STAFF.get()) ? off : ItemStack.EMPTY;
    }

    private static ItemStack interactionModeStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isInteractionModeStaff(main)) return main;
        ItemStack off = player.getOffhandItem();
        return isInteractionModeStaff(off) ? off : ItemStack.EMPTY;
    }

    private static boolean isInteractionModeStaff(ItemStack stack) {
        if (!stack.is(ModItems.WONDROUS_STAFF.get())) return false;
        if (RangeAccelerationSettings.filterMarkingMode(stack)) return true;
        return WondrousStaffAcceleration.isEnabled(stack)
                && RangeAccelerationSettings.placementMode(stack);
    }

    private record LabelLine(Component text, int color) {
    }
}
