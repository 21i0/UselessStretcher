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
@Mixin(value = BeefTimeAcceleration.class, remap = false)
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

    /** Newer upstream tools have their own entity path, separate from tryUse(block). */
    @Inject(method = "tryUseOnEntity(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$entityFallback(net.minecraft.world.item.ItemStack staff,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.LivingEntity target,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (target instanceof com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity)
            cir.setReturnValue(InteractionResult.FAIL);
        else if (staff.getItem() instanceof WondrousStaffItem) cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "accelerateEntityTimers", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$stopDollTimers(net.minecraft.world.entity.LivingEntity target,
            int speed, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (target instanceof com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity) ci.cancel();
    }
}
