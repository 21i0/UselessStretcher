package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.client.StretcherKeyBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

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

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, level, entity, slotId, selected);
        if (!(entity instanceof ServerPlayer player)
                || (!selected && player.getOffhandItem() != stack)
                || !StaffTutorialData.get(player.getServer()).markHintShown(player.getUUID(),
                StaffTutorialData.HINT_FIRST_HELD)) {
            return;
        }
        com.sorrowmist.useless.stretcher.network.Network.sendStaffTutorial(player);
    }

    /** Override the inherited name so it never displays as the 造化垂青之杖. */
    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getName(ItemStack stack) {
        return Component.translatable("item.useless_stretcher.wondrous_staff");
    }

    /** Exposes the upstream tool and shears abilities while acceleration interactions stay explicit. */
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (RangeAccelerationSettings.filterMarkingMode(stack)) {
            return false;
        }
        // Keep the same mining/tool abilities as the upstream staff.  The acceleration
        // interaction is intercepted by onItemUseFirst/useOn, so exposing these abilities
        // is safe and lets Apotheosis and the newer tool-mode UI classify the staff correctly.
        if (ItemAbilities.DEFAULT_PICKAXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability)
                || ItemAbilities.DEFAULT_BRUSH_ACTIONS.contains(ability)
                || (ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(ability)
                        && staffShearsEnabled(stack))
                || (ItemAbilities.DEFAULT_FLINT_ACTIONS.contains(ability)
                        && staffFlintAndSteelEnabled(stack))
                || (ability == ItemAbilities.FIRESTARTER_LIGHT
                        && staffFlintAndSteelEnabled(stack))
                || ability == ItemAbilities.SWORD_SWEEP) {
            return true;
        }
        if (ability == ItemAbilities.SWORD_DIG) {
            return false;
        }
        // The upstream base item exposes mode-specific abilities. Keep the staff in NONE_MODE,
        // but never leak wrench abilities when the G-menu wrench tag is disabled.
        if (isWrenchAbility(ability) && !BeefToolVariants.isWrenchTagEnabled(stack)) {
            return false;
        }
        return super.canPerformAction(stack, ability);
    }

    private static boolean isWrenchAbility(ItemAbility ability) {
        String name = ability.name();
        return name.startsWith("wrench_") || name.equals("wrench");
    }

    /** The extra switches only exist in newer Useless Mod builds; old builds keep legacy defaults. */
    private static boolean staffShearsEnabled(ItemStack stack) {
        try {
            return EndlessBeafItem.isShearsEnabled(stack);
        } catch (NoSuchMethodError ignored) {
            return true;
        }
    }

    private static boolean staffFlintAndSteelEnabled(ItemStack stack) {
        try {
            return EndlessBeafItem.isFlintAndSteelEnabled(stack);
        } catch (NoSuchMethodError ignored) {
            return true;
        }
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
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_mode",
                        StretcherKeyBindings.WONDROUS_STAFF_MODE.getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_range")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_summon")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.wondrous_staff.hint_loot_refresh")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        if (RangeAccelerationSettings.filterMarkingMode(stack)) return InteractionResult.FAIL;
        Player player = ctx.getPlayer();
        if (isEnabledLightningRodTarget(stack, ctx)) {
            return WondrousStaffAcceleration.tryUse(ctx);
        }
        if (player != null && player.isShiftKeyDown()
                && StretcherConfig.enableStaffLootRefresh()
                && WondrousStaffAcceleration.isLootRefreshEnabled(stack)) {
            InteractionResult refresh = WondrousStaffLootRefresh.tryRefreshBlock(player, ctx.getClickedPos());
            if (refresh != InteractionResult.PASS) return refresh;
        }
        if (player != null && player.isShiftKeyDown() && WondrousStaffAcceleration.isEnabled(stack)) {
            if (RangeAccelerationSettings.placementMode(stack)) return InteractionResult.FAIL;
            // Acceleration mode ON: disable every other right-click (wrench/tool/block menu)
            // so Shift+right-click can only accelerate, exactly like the base staff.
            WondrousStaffAcceleration.tryUse(ctx);
            return InteractionResult.FAIL;
        }
        return super.onItemUseFirst(stack, ctx);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (RangeAccelerationSettings.filterMarkingMode(ctx.getItemInHand())) return InteractionResult.FAIL;
        Player player = ctx.getPlayer();
        if (isEnabledLightningRodTarget(ctx.getItemInHand(), ctx)) {
            return WondrousStaffAcceleration.tryUse(ctx);
        }
        if (player != null && player.isShiftKeyDown()
                && StretcherConfig.enableStaffLootRefresh()
                && WondrousStaffAcceleration.isLootRefreshEnabled(ctx.getItemInHand())) {
            InteractionResult refresh = WondrousStaffLootRefresh.tryRefreshBlock(player, ctx.getClickedPos());
            if (refresh != InteractionResult.PASS) return refresh;
        }
        if (player != null && player.isShiftKeyDown() && WondrousStaffAcceleration.isEnabled(ctx.getItemInHand())) {
            if (RangeAccelerationSettings.placementMode(ctx.getItemInHand())) return InteractionResult.FAIL;
            WondrousStaffAcceleration.tryUse(ctx);
            return InteractionResult.FAIL;
        }
        return super.useOn(ctx);
    }

    /**
     * The upstream item handles lightning rods before its normal acceleration hook.  Keep a
     * direct item-level guard as a fallback for interaction paths that do not dispatch the
     * NeoForge block event (and for servers with a different event-handler ordering).
     */
    private static boolean isEnabledLightningRodTarget(ItemStack stack, UseOnContext ctx) {
        return WondrousStaffAcceleration.isEnabled(stack)
                && !RangeAccelerationSettings.placementMode(stack)
                && ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof LightningRodBlock;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
                                                  InteractionHand hand) {
        if (RangeAccelerationSettings.filterMarkingMode(stack)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown() && !(entity instanceof Player)
                && !entity.isRemoved()) {
            InteractionResult result = WondrousStaffAcceleration.tryUseEntity(player, entity, stack);
            if (result != InteractionResult.PASS) return result;
        }
        return super.interactLivingEntity(stack, player, entity, hand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (RangeAccelerationSettings.filterMarkingMode(player.getItemInHand(hand))) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        if (player.isShiftKeyDown() && WondrousStaffAcceleration.isLookingAtCelestial(level, player)) {
            InteractionResult result = WondrousStaffAcceleration.tryUseTime(player, player.getItemInHand(hand));
            if (result != InteractionResult.PASS) {
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
            }
        }
        return super.use(level, player, hand);
    }
}
