package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TwoICompanionshipData;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.event.TwoICarriedCompanionship;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.tick.PlayerTickEvent;

import java.util.UUID;

/** Real carried ticks and form conversion persist the five-minute clock without periodic inventory changes. */
public final class TwoICompanionshipChecks {
    private static final UUID RESTART_ID = UUID.fromString("71b68a98-f959-4768-8a60-5f10238e332f");
    private TwoICompanionshipChecks() { }

    public static void run(ServerLevel level) throws Exception {
        long oldTime = level.getGameTime();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoICarryClock"));
        player.setPos(57, 180, 57);
        var data = TwoICompanionshipData.get(level.getServer());
        try {
            ItemStack left = doll(player), right = doll(player);
            check(!com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(left, StretcherComponents.TWO_I_DOLL_ID.get()).equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(right, StretcherComponents.TWO_I_DOLL_ID.get())),
                    "two identical new dolls have distinct stable identities");
            player.setItemSlot(EquipmentSlot.MAINHAND, right);
            player.setItemSlot(EquipmentSlot.OFFHAND, left);
            ItemStack leftBefore = left.copy(), rightBefore = right.copy();
            for (int second = 0; second < 200; second++) {
                time(level, 192_000L + second * 20L);
                tick(player); tick(player);
                // F-like operation: exchange the complete stacks, then sample again in the same tick.
                ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, off);
                player.setItemInHand(InteractionHand.OFF_HAND, main);
                tick(player);
            }
            check(ItemStack.isSameItemSameComponents(leftBefore, left)
                            && ItemStack.isSameItemSameComponents(rightBefore, right),
                    "200 companion seconds and hand swaps cause zero per-second item component changes");
            check(data.progress(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(left, StretcherComponents.TWO_I_DOLL_ID.get()), TwoIDollMemory.empty()).ticks() == 4000
                            && data.progress(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(right, StretcherComponents.TWO_I_DOLL_ID.get()), TwoIDollMemory.empty()).ticks() == 4000,
                    "world records advance independently and repeated events/swaps do not double-count");

            var decoded = TwoICompanionshipData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(decoded.progress(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(left, StretcherComponents.TWO_I_DOLL_ID.get()), TwoIDollMemory.empty()).ticks() == 4000,
                    "world SavedData encode/load retains progress while the item snapshot remains unchanged");
            for (int second = 200; second < 295; second++) { time(level, 192_000L + second * 20L); tick(player); }
            var entity = ModEntities.TWO_I_DOLL.get().create(level);
            check(entity != null, "entity fixture");
            ItemStack carried = player.getMainHandItem();
            UUID carriedId = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(carried, StretcherComponents.TWO_I_DOLL_ID.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            entity.setOwnerId(player.getUUID()); entity.setPos(player.position()); entity.restoreFromItem(carried);
            check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(entity.toDollItem(), StretcherComponents.TWO_I_MEMORY.get()).interaction().companionTicks() == 5900,
                    "deploy reads latest world progress even from a stale encoded item snapshot");
            var tickEntity = TwoIDollEntity.class.getDeclaredMethod("tickCompanionship", ServerLevel.class);
            tickEntity.setAccessible(true);
            var playersField = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            playersField.setAccessible(true);
            @SuppressWarnings("unchecked") var players = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>)
                    playersField.get(level.getServer().getPlayerList());
            players.put(player.getUUID(), player);
            try {
                time(level, 197_800);
                tickEntity.invoke(entity, level);
                check(entity.getGiftScore() == 0, "deploy cannot count the carried timer interval again");
                time(level, 197_900); tickEntity.invoke(entity, level);
                check(entity.getGiftScore() == 1, "entity awards at the exact 6000-tick combined threshold");
                ItemStack returned = entity.toDollItem();
                check(carriedId.equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(returned, StretcherComponents.TWO_I_DOLL_ID.get())), "recall keeps the hand-swap identity");
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.parseOptional(level.registryAccess(),
                        (CompoundTag)returned.save(level.registryAccess())));
                tick(player);
                check(TwoIDollItem.getBond(player.getItemBySlot(EquipmentSlot.HEAD)) == 1
                                && data.progress(carriedId, TwoIDollMemory.empty()).ticks() == 0,
                        "recall/head conversion at the reward tick does not issue another reward");
            } finally { players.remove(player.getUUID()); entity.discardForReclaim(); }

            timerBudgets(level);

            // Fixed fixture is left in actual world SavedData for the next server process.
            var recordsField = TwoICompanionshipData.class.getDeclaredField("dolls"); recordsField.setAccessible(true);
            boolean fromPreviousProcess = ((java.util.Map<?, ?>)recordsField.get(data)).containsKey(RESTART_ID);
            var seed = new TwoIDollMemory(TwoIDollMemory.empty().cooldowns(),
                    new TwoIDollMemory.Interaction(8, 0, 0, 0, 1, 4560, -1, "", 0, 0), 192_000);
            var fixture = data.progress(RESTART_ID, seed);
            check(fixture.earned() == 1 && fixture.ticks() == 4560 && fixture.lastSample() == 192_000,
                    "real server restart retains daily budget, partial progress and duplicate-sample guard");
            LogUtils.getLogger().info(fromPreviousProcess ? "2I COMPANIONSHIP RESTART PASSED: actual prior-process world record restored"
                    : "2I COMPANIONSHIP RESTART FIXTURE SAVED: verify on next server process");
            LogUtils.getLogger().info("2I COMPANIONSHIP STORAGE PASSED: five-second server checks; 200 seconds with zero item changes; independent IDs, hand swaps, world save/load, legacy remainder and item/entity/head continuity");
        } finally {
            time(level, oldTime);
            for (var slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD})
                player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void timerBudgets(ServerLevel level) {
        var data = new TwoICompanionshipData();
        UUID id = UUID.randomUUID();
        var legacy = new TwoIDollMemory(TwoIDollMemory.empty().cooldowns(),
                new TwoIDollMemory.Interaction(8, 2, 2, 3, 0, 5980, 5, "minecraft:apple", 2, 1), 192_000);
        var before = data.progress(id, legacy);
        data.setDirty(false);
        for (long time = 192_001; time < 192_100; time++)
            check(!data.sample(id, legacy, time).awarded(), "no reward outside the five-second timer check");
        check(!data.isDirty() && before.equals(data.progress(id, legacy)),
                "intermediate ticks do not modify or dirty the world timer");
        var award = data.sample(id, legacy, 192_100);
        check(award.awarded() && award.progress().ticks() == 80,
                "migration keeps the legacy twenty-tick remainder with the five-second clock");
        var restored = TwoICompanionshipData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(restored.progress(id, TwoIDollMemory.empty()).ticks() == 80,
                "non-hundred-tick migration remainder survives serialization");
        var merged = award.progress().merge(legacy).interaction();
        check(merged.pats() == 2 && merged.talks() == 2 && merged.stories() == 3 && merged.gifts() == 1
                        && merged.lastGift().equals("minecraft:apple") && merged.repeatedGift() == 2,
                "companion timer does not clear other interaction budgets");
        var newer = new TwoIDollMemory(legacy.cooldowns(),
                new TwoIDollMemory.Interaction(9, 1, 1, 1, 0, 0, 6, "minecraft:diamond", 1, 1), -1);
        check(award.progress().merge(newer).interaction().gifts() == 1
                        && award.progress().merge(newer).interaction().day() == 9,
                "older world clock does not roll back newer ordinary gift/pat/story records");
        // Inactive/offline gaps earn no catch-up; only one active interval is added on return.
        var returned = data.sample(id, legacy, 194_000);
        check(!returned.awarded() && returned.progress().ticks() == 180, "inactive gaps cannot grant free bond");
        int rewards = 0;
        for (long time = 194_100; time <= 209_000; time += 100)
            if (data.sample(id, legacy, time).awarded()) rewards++;
        check(rewards == 1 && data.progress(id, legacy).earned() == 2, "five-minute timer caps at two daily rewards");
        data.setDirty(false);
        data.sample(id, legacy, 209_100);
        check(!data.isDirty(), "completed daily timer performs no world writes");
        check(data.sample(id, legacy, 216_000).progress().ticks() == 100
                        && data.progress(id, legacy).earned() == 0,
                "new day restarts the companion timer independently of other budgets");
    }

    private static ItemStack doll(net.minecraft.server.level.ServerPlayer owner) {
        ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());
        TwoIDollItem.claimOrCheckOwner(stack, owner);
        return stack;
    }

    private static void time(ServerLevel level, long time) { level.getServer().getWorldData().overworldData().setGameTime(time); }
    private static void tick(net.minecraft.server.level.ServerPlayer player) { TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player)); }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError("2i companion storage: " + message); }
}
