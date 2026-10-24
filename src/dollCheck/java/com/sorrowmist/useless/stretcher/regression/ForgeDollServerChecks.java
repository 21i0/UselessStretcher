package com.sorrowmist.useless.stretcher.regression;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs the retained companion regressions against the official Forge release. Never shipped. */
@Mod.EventBusSubscriber(modid = "useless_stretcher")
public final class ForgeDollServerChecks {
    private interface Check { void run(ServerLevel level) throws Exception; }
    private static final Check[] CHECKS = {
        LeafRewardChecks::run,
        TwoIBondMemoryChecks::run,
        TwoIDollHeadwearChecks::run,
        TwoIDollSpawnEggChecks::run,
        TwoIInteractionChecks::run,
        TwoICompanionshipChecks::run,
        TwoICuriosChecks::run,
        TwoIDollDisplayChecks::run,
        TwoIFinalPolishChecks::run,
        TwoIDollProtectionChecks::run,
        TwoIForcedKillChecks::run,
        TwoIExternalCaptureChecks::run,
        com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldServerChecks::run
    };
    private static int ticks, stage, failures;
    private static boolean done;

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("stretcher.dollCheck") || done || event.phase != TickEvent.Phase.END
                || ++ticks < 40) return;
        var server = event.getServer();
        try {
            if (Boolean.getBoolean("stretcher.dollCaptureOnly")) {
                TwoIExternalCaptureChecks.run(server.overworld());
                LogUtils.getLogger().info("FORGE 2I CAPTURE CHECKS PASSED: actual Ars Nouveau and Productive Bees jars");
                done = true;
                server.halt(false);
                return;
            }
            if (stage == 0) {
                com.sorrowmist.useless.stretcher.content.entity.TwoIStorySessionChecks.main(new String[0]);
                com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldDataChecks.run(server.registryAccess());
                checkSaveSnapshot(server.overworld());
            }
            CHECKS[stage].run(server.overworld());
            LogUtils.getLogger().info("FORGE 2I SERVER STAGE PASSED: {}/{}", ++stage, CHECKS.length);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("FORGE 2I SERVER CHECKS FAILED at stage {}", stage, failure);
            failures++;
            stage++;
        }
        if (stage < CHECKS.length) return;
        if (failures == 0)
            LogUtils.getLogger().info("FORGE 2I SERVER CHECKS PASSED: acquisition, saved memory, headwear, capture/kill protection, gifts, three-round story, companionship, Curios, display, friendship, starfield");
        else LogUtils.getLogger().error("FORGE 2I SERVER CHECKS FAILED: {} stages", failures);
        done = true;
        server.halt(false);
    }

    public static final class SaveFixture {
        final net.minecraft.nbt.CompoundTag shared = new net.minecraft.nbt.CompoundTag();
        @SubscribeEvent public void save(net.minecraftforge.event.level.ChunkDataEvent.Save event) {
            event.getData().put("stretcher_snapshot_fixture", shared);
        }
    }

    private static void checkSaveSnapshot(ServerLevel overworld) throws Exception {
        var level = overworld.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation("useless_stretcher:capsule_overworld")));
        var fixture = new SaveFixture();
        fixture.shared.putInt("value", 7);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(fixture);
        try {
            var chunk = level.getChunk(0, 0);
            chunk.setUnsaved(true);
            var storage = level.getChunkSource().chunkMap;
            var save = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("save", net.minecraft.world.level.chunk.ChunkAccess.class);
            save.setAccessible(true);
            if (!(Boolean) save.invoke(storage, chunk)) throw new AssertionError("capsule fixture chunk was not saved");
            fixture.shared.putInt("value", 42);
            storage.flushWorker();
            var saved = storage.read(chunk.getPos()).join().orElseThrow();
            if (saved.getCompound("stretcher_snapshot_fixture").getInt("value") != 7)
                throw new AssertionError("capsule IO snapshot still shares mutable callback NBT");
            LogUtils.getLogger().info("CAPSULE SAVE SNAPSHOT PASSED: native Forge chunk-save callback tag detached before asynchronous IO");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(fixture);
        }
    }
}
