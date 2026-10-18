package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import com.sorrowmist.useless.stretcher.event.UsefulArmorRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Server-side checks for the optional useful-ingot armor mode switch. */
final class UsefulArmorChecks {
    private UsefulArmorChecks() { }

    static void run(ServerLevel level) {
        check(!StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.getDefault(), "only armor crafting defaults off");
        check(ModItems.USEFUL_ARMOR_HELMET.get().getDefense() == 3
                        && ModItems.USEFUL_ARMOR_CHESTPLATE.get().getDefense() == 8
                        && ModItems.USEFUL_ARMOR_LEGGINGS.get().getDefense() == 6
                        && ModItems.USEFUL_ARMOR_BOOTS.get().getDefense() == 3,
                "useful armor uses diamond defense values");

        boolean previous = StretcherConfig.usefulArmorRecipesEnabled();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UsefulArmorRegression"));
        try {
            recipes(level);
            StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(false);
            player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.USEFUL_ARMOR_HELMET.get()));
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "partial suit does not change mode");
            equip(player);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                    "complete suit switches to creative with recipes disabled");
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.ADVENTURE, GameType.SPECTATOR}) {
                check(!player.setGameMode(mode) && !player.gameMode.changeGameModeForPlayer(mode)
                                && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                        "commands and direct controller calls cannot switch to " + mode);
            }
            var replacement = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UsefulArmorClone"));
            replacement.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            com.sorrowmist.useless.stretcher.event.UsefulArmorController.clone(
                    new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(replacement, player, false));
            tick(replacement);
            check(replacement.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "clone without the suit restores the persistent pre-suit mode");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "removing the suit restores the mode from before activation");
            equip(player);
            tick(player);
            player.getInventory().clearContent();
            player.getPersistentData().putInt("useless_stretcher_useful_armor_previous_mode", 999);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "invalid saved prior mode falls back to survival");
            LogUtils.getLogger().info("REGRESSION useful armor: recipes default off/on/off, real crafting, diamond stats, partial suit, command/direct creative lock, clone and mode restore passed");
        } finally {
            StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(previous);
            UsefulArmorRecipes.apply(level.getRecipeManager());
            player.getInventory().clearContent();
            tick(player);
        }
    }

    private static void recipes(ServerLevel level) {
        var manager = level.getRecipeManager();
        var pieces = java.util.List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS);
        StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(false);
        UsefulArmorRecipes.apply(manager);
        int count = manager.getRecipes().size();
        check(pieces.stream().noneMatch(piece -> manager.byKey(piece.getId()).isPresent()), "disabled recipes absent from synced manager");
        StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(true);
        UsefulArmorRecipes.apply(manager);
        UsefulArmorRecipes.apply(manager);
        check(manager.getRecipes().size() == count + 4, "enabling twice installs exactly four recipes");
        var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.parse("useless_mod:useful_ingot"));
        check(ingot != Items.AIR, "current upstream useful ingot exists");
        for (var piece : pieces) {
            var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) manager.byKey(piece.getId()).orElseThrow().value();
            var inputs = recipe.getIngredients().stream().map(ingredient -> ingredient.isEmpty()
                    ? ItemStack.EMPTY : new ItemStack(ingot)).toList();
            var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), inputs);
            var matched = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            check(matched.value().assemble(input, level.registryAccess()).is(piece.get()), "real armor pattern crafts " + piece.getId());
        }
        StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(false);
        UsefulArmorRecipes.apply(manager);
        check(manager.getRecipes().size() == count, "disabling removes exactly four recipes and preserves other recipes");
    }

    private static void tick(net.minecraft.server.level.ServerPlayer player) {
        com.sorrowmist.useless.stretcher.event.UsefulArmorController.tick(
                new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
    }

    private static void equip(net.minecraft.server.level.ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.USEFUL_ARMOR_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.USEFUL_ARMOR_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.USEFUL_ARMOR_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.USEFUL_ARMOR_BOOTS.get()));
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError("useful armor: " + message);
    }
}
