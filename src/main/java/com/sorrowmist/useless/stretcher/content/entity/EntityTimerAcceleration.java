package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.mixin.BeeTimerAccessor;
import com.sorrowmist.useless.stretcher.mixin.TurtleTimerAccessor;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;

/** Constant-count timer updates, compatible with Useless Mod versions before its timer API. */
public final class EntityTimerAcceleration {
    public static final int MAX_SPEED = 32768;
    public static final int DEFAULT_SPEED = 256;
    private EntityTimerAcceleration() {}

    public static int normalizeSpeed(int speed) {
        return Integer.highestOneBit(Math.max(2, Math.min(MAX_SPEED, speed)));
    }

    public static void advance(LivingEntity target, int speed) {
        int extra = normalizeSpeed(speed);
        if (target instanceof AgeableMob ageable) {
            int age = ageable.getAge();
            if (age != 0) {
                int step = (int) Math.min(Math.abs((long) age), extra);
                ageable.setAge(age < 0 ? age + step : age - step);
            }
        }
        if (target instanceof Chicken chicken && !chicken.isBaby() && !chicken.isChickenJockey()) {
            chicken.eggTime = Math.max(0, chicken.eggTime - extra);
        }
        if (target instanceof Sheep sheep && !sheep.isBaby() && sheep.isSheared()) sheep.setSheared(false);
        if (target instanceof Bee bee) {
            bee.resetTicksWithoutNectarSinceExitingHive();
            bee.setStayOutOfHiveCountdown(0);
            if (!bee.hasNectar()) ((BeeTimerAccessor) bee).uselessStretcher$setHasNectar(true);
        }
        if (target instanceof Turtle turtle && turtle.isLayingEgg()) {
            TurtleTimerAccessor access = (TurtleTimerAccessor) turtle;
            access.uselessStretcher$setLayEggCounter((int) Math.min(Integer.MAX_VALUE,
                    (long) access.uselessStretcher$getLayEggCounter() + extra));
        }
        // Love duration and potion durations are intentionally not shortened.
    }
}
