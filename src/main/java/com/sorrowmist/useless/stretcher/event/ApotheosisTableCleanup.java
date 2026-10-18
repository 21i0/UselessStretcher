package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import com.sorrowmist.useless.stretcher.network.ApotheosisNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Clears saved enchanting-table bindings when a player breaks the table. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class ApotheosisTableCleanup {
    private ApotheosisTableCleanup() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)
                || !ApotheosisStaffSettings.isTable(event.getState())) {
            return;
        }

        var data = ApotheosisTableData.get(level.getServer());
        var owner = data.remove(level.dimension().location(), event.getPos());
        if (owner == null) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) ApotheosisNetwork.sendMarkers(player);
    }
}
