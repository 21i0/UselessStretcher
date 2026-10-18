package com.sorrowmist.useless.stretcher.compat;

import com.klikli_dev.occultism.common.blockentity.DimensionalBattlefieldBlockEntity;
import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Occultism's production lives in both block entities and spirit jobs. */
final class OccultismAccelerationCompat {
    private OccultismAccelerationCompat() { }

    static boolean isWorking(BlockEntity target) {
        if (target instanceof DimensionalMineshaftBlockEntity miner) return miner.miningTime > 0;
        if (target instanceof DimensionalBattlefieldBlockEntity battlefield) return battlefield.mobHealth > 0;
        return target instanceof GoldenSacrificialBowlBlockEntity bowl && bowl.getCurrentRitualRecipe() != null;
    }

    static boolean advanceSpirit(LivingEntity target, int extra) {
        if (!(target instanceof SpiritEntity spirit) || !(spirit.level() instanceof ServerLevel level)) return false;
        if (!spirit.isAlive()) return true;
        if (!spirit.isInitialized()) spirit.init();
        var job = spirit.getJob().orElse(null);
        if (job == null) return true;
        // Other jobs can change world time/weather or depend on movement/brain sensors. Timer
        // mode only repeats inventory processing; their normal AI remains on the real clock.
        boolean processor = job instanceof com.klikli_dev.occultism.common.entity.job.CrusherJob
                || job instanceof com.klikli_dev.occultism.common.entity.job.SmelterJob
                || job instanceof com.klikli_dev.occultism.common.entity.job.CrystallizerJob;
        if (!processor || spirit.getMainHandItem().isEmpty()) return true;
        // Timer mode advances the real job without replaying movement, AI or lifetime. Bound
        // expensive jobs by the same shared count/time allowance used for machine acceleration.
        int admitted = AccelerationExecutionBudget.take(level.getServer(), spirit, extra);
        long started = System.nanoTime();
        long deadline = AccelerationExecutionBudget.deadline(level.getServer(), spirit);
        try {
            for (int i = 0; i < admitted && spirit.isAlive() && spirit.getJob().orElse(null) == job
                    && !spirit.getMainHandItem().isEmpty() && System.nanoTime() < deadline; i++) job.update();
        } finally {
            AccelerationExecutionBudget.recordWork(level.getServer(), spirit, started);
        }
        return true;
    }
}
