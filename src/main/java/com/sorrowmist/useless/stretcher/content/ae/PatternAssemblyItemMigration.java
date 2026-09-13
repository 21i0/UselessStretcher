package com.sorrowmist.useless.stretcher.content.ae;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Migrates already-saved legacy drops before they can be tracked and sent to a client. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class PatternAssemblyItemMigration {
    private PatternAssemblyItemMigration() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (!stack.is(com.sorrowmist.useless.init.ModItems.ME_PATTERN_ASSEMBLY.get())) return;
        if (PatternAssemblyDataBridge.externalize(level, stack)) {
            itemEntity.setItem(stack);
        }
    }

    /** Runs after server-side player NBT is read but before the login inventory is synchronized. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDataLoaded(PlayerEvent.LoadFromFile event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        migrateContainer(level, player.getInventory());
        migrateContainer(level, player.getEnderChestInventory());
    }

    private static void migrateContainer(ServerLevel level, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(com.sorrowmist.useless.init.ModItems.ME_PATTERN_ASSEMBLY.get())) continue;
            if (PatternAssemblyDataBridge.externalize(level, stack)) {
                container.setItem(slot, stack);
            }
        }
    }
}
