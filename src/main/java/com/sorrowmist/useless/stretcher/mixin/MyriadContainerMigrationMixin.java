package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old chest/machine items are migrated before initial or incremental menu item packets. */
@Mixin(AbstractContainerMenu.class)
public abstract class MyriadContainerMigrationMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = {"broadcastChanges", "sendAllDataToRemote"}, at = @At("HEAD"))
    private void uselessStretcher$migrate(CallbackInfo ci) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !EffectiveSide.get().isServer()) return;
        var level = server.overworld();
        for (Slot slot : slots) {
            if (MyriadMoldData.externalize(level, slot.getItem())) slot.setChanged();
        }
        MyriadMoldData.externalize(level, getCarried());
    }
}
