package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.UUID;

/** Checks the placed city, not merely a ready packet or a nonempty preview texture. */
public final class ForgeCapsuleAncientCityChecks {
    private ForgeCapsuleAncientCityChecks() { }

    public static void verify(MinecraftServer server, UUID owner, String dimension) {
        var room = CapsuleInstances.get(server).rooms.get(
                CapsuleInstances.key(owner, "minecraft:ancient_city", dimension, 0));
        check(room != null && room.ready && room.error.isEmpty(), "ancient city room completed");
        int pieces = room.plan.getList("Children", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        check(pieces > 25, "complete native city piece graph: " + pieces);
        check(room.maxX - room.minX > 100 && room.maxZ - room.minZ > 100, "city-sized physical bounds");
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)));
        check(level != null, "city mirror level exists");
        long[] counts = new long[4]; // non-air structure blocks, sculk, reinforced deepslate, loot containers
        for (int x = room.minX >> 4; x <= room.maxX >> 4; x++) {
            for (int z = room.minZ >> 4; z <= room.maxZ >> 4; z++) {
                var chunk = level.getChunk(x, z);
                for (var section : chunk.getSections()) {
                    if (section.hasOnlyAir()) continue;
                    section.getStates().count((state, count) -> {
                        if (!state.isAir() && !state.is(Blocks.BEDROCK)) counts[0] += count;
                        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("sculk")) counts[1] += count;
                        if (state.is(Blocks.REINFORCED_DEEPSLATE)) counts[2] += count;
                    });
                }
                counts[3] += chunk.getBlockEntities().values().stream()
                        .filter(RandomizableContainerBlockEntity.class::isInstance).count();
            }
        }
        check(counts[0] > 10_000, "substantial physical ancient city: " + counts[0]);
        check(counts[1] > 0, "city sculk actually placed");
        check(counts[2] > 0, "central reinforced-deepslate frame actually placed");
        check(counts[3] > 0, "city loot containers actually placed");
        LogUtils.getLogger().info("CAPSULE ANCIENT CITY PLACEMENT PASSED: pieces={}, bounds={}x{}x{}, blocks={}, sculk={}, reinforced_deepslate={}, loot_containers={}",
                pieces, room.maxX - room.minX + 1, room.maxY - room.minY + 1, room.maxZ - room.minZ + 1,
                counts[0], counts[1], counts[2], counts[3]);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
