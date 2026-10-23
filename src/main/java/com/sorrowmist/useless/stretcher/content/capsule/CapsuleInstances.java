package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native structure plans and completed work survive normal server saves and restarts. */
public final class CapsuleInstances extends SavedData {
    /** Retained only when reading older saves without an explicit origin. */
    private static final int ROOM_SPACING = 16_448;
    private static final int LEGACY_ROOM_SPACING = 8_192;
    /** Keeps the 8-block fluid-proof room clearance separate from adjacent structures. */
    private static final int ROOM_MARGIN = 16;
    private static final int PROVISIONAL_HALF_EXTENT = 16;
    final Map<String, Instance> rooms = new LinkedHashMap<>();
    private int nextSlot;
    public static CapsuleInstances get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CapsuleInstances::new,
                CapsuleInstances::load), "useless_stretcher_native_capsules");
    }
    static String key(UUID owner, String structure, String dimension) { return key(owner, structure, dimension, 0); }
    static String key(UUID owner, String structure, String dimension, int instanceId) {
        return owner + "|" + structure + "|" + dimension + "|" + Math.max(0, instanceId);
    }
    Instance getOrCreate(UUID owner, String structure, String dimension) {
        Instance existing = rooms.get(key(owner, structure, dimension, 0));
        return existing != null ? existing : create(owner, structure, dimension, 0);
    }
    /**
     * Allocates the next room after the instance selected in the UI. The requested id makes
     * repeated packets idempotent while the screen is waiting for the first response.
     */
    Instance getOrCreateAdjacent(UUID owner, String structure, String dimension, int afterInstanceId) {
        int instanceId = Math.max(0, afterInstanceId) + 1;
        Instance existing = rooms.get(key(owner, structure, dimension, instanceId));
        return existing != null ? existing : create(owner, structure, dimension, instanceId);
    }

    /** Copy the environment contract when a player explicitly asks for a second room. */
    void inheritSource(Instance room, Instance parent) {
        if (room == null || parent == null || parent.sourceDimension.isEmpty()) return;
        room.sourceDimension = parent.sourceDimension;
        room.sourceSeed = parent.sourceSeed;
        room.sourceBiome = parent.sourceBiome;
        room.sourceEffects = parent.sourceEffects;
        room.sourceMinY = parent.sourceMinY;
        room.sourceHeight = parent.sourceHeight;
        setDirty();
    }

    /** Record the real source level used to assemble this native structure. */
    void bindSource(Instance room, ServerLevel source) {
        if (room == null || source == null) return;
        String dimension = source.dimension().location().toString();
        String biome = source.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                        room.x() >> 2, source.getSeaLevel() >> 2, room.z() >> 2,
                        source.getChunkSource().randomState().sampler())
                .unwrapKey().map(key -> key.location().toString()).orElse("");
        String effects = source.dimensionType().effectsLocation().toString();
        if (dimension.equals(room.sourceDimension) && room.sourceSeed == source.getSeed()
                && biome.equals(room.sourceBiome) && effects.equals(room.sourceEffects)
                && room.sourceMinY == source.getMinBuildHeight() && room.sourceHeight == source.getHeight()) return;
        room.sourceDimension = dimension;
        room.sourceSeed = source.getSeed();
        room.sourceBiome = biome;
        room.sourceEffects = effects;
        room.sourceMinY = source.getMinBuildHeight();
        room.sourceHeight = source.getHeight();
        setDirty();
    }
    private Instance create(UUID owner, String structure, String dimension, int instanceId) {
        if (nextSlot >= 10_000) throw new IllegalStateException("Capsule allocation exhausted");
        if (rooms.containsKey(key(owner, structure, dimension, instanceId)))
            throw new IllegalStateException("Capsule instance id is already allocated");
        Instance room = new Instance(owner, structure, dimension, instanceId, nextSlot++, false);
        allocateAdjacent(room);
        rooms.put(room.key(), room); setDirty(); return room;
    }
    int instanceCount(UUID owner, String structure, String dimension) {
        return (int) rooms.values().stream().filter(room -> room.owner.equals(owner)
                && room.structure.equals(structure) && room.dimension.equals(dimension)).count();
    }

    private void allocateAdjacent(Instance room) {
        List<Instance> neighbors = rooms.values().stream()
                .filter(existing -> existing != room && existing.dimension.equals(room.dimension))
                .sorted(java.util.Comparator.comparingInt((Instance existing) -> existing.slot).reversed()).toList();
        if (neighbors.isEmpty()) return;

        Box oldBounds = boxFor(room);
        int width = oldBounds.width();
        int depth = oldBounds.depth();
        int anchorX = room.boundsKnown ? oldBounds.centerX() : room.originX;
        int anchorZ = room.boundsKnown ? oldBounds.centerZ() : room.originZ;
        Box best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Instance neighbor : neighbors) {
            Box occupied = boxFor(neighbor);
            for (Box candidate : adjacentBoxes(occupied, width, depth)) {
                if (!isFree(room.dimension, candidate, room)) continue;
                long dx = (long) candidate.centerX() - anchorX;
                long dz = (long) candidate.centerZ() - anchorZ;
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) throw new IllegalStateException("No adjacent capsule allocation available");
        moveTo(room, best);
    }

    private void moveTo(Instance room, Box destination) {
        int dx = destination.centerX() - (room.boundsKnown ? (room.minX + room.maxX) / 2 : room.originX);
        int dz = destination.centerZ() - (room.boundsKnown ? (room.minZ + room.maxZ) / 2 : room.originZ);
        room.originX += dx;
        room.originZ += dz;
        if (room.boundsKnown) {
            room.minX += dx; room.maxX += dx;
            room.minZ += dz; room.maxZ += dz;
        }
    }

    private boolean isFree(String dimension, Box candidate, Instance excluded) {
        for (Instance existing : rooms.values()) {
            if (existing == excluded || !existing.dimension.equals(dimension)) continue;
            if (overlapsWithMargin(candidate, boxFor(existing))) return false;
        }
        return true;
    }

    boolean needsRelocation(Instance room) {
        if (!room.boundsKnown) return false;
        Box current = boxFor(room);
        for (Instance existing : rooms.values()) {
            if (existing == room || !existing.dimension.equals(room.dimension)) continue;
            Box other = boxFor(existing);
            if (overlaps(current, other)) return true;
        }
        // A native StructureStart can be offset from the requested generation anchor by
        // the structure set's biome search and jigsaw rules.  The anchor allocator still
        // places explicit new instances beside the nearest room, but a completed native
        // plan is safe to keep wherever its actual bounds land as long as it does not
        // overlap another registered instance.  Replanning solely to close a gap can
        // oscillate forever for structures whose native anchor is not deterministic.
        return false;
    }

    boolean overlapsAnother(Instance room) {
        Box bounds = boxFor(room);
        return rooms.values().stream().anyMatch(existing -> existing != room
                && existing.dimension.equals(room.dimension) && overlaps(bounds, boxFor(existing)));
    }

    /** Move a newly planned structure beside an existing room when biome search placed it far away. */
    int[] snapBesideExisting(Instance room) {
        if (!room.boundsKnown) return new int[]{0, 0};
        boolean hasNeighbor = rooms.values().stream()
                .anyMatch(existing -> existing != room && existing.dimension.equals(room.dimension));
        if (!hasNeighbor || isAdjacentToExisting(room) && !overlapsAnother(room)) return new int[]{0, 0};
        int oldMinX = room.minX;
        int oldMinZ = room.minZ;
        allocateAdjacent(room);
        return new int[]{room.minX - oldMinX, room.minZ - oldMinZ};
    }

    boolean isAdjacentToExisting(Instance room) {
        Box bounds = boxFor(room);
        return rooms.values().stream().anyMatch(existing -> existing != room
                && existing.dimension.equals(room.dimension)
                && horizontalGap(bounds, boxFor(existing)) <= ROOM_MARGIN + 1);
    }

    void relocateAdjacent(Instance room) {
        if (!room.boundsKnown) throw new IllegalStateException("Cannot relocate an unplanned capsule");
        allocateAdjacent(room);
        setDirty();
    }

    static boolean adjacent(Instance first, Instance second) {
        // allocateAdjacent leaves ROOM_MARGIN + 1 empty blocks between structure envelopes.
        return horizontalGap(boxFor(first), boxFor(second)) <= ROOM_MARGIN + 1;
    }

    static boolean boundsOverlap(Instance first, Instance second) {
        return overlaps(boxFor(first), boxFor(second));
    }

    /** Test-only geometry constants kept next to the allocator so assertions cannot drift. */
    static int reservedHalfExtent() { return PROVISIONAL_HALF_EXTENT; }
    static int reservedMargin() { return ROOM_MARGIN; }

    private static Box boxFor(Instance room) {
        if (room.boundsKnown) return new Box(room.minX, room.minZ, room.maxX, room.maxZ);
        return new Box(room.originX - PROVISIONAL_HALF_EXTENT, room.originZ - PROVISIONAL_HALF_EXTENT,
                room.originX + PROVISIONAL_HALF_EXTENT, room.originZ + PROVISIONAL_HALF_EXTENT);
    }

    private static List<Box> adjacentBoxes(Box existing, int width, int depth) {
        int centerX = existing.centerX() - (width - 1) / 2;
        int centerZ = existing.centerZ() - (depth - 1) / 2;
        // Keep the generated rooms face-to-face with aligned centers. Edge-aligned
        // candidates form diagonal/corner neighbors and make room selection unpredictable.
        int[] xAlignments = new int[]{centerX};
        int[] zAlignments = new int[]{centerZ};
        List<Box> candidates = new java.util.ArrayList<>(12);
        int leftMax = existing.minX - ROOM_MARGIN - 1;
        int rightMin = existing.maxX + ROOM_MARGIN + 1;
        int northMax = existing.minZ - ROOM_MARGIN - 1;
        int southMin = existing.maxZ + ROOM_MARGIN + 1;
        for (int z : zAlignments) {
            candidates.add(new Box(rightMin, z, rightMin + width - 1, z + depth - 1));
            candidates.add(new Box(leftMax - width + 1, z, leftMax, z + depth - 1));
        }
        for (int x : xAlignments) {
            candidates.add(new Box(x, southMin, x + width - 1, southMin + depth - 1));
            candidates.add(new Box(x, northMax - depth + 1, x + width - 1, northMax));
        }
        return candidates;
    }

    private static boolean overlapsWithMargin(Box first, Box second) {
        return first.minX <= second.maxX + ROOM_MARGIN && first.maxX + ROOM_MARGIN >= second.minX
                && first.minZ <= second.maxZ + ROOM_MARGIN && first.maxZ + ROOM_MARGIN >= second.minZ;
    }

    private static boolean overlaps(Box first, Box second) {
        return first.minX <= second.maxX && first.maxX >= second.minX
                && first.minZ <= second.maxZ && first.maxZ >= second.minZ;
    }

    private static int horizontalGap(Box first, Box second) {
        int x = first.maxX < second.minX ? second.minX - first.maxX - 1
                : second.maxX < first.minX ? first.minX - second.maxX - 1 : 0;
        int z = first.maxZ < second.minZ ? second.minZ - first.maxZ - 1
                : second.maxZ < first.minZ ? first.minZ - second.maxZ - 1 : 0;
        return x + z;
    }

    private record Box(int minX, int minZ, int maxX, int maxZ) {
        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
        int centerX() { return minX + (maxX - minX) / 2; }
        int centerZ() { return minZ + (maxZ - minZ) / 2; }
    }
    static final class Instance {
        final UUID owner;
        final String structure, dimension;
        final int instanceId;
        final int slot;
        /**
         * Native generation is assembled against a real loaded ServerLevel.  Keep the
         * selected source with the room so a restart cannot silently switch a custom
         * dimension, generator or seed underneath an already persisted Piece graph.
         */
        String sourceDimension = "";
        long sourceSeed;
        String sourceBiome = "";
        String sourceEffects = "";
        int sourceMinY;
        int sourceHeight;
        boolean sourceCoordinates;
        int sourceOffsetX, sourceOffsetY, sourceOffsetZ;
        private int originX, originZ;
        CompoundTag plan = new CompoundTag();
        int chunk, piece, mapCursor, previewCursor, attempt, cleanChunk, trimChunk, trimPass, bedrockChunk;
        int cleanupVersion;
        int geometryVersion;
        int progress;
        long cleanCursor, trimCursor, bedrockCursor;
        int minX, minY, minZ, maxX, maxY, maxZ;
        boolean ready, afterPlaced, terrainCleaned, cleanupEntitiesRemoved, boundsKnown;
        boolean completionNotified;
        boolean generating;
        /** Versioned marker for the complete XZ bedrock projection beneath the structure. */
        int bedrockVersion;
        int relocations;
        String phase = "planning";
        volatile boolean planningOffThread;
        String error = "";
        final int[] pixels = new int[64 * 64];
        Instance(UUID owner, String structure, String dimension, int instanceId, int slot, boolean legacyOrigin) {
            this.owner = owner; this.structure = structure; this.dimension = dimension;
            this.instanceId = Math.max(0, instanceId); this.slot = slot;
            int spacing = legacyOrigin ? LEGACY_ROOM_SPACING : ROOM_SPACING;
            this.originX = 2_000_000 + (slot % 100) * spacing;
            this.originZ = 2_000_000 + (slot / 100) * spacing;
        }
        String key() { return CapsuleInstances.key(owner, structure, dimension, instanceId); }
        int x() { return originX; }
        int z() { return originZ; }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", owner); tag.putString("structure", structure); tag.putString("dimension", dimension);
            tag.putInt("instance_id", instanceId);
            tag.putString("source_dimension", sourceDimension);
            tag.putLong("source_seed", sourceSeed);
            tag.putString("source_biome", sourceBiome);
            tag.putString("source_effects", sourceEffects);
            tag.putInt("source_min_y", sourceMinY);
            tag.putInt("source_height", sourceHeight);
            tag.putBoolean("source_coordinates", sourceCoordinates);
            tag.putIntArray("source_offset", new int[]{sourceOffsetX, sourceOffsetY, sourceOffsetZ});
            tag.putInt("slot", slot); tag.putInt("origin_x", originX); tag.putInt("origin_z", originZ);
            tag.put("plan", plan); tag.putInt("chunk", chunk); tag.putInt("piece", piece);
            tag.putInt("map_cursor", mapCursor); tag.putInt("preview_cursor", previewCursor);
            tag.putLong("clean_cursor", cleanCursor); tag.putLong("bedrock_cursor", bedrockCursor);
            tag.putLong("trim_cursor", trimCursor);
            tag.putInt("clean_chunk", cleanChunk); tag.putInt("trim_chunk", trimChunk); tag.putInt("trim_pass", trimPass);
            tag.putInt("bedrock_chunk", bedrockChunk);
            tag.putInt("cleanup_version", cleanupVersion);
            tag.putInt("geometry_version", geometryVersion);
            tag.putInt("progress", progress);
            tag.putBoolean("after_placed", afterPlaced);
            tag.putBoolean("completion_notified", completionNotified);
            tag.putBoolean("generating", generating);
            tag.putBoolean("terrain_cleaned", terrainCleaned); tag.putBoolean("cleanup_entities_removed", cleanupEntitiesRemoved);
            tag.putBoolean("bounds_known", boundsKnown); tag.putInt("relocations", relocations);
            tag.putInt("bedrock_version", bedrockVersion);
            tag.putBoolean("planning_off_thread", planningOffThread);
            tag.putInt("attempt", attempt); tag.putBoolean("ready", ready); tag.putString("phase", phase);
            tag.putIntArray("pixels", pixels); tag.putString("error", error);
            tag.putIntArray("bounds", new int[]{minX, minY, minZ, maxX, maxY, maxZ}); return tag;
        }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("next", nextSlot);
        ListTag entries = new ListTag(); rooms.values().forEach(room -> entries.add(room.save()));
        tag.put("rooms", entries); return tag;
    }
    private static CapsuleInstances load(CompoundTag tag, HolderLookup.Provider provider) {
        CapsuleInstances data = new CapsuleInstances(); data.nextSlot = Math.max(0, tag.getInt("next"));
        boolean migrated = false;
        for (Tag element : tag.getList("rooms", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) element;
            if (!e.hasUUID("owner") || e.getInt("slot") < 0 || e.getInt("slot") >= 10_000) continue;
            boolean hasOrigin = e.contains("origin_x", Tag.TAG_INT) && e.contains("origin_z", Tag.TAG_INT);
            // Saves written by the three-part key format are migrated to instance zero.
            int instanceId = e.contains("instance_id", Tag.TAG_INT) ? Math.max(0, e.getInt("instance_id")) : 0;
            UUID owner = e.getUUID("owner");
            String structure = e.getString("structure");
            String storedDimension = e.getString("dimension");
            String dimension = migrateTargetDimension(storedDimension);
            migrated |= !storedDimension.equals(dimension);
            // A legacy save can contain both the old three-part key and a newer instance
            // record.  Pick the first free id before constructing the immutable record.
            while (data.rooms.containsKey(key(owner, structure, dimension, instanceId))) instanceId++;
            Instance room = new Instance(owner, structure, dimension,
                    instanceId, e.getInt("slot"), !hasOrigin);
            room.sourceDimension = e.getString("source_dimension");
            room.sourceSeed = e.getLong("source_seed");
            room.sourceBiome = e.getString("source_biome");
            room.sourceEffects = e.getString("source_effects");
            room.sourceMinY = e.getInt("source_min_y");
            room.sourceHeight = e.getInt("source_height");
            int[] sourceOffset = e.getIntArray("source_offset");
            if (e.getBoolean("source_coordinates") && sourceOffset.length == 3) {
                room.sourceCoordinates = true;
                room.sourceOffsetX = sourceOffset[0]; room.sourceOffsetY = sourceOffset[1]; room.sourceOffsetZ = sourceOffset[2];
            }
            if (hasOrigin) {
                room.originX = e.getInt("origin_x");
                room.originZ = e.getInt("origin_z");
            }
            room.plan = e.getCompound("plan"); room.chunk = Math.max(0, e.getInt("chunk"));
            room.piece = Math.max(0, e.getInt("piece")); room.attempt = Math.max(0, e.getInt("attempt"));
            room.cleanCursor = Math.max(0L, e.getLong("clean_cursor"));
            room.trimCursor = Math.max(0L, e.getLong("trim_cursor"));
            room.bedrockCursor = Math.max(0L, e.getLong("bedrock_cursor"));
            room.cleanChunk = Math.max(0, e.getInt("clean_chunk"));
            room.cleanupVersion = Math.max(0, e.getInt("cleanup_version"));
            room.geometryVersion = Math.max(0, e.getInt("geometry_version"));
            room.progress = Math.clamp(e.getInt("progress"), 0, 99);
            room.trimChunk = Math.max(0, e.getInt("trim_chunk"));
            room.trimPass = Math.clamp(e.getInt("trim_pass"), 0, 7);
            room.bedrockChunk = Math.max(0, e.getInt("bedrock_chunk"));
            room.mapCursor = Math.clamp(e.getInt("map_cursor"), 0, 4096); room.ready = e.getBoolean("ready");
            room.completionNotified = e.contains("completion_notified") ? e.getBoolean("completion_notified") : room.ready;
            room.previewCursor = Math.clamp(e.getInt("preview_cursor"), 0, 4096);
            room.afterPlaced = e.getBoolean("after_placed");
            room.terrainCleaned = e.getBoolean("terrain_cleaned");
            room.cleanupEntitiesRemoved = e.getBoolean("cleanup_entities_removed");
            room.boundsKnown = e.getBoolean("bounds_known"); room.relocations = Math.max(0, e.getInt("relocations"));
            room.bedrockVersion = Math.max(0, e.getInt("bedrock_version"));
            room.phase = e.contains("phase", Tag.TAG_STRING) ? e.getString("phase") : (room.ready ? "ready" : "placing");
            room.planningOffThread = e.getBoolean("planning_off_thread");
            room.error = e.getString("error"); int[] pixels = e.getIntArray("pixels");
            room.generating = e.contains("generating") ? e.getBoolean("generating")
                    : !room.ready && room.error.isEmpty() && !room.plan.isEmpty();
            if (pixels.length == 4096) System.arraycopy(pixels, 0, room.pixels, 0, pixels.length);
            int[] bounds = e.getIntArray("bounds");
            if (bounds.length == 6) {
                room.minX = bounds[0]; room.minY = bounds[1]; room.minZ = bounds[2];
                room.maxX = bounds[3]; room.maxY = bounds[4]; room.maxZ = bounds[5];
                room.boundsKnown |= !room.plan.isEmpty() || java.util.Arrays.stream(bounds).anyMatch(value -> value != 0);
            }
            data.rooms.put(room.key(), room); data.nextSlot = Math.max(data.nextSlot, room.slot + 1);
        }
        if (migrated) data.setDirty();
        return data;
    }

    private static String migrateTargetDimension(String dimension) {
        String normalized = switch (dimension) {
            case "minecraft:the_nether" -> "useless_stretcher:capsule_nether";
            case "minecraft:the_end" -> "useless_stretcher:capsule_end";
            case "minecraft:overworld", "useless_stretcher:two_i_starfield" ->
                    "useless_stretcher:capsule_overworld";
            default -> dimension;
        };
        if (normalized.equals(dimension) && !dimension.startsWith("useless_stretcher:capsule_")) {
            String path = dimension.substring(dimension.indexOf(':') + 1);
            if (path.contains("nether") || path.contains("hell")) return "useless_stretcher:capsule_nether";
            if (path.contains("end")) return "useless_stretcher:capsule_end";
            return "useless_stretcher:capsule_overworld";
        }
        return normalized;
    }
}
