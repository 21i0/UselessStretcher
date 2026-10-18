package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/** Shared local conditions for companion, carried doll and shelf decoration. */
public final class TwoIDollEnvironment {
    public static final int RAIN = 1, COLD = 2, THUNDER = 4;
    private TwoIDollEnvironment() { }

    public static int conditions(Level level, BlockPos pos) {
        if (level == null) return 0;
        Biome biome = level.getBiome(pos).value();
        boolean weather = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && biome.getPrecipitationAt(pos) != Biome.Precipitation.NONE;
        int flags = biome.coldEnoughToSnow(pos) ? COLD : 0;
        if (weather && level.isRainingAt(pos)) flags |= RAIN;
        // Thunder can be heard under a roof, but not in dimensions without weather.
        if (weather && level.isThundering()) flags |= THUNDER;
        return flags;
    }

    /** World clock prevents reloads and form changes from restarting a sneeze. */
    public static float sneezeProgress(double time, int salt) {
        double phase = Math.floorMod((long)Math.floor(time) + salt, 420) + time - Math.floor(time);
        return phase < 36 ? (float)(phase / 36) : -1;
    }
}
