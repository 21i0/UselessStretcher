package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 2i is a placed mascot, never a captured mob; block the upstream pig-carrier fallback too. */
@Mixin(targets = "com.sorrowmist.useless.utils.UselessItemUtils", remap = false)
public abstract class TwoIDollSpawnEggMixin {
    @Inject(method = "tryCaptureSpawnEgg", at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$skipDoll(LivingEntity killed, ItemStack tool, Player player,
                                                   CallbackInfo ci) {
        if (killed instanceof TwoIDollEntity) ci.cancel();
    }
}
