package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.content.blockentity.TwoIDollDisplayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UselessStretcherMod.MODID);

    public static final Supplier<BlockEntityType<OmniversalMyriadBlockEntity>> OMNIVERSAL_MYRIAD =
            BLOCK_ENTITIES.register("omniversal_myriad",
                    () -> BlockEntityType.Builder.of(OmniversalMyriadBlockEntity::new, ModBlocks.OMNIVERSAL_MYRIAD.get())
                            .build(null));

    public static final Supplier<BlockEntityType<TwoIDollDisplayBlockEntity>> TWO_I_DOLL_DISPLAY =
            BLOCK_ENTITIES.register("two_i_doll_display",
                    () -> BlockEntityType.Builder.of(TwoIDollDisplayBlockEntity::new, ModBlocks.TWO_I_DOLL_DISPLAY.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
