package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.acceleration.ExtraTickClock;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the bowl's scheduler sees extra time; the world and ritual conditions keep real time. */
@Pseudo
@Mixin(targets = "com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity", remap = false)
public abstract class OccultismRitualClockMixin implements ExtraTickClock {
    @Unique private long uselessStretcher$extraTicks;

    @Override
    public void uselessStretcher$advanceClock() {
        uselessStretcher$extraTicks++;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getGameTime()J"))
    private long uselessStretcher$ritualTime(Level level) {
        return level.getGameTime() + uselessStretcher$extraTicks;
    }
}
