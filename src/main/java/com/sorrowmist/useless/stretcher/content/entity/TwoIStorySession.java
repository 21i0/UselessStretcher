package com.sorrowmist.useless.stretcher.content.entity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Short-lived server session. Tokens and acceptance state are never loaded from a save. */
public final class TwoIStorySession {
    public static final long LIFETIME_TICKS = 20L * 60L * 30L;
    public static final int CHOICE_ROUNDS = 3;
    private static final AtomicLong NEXT_ID = new AtomicLong(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
    private final UUID player;
    private final long id;
    private final int chapter;
    private final long expiresAt;
    private int expectedRound;

    public TwoIStorySession(UUID player, int chapter, long now) {
        this.player = player;
        this.id = NEXT_ID.updateAndGet(value -> value == Long.MAX_VALUE ? 1 : value + 1);
        this.chapter = chapter;
        this.expiresAt = now + LIFETIME_TICKS;
    }

    public long id() { return id; }
    public int chapter() { return chapter; }

    public int expectedRound() { return expectedRound; }
    public boolean finished() { return expectedRound >= CHOICE_ROUNDS; }
    public boolean expired(long now) { return finished() || now >= expiresAt; }

    public boolean accept(UUID caller, long token, int choice, long now) {
        if (!player.equals(caller) || token != id || choice < 0 || choice >= CHOICE_ROUNDS * 3
                || choiceRound(choice) != expectedRound || expired(now)) return false;
        expectedRound++;
        return true;
    }

    public static int choiceRound(int code) { return code / 3; }
    public static int choiceIndex(int code) { return code % 3; }

    public static int bondDelta(int choice) {
        return switch (choice) {
            case 0 -> 1;
            case 1 -> -1;
            case 2 -> 0;
            default -> throw new IllegalArgumentException("Invalid 2i story choice");
        };
    }

    public static int nextChapterFor(int bond, int completedMask, int lastChapter) {
        if ((completedMask & 1) == 0) return 0;
        int[] unlocked = bond >= 70 ? new int[]{1, 3, 4, 5, 2}
                : bond >= 40 ? new int[]{1, 3, 4, 5}
                : bond >= 20 ? new int[]{1, 3, 4} : new int[]{1, 3};
        for (int chapter : unlocked) if ((completedMask & (1 << chapter)) == 0) return chapter;
        for (int index = 0; index < unlocked.length; index++) {
            if (unlocked[index] == lastChapter) return unlocked[(index + 1) % unlocked.length];
        }
        return unlocked[0];
    }
}
