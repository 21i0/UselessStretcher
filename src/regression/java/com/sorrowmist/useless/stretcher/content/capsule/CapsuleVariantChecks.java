package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real native plans and a three-process save/restart check, excluded from production jars. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class CapsuleVariantChecks {
    private static final UUID OWNER = UUID.fromString("767b2ca5-5015-413b-a214-4ed9869c40e3");
    private static final String ID = "minecraft:bastion_remnant";
    private static final String DIM = DimensionCapsuleCatalog.CAPSULE_NETHER;
    private static final String KEY = OWNER + "|" + ID + "|" + DIM;
    private static final Set<String> FAMILIES = Set.of("minecraft:bastion/treasure", "minecraft:bastion/bridge",
            "minecraft:bastion/hoglin_stable", "minecraft:bastion/units");
    private static ServerPlayer player;
    private static Marker marker;
    private static int ticks, stage, pauseCursor, journeys;
    private static int initialRooms;
    private static int initialTargetChunks;
    private static boolean finished;
    private static CapsuleInstances.Instance room;
    private static CapsuleInstances.Instance neighbor;
    private static String beforeJourney;
    private static Map<Long, Cell> contentSnapshot;
    private static net.minecraft.world.level.levelgen.structure.BoundingBox snapshotBounds;
    private static int snapshotPlanHash;
    private static final Map<Long, Cell> reservedMarkers = new HashMap<>();
    private static final java.util.List<BlockPos> blockTickMarkers = new java.util.ArrayList<>();
    private static final java.util.List<BlockPos> fluidTickMarkers = new java.util.ArrayList<>();
    private static final java.util.List<UUID> entityMarkers = new java.util.ArrayList<>();
    private static final java.util.Set<Long> forcedMarkerChunks = new java.util.HashSet<>();
    private static final java.util.Set<Long> markerChunks = new java.util.HashSet<>();
    private static int markerVisibilityStarted;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.capsule_variants_only")) return;
        try {
            var server = event.getServer();
            marker = server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Marker::new, Marker::load),
                    "useless_stretcher_test_capsule_variants");
            player = newPlayer(server.overworld());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
            initialRooms = CapsuleInstances.get(server).rooms.size();
            check(server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(DIM))) != null,
                    "native nether mirror exists");
            initialTargetChunks = target(server).getChunkSource().getLoadedChunksCount();
            if (marker.state.getInt("phase") == 0) {
                CapsuleVariants.request(player, ID, DIM, 1, "", false, 0, 0);
                LogUtils.getLogger().info("CAPSULE VARIANTS PROCESS 1: discovering actual native bastion plans; source seed={}",
                        server.getLevel(net.minecraft.world.level.Level.NETHER).getSeed());
            } else if (marker.state.getInt("phase") == 1) {
                var search = search(server);
                check(search != null && search.active, "active discovery restored from real disk save");
                check(search.cursor >= marker.state.getInt("cursor"), "search cursor did not roll back after restart");
                check(search.candidates.containsKey(marker.state.getString("candidate")), "selected treasure candidate survived restart");
                check(search.candidates.get(marker.state.getString("candidate")).plan.equals(marker.state.getCompound("plan")),
                        "exact source plan survived restart without reroll");
                check(initialRooms == marker.state.getInt("rooms"), "discovery did not allocate rooms while offline");
                LogUtils.getLogger().info("CAPSULE VARIANTS PROCESS 2: native candidate and active cursor loaded from disk at {}", search.cursor);
            } else if (marker.state.getInt("phase") == 2) {
                room = room(server);
                check(room != null && room.generating && !room.ready, "interrupted selected generation restored from disk");
                check(room.plan.equals(marker.state.getCompound("placed_plan")), "partial generation retains exact saved native plan");
                check(room.variantFingerprint.equals(marker.state.getString("candidate")), "selected candidate identity persisted in room");
                stage = 20;
                assertSelectedPlan(server);
                startupAssertions(server);
                LogUtils.getLogger().info("CAPSULE VARIANTS PROCESS 3: partial native placement restored phase={} chunk={} piece={}",
                        room.phase, room.chunk, room.piece);
            } else {
                LogUtils.getLogger().info("CAPSULE VARIANTS CHECKS ALREADY COMPLETED for this test save; use a fresh isolated world to rerun");
                finished = true; server.halt(false);
            }
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("useless_stretcher.capsule_variants_only") || finished || marker == null) return;
        try {
            var server = event.getServer();
            check(++ticks < 36000, "variant integration timeout at test stage " + stage);
            switch (marker.state.getInt("phase")) {
                case 0 -> discover(server);
                case 1 -> select(server);
                case 2 -> finishPlacement(server);
                default -> throw new AssertionError("Unexpected test phase");
            }
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }

    private static void discover(MinecraftServer server) {
        check(CapsuleInstances.get(server).rooms.size() == initialRooms, "discovery never allocates a capsule room");
        check(target(server).getChunkSource().getLoadedChunksCount() <= initialTargetChunks,
                "planning candidates never loads target chunks or places a preview into the world");
        var search = search(server);
        if (search == null) {
            if (ticks % 20 == 0) CapsuleVariants.request(player, ID, DIM, 1, "", false, 0, 0);
            return;
        }
        check(search.candidates.size() <= 32, "candidate cache respects network/cache bound");
        if (stage == 1) {
            if (ticks % 20 != 0) return;
            check(!search.active && search.cursor == pauseCursor, "paused search stops advancing its persisted cursor");
            CapsuleVariants.request(player, ID, DIM, 1, "", false, 0, 0);
            check(search.active, "continue resumes the existing search");
            stage = 2;
        }
        var found = search.candidates.values().stream().map(candidate -> candidate.layout.family()).collect(java.util.stream.Collectors.toSet());
        if (!found.containsAll(FAMILIES)) {
            if (!search.active && ticks % 20 == 0) CapsuleVariants.request(player, ID, DIM, 1, "", false, 0, 0);
            return;
        }
        if (stage == 0) {
            CapsuleVariants.request(player, ID, DIM, 2, "", false, 0, 0);
            pauseCursor = search.cursor; stage = 1;
            LogUtils.getLogger().info("CAPSULE VARIANT FOUR FAMILIES PASSED: {} candidates / {} attempts; roots={}",
                    search.candidates.size(), search.cursor,
                    search.candidates.values().stream().map(candidate -> candidate.layout.template()).distinct().toList());
            return;
        }
        if (stage != 2 || search.cursor <= pauseCursor) return;
        var treasure = search.candidates.values().stream().filter(candidate -> candidate.layout.family().equals("minecraft:bastion/treasure")).findFirst().orElseThrow();
        check(treasure.layout.template().equals("minecraft:bastion/treasure/big_air_full"), "treasure label comes from actual native first template");
        check(treasure.layout.pixels().length == 4096 && java.util.Arrays.stream(treasure.layout.pixels()).anyMatch(pixel -> pixel != 0),
                "saved candidate has a nonblank bounded piece-footprint preview");
        var decoded = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source(server)),
                treasure.plan.copy(), source(server).getSeed());
        check(decoded != null && decoded.isValid(), "saved source candidate is a valid full native plan");
        check(CapsuleVariantLayout.inspect(decoded, StructurePieceSerializationContext.fromLevel(source(server))).fingerprint()
                .equals(treasure.layout.fingerprint()), "saved candidate fingerprint describes its actual native graph");
        var saved = CapsuleVariantsData.get(server).save(new CompoundTag(), server.registryAccess());
        var restored = CapsuleVariantsData.load(saved.copy(), server.registryAccess());
        check(restored.searches.get(KEY).candidates.get(treasure.layout.fingerprint()).plan.equals(treasure.plan),
                "candidate source NBT round trips through SavedData");
        marker.state.putInt("phase", 1); marker.state.putInt("cursor", search.cursor);
        marker.state.putInt("rooms", initialRooms);
        marker.state.putString("candidate", treasure.layout.fingerprint()); marker.state.put("plan", treasure.plan.copy());
        marker.setDirty();
        check(search.active, "save preserves an active search for real process restart");
        LogUtils.getLogger().info("CAPSULE VARIANTS RESTART 1 REQUIRED: four native families, exact candidate, no room allocation and pause/continue verified; cursor={} candidate={}",
                search.cursor, treasure.layout.fingerprint());
        stop(server);
    }

    private static void select(MinecraftServer server) throws Exception {
        var search = search(server);
        check(search != null, "persisted search remains available");
        if (stage == 0) {
            String signature = liveSignature();
            if (signature.isEmpty()) return;
            check(!signature.equals("unavailable") && signature.equals(search.signature),
                    "selected generation waits for the current live resource signature");
            if (search.cursor <= marker.state.getInt("cursor")) return;
            CapsuleVariants.request(player, ID, DIM, 2, "", false, 0, 0);
            String token = marker.state.getString("candidate");
            int count = CapsuleInstances.get(server).rooms.size();
            CapsuleVariants.request(player, ID, DIM, 3, "f".repeat(64), false, 0, 0);
            check(CapsuleInstances.get(server).rooms.size() == count, "unknown candidate cannot allocate or place a structure");
            CapsuleVariants.request(player, ID, DIM, 3, token, false, 0, 0);
            room = room(server);
            check(room != null && room.generating, "selected treasure native plan accepted for background placement");
            check(room.variantFingerprint.equals(token), "generated room records the selected candidate");
            CapsuleVariants.request(player, ID, DIM, 3, token, false, 0, 0);
            check(CapsuleInstances.get(server).rooms.size() == count + 1, "repeated selected-generation request allocates exactly one room");
            check(search.candidates.get(token).generatedInstanceId == room.instanceId, "candidate remembers generated instance for idempotent packets");
            assertSelectedPlan(server);
            stage = 1;
        }
        check(room != null && room.error.isEmpty(), "selected native placement failed: " + (room == null ? "missing" : room.error));
        if (room.phase.equals("placing") && (room.chunk > 0 || room.piece > 0)) {
            marker.state.putInt("phase", 2); marker.state.putInt("instance", room.instanceId);
            marker.state.put("placed_plan", room.plan.copy()); marker.setDirty();
            LogUtils.getLogger().info("CAPSULE VARIANTS RESTART 2 REQUIRED: exact treasure selected once; partial placement persisted at chunk={} piece={} plan_hash={}",
                    room.chunk, room.piece, room.plan.hashCode());
            stop(server);
        }
    }

    private static void finishPlacement(MinecraftServer server) throws Exception {
        check(room != null && room.error.isEmpty(), "resumed selected native placement failed: " + (room == null ? "missing" : room.error));
        if (!room.ready) return;
        if (stage == 20) {
            assertSelectedPlan(server);
            ownerIsolation(server);
            var target = target(server);
            var start = placedStart(server);
            int treasureLoot = 0, allLoot = 0, gold = 0, magmaSpawners = 0;
            for (BlockPos pos : BlockPos.betweenClosed(start.getBoundingBox().minX(), start.getBoundingBox().minY(), start.getBoundingBox().minZ(),
                    start.getBoundingBox().maxX(), start.getBoundingBox().maxY(), start.getBoundingBox().maxZ())) {
                var state = target.getBlockState(pos);
                if (state.is(Blocks.GOLD_BLOCK)) gold++;
                var be = target.getBlockEntity(pos);
                if (be instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
                    allLoot++;
                    if (chest.getLootTable().location().toString().equals("minecraft:chests/bastion_treasure")) treasureLoot++;
                }
                if (be instanceof SpawnerBlockEntity spawner) {
                    var nbt = spawner.saveWithoutMetadata(target.registryAccess());
                    if (nbt.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:magma_cube")) magmaSpawners++;
                }
            }
            check(treasureLoot >= 1 && allLoot >= 1 && gold >= 1 && magmaSpawners >= 1,
                    "actual treasure-room blocks/native contents: treasureLoot=" + treasureLoot + " allLoot=" + allLoot + " gold=" + gold + " magmaSpawners=" + magmaSpawners);
            LogUtils.getLogger().info("CAPSULE VARIANT TREASURE CONTENT PASSED: template={} pieces={} treasure_loot={} all_loot={} gold_blocks={} magma_spawners={} instance={} fingerprint={}",
                    "minecraft:bastion/treasure/big_air_full", start.getPieces().size(), treasureLoot, allLoot, gold, magmaSpawners, room.instanceId, room.variantFingerprint);
            placeReservedMarkers(server);
            markerVisibilityStarted = ticks; stage = 23;
        } else if (stage == 23) {
            if (!reservedEntitiesVisible(server)) {
                check(ticks - markerVisibilityStarted < 200,
                        "reserved marker entities must become visible within 200 server ticks; " + reservedVisibilityDescription(server));
                return;
            }
            assertReservedMarkers(server);
            LogUtils.getLogger().info("CAPSULE VARIANT MARKER BASELINE READY: all marker chunks/entities visible after {} server ticks", ticks - markerVisibilityStarted);
            beforeJourney = contentsDigest(server);
            legacyConflictGuard(server);
            int count = CapsuleInstances.get(server).rooms.size();
            CapsuleVariants.request(player, ID, DIM, 3, marker.state.getString("candidate"), false, 0, 0);
            check(CapsuleInstances.get(server).rooms.size() == count && room.ready,
                    "completed selected candidate is reused without replacement or loot replay");
            assertContentsUnchanged(server, "repeat generate leaves all existing structure blocks and unopened loot unchanged");
            var second = search(server).candidates.values().stream()
                    .filter(candidate -> candidate.layout.family().equals("minecraft:bastion/bridge")).findFirst().orElseThrow();
            CapsuleVariants.request(player, ID, DIM, 3, second.layout.fingerprint(), false, room.instanceId, 0);
            neighbor = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, ID, DIM, second.generatedInstanceId));
            check(neighbor != null && neighbor != room && neighbor.generating,
                    "a different saved layout creates a separate adjacent instance");
            check(CapsuleInstances.adjacent(room, neighbor) && !CapsuleInstances.boundsOverlap(room, neighbor),
                    "second selected layout reserves a disjoint adjacent room before writes");
            stage = 22;
        } else if (stage == 22) {
            check(neighbor != null && neighbor.error.isEmpty(), "adjacent selected layout failed: " + (neighbor == null ? "missing" : neighbor.error));
            if (!neighbor.ready) return;
            check(CapsuleInstances.adjacent(room, neighbor) && !CapsuleInstances.boundsOverlap(room, neighbor),
                    "completed selected layouts remain adjacent with no overlapping cleanup bounds");
            assertContentsUnchanged(server,
                    "generating another selected variant preserves every block and unopened loot of the completed treasure instance");
            assertReservedMarkers(server);
            int touched = 0;
            for (long packed : reservedMarkers.keySet()) {
                var pos = BlockPos.of(packed); int margin = CapsuleInstances.reservedMargin();
                if (pos.getX() >= neighbor.minX - margin && pos.getX() <= neighbor.maxX + margin
                        && pos.getZ() >= neighbor.minZ - margin && pos.getZ() <= neighbor.maxZ + margin
                        && pos.getY() >= neighbor.minY - 16 && pos.getY() <= neighbor.maxY + 16) touched++;
            }
            check(touched > 0, "reserved marker fixture intersects the adjacent job's unprotected cleanup envelope");
            LogUtils.getLogger().info("CAPSULE VARIANT RESERVED MARGIN PASSED: {} marker blocks, {} chests/tick pairs, {} entities; {} marker cells cross adjacent cleanup envelope",
                    reservedMarkers.size(), blockTickMarkers.size(), entityMarkers.size(), touched);
            LogUtils.getLogger().info("CAPSULE VARIANT NEIGHBOR PRESERVATION PASSED: treasure_instance={} bridge_instance={} content_sha256={}",
                    room.instanceId, neighbor.instanceId, beforeJourney);
            player.setPos(12, 120, 12);
            NativeCapsules.request(player, ID, DIM, 2, 0, room.instanceId); stage = 21;
        } else if (stage == 21) {
            if (!DimensionCapsuleService.hasReturnPoint(player)) return;
            check(player.serverLevel() == target(server), "entry reaches the same stable capsule mirror");
            DimensionCapsuleService.enter(player, player.getMainHandItem());
            check(player.serverLevel() == server.overworld() && !DimensionCapsuleService.hasReturnPoint(player)
                    && Math.abs(player.getX() - 12) < 1, "return reaches original world and coordinates");
            assertContentsUnchanged(server, "enter/return preserves exact structure blocks, spawner data and unopened loot");
            if (++journeys < 2) { NativeCapsules.request(player, ID, DIM, 2, 2, room.instanceId); return; }
            marker.state.putInt("phase", 3); marker.setDirty();
            for (long packed : forcedMarkerChunks) target(server).setChunkForced(net.minecraft.world.level.ChunkPos.getX(packed),
                    net.minecraft.world.level.ChunkPos.getZ(packed), false);
            LogUtils.getLogger().info("CAPSULE VARIANTS CHECKS PASSED: four actual bastion families; exact treasure plan; bounded persisted discovery; pause/continue; two real process restarts; native treasure loot/magma spawner/gold; idempotent selection; completed neighbor preserved; two stable round trips; attempts={} candidates={} content_sha256={}",
                    search(server).cursor, search(server).candidates.size(), beforeJourney);
            stop(server);
        }
    }

    private static void assertSelectedPlan(MinecraftServer server) {
        var layout = CapsuleVariantLayout.inspect(placedStart(server), StructurePieceSerializationContext.fromLevel(source(server)));
        check(layout.fingerprint().equals(marker.state.getString("candidate")), "selected exact native graph survives translation, support lift and restart without reroll");
        check(layout.template().equals("minecraft:bastion/treasure/big_air_full"), "generated graph remains the chosen treasure family");
    }

    /** Independent copies exercise corrupt/oversized save boundaries without polluting the live cache. */
    private static void startupAssertions(MinecraftServer server) throws Exception {
        var live = CapsuleVariantsData.get(server);
        var serialized = live.save(new CompoundTag(), server.registryAccess());
        CompoundTag sampleSearch = serialized.getList("searches", Tag.TAG_COMPOUND).getCompound(0).copy();
        check(!sampleSearch.isEmpty(), "saved variant fixture exists for isolation checks");
        var searches = new ListTag();
        for (int i = 0; i < 48; i++) {
            var search = sampleSearch.copy();
            search.putUUID("owner", new UUID(0, 100 + i / 6));
            search.putString("id", "useless_stretcher_test:structure_" + i);
            search.put("candidates", new ListTag()); searches.add(search);
        }
        var oversized = new CompoundTag(); oversized.put("searches", searches);
        var cappedSearches = CapsuleVariantsData.load(oversized, server.registryAccess());
        check(cappedSearches.searches.size() == CapsuleVariantsData.MAX_SEARCHES,
                "SavedData load enforces global search count bound");
        check(cappedSearches.searches.values().stream().collect(java.util.stream.Collectors.groupingBy(s -> s.owner,
                        java.util.stream.Collectors.counting())).values().stream().allMatch(count -> count <= CapsuleVariantsData.MAX_PER_OWNER),
                "SavedData load enforces per-owner search bound");

        CompoundTag sampleCandidate = sampleSearch.getList("candidates", Tag.TAG_COMPOUND).getCompound(0).copy();
        check(!sampleCandidate.isEmpty(), "saved native candidate fixture exists for byte-bound checks");
        var manySmallPlans = new ListTag();
        for (int i = 1; i <= 40; i++) {
            var candidate = sampleCandidate.copy();
            candidate.putString("fingerprint", String.format(java.util.Locale.ROOT, "%064x", i)); manySmallPlans.add(candidate);
        }
        var candidateCountSearch = sampleSearch.copy(); candidateCountSearch.put("candidates", manySmallPlans);
        var candidateCountList = new ListTag(); candidateCountList.add(candidateCountSearch);
        var countFixture = new CompoundTag(); countFixture.put("searches", candidateCountList);
        var cappedCandidates = CapsuleVariantsData.load(countFixture, server.registryAccess());
        check(cappedCandidates.searches.values().iterator().next().candidates.size() == CapsuleVariantsData.MAX_CANDIDATES,
                "SavedData load enforces candidate count independently of byte budget");
        var largePlan = sampleCandidate.getCompound("plan").copy();
        int initialBytes = CapsuleVariantsData.planBytes(largePlan);
        largePlan.putByteArray("test_padding", new byte[CapsuleVariantsData.MAX_PLAN_BYTES - initialBytes - 64]);
        var plans = new ListTag();
        for (int i = 1; i <= 40; i++) {
            // Sharing this immutable test plan avoids allocating 80 MiB merely to test the loader's counter.
            var candidate = sampleCandidate.copy(); candidate.put("plan", largePlan);
            candidate.putString("fingerprint", String.format(java.util.Locale.ROOT, "%064x", i)); plans.add(candidate);
        }
        var oneSearch = sampleSearch.copy(); oneSearch.put("candidates", plans);
        var oneSearchList = new ListTag(); oneSearchList.add(oneSearch);
        var byteFixture = new CompoundTag(); byteFixture.put("searches", oneSearchList);
        var cappedBytes = CapsuleVariantsData.load(byteFixture, server.registryAccess());
        check(cappedBytes.bytes() <= CapsuleVariantsData.MAX_CACHE_BYTES
                        && !cappedBytes.searches.isEmpty() && cappedBytes.searches.values().iterator().next().candidates.size() < 32,
                "SavedData load counts global plan bytes before accepting more candidates");
        var tooLarge = new CompoundTag(); tooLarge.putByteArray("test_padding", new byte[CapsuleVariantsData.MAX_PLAN_BYTES]);
        boolean rejected = false;
        try { CapsuleVariantsData.planBytes(tooLarge); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "one oversized source plan is rejected by the streaming byte counter");

        var isolated = CapsuleVariantsData.load(serialized.copy(), server.registryAccess());
        var search = isolated.searches.get(KEY); check(search != null, "isolated source cache loaded");
        search.signature = "deliberately-obsolete-resource-signature"; search.active = true;
        var roomsBefore = CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess());
        var refresh = CapsuleVariants.class.getDeclaredMethod("refresh", CapsuleVariantsData.Search.class, CapsuleVariantsData.class);
        refresh.setAccessible(true); refresh.invoke(null, search, isolated);
        check(search.candidates.isEmpty() && search.cursor == 0 && !search.active && search.phase.equals("stale"),
                "resource signature change invalidates unplaced plans and their cursor");
        check(roomsBefore.equals(CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess())),
                "resource signature invalidation leaves every allocated/inhabited room and exact plan untouched");
        installationRollback(server);
        allocatorClearance();
        plannerCancellationContract();
        LogUtils.getLogger().info("CAPSULE VARIANT SAFETY BOUNDARIES PASSED: global/per-owner search count, global/per-plan bytes, stale unplaced cache isolation, planner cancellation contract");
    }

    private static void ownerIsolation(MinecraftServer server) throws Exception {
        check(liveSignature().equals(search(server).signature) && !search(server).signature.isEmpty(),
                "owner-isolation request runs against an initialized live resource signature");
        var outsider = newPlayer(server.overworld(), UUID.fromString("156b6c12-34f7-477d-a1bd-53b5a5db6055"), "VariantOutsider");
        outsider.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
        var before = CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess());
        try {
            CapsuleVariants.request(outsider, ID, DIM, 3, marker.state.getString("candidate"), false, room.instanceId, 0);
            check(before.equals(CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess())),
                    "another owner cannot generate or attach this owner's cached native candidate");
            var foreign = CapsuleVariantsData.get(server).searches.get(CapsuleVariantsData.key(outsider.getUUID(), ID, DIM));
            check(foreign != null && foreign.candidates.isEmpty(), "candidate tokens are resolved only inside the requesting owner's cache");
        } finally {
            CapsuleVariantsData.get(server).searches.remove(CapsuleVariantsData.key(outsider.getUUID(), ID, DIM));
            CapsuleVariantsData.get(server).setDirty();
        }
        LogUtils.getLogger().info("CAPSULE VARIANT OWNER ISOLATION PASSED: foreign cached token cannot allocate/replace/attach rooms");
    }

    private static String liveSignature() throws ReflectiveOperationException {
        var field = CapsuleVariants.class.getDeclaredField("signature"); field.setAccessible(true);
        return (String)field.get(null);
    }

    private static void allocatorClearance() {
        for (int direction : new int[]{1, -1}) {
            var data = new CapsuleInstances();
            var old = new CapsuleInstances.Instance(OWNER, ID, DIM, 0, 0, false);
            var adjacent = new CapsuleInstances.Instance(OWNER, ID, DIM, 1, 1, false);
            setBounds(old, 1999990, 50, 1999990, 2000002, 70, 2000002);
            if (direction > 0) setBounds(adjacent, 2000016, 50, 1999990, 2000028, 70, 2000002);
            else setBounds(adjacent, 1999964, 50, 1999990, 1999976, 70, 2000002);
            data.rooms.put(old.key(), old); data.rooms.put(adjacent.key(), adjacent);
            var oldTag = old.save().copy();
            check(!data.hasClearance(adjacent), "synthetic gap13 is rejected before snap, direction=" + direction);
            int[] shift = data.snapBesideExisting(adjacent);
            int xGap = Math.max(adjacent.minX - old.maxX - 1, old.minX - adjacent.maxX - 1);
            int zGap = Math.max(adjacent.minZ - old.maxZ - 1, old.minZ - adjacent.maxZ - 1);
            check((shift[0] != 0 || shift[1] != 0) && Math.max(xGap, zGap) >= CapsuleInstances.reservedMargin()
                            && data.hasClearance(adjacent), "snap restores full clearance for X direction " + direction);
            check(oldTag.equals(old.save()), "snap cannot move/change the existing room, direction=" + direction);
        }
        LogUtils.getLogger().info("CAPSULE VARIANT ALLOCATOR CLEARANCE PASSED: positive/negative X gap13 moved to full reserved clearance");
    }

    private static void setBounds(CapsuleInstances.Instance value, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        value.minX = minX; value.minY = minY; value.minZ = minZ;
        value.maxX = maxX; value.maxY = maxY; value.maxZ = maxZ; value.boundsKnown = true;
    }

    private static void placeReservedMarkers(MinecraftServer server) {
        var target = target(server); int margin = CapsuleInstances.reservedMargin();
        int midX = room.minX + (room.maxX - room.minX) / 2, midZ = room.minZ + (room.maxZ - room.minZ) / 2;
        int[] heights = {room.minY - 2, room.minY + (room.maxY - room.minY) / 2, room.maxY + 2};
        int serial = 0;
        for (int y : heights) for (int face = 0; face < 4; face++) {
            check(y - 1 >= target.getMinBuildHeight() && y + 3 < target.getMaxBuildHeight(),
                    "whole-column fixture below/above native bounds remains inside dimension build limits");
            int x = face == 0 ? room.maxX + margin : face == 1 ? room.minX - margin : midX;
            int z = face == 2 ? room.maxZ + margin : face == 3 ? room.minZ - margin : midZ;
            BlockPos chestPos = new BlockPos(x, y, z), markerPos = chestPos.below();
            BlockPos fluidPos = chestPos.offset(face == 0 ? -2 : face == 1 ? 2 : 3, 0,
                    face == 2 ? -2 : face == 3 ? 2 : 3);
            forceMarkerChunk(target, chestPos); forceMarkerChunk(target, fluidPos);
            target.setBlock(markerPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
            target.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity)target.getBlockEntity(chestPos);
            check(chest != null, "reserved margin fixture chest exists");
            chest.getPersistentData().putString("capsule_marker", "Reserved capsule marker " + serial);
            chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 13)); chest.setChanged();
            rememberMarker(target, markerPos); rememberMarker(target, chestPos);
            for (var direction : net.minecraft.core.Direction.values()) {
                var glass = fluidPos.relative(direction); target.setBlock(glass, Blocks.GLASS.defaultBlockState(), 2); rememberMarker(target, glass);
            }
            target.setBlock(fluidPos, Blocks.WATER.defaultBlockState(), 2); rememberMarker(target, fluidPos);
            target.getFluidTicks().clearArea(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                    fluidPos.getX(), fluidPos.getY(), fluidPos.getZ(), fluidPos.getX(), fluidPos.getY(), fluidPos.getZ()));
            target.scheduleTick(markerPos, Blocks.DIAMOND_BLOCK, 100000);
            target.scheduleTick(fluidPos, net.minecraft.world.level.material.Fluids.WATER, 100000);
            blockTickMarkers.add(markerPos); fluidTickMarkers.add(fluidPos);
            var armorStand = new net.minecraft.world.entity.decoration.ArmorStand(target, x + .5, y + 1, z + .5);
            armorStand.setNoGravity(true); armorStand.setInvulnerable(true);
            armorStand.setCustomName(net.minecraft.network.chat.Component.literal("Reserved capsule entity " + serial++));
            check(target.addFreshEntity(armorStand), "reserved margin armor stand accepted"); entityMarkers.add(armorStand.getUUID());
        }
    }

    private static void forceMarkerChunk(ServerLevel target, BlockPos pos) {
        long packed = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        markerChunks.add(packed);
        if (!target.getForcedChunks().contains(packed)) {
            target.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); forcedMarkerChunks.add(packed);
        }
        target.getChunk(pos);
    }
    private static boolean reservedEntitiesVisible(MinecraftServer server) {
        var target = target(server);
        for (long chunk : markerChunks) if (!target.areEntitiesLoaded(chunk)) return false;
        for (UUID uuid : entityMarkers) if (target.getEntity(uuid) == null || target.getEntity(uuid).isRemoved()) return false;
        return true;
    }
    private static String reservedVisibilityDescription(MinecraftServer server) {
        var target = target(server);
        return "chunks=" + markerChunks.stream().map(chunk -> chunk + ":" + target.areEntitiesLoaded(chunk)).toList()
                + " entities=" + entityMarkers.stream().map(uuid -> uuid + ":" + (target.getEntity(uuid) != null)).toList();
    }
    private static void rememberMarker(ServerLevel target, BlockPos pos) {
        reservedMarkers.put(pos.asLong(), new Cell(target.getBlockState(pos), canonicalBlockEntity(target, pos)));
    }
    private static void assertReservedMarkers(MinecraftServer server) {
        var target = target(server);
        for (var entry : reservedMarkers.entrySet()) {
            var pos = BlockPos.of(entry.getKey()); var before = entry.getValue();
            check(before.state().equals(target.getBlockState(pos)) && java.util.Objects.equals(before.nbt(), canonicalBlockEntity(target, pos)),
                    "reserved player block/chest NBT preserved at " + pos);
        }
        for (BlockPos pos : blockTickMarkers) check(target.getBlockTicks().hasScheduledTick(pos, Blocks.DIAMOND_BLOCK), "reserved block tick preserved at " + pos);
        for (BlockPos pos : fluidTickMarkers) check(target.getFluidTicks().hasScheduledTick(pos, net.minecraft.world.level.material.Fluids.WATER), "reserved fluid tick preserved at " + pos);
        for (UUID uuid : entityMarkers) {
            var entity = target.getEntity(uuid);
            check(entity instanceof net.minecraft.world.entity.decoration.ArmorStand && !entity.isRemoved() && entity.isNoGravity()
                            && entity.getCustomName() != null && entity.getCustomName().getString().startsWith("Reserved capsule entity "),
                    "reserved NoGravity named entity preserved: " + uuid);
        }
    }

    private static void legacyConflictGuard(MinecraftServer server) throws Exception {
        var fake = new CapsuleInstances();
        var old = new CapsuleInstances.Instance(OWNER, ID, DIM, 0, 0, false);
        setBounds(old, room.minX, room.minY, room.minZ, room.maxX, room.maxY, room.maxZ); old.ready = true;
        old.plan = room.plan.copy(); fake.rooms.put(old.key(), old);
        var bad = new CapsuleInstances.Instance(OWNER, ID, DIM, 1, 1, false);
        var context = StructurePieceSerializationContext.fromLevel(source(server));
        var original = placedStart(server); int dx = room.maxX + 14 - original.getBoundingBox().minX();
        var pieces = original.getPieces().stream().map(piece -> CapsulePieceGeometry.move(piece, dx, 0, 0, context)).toList();
        var moved = new StructureStart(original.getStructure(), new net.minecraft.world.level.ChunkPos(
                (original.getChunkPos().getMinBlockX() + dx) >> 4, original.getChunkPos().z), original.getReferences(),
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(pieces));
        bad.plan = moved.createTag(context, moved.getChunkPos()); bad.sourceDimension = room.sourceDimension; bad.sourceSeed = room.sourceSeed;
        bad.phase = "placing"; bad.terrainCleaned = true; bad.piece = 1; bad.generating = true;
        bad.geometryVersion = CapsulePieceGeometry.VERSION; bad.bedrockVersion = 4;
        fake.rooms.put(bad.key(), bad);
        var type = Class.forName(NativeCapsules.class.getName() + "$Job");
        var constructor = type.getDeclaredConstructor(CapsuleInstances.class, CapsuleInstances.Instance.class,
                ServerLevel.class, ServerLevel.class, net.minecraft.world.level.levelgen.structure.Structure.class);
        constructor.setAccessible(true);
        Object job = constructor.newInstance(fake, bad, target(server), source(server), original.getStructure());
        check(!fake.hasClearance(bad), "legacy partial-job fixture retains the old gap13 conflict");
        var oldTag = old.save().copy(); var actualRooms = CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess());
        var step = type.getDeclaredMethod("step"); step.setAccessible(true);
        boolean refused = false;
        try { step.invoke(job); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            var cause = failure.getCause(); var code = cause.getClass().getDeclaredField("code"); code.setAccessible(true);
            refused = code.get(cause).equals("allocation_conflict");
        }
        check(refused, "old started allocation conflict is refused before any world write");
        check(oldTag.equals(old.save()) && actualRooms.equals(CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess())),
                "refused legacy partial job leaves old room plans/NBT untouched");
        assertReservedMarkers(server); assertContentsUnchanged(server, "legacy conflict refusal preserves every existing native block");
        LogUtils.getLogger().info("CAPSULE VARIANT LEGACY CONFLICT GUARD PASSED: persisted gap13 partial job refused as allocation_conflict before blocks/entities/ticks changed");
    }

    private static void installationRollback(MinecraftServer server) throws Exception {
        var data = CapsuleInstances.get(server);
        var before = data.save(new CompoundTag(), server.registryAccess());
        var keys = Set.copyOf(data.rooms.keySet());
        var jobsField = NativeCapsules.class.getDeclaredField("JOBS"); jobsField.setAccessible(true);
        var jobs = (java.util.Deque<?>)jobsField.get(null);
        check(jobs.isEmpty(), "rollback fixture runs before persisted native jobs are requeued");
        var context = StructurePieceSerializationContext.fromLevel(source(server));
        var original = StructureStart.loadStaticStart(context, marker.state.getCompound("plan").copy(), room.sourceSeed);
        check(original != null && original.isValid() && original.getPieces().size() > 1,
                "rollback fixture starts from the real selected native multi-piece graph");
        var pieces = new java.util.ArrayList<>(original.getPieces());
        // Jigsaw pieces recompute BB from template/position when decoding; move the actual
        // native piece so both its position and serialized transform describe the bad extent.
        pieces.set(0, CapsulePieceGeometry.move(pieces.getFirst(), 10000, 0, 0, context));
        var oversized = new StructureStart(original.getStructure(), original.getChunkPos(), original.getReferences(),
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(pieces));
        var badPlan = oversized.createTag(context, oversized.getChunkPos());
        var decoded = StructureStart.loadStaticStart(context, badPlan.copy(), room.sourceSeed);
        check(decoded != null && decoded.isValid() && decoded.getBoundingBox().getXSpan() > 8192,
                "rollback fixture is a valid native graph with a deliberately oversized envelope");
        boolean rejected = false;
        try {
            NativeCapsules.installVariant(player, ID, DIM, room.sourceDimension, room.sourceSeed,
                    badPlan, "f".repeat(64), search(server).signature);
        } catch (RuntimeException expected) { rejected = true; }
        check(rejected, "oversized selected native plan fails before queueing or world writes");
        check(keys.equals(data.rooms.keySet()) && before.equals(data.save(new CompoundTag(), server.registryAccess())),
                "failed selected-plan installation restores allocation keys/count/next-slot and every old room NBT");
        check(jobs.isEmpty(), "rejected selected native plan never enters the placement queue");
        LogUtils.getLogger().info("CAPSULE VARIANT INSTALL ROLLBACK PASSED: oversized exact native plan rejected; room keys, allocator cursor, old room NBT and native job queue unchanged");
    }

    /** Runs before the first server tick; no production planner task can be in flight here. */
    @SuppressWarnings("unchecked")
    private static void plannerCancellationContract() throws Exception {
        var gateField = NativeCapsules.class.getDeclaredField("PLANNER_BUSY"); gateField.setAccessible(true);
        var gate = (AtomicBoolean)gateField.get(null);
        check(!gate.get(), "planner gate is idle before synthetic lifecycle checks");
        var type = Class.forName(NativeCapsules.class.getName() + "$PlanFuture");
        var constructor = type.getDeclaredConstructor(Callable.class); constructor.setAccessible(true);
        var enteredField = type.getDeclaredField("entered"); enteredField.setAccessible(true);
        AtomicBoolean invoked = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread thread = null;
        try {
            gate.set(true);
            var queued = (FutureTask<Object>)constructor.newInstance((Callable<Object>)() -> { invoked.set(true); return null; });
            queued.cancel(true);
            check(!gate.get(), "queued cancellation releases its unused planner lease");
            gate.set(true); queued.run();
            check(gate.get() && !invoked.get(), "later running a cancelled task cannot release a newer planner lease");
            gate.set(false);

            gate.set(true);
            var beforeCallable = (FutureTask<Object>)constructor.newInstance((Callable<Object>)() -> { invoked.set(true); return null; });
            ((AtomicBoolean)enteredField.get(beforeCallable)).set(true);
            beforeCallable.cancel(true);
            check(gate.get(), "cancel between run entry and callable dispatch retains the lease until run unwinds");
            beforeCallable.run();
            check(!gate.get() && !invoked.get(), "cancelled dispatch still releases the gate even when the callable never executes");

            gate.set(true);
            var running = (FutureTask<Object>)constructor.newInstance((Callable<Object>)() -> {
                entered.countDown();
                boolean waiting = true;
                while (waiting) { try { release.await(); waiting = false; } catch (InterruptedException ignored) { } }
                return null;
            });
            thread = new Thread(running, "capsule-variant-regression-planner-lifecycle"); thread.setDaemon(true); thread.start();
            check(entered.await(1, TimeUnit.SECONDS), "synthetic native callback entered");
            running.cancel(true);
            check(gate.get(), "cancelling an interrupt-ignoring callback never unlocks concurrent native planning");
            release.countDown(); thread.join(1000);
            check(!thread.isAlive() && !gate.get(), "planner unlocks only after the interrupted worker actually returns");
        } finally {
            release.countDown(); if (thread != null) thread.join(1000); gate.set(false);
        }
    }
    private static StructureStart placedStart(MinecraftServer server) {
        var start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source(server)), room.plan.copy(), room.sourceSeed);
        check(start != null && start.isValid(), "placed selected native plan decodes"); return start;
    }
    private static String contentsDigest(MinecraftServer server) throws Exception {
        var target = target(server); var bounds = placedStart(server).getBoundingBox();
        var digest = MessageDigest.getInstance("SHA-256");
        boolean capture = contentSnapshot == null;
        if (capture) { contentSnapshot = new HashMap<>(); snapshotBounds = bounds; snapshotPlanHash = room.plan.hashCode(); }
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var state = target.getBlockState(pos);
            var nbt = canonicalBlockEntity(target, pos);
            digest.update(state.toString().getBytes(StandardCharsets.UTF_8));
            if (nbt != null) digest.update(nbt.toString().getBytes(StandardCharsets.UTF_8));
            if (capture && (!state.is(Blocks.AIR) || nbt != null)) contentSnapshot.put(pos.asLong(), new Cell(state, nbt));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static CompoundTag canonicalBlockEntity(ServerLevel target, BlockPos pos) {
        var be = target.getBlockEntity(pos);
        if (be == null) return null;
        var nbt = be.saveWithoutMetadata(target.registryAccess());
        // Ticking a spawner is legitimate world activity, unlike replaying its native contents.
        if (be instanceof SpawnerBlockEntity) nbt.remove("Delay");
        return nbt;
    }
    private static void assertContentsUnchanged(MinecraftServer server, String assertion) throws Exception {
        String current = contentsDigest(server);
        if (!beforeJourney.equals(current)) diagnoseContents(server, assertion, current);
        check(beforeJourney.equals(current), assertion + "; expected=" + beforeJourney + " actual=" + current);
    }
    private static void diagnoseContents(MinecraftServer server, String assertion, String currentHash) {
        var target = target(server); var currentBounds = placedStart(server).getBoundingBox();
        LogUtils.getLogger().error("CAPSULE VARIANT CONTENT DIFF assertion={} old_hash={} new_hash={} old_bbox={} current_bbox={} old_plan_hash={} current_plan_hash={} old_room={} neighbor={}",
                assertion, beforeJourney, currentHash, snapshotBounds, currentBounds, snapshotPlanHash, room.plan.hashCode(),
                roomDescription(room), roomDescription(neighbor));
        int differences = 0;
        for (BlockPos pos : BlockPos.betweenClosed(snapshotBounds.minX(), snapshotBounds.minY(), snapshotBounds.minZ(),
                snapshotBounds.maxX(), snapshotBounds.maxY(), snapshotBounds.maxZ())) {
            Cell before = contentSnapshot.get(pos.asLong());
            var oldState = before == null ? Blocks.AIR.defaultBlockState() : before.state();
            var oldNbt = before == null ? null : before.nbt();
            var newState = target.getBlockState(pos); var newNbt = canonicalBlockEntity(target, pos);
            if (!oldState.equals(newState) || !java.util.Objects.equals(oldNbt, newNbt)) {
                if (++differences <= 20) LogUtils.getLogger().error("CAPSULE VARIANT CELL DIFF pos={} old_state={} new_state={} old_nbt={} new_nbt={}",
                        pos.immutable(), oldState, newState, oldNbt, newNbt);
            }
        }
        LogUtils.getLogger().error("CAPSULE VARIANT CONTENT DIFF TOTAL {} changed cells inside original envelope", differences);
    }
    private static String roomDescription(CapsuleInstances.Instance value) {
        if (value == null) return "none";
        int margin = CapsuleInstances.reservedMargin();
        return "id=" + value.instanceId + " origin=" + value.x() + "," + value.z()
                + " envelope=[" + value.minX + "," + value.minY + "," + value.minZ + " -> " + value.maxX + "," + value.maxY + "," + value.maxZ + "]"
                + " reservedXZ=[" + (value.minX - margin) + "," + (value.minZ - margin) + " -> "
                + (value.maxX + margin) + "," + (value.maxZ + margin) + "] phase=" + value.phase;
    }
    private record Cell(net.minecraft.world.level.block.state.BlockState state, CompoundTag nbt) { }
    private static CapsuleVariantsData.Search search(MinecraftServer server) { return CapsuleVariantsData.get(server).searches.get(KEY); }
    private static CapsuleInstances.Instance room(MinecraftServer server) {
        return CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, ID, DIM, marker.state.getInt("instance")));
    }
    private static ServerLevel source(MinecraftServer server) { return server.getLevel(net.minecraft.world.level.Level.NETHER); }
    private static ServerLevel target(MinecraftServer server) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(DIM))); }
    private static ServerPlayer newPlayer(ServerLevel level) {
        return newPlayer(level, OWNER, "CapsuleVariants");
    }
    private static ServerPlayer newPlayer(ServerLevel level, UUID owner, String name) {
        var server = level.getServer(); var profile = new GameProfile(owner, name);
        var result = new ServerPlayer(server, level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
        };
        new io.netty.channel.embedded.EmbeddedChannel(transport);
        result.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, transport, result,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
        };
        return result;
    }
    private static void stop(MinecraftServer server) { finished = true; server.halt(false); }
    private static void fail(MinecraftServer server, Throwable failure) {
        LogUtils.getLogger().error("CAPSULE VARIANTS CHECKS FAILED phase={} stage={} ticks={}", marker == null ? -1 : marker.state.getInt("phase"), stage, ticks, failure);
        stop(server);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Marker extends SavedData {
        CompoundTag state = new CompoundTag();
        static Marker load(CompoundTag tag, HolderLookup.Provider ignored) { var result = new Marker(); result.state = tag; return result; }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider ignored) { return state.copy(); }
    }
}
