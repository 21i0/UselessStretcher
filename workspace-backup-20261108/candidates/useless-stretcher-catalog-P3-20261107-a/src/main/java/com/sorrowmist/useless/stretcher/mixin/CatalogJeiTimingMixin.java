package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiPublication;
import com.sorrowmist.useless.stretcher.client.ClientCatalogLifecycle;
import com.sorrowmist.useless.stretcher.content.catalog.CatalogPatchSupport;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.compat.jei.JEIPlugin", remap = false)
public abstract class CatalogJeiTimingMixin {
    @Inject(method = "registerRecipes", at = @At("HEAD"), require = 1)
    private void stretcher$registrationReset(CallbackInfo ci) {
        if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.reset();
    }
    // Keep native category/catalyst registration, defer only the large catalog to runtime slices.
    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE",
            target = "Lcom/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeCatalog;isReady(Lnet/minecraft/world/level/Level;)Z"), require = 1)
    private boolean stretcher$deferInitialCatalog(Level level) {
        return !CatalogPatchSupport.jeiPublicationEnabled() && AlloyFurnaceRecipeCatalog.isReady(level);
    }
    @Inject(method = "onRuntimeAvailable", at = @At("RETURN"), require = 1)
    private void stretcher$runtimeAvailable(CallbackInfo ci) {
        if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.runtimeAvailable();
        ClientCatalogLifecycle.runtimeAvailable();
    }
    @Inject(method = "onRuntimeUnavailable", at = @At("RETURN"), require = 1)
    private void stretcher$runtimeUnavailable(CallbackInfo ci) {
        if (CatalogPatchSupport.jeiPublicationEnabled()) CatalogJeiPublication.reset();
        ClientCatalogLifecycle.runtimeUnavailable();
    }
    @Inject(method = "refreshAlloyFurnaceRecipes", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stretcher$refreshInSlices(CallbackInfo ci) {
        if (CatalogPatchSupport.jeiPublicationEnabled()) { CatalogJeiPublication.request(); ci.cancel(); }
    }
}
