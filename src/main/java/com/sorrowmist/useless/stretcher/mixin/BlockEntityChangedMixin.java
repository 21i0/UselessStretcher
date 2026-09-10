package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the last game time a block entity called {@code setChanged()}. The staff uses this as
 * its most reliable "the machine is actually doing something" signal, which works even for
 * creative / infinite-energy machines whose FE buffer never changes.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityChangedMixin implements ChangedTickAccessor {

    @Unique
    private long uselessStretcher$lastChangedTick = -1L;

    @Inject(method = "setChanged", at = @At("HEAD"))
    private void uselessStretcher$recordChanged(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null && !self.getLevel().isClientSide) {
            uselessStretcher$lastChangedTick = self.getLevel().getGameTime();
        }
    }

    @Override
    public long uselessStretcher$getLastChangedTick() {
        return uselessStretcher$lastChangedTick;
    }
}
