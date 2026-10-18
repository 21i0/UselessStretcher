package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public abstract class TwoIDollSectionLifecycleMixin {
    @Shadow @Final private EntityAccess entity;

    @WrapMethod(method = "onMove")
    private void vanillaSectionMove(Operation<Void> original) {
        if (entity instanceof TwoIDollEntity doll) {
            try (var scope = TwoILifecycleGuard.allow(doll)) { original.call(); }
        } else original.call();
    }

    @WrapMethod(method = "onRemove")
    private void vanillaRemoval(Entity.RemovalReason reason, Operation<Void> original) {
        if (entity instanceof TwoIDollEntity doll) {
            if (doll.blocksDestructiveRemoval(reason)) return;
            try (var scope = TwoILifecycleGuard.allow(doll)) { original.call(reason); }
        } else original.call(reason);
    }
}
