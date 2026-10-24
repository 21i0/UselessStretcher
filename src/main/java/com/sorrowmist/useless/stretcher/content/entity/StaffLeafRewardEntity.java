package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Untouchable visual carrier used while a player's one-time leaf reward is waiting for space. */
public final class StaffLeafRewardEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DOLL_REWARD =
            SynchedEntityData.defineId(StaffLeafRewardEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID ownerId;
    private boolean dollReward;
    private ItemStack displayStack = new ItemStack(ModItems.WONDROUS_STAFF.get());


    public StaffLeafRewardEntity(EntityType<? extends StaffLeafRewardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public StaffLeafRewardEntity(Level level, UUID ownerId) {
        this(ModEntities.STAFF_LEAF_REWARD.get(), level);
        this.ownerId = ownerId;
    }

    public StaffLeafRewardEntity(Level level, UUID ownerId, boolean dollReward) {
        this(level, ownerId);
        setDollReward(dollReward);
        this.displayStack = new ItemStack(dollReward ? ModItems.TWO_I_DOLL.get() : ModItems.WONDROUS_STAFF.get());
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.clearFire();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public ItemStack getDisplayStack() {
        // The carrier can be created on the server and rendered on a client that only
        // learns its type through entity synchronization. Read the synced value here so
        // a full inventory reward never appears as a staff on the client.
        boolean syncedDoll = entityData.get(DOLL_REWARD);
        if (syncedDoll != dollReward) {
            dollReward = syncedDoll;
            displayStack = new ItemStack(dollReward ? ModItems.TWO_I_DOLL.get() : ModItems.WONDROUS_STAFF.get());
        }
        return displayStack;
    }

    public boolean isDollReward() {
        return entityData.get(DOLL_REWARD);
    }

    private void setDollReward(boolean value) {
        dollReward = value;
        entityData.set(DOLL_REWARD, value);
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
    protected void defineSynchedData() {
        this.entityData.define(DOLL_REWARD, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        setDollReward(tag.getBoolean("doll"));
        displayStack = new ItemStack(dollReward ? ModItems.TWO_I_DOLL.get() : ModItems.WONDROUS_STAFF.get());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("owner", ownerId);
        tag.putBoolean("doll", dollReward);
    }
}
