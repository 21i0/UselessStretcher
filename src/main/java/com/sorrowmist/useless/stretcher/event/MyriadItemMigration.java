package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Remove legacy inline mold lists before dropped items or login inventories are synchronized. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class MyriadItemMigration {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity entity
                && MyriadMoldData.externalize(level, entity.getItem())) entity.setItem(entity.getItem());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void login(PlayerEvent.LoadFromFile event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        migrate(level, event.getEntity().getInventory());
        migrate(level, event.getEntity().getEnderChestInventory());
    }

    private static void migrate(ServerLevel level, Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (MyriadMoldData.externalize(level, container.getItem(slot))) container.setChanged();
        }
    }
}
