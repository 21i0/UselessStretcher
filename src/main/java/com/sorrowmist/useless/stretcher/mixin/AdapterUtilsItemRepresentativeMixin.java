package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.stretcher.content.ae.AeMaterialContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the addon is writing omniversal patterns, prefer a tag member that the nearby AE network
 * already has (or can craft) over Useless Mod's arbitrary representative. The thread-local context
 * is only set during our fetch, so all other Useless Mod code paths are unaffected.
 */
@Mixin(AdapterUtils.class)
public abstract class AdapterUtilsItemRepresentativeMixin {

    @Inject(
            method = "itemRepresentative(Lnet/minecraft/world/item/crafting/Ingredient;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            cancellable = true)
    private static void uselessStretcher$preferAeMaterial(Ingredient ingredient,
                                                          CallbackInfoReturnable<ItemStack> cir) {
        AeMaterialContext context = AeMaterialContext.current();
        if (context == null) return;
        ItemStack preferred = context.pickPreferred(ingredient, cir.getReturnValue());
        if (preferred != null && !preferred.isEmpty()) {
            cir.setReturnValue(preferred);
        }
    }
}
