package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kept as a source-only compatibility marker.  Player ticking is now handled
 * in StaffServerTickHandler, once per server tick, to avoid duplicate ticks.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerTimeStopMixin {
    @Shadow public net.minecraft.server.level.ServerPlayer player;

    @Inject(method = "tick", at = @At("HEAD"))
    private void uselessStretcher$tickStaffHolder(CallbackInfo callback) {
        // Intentionally empty. The mixin is not listed in the active server
        // mixins; this body remains for old development mappings only.
    }
}
