package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.content.acceleration.PermanentAccelerationHistory;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.acceleration.MachineActivityProbe;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/**
 * Invisible marker entity that drives extra ticks on a block / AE node / living entity /
 * world time every game tick. Retained virtual ticks act as a large cache: each game tick
 * accumulates {@code speed} work and executes at most {@value #MAX_EXECUTIONS_PER_TICK}
 * ticks, so retained work can drain over time without losing requested acceleration.
 *
 * <p>The retained-virtual-tick + per-tick budget idea is a simplified version of JDT Extras'
 * {@code TimeAccelerationWorkQueue} / {@code ExtendedTimeAccelerationManager} (MIT).
 *
 * <p>An idle machine is throttled instead of being put to sleep: after {@value #IDLE_WINDOW_TICKS}
 * ticks without any activity signal the per-tick budget drops from {@value #MAX_EXECUTIONS_PER_TICK}
 * to {@value #IDLE_EXECUTIONS_PER_TICK}. Throttling removes ~98% of the idle cost while guaranteeing
 * the target is never stopped outright; a wrongly throttled machine only runs slower for a moment,
 * and any activity signal restores full speed on the very next tick. Only a machine that has already
 * proven it can be observed working is ever throttled, so a machine whose
 * activity we cannot observe is never slowed down. The staff's third mode can explicitly disable
 * this throttle for machines whose activity signals are unreliable.
 */
public class WondrousStaffAccelerationEntity extends Entity {
    /** Negative remaining time means the acceleration never expires. */
    public static final int PERMANENT = -1;
    public static final int MAX_MULTIPLIER = 1024;
    public static final int MAX_EXECUTIONS_PER_TICK = MAX_MULTIPLIER;
    private static final long MAX_PENDING_TICKS = 1_000_000L;
    /** How long a machine may show no activity signal before it is throttled. */
    private static final int IDLE_WINDOW_TICKS = 200;
    /** Extra ticks per game tick while throttled. Never 0: the target must keep progressing. */
    private static final int IDLE_EXECUTIONS_PER_TICK = 4;

    public static final int MODE_BLOCK = 0;
    public static final int MODE_ENTITY = 1;
    public static final int MODE_TIME = 2;

    private static final EntityDataAccessor<Integer> SPEED =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING_TIME =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IDLE_THROTTLE_DISABLED =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.BOOLEAN);
    /** True while this block target is currently running at the reduced idle rate. */
    private static final EntityDataAccessor<Boolean> IDLE_THROTTLED =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.BOOLEAN);
    /** Whether entity mode should suppress Mob AI while this marker is active. */
    private static final EntityDataAccessor<Boolean> ENTITY_AI_DISABLED =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENTITY_TIMER_MODE =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.BOOLEAN);
    /** Current target height, used to keep the world-space entity progress bar above its head. */
    private static final EntityDataAccessor<Float> TARGET_HEIGHT =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.FLOAT);

    private BlockPos targetPos;
    private UUID targetUuid;
    /** Player who created this marker; used by the operator reclaimer. */
    private UUID ownerUuid;
    /** Non-null when this marker is owned by a placed range field rather than direct staff use. */
    private UUID rangeFieldUuid;
    private long pendingTicks;
    private final MachineActivityProbe activityProbe = new MachineActivityProbe();
    private BlockEntity observedTarget;
    private int idleTicks;
    /** Set once the target has ever emitted an activity signal we can rely on for waking up. */
    private boolean observedWorking;
    /** Original Mob AI state, captured before entity acceleration changes it. */
    private boolean originalNoAi;
    private boolean aiStateCaptured;
    private int restockCooldown;

    public WondrousStaffAccelerationEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public WondrousStaffAccelerationEntity(Level level, BlockPos pos, int speed, boolean timeMode) {
        this(ModEntities.WONDROUS_STAFF_ACCELERATION.get(), level);
        this.targetPos = pos.immutable();
        this.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        this.setMode(timeMode ? MODE_TIME : MODE_BLOCK);
        this.setSpeed(speed);
        this.setRemainingTime(WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
    }

    public WondrousStaffAccelerationEntity(Level level, BlockPos targetPos, int speed) {
        this(ModEntities.WONDROUS_STAFF_ACCELERATION.get(), level);
        this.targetPos = targetPos.immutable();
        this.setPos(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
        this.setMode(MODE_BLOCK);
        this.setSpeed(speed);
        this.setRemainingTime(WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
    }

    public WondrousStaffAccelerationEntity(Level level, Entity target, int speed) {
        this(ModEntities.WONDROUS_STAFF_ACCELERATION.get(), level);
        this.targetUuid = target.getUUID();
        this.targetPos = target.blockPosition();
        this.setPos(target.position());
        this.setTargetHeight(target.getBbHeight());
        this.setMode(MODE_ENTITY);
        this.setSpeed(speed);
        this.setRemainingTime(WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (!(this.level() instanceof ServerLevel level) || this.targetPos == null) {
            this.discard();
            return;
        }
        int remaining = getRemainingTime();
        PermanentAccelerationHistory history = PermanentAccelerationHistory.get(level.getServer());
        if (rangeFieldUuid != null) {
            RangeAccelerationSavedData.Field field =
                    RangeAccelerationSavedData.get(level.getServer()).getField(rangeFieldUuid);
            if (field == null || !field.enabled() || !StretcherConfig.serverRangeAcceleration()
                    || !field.dimension().equals(level.dimension().location())
                    || targetUuid == null || !field.contains(getTargetPos())) {
                discard();
                return;
            }
            applyRangeSettings(field);
        }
        if (history.isReclaimed(getUUID())) {
            discard();
            return;
        }
        if (rangeFieldUuid == null && tickCount % 20 == 1) history.track(level, this);
        if (remaining != PERMANENT && remaining <= 0) {
            this.discard();
            return;
        }
        if (!StretcherConfig.serverStaffAcceleration()) {
            pendingTicks = 0L;
            resetActivity();
            restoreEntityAi();
            if (isTimeMode() && tickCount % 10 == 1) Network.sendTimeAccelerationState(level, 0);
            if (remaining != PERMANENT) setRemainingTime(remaining - 1);
            return;
        }
        // Entity targets can move. Keep both the marker and the persisted lookup position in sync
        // before checking the chunk, otherwise a moving target would stop being accelerated after
        // leaving its first block.
        if (isEntityMode()) {
            Entity target = findEntity(level);
            if (target == null || target.isRemoved() || target instanceof Player) {
                discard();
                return;
            }
            this.targetPos = target.blockPosition();
            this.setPos(target.position());
            this.setTargetHeight(target.getBbHeight());
        }
        if (!level.isLoaded(this.targetPos)) return;

        int speed = Math.max(1, getSpeed());
        if (isTimeMode()) {
            setIdleThrottled(false);
            // Sun/moon acceleration is blacklisted from permanent mode: convert any legacy
            // permanent time entity back to the normal 30s duration so it can expire.
            if (isPermanent()) setRemainingTime(WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
            if (level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) {
                level.setDayTime(level.getDayTime() + speed);
            }
            WondrousStaffAcceleration.tickWeather(level, speed);
            if (this.tickCount % 10 == 1) {
                Network.sendTimeAccelerationState(level, speed);
            }
        } else if (isEntityMode()) {
            setIdleThrottled(false);
            advanceEntity(level);
        } else {
            // If the target block was broken (or changed to a static block), remove the
            // acceleration together with the block.
            if (!WondrousStaffAcceleration.isValidTarget(level, this.targetPos)) {
                discard();
                return;
            }

            BlockEntity target = level.getBlockEntity(this.targetPos);
            if (target != observedTarget) {
                pendingTicks = 0L;
                resetActivity();
                observedTarget = target;
            }
            // Lightning rods are deliberately driven by command-style lightning rather than
            // vanilla's weather scheduler. They do not mutate a block entity, energy buffer, or
            // inventory, so the generic activity probe can never observe them as "working".
            // Never let dynamic idle throttling silently reduce a rod to the fallback rate.
            boolean mayThrottle = isPermanent() && !isIdleThrottleDisabled() && StretcherConfig.idleThrottle()
                    && !(level.getBlockState(this.targetPos).getBlock()
                    instanceof net.minecraft.world.level.block.LightningRodBlock);
            boolean throttled = false;
            if (mayThrottle) {
                if (activityProbe.isWorking(level, this.targetPos, level.getBlockState(this.targetPos))) {
                    observedWorking = true;
                    idleTicks = 0;
                } else {
                    idleTicks++;
                }
                throttled = observedWorking && idleTicks > IDLE_WINDOW_TICKS;
            } else {
                activityProbe.reset();
                observedWorking = false;
                idleTicks = 0;
            }
            setIdleThrottled(throttled);
            if (throttled) {
                // Do not bank a backlog while throttled, otherwise waking up would fire a huge burst.
                pendingTicks = 0L;
                int executed = AccelerationExecutionBudget.take(
                        level.getServer(), this, IDLE_EXECUTIONS_PER_TICK);
                if (executed > 0) {
                    WondrousStaffAcceleration.tickTarget(level, this.targetPos, executed, this);
                }
            } else {
                pendingTicks = Math.min(MAX_PENDING_TICKS, pendingTicks + speed);
                int requested = (int) Math.min(pendingTicks, MAX_EXECUTIONS_PER_TICK);
                int executed = AccelerationExecutionBudget.take(level.getServer(), this, requested);
                if (executed > 0) {
                    pendingTicks -= WondrousStaffAcceleration.tickTarget(level, this.targetPos, executed, this);
                }
            }
        }

        if (remaining != PERMANENT) {
            setRemainingTime(remaining - 1);
        }
    }

    private void advanceEntity(ServerLevel level) {
        Entity target = findEntity(level);
        if (target == null || target.isRemoved() || target instanceof Player) {
            discard();
            return;
        }
        this.targetPos = target.blockPosition();
        this.setPos(target.position());
        updateEntityAi(target);

        if (isEntityTimerMode() && target instanceof LivingEntity living) {
            pendingTicks = 0L;
            EntityTimerAcceleration.advance(living, getSpeed());
            if (living instanceof Villager villager) {
                if (restockCooldown > 0) restockCooldown--;
                if (restockCooldown == 0) {
                    // Bound trade inspection even when no offer currently needs restocking.
                    restockCooldown = 20;
                    if (villager.getOffers().stream().anyMatch(net.minecraft.world.item.trading.MerchantOffer::needsRestock)) {
                        villager.restock();
                    }
                }
            }
            return;
        }

        // Run the target's complete tick instead of only changing an AgeableMob's age. This
        // accelerates movement, item/projectile lifetime, status effects, cooldowns and modded
        // entity logic alike. Retaining excess virtual ticks keeps x1024 from doing all work in
        // one server tick while preserving it for later frames.
        pendingTicks = Math.min(MAX_PENDING_TICKS, pendingTicks + (long) Math.max(1, getSpeed()));
        int requested = (int) Math.min(pendingTicks, MAX_EXECUTIONS_PER_TICK);
        int executed = AccelerationExecutionBudget.take(level.getServer(), this, requested);
        long started = System.nanoTime();
        long deadline = AccelerationExecutionBudget.deadline(level.getServer(), this);
        try {
            for (int i = 0; i < executed && !target.isRemoved() && System.nanoTime() < deadline; i++) {
                target.tick();
                pendingTicks--;
            }
        } finally {
            AccelerationExecutionBudget.recordWork(level.getServer(), this, started);
        }
        setTargetHeight(target.getBbHeight());
        if (target.isRemoved()) discard();
    }

    private Entity findEntity(ServerLevel level) {
        return targetUuid == null ? null : level.getEntity(targetUuid);
    }

    private void updateEntityAi(Entity target) {
        if (!(target instanceof Mob mob)) return;
        // Ender Dragon hitboxes are maintained by its phase/part tick. Forcing
        // Mob#setNoAi(true) stops that lifecycle, leaving the dragon unable to
        // process damage and death. Keep its vanilla phase ticking; all other
        // mobs still use the staff's no-AI behavior.
        if (mob instanceof EnderDragon) return;
        if (!isEntityAiDisabled()) {
            restoreEntityAi(mob);
            return;
        }
        if (!aiStateCaptured) {
            originalNoAi = mob.isNoAi();
            aiStateCaptured = true;
        }
        mob.setNoAi(true);
    }

    private void restoreEntityAi(Mob mob) {
        if (!aiStateCaptured) return;
        mob.setNoAi(originalNoAi);
        aiStateCaptured = false;
    }

    private void restoreEntityAi() {
        if (!aiStateCaptured || targetUuid == null || !(this.level() instanceof ServerLevel level)) return;
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            if (candidate.getEntity(targetUuid) instanceof Mob mob) {
                mob.setNoAi(originalNoAi);
                break;
            }
        }
        aiStateCaptured = false;
    }

    private void resetActivity() {
        activityProbe.reset();
        observedTarget = null;
        idleTicks = 0;
        observedWorking = false;
        setIdleThrottled(false);
    }

    public BlockPos getTargetPos() {
        return this.targetPos != null ? this.targetPos : this.blockPosition();
    }

    public void setTargetPos(BlockPos pos) {
        if (!java.util.Objects.equals(this.targetPos, pos)) {
            pendingTicks = 0L;
            resetActivity();
        }
        this.targetPos = pos == null ? null : pos.immutable();
    }

    public UUID getTargetUuid() {
        return this.targetUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getRangeFieldUuid() {
        return rangeFieldUuid;
    }

    public void setRangeFieldUuid(UUID rangeFieldUuid) {
        this.rangeFieldUuid = rangeFieldUuid;
        if (rangeFieldUuid != null) {
            setPermanent();
        }
    }

    /** Applies persisted range settings without resetting pending work when nothing changed. */
    public void applyRangeSettings(RangeAccelerationSavedData.Field field) {
        int desiredSpeed = field.entityTimerMode() ? field.entityTimerSpeed() : field.speed();
        if (isEntityTimerMode() != field.entityTimerMode()) setEntityTimerMode(field.entityTimerMode());
        if (getSpeed() != desiredSpeed) setSpeed(desiredSpeed);
        boolean aiDisabled = StretcherConfig.entityDisableAi();
        if (isEntityAiDisabled() != aiDisabled) setEntityAiDisabled(aiDisabled);
        if (!isPermanent()) setPermanent();
        setIdleThrottleDisabled(true);
    }

    public int getMode() {
        return this.entityData.get(MODE);
    }

    public void setMode(int mode) {
        this.entityData.set(MODE, mode);
    }

    public boolean isIdleThrottleDisabled() {
        return this.entityData.get(IDLE_THROTTLE_DISABLED);
    }

    public void setIdleThrottleDisabled(boolean disabled) {
        this.entityData.set(IDLE_THROTTLE_DISABLED, disabled);
    }

    public boolean isEntityAiDisabled() {
        return this.entityData.get(ENTITY_AI_DISABLED);
    }

    public void setEntityAiDisabled(boolean disabled) {
        this.entityData.set(ENTITY_AI_DISABLED, disabled);
        if (!disabled && this.level() instanceof ServerLevel level && targetUuid != null
                && level.getEntity(targetUuid) instanceof Mob mob) {
            restoreEntityAi(mob);
        }
    }

    /** True when the target is currently being ticked at the reduced idle rate. */
    public boolean isIdleThrottled() {
        return this.entityData.get(IDLE_THROTTLED);
    }

    private void setIdleThrottled(boolean throttled) {
        this.entityData.set(IDLE_THROTTLED, throttled);
    }

    public boolean isTimeMode() {
        return getMode() == MODE_TIME;
    }

    public boolean isEntityMode() {
        return getMode() == MODE_ENTITY;
    }

    public float getTargetHeight() {
        return this.entityData.get(TARGET_HEIGHT);
    }

    private void setTargetHeight(float height) {
        this.entityData.set(TARGET_HEIGHT, Math.max(0.1F, Math.min(16.0F, height)));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            PermanentAccelerationHistory.get(serverLevel.getServer()).forget(getUUID());
        }
        pendingTicks = 0L;
        resetActivity();
        // Chunk-unload removal is temporary and this marker is saved with the target; retain both
        // states so the field can resume after reload. Every terminal removal restores the target.
        if (reason != RemovalReason.UNLOADED_TO_CHUNK) restoreEntityAi();
        if (isTimeMode() && this.level() instanceof ServerLevel level) {
            Network.sendTimeAccelerationState(level, 0);
        }
        super.remove(reason);
    }

    public int getSpeed() {
        return this.entityData.get(SPEED);
    }

    public void setSpeed(int speed) {
        this.entityData.set(SPEED, isEntityMode() && isEntityTimerMode()
                ? Math.max(1, Math.min(EntityTimerAcceleration.MAX_SPEED, speed)) : Math.max(1, speed));
    }

    public boolean isEntityTimerMode() { return this.entityData.get(ENTITY_TIMER_MODE); }

    public void setEntityTimerMode(boolean timers) {
        if (isEntityTimerMode() == timers) return;
        this.entityData.set(ENTITY_TIMER_MODE, timers);
        pendingTicks = 0L;
        restockCooldown = 0;
        if (!timers) this.entityData.set(SPEED, Math.min(MAX_MULTIPLIER, getSpeed()));
        setSpeed(getSpeed());
    }

    public int getRemainingTime() {
        return this.entityData.get(REMAINING_TIME);
    }

    public void setRemainingTime(int ticks) {
        this.entityData.set(REMAINING_TIME, ticks);
    }

    public void setPermanent() {
        this.entityData.set(REMAINING_TIME, PERMANENT);
    }

    public boolean isPermanent() {
        return getRemainingTime() == PERMANENT;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPEED, 2);
        builder.define(REMAINING_TIME, WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
        builder.define(MODE, MODE_BLOCK);
        builder.define(IDLE_THROTTLE_DISABLED, false);
        builder.define(IDLE_THROTTLED, false);
        builder.define(ENTITY_AI_DISABLED, false);
        builder.define(ENTITY_TIMER_MODE, false);
        builder.define(TARGET_HEIGHT, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("targetPos")) this.targetPos = BlockPos.of(tag.getLong("targetPos"));
        if (tag.hasUUID("targetUuid")) this.targetUuid = tag.getUUID("targetUuid");
        if (tag.hasUUID("ownerUuid")) this.ownerUuid = tag.getUUID("ownerUuid");
        if (tag.hasUUID("rangeFieldUuid")) this.rangeFieldUuid = tag.getUUID("rangeFieldUuid");
        setMode(tag.getInt("mode"));
        setEntityTimerMode(tag.getBoolean("entityTimerMode"));
        setSpeed(tag.getInt("speed"));
        setRemainingTime(tag.getInt("remainingTime"));
        setIdleThrottleDisabled(tag.getBoolean("idleThrottleDisabled"));
        setIdleThrottled(tag.getBoolean("idleThrottled"));
        setEntityAiDisabled(tag.getBoolean("entityAiDisabled"));
        if (tag.contains("targetHeight")) setTargetHeight(tag.getFloat("targetHeight"));
        originalNoAi = tag.getBoolean("originalNoAi");
        aiStateCaptured = tag.getBoolean("aiStateCaptured");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetPos != null) tag.putLong("targetPos", this.targetPos.asLong());
        if (this.targetUuid != null) tag.putUUID("targetUuid", this.targetUuid);
        if (this.ownerUuid != null) tag.putUUID("ownerUuid", this.ownerUuid);
        if (this.rangeFieldUuid != null) tag.putUUID("rangeFieldUuid", this.rangeFieldUuid);
        tag.putInt("mode", getMode());
        tag.putInt("speed", getSpeed());
        tag.putInt("remainingTime", getRemainingTime());
        tag.putBoolean("idleThrottleDisabled", isIdleThrottleDisabled());
        tag.putBoolean("idleThrottled", isIdleThrottled());
        tag.putBoolean("entityAiDisabled", isEntityAiDisabled());
        tag.putBoolean("entityTimerMode", isEntityTimerMode());
        tag.putFloat("targetHeight", getTargetHeight());
        tag.putBoolean("originalNoAi", originalNoAi);
        tag.putBoolean("aiStateCaptured", aiStateCaptured);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
