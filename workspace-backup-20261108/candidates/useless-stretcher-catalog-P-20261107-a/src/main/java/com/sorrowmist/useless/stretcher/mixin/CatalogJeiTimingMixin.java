package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiTiming;
import com.sorrowmist.useless.stretcher.client.ClientCatalogLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.compat.jei.JEIPlugin", remap = false)
public abstract class CatalogJeiTimingMixin {
    @Inject(method = "onRuntimeAvailable", at = @At("RETURN"), require = 1)
    private void stretcher$runtimeAvailable(CallbackInfo ci) { ClientCatalogLifecycle.runtimeAvailable(); }
    @Inject(method = "onRuntimeUnavailable", at = @At("HEAD"), require = 1)
    private void stretcher$runtimeUnavailable(CallbackInfo ci) { ClientCatalogLifecycle.runtimeUnavailable(); }
    @Inject(method = "refreshAlloyFurnaceRecipes", at = @At("HEAD"), require = 1)
    private static void stretcher$refreshStart(CallbackInfo ci) { CatalogJeiTiming.beginRefresh(); }
    @Inject(method = "refreshAlloyFurnaceRecipes", at = @At("RETURN"), require = 1)
    private static void stretcher$refreshEnd(CallbackInfo ci) { CatalogJeiTiming.endRefresh(); }
    @Inject(method = "refreshAlloyFurnaceRecipes", at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/recipe/IRecipeManager;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"), require = 1)
    private static void stretcher$addStart(CallbackInfo ci) { CatalogJeiTiming.beginAdd(); }
    @Inject(method = "refreshAlloyFurnaceRecipes", at = @At(value = "INVOKE",
            target = "Lmezz/jei/api/recipe/IRecipeManager;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            shift = At.Shift.AFTER), require = 1)
    private static void stretcher$addEnd(CallbackInfo ci) { CatalogJeiTiming.endAdd(); }
}
