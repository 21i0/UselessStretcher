package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fully disables the inherited {@link BeefTimeAcceleration} for the wondrous staff so it can
 * never override our own gear-based acceleration, regardless of interaction-path priority.
 */
@Mixin(BeefTimeAcceleration.class)
public abstract class BeefTimeAccelerationMixin {

    @Inject(
            method = "tryUse(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true)
    private static void uselessStretcher$disableForStaff(UseOnContext ctx,
                                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getItemInHand().getItem() instanceof WondrousStaffItem) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
