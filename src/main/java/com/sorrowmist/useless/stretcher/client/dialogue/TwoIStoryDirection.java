package com.sorrowmist.useless.stretcher.client.dialogue;

import java.util.HashMap;
import java.util.Map;

/**
 * Authored vocal direction, independent of the expression art and Minecraft runtime.
 * A branch's shared portrait is not a reliable indication of what its words mean.
 */
public final class TwoIStoryDirection {
    public enum Mood { WARM, SHY, BRIGHT, PLAYFUL, CURIOUS, SERIOUS, UNEASY, HURT, STARTLED, CALM }

    private static final String PREFIX = "gui.useless_stretcher.two_i.vn.";
    private static final Map<String, Mood> LINES = createDirections();
    private static final Map<String, Mood> LONG_LINES = new HashMap<>();

    private TwoIStoryDirection() {}

    /** Unknown/add-on dialogue is deliberately neutral rather than guessed from isolated words. */
    public static Mood baseMood(String key) {
        return key == null ? Mood.CALM : LONG_LINES.getOrDefault(key, LINES.getOrDefault(key, Mood.CALM));
    }

    public static void register(String key, Mood mood) { LONG_LINES.put(key, mood); }
    public static void clearLongDirections() { LONG_LINES.clear(); }

    /** Short alias used by the shared text/audio timeline. */
    public static Mood at(String key, String text, int codePointIndex) {
        return mood(key, text, codePointIndex);
    }

    /**
     * Returns the performance at a Unicode code-point index, not a UTF-16 char index.
     * Localized phrase anchors provide intentional turns within a line. If a resource pack
     * rewrites those words, the line's authored base direction remains a safe fallback.
     */
    public static Mood mood(String key, String text, int codePointIndex) {
        Mood base = baseMood(key);
        if (key == null || text == null || text.isEmpty()) return base;
        int point = Math.max(0, codePointIndex);
        return switch (key) {
            case PREFIX + "0.opening.1" -> {
                Mood result = after(text, point, "你呢？", "What's your name?")
                        ? Mood.CURIOUS : base;
                if (after(text, point, "……", "...You")) result = Mood.SHY;
                yield result;
            }
            case PREFIX + "0.opening.2" -> {
                Mood result = after(text, point, "草里有东西", "草裡有東西", "Something in the grass")
                        ? Mood.UNEASY : base;
                if (after(text, point, "哦，叶子", "喔，葉子", "oh. A leaf")) result = Mood.CALM;
                if (after(text, point, "咳，你", "Ahem.")) result = Mood.SHY;
                yield result;
            }
            case PREFIX + "4.opening.2" -> split(text, point, base, Mood.UNEASY, Mood.SHY,
                    "你会不会笑话我呀", "你會不會笑話我呀", "Will you laugh at me?");
            case PREFIX + "4.branch.1.2" -> split(text, point, base, Mood.HURT, Mood.SERIOUS,
                    "不过这一页", "不過這一頁", "I'm keeping this page");
            case PREFIX + "5.branch.2.2" -> split(text, point, base, Mood.CALM, Mood.PLAYFUL,
                    "于是它", "於是它", "so it politely");
            case PREFIX + "2.branch.0.2" -> split(text, point, base, Mood.WARM, Mood.SHY,
                    "我当然也会等", "我當然也會等", "of course I'll wait");
            default -> base;
        };
    }

    private static Mood split(String text, int point, Mood fallback, Mood before, Mood after,
                              String... anchors) {
        int boundary = anchorPoint(text, anchors);
        return boundary < 0 ? fallback : point < boundary ? before : after;
    }

    private static boolean after(String text, int point, String... anchors) {
        int boundary = anchorPoint(text, anchors);
        return boundary >= 0 && point >= boundary;
    }

    private static int anchorPoint(String text, String... anchors) {
        int first = Integer.MAX_VALUE;
        for (String anchor : anchors) {
            int index = text.indexOf(anchor);
            if (index >= 0) first = Math.min(first, index);
        }
        return first == Integer.MAX_VALUE ? -1 : text.codePointCount(0, first);
    }

    private static Map<String, Mood> createDirections() {
        Map<String, Mood> result = new HashMap<>();
        // Each chapter explicitly directs all 15 spoken lines: four opening lines,
        // six branch responses, three player choices, a low-bond preface and an ending.
        chapter(result, 0, Mood.UNEASY,
                new Mood[]{Mood.SHY, Mood.STARTLED, Mood.SERIOUS, Mood.CURIOUS},
                new Mood[]{Mood.WARM, Mood.WARM, Mood.UNEASY, Mood.HURT, Mood.BRIGHT, Mood.PLAYFUL},
                new Mood[]{Mood.WARM, Mood.PLAYFUL, Mood.CURIOUS}, Mood.WARM);
        chapter(result, 1, Mood.SERIOUS,
                new Mood[]{Mood.BRIGHT, Mood.UNEASY, Mood.BRIGHT, Mood.CURIOUS},
                new Mood[]{Mood.BRIGHT, Mood.WARM, Mood.SERIOUS, Mood.SERIOUS, Mood.PLAYFUL, Mood.PLAYFUL},
                new Mood[]{Mood.WARM, Mood.PLAYFUL, Mood.CURIOUS}, Mood.BRIGHT);
        chapter(result, 2, Mood.UNEASY,
                new Mood[]{Mood.PLAYFUL, Mood.SHY, Mood.SERIOUS, Mood.WARM},
                new Mood[]{Mood.WARM, Mood.SHY, Mood.HURT, Mood.HURT, Mood.BRIGHT, Mood.PLAYFUL},
                new Mood[]{Mood.WARM, Mood.PLAYFUL, Mood.CURIOUS}, Mood.WARM);
        chapter(result, 3, Mood.UNEASY,
                new Mood[]{Mood.SERIOUS, Mood.STARTLED, Mood.SERIOUS, Mood.WARM},
                new Mood[]{Mood.WARM, Mood.SERIOUS, Mood.SERIOUS, Mood.HURT, Mood.PLAYFUL, Mood.BRIGHT},
                new Mood[]{Mood.WARM, Mood.PLAYFUL, Mood.PLAYFUL}, Mood.WARM);
        chapter(result, 4, Mood.UNEASY,
                new Mood[]{Mood.BRIGHT, Mood.SHY, Mood.UNEASY, Mood.UNEASY},
                new Mood[]{Mood.BRIGHT, Mood.WARM, Mood.HURT, Mood.HURT, Mood.BRIGHT, Mood.PLAYFUL},
                new Mood[]{Mood.WARM, Mood.SERIOUS, Mood.PLAYFUL}, Mood.PLAYFUL);
        chapter(result, 5, Mood.UNEASY,
                new Mood[]{Mood.WARM, Mood.UNEASY, Mood.CALM, Mood.WARM},
                new Mood[]{Mood.PLAYFUL, Mood.WARM, Mood.HURT, Mood.HURT, Mood.CALM, Mood.PLAYFUL},
                new Mood[]{Mood.WARM, Mood.SERIOUS, Mood.PLAYFUL}, Mood.WARM);
        return Map.copyOf(result);
    }

    private static void chapter(Map<String, Mood> result, int chapter, Mood preface,
                                Mood[] opening, Mood[] branches, Mood[] choices, Mood ending) {
        String prefix = PREFIX + chapter + ".";
        result.put(prefix + "mood", preface);
        result.put(prefix + "ending.1", ending);
        for (int i = 0; i < opening.length; i++) result.put(prefix + "opening." + (i + 1), opening[i]);
        for (int i = 0; i < choices.length; i++) {
            result.put(prefix + "choice." + i, choices[i]);
            result.put(prefix + "branch." + i + ".1", branches[i * 2]);
            result.put(prefix + "branch." + i + ".2", branches[i * 2 + 1]);
        }
    }
}
