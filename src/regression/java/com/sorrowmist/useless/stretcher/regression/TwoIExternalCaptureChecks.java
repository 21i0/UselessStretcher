package com.sorrowmist.useless.stretcher.regression;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Loads the actual optional capture classes so optional mixins are exercised, not just compiled. */
final class TwoIExternalCaptureChecks {
    private static final TagKey<EntityType<?>> ARS_JAR = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "jar_blacklist"));
    private static final TagKey<EntityType<?>> PB_AMBER = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("productivebees", "bee_encase_blacklist"));

    private TwoIExternalCaptureChecks() { }

    static void run(ServerLevel level) throws Exception {
        EntityType<TwoIDollEntity> type = ModEntities.TWO_I_DOLL.get();
        if (ModList.get().isLoaded("ars_nouveau")) {
            check(type.is(ARS_JAR), "Ars Nouveau jar blacklist contains 2i");
            Class<?> ritual = Class.forName("com.hollingsworth.arsnouveau.common.ritual.RitualMobCapture");
            Object capture = ritual.getConstructor().newInstance();
            Method canJar = ritual.getMethod("canJar", Entity.class);
            TwoIDollEntity doll = type.create(level);
            check(doll != null && !(Boolean) canJar.invoke(capture, doll),
                    "Ars Nouveau ritual refuses 2i before jar serialization");
            Class<?> jarType = Class.forName("com.hollingsworth.arsnouveau.common.block.tile.MobJarTile");
            Block jarBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ars_nouveau:mob_jar"));
            var pos = new BlockPos(8, 180, 8);
            var jar = (BlockEntity) jarType.getConstructor(BlockPos.class, BlockState.class)
                    .newInstance(pos, jarBlock.defaultBlockState());
            jar.setLevel(level);
            CompoundTag before = jar.saveWithFullMetadata(level.registryAccess());
            check(!(Boolean) jarType.getMethod("setEntityData", Entity.class).invoke(jar, doll), "direct jar capture rejects 2i");
            jarType.getMethod("writeSimple", Entity.class).invoke(jar, doll);
            check(before.equals(jar.saveWithFullMetadata(level.registryAccess())),
                    "both direct jar capture paths preserve all container data");
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "useless_stretcher:two_i_doll");
            Method decode = jarType.getMethod("loadEntityFromTag", Level.class, CompoundTag.class);
            check(decode.invoke(null, level, tag) == null, "legacy 2i jar cannot release or tick a clone");
            tag.putString("id", "minecraft:chicken");
            check(decode.invoke(null, level, tag) instanceof net.minecraft.world.entity.animal.Chicken,
                    "ordinary jar loading still works");
        }
        if (ModList.get().isLoaded("productivebees")) {
            check(type.is(PB_AMBER), "Productive Bees amber blacklist contains 2i");
            TwoIDollEntity doll = type.create(level);
            check(doll != null, "productive bees fixture creates 2i");
            var pos = doll.blockPosition().offset(8, 0, 0);
            doll.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            check(level.addFreshEntity(doll), "productive bees fixture registers 2i");
            try {
                Class<?> helper = Class.forName("cy.jdkdigital.productivebees.util.BeeHelper");
                Method encase = helper.getMethod("encaseMob", Mob.class, Level.class, Direction.class);
                encase.invoke(null, doll, level, Direction.UP);
                check(!doll.isRemoved() && level.getBlockState(pos).isAir(),
                        "amber encasing leaves 2i untouched and places no clone");
                var tag = new net.minecraft.nbt.CompoundTag();
                tag.putString("id", "useless_stretcher:two_i_doll");
                Class<?> amber = Class.forName("cy.jdkdigital.productivebees.common.block.entity.AmberBlockEntity");
                Method decode = amber.getMethod("createEntity", Level.class, net.minecraft.nbt.CompoundTag.class);
                check(decode.invoke(null, level, tag) == null,
                        "amber release refuses legacy serialized 2i data");
                Block amberBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("productivebees:amber"));
                Object container = amber.getConstructor(BlockPos.class, BlockState.class)
                        .newInstance(pos, amberBlock.defaultBlockState());
                amber.getMethod("setEntity", Mob.class).invoke(container, doll);
                check(amber.getField("entityTag").get(container) == null,
                        "direct amber setter refuses to serialize 2i");
                tag.putString("id", "minecraft:chicken");
                check(decode.invoke(null, level, tag) instanceof net.minecraft.world.entity.animal.Chicken,
                        "ordinary amber thawing still works");
            } finally {
                doll.discardForReclaim();
            }
        }
        if (ModList.get().isLoaded("ars_nouveau") || ModList.get().isLoaded("productivebees"))
            LogUtils.getLogger().info("REGRESSION 2i external capture: Ars jar and Productive Bees amber protections exercised");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
