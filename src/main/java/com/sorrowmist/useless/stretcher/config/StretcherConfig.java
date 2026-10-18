package com.sorrowmist.useless.stretcher.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModList;


/**
 * Addon configuration. The grinding-ball toggle mirrors Useless Mod's own recipe-compat toggles:
 * it is read when the shared recipe catalog is built, so it applies to JEI, AE2 pattern encoding
 * and the furnace alike — not just this addon's pattern picker.
 */
public final class StretcherConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.BooleanValue HIGHLIGHT_SEE_THROUGH;
    public static final ModConfigSpec.BooleanValue SERVER_STAFF_ACCELERATION;
    public static final ModConfigSpec.BooleanValue SERVER_RANGE_ACCELERATION;
    public static final ModConfigSpec.BooleanValue SERVER_REMOTE_RECLAIMER;
    public static final ModConfigSpec.BooleanValue SERVER_IDLE_THROTTLE;
    public static final ModConfigSpec.BooleanValue SERVER_ENTITY_DISABLE_AI;
    public static final ModConfigSpec.BooleanValue SERVER_STAFF_LEAF_DROP;
    public static final ModConfigSpec.DoubleValue SERVER_STAFF_LEAF_DROP_PROBABILITY;
    public static final ModConfigSpec.BooleanValue SERVER_DOLL_LEAF_DROP;
    public static final ModConfigSpec.DoubleValue SERVER_DOLL_LEAF_DROP_PROBABILITY;
    public static final ModConfigSpec.BooleanValue SERVER_STAFF_SUMMON;
    public static final ModConfigSpec.BooleanValue SERVER_STAFF_LOOT_REFRESH;
    public static final ModConfigSpec.BooleanValue SERVER_TIME_STOP;
    public static final ModConfigSpec.BooleanValue SERVER_APOTHEOSIS_COMPAT;
    public static final ModConfigSpec.BooleanValue SERVER_HIDE_ENDERIO_GRINDING_BALLS;
    public static final ModConfigSpec.BooleanValue SERVER_USEFUL_ARMOR_ENABLE;
    public static final ModConfigSpec.BooleanValue SERVER_USEFUL_ARMOR_RECIPES;
    public static final ModConfigSpec.BooleanValue SERVER_STARFIELD_INVITATION;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.translation("useless_stretcher.configuration.server_controls").push("server_controls");
        SERVER_STAFF_ACCELERATION = BUILDER
                .comment("服务器是否允许手杖加速功能。关闭后全服手杖加速请求都会被拒绝。")
                .translation("useless_stretcher.configuration.server.staff_acceleration")
                .define("staff_acceleration", true);
        SERVER_RANGE_ACCELERATION = BUILDER
                .comment("服务器是否允许范围加速。关闭后已有范围暂停，并禁止放置/启用；仍可回收。")
                .translation("useless_stretcher.configuration.server.range_acceleration")
                .define("range_acceleration", true);
        SERVER_REMOTE_RECLAIMER = BUILDER
                .comment("服务器是否允许时间流逝回收器远程管理和回收。")
                .translation("useless_stretcher.configuration.server.remote_reclaimer")
                .define("remote_reclaimer", true);
        SERVER_TIME_STOP = BUILDER
                .comment("是否允许手杖使用一次性时间暂停。每次启动固定暂停 12 秒，结束后自动恢复。")
                .translation("useless_stretcher.configuration.server.time_stop")
                .define("time_stop", true);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.highlight").push("highlight");
        HIGHLIGHT_SEE_THROUGH = BUILDER
                .comment("高亮框是否透视（穿墙显示）。",
                        "false = 被墙挡住的部分不显示（默认）；true = 隔着墙也能看到机器线框。")
                .translation("useless_stretcher.configuration.highlight.highlight_see_through")
                .define("highlight_see_through", false);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.acceleration").push("acceleration");
        SERVER_IDLE_THROTTLE = BUILDER
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
        SERVER_ENTITY_DISABLE_AI = BUILDER
                .comment("实体加速时是否关闭目标 Mob 的 AI。",
                        "开启后，目标不会执行寻路、攻击等 AI 行为；完整 tick 模式仍执行实体 tick，计时器模式只推进支持的生物计时器。",
                        "加速效果结束或目标消失时会恢复目标原本的 AI 状态。")
                .translation("useless_stretcher.configuration.acceleration.entity_disable_ai")
                .define("entity_disable_ai", true);
        BUILDER.pop();

        // Keep all staff-specific categories adjacent in the generated config UI while retaining
        // their established paths so existing server settings continue to load unchanged.
        BUILDER.translation("useless_stretcher.configuration.summoning").push("summoning");
        SERVER_STAFF_SUMMON = BUILDER
                .comment("是否允许手杖召唤目录和召唤功能。默认关闭。",
                        "关闭后，手杖 UI 中的召唤功能不会执行，已保存的手杖开关不会被删除。")
                .translation("useless_stretcher.configuration.summoning.enable")
                .define("enable", false);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.loot_refresh").push("loot_refresh");
        SERVER_STAFF_LOOT_REFRESH = BUILDER
                .comment("是否允许手杖刷新战利品箱、其它原版战利品容器、容器矿车和 Lootr 容器。默认关闭。",
                        "关闭后，手杖 UI 中仍会显示该功能，但无法开启或执行刷新。")
                .translation("useless_stretcher.configuration.loot_refresh.enable")
                .define("enable", false);
        BUILDER.pop();

        if (ModList.get().isLoaded("apotheosis") || ModList.get().isLoaded("apothic_enchanting")) {
            BUILDER.translation("useless_stretcher.configuration.apotheosis").push("apotheosis");
            SERVER_APOTHEOSIS_COMPAT = BUILDER
                    .comment("是否允许手杖修改 Apotheosis 世界等级并为已标记的附魔台添加属性。默认关闭。",
                            "附魔台属性需要 Apothic Enchanting（可独立安装）；世界等级需要 Apotheosis。关闭后已保存的附魔台数据保留，但不参与附魔计算。")
                    .translation("useless_stretcher.configuration.apotheosis.enable")
                    .define("enable", false);
            BUILDER.pop();
        } else {
            SERVER_APOTHEOSIS_COMPAT = null;
        }

        BUILDER.translation("useless_stretcher.configuration.staff_leaf_drop").push("staff_leaf_drop");
        SERVER_STAFF_LEAF_DROP = BUILDER
                .comment("是否启用破坏树叶时极低概率掉落荒辰移晷之杖彩蛋。")
                .translation("useless_stretcher.configuration.staff_leaf_drop.enable")
                .define("enable", true);
        SERVER_STAFF_LEAF_DROP_PROBABILITY = BUILDER
                .comment("破坏树叶掉落荒辰移晷之杖的概率，0.00001为十万分之一。")
                .translation("useless_stretcher.configuration.staff_leaf_drop.probability")
                .defineInRange("probability", 0.00001D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.doll_leaf_drop").push("doll_leaf_drop");
        SERVER_DOLL_LEAF_DROP = BUILDER
                .comment("是否允许打树叶独立掉落 2i 玩偶；不受手杖一次性掉落记录影响。")
                .translation("useless_stretcher.configuration.doll_leaf_drop.enable")
                .define("enable", true);
        SERVER_DOLL_LEAF_DROP_PROBABILITY = BUILDER
                .comment("每次打树叶独立获得 2i 玩偶的概率，0.0001 为万分之一；每位玩家每个存档只领取一次。")
                .translation("useless_stretcher.configuration.doll_leaf_drop.probability")
                .defineInRange("probability", 0.0001D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.two_i").push("two_i");
        SERVER_STARFIELD_INVITATION = BUILDER
                .comment("是否允许 2i 发出星空庭院邀请和重游，默认开启。",
                        "关闭只阻止新的进入，不删除好感、邀请或奖励记录；庭院内玩家仍可安全返回。")
                .translation("useless_stretcher.configuration.two_i.starfield_invitation")
                .define("starfield_invitation", true);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.recipe_compat").push("recipe_compat");
        SERVER_HIDE_ENDERIO_GRINDING_BALLS = BUILDER
                .comment("隐藏 Ender IO 半自磨机（SAG Mill）的磨珠配方变体。",
                        "每种磨珠都会生成几乎重复的配方，导致配方目录被刷屏。",
                        "重启游戏后生效。")
                .translation("useless_stretcher.configuration.recipe_compat.hide_enderio_grinding_balls")
                .define("hide_enderio_grinding_balls", true);
        BUILDER.pop();

        BUILDER.translation("useless_stretcher.configuration.useful_armor").push("useful_armor");
        SERVER_USEFUL_ARMOR_ENABLE = BUILDER
                .comment("是否启用有用锭装甲的创造模式套装功能，默认关闭。",
                        "关闭后已有套装也不会锁定创造，已被套装切换的玩家恢复原模式。四件装备始终在 JEI 隐藏。")
                .translation("useless_stretcher.configuration.server.useful_armor_enable")
                .define("enable", false);
        SERVER_USEFUL_ARMOR_RECIPES = BUILDER
                .comment("是否允许用有用锭合成装甲，默认关闭。修改后重启服务器或执行 /reload。",
                        "同时需要开启有用锭装甲功能总开关；四件装备始终在 JEI 隐藏。")
                .translation("useless_stretcher.configuration.server.useful_armor_recipes")
                .define("enable_recipes", false);
        BUILDER.pop();

        SERVER_SPEC = BUILDER.build();
    }

    private StretcherConfig() {
    }

    // Recipe/client setup can query these before a world or server config has loaded.
    private static <T> T value(ModConfigSpec.ConfigValue<T> option) {
        return SERVER_SPEC.isLoaded() ? option.get() : option.getDefault();
    }

    public static boolean hideEnderIoGrindingBalls() {
        return value(SERVER_HIDE_ENDERIO_GRINDING_BALLS);
    }

    public static boolean highlightSeeThrough() {
        return value(HIGHLIGHT_SEE_THROUGH);
    }

    public static boolean idleThrottle() {
        return value(SERVER_IDLE_THROTTLE);
    }

    public static boolean entityDisableAi() {
        return value(SERVER_ENTITY_DISABLE_AI);
    }

    public static boolean enableStaffLeafDrop() {
        return value(SERVER_STAFF_LEAF_DROP);
    }

    public static double staffLeafDropProbability() {
        return value(SERVER_STAFF_LEAF_DROP_PROBABILITY);
    }

    public static boolean enableDollLeafDrop() {
        return value(SERVER_DOLL_LEAF_DROP);
    }

    public static double dollLeafDropProbability() {
        return value(SERVER_DOLL_LEAF_DROP_PROBABILITY);
    }

    public static boolean enableStaffSummon() {
        return value(SERVER_STAFF_SUMMON);
    }

    public static boolean enableStaffLootRefresh() {
        return value(SERVER_STAFF_LOOT_REFRESH);
    }

    public static boolean hasApotheosisConfig() {
        return SERVER_APOTHEOSIS_COMPAT != null;
    }

    public static boolean enableApotheosisCompat() {
        return SERVER_APOTHEOSIS_COMPAT != null && value(SERVER_APOTHEOSIS_COMPAT);
    }

    public static boolean serverStaffAcceleration() {
        return value(SERVER_STAFF_ACCELERATION);
    }

    public static boolean serverRangeAcceleration() {
        return value(SERVER_RANGE_ACCELERATION);
    }

    public static boolean serverRemoteReclaimer() {
        return value(SERVER_REMOTE_RECLAIMER);
    }

    public static boolean serverTimeStop() {
        return value(SERVER_TIME_STOP);
    }

    public static boolean usefulArmorRecipesEnabled() {
        return usefulArmorEnabled() && value(SERVER_USEFUL_ARMOR_RECIPES);
    }

    public static boolean usefulArmorEnabled() {
        return value(SERVER_USEFUL_ARMOR_ENABLE);
    }

    public static boolean starfieldInvitationEnabled() {
        return value(SERVER_STARFIELD_INVITATION);
    }

}
