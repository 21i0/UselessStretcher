package com.sorrowmist.useless.stretcher.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual public StringSplitter plus pure GUI geometry; no Font, client, world or render context is created. */
public final class JeiHudLayoutChecks {
    private static final StringSplitter SPLITTER = new StringSplitter((codePoint, style) ->
            codePoint == 0x2026 ? 6.0F : codePoint > 0xFFFF ? 12.0F : codePoint >= 0x3400 && codePoint <= 0x9FFF ? 9.0F : 6.0F);
    private static final JsonArray CONDITIONS = new JsonArray();
    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected result.json");
        check("1280x960_gui2_top_center_geometry", () -> {
            var box = CatalogJeiProgressOverlay.layout(640, 480, 9, 2);
            require(box.left() == 160 && box.top() == 6 && box.width() == 320, "GUI2 wrong position/width");
            require(box.height() == 28 && box.bottom() == 34 && box.textWidth() == 312 && box.lines() == 2, "GUI2 wrong capacity");
            bounds(box, 640, 480, 9);
        });
        check("1280x960_gui4_top_center_geometry", () -> {
            var box = CatalogJeiProgressOverlay.layout(320, 240, 9, 2);
            require(box.left() == 80 && box.top() == 6 && box.width() == 160, "GUI4 wrong position/width");
            require(box.height() == 28 && box.bottom() == 34 && box.textWidth() == 152 && box.lines() == 2, "GUI4 wrong capacity");
            bounds(box, 320, 240, 9);
        });
        check("gui2_avoids_map_if_given_map_coordinates_are_physical_pixels", () -> mapSeparation(640, 480, 2, true));
        check("gui4_avoids_map_if_given_map_coordinates_are_physical_pixels", () -> mapSeparation(320, 240, 4, true));
        check("gui2_avoids_map_if_given_map_coordinates_are_gui_units", () -> mapSeparation(640, 480, 2, false));
        check("gui4_avoids_map_if_given_map_coordinates_are_gui_units", () -> mapSeparation(320, 240, 4, false));
        check("invalid_screen_font_or_requested_lines_produces_empty_layout", () -> {
            int[][] invalid = {{0, 240, 9, 2}, {-1, 240, 9, 2}, {320, 0, 9, 2}, {320, -1, 9, 2},
                    {320, 240, 0, 2}, {320, 240, -1, 2}, {320, 240, 9, 0}, {320, 240, 9, -1}};
            for (int[] input : invalid) empty(CatalogJeiProgressOverlay.layout(input[0], input[1], input[2], input[3]));
        });
        check("ultranarrow_screen_without_padding_and_text_space_stays_empty", () -> {
            for (int width = 1; width <= 17; width++) empty(CatalogJeiProgressOverlay.layout(width, 240, 9, 2));
            bounds(CatalogJeiProgressOverlay.layout(18, 240, 9, 2), 18, 240, 9);
        });
        check("short_height_never_draws_partial_glyph_rows", () -> {
            for (int height = 0; height < 17; height++) empty(CatalogJeiProgressOverlay.layout(320, height, 9, 2));
            var exact = CatalogJeiProgressOverlay.layout(320, 17, 9, 2);
            require(exact.top() == 0 && exact.height() == 17 && exact.lines() == 1, "Exact one-row fit");
            bounds(exact, 320, 17, 9);
            for (int height = 18; height <= 34; height++) bounds(CatalogJeiProgressOverlay.layout(320, height, 9, 2), 320, height, 9);
        });
        check("requested_line_capacity_is_zero_one_or_at_most_two", () -> {
            empty(CatalogJeiProgressOverlay.layout(640, 480, 9, 0));
            var one = CatalogJeiProgressOverlay.layout(640, 480, 9, 1);
            require(one.lines() == 1 && one.height() == 17, "One-row request");
            require(CatalogJeiProgressOverlay.layout(640, 480, 9, 2).lines() == 2, "Two-row request");
            require(CatalogJeiProgressOverlay.layout(640, 480, 9, Integer.MAX_VALUE).lines() == 2, "Maximum rows not capped");
        });
        check("ten_thousand_small_viewport_font_combinations_stay_inside_screen", () -> {
            int examined = 0;
            for (int width = 0; width < 200; width += 10)
                for (int height = 0; height < 200; height += 10)
                    for (int fontHeight = 1; fontHeight <= 25; fontHeight++) {
                        bounds(CatalogJeiProgressOverlay.layout(width, height, fontHeight, 2), width, height, fontHeight);
                        examined++;
                    }
            require(examined == 10000, "Matrix size changed");
        });
        check("integer_extremes_do_not_overflow_geometry", () -> {
            bounds(CatalogJeiProgressOverlay.layout(Integer.MAX_VALUE, Integer.MAX_VALUE, 9, 2), Integer.MAX_VALUE, Integer.MAX_VALUE, 9);
            empty(CatalogJeiProgressOverlay.layout(320, Integer.MAX_VALUE, Integer.MAX_VALUE, 2));
            empty(CatalogJeiProgressOverlay.layout(Integer.MIN_VALUE, 240, 9, 2));
        });
        check("nonpositive_text_width_or_line_limit_returns_no_lines", () -> {
            for (int width : new int[]{-1, 0, 100})
                for (int maximum : new int[]{-1, 0, 2})
                    if (width <= 0 || maximum <= 0)
                        require(CatalogJeiProgressOverlay.fitLines(FormattedText.of("测试 Test"), width, maximum, SPLITTER).isEmpty(), "Invalid text bounds produced lines");
        });
        check("long_chinese_text_wraps_and_last_line_marks_omitted_text", () -> {
            String message = "正在整理 JEI 配方：155024/155024。正在等待配方目录，正在更新配方。".repeat(4);
            List<FormattedText> lines = fitted(message, 152, 2);
            require(lines.size() == 2 && lines.getLast().getString().endsWith("…"), "Chinese missing last-row ellipsis");
            require(!lines.getFirst().getString().endsWith("…"), "Earlier Chinese row was incorrectly marked");
        });
        check("long_english_text_wraps_and_last_line_marks_omitted_text", () -> {
            List<FormattedText> lines = fitted("Organizing JEI recipes: 155024/155024. Waiting for the recipe catalog. ".repeat(5), 312, 2);
            require(lines.size() == 2 && lines.getLast().getString().endsWith("…"), "English missing last-row ellipsis");
        });
        check("one_line_limit_truncates_using_real_splitter", () -> {
            List<FormattedText> lines = fitted("正在加入 JEI 配方（已加入 155024 条）".repeat(3), 80, 1);
            require(lines.size() == 1 && lines.getFirst().getString().endsWith("…"), "One-row truncation failed");
        });
        check("two_explicit_short_lines_keep_text_without_unneeded_ellipsis", () -> {
            List<FormattedText> lines = fitted("abc\ndef", 36, 2);
            require(lines.size() == 2, "Explicit line break lost");
            require(lines.getFirst().getString().equals("abc") && lines.getLast().getString().equals("def"), "Unnecessary trimming/ellipsis");
        });
        check("glyph_wider_than_content_and_ellipsis_wider_than_content_do_not_overflow", () -> {
            List<FormattedText> lines = fitted("汉字汉字汉字", 1, 1);
            require(lines.size() <= 1, "Narrow text exceeded row cap");
            require(lines.stream().allMatch(line -> line.getString().isEmpty()), "Too-wide ellipsis must be omitted");
            fitted("汉字", 8, 2);
            fitted("😀😀😀", 5, 2);
        });
        check("emoji_wrapping_and_trimming_never_split_surrogate_pairs", () -> {
            for (int width = 1; width <= 120; width++) {
                List<FormattedText> lines = fitted("A😀汉🦊B😺C😀汉🦊B😺C".repeat(3), width, 2);
                for (FormattedText line : lines) validSurrogates(line.getString());
            }
        });
        check("native_formatted_wrapping_preserves_visible_styles", () -> {
            FormattedText styled = Component.literal("Styled中文Styled中文Styled中文Styled中文")
                    .withStyle(Style.EMPTY.withBold(true).withColor(0x55AAFF));
            List<FormattedText> lines = CatalogJeiProgressOverlay.fitLines(styled, 90, 2, SPLITTER);
            require(lines.size() == 2 && lines.getLast().getString().endsWith("…"), "Styled wrapping missing");
            AtomicBoolean sawStyledSegment = new AtomicBoolean();
            for (FormattedText line : lines) {
                withinWidth(line, 90);
                line.visit((style, value) -> {
                    if (!value.isEmpty() && !value.equals("…")) {
                        require(style.isBold(), "Bold lost in native wrapping");
                        require(style.getColor() != null && style.getColor().getValue() == 0x55AAFF, "Color lost in native wrapping");
                        sawStyledSegment.set(true);
                    }
                    return Optional.empty();
                }, Style.EMPTY);
            }
            require(sawStyledSegment.get(), "Styled text never observed");
        });
        check("text_width_row_cap_and_unicode_hold_across_narrow_width_matrix", () -> {
            for (String message : List.of("短", "Short", "JEI 配方（已加入 0 条）", "中文 English 😀 long ".repeat(8)))
                for (int width = 1; width <= 160; width++)
                    for (int rows = 1; rows <= 3; rows++) fitted(message, width, rows);
        });
        JsonObject report = new JsonObject();
        report.addProperty("status", failed == 0 ? "PASS" : "FAIL");
        report.addProperty("checksFound", passed + failed);
        report.addProperty("checksStarted", passed + failed);
        report.addProperty("checksPassed", passed);
        report.addProperty("checksFailed", failed);
        report.addProperty("gameLaunched", false);
        report.addProperty("scope", "Pure GUI geometry and actual public StringSplitter; no Font/render/client instance and no 184-test rerun.");
        report.addProperty("geometryMatrixCases", 10000);
        report.addProperty("minimapBoundary", "Only the supplied map rectangle (12..254,43..283) at physical 1280x960 GUI2/4 is asserted; arbitrary minimap settings are not covered.");
        report.add("conditions", CONDITIONS);
        Path result = Path.of(args[0]);
        Files.createDirectories(result.toAbsolutePath().getParent());
        Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        System.out.println("JEI HUD LAYOUT CHECKS " + (failed == 0 ? "PASSED" : "FAILED") + ": " + passed + "/" + (passed + failed));
        if (failed != 0) System.exit(1);
    }

    private static void mapSeparation(int width, int height, int scale, boolean mapPhysical) {
        var box = CatalogJeiProgressOverlay.layout(width, height, 9, 2);
        int multiplier = mapPhysical ? scale : 1;
        int left = box.left() * multiplier, right = box.right() * multiplier;
        int top = box.top() * multiplier, bottom = box.bottom() * multiplier;
        require(!intersects(left, top, right, bottom, 12, 43, 254, 283), "HUD overlaps supplied map rectangle");
        if (mapPhysical) require(left == 320 && right == 960, "Unexpected physical center strip");
        else require(bottom == 34 && bottom < 43, "Unexpected GUI-unit top clearance");
    }
    private static boolean intersects(int l1, int t1, int r1, int b1, int l2, int t2, int r2, int b2) {
        return l1 < r2 && r1 > l2 && t1 < b2 && b1 > t2;
    }
    private static void bounds(CatalogJeiProgressOverlay.Layout box, int width, int height, int fontHeight) {
        if (box.lines() == 0) { empty(box); return; }
        require(box.lines() >= 1 && box.lines() <= 2, "Invalid row count");
        require(box.left() >= 0 && box.top() >= 0 && box.right() <= width && box.bottom() <= height, "Panel outside viewport");
        require(box.width() <= 320 && box.width() <= width / 2 && box.textWidth() == box.width() - 8 && box.textWidth() > 0, "Width or padding bound");
        require(Math.abs((long) box.left() * 2 + box.width() - width) <= 1, "Panel not centered");
        require(box.height() == 8L + (long) box.lines() * fontHeight + (long) (box.lines() - 1) * 2, "Partial row or wrong gap");
        require(box.top() <= 6, "Top anchor too low");
    }
    private static void empty(CatalogJeiProgressOverlay.Layout box) {
        require(box.lines() == 0 && box.width() == 0 && box.height() == 0 && box.textWidth() == 0 && box.left() == 0 && box.top() == 0, "Empty layout has drawable extent");
    }
    private static List<FormattedText> fitted(String message, int width, int maximumLines) {
        List<FormattedText> lines = CatalogJeiProgressOverlay.fitLines(FormattedText.of(message), width, maximumLines, SPLITTER);
        require(lines.size() <= Math.min(2, maximumLines), "Too many visible rows");
        for (FormattedText line : lines) { withinWidth(line, width); validSurrogates(line.getString()); }
        return lines;
    }
    private static void withinWidth(FormattedText line, int width) {
        require(SPLITTER.stringWidth(line) <= width + 0.0001F, "Rendered line is wider than available content");
    }
    private static void validSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.isHighSurrogate(current)) {
                require(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)), "Split high surrogate");
                i++;
            } else require(!Character.isLowSurrogate(current), "Split low surrogate");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void check(String name, Throwing action) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        try { action.run(); passed++; result.addProperty("status", "PASS"); }
        catch (Throwable error) {
            failed++;
            result.addProperty("status", "FAIL");
            result.addProperty("error", error.toString());
            error.printStackTrace(System.out);
        }
        CONDITIONS.add(result);
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
