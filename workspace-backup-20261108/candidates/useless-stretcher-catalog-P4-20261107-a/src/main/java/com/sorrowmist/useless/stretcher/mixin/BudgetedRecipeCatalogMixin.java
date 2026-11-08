package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.content.catalog.BudgetedRecipeCatalog;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AlloyFurnaceRecipeCatalog.class)
public abstract class BudgetedRecipeCatalogMixin {
    @Inject(method = "snapshot", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stretcher$nonBlockingQuery(Level level, CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(BudgetedRecipeCatalog.snapshotOrSchedule(level));
    }

    @Inject(method = "prewarm", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stretcher$coalescePrewarm(Level level, CallbackInfo ci) {
        BudgetedRecipeCatalog.request(level);
        ci.cancel();
    }

    @Inject(method = "invalidate()V", at = @At("RETURN"), require = 1)
    private static void stretcher$cancelAll(CallbackInfo ci) { BudgetedRecipeCatalog.cancelAll(); }

    @Inject(method = "invalidate(Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"), require = 1)
    private static void stretcher$cancel(Level level, CallbackInfo ci) {
        if (level == null) BudgetedRecipeCatalog.cancelAll();
        else BudgetedRecipeCatalog.cancel(level);
    }
}
