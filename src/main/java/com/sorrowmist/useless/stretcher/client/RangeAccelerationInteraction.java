package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Sends range placement and label-gun-style filter-marking gestures from the client. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class RangeAccelerationInteraction {
    private RangeAccelerationInteraction() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;

        ItemStack staff = event.getItemStack();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;

        if (RangeAccelerationSettings.filterMarkingMode(staff)) {
            if (WondrousStaffAcceleration.isValidTarget(event.getLevel(), event.getPos())) {
                RangeNetwork.toggleFilter(event.getHand(), event.getPos(), Screen.hasControlDown());
            }
        } else if (RangeAccelerationSettings.placementMode(staff)
                && WondrousStaffAcceleration.isEnabled(staff)
                && event.getEntity().isShiftKeyDown()) {
            RangeNetwork.place(event.getHand(), event.getPos(), event.getFace());
        } else {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
