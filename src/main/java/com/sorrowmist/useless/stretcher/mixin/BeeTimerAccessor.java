package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Bee.class)
public interface BeeTimerAccessor {
    @Invoker("setHasNectar")
    void uselessStretcher$setHasNectar(boolean hasNectar);
}
