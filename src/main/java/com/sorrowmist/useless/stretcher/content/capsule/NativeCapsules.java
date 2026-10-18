package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native callbacks retain loot initialization, processors, jigsaw assembly and structure spawn rules. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class NativeCapsules {
    /**
     * Native structure generation is deliberately serialized and checkpointed. These limits
     * bound the amount of world that one capsule may reserve while still allowing very large
     * mod structures to finish over many server ticks instead of monopolising a tick.
     */
    private static final int MAX_STRUCTURE_AXIS = 8_192;
    private static final long MAX_STRUCTURE_XZ_AREA = 67_108_864L;
    private static final int MAX_STRUCTURE_CHUNKS = 32_768;
    private static final int MAX_STRUCTURE_PIECES = 250_000;
    private static final long MAX_STRUCTURE_VOLUME = 268_435_456L;
    private static final int MAX_GENERATION_TICKS = 1_000_000;
    private static final int FLUID_SETTLE_PASSES = 8;
    /**
     * Structure.findGenerationPoint is not incremental.  It therefore runs on one daemon
     * planner, never on the server thread.  A slow or pathological generator is abandoned
     * after this bound; the worker is still allowed to finish before another plan starts.
     */
    private static final long PLAN_TIMEOUT_NANOS = 300_000_000_000L;
    private static final long STEP_BUDGET_NANOS = 4_000_000L;
    private static final long PREVIEW_BUDGET_NANOS = 1_500_000L;
    private static final long CALLBACK_WARN_NANOS = 50_000_000L;
    /** Finalization v4 includes fluid-stable repeated passes across the reserved isolation margin. */
    private static final int FINALIZATION_VERSION = 4;
    /** Capsule room cleanup changed from chunk-aligned spill into neighboring instances. */
    private static final int CLEANUP_VERSION = 2;
    private static final AtomicBoolean PLANNER_BUSY = new AtomicBoolean();
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "useless-stretcher-capsule-planner");
        thread.setDaemon(true);
        return thread;
    });
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static final ArrayDeque<CapsuleInstances.Instance> RESUMING = new ArrayDeque<>();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("useless_stretcher_native_capsule",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final TicketType<ChunkPos> START_TICKET = TicketType.create("useless_stretcher_capsule_start",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final TicketType<ChunkPos> ENTRY_TICKET = TicketType.create("useless_stretcher_capsule_entry",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<UUID, PendingEntry> ENTRIES = new LinkedHashMap<>();
    private NativeCapsules() { }

    public static void request(ServerPlayer player, String id, String dimension, int action, int side) {
        request(player, id, dimension, action, side, 0);
    }
    public static void request(ServerPlayer player, String id, String dimension, int action, int side, int instanceId) {
        Structure structure = player.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));
        if (structure == null) return;
        ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
        if (level == null) return;
        CapsuleInstances data = CapsuleInstances.get(player.getServer());
        CapsuleInstances.Instance existing = data.rooms.get(CapsuleInstances.key(player.getUUID(), id, dimension, instanceId));
        boolean knownCapsuleDimension = CapsuleDimensions.isCapsule(level.dimension().location());
        if (!knownCapsuleDimension || (action == 1 || existing == null)
                && !DimensionCapsuleCatalog.dimensions(player, structure).contains(dimension)) return;
        CapsuleInstances.Instance room;
        if (action == 3) {
            CapsuleInstances.Instance selected = data.rooms.get(
                    CapsuleInstances.key(player.getUUID(), id, dimension, instanceId));
            if (selected == null || !selected.ready) return;

            // Resolve/migrate the selected room before allocating its neighbour.  This
            // prevents an adjacent request from inheriting an empty source contract.
            ServerLevel selectedSource = resolveSource(player, structure, dimension, selected);
            if (selectedSource == null) {
                selected.error = selected.sourceDimension.isBlank()
                        ? "source_dimension_missing" : "source_environment_unavailable";
                data.setDirty();
                sendState(player, selected, null);
                return;
            }
            room = data.getOrCreateAdjacent(player.getUUID(), id, dimension, instanceId);
            data.inheritSource(room, selected);
            action = 1;
        } else {
            room = data.rooms.get(CapsuleInstances.key(player.getUUID(), id, dimension, instanceId));
            if (room == null && action == 1 && instanceId == 0) room = data.getOrCreate(player.getUUID(), id, dimension);
        }
        String key = room == null ? CapsuleInstances.key(player.getUUID(), id, dimension) : room.key();
        if (room == null) {
            CapsuleNetwork.state(player, id, dimension, instanceId, data.instanceCount(player.getUUID(), id, dimension),
                    0, 0, "empty", 0, new int[0], 0, 0, 0);
            return;
        }
        ServerLevel sourceLevel = resolveSource(player, structure, dimension, room);
        if (action == 1) {
            if (sourceLevel == null) {
                room.error = room.sourceDimension.isBlank()
                        ? "source_dimension_missing" : "source_environment_unavailable";
                data.setDirty();
                sendState(player, room, null);
                return;
            }
            if (room.error.equals("source_dimension_missing") || room.error.equals("source_environment_unavailable")) {
                room.error = "";
                data.setDirty();
            }
        }
        if (action == 1 && !room.error.isEmpty()) {
            room.error = "";
            if (room.plan.isEmpty()) room.attempt = 0;
            data.setDirty();
        }
        Job current = null;
        for (Job job : JOBS) if (job.room.key().equals(key)) current = job;
        if (action == 1 && current == null && (!room.ready || room.bedrockVersion < FINALIZATION_VERSION) && room.error.isEmpty()) {
            if (JOBS.size() >= 4 || JOBS.stream().anyMatch(job -> job.room.owner.equals(player.getUUID()))) {
                CapsuleNetwork.state(player, id, dimension, "queue_full", 0, new int[0], 0, 0, 0); return;
            }
            current = new Job(data, room, level, sourceLevel, structure);
            room.generating = true;
            room.completionNotified = false;
            data.setDirty();
            JOBS.add(current);
        }
        if (action == 2 && room.ready && !ENTRIES.containsKey(player.getUUID())
                && !DimensionCapsuleService.hasReturnPoint(player)) {
            PendingEntry pending = new PendingEntry(player, room, level, side);
            ENTRIES.put(player.getUUID(), pending);
        }
        sendState(player, room, current);
    }

    /**
     * Return the persisted source level, or bind a legacy room to the currently selected
     * compatible source exactly once.  A saved source is never replaced by another
     * dimension merely because the server's dimension iteration order changed.
     */
    static ServerLevel resolveSource(ServerPlayer player, Structure structure, String target,
                                     CapsuleInstances.Instance room) {
        if (!room.sourceDimension.isBlank()) {
            ServerLevel source = DimensionCapsuleCatalog.sourceLevel(player, structure, target, room.sourceDimension);
            return source != null && source.getSeed() == room.sourceSeed ? source : null;
        }
        ServerLevel source = DimensionCapsuleCatalog.sourceLevel(player, structure, target);
        if (source != null) CapsuleInstances.get(player.getServer()).bindSource(room, source);
        return source;
    }

    private static void sendState(ServerPlayer player, CapsuleInstances.Instance room, Job job) {
        int sx = room.plan.isEmpty() ? 0 : room.maxX - room.minX + 1;
        int sy = room.plan.isEmpty() ? 0 : room.maxY - room.minY + 1;
        int sz = room.plan.isEmpty() ? 0 : room.maxZ - room.minZ + 1;
        String state = !room.error.isEmpty() ? room.error : room.ready ? "ready" : job == null ? "paused" : room.phase;
        if (job != null) room.progress = Math.max(room.progress, job.progress());
        int progress = room.ready ? 100 : room.progress;
        CapsuleNetwork.state(player, room.structure, room.dimension, room.instanceId,
                job == null ? CapsuleInstances.get(player.getServer()).instanceCount(room.owner, room.structure, room.dimension)
                        : job.data.instanceCount(room.owner, room.structure, room.dimension),
                room.x(), room.z(), state, progress, room.pixels, sx, sy, sz);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        ENTRIES.values().removeIf(PendingEntry::tick);
        resumeNext(event.getServer());
        Job job = JOBS.poll();
        if (job == null) return;
        try {
            long began = System.nanoTime();
            boolean done = job.step();
            job.room.progress = Math.max(job.room.progress, job.progress());
            long elapsed = System.nanoTime() - began;
            job.maxStep = Math.max(job.maxStep, elapsed);
            if (!job.room.phase.equals(job.loggedPhase) || job.ticks % 200 == 0) {
                job.loggedPhase = job.room.phase;
                LogUtils.getLogger().info("CAPSULE PROGRESS {} target={} phase={} progress={}% chunks={}/{} attempt={}",
                        job.room.structure, job.room.dimension, job.room.phase, job.room.progress,
                        job.room.chunk, job.chunkCount, job.room.attempt);
            }
            if (!done) JOBS.add(job);
            else {
                job.release();
                job.room.ready = job.room.phase.equals("ready");
                job.room.generating = false;
                job.data.setDirty();
                if (job.room.ready) notifyReady(job.data, job.room);
                if (!job.room.ready) LogUtils.getLogger().error(
                        "Native capsule job ended before finalization completed: structure={} phase={} trim={}/{} bedrock={}/{}",
                        job.room.structure, job.room.phase, job.room.trimChunk, job.chunkCount,
                        job.room.bedrockChunk, job.chunkCount);
                LogUtils.getLogger().info("CAPSULE NATIVE COMPLETE {}: {} chunks, {} pieces, max callback {} ms",
                        job.room.structure, job.chunkCount, job.start.getPieces().size(), job.maxStep / 1_000_000.0);
            }
        } catch (Exception failure) {
            job.release(); job.room.error = "failed"; job.data.setDirty();
            job.room.generating = false;
            if (failure instanceof CapsuleLimitException limit) job.room.error = limit.code;
            LogUtils.getLogger().error("Native capsule {} failed; partial instance retained", job.room.key(), failure);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        while (!JOBS.isEmpty()) JOBS.remove().release();
        RESUMING.clear();
        ENTRIES.values().forEach(PendingEntry::release); ENTRIES.clear();
    }

    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        RESUMING.clear();
        for (var room : CapsuleInstances.get(event.getServer()).rooms.values())
            if (room.generating && !room.ready && room.error.isEmpty()) RESUMING.add(room);
    }

    /** Reload only accepted jobs, retaining their plan and cursors; never regenerate finished rooms. */
    private static void resumeNext(net.minecraft.server.MinecraftServer server) {
        if (RESUMING.isEmpty() || JOBS.size() >= 4) return;
        var room = RESUMING.remove();
        if (!room.generating || room.ready || !room.error.isEmpty()
                || JOBS.stream().anyMatch(job -> job.room.key().equals(room.key()))) return;
        if (JOBS.stream().anyMatch(job -> job.room.owner.equals(room.owner))) {
            RESUMING.addLast(room); return;
        }
        var target = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(room.dimension)));
        var source = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(room.sourceDimension)));
        var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(room.structure));
        var data = CapsuleInstances.get(server);
        try {
            if (target == null || source == null || source.getSeed() != room.sourceSeed || structure == null)
                throw new CapsuleLimitException("source_environment_unavailable");
            JOBS.addLast(new Job(data, room, target, source, structure));
            LogUtils.getLogger().info("CAPSULE BACKGROUND RESUMED {} phase={} chunk={} piece={}", room.key(), room.phase, room.chunk, room.piece);
        } catch (RuntimeException failure) {
            room.generating = false;
            room.error = failure instanceof CapsuleLimitException limit ? limit.code : "failed";
            data.setDirty();
            LogUtils.getLogger().error("Cannot resume capsule {}", room.key(), failure);
        }
    }

    private static void notifyReady(CapsuleInstances data, CapsuleInstances.Instance room) {
        if (room.completionNotified || !room.ready) return;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        var player = server == null ? null : server.getPlayerList().getPlayer(room.owner);
        if (player == null) return; // Persist pending notification for the next login.
        notifyReady(data, room, player);
    }

    static void notifyReady(CapsuleInstances data, CapsuleInstances.Instance room, ServerPlayer player) {
        if (!room.ready || room.completionNotified || !room.owner.equals(player.getUUID())) return;
        room.completionNotified = true;
        data.setDirty();
        CapsuleNetwork.complete(player, room.structure, room.dimension, room.instanceId);
        sendState(player, room, null);
    }

    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var data = CapsuleInstances.get(player.getServer());
        for (var room : data.rooms.values()) if (room.owner.equals(player.getUUID())) notifyReady(data, room, player);
    }

    private static final class Job {
        final CapsuleInstances data;
        final CapsuleInstances.Instance room;
        final ServerLevel level;
        /** Native generation context. The resulting Piece graph is placed in the isolated level. */
        final ServerLevel sourceLevel;
        final Structure structure;
        StructureStart start;
        BoundingBox bounds;
        int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
        int cleanMinChunkX, cleanMaxChunkX, cleanMinChunkZ, cleanMaxChunkZ;
        int trimMinChunkX, trimMaxChunkX, trimMinChunkZ, trimMaxChunkZ;
        long chunkCount;
        final Map<Long, List<Integer>> mapSamples = new LinkedHashMap<>();
        final List<ChunkPos> tickets = new ArrayList<>();
        int ticketRadius = 2;
        boolean registered;
        boolean startTicket;
        int ticks;
        String loggedPhase = "";
        long maxStep;
        PlanFuture planning;
        long planningStarted;
        long planningMemoryBaseline;
        List<StructurePlacement> placements;
        ChunkPos biomeAnchor;
        ChunkPos plannedAnchor;
        Job(CapsuleInstances data, CapsuleInstances.Instance room, ServerLevel level,
            ServerLevel sourceLevel, Structure structure) {
            this.data = data; this.room = room; this.level = level; this.sourceLevel = sourceLevel;
            this.structure = structure;
            if (!room.plan.isEmpty()) {
                start = StructureStart.loadStaticStart(serializationContext(), room.plan, generationSeed());
                if (start == null || !start.isValid()) throw new IllegalStateException("Saved structure plan cannot be restored");
                // Migrate the previous in-progress format, which encoded afterPlace in the piece index.
                if (room.piece > start.getPieces().size()) room.afterPlaced = true;
                // Old plans may have been serialized at the world's minimum Y before the
                // foundation phase existed. Only an untouched plan may be moved: once terrain
                // cleaning or piece placement has started, moving the plan would leave blocks
                // from the old projection behind and could duplicate a partial structure.
                boolean movedForBedrock = false;
                if (!room.ready && room.phase.equals("planning") && room.chunk == 0 && room.piece == 0
                        && room.cleanChunk == 0 && room.cleanCursor == 0 && room.bedrockVersion < FINALIZATION_VERSION) {
                    movedForBedrock = ensureBedrockSpace();
                }
                if (!room.ready && (room.phase.equals("bedrock") || room.phase.equals("trimming"))
                        && room.bedrockVersion < FINALIZATION_VERSION) {
                    // Older interrupted jobs may have reached foundation before the
                    // post-piece cleanup phase existed. Resume by trimming, then reseal.
                    room.phase = "trimming";
                    room.trimChunk = 0;
                    room.trimCursor = 0;
                    room.trimPass = 0;
                    room.bedrockChunk = 0;
                    room.bedrockCursor = 0;
                    data.setDirty();
                }
                describe();
                if (movedForBedrock) checkpoint();
                // Older completed rooms did not record a full bedrock projection. Re-run only
                // that bounded phase after loading; the native Piece plan is never regenerated.
                if (room.ready && room.bedrockVersion < FINALIZATION_VERSION) {
                    room.ready = false;
                    room.phase = "trimming";
                    room.trimChunk = 0;
                    room.trimCursor = 0;
                    room.trimPass = 0;
                    room.terrainCleaned = true;
                    data.setDirty();
                }
                if (!room.ready && room.phase.equals("cleaning") && room.cleanupVersion < CLEANUP_VERSION) {
                    // Upgrade interrupted cleanup before native placement. The exact
                    // structure plan is retained; only the expanded safe room is cleared again.
                    room.cleanChunk = 0;
                    room.cleanCursor = 0;
                    room.cleanupEntitiesRemoved = false;
                    data.setDirty();
                }
                if (!room.terrainCleaned && !room.ready && !room.phase.equals("cleaning")) {
                    room.phase = "cleaning";
                    room.cleanChunk = 0; room.cleanCursor = 0; room.cleanupEntitiesRemoved = false;
                    room.chunk = 0; room.piece = 0; room.afterPlaced = false;
                    room.mapCursor = 0; room.previewCursor = 0;
                    room.bedrockChunk = 0; room.bedrockCursor = 0;
                    java.util.Arrays.fill(room.pixels, 0);
                    data.setDirty();
                }
            }
        }
        int progress() {
            if (start == null) return 0; // Planning has no measurable completion fraction.
            if (room.phase.equals("cleaning")) {
                return percent(0, 25, room.cleanChunk, cleanChunkCount());
            }
            if (room.phase.equals("bedrock")) {
                return percent(65, 5, room.bedrockChunk, chunkCount);
            }
            if (room.phase.equals("trimming")) {
                long trimChunks = ((long) trimMaxChunkX - trimMinChunkX + 1)
                        * ((long) trimMaxChunkZ - trimMinChunkZ + 1);
                return percent(70, 29, room.trimPass * trimChunks + room.trimChunk,
                        trimPasses() * trimChunks);
            }
            double pieceFraction = start.getPieces().isEmpty() ? 0
                    : Math.min(1D, (double) room.piece / start.getPieces().size());
            return percent(25, 40, room.chunk + pieceFraction, chunkCount);
        }
        private static int percent(int base, int weight, double done, long total) {
            return base + (int) (weight * Math.clamp(done / Math.max(1L, total), 0D, 1D));
        }
        void describe() {
            bounds = placementEnvelope();
            long xSpan = bounds.getXSpan();
            long ySpan = bounds.getYSpan();
            long zSpan = bounds.getZSpan();
            long xzArea = xSpan * zSpan;
            if (xSpan <= 0 || ySpan <= 0 || zSpan <= 0
                    || xSpan > MAX_STRUCTURE_AXIS || zSpan > MAX_STRUCTURE_AXIS
                    || ySpan > level.getHeight() || xzArea > MAX_STRUCTURE_XZ_AREA
                    || start.getPieces().size() > MAX_STRUCTURE_PIECES
                    || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                    || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())))
                throw new CapsuleLimitException("too_large");
            long volume = xSpan * ySpan * zSpan;
            if (volume <= 0 || volume > MAX_STRUCTURE_VOLUME) throw new CapsuleLimitException("too_large");
            if (bounds.minY() < level.getMinBuildHeight()) throw new CapsuleLimitException("below_world");
            room.minX = bounds.minX(); room.minY = bounds.minY(); room.minZ = bounds.minZ();
            room.maxX = bounds.maxX(); room.maxY = bounds.maxY(); room.maxZ = bounds.maxZ();
            minChunkX = bounds.minX() >> 4;
            maxChunkX = bounds.maxX() >> 4;
            minChunkZ = bounds.minZ() >> 4;
            maxChunkZ = bounds.maxZ() >> 4;
            // Keep the terrain-free margin inside the allocator's clearance. Chunk-aligned
            // +/-1 cleanup used to erase a previously generated neighboring structure.
            cleanMinChunkX = (bounds.minX() - CapsuleInstances.reservedMargin()) >> 4;
            cleanMaxChunkX = (bounds.maxX() + CapsuleInstances.reservedMargin()) >> 4;
            cleanMinChunkZ = (bounds.minZ() - CapsuleInstances.reservedMargin()) >> 4;
            cleanMaxChunkZ = (bounds.maxZ() + CapsuleInstances.reservedMargin()) >> 4;
            trimMinChunkX = (bounds.minX() - CapsuleInstances.reservedMargin()) >> 4;
            trimMaxChunkX = (bounds.maxX() + CapsuleInstances.reservedMargin()) >> 4;
            trimMinChunkZ = (bounds.minZ() - CapsuleInstances.reservedMargin()) >> 4;
            trimMaxChunkZ = (bounds.maxZ() + CapsuleInstances.reservedMargin()) >> 4;
            room.boundsKnown = true;
            long width = (long) maxChunkX - minChunkX + 1L;
            long depth = (long) maxChunkZ - minChunkZ + 1L;
            chunkCount = width * depth;
            if (chunkCount <= 0 || chunkCount > MAX_STRUCTURE_CHUNKS) throw new CapsuleLimitException("too_large");
            mapSamples.clear();
            for (int i = 0; i < 4096; i++) {
                int x = sampleX(i), z = sampleZ(i);
                mapSamples.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), ignored -> new ArrayList<>()).add(i);
            }
        }
        boolean plan() {
            if (planning == null) {
                // The same entry point as /place structure: the mod creates its complete piece graph.
                // The complete graph can be enormous for modded structures, so never build it in a
                // ServerTickEvent callback.  A single planner also prevents several large requests
                // from competing for memory or running generation callbacks concurrently.
                if (PLANNER_BUSY.get()) return false;
                var generator = sourceLevel.getChunkSource().getGenerator();
                int offset = room.attempt++;
                if (offset >= 1024) throw new IllegalStateException("No matching biome/generation point in the reserved region");
                if (placements == null) {
                    var holder = sourceLevel.registryAccess().registryOrThrow(Registries.STRUCTURE)
                            .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(room.structure)));
                    placements = List.copyOf(sourceLevel.getChunkSource().getGeneratorState().getPlacementsForStructure(holder));
                }
                if (!PLANNER_BUSY.compareAndSet(false, true)) return false;
                planningStarted = System.nanoTime();
                planningMemoryBaseline = usedMemory();
                try {
                    planning = new PlanFuture(() -> {
                        room.planningOffThread = true;
                        try {
                            // Follow the live source placement and biome predicates without
                            // generating ordinary source-world chunks just to search for a site.
                            ChunkPos anchor = sourceAnchor(offset);
                            if (anchor == null) return StructureStart.INVALID_START;
                            plannedAnchor = anchor;
                            StructureStart candidate = structure.generate(sourceLevel.registryAccess(), generator, generator.getBiomeSource(),
                                    sourceLevel.getChunkSource().randomState(), sourceLevel.getStructureManager(), generationSeed(), anchor,
                                    0, sourceLevel, structure.biomes()::contains);
                            return candidate != null && candidate.isValid() ? anchorScatteredPieces(candidate) : candidate;
                        } finally {
                            PLANNER_BUSY.set(false);
                        }
                    });
                    PLANNER.execute(planning);
                } catch (RuntimeException failure) {
                    PLANNER_BUSY.set(false);
                    planning = null;
                    throw failure;
                }
                data.setDirty();
                return false;
            }
            if (!planning.isDone()) {
                long elapsed = System.nanoTime() - planningStarted;
                long memoryGrowth = Math.max(0L, usedMemory() - planningMemoryBaseline);
                long memoryLimit = planningMemoryGrowthLimit();
                if (memoryGrowth > memoryLimit) {
                    LogUtils.getLogger().error("Native capsule planning grew heap usage by {} MiB for {}; request rejected",
                            memoryGrowth / (1024L * 1024L), room.structure);
                    planning.cancel(true);
                    planning = null;
                    throw new CapsuleLimitException("planning_memory");
                }
                if (elapsed > PLAN_TIMEOUT_NANOS) {
                    LogUtils.getLogger().error("Native capsule planning exceeded {} seconds for {}; request rejected without blocking the server",
                            PLAN_TIMEOUT_NANOS / 1_000_000_000L, room.structure);
                    planning.cancel(true);
                    planning = null;
                    throw new CapsuleLimitException("planning_timeout");
                }
                return false;
            }
            try {
                start = planning.get();
            } catch (CancellationException failure) {
                throw new CapsuleLimitException("planning_timeout");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Native capsule planning interrupted", failure);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Native capsule planning failed", cause);
            } finally {
                planning = null;
            }
            if (start == null || !start.isValid()) { start = null; return false; }
            // Planning coordinates obey the real world's placement/biome rules. Physical
            // storage is independent: translate the completed graph into this reserved room.
            int dxToRoom = (room.x() >> 4 << 4) - start.getChunkPos().getMinBlockX();
            int dzToRoom = (room.z() >> 4 << 4) - start.getChunkPos().getMinBlockZ();
            room.sourceOffsetX = -dxToRoom;
            room.sourceOffsetZ = -dzToRoom;
            room.sourceOffsetY = 0;
            room.sourceCoordinates = true;
            room.sourceBiome = sourceLevel.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                    plannedAnchor.getMiddleBlockX() >> 2, start.getBoundingBox().getCenter().getY() >> 2,
                    plannedAnchor.getMiddleBlockZ() >> 2, sourceLevel.getChunkSource().randomState().sampler())
                    .unwrapKey().map(key -> key.location().toString()).orElse("");
            movePlan(dxToRoom, 0, dzToRoom);
            ensureBedrockSpace();
            describe();
            if (bounds.minX() < room.x() - MAX_STRUCTURE_AXIS || bounds.maxX() >= room.x() + MAX_STRUCTURE_AXIS
                    || bounds.minZ() < room.z() - MAX_STRUCTURE_AXIS || bounds.maxZ() >= room.z() + MAX_STRUCTURE_AXIS) {
                start = null; return false;
            }
            // Region files are created by ordinary terrain generation and cannot be used as
            // an occupancy test.  Only registered capsule bounds participate in collision checks.
            if (data.needsRelocation(room)) {
                if (++room.relocations > 8) throw new CapsuleLimitException("allocation_conflict");
                data.relocateAdjacent(room);
                room.boundsKnown = false;
                room.plan = new CompoundTag();
                room.phase = "planning";
                room.terrainCleaned = false;
                room.cleanupEntitiesRemoved = false;
                room.attempt = 0;
                start = null;
                bounds = null;
                data.setDirty();
                return false;
            }
            int[] adjacentShift = data.snapBesideExisting(room);
            if (adjacentShift[0] != 0 || adjacentShift[1] != 0) {
                // A generated native plan is already fully assembled, so shifting each
                // serialized Piece preserves all mod processors, variants and jigsaw output.
                // Regenerate the wrapper to refresh StructureStart's cached aggregate bounds.
                int dx = adjacentShift[0], dz = adjacentShift[1];
                movePlan(dx, 0, dz);
                room.sourceOffsetX -= dx;
                room.sourceOffsetZ -= dz;
                if (start == null || !start.isValid()) throw new IllegalStateException("Could not shift adjacent native structure plan");
                describe();
            }
            room.phase = "cleaning";
            room.terrainCleaned = false;
            room.cleanupEntitiesRemoved = false;
            room.cleanCursor = 0;
            room.cleanChunk = 0;
            room.bedrockCursor = 0;
            room.bedrockChunk = 0;
            checkpoint(); return false;
        }

        private ChunkPos sourceAnchor(int attempt) {
            if (biomeAnchor == null) biomeAnchor = findBiomeAnchor();
            int placementCount = Math.max(1, placements.size());
            int[] spiral = spiralOffset(attempt / placementCount);
            if (placements.isEmpty()) return new ChunkPos(biomeAnchor.x + spiral[0] * 16, biomeAnchor.z + spiral[1] * 16);
            StructurePlacement placement = placements.get(attempt % placementCount);
            var state = sourceLevel.getChunkSource().getGeneratorState();
            ChunkPos candidate;
            if (placement instanceof RandomSpreadStructurePlacement random) {
                int spacing = random.spacing();
                candidate = random.getPotentialStructureChunk(generationSeed(),
                        (Math.floorDiv(biomeAnchor.x, spacing) + spiral[0]) * spacing,
                        (Math.floorDiv(biomeAnchor.z, spacing) + spiral[1]) * spacing);
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                var positions = state.getRingPositionsFor(rings);
                int index = attempt / placementCount;
                if (positions == null || index >= positions.size()) return null;
                candidate = positions.get(index);
            } else {
                candidate = new ChunkPos(biomeAnchor.x + spiral[0], biomeAnchor.z + spiral[1]);
            }
            return placement.isStructureChunk(state, candidate.x, candidate.z) ? candidate : null;
        }

        private ChunkPos findBiomeAnchor() {
            int[] spiral = spiralOffset(room.instanceId);
            int fallbackX = room.x() + spiral[0] * 16;
            int fallbackZ = room.z() + spiral[1] * 16;
            try {
                var biomeSource = sourceLevel.getChunkSource().getGenerator().getBiomeSource();
                var sampler = sourceLevel.getChunkSource().randomState().sampler();
                var nearest = biomeSource.findClosestBiome3d(
                        new BlockPos(fallbackX, sourceLevel.getSeaLevel(), fallbackZ),
                        4_096, 64, 64, structure.biomes()::contains, sampler, sourceLevel);
                if (nearest != null) {
                    BlockPos point = nearest.getFirst();
                    return new ChunkPos(point.getX() >> 4, point.getZ() >> 4);
                }
            } catch (RuntimeException failure) {
                LogUtils.getLogger().debug("Source biome lookup failed for {}; using bounded native spiral", room.structure, failure);
            }
            return new ChunkPos(fallbackX >> 4, fallbackZ >> 4);
        }

        private void movePlan(int dx, int dy, int dz) {
            ChunkPos original = start.getChunkPos();
            for (var piece : start.getPieces()) piece.move(dx, dy, dz);
            start = new StructureStart(structure,
                    new ChunkPos((original.getMinBlockX() + dx) >> 4, (original.getMinBlockZ() + dz) >> 4),
                    start.getReferences(), new PiecesContainer(start.getPieces()));
        }

        /** Ground-dependent pieces must resolve against their source before entering void storage. */
        private StructureStart anchorScatteredPieces(StructureStart candidate) {
            if (candidate.getPieces().stream().noneMatch(piece ->
                    piece instanceof net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece)) return candidate;
            boolean changed = false;
            CompoundTag saved = candidate.createTag(serializationContext(), candidate.getChunkPos());
            var children = saved.getList("Children", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int index = 0; index < candidate.getPieces().size(); index++) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                var piece = candidate.getPieces().get(index);
                if (!(piece instanceof net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece)
                        || children.getCompound(index).getInt("HPos") >= 0) continue;
                BoundingBox box = piece.getBoundingBox();
                boolean lowest = piece instanceof net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
                int ground = lowest ? Integer.MAX_VALUE : 0, samples = 0;
                for (int x = box.minX(); x <= box.maxX(); x++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                    int height = sourceLevel.getChunkSource().getGenerator().getBaseHeight(x, z,
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            sourceLevel, sourceLevel.getChunkSource().randomState());
                    if (lowest) ground = Math.min(ground, height); else ground += height;
                    samples++;
                }
                if (!lowest) ground /= Math.max(1, samples);
                int burial = lowest ? RandomSource.create(generationSeed() ^ box.getCenter().asLong()).nextInt(3) : 0;
                piece.move(0, ground - burial - box.minY(), 0);
                CompoundTag child = piece.createTag(serializationContext());
                child.putInt("HPos", ground);
                child.putBoolean("useless_stretcher_ground_anchor", true);
                children.set(index, child);
                changed = true;
            }
            if (changed) {
                candidate = StructureStart.loadStaticStart(serializationContext(), saved, generationSeed());
                if (candidate == null || !candidate.isValid()) throw new IllegalStateException("Ground-anchored native plan could not be restored");
            }
            return candidate;
        }

        /** Scattered pieces place basements below the coarse vanilla Piece box. */
        private BoundingBox placementEnvelope() {
            BoundingBox envelope = start.getBoundingBox();
            int minY = envelope.minY();
            for (var piece : start.getPieces()) {
                if (piece instanceof net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece)
                    minY = Math.min(minY, piece.getBoundingBox().minY() - 16);
            }
            return new BoundingBox(envelope.minX(), minY, envelope.minZ(), envelope.maxX(), envelope.maxY(), envelope.maxZ());
        }

        /** Keep the complete native envelope above the bedrock support layer. */
        private boolean ensureBedrockSpace() {
            if (start.getPieces().isEmpty()) throw new CapsuleLimitException("too_large");
            // Native terrain adaptation can expand the Piece bounds (vanilla uses 12 blocks).
            // Calculate a fresh adjusted envelope rather than reusing StructureStart's cache.
            BoundingBox envelope = placementEnvelope();
            long lift = Math.max(0L, (long) level.getMinBuildHeight() + 1 - envelope.minY());
            if (lift > Integer.MAX_VALUE || (long) envelope.maxY() + lift >= level.getMaxBuildHeight())
                throw new CapsuleLimitException("too_large");
            if (lift == 0) return false;
            int delta = (int) lift;
            for (var piece : start.getPieces()) piece.move(0, delta, 0);
            start = new StructureStart(start.getStructure(), start.getChunkPos(), start.getReferences(),
                    new PiecesContainer(start.getPieces()));
            if (room.sourceCoordinates) room.sourceOffsetY -= delta;
            return true;
        }
        boolean ready(ChunkPos center) {
            return ready(center, true);
        }
        boolean ready(ChunkPos center, boolean neighbors) {
            int radius = neighbors ? 2 : 0;
            if (tickets.isEmpty() || !tickets.getFirst().equals(center) || ticketRadius != radius) {
                releaseCurrent(); tickets.add(center);
                ticketRadius = radius;
                level.getChunkSource().addRegionTicket(TICKET, center, radius, center);
            }
            // Never join a generation future or synchronously request an unloaded chunk.
            int reach = neighbors ? 1 : 0;
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++)
                if (level.getChunkSource().getChunkNow(center.x + dx, center.z + dz) == null) return false;
            return level.areEntitiesLoaded(center.toLong());
        }
        boolean step() {
            if (++ticks > MAX_GENERATION_TICKS) throw new CapsuleLimitException("timeout");
            long deadline = System.nanoTime() + STEP_BUDGET_NANOS;
            if (start == null) return plan();
            if (room.phase.equals("cleaning")) return clean(deadline);
            if (room.phase.equals("trimming")) return trim(deadline);
            if (room.phase.equals("bedrock")) return bedrock(deadline);
            if (!registered) {
                if (!startTicket) {
                    level.getChunkSource().addRegionTicket(START_TICKET, start.getChunkPos(), 0, start.getChunkPos());
                    startTicket = true;
                }
                if (!ready(start.getChunkPos())) return false;
                level.getChunk(start.getChunkPos().x, start.getChunkPos().z).setStartForStructure(structure, start);
                refreshPoiSections(start.getBoundingBox());
                registered = true; room.phase = "placing"; data.setDirty(); return false;
            }
            if ((long) room.chunk < chunkCount) {
                ChunkPos chunk = chunkAt(room.chunk);
                if (!ready(chunk)) return false;
                var access = level.getChunk(chunk.x, chunk.z);
                if (room.piece == 0 && !room.afterPlaced && room.sourceCoordinates) {
                    // Store the source's 3-D biomes alongside the relocated graph. Runtime
                    // biome spawn lists and structure overrides now see the actual environment.
                    var biomeSource = sourceLevel.getChunkSource().getGenerator().getBiomeSource();
                    var sampler = sourceLevel.getChunkSource().randomState().sampler();
                    access.fillBiomesFromNoise((x, y, z, ignored) -> biomeSource.getNoiseBiome(
                            Math.floorDiv((x << 2) + room.sourceOffsetX, 4),
                            Math.floorDiv((y << 2) + room.sourceOffsetY, 4),
                            Math.floorDiv((z << 2) + room.sourceOffsetZ, 4), sampler), sampler);
                    access.setUnsaved(true);
                    level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(access));
                }
                access.addReferenceForStructure(structure, start.getChunkPos().toLong());
                refreshPoiSections(new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ()));
                BoundingBox clip = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                var pieces = start.getPieces();
                while (room.piece < pieces.size() && !pieces.get(room.piece).getBoundingBox().intersects(clip)) room.piece++;
                RandomSource random = RandomSource.create(generationSeed() ^ chunk.toLong() ^ room.piece);
                if (room.piece < pieces.size()) {
                    var first = pieces.getFirst().getBoundingBox();
                    long began = System.nanoTime();
                    try (var context = new CapsuleGenerationContext(level, sourceLevel, room,
                            pieces.get(room.piece) instanceof net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece)) {
                        pieces.get(room.piece).postProcess(level, level.structureManager(), sourceLevel.getChunkSource().getGenerator(),
                                random, clip, chunk, new BlockPos(first.getCenter().getX(), first.minY(), first.getCenter().getZ()));
                    }
                    long elapsed = System.nanoTime() - began;
                    if (elapsed > CALLBACK_WARN_NANOS) {
                        LogUtils.getLogger().warn("Native capsule callback took {} ms for {} chunk {} piece {}",
                                elapsed / 1_000_000.0, room.structure, chunk, room.piece);
                    }
                    checkpointPiece(room.piece++); return false;
                }
                if (!room.afterPlaced) {
                    long began = System.nanoTime();
                    try (var context = new CapsuleGenerationContext(level, sourceLevel, room)) {
                        structure.afterPlace(level, level.structureManager(), sourceLevel.getChunkSource().getGenerator(), random,
                                clip, chunk, new PiecesContainer(pieces));
                    }
                    long elapsed = System.nanoTime() - began;
                    if (elapsed > CALLBACK_WARN_NANOS) {
                        LogUtils.getLogger().warn("Native capsule afterPlace took {} ms for {} chunk {}",
                                elapsed / 1_000_000.0, room.structure, chunk);
                    }
                    room.afterPlaced = true; checkpoint(); return false;
                }
                // Publish the finished chunk to the live map before moving to the next chunk.
                if (!sampleChunk(chunk, deadline)) return false;
                room.chunk++; room.piece = 0; room.afterPlaced = false; room.previewCursor = 0;
                data.setDirty(); return false;
            }
            room.phase = "bedrock";
            room.bedrockCursor = 0;
            room.bedrockChunk = 0;
            data.setDirty();
            return false;
        }

        private void refreshPoiSections(BoundingBox area) {
            for (int chunkX = area.minX() >> 4; chunkX <= area.maxX() >> 4; chunkX++) {
                for (int chunkZ = area.minZ() >> 4; chunkZ <= area.maxZ() >> 4; chunkZ++) {
                    var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                    var sections = chunk.getSections();
                    int baseSection = level.getMinSection();
                    for (int i = 0; i < sections.length; i++)
                        level.getPoiManager().checkConsistencyWithBlocks(
                                net.minecraft.core.SectionPos.of(chunk.getPos(), baseSection + i), sections[i]);
                }
            }
        }

        /**
         * Natural fluids can flow back into a cleared envelope while native Pieces are
         * placed over many ticks. Remove everything outside the union of native Piece
         * boxes after placement; this is mod-agnostic and keeps every native Piece intact.
         */
        private boolean trim(long deadline) {
            BoundingBox projection = trimBounds();
            long trimWidthChunks = (long) trimMaxChunkX - trimMinChunkX + 1L;
            long trimDepthChunks = (long) trimMaxChunkZ - trimMinChunkZ + 1L;
            long trimChunks = trimWidthChunks * trimDepthChunks;
            // A source block outside the envelope may have queued fluid ticks inside it
            // before the foundation was sealed. Drop those delayed updates before the
            // last projection pass, so isolated fluid cannot reappear after completion.
            if (room.trimChunk == 0 && room.trimCursor == 0 && room.trimPass == 0) {
                level.getFluidTicks().clearArea(projection);
            }
            if (room.trimChunk >= trimChunks) {
                if (room.trimPass + 1 < trimPasses()) {
                    // Fluid ticks and boundary updates may be queued while a huge plan
                    // loads in chunks. Scan in the opposite direction once more: water
                    // can otherwise flow from the unprocessed edge into cleared chunks.
                    room.trimPass++;
                    room.trimChunk = 0;
                    room.trimCursor = 0;
                    level.getFluidTicks().clearArea(projection);
                    data.setDirty();
                    return false;
                }
                room.phase = "ready";
                room.ready = true;
                room.bedrockVersion = FINALIZATION_VERSION;
                data.setDirty();
                return true;
            }
            ChunkPos chunk = trimChunkAt(room.trimChunk, trimWidthChunks, trimChunks, (room.trimPass & 1) != 0);
            if (!ready(chunk, false)) return false;
            int minX = Math.max(projection.minX(), chunk.getMinBlockX());
            int maxX = Math.min(projection.maxX(), chunk.getMaxBlockX());
            int minZ = Math.max(projection.minZ(), chunk.getMinBlockZ());
            int maxZ = Math.min(projection.maxZ(), chunk.getMaxBlockZ());
            long width = (long) maxX - minX + 1L;
            long depth = (long) maxZ - minZ + 1L;
            long volume = width * depth * projection.getYSpan();
            BoundingBox clip = new BoundingBox(minX, projection.minY(), minZ, maxX, projection.maxY(), maxZ);
            // clearArea only touches registered chunk tick containers. Clear each clip
            // when that chunk is loaded, because distant structure chunks join the tick
            // scheduler progressively while this bounded job walks the footprint.
            level.getFluidTicks().clearArea(clip);
            while (room.trimCursor < volume && System.nanoTime() < deadline) {
                long index = room.trimCursor;
                int x = minX + (int) (index % width);
                int z = minZ + (int) ((index / width) % depth);
                int y = projection.minY() + (int) (index / (width * depth));
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z).getSection(
                        level.getSectionIndex(y)).hasOnlyAir()) {
                    room.trimCursor = Math.min(volume, ((long) ((y >> 4) + 1) * 16 - projection.minY()) * width * depth);
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                // The foundation is intentionally one block below the native
                // structure envelope. It lies inside the wider fluid-settle scan,
                // but must remain as the room's sealed bottom layer.
                if (y == bounds.minY() - 1
                        && x >= bounds.minX() && x <= bounds.maxX()
                        && z >= bounds.minZ() && z <= bounds.maxZ()) {
                    room.trimCursor++;
                    continue;
                }
                // Piece boxes are only coarse geometry. They often enclose untouched
                // terrain, caves, or fluids; keeping every block inside them produced
                // terrain-filled rooms. Preserve only positions that the live native
                // structure manager identifies as belonging to this actual structure.
                // Most envelope cells are air, so avoid the more expensive structure
                // lookup unless there is a block that could actually be trimmed.
                var state = level.getBlockState(pos);
                if (!state.isAir()) {
                    boolean nativeStructureBlock = level.structureManager()
                            .getStructureWithPieceAt(pos, start.getStructure()).isValid();
                    if (!nativeStructureBlock) nativeStructureBlock = start.getPieces().stream().anyMatch(piece ->
                            piece instanceof net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece
                                    && x >= piece.getBoundingBox().minX() && x <= piece.getBoundingBox().maxX()
                                    && z >= piece.getBoundingBox().minZ() && z <= piece.getBoundingBox().maxZ()
                                    && y >= piece.getBoundingBox().minY() - 16 && y <= piece.getBoundingBox().maxY());
                    if (!nativeStructureBlock) {
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                room.trimCursor++;
            }
            data.setDirty();
            if (room.trimCursor < volume) return false;
            refreshPoiSections(clip);
            room.trimCursor = 0;
            room.trimChunk++;
            releaseCurrent();
            data.setDirty();
            return false;
        }

        private BoundingBox trimBounds() {
            int margin = CapsuleInstances.reservedMargin();
            int minY = Math.max(level.getMinBuildHeight(), bounds.minY() - margin);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, bounds.maxY() + margin);
            return new BoundingBox(bounds.minX() - margin, minY, bounds.minZ() - margin,
                    bounds.maxX() + margin, maxY, bounds.maxZ() + margin);
        }

        private int trimPasses() {
            // The old worlds could contain exterior oceans and terrain which kept
            // feeding fluids back into a room. Native-only void storage has no such
            // source: a forward and reverse pass still seals delayed boundary updates.
            return level.getChunkSource().getGenerator() instanceof CapsuleVoidChunkGenerator ? 2 : FLUID_SETTLE_PASSES;
        }

        private ChunkPos trimChunkAt(int index, long width, long count, boolean reverse) {
            long linear = reverse ? count - 1L - index : index;
            return new ChunkPos(trimMinChunkX + (int) (linear % width), trimMinChunkZ + (int) (linear / width));
        }

        private static int[] spiralOffset(int index) {
            if (index <= 0) return new int[]{0, 0};
            int ring = (int) Math.ceil((Math.sqrt(index + 1.0) - 1.0) / 2.0);
            int sideLength = ring * 2;
            int distanceFromLast = (ring * 2 + 1) * (ring * 2 + 1) - 1 - index;
            int side = distanceFromLast / sideLength;
            int step = distanceFromLast % sideLength;
            return switch (side) {
                case 0 -> new int[]{ring - step, -ring};
                case 1 -> new int[]{-ring, -ring + step};
                case 2 -> new int[]{-ring + step, ring};
                default -> new int[]{ring, ring - step};
            };
        }

        private boolean clean(long deadline) {
            if (!room.cleanupEntitiesRemoved) {
                int minX = bounds.minX() - CapsuleInstances.reservedMargin();
                int maxX = bounds.maxX() + CapsuleInstances.reservedMargin();
                int minZ = bounds.minZ() - CapsuleInstances.reservedMargin();
                int maxZ = bounds.maxZ() + CapsuleInstances.reservedMargin();
                int minY = Math.max(level.getMinBuildHeight(), bounds.minY() - 16);
                int maxY = Math.min(level.getMaxBuildHeight(), bounds.maxY() + 17);
                var area = new net.minecraft.world.phys.AABB(minX, minY, minZ,
                        maxX + 1.0, maxY, maxZ + 1.0);
                for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area,
                        entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) entity.discard();
                room.cleanupEntitiesRemoved = true;
                data.setDirty();
            }
            long cleanupChunks = cleanChunkCount();
            if (room.cleanChunk >= cleanupChunks) {
                room.phase = "placing";
                room.terrainCleaned = true;
                room.cleanupVersion = CLEANUP_VERSION;
                room.chunk = 0; room.piece = 0; room.afterPlaced = false;
                data.setDirty();
                return false;
            }
            ChunkPos chunk = cleanChunkAt(room.cleanChunk);
            if (!ready(chunk, false)) return false;
            int minX = Math.max(chunk.getMinBlockX(), bounds.minX() - CapsuleInstances.reservedMargin());
            int maxX = Math.min(chunk.getMaxBlockX(), bounds.maxX() + CapsuleInstances.reservedMargin());
            int minZ = Math.max(chunk.getMinBlockZ(), bounds.minZ() - CapsuleInstances.reservedMargin());
            int maxZ = Math.min(chunk.getMaxBlockZ(), bounds.maxZ() + CapsuleInstances.reservedMargin());
            long width = (long) maxX - minX + 1L;
            long depth = (long) maxZ - minZ + 1L;
            int cleanMinY = Math.max(level.getMinBuildHeight(), bounds.minY() - 16);
            int cleanMaxY = Math.min(level.getMaxBuildHeight() - 1, bounds.maxY() + 16);
            long height = (long) cleanMaxY - cleanMinY + 1L;
            long volume = width * depth * height;
            BoundingBox cleanClip = new BoundingBox(minX, cleanMinY, minZ, maxX, cleanMaxY, maxZ);
            level.getFluidTicks().clearArea(cleanClip);
            level.getBlockTicks().clearArea(cleanClip);
            while (room.cleanCursor < volume && System.nanoTime() < deadline) {
                long index = room.cleanCursor;
                int x = minX + (int) (index % width);
                int z = minZ + (int) ((index / width) % depth);
                int y = cleanMinY + (int) (index / (width * depth));
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z).getSection(
                        level.getSectionIndex(y)).hasOnlyAir()) {
                    room.cleanCursor = Math.min(volume, ((long) ((y >> 4) + 1) * 16 - cleanMinY) * width * depth);
                    continue;
                }
                BlockPos pos = new BlockPos(x, y, z);
                var state = level.getBlockState(pos);
                if (!state.isAir() || !state.getFluidState().isEmpty()) {
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                room.cleanCursor++;
            }
            data.setDirty();
            if (room.cleanCursor < volume) return false;
            refreshPoiSections(cleanClip);
            room.cleanChunk++;
            room.cleanCursor = 0;
            releaseCurrent();
            data.setDirty();
            return false;
        }

        private long cleanChunkCount() {
            long width = (long) cleanMaxChunkX - cleanMinChunkX + 1L;
            long depth = (long) cleanMaxChunkZ - cleanMinChunkZ + 1L;
            return width * depth;
        }

        private ChunkPos cleanChunkAt(int index) {
            int width = cleanMaxChunkX - cleanMinChunkX + 1;
            int x = cleanMinChunkX + index % width;
            int z = cleanMinChunkZ + index / width;
            return new ChunkPos(x, z);
        }

        private boolean bedrock(long deadline) {
            if (room.bedrockChunk >= chunkCount) {
                room.phase = "trimming";
                room.ready = false;
                room.bedrockVersion = 1;
                room.trimChunk = 0;
                room.trimCursor = 0;
                room.trimPass = 0;
                data.setDirty();
                return false;
            }
            ChunkPos chunk = chunkAt(room.bedrockChunk);
            if (!ready(chunk, false)) return false;
            int minX = Math.max(bounds.minX(), chunk.getMinBlockX());
            int maxX = Math.min(bounds.maxX(), chunk.getMaxBlockX());
            int minZ = Math.max(bounds.minZ(), chunk.getMinBlockZ());
            int maxZ = Math.min(bounds.maxZ(), chunk.getMaxBlockZ());
            long width = (long) maxX - minX + 1L;
            long depth = (long) maxZ - minZ + 1L;
            long total = width * depth;
            int floorY = Math.max(level.getMinBuildHeight(), bounds.minY() - 1);
            while (room.bedrockCursor < total && System.nanoTime() < deadline) {
                long index = room.bedrockCursor;
                int x = minX + (int) (index % width);
                int z = minZ + (int) (index / width);
                BlockPos floor = new BlockPos(x, floorY, z);
                // The projection is deliberately unconditional: natural terrain is removed
                // and every cell under the structure receives the same sealed foundation.
                // The structure itself starts at bounds.minY(), so this cannot overwrite a
                // native Piece block.
                level.setBlock(floor, Blocks.BEDROCK.defaultBlockState(), 2);
                room.bedrockCursor++;
            }
            data.setDirty();
            if (room.bedrockCursor < total) return false;
            room.bedrockCursor = 0;
            room.bedrockChunk++;
            releaseCurrent();
            data.setDirty();
            return false;
        }
        private ChunkPos chunkAt(int index) {
            long width = (long) maxChunkX - minChunkX + 1L;
            int x = minChunkX + (int) (index % width);
            int z = minChunkZ + (int) (index / width);
            return new ChunkPos(x, z);
        }
        void checkpoint() {
            room.plan = start.createTag(serializationContext(), start.getChunkPos()); data.setDirty();
        }
        void checkpointPiece(int index) {
            var children = room.plan.getList("Children", net.minecraft.nbt.Tag.TAG_COMPOUND);
            if (index < children.size()) children.set(index,
                    start.getPieces().get(index).createTag(serializationContext()));
            data.setDirty();
        }
        private long generationSeed() {
            return room.sourceDimension.isBlank() ? sourceLevel.getSeed() : room.sourceSeed;
        }
        private StructurePieceSerializationContext serializationContext() {
            return StructurePieceSerializationContext.fromLevel(sourceLevel);
        }
        boolean sampleChunk(ChunkPos chunk, long deadline) {
            long until = Math.min(deadline, System.nanoTime() + PREVIEW_BUDGET_NANOS);
            int samples = 0;
            List<Integer> indices = mapSamples.getOrDefault(chunk.toLong(), List.of());
            while (room.previewCursor < indices.size() && samples++ < 64 && System.nanoTime() < until) {
                int i = indices.get(room.previewCursor++);
                int x = sampleX(i), z = sampleZ(i);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(bounds.maxY(), level.getMaxBuildHeight() - 1), z);
                room.pixels[i] = 0xFF171B20;
                while (pos.getY() >= Math.max(bounds.minY(), level.getMinBuildHeight())) {
                    var state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        int rgb = state.getMapColor(level, pos).col;
                        double shade = 0.72 + 0.28 * (pos.getY() - bounds.minY()) / Math.max(1, bounds.getYSpan());
                        room.pixels[i] = 0xFF000000 | (int)(((rgb >> 16) & 255) * shade) << 16
                                | (int)(((rgb >> 8) & 255) * shade) << 8 | (int)((rgb & 255) * shade);
                        break;
                    }
                    pos.move(0, -1, 0);
                }
                room.mapCursor++;
            }
            data.setDirty(); return room.previewCursor == indices.size();
        }
        int sampleX(int i) { return bounds.minX() + (i % 64) * Math.max(0, bounds.getXSpan() - 1) / 63; }
        int sampleZ(int i) { return bounds.minZ() + (i / 64) * Math.max(0, bounds.getZSpan() - 1) / 63; }
        void releaseCurrent() {
            for (ChunkPos ticket : tickets) level.getChunkSource().removeRegionTicket(TICKET, ticket, ticketRadius, ticket);
            tickets.clear();
        }
        void release() {
            releaseCurrent();
            if (planning != null) {
                planning.cancel(true);
                planning = null;
            }
            if (startTicket) level.getChunkSource().removeRegionTicket(START_TICKET, start.getChunkPos(), 0, start.getChunkPos());
            startTicket = false;
        }
    }

    /** FutureTask keeps the planner gate closed until an interrupted worker has really returned. */
    private static final class PlanFuture extends FutureTask<StructureStart> {
        private final AtomicBoolean entered = new AtomicBoolean();
        PlanFuture(Callable<StructureStart> callable) {
            super(callable);
        }
        @Override public void run() { entered.set(true); super.run(); }
        @Override protected void done() {
            if (!entered.get()) PLANNER_BUSY.set(false);
        }
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Large native templates and jigsaw graphs can legitimately need more than 512 MiB.
     * The old fixed threshold also counted normal JVM heap expansion as a failure. Keep a
     * bounded per-plan budget while scaling it with the configured heap of the test/server.
     */
    private static long planningMemoryGrowthLimit() {
        long max = Runtime.getRuntime().maxMemory();
        long scaled = max * 45L / 100L;
        long floor = 768L * 1024L * 1024L;
        long ceiling = 1_536L * 1024L * 1024L;
        return Math.max(floor, Math.min(ceiling, scaled));
    }

    private static final class CapsuleLimitException extends RuntimeException {
        final String code;
        CapsuleLimitException(String code) { super(code); this.code = code; }
    }

    private static final class PendingEntry {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos entrance;
        final ChunkPos chunk;
        final int side;
        int ticks;
        PendingEntry(ServerPlayer player, CapsuleInstances.Instance room, ServerLevel level, int side) {
            this.player = player; this.level = level; this.side = side;
        if (!room.boundsKnown || room.minX > room.maxX || room.minY > room.maxY || room.minZ > room.maxZ)
            throw new IllegalStateException("Missing saved capsule bounds");
        int x = room.minX + (room.maxX - room.minX) / 2;
        int z = room.minZ + (room.maxZ - room.minZ) / 2;
        switch (side & 3) {
            case 0 -> z = room.minZ - 3;
            case 1 -> x = room.maxX + 3;
            case 2 -> z = room.maxZ + 3;
            case 3 -> x = room.minX - 3;
        }
        // Use a small separately loaded entrance so arriving cannot collide with a chest or machine.
        int y = Math.clamp(room.minY + 1, level.getMinBuildHeight() + 2, level.getMaxBuildHeight() - 4);
            entrance = new BlockPos(x, y, z); chunk = new ChunkPos(entrance);
            level.getChunkSource().addRegionTicket(ENTRY_TICKET, chunk, 2, chunk);
        }
        boolean tick() {
            if (++ticks > 1200 || player.isRemoved() || !player.isAlive() || DimensionCapsuleService.hasReturnPoint(player)) {
                LogUtils.getLogger().warn("Capsule entry cancelled: player={} target={} ticks={} removed={} alive={} return={}",
                        player.getGameProfile().getName(), level.dimension().location(), ticks,
                        player.isRemoved(), player.isAlive(), DimensionCapsuleService.hasReturnPoint(player));
                release(); return true;
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (level.getChunkSource().getChunkNow(chunk.x + dx, chunk.z + dz) == null) return false;
            if (!level.areEntitiesLoaded(chunk.toLong())) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = entrance.offset(dx, -1, dz);
            level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 2);
            for (int dy = 1; dy <= 3; dy++) level.setBlock(p.above(dy), Blocks.AIR.defaultBlockState(), 2);
        }
        DimensionCapsuleService.enterAt(player, level, entrance, (side * 90.0F) % 360.0F);
            release(); return true;
        }
        void release() { level.getChunkSource().removeRegionTicket(ENTRY_TICKET, chunk, 2, chunk); }
    }
}
