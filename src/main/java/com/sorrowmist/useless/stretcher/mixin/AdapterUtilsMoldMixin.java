package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a configured {@code useless_stretcher:omniversal_myriad} stack satisfy any mold that is
 * listed in its NBT, so one block can stand in for a whole bank of molds inside the alloy furnace.
 */
@Mixin(AdapterUtils.class)
public abstract class AdapterUtilsMoldMixin {

    @Inject(
            method = "matchesMold(Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void uselessStretcher$matchesMultiMold(Ingredient requiredMold, ItemStack actualMold,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (requiredMold == null || requiredMold.isEmpty() || actualMold == null || actualMold.isEmpty()) return;
        if (actualMold.getItem() != ModItems.OMNIVERSAL_MYRIAD.get()) return;

        for (ResourceLocation id : MyriadMoldData.readEnabledMolds(actualMold)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && requiredMold.test(new ItemStack(item))) {
                cir.setReturnValue(true);
                return;
            }
            // Data-component ingredients (e.g. Productive Bees spawn eggs) keep their identity
            // in the component, so a bare stack can never match. Compare against the
            // ingredient's own representative instead.
            try {
                ItemStack representative = AdapterUtils.itemRepresentative(requiredMold);
                if (representative != null && !representative.isEmpty()
                        && id.equals(BuiltInRegistries.ITEM.getKey(representative.getItem()))) {
                    cir.setReturnValue(true);
                    return;
                }
            } catch (RuntimeException ignored) {
                // Custom ingredient without an enumerable representative.
            }
        }
    }
}
