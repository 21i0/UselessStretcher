package com.sorrowmist.useless.stretcher.compat;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

/** The switch belongs to the staff, so normal inventory save/sync preserves it. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class StaffRadiationProtection {
    private StaffRadiationProtection() { }

    public static boolean enabled(Player player) {
        if (player == null) return false;
        if (enabled(player.getMainHandItem()) || enabled(player.getOffhandItem())) return true;
        for (ItemStack stack : player.getInventory().items) if (enabled(stack)) return true;
        return false;
    }

    private static boolean enabled(ItemStack stack) {
        return stack.is(ModItems.WONDROUS_STAFF.get()) && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack,
                StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), false);
    }

    public static void configure(ServerPlayer player, boolean offhand, boolean enabled) {
        ItemStack stack = offhand ? player.getOffhandItem() : player.getMainHandItem();
        if (!stack.is(ModItems.WONDROUS_STAFF.get())) return;
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), enabled);
        if (enabled && ModList.get().isLoaded("mekanism")) MekanismRadiationCompat.clear(player);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        // Existing dose is cleared before player effects, including login and world changes.
        // MEK's own dose synchronization remains responsible for changed-only packets.
        if (event.player instanceof ServerPlayer player && player.tickCount % 20 == 0
                && ModList.get().isLoaded("mekanism") && enabled(player)) MekanismRadiationCompat.clear(player);
    }
}
