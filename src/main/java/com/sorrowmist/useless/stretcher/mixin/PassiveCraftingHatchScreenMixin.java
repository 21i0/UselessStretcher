package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.client.gui.PassiveCraftingHatchScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Enlarges the passive crafting hatch screen to the 10-row storage grid while
 * keeping its right-hand settings sidebar in place. The informational labels
 * move down below the storage grid.
 */
@Mixin(PassiveCraftingHatchScreen.class)
public abstract class PassiveCraftingHatchScreenMixin {

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/PassiveCraftingHatchMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
            constant = @Constant(intValue = 242))
    private static int uselessStretcher$screenHeight(int original) {
        return 336;
    }

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/PassiveCraftingHatchMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
            constant = @Constant(intValue = 146))
    private static int uselessStretcher$inventoryLabelY(int original) {
        return 242;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 3, ordinal = 0))
    private static int uselessStretcher$storageRows(int original) {
        return 10;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 158))
    private static int uselessStretcher$playerInventoryY(int original) {
        return 254;
    }

    @ModifyConstant(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
            constant = @Constant(intValue = 218))
    private static int uselessStretcher$hotbarY(int original) {
        return 312;
    }

    @ModifyConstant(method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            constant = @Constant(intValue = 91))
    private static int uselessStretcher$maxMultiplierY(int original) {
        return 204;
    }

    @ModifyConstant(method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            constant = @Constant(intValue = 102))
    private static int uselessStretcher$countdownY(int original) {
        return 215;
    }

    @ModifyConstant(method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            constant = @Constant(intValue = 116))
    private static int uselessStretcher$statusY(int original) {
        return 227;
    }

    @ModifyConstant(method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            constant = @Constant(intValue = 128))
    private static int uselessStretcher$progressY(int original) {
        return 239;
    }
}
