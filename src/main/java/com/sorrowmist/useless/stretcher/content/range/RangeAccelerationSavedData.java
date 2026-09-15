package com.sorrowmist.useless.stretcher.content.range;

import appeng.api.networking.IInWorldGridNodeHost;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.ChangedTickAccessor;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-owned acceleration fields. No range configuration or work queue is attached to a player. */
public final class RangeAccelerationSavedData extends net.minecraft.world.level.saveddata.SavedData {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 15;
    public static final int MAX_FILTERS = 128;
    public static final int MAX_MARKED_POSITIONS = MAX_SIZE * MAX_SIZE * MAX_SIZE;
    public static final int MAX_ACTIVE_FIELDS_PER_OWNER = 64;
    public static final int MAX_HISTORY_PER_OWNER = 512;
    private static final String FILE_ID = "useless_stretcher_range_acceleration";
    private static final int MAX_EXECUTIONS_PER_TARGET = WondrousStaffAccelerationEntity.MAX_MULTIPLIER;
    /**
     * A malformed or simply over-ambitious setup must not turn one server tick into an
     * unbounded amount of block-entity work. The budget is shared by all loaded fields and is
     * deliberately high enough for ordinary x1024 use; excess work remains in each target's
     * pending counter and is serviced on later ticks.
     */
    private static final int MAX_EXECUTIONS_PER_SERVER_TICK = 1_000_000;
    private static final long MAX_PENDING_TICKS = 8192L;
    private static final long PLACEMENT_COOLDOWN_TICKS = 5L;
    private static final int IDLE_WINDOW_TICKS = 100;
    private static final int IDLE_EXECUTIONS_PER_TICK = 4;
    private static final int CHANGED_WINDOW_TICKS = 60;
    private static final int TARGET_RESCAN_TICKS = 20;

    private final Map<UUID, Field> fields = new LinkedHashMap<>();
    private final Map<UUID, FieldRuntime> runtime = new HashMap<>();
    private final Map<UUID, Long> lastPlacementTicks = new HashMap<>();
    /** Runtime-only cursor so a busy field cannot permanently starve later fields. */
    private int executionCursor;

    public static RangeAccelerationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(RangeAccelerationSavedData::new, RangeAccelerationSavedData::load), FILE_ID);
    }

    public boolean acquirePlacement(UUID owner, long gameTime) {
        Long previous = lastPlacementTicks.get(owner);
        if (previous != null && gameTime >= previous
                && gameTime - previous < PLACEMENT_COOLDOWN_TICKS) {
            return false;
        }
        lastPlacementTicks.put(owner, gameTime);
        return true;
    }

    public PlacementResult place(ServerLevel level, ServerPlayer owner, BlockPos center, ItemStack staff) {
        List<Field> samePosition = fields.values().stream()
                .filter(field -> field.dimension.equals(level.dimension().location())
                        && field.center.equals(center))
                .toList();
        if (samePosition.stream().anyMatch(field -> !field.owner.equals(owner.getUUID()))) {
            return new PlacementResult(PlacementStatus.OCCUPIED, null);
        }
        long activeCount = fields.values().stream()
                .filter(field -> field.owner.equals(owner.getUUID()) && field.enabled)
                .count();
        if (!samePosition.isEmpty()) {
            Field existing = samePosition.getFirst();
            if (!existing.enabled && activeCount >= MAX_ACTIVE_FIELDS_PER_OWNER) {
                return new PlacementResult(PlacementStatus.LIMIT_REACHED, null);
            }
            for (int i = 1; i < samePosition.size(); i++) {
                Field duplicate = samePosition.get(i);
                fields.remove(duplicate.id);
                runtime.remove(duplicate.id);
            }
            existing.updateFrom(staff);
            runtime.remove(existing.id);
            setDirty();
            return new PlacementResult(PlacementStatus.UPDATED, existing.id);
        }
        if (activeCount >= MAX_ACTIVE_FIELDS_PER_OWNER) {
            return new PlacementResult(PlacementStatus.LIMIT_REACHED, null);
        }

        UUID id = UUID.randomUUID();
        Field field = new Field(
                id,
                owner.getUUID(),
                level.dimension().location(),
                center.immutable(),
                System.currentTimeMillis(),
                true,
                Math.max(1, Math.min(1024, WondrousStaffAcceleration.getSpeed(staff))),
                RangeAccelerationSettings.sizeX(staff),
                RangeAccelerationSettings.sizeY(staff),
                RangeAccelerationSettings.sizeZ(staff),
                RangeAccelerationSettings.whitelistMode(staff),
                RangeAccelerationSettings.sleepWhitelistMode(staff),
                List.of(), List.of(), List.of(), true);
        fields.put(id, field);
        pruneHistory(owner.getUUID());
        setDirty();
        return new PlacementResult(PlacementStatus.CREATED, id);
    }

    private void pruneHistory(UUID owner) {
        List<Field> owned = fields.values().stream()
                .filter(field -> field.owner.equals(owner))
                .sorted(Comparator.comparingLong(Field::createdAt))
                .toList();
        int removeCount = owned.size() - MAX_HISTORY_PER_OWNER;
        for (Field field : owned) {
            if (removeCount <= 0) break;
            if (field.enabled) continue;
            fields.remove(field.id);
            runtime.remove(field.id);
            removeCount--;
        }
    }

    public void remove(UUID id) {
        if (fields.remove(id) != null) {
            runtime.remove(id);
            setDirty();
        }
    }

    /** Permanently removes an owned field and its loaded visual marker. */
    public Summary reclaim(MinecraftServer server, UUID owner, UUID id) {
        Field field = fields.get(id);
        if (field == null || !field.owner.equals(owner)) return null;

        Summary summary = field.summary();
        fields.remove(id);
        runtime.remove(id);
        setDirty();

        ServerLevel level = server.getLevel(field.dimensionKey());
        if (level != null && level.getEntity(id) instanceof TimeFlowEntity marker) {
            marker.discard();
        }
        return summary;
    }

    public Field getField(UUID id) {
        return fields.get(id);
    }

    /**
     * Applies a label-gun-style toggle to one owned, enabled field. A normal click affects only
     * the target position; Ctrl affects every valid target of the same block type in that field.
     */
    public MarkResult toggleMarks(ServerLevel level, UUID owner, BlockPos target,
                                  boolean sleepList, boolean sameType, boolean whitelistMode) {
        Field field = fields.values().stream()
                .filter(candidate -> candidate.enabled
                        && candidate.owner.equals(owner)
                        && candidate.dimension.equals(level.dimension().location())
                        && candidate.contains(target))
                .max(Comparator.comparingLong(Field::createdAt))
                .orElse(null);
        if (field == null) return new MarkResult(MarkStatus.NO_RANGE, false, 0, 0, null);
        if (!WondrousStaffAcceleration.isValidTarget(level, target)) {
            return new MarkResult(MarkStatus.INVALID_TARGET, false, 0, 0, field);
        }

        field.migrateLegacyFilters(level);
        field.setWhitelistMode(sleepList, whitelistMode);
        List<BlockPos> targets = sameType ? field.sameTypeTargets(level, target) : List.of(target.immutable());
        Set<Long> marks = sleepList ? field.sleepMarks : field.accelerationMarks;
        boolean add = targets.stream().mapToLong(BlockPos::asLong).anyMatch(value -> !marks.contains(value));
        long missing = targets.stream().mapToLong(BlockPos::asLong).filter(value -> !marks.contains(value)).count();
        if (add && marks.size() + missing > MAX_MARKED_POSITIONS) {
            return new MarkResult(MarkStatus.FULL, false, 0, marks.size(), field);
        }

        int affected = 0;
        for (BlockPos pos : targets) {
            boolean changed = add ? marks.add(pos.asLong()) : marks.remove(pos.asLong());
            if (changed) affected++;
        }
        field.revision++;
        runtime.remove(field.id);
        setDirty();
        if (level.getEntity(field.id) instanceof TimeFlowEntity marker) marker.sync(field);
        return new MarkResult(MarkStatus.CHANGED, add, affected, marks.size(), field);
    }

    public List<Summary> history(UUID owner) {
        return fields.values().stream()
                .filter(field -> field.owner.equals(owner))
                .sorted(Comparator.comparingLong(Field::createdAt).reversed())
                .map(Field::summary)
                .toList();
    }

    public boolean setEnabled(MinecraftServer server, UUID owner, UUID id, boolean enabled) {
        Field field = fields.get(id);
        if (field == null || !field.owner.equals(owner)) return false;
        if (enabled && !field.enabled) {
            long activeCount = fields.values().stream()
                    .filter(candidate -> candidate.owner.equals(owner) && candidate.enabled)
                    .count();
            boolean occupied = fields.values().stream()
                    .anyMatch(candidate -> candidate != field && candidate.enabled
                            && candidate.dimension.equals(field.dimension)
                            && candidate.center.equals(field.center));
            if (activeCount >= MAX_ACTIVE_FIELDS_PER_OWNER || occupied) return false;
        }
        field.enabled = enabled;
        field.idleThrottled = false;
        field.revision++;
        runtime.remove(id);
        setDirty();

        ServerLevel level = server.getLevel(field.dimensionKey());
        if (level != null && level.getEntity(id) instanceof TimeFlowEntity marker) {
            marker.sync(field);
        }
        return true;
    }

    public void tick(MinecraftServer server) {
        if (fields.isEmpty()) return;
        List<Field> snapshot = List.copyOf(fields.values());
        int start = Math.floorMod(executionCursor++, snapshot.size());
        ExecutionBudget budget = new ExecutionBudget(MAX_EXECUTIONS_PER_SERVER_TICK);
        for (int offset = 0; offset < snapshot.size(); offset++) {
            Field field = snapshot.get((start + offset) % snapshot.size());
            ServerLevel level = server.getLevel(field.dimensionKey());
            if (level == null || !level.isLoaded(field.center)) continue;
            ensureMarker(level, field);
            if (!field.enabled) continue;
            tickField(level, field, budget);
        }
    }

    private static void ensureMarker(ServerLevel level, Field field) {
        if (level.getEntity(field.id) instanceof TimeFlowEntity) return;
        TimeFlowEntity marker = new TimeFlowEntity(level, field.id);
        marker.sync(field);
        level.addFreshEntity(marker);
    }

    private void tickField(ServerLevel level, Field field, ExecutionBudget budget) {
        if (field.migrateLegacyFilters(level)) setDirty();
        FieldRuntime state = runtime.computeIfAbsent(field.id, ignored -> new FieldRuntime());
        long now = level.getGameTime();
        if (state.revision != field.revision || now - state.lastScanTick >= TARGET_RESCAN_TICKS) {
            state.targets = scanTargets(level, field);
            state.revision = field.revision;
            state.lastScanTick = now;
            Set<Long> retained = new LinkedHashSet<>();
            for (BlockPos target : state.targets) retained.add(target.asLong());
            state.targetWork.keySet().removeIf(key -> !retained.contains(key));
        }

        boolean anyTargetThrottled = false;
        int targetCount = state.targets.size();
        int targetStart = targetCount == 0 ? 0 : Math.floorMod(state.targetCursor, targetCount);
        int nextTarget = targetStart;
        for (int offset = 0; offset < targetCount; offset++) {
            int targetIndex = (targetStart + offset) % targetCount;
            BlockPos target = state.targets.get(targetIndex);
            if (!level.isLoaded(target)) continue;
            BlockState targetState = level.getBlockState(target);
            if (!field.matches(target, targetState) || !WondrousStaffAcceleration.isValidTarget(level, target)) continue;

            TargetWork work = state.targetWork.computeIfAbsent(target.asLong(), ignored -> new TargetWork());
            boolean throttled = field.allowsSleep(target) && StretcherConfig.idleThrottle()
                    && work.shouldThrottle(level, target, targetState);
            if (throttled) {
                anyTargetThrottled = true;
                work.pendingTicks = 0L;
                int executions = budget.take(IDLE_EXECUTIONS_PER_TICK);
                if (executions > 0) {
                    WondrousStaffAcceleration.tickTarget(level, target, executions);
                    nextTarget = (targetIndex + 1) % targetCount;
                }
                continue;
            }

            work.pendingTicks = Math.min(MAX_PENDING_TICKS, work.pendingTicks + field.speed);
            int requested = (int) Math.min(work.pendingTicks, MAX_EXECUTIONS_PER_TARGET);
            int executions = budget.take(requested);
            work.pendingTicks -= executions;
            if (executions > 0) {
                WondrousStaffAcceleration.tickTarget(level, target, executions);
                nextTarget = (targetIndex + 1) % targetCount;
            }
        }
        state.targetCursor = nextTarget;
        // The marker exposes a range-level status. If even one target is reduced, gold is used so
        // the player is never told the whole field is running at full speed while part of it sleeps.
        field.idleThrottled = anyTargetThrottled;
    }

    /** Mutable per-tick work allowance shared by every enabled field. */
    private static final class ExecutionBudget {
        private int remaining;

        private ExecutionBudget(int amount) {
            remaining = Math.max(0, amount);
        }

        private int take(int requested) {
            int granted = Math.min(Math.max(0, requested), remaining);
            remaining -= granted;
            return granted;
        }
    }

    private static List<BlockPos> scanTargets(ServerLevel level, Field field) {
        List<BlockPos> targets = new ArrayList<>();
        int startX = field.center.getX() - (field.sizeX - 1) / 2;
        int startY = field.center.getY() - (field.sizeY - 1) / 2;
        int startZ = field.center.getZ() - (field.sizeZ - 1) / 2;
        for (int x = 0; x < field.sizeX; x++) {
            for (int y = 0; y < field.sizeY; y++) {
                for (int z = 0; z < field.sizeZ; z++) {
                    BlockPos target = new BlockPos(startX + x, startY + y, startZ + z);
                    if (!level.isLoaded(target)) continue;
                    BlockState state = level.getBlockState(target);
                    if (field.matches(target, state) && WondrousStaffAcceleration.isValidTarget(level, target)) {
                        targets.add(target);
                    }
                }
            }
        }
        return List.copyOf(targets);
    }

    public static AABB bounds(BlockPos center, int sizeX, int sizeY, int sizeZ) {
        int x = center.getX() - (clampSize(sizeX) - 1) / 2;
        int y = center.getY() - (clampSize(sizeY) - 1) / 2;
        int z = center.getZ() - (clampSize(sizeZ) - 1) / 2;
        return new AABB(x, y, z, x + clampSize(sizeX), y + clampSize(sizeY), z + clampSize(sizeZ));
    }

    public static int clampSize(int value) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Field field : fields.values()) list.add(field.save());
        tag.put("fields", list);
        return tag;
    }

    private static RangeAccelerationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RangeAccelerationSavedData data = new RangeAccelerationSavedData();
        ListTag list = tag.getList("fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Field field = Field.load(list.getCompound(i));
            if (field != null) data.fields.put(field.id, field);
        }
        return data;
    }

    public record Summary(UUID id, ResourceLocation dimension, BlockPos center, long createdAt,
                          boolean enabled, int speed, int sizeX, int sizeY, int sizeZ) {
    }

    public enum PlacementStatus {
        CREATED,
        UPDATED,
        OCCUPIED,
        LIMIT_REACHED
    }

    public record PlacementResult(PlacementStatus status, UUID id) {
    }

    public enum MarkStatus {
        CHANGED,
        NO_RANGE,
        INVALID_TARGET,
        FULL
    }

    public record MarkResult(MarkStatus status, boolean added, int affected, int listSize, Field field) {
    }

    public static final class Field {
        private final UUID id;
        private final UUID owner;
        private final ResourceLocation dimension;
        private final BlockPos center;
        private long createdAt;
        private boolean enabled;
        private int speed;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private boolean accelerationWhitelistMode;
        private boolean sleepWhitelistMode;
        private final Set<Long> accelerationMarks;
        private final Set<Long> sleepMarks;
        /** Only populated while migrating a pre-positional, block-type list. */
        private final Set<ResourceLocation> legacyFilters;
        private boolean positionalLists;
        /** Runtime-only aggregate state, synchronized by TimeFlowEntity and never persisted. */
        private boolean idleThrottled;
        private int revision;

        private Field(UUID id, UUID owner, ResourceLocation dimension, BlockPos center,
                      long createdAt, boolean enabled, int speed, int sizeX, int sizeY, int sizeZ,
                      boolean accelerationWhitelistMode, boolean sleepWhitelistMode,
                      Iterable<Long> accelerationMarks, Iterable<Long> sleepMarks,
                      Iterable<ResourceLocation> legacyFilters, boolean positionalLists) {
            this.id = id;
            this.owner = owner;
            this.dimension = dimension;
            this.center = center;
            this.createdAt = createdAt;
            this.enabled = enabled;
            this.speed = Math.max(1, Math.min(1024, speed));
            this.sizeX = clampSize(sizeX);
            this.sizeY = clampSize(sizeY);
            this.sizeZ = clampSize(sizeZ);
            this.accelerationWhitelistMode = accelerationWhitelistMode;
            this.sleepWhitelistMode = sleepWhitelistMode;
            this.accelerationMarks = validatedMarks(accelerationMarks);
            this.sleepMarks = validatedMarks(sleepMarks);
            this.legacyFilters = new LinkedHashSet<>();
            for (ResourceLocation idValue : legacyFilters) {
                if (idValue != null && this.legacyFilters.size() < MAX_FILTERS) this.legacyFilters.add(idValue);
            }
            this.positionalLists = positionalLists;
            trimMarksToBounds();
        }

        private void updateFrom(ItemStack staff) {
            createdAt = System.currentTimeMillis();
            enabled = true;
            speed = Math.max(1, Math.min(WondrousStaffAccelerationEntity.MAX_MULTIPLIER,
                    WondrousStaffAcceleration.getSpeed(staff)));
            sizeX = RangeAccelerationSettings.sizeX(staff);
            sizeY = RangeAccelerationSettings.sizeY(staff);
            sizeZ = RangeAccelerationSettings.sizeZ(staff);
            accelerationWhitelistMode = RangeAccelerationSettings.whitelistMode(staff);
            sleepWhitelistMode = RangeAccelerationSettings.sleepWhitelistMode(staff);
            trimMarksToBounds();
            idleThrottled = false;
            revision++;
        }

        public UUID id() { return id; }
        public UUID owner() { return owner; }
        public ResourceLocation dimension() { return dimension; }
        public BlockPos center() { return center; }
        public long createdAt() { return createdAt; }
        public boolean enabled() { return enabled; }
        public int speed() { return speed; }
        public int sizeX() { return sizeX; }
        public int sizeY() { return sizeY; }
        public int sizeZ() { return sizeZ; }
        public boolean accelerationWhitelistMode() { return accelerationWhitelistMode; }
        public boolean sleepWhitelistMode() { return sleepWhitelistMode; }
        public Set<Long> accelerationMarks() { return Set.copyOf(accelerationMarks); }
        public Set<Long> sleepMarks() { return Set.copyOf(sleepMarks); }
        public int accelerationMarkCount() { return accelerationMarks.size(); }
        public int sleepMarkCount() { return sleepMarks.size(); }
        public boolean idleThrottled() { return idleThrottled; }

        private ResourceKey<Level> dimensionKey() {
            return ResourceKey.create(Registries.DIMENSION, dimension);
        }

        private boolean matches(BlockPos pos, BlockState state) {
            if (!positionalLists) {
                ResourceLocation blockId = state.getBlockHolder().unwrapKey()
                        .map(key -> key.location()).orElse(null);
                boolean listed = blockId != null && legacyFilters.contains(blockId);
                return accelerationWhitelistMode ? listed : !listed;
            }
            boolean listed = accelerationMarks.contains(pos.asLong());
            return accelerationWhitelistMode ? listed : !listed;
        }

        private boolean allowsSleep(BlockPos pos) {
            boolean listed = sleepMarks.contains(pos.asLong());
            return sleepWhitelistMode ? listed : !listed;
        }

        private boolean contains(BlockPos pos) {
            return bounds(center, sizeX, sizeY, sizeZ).contains(Vec3.atCenterOf(pos));
        }

        private void setWhitelistMode(boolean sleepList, boolean whitelistMode) {
            if (sleepList) sleepWhitelistMode = whitelistMode;
            else accelerationWhitelistMode = whitelistMode;
        }

        private List<BlockPos> sameTypeTargets(ServerLevel level, BlockPos target) {
            net.minecraft.world.level.block.Block targetBlock = level.getBlockState(target).getBlock();
            List<BlockPos> matches = new ArrayList<>();
            int startX = center.getX() - (sizeX - 1) / 2;
            int startY = center.getY() - (sizeY - 1) / 2;
            int startZ = center.getZ() - (sizeZ - 1) / 2;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockPos candidate = new BlockPos(startX + x, startY + y, startZ + z);
                        if (!level.isLoaded(candidate)
                                || level.getBlockState(candidate).getBlock() != targetBlock
                                || !WondrousStaffAcceleration.isValidTarget(level, candidate)) continue;
                        matches.add(candidate);
                    }
                }
            }
            return List.copyOf(matches);
        }

        /** Converts old type-wide filters to the concrete target positions inside this field. */
        private boolean migrateLegacyFilters(ServerLevel level) {
            if (positionalLists) return false;
            int startX = center.getX() - (sizeX - 1) / 2;
            int startY = center.getY() - (sizeY - 1) / 2;
            int startZ = center.getZ() - (sizeZ - 1) / 2;
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockPos candidate = new BlockPos(startX + x, startY + y, startZ + z);
                        if (!level.isLoaded(candidate)) continue;
                        ResourceLocation blockId = level.getBlockState(candidate).getBlockHolder().unwrapKey()
                                .map(key -> key.location()).orElse(null);
                        if (blockId != null && legacyFilters.contains(blockId)
                                && WondrousStaffAcceleration.isValidTarget(level, candidate)) {
                            accelerationMarks.add(candidate.asLong());
                        }
                    }
                }
            }
            legacyFilters.clear();
            positionalLists = true;
            revision++;
            return true;
        }

        private static Set<Long> validatedMarks(Iterable<Long> source) {
            Set<Long> result = new LinkedHashSet<>();
            for (Long value : source) {
                if (value != null && result.size() < MAX_MARKED_POSITIONS) result.add(value);
            }
            return result;
        }

        private void trimMarksToBounds() {
            accelerationMarks.removeIf(value -> !contains(BlockPos.of(value)));
            sleepMarks.removeIf(value -> !contains(BlockPos.of(value)));
        }

        private Summary summary() {
            return new Summary(id, dimension, center, createdAt, enabled, speed, sizeX, sizeY, sizeZ);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("owner", owner);
            tag.putString("dimension", dimension.toString());
            tag.putLong("center", center.asLong());
            tag.putLong("created_at", createdAt);
            tag.putBoolean("enabled", enabled);
            tag.putInt("speed", speed);
            tag.putInt("size_x", sizeX);
            tag.putInt("size_y", sizeY);
            tag.putInt("size_z", sizeZ);
            tag.putBoolean("acceleration_whitelist", accelerationWhitelistMode);
            tag.putBoolean("sleep_whitelist", sleepWhitelistMode);
            tag.putBoolean("positional_lists", positionalLists);
            tag.putLongArray("acceleration_marks", accelerationMarks.stream().mapToLong(Long::longValue).toArray());
            tag.putLongArray("sleep_marks", sleepMarks.stream().mapToLong(Long::longValue).toArray());
            ListTag filterTags = new ListTag();
            for (ResourceLocation filter : legacyFilters) filterTags.add(StringTag.valueOf(filter.toString()));
            tag.put("filters", filterTags);
            return tag;
        }

        private static Field load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.hasUUID("owner")) return null;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null) return null;
            List<ResourceLocation> filters = new ArrayList<>();
            ListTag filterTags = tag.getList("filters", Tag.TAG_STRING);
            for (int i = 0; i < filterTags.size() && filters.size() < MAX_FILTERS; i++) {
                ResourceLocation id = ResourceLocation.tryParse(filterTags.getString(i));
                if (id != null) filters.add(id);
            }
            boolean positionalLists = tag.contains("positional_lists") && tag.getBoolean("positional_lists");
            boolean accelerationWhitelist = tag.contains("acceleration_whitelist")
                    ? tag.getBoolean("acceleration_whitelist") : tag.getBoolean("whitelist");
            // Old no-throttle fields become an empty sleep whitelist; old throttled fields become
            // an empty sleep blacklist, preserving their effective behaviour exactly.
            boolean sleepWhitelist = tag.contains("sleep_whitelist")
                    ? tag.getBoolean("sleep_whitelist") : tag.getBoolean("idle_throttle_disabled");
            List<Long> accelerationMarks = java.util.Arrays.stream(tag.getLongArray("acceleration_marks"))
                    .boxed().limit(MAX_MARKED_POSITIONS).toList();
            List<Long> sleepMarks = java.util.Arrays.stream(tag.getLongArray("sleep_marks"))
                    .boxed().limit(MAX_MARKED_POSITIONS).toList();
            return new Field(tag.getUUID("id"), tag.getUUID("owner"), dimension,
                    BlockPos.of(tag.getLong("center")), tag.getLong("created_at"),
                    !tag.contains("enabled") || tag.getBoolean("enabled"), tag.getInt("speed"),
                    tag.getInt("size_x"), tag.getInt("size_y"), tag.getInt("size_z"),
                    accelerationWhitelist, sleepWhitelist, accelerationMarks, sleepMarks,
                    filters, positionalLists);
        }
    }

    private static final class FieldRuntime {
        private int revision = Integer.MIN_VALUE;
        private long lastScanTick = Long.MIN_VALUE;
        /** Runtime-only target cursor; prevents early targets from monopolizing the shared budget. */
        private int targetCursor;
        private List<BlockPos> targets = List.of();
        private final Map<Long, TargetWork> targetWork = new HashMap<>();
    }

    private static final class TargetWork {
        private long pendingTicks;
        private long lastEnergy = Long.MIN_VALUE;
        private BlockState lastState;
        private int idleTicks;
        private boolean observedWorking;

        private boolean shouldThrottle(ServerLevel level, BlockPos pos, BlockState state) {
            boolean working = isWorking(level, pos, state);
            if (working) {
                observedWorking = true;
                idleTicks = 0;
            } else {
                idleTicks++;
            }
            return observedWorking && idleTicks > IDLE_WINDOW_TICKS;
        }

        private boolean isWorking(ServerLevel level, BlockPos pos, BlockState state) {
            BlockEntity target = level.getBlockEntity(pos);
            if (target == null) return true;
            if (target instanceof ChangedTickAccessor accessor) {
                long changed = accessor.uselessStretcher$getLastChangedTick();
                if (changed >= 0L && level.getGameTime() - changed <= CHANGED_WINDOW_TICKS) return true;
            }
            if (!state.equals(lastState)) {
                lastState = state;
                return true;
            }
            if (target instanceof IInWorldGridNodeHost host
                    && WondrousStaffAcceleration.isAeDeviceWorking(host)) return true;
            IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (energy != null) {
                int stored = energy.getEnergyStored();
                if (lastEnergy == Long.MIN_VALUE || stored != lastEnergy) {
                    lastEnergy = stored;
                    return true;
                }
            }
            return false;
        }
    }

}
