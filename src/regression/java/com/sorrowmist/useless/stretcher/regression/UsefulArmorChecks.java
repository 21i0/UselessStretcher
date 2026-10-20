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
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Server-side checks for the optional useful-ingot armor mode switch. */
final class UsefulArmorChecks {
    private UsefulArmorChecks() { }

    static void run(ServerLevel level) {
        check(!StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.getDefault(), "armor functionality defaults off");
        check(!StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.getDefault(), "armor crafting defaults off");
        var hidden = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                new ResourceLocation("c:hidden_from_recipe_viewers"));
        for (var piece : java.util.List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS)) {
            check(new ItemStack(piece.get()).is(hidden), "real recipe-viewer hide tag contains " + piece.getId());
        }
        check(!new ItemStack(Items.DIAMOND_HELMET).is(hidden), "normal diamond armor not hidden");
        check(ModItems.USEFUL_ARMOR_HELMET.get().getDefense() == 3
                        && ModItems.USEFUL_ARMOR_CHESTPLATE.get().getDefense() == 8
                        && ModItems.USEFUL_ARMOR_LEGGINGS.get().getDefense() == 6
                        && ModItems.USEFUL_ARMOR_BOOTS.get().getDefense() == 3,
                "useful armor uses diamond defense values");

        boolean previousEnabled = StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.get();
        boolean previousRecipes = StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.get();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UsefulArmorRegression"));
        try {
            StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(false);
            StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(true);
            UsefulArmorRecipes.apply(level.getRecipeManager());
            check(com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.byKey(level.getRecipeManager(), ModItems.USEFUL_ARMOR_HELMET.getId()).isEmpty(),
                    "master switch prevents crafting even when legacy recipe flag is enabled");
            player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            equip(player);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "disabled complete suit does not force creative");
            check(player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL),
                    "disabled complete suit permits direct game mode changes");
            player.getInventory().clearContent();
            StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(true);
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
                    new net.minecraftforge.event.entity.player.PlayerEvent.Clone(replacement, player, false));
            tick(replacement);
            check(replacement.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "clone without the suit restores the persistent pre-suit mode");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "removing the suit restores the mode from before activation");
            equip(player);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "enabled set activates again");
            StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(false);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                    "disabling functionality restores original mode while still wearing complete suit");
            check(player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL), "disabled lock releases controller");
            StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(true);
            player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            equip(player);
            tick(player);
            player.getInventory().clearContent();
            player.getPersistentData().putInt("useless_stretcher_useful_armor_previous_mode", 999);
            tick(player);
            check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "invalid saved prior mode falls back to survival");
            LogUtils.getLogger().info("REGRESSION useful armor: functionality/recipes default off, disabled full suit, legacy recipe gate, real crafting, enabled creative lock, disable-while-equipped restoration, clone and mode restore passed");
        } finally {
            StretcherConfig.SERVER_USEFUL_ARMOR_ENABLE.set(previousEnabled);
            StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(previousRecipes);
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
        int count = com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(manager).size();
        check(pieces.stream().noneMatch(piece -> com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.byKey(manager, piece.getId()).isPresent()), "disabled recipes absent from synced manager");
        StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(true);
        UsefulArmorRecipes.apply(manager);
        UsefulArmorRecipes.apply(manager);
        check(com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(manager).size() == count + 4, "enabling twice installs exactly four recipes");
        var ingot = BuiltInRegistries.ITEM.get(new ResourceLocation("useless_mod:useful_ingot"));
        check(ingot != Items.AIR, "current upstream useful ingot exists");
        for (var piece : pieces) {
            var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.byKey(manager, piece.getId()).orElseThrow().value();
            var inputs = recipe.getIngredients().stream().map(ingredient -> ingredient.isEmpty()
                    ? ItemStack.EMPTY : new ItemStack(ingot)).toList();
            var input = CraftingInput.of(recipe.getWidth(), recipe.getHeight(), inputs);
            var matched = com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipeFor(manager, RecipeType.CRAFTING, input, level).orElseThrow();
            check(matched.value().assemble(input, level.registryAccess()).is(piece.get()), "real armor pattern crafts " + piece.getId());
        }
        StretcherConfig.SERVER_USEFUL_ARMOR_RECIPES.set(false);
        UsefulArmorRecipes.apply(manager);
        check(com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(manager).size() == count, "disabling removes exactly four recipes and preserves other recipes");
    }

    private static void tick(net.minecraft.server.level.ServerPlayer player) {
        com.sorrowmist.useless.stretcher.event.UsefulArmorController.tick(
                new net.minecraftforge.event.tick.PlayerTickEvent.Post(player));
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
