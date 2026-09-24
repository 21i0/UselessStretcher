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
    public static final ModConfigSpec.BooleanValue IDLE_THROTTLE;
    public static final ModConfigSpec.BooleanValue ENTITY_DISABLE_AI;
    public static final ModConfigSpec.BooleanValue ENABLE_STAFF_LEAF_DROP;
    public static final ModConfigSpec.DoubleValue STAFF_LEAF_DROP_PROBABILITY;
    public static final ModConfigSpec.BooleanValue ENABLE_STAFF_SUMMON;
    public static final ModConfigSpec.BooleanValue ENABLE_STAFF_LOOT_REFRESH;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.translation("useless_stretcher.configuration.acceleration").push("acceleration");
        IDLE_THROTTLE = BUILDER
                .comment("加速时是否对空闲机器自动降频以省性能（推荐开启）。",
                        "最高档会在每游戏刻把目标 tick 额外执行 1024 次，所以哪怕目标空闲、单次 tick 很便宜，",
                        "高倍率放大后也可能很可观；遇到空闲 tick 偏重的机器（扫背包/查配方）会直接拖垮 TPS。",
                        "true（默认）：永久模式下，机器连续 200 刻没有任何活动迹象（setChanged / 方块状态 / 能量 / 物品 / 流体 / AE 节点）",
                        "              就把每刻次数降到 4 次；任一活动迹象出现立刻恢复满速。普通模式（30 秒）不降频。",
                        "              注意是降频不是休眠，机器永远不会被停掉，最坏也只是短暂变慢。",
                        "              只有曾经被确认为「能被检测到活动」的机器才会降频，检测不到活动的机器永不休眠。",
                        "false：永久模式目标始终满速加速，不做动态降频判断；普通模式本来就始终满速。")
                .translation("useless_stretcher.configuration.acceleration.idle_throttle")
                .define("idle_throttle", true);
        ENTITY_DISABLE_AI = BUILDER
                .comment("实体加速时是否关闭目标 Mob 的 AI。",
                        "开启后，目标不会执行寻路、攻击等 AI 行为，但仍会按加速倍率执行实体 tick。",
                        "加速效果结束或目标消失时会恢复目标原本的 AI 状态。")
                .translation("useless_stretcher.configuration.acceleration.entity_disable_ai")
                .define("entity_disable_ai", true);
        BUILDER.pop();

        // Keep all staff-specific categories adjacent in the generated config UI while retaining
        // their established paths so existing server settings continue to load unchanged.
        BUILDER.translation("useless_stretcher.configuration.summoning").push("summoning");
        ENABLE_STAFF_SUMMON = BUILDER
                .comment("是否允许手杖召唤目录和召唤功能。默认关闭。",
                        "关闭后，手杖 UI 中的召唤功能不会执行，已保存的手杖开关不会被删除。")
                .translation("useless_stretcher.configuration.summoning.enable")
                .define("enable", false);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.loot_refresh").push("loot_refresh");
        ENABLE_STAFF_LOOT_REFRESH = BUILDER
                .comment("是否允许手杖刷新战利品箱、其它原版战利品容器、容器矿车和 Lootr 容器。默认关闭。",
                        "关闭后，手杖 UI 中仍会显示该功能，但无法开启或执行刷新。")
                .translation("useless_stretcher.configuration.loot_refresh.enable")
                .define("enable", false);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.staff_leaf_drop").push("staff_leaf_drop");
        ENABLE_STAFF_LEAF_DROP = BUILDER
                .comment("是否启用破坏树叶时极低概率掉落荒辰移晷之杖彩蛋。")
                .translation("useless_stretcher.configuration.staff_leaf_drop.enable")
                .define("enable", true);
        STAFF_LEAF_DROP_PROBABILITY = BUILDER
                .comment("破坏树叶掉落荒辰移晷之杖的概率，0.00001为十万分之一。")
                .translation("useless_stretcher.configuration.staff_leaf_drop.probability")
                .defineInRange("probability", 0.00001D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.recipe_compat").push("recipe_compat");
        HIDE_ENDERIO_GRINDING_BALLS = BUILDER
                .comment("隐藏 Ender IO 半自磨机（SAG Mill）的磨珠配方变体。",
                        "每种磨珠都会生成几乎重复的配方，导致配方目录被刷屏。",
                        "重启游戏后生效。")
                .translation("useless_stretcher.configuration.recipe_compat.hide_enderio_grinding_balls")
                .define("hide_enderio_grinding_balls", true);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.highlight").push("highlight");
        HIGHLIGHT_SEE_THROUGH = BUILDER
                .comment("高亮框是否透视（穿墙显示）。",
                        "false = 被墙挡住的部分不显示（默认）；true = 隔着墙也能看到机器线框。")
                .translation("useless_stretcher.configuration.highlight.highlight_see_through")
                .define("highlight_see_through", false);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.dimension_floor").push("dimension_floor");
        DIMENSION_FLOOR_BLACKLIST = BUILDER
                .comment("四联/九联维度地板方块黑名单（方块 ID，例如 minecraft:stone）。",
                        "黑名单内的方块不会被用作边框/地板/中心方块，会自动回退到默认发光塑料。",
                        "支持 modid:* 通配整包禁用；留空则不额外限制（仍会走无用之物自带的黑名单）。")
                .translation("useless_stretcher.configuration.dimension_floor.blacklist")
                .defineList("blacklist", ArrayList::new, o -> o instanceof String);
        DIMENSION_FLOOR_WHITELIST = BUILDER
                .comment("四联/九联维度地板方块白名单（方块 ID）。",
                        "白名单非空时，只允许名单内的方块；名单外的方块回退到默认发光塑料。",
                        "支持 modid:* 通配；留空则不额外限制。")
                .translation("useless_stretcher.configuration.dimension_floor.whitelist")
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

    public static boolean idleThrottle() {
        return IDLE_THROTTLE.get();
    }

    public static boolean entityDisableAi() {
        return ENTITY_DISABLE_AI.get();
    }

    public static boolean enableStaffLeafDrop() {
        return ENABLE_STAFF_LEAF_DROP.get();
    }

    public static double staffLeafDropProbability() {
        return STAFF_LEAF_DROP_PROBABILITY.get();
    }

    public static boolean enableStaffSummon() {
        return ENABLE_STAFF_SUMMON.get();
    }

    public static boolean enableStaffLootRefresh() {
        return ENABLE_STAFF_LOOT_REFRESH.get();
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
