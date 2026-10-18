package com.sorrowmist.useless.stretcher.compat;

import mekanism.api.radiation.IRadiationManager;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.world.entity.LivingEntity;

/** Loaded only when Mekanism is installed. Uses the same native shielding pipeline as AdvancedAE. */
public final class MekanismRadiationCompat {
    private MekanismRadiationCompat() { }

    public static void clear(LivingEntity entity) {
        var radiation = entity.getCapability(Capabilities.RADIATION_ENTITY);
        if (radiation != null && radiation.getRadiation() > IRadiationManager.INSTANCE.baselineRadiation())
            radiation.set(IRadiationManager.INSTANCE.baselineRadiation());
    }
}
