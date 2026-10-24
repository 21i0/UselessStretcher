package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.util.Arrays;
import java.util.UUID;

/** Exercises native world-generation callbacks, not an artificial structure template. */
public final class NativeCapsuleChecks {
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;
    private static int stage, ticks;
    private static boolean resumed;
    private static boolean cleaningResumed;
    private static boolean trimmingResumed;
    private static boolean lateTrimPassResumed;
    private static CapsuleInstances.Instance adjacentRoom;
    private static StructureStart firstStart;
    private static int firstStructureBlocks;
    private static int previousProgress;
    private static int previousAdjacentProgress;
    private static final String ID = "minecraft:pillager_outpost";
    private static final String DIM = DimensionCapsuleCatalog.CAPSULE_OVERWORLD;

    public static boolean tick(ServerLevel level) {
        if (stage == 6) return true;
        if (++ticks > 18_000) throw new AssertionError("Native capsule timed out at stage " + stage + ": " + (room == null ? "not started" : room.error));
        if (stage == 0) {
            CapsuleEnvironmentChecks.run(level.getServer());
            var profile = new GameProfile(UUID.randomUUID(), "NativeCapsule");
            player = new ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
            var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
            };
            // A ServerPlayer created by the regression harness does not pass through the
            // Netty bootstrap.  Attach an in-memory channel so MinecraftForge's dimension-change
            // packet negotiation sees the same non-null channel as a real client.
            new io.netty.channel.embedded.EmbeddedChannel(transport);
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), transport, player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
            };
            assertDimension(player, "minecraft:fortress", DimensionCapsuleCatalog.CAPSULE_NETHER, DIM_OVERWORLD);
            assertDimension(player, "minecraft:bastion_remnant", DimensionCapsuleCatalog.CAPSULE_NETHER, DIM_OVERWORLD);
            assertDimension(player, "minecraft:trail_ruins", DimensionCapsuleCatalog.CAPSULE_OVERWORLD, DIM_NETHER);
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(ID));
            check(DimensionCapsuleCatalog.dimensions(player, structure).contains(DIM), "real biome-compatible dimension");
            check(DimensionCapsuleCatalog.scan(player).stream().anyMatch(e -> e.id().toString().equals(ID)), "procedural structure catalogue");
            NativeCapsules.request(player, ID, DIM, 1, 0);
            room = CapsuleInstances.get(level.getServer()).rooms.get(CapsuleInstances.key(player.getUUID(), ID, DIM));
            check(room != null, "request allocates room"); stage = 1; return false;
        }
        check(room.error.isEmpty(), "native callback failed: " + room.error);
        if (ticks % 200 == 0) LogUtils.getLogger().info("CAPSULE NATIVE HARNESS stage={} phase={} progress={} position={} removed={} alive={} return={}",
                stage, room.phase, room.progress, player.position(), player.isRemoved(), player.isAlive(),
                DimensionCapsuleService.hasReturnPoint(player));
        if (stage == 1 && room.plan.isEmpty()) {
            check(!room.sourceDimension.isEmpty(), "instance binds a concrete source dimension");
            var source = DimensionCapsuleCatalog.sourceLevel(player,
                    level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(ID)), DIM,
                    room.sourceDimension);
            check(source != null, "bound source dimension remains available during planning");
            check(room.sourceSeed == source.getSeed(), "instance stores the source world seed");
        }
        check(room.progress >= previousProgress && room.progress <= 99, "saved generation progress never regresses or completes early");
        previousProgress = room.progress;
        if (stage == 1) {
            if (!cleaningResumed && room.phase.equals("cleaning")) {
                // Empty storage skips air sections in one step. Persist a real voxel
                // checkpoint explicitly instead of depending on the machine's tick speed.
                if (room.cleanCursor == 0) room.cleanCursor = 1;
                long savedCursor = room.cleanCursor;
                NativeCapsules.stopped(null);
                CompoundTag saved = CapsuleInstances.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
                try {
                    var load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
                    load.setAccessible(true);
                    var restored = (CapsuleInstances)load.invoke(null, saved, level.registryAccess());
                    var checkpoint = restored.rooms.get(room.key());
                    check(checkpoint != null && checkpoint.phase.equals("cleaning")
                                    && checkpoint.cleanCursor == savedCursor && !checkpoint.terrainCleaned
                                    && checkpoint.progress == room.progress,
                            "partial terrain cleaning survives SavedData reload without migration reset");
                    CapsuleInstances.get(level.getServer()).rooms.put(room.key(), checkpoint);
                    room = checkpoint;
                    NativeCapsules.request(player, ID, DIM, 1, 0);
                    check(room.phase.equals("cleaning") && (room.cleanCursor == savedCursor
                                    || room.cleanCursor == 0 && room.cleanChunk == 0 && room.cleanupVersion < 2),
                            "resumed cleanup either continues its cursor or restarts safely for a clearance migration");
                    cleaningResumed = true;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                return false;
            }
            if (!resumed && room.afterPlaced && room.previewCursor > 0) {
                NativeCapsules.stopped(null);
                CompoundTag saved = CapsuleInstances.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
                try {
                    var load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
                    load.setAccessible(true);
                    var restored = (CapsuleInstances)load.invoke(null, saved, level.registryAccess());
                    var checkpoint = restored.rooms.get(room.key());
                    check(checkpoint.afterPlaced && checkpoint.previewCursor == room.previewCursor
                            && checkpoint.piece == room.piece && checkpoint.mapCursor == room.mapCursor,
                            "afterPlace and partial map sampling survive SavedData reload");
                    CapsuleInstances.get(level.getServer()).rooms.put(room.key(), checkpoint);
                    room = checkpoint;
                    NativeCapsules.request(player, ID, DIM, 1, 0);
                    resumed = true;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                return false;
            }
            // Chunk scans can finish between regression ticks. Check a deterministic late-pass
            // snapshot, then reload and resume the actual live checkpoint without advancing it.
            if (!lateTrimPassResumed && room.phase.equals("trimming")) {
                int savedPass = room.trimPass;
                long savedCursor = room.trimCursor;
                if (room.trimCursor == 0) room.trimCursor = 1;
                room.trimPass = 7;
                CompoundTag latePassSave = CapsuleInstances.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
                try {
                    var load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
                    load.setAccessible(true);
                    var restored = (CapsuleInstances)load.invoke(null, latePassSave, level.registryAccess());
                    var checkpoint = restored.rooms.get(room.key());
                    check(checkpoint != null && checkpoint.trimPass == 7 && checkpoint.trimCursor == room.trimCursor,
                            "late fluid-settle pass and cursor survive SavedData reload");
                    lateTrimPassResumed = true;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                room.trimPass = savedPass;
                room.trimCursor = savedCursor;
                NativeCapsules.stopped(null);
                CompoundTag saved = CapsuleInstances.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
                try {
                    var load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
                    load.setAccessible(true);
                    var restored = (CapsuleInstances)load.invoke(null, saved, level.registryAccess());
                    var checkpoint = restored.rooms.get(room.key());
                    check(checkpoint != null && checkpoint.phase.equals("trimming")
                                    && checkpoint.trimCursor == savedCursor,
                            "post-placement terrain trimming resumes from a saved voxel cursor");
                    CapsuleInstances.get(level.getServer()).rooms.put(room.key(), checkpoint);
                    room = checkpoint;
                    NativeCapsules.request(player, ID, DIM, 1, 0);
                    trimmingResumed = true;
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                return false;
            }
            if (!room.ready) return false;
            ServerLevel placed = roomLevel(level, room.dimension);
            check(placed != null, "native room target dimension is loaded");
            check(resumed, "native generation resumed from a persisted partial chunk");
            check(room.planningOffThread, "native piece graph is planned away from the server tick thread");
            ServerLevel source = sourceLevel(level, room);
            check(source != null && room.sourceSeed == source.getSeed(), "saved native plan source remains available");
            var start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source), room.plan, room.sourceSeed);
            check(start != null && start.isValid() && !start.getPieces().isEmpty(), "complete native plan saved");
            firstStart = start;
            var structure = start.getStructure();
            check(room.sourceCoordinates, "source-to-storage coordinate mapping is persisted");
            BlockPos middle = start.getBoundingBox().getCenter();
            var expectedBiome = source.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                    Math.floorDiv((middle.getX() >> 2 << 2) + room.sourceOffsetX, 4),
                    Math.floorDiv((middle.getY() >> 2 << 2) + room.sourceOffsetY, 4),
                    Math.floorDiv((middle.getZ() >> 2 << 2) + room.sourceOffsetZ, 4),
                    source.getChunkSource().randomState().sampler());
            // LevelReader's unloaded-biome fallback samples the generator without loading
            // saved data. Inspect the physical chunk palette, as runtime spawning does.
            check(placed.getChunkAt(middle).getNoiseBiome(middle.getX() >> 2, middle.getY() >> 2, middle.getZ() >> 2).equals(expectedBiome),
                    "relocated room stores the source three-dimensional biome for natural spawning");
            int chests = 0;
            var b = start.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                if (!(placed.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) continue;
                check(chest.getLootTable() != null, "unopened generated chest retains loot table");
                check(pieceContains(start, pos), "machine/chest position resolves native structure identity");
                chests++;
            }
            check(chests == 1, "native outpost loot chest generated: " + chests);
            firstStructureBlocks = countStructureBlocks(placed, start);
            check(firstStructureBlocks > 0, "first native structure has blocks before adjacent generation");
            check(Arrays.stream(room.pixels).anyMatch(color -> color != 0), "actual generated block map is nonblank");
            check(room.mapCursor == 4096 && Arrays.stream(room.pixels).noneMatch(color -> color == 0),
                    "every preview pixel sampled exactly once across all chunks");
            check(room.bedrockVersion > 0, "completed instance records the bedrock foundation version");
            for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pieceContains(start, pos)) continue;
                    if (!placed.getBlockState(pos).isAir()) {
                        var hits = new java.util.ArrayList<Integer>();
                        for (int i = 0; i < start.getPieces().size(); i++)
                            if (start.getPieces().get(i).getBoundingBox().isInside(pos)) hits.add(i);
                        LogUtils.getLogger().error("CAPSULE RESIDUAL block={} pos={} phase={} pieceBoxes={} fluidNeighbors={}",
                                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlockState(pos).getBlock()),
                                pos, room.phase, hits,
                                java.util.stream.Stream.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below())
                                        .filter(neighbor -> placed.getBlockState(neighbor).getFluidState().isSource()).toList());
                        throw new AssertionError("natural terrain is absent inside the capsule structure envelope");
                    }
                }
            }
            int foundationY = b.minY() - 1;
            int foundationCells = 0;
            for (int x = b.minX(); x <= b.maxX(); x++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
                BlockPos foundation = new BlockPos(x, foundationY, z);
                if (!placed.getBlockState(foundation).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                    LogUtils.getLogger().error("CAPSULE FOUNDATION MISSING pos={} state={} bounds={} phase={} cursor={}/{} version={} trimPass={}",
                            foundation, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placed.getBlockState(foundation).getBlock()),
                            b, room.phase, room.bedrockChunk, room.bedrockCursor, room.bedrockVersion, room.trimPass);
                    throw new AssertionError("every structure footprint cell has a bedrock foundation");
                }
                foundationCells++;
            }
            check(foundationCells == b.getXSpan() * b.getZSpan(), "bedrock covers the complete XZ projection");
            CompoundTag saved = CapsuleInstances.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            check(saved.getList("rooms", 10).stream().anyMatch(e -> ((CompoundTag)e).getBoolean("ready")), "completed instance persists");
            player.setPos(12, 120, 12);
            NativeCapsules.request(player, ID, DIM, 2, 1); stage = 2; return false;
        }
        if (stage == 2) {
            if (!DimensionCapsuleService.hasReturnPoint(player)) return false;
            check(player.getX() > 1_000_000, "one click waits for entrance and teleports automatically");
            var capsule = new net.minecraft.world.item.ItemStack(com.sorrowmist.useless.stretcher.init.ModItems.DIMENSION_CAPSULE.get());
            DimensionCapsuleService.enter(player, capsule);
            check(!DimensionCapsuleService.hasReturnPoint(player) && Math.abs(player.getX() - 12) < 1,
                    "capsule returns to saved original position");
            NativeCapsules.request(player, ID, DIM, 1, 0);
            check(room.ready, "completed instance never regenerates on repeat request");
            var data = CapsuleInstances.get(level.getServer());
            var reused = data.getOrCreate(player.getUUID(), ID, DIM);
            check(reused == room && reused.instanceId == room.instanceId,
                    "same owner/structure/dimension reuses instance zero instead of allocating a duplicate");
            check(cleaningResumed, "native structure check exercised a persisted partial-cleaning restart");
            check(trimmingResumed, "native structure check resumed its actual trimming checkpoint");
            check(lateTrimPassResumed, "native structure check exercised a persisted late trimming pass");
            // Exercise the actual UI request once. Repeating the same request while the
            // response is in flight must resolve to instance 1 rather than allocate 2, 3…
            NativeCapsules.request(player, ID, DIM, 3, 0);
            NativeCapsules.request(player, ID, DIM, 3, 0);
            adjacentRoom = data.rooms.get(CapsuleInstances.key(player.getUUID(), ID, DIM, 1));
            check(adjacentRoom != null && adjacentRoom.instanceId == room.instanceId + 1 && adjacentRoom != room,
                    "explicit adjacent generation allocates one distinct next instance idempotently");
            check(adjacentRoom.sourceDimension.equals(room.sourceDimension)
                            && adjacentRoom.sourceSeed == room.sourceSeed,
                    "adjacent instance inherits the completed source environment contract");
            check(data.instanceCount(player.getUUID(), ID, DIM) == 2,
                    "repeated adjacent requests do not allocate duplicate rooms");
            check(!CapsuleInstances.boundsOverlap(room, adjacentRoom) && CapsuleInstances.adjacent(room, adjacentRoom),
                    "new instance reserves a non-overlapping room beside the completed structure envelope");
            check(adjacentRoom.plan.isEmpty() && !adjacentRoom.ready,
                    "adjacent instance starts ungenerated and does not mutate the completed room");
            stage = 5;
            return false;
        }
        if (stage == 5) {
            check(adjacentRoom.progress >= previousAdjacentProgress && adjacentRoom.progress <= 99,
                    "adjacent instance reports independent monotonic progress");
            previousAdjacentProgress = adjacentRoom.progress;
            if (!adjacentRoom.error.isEmpty()) throw new AssertionError("Adjacent native capsule failed: " + adjacentRoom.error);
            if (!adjacentRoom.ready) return false;
            check(CapsuleInstances.adjacent(room, adjacentRoom)
                            && !CapsuleInstances.boundsOverlap(room, adjacentRoom),
                    "second native structure generates completely inside the adjacent reserved room");
            ServerLevel placed = roomLevel(level, adjacentRoom.dimension);
            ServerLevel source = sourceLevel(level, adjacentRoom);
            check(source != null && source.getSeed() == adjacentRoom.sourceSeed,
                    "adjacent source environment remains available after generation");
            var secondStart = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source),
                    adjacentRoom.plan, adjacentRoom.sourceSeed);
            check(secondStart != null && secondStart.isValid() && secondStart.getPieces().size() > 1,
                    "adjacent outpost also preserves the complete native jigsaw piece graph");
            check(countStructureBlocks(placed, firstStart) == firstStructureBlocks,
                    "cleaning and generating the adjacent instance preserves every block of the original instance");
            LogUtils.getLogger().info("CAPSULE NATIVE CHECKS PASSED: native outpost reuses a stable instance, explicit second outpost generates beside it, matching dimension/biome, native loot table, structure identity, complete real map, resumable cleanup, bedrock and neighbor preservation; {} ticks", ticks);
            stage = 6;
            return true;
        }
        return false;
    }
    private static final String DIM_OVERWORLD = "minecraft:overworld";
    private static final String DIM_NETHER = "minecraft:the_nether";
    private static void assertDimension(ServerPlayer player, String id, String required, String forbidden) {
        var structure = player.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(id));
        check(structure != null, "registered structure exists: " + id);
        var dimensions = DimensionCapsuleCatalog.dimensions(player, structure);
        check(dimensions.contains(required), id + " follows active structure-set dimension " + required + ": " + dimensions);
        check(!dimensions.contains(forbidden), id + " is not offered in incompatible dimension " + forbidden + ": " + dimensions);
    }
    private static int countStructureBlocks(ServerLevel level, StructureStart start) {
        int count = 0;
        var b = start.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
            if (!level.getBlockState(pos).isAir() && pieceContains(start, pos)) count++;
        return count;
    }
    private static boolean pieceContains(StructureStart start, BlockPos pos) {
        return start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(pos));
    }
    private static ServerLevel roomLevel(ServerLevel fallback, String dimension) {
        return fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                Registries.DIMENSION, new ResourceLocation(dimension)));
    }
    private static ServerLevel sourceLevel(ServerLevel fallback, CapsuleInstances.Instance room) {
        return fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                Registries.DIMENSION, new ResourceLocation(room.sourceDimension)));
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
