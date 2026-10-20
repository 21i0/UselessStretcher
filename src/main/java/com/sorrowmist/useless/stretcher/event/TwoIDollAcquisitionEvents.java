package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollAcquisition;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Announces completed item pickups after the stack entered the player inventory. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class TwoIDollAcquisitionEvents {
    private TwoIDollAcquisitionEvents() { }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void beforeItemPickup(EntityItemPickupEvent event) {
        var stack = event.getItem().getItem();
        if (stack.is(ModItems.TWO_I_DOLL.get()))
            com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.ensureIdentity(stack);
    }

    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getStack().is(ModItems.TWO_I_DOLL.get())) return;
        var original = event.getStack();
        var id = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(original, com.sorrowmist.useless.stretcher.init.StretcherComponents.TWO_I_DOLL_ID.get());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var received = player.getInventory().getItem(i);
            if (!received.is(ModItems.TWO_I_DOLL.get())) continue;
            if (id == null ? !net.minecraft.world.item.ItemStack.isSameItemSameTags(original, received)
                    : !id.equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(received, com.sorrowmist.useless.stretcher.init.StretcherComponents.TWO_I_DOLL_ID.get()))) continue;
            com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.claimOrCheckOwner(received, player);
            TwoIDollAcquisition.notifyAcquired(player, received);
            break;
        }
    }
}
