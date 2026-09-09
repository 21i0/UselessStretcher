package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.client.gui.OreGeneratorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Enlarges the ore generator screen to match the shared 10-row grid.
 */
@Mixin(OreGeneratorScreen.class)
public abstract class OreGeneratorScreenMixin {

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/OreGeneratorMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
            constant = @Constant(intValue = 242))
    private static int uselessStretcher$screenHeight(int original) {
        return 336;
    }

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/menus/OreGeneratorMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V",
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
    private static int uselessStretcher$countdownY(int original) {
        return 204;
    }

    @ModifyConstant(method = "renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            constant = @Constant(intValue = 103))
    private static int uselessStretcher$activeSlotsY(int original) {
        return 215;
    }
}
