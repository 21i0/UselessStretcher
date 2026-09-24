package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;

import java.util.List;

/** Administrative tool for removing any placed time-flow field. */
public final class RangeReclaimerItem extends Item {
    public RangeReclaimerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static InteractionResult tryReclaim(Player player, ItemStack stack, TimeFlowEntity marker) {
        if (stack.getItem() != ModItems.RANGE_RECLAIMER.get()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        RangeAccelerationSavedData.Summary reclaimed = RangeAccelerationSavedData
                .get(serverPlayer.getServer()).reclaimByOperator(serverPlayer.getServer(), marker.getUUID());
        if (reclaimed == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("msg.useless_stretcher.range_reclaimer.missing"), true);
        } else {
            serverPlayer.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.range_reclaimer.reclaimed",
                    reclaimed.center().getX(), reclaimed.center().getY(), reclaimed.center().getZ()), true);
        }
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
        tooltip.add(Component.translatable("tooltip.useless_stretcher.range_reclaimer.hint_recipe")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
