package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Real loaded worlds, including custom dimensions and previously saved physical instances. */
public final class CapsuleEnvironmentChecks {
    private CapsuleEnvironmentChecks() { }
    public static void run(MinecraftServer server) {
        int sources = 0;
        for (var source : server.getAllLevels()) {
            if (CapsuleDimensions.isCapsule(source.dimension().location())) continue;
            var key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(DimensionCapsuleCatalog.targetFor(source)));
            var target = server.getLevel(key);
            check(target != null && target != source, "isolated physical mirror for " + source.dimension().location());
            check(target.dimensionType() == source.dimensionType(), "exact runtime dimension type retained");
            check(target.dimensionTypeRegistration().equals(source.dimensionTypeRegistration()), "registered type holder retained for clients");
            check(target.getMinBuildHeight() == source.getMinBuildHeight() && target.getHeight() == source.getHeight(), "build limits retained");
            check(target.getWorldBorder().isWithinBounds(new BlockPos(3_648_000, source.getMinBuildHeight() + 1, 3_648_000)),
                    "storage border includes reserved rooms even when the source border is small");
            if (source.dimension().equals(net.minecraft.world.level.Level.OVERWORLD))
                LogUtils.getLogger().info("CAPSULE BORDER CHECK: source={} storage={}", source.getWorldBorder().getSize(), target.getWorldBorder().getSize());
            check(target.getChunkSource().getGenerator() instanceof CapsuleVoidChunkGenerator, "void generator retained");
            var generator = target.getChunkSource().getGenerator();
            var state = target.getChunkSource().randomState();
            check(generator.getBaseHeight(100, 100, Heightmap.Types.WORLD_SURFACE_WG, target, state) == target.getMinBuildHeight(), "no natural terrain height");
            check(generator.getBaseColumn(100, 100, target, state).getBlock(target.getMinBuildHeight()).isAir(), "no natural terrain column");
            sources++;
        }
        int restored = 0;
        verifyArchaeologyProcessors(server);
        for (var room : CapsuleInstances.get(server).rooms.values()) {
            if (!room.ready || !room.sourceCoordinates) continue;
            var target = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(room.dimension)));
            var source = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(room.sourceDimension)));
            check(target != null && source != null, "saved physical environment restored before login");
            var plan = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source), room.plan, room.sourceSeed);
            check(plan != null && plan.isValid(), "real restart retains native Piece graph");
            check(target.getBlockState(new BlockPos(room.minX, room.minY - 1, room.minZ))
                    .is(net.minecraft.world.level.block.Blocks.BEDROCK), "real restart retains placed foundation");
            BlockPos center = plan.getBoundingBox().getCenter();
            var expected = source.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                    Math.floorDiv((center.getX() >> 2 << 2) + room.sourceOffsetX, 4),
                    Math.floorDiv((center.getY() >> 2 << 2) + room.sourceOffsetY, 4),
                    Math.floorDiv((center.getZ() >> 2 << 2) + room.sourceOffsetZ, 4),
                    source.getChunkSource().randomState().sampler());
            check(target.getChunkAt(center).getNoiseBiome(center.getX() >> 2, center.getY() >> 2, center.getZ() >> 2).equals(expected),
                    "real restart retains the source three-dimensional biome palette");
            auditContents(target, plan, room.structure);
            restored++;
        }
        LogUtils.getLogger().info("CAPSULE ENVIRONMENT CHECKS PASSED: {} source mirrors, {} completed native rooms restored from disk", sources, restored);
    }
    private static void verifyArchaeologyProcessors(MinecraftServer server) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("allthemodium")) return;
        var registry = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        String[][] rules = {{"minecraft:ancient_city_generic_degradation", "allthemodium:suspicious_clay", "allthemodium:arch"},
                {"minecraft:treasure_rooms", "allthemodium:suspicious_soul_sand", "allthemodium:arch2"}};
        for (String[] rule : rules) {
            var list = registry.get(new ResourceLocation(rule[0]));
            check(list != null, "active archaeological processor list exists: " + rule[0]);
            String json = net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.DIRECT_CODEC
                    .encodeStart(ops, list).getOrThrow().toString();
            check(json.contains(rule[1]) && json.contains(rule[2]), "runtime processor retains the modded block and loot table: " + rule[0]);
        }
        LogUtils.getLogger().info("CAPSULE RUNTIME ARCHAEOLOGY PROCESSORS PASSED: ancient-city clay/arch and bastion soul-sand/arch2");
    }
    private static void auditContents(net.minecraft.server.level.ServerLevel level, StructureStart plan, String id) {
        var bounds = plan.getBoundingBox();
        int loot = 0, spawners = 0, archaeology = 0;
        var mobs = new java.util.TreeSet<String>();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                for (var blockEntity : java.util.List.copyOf(level.getChunk(x, z).getBlockEntities().values())) {
                    if (!bounds.isInside(blockEntity.getBlockPos())) continue;
                    if (blockEntity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container
                            && container.getLootTable() != null) loot++;
                    if (blockEntity instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
                        String entityId = spawner.saveWithoutMetadata(level.registryAccess())
                                .getCompound("SpawnData").getCompound("entity").getString("id");
                        check(!entityId.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                                .containsKey(new ResourceLocation(entityId)), "saved native spawner entity is registered: " + entityId);
                        mobs.add(entityId); spawners++;
                    }
                    if (blockEntity instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity brushable) {
                        check(!brushable.saveWithoutMetadata(level.registryAccess()).getString("LootTable").isEmpty(),
                                "saved archaeology block keeps its native loot table");
                        archaeology++;
                    }
                }
            }
        }
        if (id.equals("allthemodium:dungeon")) {
            check(loot > 0, "real ATM dungeon retains unopened loot containers after restart");
            check(spawners > 0, "real ATM dungeon retains native mob spawners after restart");
        }
        LogUtils.getLogger().info("CAPSULE SAVED CONTENTS {}: loot={} spawners={} archaeology={} mobs={}", id, loot, spawners, archaeology, mobs);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
