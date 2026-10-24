package com.sorrowmist.useless.stretcher.content.blockentity;

import com.sorrowmist.useless.stretcher.init.ModBlockEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** An inert display, not a mob: stores one full stack and never has a server ticker. */
public final class TwoIDollDisplayBlockEntity extends BlockEntity {
    private ItemStack doll = ItemStack.EMPTY;
    private boolean spinning;
    private float rotationOffset;
    private long spinStartedAt;

    public TwoIDollDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TWO_I_DOLL_DISPLAY.get(), pos, state);
    }

    public void setDoll(ItemStack stack) {
        doll = stack.is(ModItems.TWO_I_DOLL.get()) ? stack.copyWithCount(1) : ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Always copy: pick-block, loot and shift-pickup must not share a mutable stack. */
    public ItemStack copyDoll() {
        return doll.isEmpty() ? new ItemStack(ModItems.TWO_I_DOLL.get()) : doll.copyWithCount(1);
    }

    public boolean isSpinning() { return spinning; }

    /** Server world time anchors animation for all observers, with no per-tick updates. */
    public float rotationAt(double gameTime) {
        return (float)((rotationOffset + (spinning ? Math.max(0, gameTime - spinStartedAt) * 1.8 : 0)) % 360);
    }

    public void toggleSpinning() {
        if (level == null || level.isClientSide) return;
        rotationOffset = rotationAt(level.getGameTime());
        spinStartedAt = level.getGameTime();
        spinning = !spinning;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        HolderLookup.Provider registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        if (!doll.isEmpty()) tag.put("Doll", com.sorrowmist.useless.compat.forge1201.nbt.NativeStackNbt.saveItem(doll, registries));
        tag.putBoolean("Spinning", spinning);
        tag.putFloat("SpinAngle", rotationOffset);
        tag.putLong("SpinStart", spinStartedAt);
    }

    @Override
    public void load(CompoundTag tag) {
        HolderLookup.Provider registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        ItemStack restored = tag.contains("Doll")
                ? com.sorrowmist.useless.compat.forge1201.nbt.NativeStackNbt.readItem(registries, tag.getCompound("Doll")) : ItemStack.EMPTY;
        doll = restored.is(ModItems.TWO_I_DOLL.get()) ? restored.copyWithCount(1) : ItemStack.EMPTY;
        spinning = tag.getBoolean("Spinning");
        float angle = tag.getFloat("SpinAngle");
        rotationOffset = Float.isFinite(angle) ? angle % 360 : 0;
        spinStartedAt = tag.getLong("SpinStart");
    }

    @Override
    public CompoundTag getUpdateTag() {
        HolderLookup.Provider registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
