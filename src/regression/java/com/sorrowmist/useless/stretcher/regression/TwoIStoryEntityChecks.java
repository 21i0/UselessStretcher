package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIStoryData;
import com.sorrowmist.useless.stretcher.content.entity.TwoIStorySession;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.Map;
import java.util.UUID;

/** Real server-side dialogue action checks, including the per-day reward cap and all three rounds. */
public final class TwoIStoryEntityChecks {
    private TwoIStoryEntityChecks() { }

    public static void run(ServerLevel level) throws Exception {
        long originalTime = level.getGameTime();
        ServerPlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIStoryOwner"));
        ServerPlayer stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIStoryGuest"));
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "fixture exists");
        owner.setPos(35, 160, 35); stranger.setPos(35, 160, 36); doll.setPos(35, 160, 36);
        doll.setOwnerId(owner.getUUID());
        try {
            level.getServer().getWorldData().overworldData().setGameTime(240_000);
            var progress = TwoIStoryData.get(level.getServer());
            doll.startStory(owner);
            var first = session(doll, owner);
            check(first != null && first.expectedRound() == 0, "start creates first server-owned round");
            doll.startStory(owner);
            check(session(doll, owner) == first, "duplicate start resumes the same session");
            doll.storyChoice(stranger, first.id(), 0);
            doll.storyChoice(owner, first.id() + 1, 0);
            doll.storyChoice(owner, first.id(), 6);
            check(doll.getGiftScore() == 0 && first.expectedRound() == 0, "stranger, wrong token and out-of-order packet cannot award");
            doll.storyChoice(owner, first.id(), 0);
            check(doll.getGiftScore() == 1 && first.expectedRound() == 1, "first valid kindness awards once");
            check(progress.completedMask(owner.getUUID()) == 0, "first round does not unlock chapter");
            doll.storyChoice(owner, first.id(), 0);
            check(doll.getGiftScore() == 1 && first.expectedRound() == 1, "duplicate first choice ignored");
            doll.startStory(owner);
            check(session(doll, owner) == first && first.expectedRound() == 1, "reopen keeps progressed round");
            doll.storyChoice(owner, first.id(), 3);
            check(doll.getGiftScore() == 2 && progress.completedMask(owner.getUUID()) == 0, "second round still does not complete");
            doll.storyChoice(owner, first.id(), 6);
            check(doll.getGiftScore() == 3 && session(doll, owner) == null, "third round awards and consumes session");
            check(progress.completedMask(owner.getUUID()) == 1, "only final choice unlocks completed chapter");
            doll.storyChoice(owner, first.id(), 6);
            check(doll.getGiftScore() == 3, "old completed token cannot repeat reward");

            clearCooldown(doll);
            doll.startStory(owner);
            var second = session(doll, owner);
            check(second != null && second.id() != first.id(), "next story has distinct token");
            doll.storyChoice(owner, second.id(), 0);
            check(doll.getGiftScore() == 3 && second.expectedRound() == 1, "daily plus-three cap still permits story progression");
            doll.storyChoice(owner, second.id(), 4);
            check(doll.getGiftScore() == 2, "unkind choice still removes one after positive cap");
            doll.storyChoice(owner, second.id(), 8);
            check(doll.getGiftScore() == 2 && second.finished(), "neutral choice changes no bond");

            clearCooldown(doll);
            doll.startStory(owner);
            var third = session(doll, owner);
            owner.setPos(100, 160, 100);
            doll.storyChoice(owner, third.id(), 0);
            check(third.expectedRound() == 0 && doll.getGiftScore() == 2, "out-of-range choice cannot consume round");
            owner.setPos(35, 160, 35);
            level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime() + TwoIStorySession.LIFETIME_TICKS);
            doll.storyChoice(owner, third.id(), 0);
            check(third.expectedRound() == 0 && doll.getGiftScore() == 2, "expired session cannot award");

            clearCooldown(doll);
            doll.startStory(owner);
            var refreshed = session(doll, owner);
            check(refreshed != null && refreshed.id() != third.id(), "expired session can be restarted");
            doll.storyChoice(owner, refreshed.id(), 0);
            check(doll.getGiftScore() == 3, "next game day refreshes story reward allowance");
            com.mojang.logging.LogUtils.getLogger().info(
                    "REGRESSION 2i story: three rounds, final-only unlock, spoof/order/duplicate rejection, reopen, daily cap, negative/neutral, distance and expiry passed");
        } finally {
            level.getServer().getWorldData().overworldData().setGameTime(originalTime);
            doll.discardForReclaim();
        }
    }

    @SuppressWarnings("unchecked")
    private static TwoIStorySession session(TwoIDollEntity doll, ServerPlayer player) throws Exception {
        var field = TwoIDollEntity.class.getDeclaredField("storySessions");
        field.setAccessible(true);
        return ((Map<UUID, TwoIStorySession>) field.get(doll)).get(player.getUUID());
    }

    private static void clearCooldown(TwoIDollEntity doll) throws Exception {
        var field = TwoIDollEntity.class.getDeclaredField("talkCooldown");
        field.setAccessible(true);
        field.setInt(doll, 0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("2i real story: " + message);
    }
}
