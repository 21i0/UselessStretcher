package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * 荒辰移晷之杖：继承造化垂青之杖的全部功能，并额外支持 AE 刻加速（shift+右键 AE 机器）、
 * 时间/实体加速等扩展。AE 机器走自己的 AE 刻，因此这里不走普通方块 ticker，而是直接驱动
 * AE 节点的 {@code IGridTickable}。
 */
public class WondrousStaffItem extends EndlessBeafItem {

    public WondrousStaffItem() {
        super(ToolTypeMode.NONE_MODE, true);
    }

    /** Override the inherited name so it never displays as the 造化垂青之杖. */
    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getName(ItemStack stack) {
        return Component.translatable("item.useless_stretcher.wondrous_staff");
    }

    /**
     * While time acceleration is enabled, the staff is never a wrench (or any other tool):
     * acceleration takes priority over dismantling / rotating machines.
     */
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (WondrousStaffAcceleration.isEnabled(stack) && ability.name().startsWith("wrench_")) {
            return false;
        }
        return super.canPerformAction(stack, ability);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_ae")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_animal")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_time")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_speed")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown() && WondrousStaffAcceleration.isEnabled(stack)) {
            // Acceleration mode ON: disable every other right-click (wrench/tool/block menu)
            // so Shift+right-click can only accelerate, exactly like the base staff.
            WondrousStaffAcceleration.tryUse(ctx);
            return InteractionResult.FAIL;
        }
        return super.onItemUseFirst(stack, ctx);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player != null && player.isShiftKeyDown() && WondrousStaffAcceleration.isEnabled(ctx.getItemInHand())) {
            WondrousStaffAcceleration.tryUse(ctx);
            return InteractionResult.FAIL;
        }
        return super.useOn(ctx);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
                                                  InteractionHand hand) {
        if (player.isShiftKeyDown() && !(entity instanceof Player)
                && entity instanceof AgeableMob) {
            InteractionResult result = WondrousStaffAcceleration.tryUseEntity(player, entity);
            if (result != InteractionResult.PASS) return result;
        }
        return super.interactLivingEntity(stack, player, entity, hand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && WondrousStaffAcceleration.isLookingAtCelestial(level, player)) {
            InteractionResult result = WondrousStaffAcceleration.tryUseTime(player);
            if (result != InteractionResult.PASS) {
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
            }
        }
        return super.use(level, player, hand);
    }
}
