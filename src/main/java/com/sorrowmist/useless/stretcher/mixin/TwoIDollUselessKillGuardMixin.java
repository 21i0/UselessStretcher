package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protect before upstream's forced-kill fallback mutates world indexes, not after removal. */
@Mixin(targets = "com.sorrowmist.useless.content.items.EndlessBeafItem", remap = false)
public abstract class TwoIDollUselessKillGuardMixin {
    // Newer upstream versions share this predicate between direct attacks, AoE and the aura.
    @Inject(method = "isProtectedTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$protected(Player player, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof TwoIDollEntity) cir.setReturnValue(true);
    }

    @Inject(method = "isForceKillBlacklisted", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$blacklisted(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof TwoIDollEntity) cir.setReturnValue(true);
    }

    @Inject(method = "forceKillLivingEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$forceKill(ItemStack stack, LivingEntity target, Player player,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof TwoIDollEntity) cir.setReturnValue(false);
    }

    @Inject(method = "forceDie", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$forceDie(ServerLevel level, LivingEntity target, DamageSource source,
                                                boolean dropLoot, CallbackInfo ci) {
        if (target instanceof TwoIDollEntity) ci.cancel();
    }

    @Inject(method = "removeEntityFromServerStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$storage(ServerLevel level, Entity target, CallbackInfo ci) {
        if (target instanceof TwoIDollEntity) ci.cancel();
    }
}
