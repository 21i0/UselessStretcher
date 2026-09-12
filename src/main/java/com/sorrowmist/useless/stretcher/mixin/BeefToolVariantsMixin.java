package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the wondrous staff's item identity when the G-menu switches tool mode back to
 * NONE_MODE. Without this, Useless Mod's {@code BeefToolVariants.createForToolMode} always
 * rebuilds a base {@code endless_beaf_item} (太初杖), so a staff that went through OmniTools
 * (or any other tool mode) would silently turn back into the base staff.
 */
@Mixin(BeefToolVariants.class)
public abstract class BeefToolVariantsMixin {

    @Inject(
            method = "createForToolMode(Lnet/minecraft/world/item/ItemStack;Lcom/sorrowmist/useless/api/enums/tool/ToolTypeMode;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    private static void uselessStretcher$keepStaffIdentity(ItemStack source, ToolTypeMode mode,
                                                           CallbackInfoReturnable<ItemStack> cir) {
        if (mode != ToolTypeMode.NONE_MODE) return;
        if (!isWondrousStaffSource(source)) return;

        ItemStack result = new ItemStack(ModItems.WONDROUS_STAFF.get());
        result.applyComponents(source.getComponents());
        result.set(UComponents.WrenchTagEnabledComponent.get(), BeefToolVariants.isWrenchTagEnabled(source));
        result.set(UComponents.CurrentToolTypeComponent.get(), ToolTypeMode.NONE_MODE);
        cir.setReturnValue(result);
    }

    private static boolean isWondrousStaffSource(ItemStack source) {
        return source.getItem() instanceof WondrousStaffItem
                || source.has(StretcherComponents.WONDROUS_STAFF_SPEED.get())
                || source.has(StretcherComponents.WONDROUS_STAFF_MODE.get());
    }
}
