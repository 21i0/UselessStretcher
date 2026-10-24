package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge Builder stores its work cursor directly, before the 1.21 data API. */
@Pseudo
@Mixin(targets = "mcjty.rftoolsbuilder.modules.builder.blocks.BuilderTileEntity", remap = false)
public interface BuilderProgressAccessor {
    @Accessor("scan") BlockPos uselessStretcher$getScan();
}
