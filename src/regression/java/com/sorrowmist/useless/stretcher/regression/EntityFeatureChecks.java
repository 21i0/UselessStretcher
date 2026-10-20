package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.mixin.TurtleTimerAccessor;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
        checkTwoIDollPersistence(level);
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        check(!staff.getOrDefault(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), false),
                "phase staff default is off");
        check(staff.getOrDefault(StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), true),
                "phase interaction default is on");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, staff);
        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), true);
        check(StaffPhaseManager.state(player).phase(), "held phase staff enables player phase state");
        check(!StaffPhaseManager.blocksInteraction(player), "phase interaction remains available by default");
        StaffPhaseManager.setNoPhysics(player, false);
        StaffPhaseManager.apply(player);
        check(StaffPhaseManager.noPhysics(player), "phase staff temporarily disables block collision");
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.doTick();
        check(StaffPhaseManager.noPhysics(player) && player.getAbilities().flying && player.getAbilities().mayfly,
                "actual vanilla player tick cannot overwrite phase or free flight");
        var phaseStart = player.position();
        for (int x=4; x<=9; x++) for (int y=160; y<=163; y++)
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,4), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        player.move(net.minecraft.world.entity.MoverType.SELF, new net.minecraft.world.phys.Vec3(5,2,0));
        check(player.position().distanceToSqr(phaseStart.add(5,2,0)) < 0.0001, "actual movement traverses thick wall in all axes");
        StaffPhaseManager.setNoPhysics(player, false); // another mod changes its collision state while phasing
        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), false);
        check(StaffPhaseManager.blocksInteraction(player), "phase interaction switch can suppress interaction");
        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), false);
        StaffPhaseManager.apply(player);
        StaffPhaseManager.movementTick(player);
        check(!player.getAbilities().flying, "phase shutdown restores prior flight intent while upstream mayfly stays available");
        check(!StaffPhaseManager.noPhysics(player), "disabling phase preserves a newer external collision-state change");
        check(!StaffPhaseManager.state(player).phase(), "phase state clears when staff phase is off");
        var persistedFlight = new CompoundTag();
        persistedFlight.putBoolean("mayfly", false); persistedFlight.putBoolean("flying", false);
        player.getPersistentData().put("useless_stretcher_phase_flight", persistedFlight);
        player.getAbilities().mayfly = true; player.getAbilities().flying = true;
        StaffPhaseManager.movementTick(player);
        check(!player.getAbilities().flying && !player.getPersistentData().contains("useless_stretcher_phase_flight"),
                "restored player releases persistent phase flight snapshot after restart");
        checkCarriedPhaseStaff(player, staff);
        checkUselessModPhaseCompatibility(player, staff);
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

    private static void checkTwoIDollPersistence(ServerLevel level) throws Exception {
        UUID owner = UUID.randomUUID();
        TwoIDollEntity doll = com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "2i entity type creates");
        doll.setPos(12.5D, 150.0D, 12.5D);
        doll.setOwnerId(owner);
        doll.restoreBond(7, true);
        check(level.addFreshEntity(doll), "2i entity enters level");
        check(TwoIDollEntity.trackedFor(level.getServer(), owner).contains(doll), "loaded 2i enters remote index");
        var toucher = FakePlayerFactory.get(level, new GameProfile(owner, "2iPatRegression"));
        toucher.setPos(12.5D, 150.0D, 14.0D);
        check(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(
                com.sorrowmist.useless.stretcher.init.ModSounds.TWO_I_PAT.get()).toString().equals("useless_stretcher:two_i_pat"),
                "head pat uses our registered original sound on a dedicated server");
        check(doll.patBy(toucher) && doll.getPoseAction() == TwoIDollEntity.ACTION_PATTED,
                "pat interaction triggers the synchronized response and sound path");
        check(!doll.patBy(toucher), "pat interaction is cooldown limited");
        check(!doll.isAttackable() && doll.isPickable()
                        && !doll.hurt(level.damageSources().generic(), 100.0F),
                "2i remains ray-interactable and immune to damage");

        CompoundTag savedDoll = doll.saveWithoutId(new CompoundTag());
        TwoIDollEntity restored = com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(level);
        check(restored != null, "2i entity can be restored");
        restored.load(savedDoll);
        check(restored.getGiftScore() == 8 && restored.isInvitationUsed(), "2i gift score including pat reward and invite state persist");
        check(doll.discardForReclaim(), "authorized 2i reclaim removes the live entity");
        check(!TwoIDollEntity.trackedFor(level.getServer(), owner).contains(doll), "removed 2i leaves remote index");

        TwoIStarfieldData data = new TwoIStarfieldData();
        int first = data.slotFor(owner);
        int second = data.slotFor(UUID.randomUUID());
        check(first != second, "starfield players get distinct slots");
        data.setReturnPoint(owner, level, new net.minecraft.world.phys.Vec3(3.5D, 72.0D, -8.5D), 45.0F, 10.0F);
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        var load = TwoIStarfieldData.class.getDeclaredMethod("load", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        TwoIStarfieldData restoredData = (TwoIStarfieldData) load.invoke(null, saved, level.registryAccess());
        check(restoredData.slotFor(owner) == first, "starfield coordinate assignment survives save/load");
        check(restoredData.returnPoint(owner).equals(data.returnPoint(owner)), "return location survives save/load");
        LogUtils.getLogger().info("REGRESSION 1.6.0 2i: damage immunity, loaded-entity indexing, gift state and private starfield save/load passed");
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

    private static void checkCarriedPhaseStaff(
            net.minecraft.server.level.ServerPlayer player, ItemStack staff) {
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.selected = 0;
        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), true);
        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), true);
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            inventory.setItem(slot, staff);
            check(StaffPhaseManager.state(player).phase(), "carried phase staff found in slot " + slot);
            StaffPhaseManager.movementTick(player);
            check(StaffPhaseManager.noPhysics(player) && player.getAbilities().flying,
                    "carried staff grants collision-free flight in slot " + slot);
            inventory.setItem(slot, ItemStack.EMPTY);
            StaffPhaseManager.movementTick(player);
            check(!StaffPhaseManager.state(player).phase() && !StaffPhaseManager.noPhysics(player)
                            && !player.getAbilities().flying,
                    "removing last staff restores collision and flight in same tick, slot " + slot);
        }
        inventory.setItem(17, staff);
        var disabled = new ItemStack(ModItems.WONDROUS_STAFF.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, disabled);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, disabled.copy());
        check(StaffPhaseManager.state(player).phase() && !StaffPhaseManager.blocksInteraction(player),
                "disabled held staffs do not hide an enabled backpack staff");
        var blocked = staff.copy();
        blocked.set(StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), false);
        inventory.setItem(8, blocked);
        check(StaffPhaseManager.blocksInteraction(player), "first enabled inventory slot controls interaction");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, staff.copy());
        check(!StaffPhaseManager.blocksInteraction(player), "offhand precedes inventory interaction setting");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, blocked.copy());
        check(StaffPhaseManager.blocksInteraction(player), "main hand precedes offhand interaction setting");
        inventory.selected = 2;
        StaffPhaseManager.movementTick(player);
        check(StaffPhaseManager.state(player).phase() && StaffPhaseManager.noPhysics(player),
                "selecting another hotbar slot keeps carried phase active");
        inventory.setItem(0, ItemStack.EMPTY);
        inventory.setItem(8, ItemStack.EMPTY);
        inventory.setItem(17, ItemStack.EMPTY);
        StaffPhaseManager.movementTick(player);
        check(StaffPhaseManager.noPhysics(player), "removing inventory staffs preserves enabled offhand");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        StaffPhaseManager.movementTick(player);
        check(!StaffPhaseManager.noPhysics(player) && !player.getAbilities().flying,
                "removing final offhand staff releases phase");
        inventory.selected = 0;
        inventory.setItem(17, staff);
        LogUtils.getLogger().info("REGRESSION CARRIED STAFF PHASE PASSED: all 36 inventory slots, offhand, hotbar selection, multiple staffs, interaction priority and immediate removal restoration");
    }

    /** Exercise the optional upstream modes against phase control when that mod is present. */
    private static void checkUselessModPhaseCompatibility(
            net.minecraft.server.level.ServerPlayer player, ItemStack staff) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("useless_mod")) return;
        Class<?> components = Class.forName("com.sorrowmist.useless.core.component.UComponents");
        Object protectedHolder = components.getField("BeefInvulnerabilityEnabledComponent").get(null);
        Object stealthHolder = components.getField("BeefAdvancedStealthEnabledComponent").get(null);
        var deferredHolder = protectedHolder.getClass().getMethod("get");
        @SuppressWarnings("unchecked")
        var protectedMode = (net.minecraft.core.component.DataComponentType<Boolean>) deferredHolder.invoke(protectedHolder);
        @SuppressWarnings("unchecked")
        var stealthMode = (net.minecraft.core.component.DataComponentType<Boolean>) deferredHolder.invoke(stealthHolder);
        var beefItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("useless_mod:endless_beaf_item"));
        check(!beefItem.equals(Items.AIR), "upstream endless beef tool is registered for phase compatibility check");
        ItemStack beef = new ItemStack(beefItem);
        beef.set(protectedMode, true);
        beef.set(stealthMode, true);
        player.getInventory().add(beef);

        Class<?> handler = Class.forName("com.sorrowmist.useless.event.EventHandler");
        var protectedQuery = handler.getMethod("hasBeefInvulnerabilityItem", net.minecraft.world.entity.player.Player.class);
        var stealthQuery = handler.getMethod("hasBeefAdvancedStealthItem", net.minecraft.world.entity.player.Player.class);
        check((boolean) protectedQuery.invoke(null, player), "upstream player protection is active before phasing");
        check((boolean) stealthQuery.invoke(null, player), "upstream advanced stealth is active before phasing");

        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), true);
        StaffPhaseManager.apply(player);
        check(StaffPhaseManager.noPhysics(player), "phase and upstream protections can be active together");
        // The upstream Useless Mod may normalize its inventory copy while its
        // server-side mode sets are refreshed.  The authoritative compatibility
        // contract is the two public queries below, rather than the pre-inventory
        // ItemStack object that was handed to Inventory#add.
        check((boolean) protectedQuery.invoke(null, player) && (boolean) stealthQuery.invoke(null, player),
                "upstream player protection and advanced stealth remain effective while phasing");

        staff.set(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), false);
        StaffPhaseManager.apply(player);
        check(!StaffPhaseManager.noPhysics(player), "phase release restores collision while upstream modes stay enabled");
        check((boolean) protectedQuery.invoke(null, player) && (boolean) stealthQuery.invoke(null, player),
                "upstream protections remain effective after phase release");
        player.getInventory().removeItem(beef);
        LogUtils.getLogger().info("REGRESSION staff phase and Useless Mod player protection/advanced stealth coexistence passed");
    }
}
