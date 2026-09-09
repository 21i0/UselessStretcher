package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Grows the shared paged-menu from 27 slots (3 rows x 9 columns) per page to
 * 90 slots (10 rows x 9 columns) per page so a 4096-slot inventory fits in 46
 * pages. The mold hub shares this base and therefore grows too, which is
 * harmless.
 *
 * <p>Useless Mod 2.3.3 moved the slot layout into a new 12-argument constructor
 * (with {@code PagedMenuPageMemory}); the coordinate constants are duplicated in
 * the 5- and 6-argument delegating constructors, so all three are patched.
 */
@Mixin(PagedRecoverableMenu.class)
public abstract class PagedRecoverableMenuMixin {

    // 5-arg ctor: default layout used by the pattern assembly and mold hub.
    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Lnet/minecraft/core/BlockPos;)V",
            constant = @Constant(intValue = 85))
    private static int uselessStretcher$shiftPlayerInventory5(int original) {
        return 211;
    }

    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Lnet/minecraft/core/BlockPos;)V",
            constant = @Constant(intValue = 143))
    private static int uselessStretcher$shiftHotbar5(int original) {
        return 269;
    }

    // 6-arg ctor: default layout + page memory.
    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Lnet/minecraft/core/BlockPos;Lcom/sorrowmist/useless/content/blockentities/PagedMenuPageMemory;)V",
            constant = @Constant(intValue = 85))
    private static int uselessStretcher$shiftPlayerInventory6(int original) {
        return 211;
    }

    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Lnet/minecraft/core/BlockPos;Lcom/sorrowmist/useless/content/blockentities/PagedMenuPageMemory;)V",
            constant = @Constant(intValue = 143))
    private static int uselessStretcher$shiftHotbar6(int original) {
        return 269;
    }

    // 12-arg ctor owns the actual storage slot loop. The first "3" is the storage
    // row bound; the second belongs to the player-inventory loop and must stay.
    @ModifyConstant(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Lnet/minecraft/core/BlockPos;IIIIIILcom/sorrowmist/useless/content/blockentities/PagedMenuPageMemory;)V",
            constant = @Constant(intValue = 3, ordinal = 0))
    private static int uselessStretcher$expandStorageRows(int original) {
        return 10;
    }

    @ModifyConstant(
            method = "quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$quickMovePageSize(int original) {
        return 90;
    }

    @ModifyConstant(method = "calculatePageCount()I", constant = @Constant(intValue = 27))
    private static int uselessStretcher$pageCountSize(int original) {
        return 90;
    }

    @ModifyConstant(method = "calculateActivePageCount()I", constant = @Constant(intValue = 27))
    private static int uselessStretcher$activePageCountSize(int original) {
        return 90;
    }
}
