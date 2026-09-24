package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.LightningRodBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents Useless Mod's highest-priority event listener from consuming a wondrous-staff rod
 * click before this addon's item or event handlers can see it.
 */
@Mixin(UselessMod.class)
public abstract class UselessModLightningRodMixin {

    @Inject(
            method = "onRightClickBlock(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void uselessStretcher$interceptLightningRod(PlayerInteractEvent.RightClickBlock event,
                                                         CallbackInfo ci) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.WONDROUS_STAFF.get())
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof LightningRodBlock)) {
            return;
        }

        // These modes own the click in the addon's dedicated handler. Stop only this upstream
        // listener and leave the shared event untouched so placement/filter packets still run.
        if (RangeAccelerationSettings.filterMarkingMode(stack)
                || RangeAccelerationSettings.placementMode(stack)) {
            ci.cancel();
            return;
        }

        // With acceleration disabled, preserve the upstream staff's one-shot rod interaction.
        if (!WondrousStaffAcceleration.isEnabled(stack)) return;

        // If our own highest-priority listener already handled this event, merely suppress the
        // upstream listener. Otherwise start/update the same world-owned acceleration marker here.
        if (!event.isCanceled()) {
            InteractionResult result = WondrousStaffAcceleration.tryUse(
                    new UseOnContext(event.getLevel(), event.getEntity(), event.getHand(), stack,
                            event.getHitVec()));
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        }
        ci.cancel();
    }
}
