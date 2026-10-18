package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.event.UsefulArmorController;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard both commands and direct mod calls to the authoritative game-mode controller. */
@Mixin(ServerPlayerGameMode.class)
public abstract class UsefulArmorGameModeMixin {
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "changeGameModeForPlayer", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$lockCreative(GameType requested, CallbackInfoReturnable<Boolean> cir) {
        if (requested != GameType.CREATIVE && UsefulArmorController.hasCompleteSet(player)) {
            cir.setReturnValue(false);
        }
    }
}
