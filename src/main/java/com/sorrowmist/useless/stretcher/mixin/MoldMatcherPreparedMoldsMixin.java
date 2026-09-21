package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldMatcher;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.Map;

@Mixin(targets = "com.sorrowmist.useless.content.recipe.MoldMatcher$PreparedMolds")
public abstract class MoldMatcherPreparedMoldsMixin {
    @Shadow @Final private Map<Integer, ItemStack> available;
    @Unique private MyriadMoldMatcher uselessStretcher$matcher;

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void uselessStretcher$prepare(Map<Integer, ItemStack> slots, CallbackInfo ci) {
        uselessStretcher$matcher = MyriadMoldMatcher.prepare(available);
    }

    @Inject(method = "matches(Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$matches(List<Ingredient> requirements, CallbackInfoReturnable<Boolean> cir) {
        if (uselessStretcher$matcher != null) cir.setReturnValue(uselessStretcher$matcher.matches(requirements));
    }
}
