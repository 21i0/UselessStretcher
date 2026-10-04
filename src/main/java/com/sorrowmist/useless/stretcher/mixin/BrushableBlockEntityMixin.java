package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.item.StaffBrushContext;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a staff-brushed archaeological block brushable after each instant reward. */
@Mixin(BrushableBlockEntity.class)
public abstract class BrushableBlockEntityMixin {
    @Shadow private int brushCount;
    @Shadow private long brushCountResetsAtTick;
    @Shadow private long coolDownEndsAtTick;
    @Shadow private ItemStack item;

    @org.spongepowered.asm.mixin.gen.Invoker("dropContent")
    protected abstract void uselessStretcher$dropContent(Player player);

    @Inject(method = "brushingCompleted", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$repeat(Player player, CallbackInfo callback) {
        if (!StaffBrushContext.active()) return;
        ItemStack reward = item.copy();
        uselessStretcher$dropContent(player);
        if (!reward.isEmpty()) item = reward;
        brushCount = 0;
        brushCountResetsAtTick = 0L;
        coolDownEndsAtTick = 0L;
        callback.cancel();
    }
}
