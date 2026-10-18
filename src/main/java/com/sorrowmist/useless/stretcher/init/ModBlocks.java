package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.block.OmniversalMyriadBlock;
import com.sorrowmist.useless.stretcher.content.block.TwoIDollDisplayBlock;
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

    /** Decorative block backing the 2i BlockItem. */
    public static final DeferredBlock<Block> TWO_I_DOLL_DISPLAY = BLOCKS.register("two_i_doll_display",
            () -> new TwoIDollDisplayBlock(BlockBehaviour.Properties.of()
                    .strength(0.2f)
                    .noOcclusion()
                    .noCollission()));

    private ModBlocks() {
    }
}
