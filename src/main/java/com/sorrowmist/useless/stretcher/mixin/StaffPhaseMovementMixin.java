package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep spectator-like movement without changing game mode or interaction permissions. */
@Mixin(Player.class)
public abstract class StaffPhaseMovementMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void phaseBeforeMovement(CallbackInfo ci) {
        StaffPhaseManager.movementTick((Player) (Object) this);
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void phaseBeforeTravel(net.minecraft.world.phys.Vec3 movement, CallbackInfo ci) {
        StaffPhaseManager.movementTick((Player) (Object) this);
    }
}
