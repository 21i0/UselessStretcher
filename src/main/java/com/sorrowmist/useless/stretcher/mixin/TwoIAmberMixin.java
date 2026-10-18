package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cy.jdkdigital.productivebees.common.block.entity.AmberBlockEntity", remap = false)
public abstract class TwoIAmberMixin {
    @Inject(method = "setEntity", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$rejectCapture(Mob target, CallbackInfo ci) {
        if (target instanceof TwoIDollEntity) ci.cancel();
    }

    /** Also covers thawing and item previews of amber created before this fix. */
    @Inject(method = "createEntity", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$rejectStoredDoll(Level level, CompoundTag tag,
                                                        CallbackInfoReturnable<Entity> cir) {
        if (com.sorrowmist.useless.stretcher.content.entity.TwoICaptureProtection.containsDoll(tag))
            cir.setReturnValue(null);
    }
}
