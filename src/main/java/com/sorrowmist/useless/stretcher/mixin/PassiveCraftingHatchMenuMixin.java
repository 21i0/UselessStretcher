package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The menu mirrors the hatch's slot count through the inlined
 * {@code PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS} constant. Bump the
 * mirrored slot status array, fallback handler and two clamps to 4096.
 */
@Mixin(PassiveCraftingHatchMenu.class)
public abstract class PassiveCraftingHatchMenuMixin {

    @ModifyConstant(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/core/BlockPos;)V",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandStatusArray(int original) {
        return 4096;
    }

    @ModifyConstant(
            method = "handler(Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/core/BlockPos;)Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandFallbackHandler(int original) {
        return 4096;
    }

    @ModifyConstant(method = "getActivePatternSlots()I", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandActiveClamp(int original) {
        return 4096;
    }

    @ModifyConstant(method = "getConfiguredPatternSlots()I", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandConfiguredClamp(int original) {
        return 4096;
    }

    @ModifyConstant(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/core/BlockPos;)V",
            constant = @Constant(intValue = 158))
    private static int uselessStretcher$playerInventoryY(int original) {
        return 254;
    }

    @ModifyConstant(
            method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/core/BlockPos;)V",
            constant = @Constant(intValue = 218))
    private static int uselessStretcher$hotbarY(int original) {
        return 312;
    }

    @ModifyConstant(method = "getPatternSlotIndex(Lnet/minecraft/world/inventory/Slot;)I",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$patternSlotPageSize(int original) {
        return 90;
    }
}
