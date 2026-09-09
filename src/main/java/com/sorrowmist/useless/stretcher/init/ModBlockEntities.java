package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, UselessStretcherMod.MODID);

    public static final Supplier<BlockEntityType<OmniversalMyriadBlockEntity>> OMNIVERSAL_MYRIAD =
            BLOCK_ENTITIES.register("omniversal_myriad",
                    () -> BlockEntityType.Builder.of(OmniversalMyriadBlockEntity::new, ModBlocks.OMNIVERSAL_MYRIAD.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
