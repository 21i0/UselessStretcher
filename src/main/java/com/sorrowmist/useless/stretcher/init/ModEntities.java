package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UselessStretcherMod.MODID);

    public static final Supplier<EntityType<WondrousStaffAccelerationEntity>> WONDROUS_STAFF_ACCELERATION =
            ENTITIES.register("wondrous_staff_acceleration",
                    () -> EntityType.Builder.<WondrousStaffAccelerationEntity>of(
                                    WondrousStaffAccelerationEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .noSummon()
                            .build("wondrous_staff_acceleration"));

    private ModEntities() {
    }
}
