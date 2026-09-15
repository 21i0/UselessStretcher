package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.StaffLeafRewardEntity;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
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
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSummon()
                            .build("wondrous_staff_acceleration"));

    public static final Supplier<EntityType<StaffLeafRewardEntity>> STAFF_LEAF_REWARD =
            ENTITIES.register("staff_leaf_reward",
                    () -> EntityType.Builder.<StaffLeafRewardEntity>of(
                                    StaffLeafRewardEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .fireImmune()
                            .noSummon()
                            .build("staff_leaf_reward"));

    public static final Supplier<EntityType<TimeFlowEntity>> TIME_FLOW =
            ENTITIES.register("time_flow",
                    () -> EntityType.Builder.<TimeFlowEntity>of(TimeFlowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(10)
                            .updateInterval(10)
                            .fireImmune()
                            .noSummon()
                            .build("time_flow"));

    private ModEntities() {
    }
}
