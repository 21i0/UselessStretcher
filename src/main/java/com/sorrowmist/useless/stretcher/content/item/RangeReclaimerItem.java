package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;

import java.util.List;

/** Opens the server-authoritative reclaimer UI; using the item never deletes a target. */
public final class RangeReclaimerItem extends Item {
    public RangeReclaimerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return new InteractionResultHolder<>(tryOpen(player, stack), stack);
    }

    public static InteractionResult tryOpen(Player player, ItemStack stack) {
        if (!player.isShiftKeyDown() || !stack.is(ModItems.RANGE_RECLAIMER.get())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (StretcherConfig.serverRemoteReclaimer()) {
                Network.requestReclaimerState(serverPlayer);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(
                        "msg.useless_stretcher.reclaimer.disabled"), true);
            }
        }
        // Consume the same gesture on both sides; only the server sends the screen's state.
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_owner")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_remove")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_remote")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_recipe")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
