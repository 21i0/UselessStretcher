package com.sorrowmist.useless.stretcher.content.entity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.UUID;

/**
 * Invisible marker entity that drives extra ticks on a block / AE node / living entity /
 * world time every game tick. Retained virtual ticks act as a large cache: each game tick
 * accumulates {@code speed} work and executes at most {@value #MAX_EXECUTIONS_PER_TICK}
 * ticks, so a very high multiplier never stalls the server — the backlog drains over time.
 *
 * <p>The retained-virtual-tick + per-tick budget idea is a simplified version of JDT Extras'
 * {@code TimeAccelerationWorkQueue} / {@code ExtendedTimeAccelerationManager} (MIT).
 *
 * <p>Block targets that expose FE storage sleep automatically when their energy level stays
 * unchanged for {@value #IDLE_THRESHOLD_TICKS} ticks (i.e. the machine is not working), and
 * wake up again as soon as the energy level moves. AE2 targets sleep through their own grid
 * node activity flag.
 */
public class WondrousStaffAccelerationEntity extends Entity {
    /** Negative remaining time means the acceleration never expires. */
    public static final int PERMANENT = -1;
    public static final int MAX_MULTIPLIER = 1024;
    public static final int MAX_EXECUTIONS_PER_TICK = 256;
    private static final long MAX_PENDING_TICKS = 8192L;
    private static final int IDLE_THRESHOLD_TICKS = 40;

    public static final int MODE_BLOCK = 0;
    public static final int MODE_ENTITY = 1;
    public static final int MODE_TIME = 2;

    private static final EntityDataAccessor<Integer> SPEED =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING_TIME =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(WondrousStaffAccelerationEntity.class, EntityDataSerializers.INT);

    private BlockPos targetPos;
    private UUID targetUuid;
    private long pendingTicks;
    private long lastEnergy = -1L;
    private int idleTicks;

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
        if (remaining != PERMANENT && remaining <= 0) {
            this.discard();
            return;
        }
        if (!level.isLoaded(this.targetPos)) return;

        int speed = Math.max(1, getSpeed());
        if (isTimeMode()) {
            // Sun/moon acceleration is blacklisted from permanent mode: convert any legacy
            // permanent time entity back to the normal 30s duration so it can expire.
            if (isPermanent()) setRemainingTime(WondrousStaffAcceleration.DEFAULT_DURATION_TICKS);
            level.setDayTime(level.getDayTime() + speed);
        } else if (isEntityMode()) {
            advanceEntity(level);
        } else {
            // If the target block was broken (or changed to a static block), remove the
            // acceleration together with the block.
            if (!WondrousStaffAcceleration.isValidTarget(level, this.targetPos)) {
                discard();
                return;
            }
            if (isTargetActive(level, this.targetPos)) {
                pendingTicks = Math.min(MAX_PENDING_TICKS, pendingTicks + speed);
                int executed = (int) Math.min(pendingTicks, MAX_EXECUTIONS_PER_TICK);
                pendingTicks -= executed;
                if (executed > 0) {
                    WondrousStaffAcceleration.tickTarget(level, this.targetPos, executed);
                }
            }
        }

        if (remaining != PERMANENT) {
            setRemainingTime(remaining - 1);
        }
    }

    private void advanceEntity(ServerLevel level) {
        if (targetUuid == null) {
            discard();
            return;
        }
        if (level.getEntity(targetUuid) instanceof AgeableMob ageable) {
            int age = ageable.getAge();
            int speed = getSpeed();
            if (age < 0) {
                // Baby: advance the negative age toward 0 to grow it up.
                ageable.setAge(Math.min(0, age + speed));
                if (ageable.getAge() >= 0) discard();
            } else if (age > 0) {
                // Adult: advance the breeding cooldown back down to 0.
                ageable.setAge(Math.max(0, age - speed));
                if (ageable.getAge() <= 0) discard();
            } else {
                discard();
            }
        } else {
            discard();
        }
    }

    /**
     * True while the target is actually working. FE-backed machines are considered active
     * whenever their stored energy changes in either direction — consuming (working machines)
     * or generating (power generators) — and sleep only while the level stays constant.
     */
    private boolean isTargetActive(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof IInWorldGridNodeHost host) {
            return hasActiveAeNode(host);
        }
        if (blockEntity == null) {
            return true; // random-tick blocks (crops etc.) are treated as always active
        }
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energy == null) {
            return true; // no energy information, keep the previous always-on behavior
        }
        // Creative / infinite-energy machines never change their energy level, so the
        // "energy changed = working" signal below would incorrectly put them to sleep.
        if (energy.getMaxEnergyStored() >= 2_000_000_000) {
            return true;
        }
        int current = energy.getEnergyStored();
        if (lastEnergy < 0L || current != lastEnergy) {
            lastEnergy = current;
            idleTicks = 0;
            return true;
        }
        lastEnergy = current;
        idleTicks++;
        return idleTicks <= IDLE_THRESHOLD_TICKS;
    }

    private static boolean hasActiveAeNode(IInWorldGridNodeHost host) {
        for (Direction direction : Direction.values()) {
            IGridNode node = host.getGridNode(direction);
            if (node != null && node.getGrid() != null && node.isActive()) {
                return true;
            }
        }
        return false;
    }

    public BlockPos getTargetPos() {
        return this.targetPos != null ? this.targetPos : this.blockPosition();
    }

    public void setTargetPos(BlockPos pos) {
        this.targetPos = pos == null ? null : pos.immutable();
    }

    public UUID getTargetUuid() {
        return this.targetUuid;
    }

    public int getMode() {
        return this.entityData.get(MODE);
    }

    public void setMode(int mode) {
        this.entityData.set(MODE, mode);
    }

    public boolean isTimeMode() {
        return getMode() == MODE_TIME;
    }

    public boolean isEntityMode() {
        return getMode() == MODE_ENTITY;
    }

    public int getSpeed() {
        return this.entityData.get(SPEED);
    }

    public void setSpeed(int speed) {
        this.entityData.set(SPEED, Math.max(1, speed));
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("targetPos")) this.targetPos = BlockPos.of(tag.getLong("targetPos"));
        if (tag.hasUUID("targetUuid")) this.targetUuid = tag.getUUID("targetUuid");
        setMode(tag.getInt("mode"));
        setSpeed(tag.getInt("speed"));
        setRemainingTime(tag.getInt("remainingTime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetPos != null) tag.putLong("targetPos", this.targetPos.asLong());
        if (this.targetUuid != null) tag.putUUID("targetUuid", this.targetUuid);
        tag.putInt("mode", getMode());
        tag.putInt("speed", getSpeed());
        tag.putInt("remainingTime", getRemainingTime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
