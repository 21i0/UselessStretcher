package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleService;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A portable, server-authoritative catalogue and entrance for saved structure templates. */
public final class DimensionCapsuleItem extends Item {
    public DimensionCapsuleItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (DimensionCapsuleService.hasReturnPoint(serverPlayer)) DimensionCapsuleService.enter(serverPlayer, stack);
            else Network.sendDimensionCapsuleCatalog(serverPlayer, hand == InteractionHand.OFF_HAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean is(ItemStack stack) { return stack.is(ModItems.DIMENSION_CAPSULE.get()); }

    public static void enter(ServerPlayer player, InteractionHand hand) {
        DimensionCapsuleService.enter(player, player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.dimension_capsule.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.dimension_capsule.use")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
