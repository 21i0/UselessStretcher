package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Older saves embed a {@code Size: 540} tag that {@link ItemStackHandler#deserializeNBT}
 * honours by shrinking the handler back down to 540, which (a) silently drops the expanded
 * 4096 capacity and (b) can clear the passive hatch's patterns. Strip the {@code Size} tag
 * only for our expanded {@link RecoverableItemStackHandler} so existing blocks keep the new
 * capacity and their items without needing to be re-placed.
 */
@Mixin(ItemStackHandler.class)
public abstract class ItemStackHandlerMixin {

    @Inject(
            method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"))
    private void uselessStretcher$keepExpandedSize(HolderLookup.Provider provider, CompoundTag nbt,
                                                   CallbackInfo ci) {
        if ((Object) this instanceof RecoverableItemStackHandler) {
            nbt.remove("Size");
        }
    }
}
