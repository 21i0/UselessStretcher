package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Reward persistence and multiplayer dialogue leases, with real registered item components. */
public final class TwoIInteractionChecks {
    public static void run(ServerLevel level) throws Exception {
        TwoIStoryEntityChecks.run(level);
        long originalTime = level.getGameTime();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIInteraction"));
        var guest = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIViewer"));
        var indexField = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        indexField.setAccessible(true);
        @SuppressWarnings("unchecked") var index = (java.util.Map<UUID, net.minecraft.server.level.ServerPlayer>)
                indexField.get(level.getServer().getPlayerList());
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "doll fixture");
        owner.setPos(25, 160, 25); guest.setPos(26, 160, 25);
        doll.setPos(25, 160, 26); doll.setOwnerId(owner.getUUID());
        index.put(owner.getUUID(), owner); index.put(guest.getUUID(), guest);
        try {
            level.getServer().getWorldData().overworldData().setGameTime(48_000);
            check(doll.patBy(owner) && doll.getGiftScore() == 1, "first pat rewards");
            clearCooldown(doll, "patCooldown");
            check(doll.patBy(owner) && doll.getGiftScore() == 1, "second pat still animates without reward");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHER_STAR, 3));
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 6 && owner.getMainHandItem().getCount() == 2, "precious gift grants five");
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 11 && owner.getMainHandItem().getCount() == 1, "consecutive precious gifts each grant five");
            ItemStack saved = doll.toDollItem();
            check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).interaction().gifts() == 0
                    && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).cooldowns().gift() == 0,
                    "precious gifts neither spend the ordinary budget nor start its cooldown");
            doll.restoreFromItem(ItemStack.of(saved.save(new CompoundTag())));
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 16 && owner.getMainHandItem().isEmpty(), "encoded item restoration preserves unlimited precious gifts");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 2));
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 17 && owner.getMainHandItem().getCount() == 1,
                    "ordinary gift still works immediately after precious gifts");
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 17 && owner.getMainHandItem().isEmpty()
                            && doll.getBubbleText().equals("msg.useless_stretcher.two_i.gift_daily_limit"),
                    "ordinary daily cap accepts gift with synchronized no-bond bubble");
            saved = doll.toDollItem();
            check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).interaction().gifts() == 1
                    && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).cooldowns().gift() == 1200,
                    "ordinary budget and cooldown persisted");
            doll.restoreFromItem(ItemStack.of(saved.save(new CompoundTag())));
            doll.talkTo(owner);
            int afterChat = doll.getGiftScore();
            doll.acceptGift(owner);
            check(owner.getMainHandItem().isEmpty() && doll.getGiftScore() == afterChat
                            && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(doll.toDollItem(), StretcherComponents.TWO_I_MEMORY.get()).interaction().gifts() == 1,
                    "recall or ordinary chat cannot reset gift budget");

            int before = doll.getGiftScore();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHER_STAR, 2));
            doll.acceptGift(owner); doll.acceptGift(owner);
            check(doll.getGiftScore() == before + 10 && owner.getMainHandItem().isEmpty(),
                    "precious gifts bypass both an active ordinary cooldown and spent daily budget");
            saved = doll.toDollItem();
            check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).interaction().gifts() == 1
                    && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).cooldowns().gift() == 1200,
                    "precious gifts do not clear or extend ordinary restrictions");
            CompoundTag entitySave = doll.saveWithoutId(new CompoundTag());
            doll.load(entitySave);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHER_STAR));
            before = doll.getGiftScore();
            doll.acceptGift(owner);
            check(doll.getGiftScore() == before + 5 && owner.getMainHandItem().isEmpty(),
                    "entity reload with a spent ordinary budget still accepts precious gifts");
            clearCooldown(doll, "giftCooldown");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 2));
            doll.acceptGift(owner);
            check(owner.getMainHandItem().getCount() == 1 && doll.getGiftScore() == before + 5,
                    "ordinary daily limit accepts without bond after precious gifts and cooldown expiry");
            level.getServer().getWorldData().overworldData().setGameTime(72_000);
            clearCooldown(doll, "patCooldown");
            before = doll.getGiftScore();
            doll.patBy(owner); doll.acceptGift(owner);
            check(doll.getGiftScore() == before + 2 && owner.getMainHandItem().isEmpty(),
                    "next game day refreshes pat and ordinary gift budgets");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPIDER_EYE, 2));
            before = doll.getGiftScore();
            doll.acceptGift(owner);
            check(doll.getGiftScore() == before && owner.getMainHandItem().getCount() == 1
                            && doll.getBubbleText().equals("msg.useless_stretcher.two_i.gift_cooldown"),
                    "ordinary cooldown accepts without bond and explains in bubble");
            clearCooldown(doll, "giftCooldown");
            doll.acceptGift(owner);
            check(doll.getGiftScore() == before - 2 && owner.getMainHandItem().isEmpty(),
                    "negative gift still loses bond after the positive budget is spent");

            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            doll.acceptGift(owner);
            check(owner.getMainHandItem().getCount() == 3 && doll.getGiftScore() == before - 2
                            && doll.getBubbleText().equals("msg.useless_stretcher.two_i.gift_daily_limit"),
                    "uncatalogued gifts share the ordinary daily budget");
            level.getServer().getWorldData().overworldData().setGameTime(96_000);
            clearCooldown(doll, "giftCooldown");
            before = doll.getGiftScore(); doll.acceptGift(owner);
            saved = doll.toDollItem();
            check(doll.getGiftScore() == before + 1 && owner.getMainHandItem().getCount() == 2
                            && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).interaction().gifts() == 1
                            && com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get()).cooldowns().gift() == 1200
                            && doll.getBubbleMessage().getString().contains("1"),
                    "uncatalogued gift grants one, starts cooldown and synchronizes a parameterized bubble");
            doll.restoreFromItem(ItemStack.of(saved.save(new CompoundTag())));
            doll.acceptGift(owner);
            check(doll.getGiftScore() == before + 1 && owner.getMainHandItem().getCount() == 1
                            && doll.getBubbleText().equals("msg.useless_stretcher.two_i.gift_daily_limit"),
                    "uncatalogued gift cooldown/budget survives encoded recall");
            for (long day : new long[]{120_000, 144_000}) {
                level.getServer().getWorldData().overworldData().setGameTime(day);
                clearCooldown(doll, "giftCooldown");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE));
                before = doll.getGiftScore(); doll.acceptGift(owner);
                check(doll.getGiftScore() == before + 1 && owner.getMainHandItem().isEmpty(), "ordinary gift rewards before repetition limit");
            }
            level.getServer().getWorldData().overworldData().setGameTime(168_000);
            clearCooldown(doll, "giftCooldown");
            // Earlier versions could save repetition history without the later daily budget.
            saved = doll.toDollItem();
            var previous = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(saved, StretcherComponents.TWO_I_MEMORY.get());
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(saved, StretcherComponents.TWO_I_MEMORY.get(), new com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory(
                    previous.cooldowns(), new com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory.Interaction(
                    7, 0, 0, 0, 0, 0, -1, "minecraft:cookie", 2, 0), previous.lastCompanionSample()));
            doll.restoreFromItem(ItemStack.of(saved.save(new CompoundTag())));
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 2));
            before = doll.getGiftScore(); doll.acceptGift(owner);
            check(doll.getGiftScore() == before && owner.getMainHandItem().getCount() == 1
                            && doll.getBubbleText().equals("msg.useless_stretcher.two_i.gift_repeated"),
                    "ordinary repetition accepted without bond and explained in bubble");

            doll.restoreBond(98, false);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHER_STAR, 3));
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 100 && owner.getMainHandItem().getCount() == 2,
                    "precious gift clamps bond at the existing maximum");
            doll.acceptGift(owner);
            check(doll.getGiftScore() == 100 && owner.getMainHandItem().getCount() == 2,
                    "full bond refuses precious gifts without consuming them");
            com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i gifts: unlimited consecutive +5 precious gifts, ordinary cooldown/day budget isolation, item/entity reload, negative gifts and full-bond preservation passed");

            checkOwnership(level, doll, owner, guest);
            doll.keepInteraction(owner, true); doll.keepInteraction(guest, true);
            check(doll.isInteractionHeld() && !doll.isPushable(), "owner holds doll and stranger cannot add a lease");
            doll.keepInteraction(owner, false);
            check(!doll.isInteractionHeld(), "owner closing releases hold despite stranger heartbeat");
            doll.keepInteraction(owner, true);
            owner.setPos(100, 160, 100);
            doll.tick();
            check(!doll.isInteractionHeld(), "out of range owner releases hold");
            owner.setPos(25, 160, 25);
            doll.keepInteraction(owner, true);
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + 61);
            doll.tick();
            check(!doll.isInteractionHeld(), "missing heartbeat cannot permanently freeze doll");
            doll.keepInteraction(owner, true);
            index.remove(owner.getUUID());
            doll.tick();
            check(!doll.isInteractionHeld(), "disconnect releases hold immediately");
            index.put(owner.getUUID(), owner);
            checkFollowGoal(doll, owner, index);
        } finally {
            level.getServer().getWorldData().overworldData().setGameTime(originalTime);
            index.remove(owner.getUUID()); index.remove(guest.getUUID());
            doll.discardForReclaim();
        }
    }

    private static void checkOwnership(ServerLevel level, TwoIDollEntity doll, ServerPlayer owner,
                                       ServerPlayer guest) throws Exception {
        int bond = doll.getGiftScore();
        guest.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHER_STAR, 2));
        doll.openDialogue(guest); doll.keepInteraction(guest, true);
        check(!doll.isInteractionHeld(), "stranger cannot open UI or freeze the doll by forged heartbeat");
        check(!doll.patBy(guest), "stranger pat rejected");
        doll.acceptGift(guest); doll.talkTo(guest); doll.startStory(guest);
        var sessions = TwoIDollEntity.class.getDeclaredField("storySessions"); sessions.setAccessible(true);
        check(doll.getGiftScore() == bond && guest.getMainHandItem().getCount() == 2
                && ((java.util.Map<?, ?>)sessions.get(doll)).isEmpty(), "forged stranger gift/chat/story cannot change relationship");
        ItemStack owned = doll.toDollItem();
        check(owner.getUUID().equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(owned, StretcherComponents.TWO_I_OWNER.get())), "recall preserves owner on item");
        owned = ItemStack.of(owned.save(new CompoundTag()));
        guest.setItemInHand(InteractionHand.MAIN_HAND, owned);
        var item = (TwoIDollItem)ModItems.TWO_I_DOLL.get();
        check(item.use(level, guest, InteractionHand.MAIN_HAND).getResult() == net.minecraft.world.InteractionResult.FAIL,
                "foreign item refuses UI on real use path");
        check(!TwoIDollItem.deploy(guest, InteractionHand.MAIN_HAND)
                && owner.getUUID().equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(owned, StretcherComponents.TWO_I_OWNER.get())), "foreign deployment cannot rebind owner");
        ItemStack legacy = new ItemStack(ModItems.TWO_I_DOLL.get());
        check(TwoIDollItem.claimOrCheckOwner(legacy, owner) && !TwoIDollItem.claimOrCheckOwner(legacy, guest),
                "legacy item binds exactly once to its first holder");
        com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i ownership: entity UI/heartbeat/actions, encoded item owner, foreign item use/deploy and legacy binding PASSED");
    }

    private static void checkFollowGoal(TwoIDollEntity doll, ServerPlayer owner,
                                        java.util.Map<UUID, ServerPlayer> index) throws Exception {
        Class<?> type = Class.forName(TwoIDollEntity.class.getName() + "$FollowOwnerGoal");
        var constructor = type.getDeclaredConstructor(TwoIDollEntity.class);
        constructor.setAccessible(true);
        Goal follow = (Goal) constructor.newInstance(doll);
        doll.setPos(25, 160, 25);
        owner.setPos(32, 160, 25);
        check(!follow.canUse(), "nearby companion may idle instead of constantly pathfinding");
        owner.setPos(36, 160, 25);
        check(follow.canUse() && follow.canContinueToUse(), "leaving owner starts follow beyond ten blocks");
        owner.setPos(31, 160, 25);
        check(follow.canContinueToUse(), "active follow continues through start threshold to avoid jitter");
        owner.setPos(29, 160, 25);
        check(!follow.canContinueToUse(), "follow stops within four blocks");

        owner.setPos(36, 160, 25);
        check(follow.canUse(), "follow may restart");
        owner.setPos(29, 160, 25);
        doll.keepInteraction(owner, true);
        owner.setPos(36, 160, 25);
        check(!follow.canUse() && !follow.canContinueToUse(), "open dialogue pauses owner follow");
        doll.keepInteraction(owner, false);
        check(follow.canUse() && follow.canContinueToUse(), "closing last dialogue resumes follow");
        index.remove(owner.getUUID());
        check(!follow.canContinueToUse(), "disconnected owner cannot remain cached as an active follow target");
        check(!follow.canUse(), "disconnected owner cannot start follow");
        index.put(owner.getUUID(), owner);
        check(follow.canUse(), "reconnected owner may resume follow");
        owner.setHealth(0);
        check(!follow.canContinueToUse() && !follow.canUse(), "dead owner neither starts nor continues follow");
        owner.setHealth(20);

        GameType oldMode = owner.gameMode.getGameModeForPlayer();
        try {
            owner.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
            check(!follow.canUse(), "spectator does not pull the companion through terrain");
        } finally {
            owner.gameMode.changeGameModeForPlayer(oldMode);
        }
        check(follow.canUse(), "normal owner resumes after spectator mode");
        boolean wasFlying = owner.getAbilities().flying;
        try {
            owner.getAbilities().flying = true;
            owner.setPos(25, 173, 25);
            check(follow.canUse(), "vertical departure starts flight follow");
            doll.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            follow.tick();
            check(doll.isNoGravity() && doll.getDeltaMovement().y > 0,
                    "ability flight gives doll real upward movement and disables gravity");
            double oldY = doll.getY();
            doll.travel(net.minecraft.world.phys.Vec3.ZERO);
            check(doll.getY() > oldY, "flight velocity moves the real mob through vanilla collision physics");
            doll.setPos(25, 172, 25);
            check(follow.canContinueToUse(), "nearby flying companion keeps hovering instead of falling");
            follow.tick();
            doll.openDialogue(owner); follow.stop();
            check(doll.isInteractionHeld() && doll.isNoGravity() && doll.getDeltaMovement().lengthSqr() == 0,
                    "owner UI pauses flight in midair without dropping the companion");
            doll.keepInteraction(owner, false);
            check(follow.canUse(), "closing airborne UI resumes hovering even inside the ground follow radius");
            owner.getAbilities().flying = false;
            doll.tick();
            check(!doll.isNoGravity(), "owner ending flight restores doll gravity");
            doll.setPos(25, 160, 25);
            owner.startFallFlying();
            check(follow.canUse(), "elytra flight is also recognized");
            follow.tick();
            check(doll.isNoGravity(), "elytra flight activates airborne following");
            owner.stopFallFlying();
            index.remove(owner.getUUID()); doll.tick();
            check(!doll.isNoGravity(), "disconnect cannot strand a floating companion");
            index.put(owner.getUUID(), owner);
            doll.setNoGravity(true);
            doll.load(doll.saveWithoutId(new CompoundTag()));
            check(!doll.isNoGravity(), "restart does not preserve stale flight gravity");
        } finally { owner.getAbilities().flying = wasFlying; owner.stopFallFlying(); }
        follow.stop();
        check(doll.getNavigation().isDone() && !doll.isNoGravity() && !follow.canContinueToUse(), "stopping follow clears path, flight and cached owner");
        com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i follow: ten/four-block freedom, dialogue pause/resume, ability flight with real movement, landing/reload cleanup, disconnect, death and spectator passed");
    }

    private static void clearCooldown(TwoIDollEntity doll, String name) throws Exception {
        var field = TwoIDollEntity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(doll, 0);
    }
    private static void check(boolean passed, String name) {
        if (!passed) throw new AssertionError("2i interactions: " + name);
    }
}
