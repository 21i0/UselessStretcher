package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sorrowmist.useless.stretcher.content.item.StaffMiningContext;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MiningDispatcher.class)
public abstract class StaffMiningDispatcherMixin {
    @WrapMethod(method = "dispatchBreak(Lnet/neoforged/neoforge/event/level/BlockEvent$BreakEvent;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V")
    private static void uselessStretcher$mine(BlockEvent.BreakEvent event, ItemStack stack, Player player,
                                              Operation<Void> original) {
        if (stack.is(ModItems.WONDROUS_STAFF.get())) {
            StaffMiningContext.run(() -> original.call(event, stack, player));
        } else original.call(event, stack, player);
    }

    @WrapMethod(method = "dispatchForceBreak(Lnet/minecraft/world/entity/player/Player;Z)V")
    private static void uselessStretcher$force(Player player, boolean chain, Operation<Void> original) {
        if (player.getMainHandItem().is(ModItems.WONDROUS_STAFF.get())) {
            StaffMiningContext.run(() -> original.call(player, chain));
        } else original.call(player, chain);
    }
}
