package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Maintains a continuous client-only clock for accelerated vanilla cloud movement. */
public final class WondrousStaffCloudTime {
    /** Server refreshes every 10 ticks; this also recovers cleanly if an effect entity unloads. */
    private static final long SIGNAL_TIMEOUT_TICKS = 30L;
    /** Exact vanilla cloud-texture repeat period: 2048 * 12 / 0.03 renderer ticks. */
    private static final double CLOUD_TICK_PERIOD = 819200.0D;

    private static ClientLevel signalLevel;
    private static int signaledSpeed;
    private static long signalGameTime = Long.MIN_VALUE;

    private static ClientLevel renderedLevel;
    private static double lastVanillaTime = Double.NaN;
    private static double acceleratedTime;

    private WondrousStaffCloudTime() {
    }

    public static void accept(String dimension, int speed) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().toString().equals(dimension)) return;
        signalLevel = level;
        signaledSpeed = Math.max(0, Math.min(WondrousStaffAccelerationEntity.MAX_MULTIPLIER, speed));
        signalGameTime = level.getGameTime();
    }

    /**
     * Maps LevelRenderer's private vanilla tick counter onto a continuous accelerated clock.
     * Only the cloud expression reads this value; weather particles and every gameplay clock keep
     * their normal counters. The partial-tick term is folded into the returned integer as closely
     * as that vanilla integer expression permits, avoiding visible 20 Hz stepping at high gears.
     */
    public static int cloudTicks(int vanillaTicks, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            renderedLevel = null;
            lastVanillaTime = Double.NaN;
            return vanillaTicks;
        }

        float partial = Math.clamp(partialTick, 0.0F, 1.0F);
        double vanillaTime = vanillaTicks + (double) partial;
        if (level != renderedLevel || Double.isNaN(lastVanillaTime)
                || vanillaTime < lastVanillaTime) {
            renderedLevel = level;
            lastVanillaTime = vanillaTime;
            acceleratedTime = positiveModulo(vanillaTime, CLOUD_TICK_PERIOD);
        }

        int multiplier = activeMultiplier(level);
        double elapsed = vanillaTime - lastVanillaTime;
        if (elapsed > 0.0D) {
            acceleratedTime = positiveModulo(acceleratedTime + elapsed * multiplier,
                    CLOUD_TICK_PERIOD);
            lastVanillaTime = vanillaTime;
        }

        // Vanilla adds partialTick after reading this integer. Subtract it before quantizing so
        // the resulting expression stays within one renderer tick of the continuous clock.
        return (int) Math.floor(acceleratedTime - partial);
    }

    private static int activeMultiplier(ClientLevel level) {
        if (signalLevel != level || signaledSpeed <= 0 || signalGameTime == Long.MIN_VALUE) return 1;
        long age = level.getGameTime() - signalGameTime;
        return age >= 0L && age <= SIGNAL_TIMEOUT_TICKS ? signaledSpeed + 1 : 1;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }
}
