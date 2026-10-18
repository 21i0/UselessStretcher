package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;

/** Visibility changes must still stop tracking/ticking without destructive removal. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class TwoIDollManagerLifecycleMixin {
    @WrapMethod(method = {"stopTicking", "stopTracking"})
    private void vanillaVisibilityTransition(EntityAccess entity, Operation<Void> original) {
        if (entity instanceof TwoIDollEntity doll) {
            try (var scope = TwoILifecycleGuard.allow(doll)) { original.call(entity); }
        } else original.call(entity);
    }
}
