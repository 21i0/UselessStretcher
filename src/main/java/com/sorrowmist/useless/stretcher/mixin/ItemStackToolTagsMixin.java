package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the upstream wrench-tag switch to the staff's dynamic item stack. */
@Mixin(ItemStack.class)
public abstract class ItemStackToolTagsMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$staffWrenchTag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (!(self.getItem() instanceof WondrousStaffItem)) return;
        String namespace = tag.location().getNamespace();
        String path = tag.location().getPath();
        // Mekanism's configurator tag currently inherits c:tools/wrench.  It also
        // uses that tag as a fallback when an item does not expose its custom
        // wrench abilities, so it must follow the same G-menu switch directly.
        if ((namespace.equals("c") && (path.equals("tools/wrench") || path.equals("wrenches")))
                || (namespace.equals("gtceu") && path.equals("crafting_tools/wrench"))
                || (namespace.equals("mekanism") && path.equals("configurators"))) {
            cir.setReturnValue(BeefToolVariants.isWrenchTagEnabled(self));
        }
    }
}
