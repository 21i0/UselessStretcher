package com.sorrowmist.useless.stretcher.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Addon configuration. The grinding-ball toggle mirrors Useless Mod's own recipe-compat toggles:
 * it is read when the shared recipe catalog is built, so it applies to JEI, AE2 pattern encoding
 * and the furnace alike — not just this addon's pattern picker.
 */
public final class StretcherConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue HIDE_ENDERIO_GRINDING_BALLS;
    public static final ModConfigSpec.BooleanValue HIGHLIGHT_SEE_THROUGH;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_FLOOR_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_FLOOR_WHITELIST;
    public static final ModConfigSpec.BooleanValue IDLE_SLEEP;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("acceleration");
        IDLE_SLEEP = BUILDER
                .comment("加速时是否对空闲机器自动休眠以省性能。",
                        "true（默认）：机器一段时间没有活动迹象（setChanged / 方块状态 / 能量均无变化）就休眠；",
                        "              一旦重新工作会立刻满速恢复。只有能可靠检测到活动的机器才会被休眠。",
                        "false：目标始终满速加速，不做任何休眠判断。")
                .define("idle_sleep", true);
        BUILDER.pop();

        BUILDER.push("recipe_compat");
        HIDE_ENDERIO_GRINDING_BALLS = BUILDER
                .comment("隐藏 Ender IO 半自磨机（SAG Mill）的磨珠配方变体。",
                        "每种磨珠都会生成几乎重复的配方，导致配方目录被刷屏。",
                        "重启游戏后生效。")
                .define("hide_enderio_grinding_balls", true);
        BUILDER.pop();

        BUILDER.push("highlight");
        HIGHLIGHT_SEE_THROUGH = BUILDER
                .comment("高亮框是否透视（穿墙显示）。",
                        "false = 被墙挡住的部分不显示（默认）；true = 隔着墙也能看到机器线框。")
                .define("highlight_see_through", false);
        BUILDER.pop();

        BUILDER.push("dimension_floor");
        DIMENSION_FLOOR_BLACKLIST = BUILDER
                .comment("四联/九联维度地板方块黑名单（方块 ID，例如 minecraft:stone）。",
                        "黑名单内的方块不会被用作边框/地板/中心方块，会自动回退到默认发光塑料。",
                        "支持 modid:* 通配整包禁用；留空则不额外限制（仍会走无用之物自带的黑名单）。")
                .defineList("blacklist", ArrayList::new, o -> o instanceof String);
        DIMENSION_FLOOR_WHITELIST = BUILDER
                .comment("四联/九联维度地板方块白名单（方块 ID）。",
                        "白名单非空时，只允许名单内的方块；名单外的方块回退到默认发光塑料。",
                        "支持 modid:* 通配；留空则不额外限制。")
                .defineList("whitelist", ArrayList::new, o -> o instanceof String);
        BUILDER.pop();

        COMMON_SPEC = BUILDER.build();
    }

    private StretcherConfig() {
    }

    public static boolean hideEnderIoGrindingBalls() {
        return HIDE_ENDERIO_GRINDING_BALLS.get();
    }

    public static boolean highlightSeeThrough() {
        return HIGHLIGHT_SEE_THROUGH.get();
    }

    public static boolean idleSleep() {
        return IDLE_SLEEP.get();
    }

    /** True when a dimension floor block id is allowed by this addon's own black/whitelist. */
    public static boolean isDimensionFloorBlockAllowed(ResourceLocation id) {
        if (id == null) return false;
        String key = id.toString();
        String namespace = id.getNamespace();
        for (String entry : DIMENSION_FLOOR_BLACKLIST.get()) {
            if (matches(entry, key, namespace)) return false;
        }
        List<? extends String> whitelist = DIMENSION_FLOOR_WHITELIST.get();
        if (!whitelist.isEmpty()) {
            for (String entry : whitelist) {
                if (matches(entry, key, namespace)) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean matches(String entry, String key, String namespace) {
        if (entry == null || entry.isBlank()) return false;
        String pattern = entry.trim();
        if (pattern.endsWith(":*")) {
            return namespace.equals(pattern.substring(0, pattern.length() - 2));
        }
        return pattern.equals(key);
    }
}
