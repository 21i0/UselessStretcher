package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.util.UUID;

/** Real ATM processor regression: native pieces must keep ATM's archaeological blocks and block entities. */
public final class AllTheModiumCapsuleChecks {
    private static final String OVERWORLD = DimensionCapsuleCatalog.CAPSULE_OVERWORLD;
    private static final String NETHER = DimensionCapsuleCatalog.CAPSULE_NETHER;
    private static final String ANCIENT_CITY = "minecraft:ancient_city";
    private static final String BASTION = "minecraft:bastion_remnant";
    private static final String DUNGEON = "allthemodium:dungeon";
    private static final String OTHER = CapsuleDimensions.targetFor(ResourceLocation.parse("allthemodium:the_other"));
    private static final String CLAY = "allthemodium:suspicious_clay";
    private static final String SOUL_SAND = "allthemodium:suspicious_soul_sand";
    private static int stage;
    private static int ticks;
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;
    private static String structureId;
    private static String dimensionId;
    private static String blockId;
    private static CapsuleInstances.Instance completedDungeon;
    private static int journeys;

    private AllTheModiumCapsuleChecks() { }

    public static boolean tick(ServerLevel fallback) {
        if (!ModList.get().isLoaded("allthemodium")) {
            if (stage == 0) LogUtils.getLogger().info("CAPSULE ATM CHECKS SKIPPED: AllTheModium is not loaded");
            stage = 99;
            return true;
        }
        if (stage == 99) return true;
        if (++ticks > 60_000) throw new AssertionError("ATM capsule checks timed out at " + structureId + ": " + (room == null ? "not started" : room.error));
        if (stage == 0 || (stage == 2 && room == null)) {
            begin(fallback, ANCIENT_CITY, OVERWORLD, CLAY, "ATMAncientCity");
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (finishCurrent(fallback)) {
                begin(fallback, BASTION, NETHER, SOUL_SAND, "ATMBastion");
                stage = 2;
            }
            return false;
        }
        if (stage == 2) {
            if (finishCurrent(fallback)) {
                begin(fallback, DUNGEON, OTHER, "", "ATMDungeon");
                stage = 3;
            }
            return false;
        }
        if (stage == 3) {
            completedDungeon = room;
            if (finishCurrent(fallback)) {
                player.teleportTo(fallback, 12, 120, 12, 0, 0);
                NativeCapsules.request(player, DUNGEON, OTHER, 2, 0);
                stage = 4;
            }
            return false;
        }
        if (stage == 4) {
            if (!DimensionCapsuleService.hasReturnPoint(player)) return false;
            check(player.serverLevel().dimension().location().toString().equals(OTHER), "entry uses the stable custom mirror");
            var capsule = new net.minecraft.world.item.ItemStack(com.sorrowmist.useless.stretcher.init.ModItems.DIMENSION_CAPSULE.get());
            DimensionCapsuleService.enter(player, capsule);
            check(player.serverLevel() == fallback && !DimensionCapsuleService.hasReturnPoint(player)
                    && Math.abs(player.getX() - 12) < 1, "custom mirror returns to the original world and position");
            check(CapsuleInstances.get(fallback.getServer()).getOrCreate(player.getUUID(), DUNGEON, OTHER) == completedDungeon,
                    "repeated custom entry keeps the exact completed native instance");
            if (++journeys < 2) { NativeCapsules.request(player, DUNGEON, OTHER, 2, 2); return false; }
            stage = 99;
            LogUtils.getLogger().info("CAPSULE ATM CHECKS PASSED: native ancient city and bastion, active archaeological processor data, complete dungeon in exact Other environment, two stable mirror round trips; {} ticks", ticks);
            return true;
        }
        return true;
    }

    private static void begin(ServerLevel fallback, String id, String dimension, String block, String name) {
        var registry = fallback.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var structure = registry.get(ResourceLocation.parse(id));
        check(structure != null, "ATM test structure is registered: " + id);
        var level = fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
        check(level != null, "ATM test dimension is loaded: " + dimension);
        if (id.equals(DUNGEON)) {
            var original = fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse("allthemodium:the_other")));
            check(original != null && level.dimensionType() == original.dimensionType(), "ATM mirror shares the exact Other dimension type");
            check(level.getMinBuildHeight() == original.getMinBuildHeight() && level.getHeight() == original.getHeight(),
                    "ATM mirror preserves native vertical limits");
        }
        player = newPlayer(level, name);
        check(DimensionCapsuleCatalog.dimensions(player, structure).contains(dimension), id + " is offered in " + dimension);
        structureId = id;
        dimensionId = dimension;
        blockId = block;
        var data = CapsuleInstances.get(fallback.getServer());
        room = data.getOrCreate(player.getUUID(), id, dimension);
        var source = DimensionCapsuleCatalog.sourceLevel(player, structure, dimension);
        check(source != null, id + " has a compatible source world");
        moveOriginNearBiome(room, source, structure);
        NativeCapsules.request(player, id, dimension, 1, 0);
    }

    private static boolean finishCurrent(ServerLevel fallback) {
        check(room != null, "ATM room allocated");
        if (!room.error.isEmpty()) throw new AssertionError("ATM native generation failed for " + structureId + ": " + room.error);
        if (!room.ready) return false;
        var level = fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
        var start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(level), room.plan, level.getSeed());
        check(start != null && start.isValid(), "ATM native plan restores for " + structureId);
        assertStructureEnvelopeContainsNoTerrain(level, start);
        if (blockId.isEmpty()) {
            check(start.getPieces().size() > 1, "native allthemodium dungeon expands its complete jigsaw Piece graph");
            int nonAir = 0;
            for (BlockPos pos : BlockPos.betweenClosed(start.getBoundingBox().minX(), start.getBoundingBox().minY(), start.getBoundingBox().minZ(),
                    start.getBoundingBox().maxX(), start.getBoundingBox().maxY(), start.getBoundingBox().maxZ())) {
                if (!level.getBlockState(pos).isAir()
                        && level.structureManager().getStructureWithPieceAt(pos, start.getStructure()).isValid()) {
                    nonAir++;
                    if (nonAir >= 128) break;
                }
            }
            check(nonAir >= 128, "native allthemodium dungeon placed real structure blocks, not only terrain");
            room = null;
            return true;
        }
        Block expected = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId));
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(start.getBoundingBox().minX(), start.getBoundingBox().minY(), start.getBoundingBox().minZ(),
                start.getBoundingBox().maxX(), start.getBoundingBox().maxY(), start.getBoundingBox().maxZ())) {
            if (level.getBlockState(pos).is(expected)) {
                found++;
                check(level.getBlockEntity(pos) != null, "ATM archaeological block keeps its block entity at " + pos);
            }
        }
        // The ATM processor intentionally uses a 0.7% random match. A single native
        // placement is therefore not a sound assertion that the processor ran: a
        // valid seed can contain no transformed blocks at all. Verify the active
        // runtime processor list instead, and retain the stronger block-entity check
        // whenever the random branch happens to produce the archaeological block.
        if (found == 0) {
            var processors = fallback.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
            String processorId = structureId.equals(ANCIENT_CITY)
                    ? "minecraft:ancient_city_generic_degradation" : "minecraft:treasure_rooms";
            var processor = processors.get(ResourceLocation.parse(processorId));
            check(processor != null, "native " + structureId + " keeps its active archaeological processor list");
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, fallback.registryAccess());
            String encoded = net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType.DIRECT_CODEC
                    .encodeStart(ops, processor).getOrThrow().toString();
            check(encoded.contains(blockId), "active processor contains the expected modded archaeological block");
            LogUtils.getLogger().info("CAPSULE ATM PROCESSOR BRANCH NOT SELECTED {}: no {} in this variant; runtime processor {} contains the expected block",
                    structureId, blockId, processorId);
        }
        LogUtils.getLogger().info("CAPSULE ATM ARCHAEOLOGY COUNT {}: {}={}", structureId, blockId, found);
        room = null;
        return true;
    }

    private static void assertStructureEnvelopeContainsNoTerrain(ServerLevel level, StructureStart start) {
        var bounds = start.getBoundingBox();
        int residual = 0;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (!level.getBlockState(pos).isAir()
                    && !level.structureManager().getStructureWithPieceAt(pos, start.getStructure()).isValid()) {
                if (++residual <= 8) LogUtils.getLogger().error("CAPSULE ATM RESIDUAL terrain at {}: {}",
                        pos.immutable(), BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
            }
        }
        check(residual == 0, "ATM structure envelope contains only native structure blocks for " + structureId
                + "; residual terrain blocks=" + residual);
    }

    private static void moveOriginNearBiome(CapsuleInstances.Instance room, ServerLevel level, net.minecraft.world.level.levelgen.structure.Structure structure) {
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        var pair = source.findClosestBiome3d(new BlockPos(room.x(), level.getSeaLevel(), room.z()), 4096, 64, 64,
                structure.biomes()::contains, sampler, level);
        if (pair == null) throw new AssertionError("No compatible biome near the reserved ATM room for " + structureId);
        try {
            Field x = room.getClass().getDeclaredField("originX");
            Field z = room.getClass().getDeclaredField("originZ");
            x.setAccessible(true); z.setAccessible(true);
            x.setInt(room, pair.getFirst().getX());
            z.setInt(room, pair.getFirst().getZ());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot position ATM regression room", failure);
        }
    }

    private static ServerPlayer newPlayer(ServerLevel level, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var result = new ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
        };
        new io.netty.channel.embedded.EmbeddedChannel(transport);
        result.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), transport, result,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
        };
        return result;
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
