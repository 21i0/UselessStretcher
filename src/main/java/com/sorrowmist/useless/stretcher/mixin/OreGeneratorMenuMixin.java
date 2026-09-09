package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.menus.OreGeneratorMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The ore generator shares the paged base layout, so its player inventory and
 * hotbar also move down for the 10-row grid.
 */
@Mixin(OreGeneratorMenu.class)
public abstract class OreGeneratorMenuMixin {

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

    @ModifyConstant(method = "getSampleSlotIndex(Lnet/minecraft/world/inventory/Slot;)I",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$sampleSlotPageSize(int original) {
        return 90;
    }
}
