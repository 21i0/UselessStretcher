package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.capsule.CapsuleGenerationContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class CapsuleGenerationHeightMixin {
    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void capsuleSourceHeight(Heightmap.Types type, int x, int z, CallbackInfoReturnable<Integer> cir) {
        Integer height = CapsuleGenerationContext.sourceHeight((Level) (Object) this, type, x, z);
        if (height != null) cir.setReturnValue(height);
    }
}
