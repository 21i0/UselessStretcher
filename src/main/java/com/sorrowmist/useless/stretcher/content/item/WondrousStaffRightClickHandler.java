package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != ModItems.WONDROUS_STAFF.get()) return;
        if (!player.isShiftKeyDown()) return;
        if (!WondrousStaffAcceleration.isEnabled(stack)) return;

        UseOnContext ctx = new UseOnContext(event.getLevel(), player, event.getHand(), stack, event.getHitVec());
        WondrousStaffAcceleration.tryUse(ctx);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
