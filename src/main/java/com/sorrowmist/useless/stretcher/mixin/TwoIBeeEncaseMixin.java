package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop before placing amber: removal protection alone leaves a duplicating container. */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivebees.util.BeeHelper", remap = false)
public abstract class TwoIBeeEncaseMixin {
    @Inject(method = "encaseMob", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$rejectEncase(Mob target, Level level, Direction direction, CallbackInfo ci) {
        if (target instanceof TwoIDollEntity) ci.cancel();
    }
}
