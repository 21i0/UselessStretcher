package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.stretcher.content.item.UselessStretcherItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Outlines a valid delivery target while the stretcher is held. */
@EventBusSubscriber(value = Dist.CLIENT)
public final class StretcherHighlight {
    private StretcherHighlight() {
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof UselessStretcherItem)) return;

        BlockHitResult target = event.getTarget();
        if (target.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = target.getBlockPos();
        BlockEntity be = minecraft.level.getBlockEntity(pos);
        if (!(be instanceof MePatternAssemblyBlockEntity
                || be instanceof PassiveCraftingHatchBlockEntity
                || be instanceof AdvancedAlloyFurnaceBlockEntity)) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        AABB box = new AABB(pos).inflate(0.004);

        event.getPoseStack().pushPose();
        event.getPoseStack().translate(-camPos.x, -camPos.y, -camPos.z);
        LevelRenderer.renderLineBox(event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()),
                box, 0.0f, 1.0f, 0.6f, 0.9f);
        event.getPoseStack().popPose();
    }
}
