package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.client.gui.PagedRecoverableScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Enlarges the shared paged screen (pattern assembly + mold hub) to render the
 * 10-row storage grid and the shifted player inventory / hotbar.
 */
@Mixin(PagedRecoverableScreen.class)
public abstract class PagedRecoverableScreenMixin {

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/PagedRecoverableMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
            constant = @Constant(intValue = 166))
    private static int uselessStretcher$screenHeight(int original) {
        return 292;
    }

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/PagedRecoverableMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
            constant = @Constant(intValue = 73))
    private static int uselessStretcher$inventoryLabelY(int original) {
        return 199;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 3, ordinal = 0))
    private static int uselessStretcher$storageRows(int original) {
        return 10;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 85))
    private static int uselessStretcher$playerInventoryY(int original) {
        return 211;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 143))
    private static int uselessStretcher$hotbarY(int original) {
        return 269;
    }
}
