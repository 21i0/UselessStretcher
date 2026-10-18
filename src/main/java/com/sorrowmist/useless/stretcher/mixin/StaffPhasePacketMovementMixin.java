package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class StaffPhasePacketMovementMixin {
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER))
    private void permitPhaseMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        StaffPhaseManager.movementTick(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
