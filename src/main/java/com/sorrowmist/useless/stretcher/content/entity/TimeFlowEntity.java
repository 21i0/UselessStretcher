package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Persistent, non-blocking visual anchor for one placed acceleration range. */
public final class TimeFlowEntity extends Entity {
    private static final EntityDataAccessor<Integer> SIZE_X =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE_Y =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE_Z =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OFFSET_X =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OFFSET_Y =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OFFSET_Z =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ENABLED =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IDLE_THROTTLED =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> CREATED_AT =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> LIST_STATE =
            SynchedEntityData.defineId(TimeFlowEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private CompoundTag cachedListState;
    private UUID cachedOwner;
    private Set<Long> cachedAccelerationMarks = Set.of();
    private Set<Long> cachedSleepMarks = Set.of();

    public TimeFlowEntity(EntityType<? extends TimeFlowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public TimeFlowEntity(ServerLevel level, java.util.UUID fieldId) {
        this(ModEntities.TIME_FLOW.get(), level);
        setUUID(fieldId);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
        setDeltaMovement(Vec3.ZERO);
        clearFire();
        if (!(level() instanceof ServerLevel serverLevel)) return;

        RangeAccelerationSavedData.Field field =
                RangeAccelerationSavedData.get(serverLevel.getServer()).getField(getUUID());
        if (field == null || !field.dimension().equals(serverLevel.dimension().location())) {
            discard();
            return;
        }
        sync(field);
    }

    public void sync(RangeAccelerationSavedData.Field field) {
        entityData.set(SIZE_X, field.sizeX());
        entityData.set(SIZE_Y, field.sizeY());
        entityData.set(SIZE_Z, field.sizeZ());
        entityData.set(OFFSET_X, field.offsetX());
        entityData.set(OFFSET_Y, field.offsetY());
        entityData.set(OFFSET_Z, field.offsetZ());
        entityData.set(ENABLED, field.enabled());
        entityData.set(IDLE_THROTTLED, field.idleThrottled());
        entityData.set(CREATED_AT, field.createdAt());
        CompoundTag state = new CompoundTag();
        state.putUUID("owner", field.owner());
        state.putBoolean("acceleration_whitelist", field.accelerationWhitelistMode());
        state.putBoolean("sleep_whitelist", field.sleepWhitelistMode());
        state.putLongArray("acceleration_marks",
                field.accelerationMarks().stream().mapToLong(Long::longValue).toArray());
        state.putLongArray("sleep_marks", field.sleepMarks().stream().mapToLong(Long::longValue).toArray());
        entityData.set(LIST_STATE, state);
        var center = field.effectiveCenter();
        setPos(center.getX() + 0.5D, center.getY() + 0.08D, center.getZ() + 0.5D);
    }

    public boolean isEnabled() {
        return entityData.get(ENABLED);
    }

    public boolean isIdleThrottled() {
        return entityData.get(IDLE_THROTTLED);
    }

    public long getCreatedAt() {
        return entityData.get(CREATED_AT);
    }

    public int getSizeX() { return entityData.get(SIZE_X); }
    public int getSizeY() { return entityData.get(SIZE_Y); }
    public int getSizeZ() { return entityData.get(SIZE_Z); }
    public int getOffsetX() { return entityData.get(OFFSET_X); }
    public int getOffsetY() { return entityData.get(OFFSET_Y); }
    public int getOffsetZ() { return entityData.get(OFFSET_Z); }

    public UUID getOwner() {
        refreshListCache();
        return cachedOwner;
    }

    public boolean isAccelerationWhitelist() {
        return entityData.get(LIST_STATE).getBoolean("acceleration_whitelist");
    }

    public boolean isSleepWhitelist() {
        return entityData.get(LIST_STATE).getBoolean("sleep_whitelist");
    }

    public Set<Long> getAccelerationMarks() {
        refreshListCache();
        return cachedAccelerationMarks;
    }

    public Set<Long> getSleepMarks() {
        refreshListCache();
        return cachedSleepMarks;
    }

    private void refreshListCache() {
        CompoundTag current = entityData.get(LIST_STATE);
        if (cachedListState != null && cachedListState.equals(current)) return;
        cachedListState = current.copy();
        cachedOwner = current.hasUUID("owner") ? current.getUUID("owner") : null;
        cachedAccelerationMarks = immutableLongSet(current.getLongArray("acceleration_marks"));
        cachedSleepMarks = immutableLongSet(current.getLongArray("sleep_marks"));
    }

    private static Set<Long> immutableLongSet(long[] values) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        Arrays.stream(values).limit(RangeAccelerationSavedData.MAX_MARKED_POSITIONS).forEach(result::add);
        return Set.copyOf(result);
    }

    public AABB coveredBounds() {
        return RangeAccelerationSavedData.bounds(blockPosition(), getSizeX(), getSizeY(), getSizeZ());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE_X, 3);
        builder.define(SIZE_Y, 3);
        builder.define(SIZE_Z, 3);
        builder.define(OFFSET_X, 0);
        builder.define(OFFSET_Y, 0);
        builder.define(OFFSET_Z, 0);
        builder.define(ENABLED, true);
        builder.define(IDLE_THROTTLED, false);
        builder.define(CREATED_AT, 0L);
        builder.define(LIST_STATE, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(SIZE_X, RangeAccelerationSavedData.clampSize(tag.getInt("size_x")));
        entityData.set(SIZE_Y, RangeAccelerationSavedData.clampSize(tag.getInt("size_y")));
        entityData.set(SIZE_Z, RangeAccelerationSavedData.clampSize(tag.getInt("size_z")));
        entityData.set(OFFSET_X, RangeAccelerationSavedData.clampOffset(tag.getInt("offset_x")));
        entityData.set(OFFSET_Y, RangeAccelerationSavedData.clampOffset(tag.getInt("offset_y")));
        entityData.set(OFFSET_Z, RangeAccelerationSavedData.clampOffset(tag.getInt("offset_z")));
        entityData.set(ENABLED, !tag.contains("enabled") || tag.getBoolean("enabled"));
        entityData.set(IDLE_THROTTLED, tag.getBoolean("idle_throttled"));
        entityData.set(CREATED_AT, tag.getLong("created_at"));
        entityData.set(LIST_STATE, tag.getCompound("list_state").copy());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("size_x", getSizeX());
        tag.putInt("size_y", getSizeY());
        tag.putInt("size_z", getSizeZ());
        tag.putInt("offset_x", getOffsetX());
        tag.putInt("offset_y", getOffsetY());
        tag.putInt("offset_z", getOffsetZ());
        tag.putBoolean("enabled", isEnabled());
        tag.putBoolean("idle_throttled", isIdleThrottled());
        tag.putLong("created_at", getCreatedAt());
        tag.put("list_state", entityData.get(LIST_STATE).copy());
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.useless_stretcher.time_flow");
    }
}
