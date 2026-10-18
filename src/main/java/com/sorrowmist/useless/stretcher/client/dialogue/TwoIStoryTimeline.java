package com.sorrowmist.useless.stretcher.client.dialogue;

import java.util.ArrayList;
import java.util.List;

/** One deterministic clock for both visible characters and their original syllables. No sound queue. */
public final class TwoIStoryTimeline {
    public enum Timbre { NATURAL, SOFT, BRIGHT, PLAYER }
    public record Cue(int revealed, int codePoint, boolean voiced, float pitch, float volume,
                      Timbre timbre, double cadence, double pauseMs) {}

    private final List<Cue> cues;
    private int cursor;
    private double remainingMs;

    public TwoIStoryTimeline(String key, String text, boolean player, boolean narration) {
        int[] chars = text.codePoints().toArray();
        List<Cue> result = new ArrayList<>();
        int phraseStart = 0;
        for (int i = 0; i < chars.length; i++) {
            int cp = chars[i];
            int end = i + 1;
            // Never show half a surrogate pair or a detached accent/variation selector.
            while (end < chars.length && isCombining(chars[end])) end++;
            // Chinese ellipses/dashes are one pause, not six repeated waits.
            if (isRepeatedPause(cp)) while (end < chars.length && chars[end] == cp) end++;
            var mood = TwoIStoryDirection.mood(key, text, i);
            Profile profile = profile(mood);
            int phraseEnd = i;
            while (phraseEnd < chars.length && !isBoundary(chars[phraseEnd])) phraseEnd++;
            int voicedBefore = 0, voicedAfter = 0;
            for (int k = phraseStart; k < i; k++) if (Character.isLetterOrDigit(chars[k])) voicedBefore++;
            for (int k = i + 1; k < phraseEnd; k++) if (Character.isLetterOrDigit(chars[k])) voicedAfter++;
            double phase = (double) voicedBefore / Math.max(1, voicedBefore + voicedAfter);
            // Phrase-length contour and a gentle word accent, never a looping eight-note sequence.
            double arch = Math.sin(Math.PI * phase);
            double accent = Math.sin((voicedBefore + 1) * 1.71 + phraseStart * 0.37) * 0.16;
            double contour = arch + accent;
            if (voicedAfter < 4) {
                int total = voicedBefore + voicedAfter + 1;
                int tailStart = Math.max(0, total - 4);
                double tailPhase = (double) tailStart / Math.max(1, total - 1);
                double startContour = Math.sin(Math.PI * tailPhase)
                        + Math.sin((tailStart + 1) * 1.71 + phraseStart * 0.37) * 0.16;
                double terminal = phraseEnd < chars.length ? ending(chars[phraseEnd]) : -0.30;
                double blend = (double)(voicedBefore - tailStart) / Math.max(1, total - 1 - tailStart);
                // Explicit endpoint owns the last four syllables: the falling arch cannot
                // accidentally cancel the upward question inflection.
                contour = startContour + (terminal - startContour) * blend;
            }
            float pitch = (float) ((player ? 0.95 : profile.pitch) + profile.range
                    * contour);
            float volume = (float) (profile.volume * (0.89 + arch * 0.11));
            double cadence = profile.cadence * (1 + 0.08 * arch + (voicedBefore == 0 ? profile.hesitation : 0));
            if (voicedAfter < 2) cadence *= 1.10;
            double pause = pause(cp) * (mood == TwoIStoryDirection.Mood.SHY || mood == TwoIStoryDirection.Mood.UNEASY ? 1.18 : 1);
            result.add(new Cue(end, cp, !narration && Character.isLetterOrDigit(cp),
                    Math.clamp(pitch, 0.78F, 1.30F), volume, player ? Timbre.PLAYER : profile.timbre,
                    Character.isWhitespace(cp) ? 0.35 : cadence, pause));
            if (isBoundary(cp)) phraseStart = end;
            i = end - 1;
        }
        cues = List.copyOf(result);
    }

    /** At most one character event per frame: a stalled client stretches speech, never bursts a backlog. */
    public Cue advance(double elapsedMs, int speed) {
        if (cursor >= cues.size()) return null;
        remainingMs -= Math.max(0, elapsedMs);
        if (remainingMs > 0) return null;
        Cue cue = cues.get(cursor++);
        double base = speed == 1 ? 92 : speed == 4 ? 48 : 65;
        remainingMs = Math.max(48, base * cue.cadence()) + cue.pauseMs();
        return cue;
    }

    public List<Cue> cues() { return cues; }
    public boolean complete() { return cursor == cues.size(); }
    public void finish() { cursor = cues.size(); remainingMs = 0; }

    private record Profile(float pitch, float range, float volume, double cadence, double hesitation, Timbre timbre) {}
    private static Profile profile(TwoIStoryDirection.Mood mood) {
        return switch (mood) {
            case WARM -> new Profile(0.99F, 0.045F, 0.32F, 1.07, 0.12, Timbre.SOFT);
            case SHY -> new Profile(1.02F, 0.025F, 0.26F, 1.20, 0.55, Timbre.SOFT);
            case BRIGHT -> new Profile(1.06F, 0.10F, 0.36F, 0.90, 0, Timbre.BRIGHT);
            case PLAYFUL -> new Profile(1.03F, 0.12F, 0.34F, 0.94, 0.05, Timbre.BRIGHT);
            case CURIOUS -> new Profile(1.02F, 0.07F, 0.32F, 1.02, 0.15, Timbre.NATURAL);
            case SERIOUS -> new Profile(0.95F, 0.035F, 0.34F, 1.04, 0, Timbre.NATURAL);
            case UNEASY -> new Profile(0.98F, 0.04F, 0.27F, 1.17, 0.42, Timbre.SOFT);
            case HURT -> new Profile(0.88F, 0.025F, 0.23F, 1.28, 0.26, Timbre.SOFT);
            case STARTLED -> new Profile(1.12F, 0.08F, 0.37F, 0.91, 0, Timbre.BRIGHT);
            case CALM -> new Profile(0.96F, 0.03F, 0.29F, 1.12, 0.10, Timbre.SOFT);
        };
    }

    private static boolean isCombining(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || cp == 0xFE0F;
    }
    private static boolean isRepeatedPause(int cp) { return cp == '…' || cp == '.' || cp == '—'; }
    private static boolean isBoundary(int cp) { return "，,、；;：:。.!！?？…—\n".indexOf(cp) >= 0; }
    private static double ending(int cp) {
        return switch (cp) { case '?', '？' -> 1.60; case '!', '！' -> 0.70; case '…', '—' -> -0.65; default -> -0.45; };
    }
    private static double pause(int cp) {
        return switch (cp) {
            case '，', ',', '、', ':', '：' -> 110;
            case '；', ';', '\n' -> 180;
            case '。', '.', '!', '！', '?', '？' -> 220;
            case '…' -> 330;
            case '—' -> 190;
            default -> 0;
        };
    }
}
