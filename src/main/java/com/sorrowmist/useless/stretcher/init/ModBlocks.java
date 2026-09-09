package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.block.OmniversalMyriadBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessStretcherMod.MODID);

    public static final DeferredBlock<Block> OMNIVERSAL_MYRIAD = BLOCKS.register("omniversal_myriad",
            () -> new OmniversalMyriadBlock(BlockBehaviour.Properties.of()
                    .strength(0.8f)
                    .noOcclusion()));

    private ModBlocks() {
    }
}
