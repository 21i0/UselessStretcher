package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SERVER config is loaded after the initial recipe reload, so install these before player sync. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class UsefulArmorRecipes {
    private UsefulArmorRecipes() { }

    @SubscribeEvent
    public static void starting(ServerStartingEvent event) {
        apply(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        apply(event.getPlayerList().getServer().getRecipeManager());
    }

    public static void apply(RecipeManager manager) {
        var pieces = List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS);
        var ids = pieces.stream().map(piece -> piece.getId()).toList();
        boolean enabled = StretcherConfig.usefulArmorRecipesEnabled();
        long present = ids.stream().filter(id -> manager.byKey(id).isPresent()).count();
        if (enabled && present == pieces.size() || !enabled && present == 0) return;
        var recipes = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        if (!enabled) {
            recipes.removeIf(recipe -> ids.contains(recipe.id()));
        } else {
            var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.parse("useless_mod:useful_ingot"));
            var key = Map.of('#', Ingredient.of(ingot));
            var patterns = List.of(List.of("###", "# #"), List.of("# #", "###", "###"),
                    List.of("###", "# #", "# #"), List.of("# #", "# #"));
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                // Preserve data-pack definitions when the server enables this recipe family.
                if (manager.byKey(piece.getId()).isPresent()) continue;
                recipes.add(new RecipeHolder<>(piece.getId(), new ShapedRecipe("useful_armor",
                        CraftingBookCategory.EQUIPMENT, ShapedRecipePattern.of(key, patterns.get(i)),
                        new ItemStack(piece.get()))));
            }
        }
        manager.replaceRecipes(recipes);
    }
}
