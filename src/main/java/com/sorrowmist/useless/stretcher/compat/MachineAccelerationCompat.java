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
        return (OCCULTISM && com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call("occultism", "com.sorrowmist.useless.stretcher.compat.OccultismAccelerationCompat", "isWorking", Boolean.class, false, new Class<?>[]{BlockEntity.class}, target))
                || (RFTOOLS && com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call("rftoolsbuilder", "com.sorrowmist.useless.stretcher.compat.RftoolsBuilderAccelerationCompat", "isWorking", Boolean.class, false, new Class<?>[]{BlockEntity.class}, target));
    }

    /** A nullable answer separates authoritative idle from an unsupported target. */
    public static Boolean knownActivity(BlockEntity target) {
        return OCCULTISM ? com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call(
                "occultism", "com.sorrowmist.useless.stretcher.compat.OccultismAccelerationCompat",
                "knownActivity", Boolean.class, null, new Class<?>[]{BlockEntity.class}, target) : null;
    }

    public static BlockEntityTicker<BlockEntity> ticker(BlockEntity target) {
        return RFTOOLS ? com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call("rftoolsbuilder", "com.sorrowmist.useless.stretcher.compat.RftoolsBuilderAccelerationCompat", "ticker", BlockEntityTicker.class, null, new Class<?>[]{BlockEntity.class}, target) : null;
    }

    public static boolean advanceSpirit(net.minecraft.world.entity.LivingEntity target, int extra) {
        return OCCULTISM && com.sorrowmist.useless.compat.forge1201.OptionalIntegration.call("occultism", "com.sorrowmist.useless.stretcher.compat.OccultismAccelerationCompat", "advanceSpirit", Boolean.class, false, new Class<?>[]{net.minecraft.world.entity.LivingEntity.class,int.class}, target,extra);
    }
}
