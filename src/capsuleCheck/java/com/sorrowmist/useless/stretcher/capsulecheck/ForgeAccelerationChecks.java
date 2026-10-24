package com.sorrowmist.useless.stretcher.capsulecheck;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated server with the real optional mods; excluded from the release JAR. */
@Mod.EventBusSubscriber(modid = "useless_stretcher")
public final class ForgeAccelerationChecks {
    private static boolean started, done;
    private static long chunksRequestedAt;
    private static boolean ready;
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("stretcher.acceleration.check") || done || event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        var level = server.overworld();
        try {
            if (!started) {
                started = true;
                for (int x = 3; x <= 9; x++) for (int z = 3; z <= 6; z++) level.setChunkForced(x, z, true);
                chunksRequestedAt = level.getGameTime();
            }
            // Loading a LevelChunk does not immediately make its entity section visible.
            // Simulate clicks only after the forced chunks have entered the ticking set.
            if (level.getGameTime() - chunksRequestedAt < 40) return;
            for (int x = 3; x <= 9; x++) for (int z = 3; z <= 6; z++) {
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x, z))) {
                    if (level.getGameTime() - chunksRequestedAt > 600)
                        throw new AssertionError("Timed out loading test entity sections");
                    return;
                }
            }
            if (!ready) {
                ready = true;
                ForgeBuilderChecks.run(level);
            }
            if (!OccultismLiveRitualChecks.tick(level)) return;
            LogUtils.getLogger().info("FORGE OPTIONAL ACCELERATION CHECKS PASSED");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("FORGE OPTIONAL ACCELERATION CHECKS FAILED", failure);
        }
        done = true;
        server.halt(false);
    }
}
