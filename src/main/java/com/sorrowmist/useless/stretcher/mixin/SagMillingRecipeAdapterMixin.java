package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SagMillingRecipeAdapter;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * When {@code hide_enderio_grinding_balls} is enabled, drop every SAG-mill grinding-ball variant
 * at the adapter level. The base recipe is kept, so JEI, AE2 pattern encoding, our pattern picker
 * and the furnace all stop seeing the near-duplicate ball recipes.
 */
@Mixin(SagMillingRecipeAdapter.class)
public abstract class SagMillingRecipeAdapterMixin {

    @Inject(
            method = "convertAll(Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true)
    private void uselessStretcher$hideGrindingBalls(
            CallbackInfoReturnable<List<AdvancedAlloyFurnaceRecipe>> cir) {
        if (!StretcherConfig.hideEnderIoGrindingBalls()) return;
        List<AdvancedAlloyFurnaceRecipe> filtered = cir.getReturnValue().stream()
                .filter(recipe -> recipe == null || !recipe.id().getPath().contains("_with_grinding_ball_"))
                .toList();
        cir.setReturnValue(filtered);
    }
}
