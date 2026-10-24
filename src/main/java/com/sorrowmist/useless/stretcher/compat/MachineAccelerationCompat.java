package com.sorrowmist.useless.stretcher.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraftforge.fml.ModList;

/** Optional adapters shared by single-target and range acceleration, with no hard mod dependency. */
public final class MachineAccelerationCompat {
    private static final boolean OCCULTISM = ModList.get().isLoaded("occultism");
    private static final boolean RFTOOLS = ModList.get().isLoaded("rftoolsbuilder");

    private MachineAccelerationCompat() { }

    public static boolean isWorking(BlockEntity target) {
        return (OCCULTISM && OccultismAccelerationCompat.isWorking(target))
                || (RFTOOLS && RftoolsBuilderAccelerationCompat.isWorking(target));
    }

    /** A nullable answer separates authoritative idle from an unsupported target. */
    public static Boolean knownActivity(BlockEntity target) {
        return OCCULTISM ? OccultismAccelerationCompat.knownActivity(target) : null;
    }

    public static BlockEntityTicker<BlockEntity> ticker(BlockEntity target) {
        return RFTOOLS ? RftoolsBuilderAccelerationCompat.ticker(target) : null;
    }

    public static boolean advanceSpirit(net.minecraft.world.entity.LivingEntity target, int extra) {
        return OCCULTISM && OccultismAccelerationCompat.advanceSpirit(target, extra);
    }
}
