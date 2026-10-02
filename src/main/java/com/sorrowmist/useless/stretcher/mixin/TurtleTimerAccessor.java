package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Turtle.class)
public interface TurtleTimerAccessor {
    @Accessor("layEggCounter") int uselessStretcher$getLayEggCounter();
    @Accessor("layEggCounter") void uselessStretcher$setLayEggCounter(int value);
}
