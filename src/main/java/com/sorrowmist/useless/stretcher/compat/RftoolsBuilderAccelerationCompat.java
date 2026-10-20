package com.sorrowmist.useless.stretcher.compat;

import com.sorrowmist.useless.stretcher.mixin.BuilderProgressAccessor;
import mcjty.rftoolsbuilder.modules.builder.blocks.BuilderTileEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

public final class RftoolsBuilderAccelerationCompat {
    private static final BlockEntityTicker<BlockEntity> TICKER = (level, pos, state, target) ->
            ((BuilderTileEntity) target).tickServer();

    private RftoolsBuilderAccelerationCompat() { }

    public static BlockEntityTicker<BlockEntity> ticker(BlockEntity target) {
        // Replace the generic wrapper, never run both. Keep the builder's own card, redstone,
        // inventory, energy, permissions, chunk and quarry logic as the sole work executor.
        return target instanceof BuilderTileEntity ? TICKER : null;
    }

    public static boolean isWorking(BlockEntity target) {
        return target instanceof BuilderTileEntity builder && builder.isMachineEnabled()
                && ((BuilderProgressAccessor) builder).uselessStretcher$getScan() != null;
    }
}
