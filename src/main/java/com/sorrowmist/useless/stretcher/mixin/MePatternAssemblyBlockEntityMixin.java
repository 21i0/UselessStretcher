package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.stretcher.content.ae.PatternAssemblyReferenceAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Persists the server-store reference so repeated moves update one snapshot instead of leaking copies. */
@Mixin(MePatternAssemblyBlockEntity.class)
public abstract class MePatternAssemblyBlockEntityMixin implements PatternAssemblyReferenceAccessor {
    @Unique
    private static final String USELESS_STRETCHER$REFERENCE_TAG = "UselessStretcherPatternAssemblyRef";
    @Unique
    private UUID uselessStretcher$patternAssemblyReference;

    @Inject(method = "loadTag(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
            at = @At("TAIL"))
    private void uselessStretcher$loadReference(CompoundTag tag, HolderLookup.Provider registries,
                                                CallbackInfo ci) {
        uselessStretcher$patternAssemblyReference = null;
        String value = tag.getString(USELESS_STRETCHER$REFERENCE_TAG);
        if (!value.isBlank()) {
            try {
                uselessStretcher$patternAssemblyReference = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V",
            at = @At("TAIL"))
    private void uselessStretcher$saveReference(CompoundTag tag, HolderLookup.Provider registries,
                                                CallbackInfo ci) {
        if (uselessStretcher$patternAssemblyReference != null) {
            tag.putString(USELESS_STRETCHER$REFERENCE_TAG,
                    uselessStretcher$patternAssemblyReference.toString());
        }
    }

    @Override
    public UUID uselessStretcher$getPatternAssemblyReference() {
        return uselessStretcher$patternAssemblyReference;
    }

    @Override
    public void uselessStretcher$setPatternAssemblyReference(UUID reference) {
        uselessStretcher$patternAssemblyReference = reference;
    }
}
