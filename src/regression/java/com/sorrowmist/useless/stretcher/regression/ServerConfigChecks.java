package com.sorrowmist.useless.stretcher.regression;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.ServerConfigSync;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.acceleration.PermanentAccelerationHistory;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.config.ModConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Isolated, opt-in checks for 1.5.1. No test source is shipped in the release jar. */
final class ServerConfigChecks {
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    static void run(ServerLevel level) throws Exception {
        var config = ServerConfigSync.config();
        check(config != null && config.getType() == ModConfig.Type.SERVER, "all settings use SERVER config");
        var values = config.getLoadedConfig().config();
        check(values.contains("acceleration.idle_throttle") && values.contains("highlight.highlight_see_through")
                && !values.contains("gameplay"), "original config paths retained without accidental nesting");

        Path migration = Files.createTempDirectory(Path.of("."), "config-migration-test-");
        Path legacy = migration.resolve("useless_stretcher-common.toml");
        String original = "[summoning]\nenable = true\n[staff_leaf_drop]\nprobability = 0.5\n"
                + "[highlight]\nhighlight_see_through = true\n[server_controls]\nrange_acceleration = false\n";
        Files.writeString(legacy, original);
        ServerConfigSync.migrateLegacy(migration, migration.resolve("defaults"));
        var migrated = new TomlParser().parse(Files.readString(migration.resolve(ServerConfigSync.FILE_NAME)));
        check(Boolean.TRUE.equals(migrated.get("summoning.enable")), "legacy summon setting migrated");
        check(Boolean.TRUE.equals(migrated.get("highlight.highlight_see_through")), "highlight migrated into server config");
        check(Boolean.FALSE.equals(migrated.get("server_controls.range_acceleration")), "legacy controls preserved");
        check(Double.valueOf(0.5).equals(migrated.get("staff_leaf_drop.probability")), "legacy probability preserved");
        check(original.equals(Files.readString(legacy)), "legacy file retained untouched");
        Files.writeString(migration.resolve(ServerConfigSync.FILE_NAME), "# existing server configuration\n");
        ServerConfigSync.migrateLegacy(migration, migration.resolve("defaults"));
        check(Files.readString(migration.resolve(ServerConfigSync.FILE_NAME)).equals("# existing server configuration\n"),
                "migration cannot overwrite existing server config");

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            byte[] bytes = new TomlWriter().writeToString(values).getBytes(StandardCharsets.UTF_8);
            ServerConfigSync.Payload.STREAM_CODEC.encode(buffer, new ServerConfigSync.Payload(bytes));
            check(java.util.Arrays.equals(bytes, ServerConfigSync.Payload.STREAM_CODEC.decode(buffer).contents()),
                    "full server config packet roundtrip");
        } finally { buffer.release(); }
        boolean summon = StretcherConfig.enableStaffSummon();
        try {
            StretcherConfig.SERVER_STAFF_SUMMON.set(!summon);
            StretcherConfig.SERVER_SPEC.save();
            check(StretcherConfig.enableStaffSummon() != summon, "saved host value applies immediately");
        } finally {
            StretcherConfig.SERVER_STAFF_SUMMON.set(summon);
            StretcherConfig.SERVER_SPEC.save();
        }

        BlockPos pos = new BlockPos(8, 115, 8);
        level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
        UUID owner = UUID.randomUUID();
        var marker = new WondrousStaffAccelerationEntity(level, pos, 4);
        marker.setOwnerUuid(owner);
        marker.setRemainingTime(WondrousStaffAccelerationEntity.PERMANENT);
        var history = PermanentAccelerationHistory.get(level.getServer());
        history.track(level, marker);
        check(history.activeEntries(owner).stream().anyMatch(e -> e.id().equals(marker.getUUID())),
                "permanent machine history indexed");
        check(!history.reclaim(level.getServer(), marker.getUUID(), UUID.randomUUID()), "personal reclaim checks owner");
        marker.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        check(history.activeEntries(owner).size() == 1, "unloading keeps machine history");
        check(history.reclaim(level.getServer(), marker.getUUID(), owner), "remote reclaim of unloaded machine");
        var restored = PermanentAccelerationHistory.load(history.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(restored.isReclaimed(marker.getUUID()) && restored.activeEntries(owner).isEmpty(), "reclaim survives world save/load");
        var reloadedMarker = new WondrousStaffAccelerationEntity(level, pos, 4);
        reloadedMarker.setUUID(marker.getUUID());
        reloadedMarker.setOwnerUuid(owner);
        reloadedMarker.setRemainingTime(WondrousStaffAccelerationEntity.PERMANENT);
        reloadedMarker.tick();
        check(reloadedMarker.isRemoved(), "reclaimed marker cannot restart when chunk loads");
        check(!history.isReclaimed(marker.getUUID()), "confirmed removal clears tombstone");
        level.removeBlock(pos, false);

        for (String id : List.of("goat_horn", "turtle_scute", "enchanted_golden_apple", "wither_rose",
                "dragon_head", "shulker_shell", "phantom_membrane", "dragon_breath", "elytra", "totem_of_undying",
                "wither_skeleton_skull", "omniversal_myriad", "useless_stretcher", "wondrous_staff")) {
            check(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("useless_stretcher:" + id)).isPresent(),
                    "GuideME exact recipe exists: " + id);
        }
        LogUtils.getLogger().info("REGRESSION 1.5.1: server config, migration, payload, save, permanent history ownership/unload/reclaim and 14 guide recipes passed");
    }
}
