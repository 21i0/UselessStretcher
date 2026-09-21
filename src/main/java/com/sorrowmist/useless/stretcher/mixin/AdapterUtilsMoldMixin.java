package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.stretcher.content.mold.MoldMatch;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdapterUtils.class)
public abstract class AdapterUtilsMoldMixin {
    @Inject(method = "matchesMold(Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$matches(Ingredient required, ItemStack actual,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (actual == null || actual.isEmpty() || !actual.is(ModItems.OMNIVERSAL_MYRIAD.get())) return;
        cir.setReturnValue(required == null || required.isEmpty()
                || !MoldMatch.matchingIds(required, MyriadMoldData.readEnabledMolds(actual)).isEmpty());
    }
}
