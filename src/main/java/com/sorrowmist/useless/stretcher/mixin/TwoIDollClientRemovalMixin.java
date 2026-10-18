package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Server removal packets and vanilla entity replacement still remove the client copy. */
@Mixin(ClientLevel.class)
public abstract class TwoIDollClientRemovalMixin {
    @Shadow public abstract Entity getEntity(int id);

    @WrapMethod(method = "removeEntity")
    private void serverAuthorizedRemoval(int id, Entity.RemovalReason reason, Operation<Void> original) {
        if (getEntity(id) instanceof TwoIDollEntity doll) {
            try (var scope = TwoILifecycleGuard.allow(doll)) { original.call(id, reason); }
        } else original.call(id, reason);
    }
}
