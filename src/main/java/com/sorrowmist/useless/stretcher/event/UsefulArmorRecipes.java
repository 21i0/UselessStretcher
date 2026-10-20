package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartingEvent;

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

    private static ShapedRecipe shaped(ResourceLocation id,List<String> rows,Ingredient ingot,ItemStack output) {
        int width=rows.get(0).length(),height=rows.size();
        var inputs=net.minecraft.core.NonNullList.withSize(width*height,Ingredient.EMPTY);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) if(rows.get(y).charAt(x)=='#') inputs.set(y*width+x,ingot);
        return new ShapedRecipe(id,"useful_armor",CraftingBookCategory.EQUIPMENT,width,height,inputs,output);
    }
    public static void apply(RecipeManager manager) {
        var pieces = List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS);
        var ids = pieces.stream().map(piece -> piece.getId()).toList();
        boolean enabled = StretcherConfig.usefulArmorRecipesEnabled();
        long present = ids.stream().filter(id -> com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.byKey(manager, id).isPresent()).count();
        if (enabled && present == pieces.size() || !enabled && present == 0) return;
        var recipes = new ArrayList<RecipeHolder<?>>(com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(manager));
        if (!enabled) {
            recipes.removeIf(recipe -> ids.contains(recipe.id()));
        } else {
            var ingot = BuiltInRegistries.ITEM.get(new ResourceLocation("useless_mod:useful_ingot"));
            var key = Map.of('#', Ingredient.of(ingot));
            var patterns = List.of(List.of("###", "# #"), List.of("# #", "###", "###"),
                    List.of("###", "# #", "# #"), List.of("# #", "# #"));
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                // Preserve data-pack definitions when the server enables this recipe family.
                if (com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.byKey(manager, piece.getId()).isPresent()) continue;
                recipes.add(new RecipeHolder<>(piece.getId(), shaped(piece.getId(), patterns.get(i), Ingredient.of(ingot), new ItemStack(piece.get()))));
            }
        }
        manager.replaceRecipes(recipes.stream().<net.minecraft.world.item.crafting.Recipe<?>>map(holder -> holder.value()).toList());
    }
}
