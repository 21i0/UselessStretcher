package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldMatcher;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(OmniversalMoldHubBlockEntity.class)
public abstract class MoldHubMultiMoldMixin {
    @Inject(method = "matchesMolds(Ljava/util/List;Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$matches(List<Ingredient> requirements, List<ItemStack> available,
                                                CallbackInfoReturnable<Boolean> cir) {
        MyriadMoldMatcher matcher = MyriadMoldMatcher.prepare(MoldMatcher.sparseSlots(available));
        if (matcher != null) cir.setReturnValue(matcher.matches(requirements));
    }
}
