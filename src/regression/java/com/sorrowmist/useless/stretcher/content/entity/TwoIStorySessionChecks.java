package com.sorrowmist.useless.stretcher.content.entity;

import java.util.UUID;

/** Standalone checks; deliberately independent of a Minecraft client or test world. */
public final class TwoIStorySessionChecks {
    public static void main(String[] args) {
        UUID starter = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        TwoIStorySession session = new TwoIStorySession(starter, 1, 100);
        check(session.id() != 0L, "zero is reserved for failed starts");
        check(session.chapter() == 1, "chapter fixed at server start");
        check(!session.accept(stranger, session.id(), 0, 101), "other player cannot hijack session");
        check(!session.accept(starter, 0L, 0, 101), "failed-start sentinel is not a session token");
        check(!session.accept(starter, session.id() + 1, 0, 101), "incorrect token rejected");
        check(!session.accept(starter, session.id(), -1, 101), "negative choice rejected");
        check(!session.accept(starter, session.id(), 3, 101), "later round cannot skip an earlier choice");
        check(!session.accept(starter, session.id(), 9, 101), "unknown choice rejected");
        check(!session.accept(starter, session.id(), Integer.MAX_VALUE, 101), "oversized choice rejected");
        check(session.accept(starter, session.id(), 2, 101), "invalid attempts did not consume session");
        check(!session.accept(starter, session.id(), 0, 102), "duplicate choice cannot award twice");
        check(!session.expired(102) && session.expectedRound() == 1, "first choice advances to second round");
        check(!session.accept(starter, session.id(), 8, 102), "future third round is rejected");
        check(session.accept(starter, session.id(), 3, 102), "second round accepted");
        check(!session.accept(starter, session.id(), 5, 102), "duplicate second round cannot alter the result");
        check(session.accept(starter, session.id(), 8, 103), "third round accepted");
        check(session.finished() && session.expired(103), "only final choice completes chapter");

        TwoIStorySession expiring = new TwoIStorySession(starter, 0, 100);
        check(expiring.id() != session.id(), "successive sessions get distinct tokens");
        check(!expiring.accept(starter, session.id(), 0, 101), "old token cannot consume a new session");
        check(!expiring.expired(100 + TwoIStorySession.LIFETIME_TICKS - 1), "session live before deadline");
        check(!expiring.accept(starter, expiring.id(), 0, 100 + TwoIStorySession.LIFETIME_TICKS),
                "session rejected at deadline");
        TwoIStorySession lastTick = new TwoIStorySession(starter, 1, 100);
        check(lastTick.accept(starter, lastTick.id(), 0, 100 + TwoIStorySession.LIFETIME_TICKS - 1),
                "choice accepted on final live tick");
        check(!lastTick.accept(starter, lastTick.id(), 1, 100 + TwoIStorySession.LIFETIME_TICKS - 1),
                "same round replay cannot change an accepted result");
        check(!lastTick.accept(starter, lastTick.id(), 3, 100 + TwoIStorySession.LIFETIME_TICKS),
                "later round cannot outlive original thirty-minute session");
        TwoIStorySession secondPlayer = new TwoIStorySession(stranger, 2, 100);
        check(secondPlayer.accept(stranger, secondPlayer.id(), 1, 101), "independent guest session accepted");

        check(TwoIStorySession.bondDelta(0) == 1, "kindness awards one bond");
        check(TwoIStorySession.bondDelta(1) == -1, "tease removes one bond");
        check(TwoIStorySession.bondDelta(2) == 0, "neutral choice leaves bond unchanged");
        check(TwoIStorySession.nextChapterFor(100, 0, -1) == 0, "first conversation uses meeting chapter");
        check(TwoIStorySession.nextChapterFor(0, 1, 0) == 1, "returning player gets friends chapter");
        check(TwoIStorySession.nextChapterFor(0, 3, 1) == 3, "second common story available at zero bond");
        check(TwoIStorySession.nextChapterFor(-100, 11, 3) == 1, "negative bond rotates unlocked stories");
        check(TwoIStorySession.nextChapterFor(20, 11, 3) == 4, "new story unlocks at twenty bond");
        check(TwoIStorySession.nextChapterFor(40, 27, 4) == 5, "quiet night unlocks at forty bond");
        check(TwoIStorySession.nextChapterFor(69, 59, 5) == 1, "stars remain locked below seventy");
        check(TwoIStorySession.nextChapterFor(70, 59, 5) == 2, "stars unlock after remaining chapters");
        check(TwoIStorySession.nextChapterFor(100, 63, 2) == 1, "completed chapters wrap around");
        check(TwoIStorySession.nextChapterFor(100, 63, 1) == 3, "completed chapters do not repeat immediately");
        check(TwoIStorySession.nextChapterFor(0, 63, 4) == 1, "lost threshold uses still-unlocked pool");
        check(TwoIStorySession.LIFETIME_TICKS == 36_000, "long dialogue session allows thirty minutes");
        check(TwoIStorySession.choiceRound(7) == 2 && TwoIStorySession.choiceIndex(7) == 1,
                "wire choice code decodes round and answer");
        System.out.println("2i story session checks passed (multi-round ordering, expiry and replay protection)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
