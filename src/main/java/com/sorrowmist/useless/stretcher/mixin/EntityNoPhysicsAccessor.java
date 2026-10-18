package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Controlled access to vanilla's collision bypass flag for the staff phase mode. */
@Mixin(Entity.class)
public interface EntityNoPhysicsAccessor {
    @Accessor("noPhysics")
    boolean uselessStretcher$getNoPhysics();

    @Accessor("noPhysics")
    void uselessStretcher$setNoPhysics(boolean value);
}
