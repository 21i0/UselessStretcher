package com.sorrowmist.useless.stretcher.compat;

import mcjty.rftoolsbuilder.modules.builder.BuilderModule;
import mcjty.rftoolsbuilder.modules.builder.blocks.BuilderTileEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

final class RftoolsBuilderAccelerationCompat {
    private static final BlockEntityTicker<BlockEntity> TICKER = (level, pos, state, target) ->
            ((BuilderTileEntity) target).tickServer();

    private RftoolsBuilderAccelerationCompat() { }

    static BlockEntityTicker<BlockEntity> ticker(BlockEntity target) {
        // Replace the generic wrapper, never run both. Keep the builder's own card, redstone,
        // inventory, energy, permissions, chunk and quarry logic as the sole work executor.
        return target instanceof BuilderTileEntity ? TICKER : null;
    }

    static boolean isWorking(BlockEntity target) {
        return target instanceof BuilderTileEntity builder && builder.isMachineEnabled()
                && builder.getData(BuilderModule.BUILDER_DATA).scan() != null;
    }
}
