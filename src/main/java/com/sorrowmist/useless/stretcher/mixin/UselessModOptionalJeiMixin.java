package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The paired base port must not load its JEI plugin when JEI is absent. */
@Mixin(targets = "com.sorrowmist.useless.init.ClientSetup", remap = false)
public abstract class UselessModOptionalJeiMixin {
    @Inject(method = "onRecipeCatalogReady", at = @At("HEAD"), cancellable = true)
    private static void stretcher$refreshWithoutJei(CallbackInfo ci) {
        if (ModList.get().isLoaded("jei")) return;
        var player = Minecraft.getInstance().player;
        if (player != null) EndlessBeafItem.refreshAttackDamage(player);
        ci.cancel();
    }
}
