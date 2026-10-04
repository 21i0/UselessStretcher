package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.LightningRodBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * While the staff's time-acceleration switch is on, Shift + right-click must only accelerate.
 * AE2 dismantles its machines through a {@code RightClickBlock} handler that runs before the
 * item's own {@code onItemUseFirst}, so it would otherwise win. This handler runs first,
 * performs our acceleration, and cancels the event so the AE2 wrench hook sees a cancelled
 * event and skips the dismantle.
 */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class WondrousStaffRightClickHandler {
    private WondrousStaffRightClickHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != ModItems.WONDROUS_STAFF.get()) return;
        if (com.sorrowmist.useless.stretcher.config.StretcherConfig.enableApotheosisCompat()
                && com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings.isEnchantingAvailable()
                && com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings.selectionMode(stack)
                && com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings.isTable(
                        event.getLevel().getBlockState(event.getPos()))) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings.markTable(
                        serverPlayer, event.getHand(), event.getPos());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (RangeAccelerationSettings.filterMarkingMode(stack)) {
            // The client sends a dedicated filter packet. The logical server must suppress any
            // vanilla block use that still arrives while marking is active.
            if (!event.getLevel().isClientSide) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        boolean lightningRod = event.getLevel().getBlockState(event.getPos()).getBlock() instanceof LightningRodBlock;
        // Range placement keeps its Shift gesture and must not turn a normal right-click into an
        // acceleration action.  Outside placement mode, an enabled staff owns rod clicks even
        // without Shift so the upstream staff cannot run its lightning-collector action first.
        if (RangeAccelerationSettings.placementMode(stack) && !player.isShiftKeyDown()) return;
        if (!WondrousStaffAcceleration.isEnabled(stack)) return;

        // The upstream staff only guards the sneaking path.  Wrench implementations commonly
        // handle an ordinary RightClickBlock event themselves, however, so an enabled
        // acceleration staff could still dismantle/rotate a machine before its own action ran.
        // Suppress that path for modded block entities as well; vanilla containers and normal
        // tool actions remain available.  This deliberately does not add another item tag or
        // event listener for wrench classification.
        if (!player.isShiftKeyDown() && !lightningRod) {
            if (isModdedBlockEntity(event.getLevel().getBlockEntity(event.getPos()))) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (RangeAccelerationSettings.placementMode(stack)) {
            // The client sends a dedicated placement packet. The server side of this event only
            // suppresses the machine's normal use and tool actions.
            if (!event.getLevel().isClientSide) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        UseOnContext ctx = new UseOnContext(event.getLevel(), player, event.getHand(), stack, event.getHitVec());
        WondrousStaffAcceleration.tryUse(ctx);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean isModdedBlockEntity(BlockEntity blockEntity) {
        return blockEntity != null && !blockEntity.getClass().getName().startsWith("net.minecraft.");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = tryEntityInteraction(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = tryEntityInteraction(event.getEntity(), event.getItemStack(), event.getTarget());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    private static InteractionResult tryEntityInteraction(Player player, ItemStack stack, Entity target) {
        if (stack.getItem() == ModItems.WONDROUS_STAFF.get()
                && RangeAccelerationSettings.filterMarkingMode(stack)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() || stack.getItem() != ModItems.WONDROUS_STAFF.get()
                || !WondrousStaffAcceleration.isEnabled(stack)) return InteractionResult.PASS;
        return WondrousStaffAcceleration.tryUseEntity(player, target, stack);
    }
}
