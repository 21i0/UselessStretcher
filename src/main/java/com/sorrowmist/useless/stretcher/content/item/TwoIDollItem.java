package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** The author's 2i mascot, with relationship and ownership preserved in every form. */
public final class TwoIDollItem extends BlockItem {
    public TwoIDollItem() {
        super(com.sorrowmist.useless.stretcher.init.ModBlocks.TWO_I_DOLL_DISPLAY.get(),
                new Properties().stacksTo(1));
    }

    @Override
    public String getDescriptionId() {
        return "item.useless_stretcher.two_i_doll";
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Updates to this doll keep the hand still; exchanging two dolls must still animate.
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack)
                || !java.util.Objects.equals(oldStack.get(StretcherComponents.TWO_I_DOLL_ID.get()),
                        newStack.get(StretcherComponents.TWO_I_DOLL_ID.get()));
    }

    /** Ordinary inventory/shift-click equipment rules retain the complete original stack. */
    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity wearer) {
        return slot == EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!claimOrCheckOwner(stack, player)) return InteractionResultHolder.fail(stack);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                deploy(serverPlayer, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.sorrowmist.useless.stretcher.network.Network.sendTwoIItemDialogue(serverPlayer, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static boolean deploy(net.minecraft.server.level.ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.TWO_I_DOLL.get()) || !claimOrCheckOwner(stack, player)) return false;
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(player.serverLevel());
        if (doll == null) return false;
        var look = player.getLookAngle();
        doll.moveTo(player.getX() + look.x * 0.9D, player.getY(),
                player.getZ() + look.z * 0.9D, player.getYRot(), 0.0F);
        doll.setOwnerId(player.getUUID());
        doll.restoreFromItem(stack);
        if (!player.serverLevel().addFreshEntity(doll)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }

    /** Legacy unowned items bind once on the server; subsequent holders cannot take ownership. */
    public static boolean claimOrCheckOwner(ItemStack stack, Player player) {
        if (!player.level().isClientSide) ensureIdentity(stack);
        java.util.UUID owner = stack.get(StretcherComponents.TWO_I_OWNER.get());
        if (owner == null) {
            if (!player.level().isClientSide) stack.set(StretcherComponents.TWO_I_OWNER.get(), player.getUUID());
            return true;
        }
        if (owner.equals(player.getUUID())) return true;
        if (!player.level().isClientSide)
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.owner_only"), true);
        return false;
    }

    public static java.util.UUID ensureIdentity(ItemStack stack) {
        java.util.UUID id = stack.get(StretcherComponents.TWO_I_DOLL_ID.get());
        if (id == null) {
            id = java.util.UUID.randomUUID();
            stack.set(StretcherComponents.TWO_I_DOLL_ID.get(), id);
        }
        return id;
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getPlayer() != null && !claimOrCheckOwner(context.getItemInHand(), context.getPlayer()))
            return InteractionResult.FAIL;
        return super.useOn(context);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity holder,
                              int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        if (!level.isClientSide && holder instanceof Player player) {
            boolean newlyReceived = !stack.has(StretcherComponents.TWO_I_DOLL_ID.get())
                    || !stack.has(StretcherComponents.TWO_I_OWNER.get());
            ensureIdentity(stack);
            if (!stack.has(StretcherComponents.TWO_I_OWNER.get()))
                stack.set(StretcherComponents.TWO_I_OWNER.get(), player.getUUID());
            if (newlyReceived && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                TwoIDollAcquisition.notifyAcquired(serverPlayer, stack);
        }
    }

    public static int getBond(ItemStack stack) {
        if (stack.has(StretcherComponents.TWO_I_BOND.get())) {
            return Math.max(-100, Math.min(100, stack.getOrDefault(StretcherComponents.TWO_I_BOND.get(), 0)));
        }
        int legacyScore = stack.getOrDefault(StretcherComponents.TWO_I_GIFT_SCORE.get(), 0);
        return Math.max(0, Math.min(100, legacyScore * 10));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (target instanceof TwoIDollEntity doll) {
            if (!player.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                doll.openDialogue(serverPlayer);
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.two_i_doll.flavor")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.two_i_doll.use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.two_i_doll.headwear")
                .withStyle(ChatFormatting.GRAY));
        if (net.neoforged.fml.ModList.get().isLoaded("curios"))
            tooltip.add(Component.translatable("tooltip.useless_stretcher.two_i_doll.curios")
                    .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.two_i_doll.remote")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
