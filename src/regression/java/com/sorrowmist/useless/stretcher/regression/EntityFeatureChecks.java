package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.mixin.TurtleTimerAccessor;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.List;
import java.util.UUID;

final class EntityFeatureChecks {
    static void run(ServerLevel level) throws Exception {
        for (var type : List.of(EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.IRON_GOLEM,
                EntityType.SNOW_GOLEM, EntityType.CHICKEN, EntityType.ENDER_DRAGON)) {
            check(WondrousStaffSummoning.canSummonType(type), "living summon eligible: " + type);
        }
        for (var type : List.of(EntityType.PLAYER, EntityType.ITEM, EntityType.LIGHTNING_BOLT, EntityType.ARROW)) {
            check(!WondrousStaffSummoning.canSummonType(type), "utility summon excluded: " + type);
        }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "EntityRegression"));
        player.setPos(4.5, 160, 4.5);
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        check(!WondrousStaffAcceleration.usesEntityTimers(staff), "old staff defaults to complete ticks");
        boolean summonEnabled = StretcherConfig.enableStaffSummon();
        try {
            StretcherConfig.SERVER_STAFF_SUMMON.set(true);
            WondrousStaffSummoning.summon(player, staff, List.of("minecraft:villager"));
            var villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(5));
            check(villagers.size() == 1 && villagers.getFirst().isNoAi(), "actual villager summon succeeds with disabled AI");
            villagers.forEach(Villager::discard);
        } finally { StretcherConfig.SERVER_STAFF_SUMMON.set(summonEnabled); }

        var chicken = new CountingChicken(level);
        chicken.setPos(4.5, 150, 4.5);
        check(level.addFreshEntity(chicken), "timer target added");
        chicken.setAge(-24000);
        chicken.eggTime = 12000;
        chicken.setInLoveTime(600);
        chicken.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        var marker = new WondrousStaffAccelerationEntity(level, chicken, 16);
        check(!marker.isEntityTimerMode(), "old marker defaults to complete ticks");
        marker.setEntityTimerMode(true);
        marker.setSpeed(32768);
        marker.setPermanent();
        marker.setEntityAiDisabled(true);
        marker.tick();
        check(chicken.calls == 0 && chicken.getAge() == 0 && chicken.eggTime == 0,
                "32768 advances timers without invoking full entity ticks");
        check(chicken.getInLoveTime() == 600 && chicken.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 200,
                "love window and potion durations preserved");
        check(chicken.isNoAi() && marker.isPermanent(), "AI setting and permanent duration preserved");
        var saved = marker.saveWithoutId(new CompoundTag());
        var restored = new WondrousStaffAccelerationEntity(level, chicken, 2);
        restored.load(saved);
        check(restored.isEntityTimerMode() && restored.getSpeed() == 32768 && restored.isPermanent(),
                "timer mode and multiplier survive entity save/load");
        saved.remove("entityTimerMode");
        var legacy = new WondrousStaffAccelerationEntity(level, chicken, 2);
        legacy.load(saved);
        check(!legacy.isEntityTimerMode(), "legacy save never auto-switches to timers");
        marker.setRemainingTime(1);
        marker.tick();
        marker.tick();
        check(marker.isRemoved() && !chicken.isNoAi(), "expiration stops timer mode and restores AI");
        chicken.discard();

        var sheep = EntityType.SHEEP.create(level);
        sheep.setSheared(true);
        EntityTimerAcceleration.advance(sheep, 2);
        check(!sheep.isSheared(), "sheep wool regrowth");
        var bee = EntityType.BEE.create(level);
        EntityTimerAcceleration.advance(bee, 2);
        check(bee.hasNectar(), "bee nectar accessor works on old upstream");
        var turtle = EntityType.TURTLE.create(level);
        var laying = net.minecraft.world.entity.animal.Turtle.class.getDeclaredMethod("setLayingEgg", boolean.class);
        laying.setAccessible(true);
        laying.invoke(turtle, true);
        EntityTimerAcceleration.advance(turtle, 32768);
        check(((TurtleTimerAccessor) turtle).uselessStretcher$getLayEggCounter() >= 32768, "laying turtle timer advances");
        sheep.setAge(6000);
        EntityTimerAcceleration.advance(sheep, 32768);
        check(sheep.getAge() == 0, "breeding cooldown advances");

        var villager = new CountingVillager(level);
        villager.setPos(5.5, 150, 5.5);
        level.addFreshEntity(villager);
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, 0.05F);
        for (int i = 0; i < 12; i++) offer.increaseUses();
        villager.getOffers().clear();
        villager.getOffers().add(offer);
        var restocker = new WondrousStaffAccelerationEntity(level, villager, 2);
        restocker.setEntityTimerMode(true);
        restocker.tick();
        check(villager.restocks == 1 && !offer.isOutOfStock(), "villager restock works");
        for (int i = 0; i < 12; i++) offer.increaseUses();
        for (int i = 0; i < 19; i++) restocker.tick();
        check(villager.restocks == 1, "restock throttled for twenty ticks");
        restocker.tick();
        check(villager.restocks == 2, "restock resumes after cooldown");
        restocker.discard();
        villager.discard();

        check(EntityTimerAcceleration.normalizeSpeed(Integer.MAX_VALUE) == 32768
                && EntityTimerAcceleration.normalizeSpeed(Integer.MIN_VALUE) == 2, "bounded multiplier input");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            var payload = new Network.EntityTimerSettingsPayload(true, 32768, true);
            Network.EntityTimerSettingsPayload.STREAM_CODEC.encode(buffer, payload);
            check(payload.equals(Network.EntityTimerSettingsPayload.STREAM_CODEC.decode(buffer)), "timer settings packet roundtrip");
        } finally { buffer.release(); }
        staff.set(StretcherComponents.ENTITY_TIMER_MODE.get(), true);
        staff.set(StretcherComponents.ENTITY_TIMER_SPEED.get(), 32768);
        staff.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 16);
        check(WondrousStaffAcceleration.getSpeed(staff) == 16 && WondrousStaffAcceleration.getEntityTimerSpeed(staff) == 32768,
                "timer settings never change machine multiplier");
        LogUtils.getLogger().info("REGRESSION 1.5.2 entities: summon, timers, AI, legacy saves, duration, restocking, packets and independent speeds passed");
    }

    private static final class CountingChicken extends Chicken {
        int calls;
        CountingChicken(ServerLevel level) { super(EntityType.CHICKEN, level); }
        @Override public void tick() { calls++; super.tick(); }
    }
    private static final class CountingVillager extends Villager {
        int restocks;
        CountingVillager(ServerLevel level) { super(EntityType.VILLAGER, level); }
        @Override public void restock() { restocks++; super.restock(); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
