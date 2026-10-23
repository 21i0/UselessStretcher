package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import com.sorrowmist.useless.stretcher.content.ae.PassiveStretcherCursor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(PassiveCraftingHatchBlockEntity.class)
public abstract class InstalledStretcherPassiveMixin {
    @Unique private final Map<Integer, PassiveStretcherCursor> uselessStretcher$cursors = new HashMap<>();

    @Inject(method = "lambda$new$0", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InstalledStretcherPatterns.isStretcher(stack)) cir.setReturnValue(true);
    }

    @Inject(method = "decodePattern", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$select(int slot, ItemStack stack, CallbackInfoReturnable<OmniversalPatternDetails> cir) {
        if (InstalledStretcherPatterns.isStretcher(stack)) {
            cir.setReturnValue(uselessStretcher$cursors.computeIfAbsent(slot, ignored -> new PassiveStretcherCursor())
                    .select((PassiveCraftingHatchBlockEntity) (Object) this, slot, stack));
        } else uselessStretcher$cursors.remove(slot);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void uselessStretcher$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        uselessStretcher$cursors.clear();
        ListTag entries = tag.getList("StretcherPatternCursors", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS) continue;
            var cursor = new PassiveStretcherCursor();
            cursor.position(entry.getInt("next"));
            uselessStretcher$cursors.put(slot, cursor);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void uselessStretcher$save(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag entries = new ListTag();
        var hatch = (PassiveCraftingHatchBlockEntity) (Object) this;
        uselessStretcher$cursors.forEach((slot, cursor) -> {
            if (!InstalledStretcherPatterns.isStretcher(hatch.getPatterns().getStackInSlot(slot))) return;
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", slot); entry.putInt("next", cursor.position());
            entries.add(entry);
        });
        if (!entries.isEmpty()) tag.put("StretcherPatternCursors", entries);
    }
}
