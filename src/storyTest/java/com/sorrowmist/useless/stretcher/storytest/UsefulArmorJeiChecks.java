package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.item.UsefulArmorItem;
import com.sorrowmist.useless.stretcher.event.UsefulArmorRecipes;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses JEI's actual runtime visibility and filtered list after integrated-server tag sync. */
final class UsefulArmorJeiChecks {
    private static boolean requested, finished;
    private static int ticks;
    private static final AtomicInteger phase = new AtomicInteger();

    static void tick(Minecraft client) {
        if (finished) return;
        try {
            check(++ticks < 3600, "JEI preview timed out");
            if (client.getOverlay() != null) return;
            if (!requested) {
                if (client.screen == null) return;
                requested = true;
                LogUtils.getLogger().info("USEFUL ARMOR JEI: starting isolated world from {}", client.screen.getClass().getSimpleName());
                var settings = new LevelSettings("Useful armor JEI check", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("armor-jei-" + System.currentTimeMillis(), settings,
                        new WorldOptions(2L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            Object runtime = ((Optional<?>) Class.forName("mezz.jei.common.Internal")
                    .getMethod("getOptionalJeiRuntime").invoke(null)).orElse(null);
            if (runtime == null || phase.get() == 1) return;
            if (phase.get() == 3) {
                LogUtils.getLogger().info("USEFUL ARMOR JEI CHECKS PASSED: master/recipes default off; four real JEI ingredients hidden and absent from actual filtered list with feature both off/on; diamond and staff controls visible; integrated-server tags synchronized");
                finished = true;
                client.stop();
                return;
            }
            boolean enabled = phase.get() == 2;
            check(StretcherConfig.usefulArmorEnabled() == enabled, "expected server master switch");
            check(StretcherConfig.usefulArmorRecipesEnabled() == enabled, "expected server recipe gate");
            Class<?> runtimeApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            Object visibility = runtimeApi.getMethod("getIngredientVisibility").invoke(runtime);
            Object type = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
            var visible = Class.forName("mezz.jei.api.runtime.IIngredientVisibility").getMethod("isIngredientVisible",
                    Class.forName("mezz.jei.api.ingredients.IIngredientType"), Object.class);
            for (var item : List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                    ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS)) {
                check(!(Boolean) visible.invoke(visibility, type, new ItemStack(item.get())), "JEI hides " + item.getId());
            }
            check((Boolean) visible.invoke(visibility, type, new ItemStack(Items.DIAMOND_HELMET)), "diamond control visible");
            Object filter = runtimeApi.getMethod("getIngredientFilter").invoke(runtime);
            Class<?> filterApi = Class.forName("mezz.jei.api.runtime.IIngredientFilter");
            filterApi.getMethod("setFilterText", String.class).invoke(filter, "");
            var items = (List<?>) filterApi.getMethod("getFilteredIngredients",
                    Class.forName("mezz.jei.api.ingredients.IIngredientType")).invoke(filter, type);
            check(items.stream().noneMatch(value -> ((ItemStack) value).getItem() instanceof UsefulArmorItem),
                    "actual JEI filtered list has no useful armor");
            check(items.stream().anyMatch(value -> ((ItemStack) value).is(ModItems.WONDROUS_STAFF.get())),
                    "own staff control remains in actual list");
            LogUtils.getLogger().info("USEFUL ARMOR JEI VISIBILITY PASSED: enabled={}, filteredItems={}", enabled, items.size());
            phase.set(1);
            client.getSingleplayerServer().execute(() -> {
                StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(!enabled);
                StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(!enabled);
                UsefulArmorRecipes.apply(client.getSingleplayerServer().getRecipeManager());
                phase.set(enabled ? 3 : 2);
            });
        } catch (Throwable failure) {
            LogUtils.getLogger().error("USEFUL ARMOR JEI CHECKS FAILED", failure);
            finished = true;
            client.stop();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
