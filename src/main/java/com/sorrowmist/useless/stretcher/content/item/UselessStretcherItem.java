package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.stretcher.content.ae.AeBindingStore;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IInWorldGridNodeHost;

import java.util.List;
import java.util.UUID;
import appeng.api.stacks.AEItemKey;
import java.util.Collection;
import java.util.LinkedList;

public final class UselessStretcherItem extends Item {
    public UselessStretcherItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.hint_ae")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.hint_myriad")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.hint_send")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.hint_copy")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.stretcher.hint_full")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleUse(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleUse(context);
    }

    private InteractionResult handleUse(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stretcher = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;

        boolean sneaking = player.isShiftKeyDown();

        if (sneaking) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OmniversalMyriadBlockEntity myriad) {
                if (!level.isClientSide) {
                    UUID ref = myriad.getPatternRef();
                    MyriadMoldData.writePatternRef(stretcher, ref);
                    UUID aeRef = MyriadMoldData.readAeRef(stretcher);
                    if (aeRef != null) myriad.setAeRef(aeRef);
                    int count = ref == null ? 0 : myriad.getPatternCount();
                    player.displayClientMessage(
                            Component.translatable("msg.useless_stretcher.bound", count), true);
                }
                return InteractionResult.SUCCESS;
            }
            if (be instanceof IInWorldGridNodeHost) {
                if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                    UUID ref = AeBindingStore.get(serverLevel).put(pos);
                    MyriadMoldData.writeAeRef(stretcher, ref);
                    player.displayClientMessage(
                            Component.translatable("msg.useless_stretcher.ae_bound"), true);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        boolean validTarget = be instanceof MePatternAssemblyBlockEntity
                || be instanceof PassiveCraftingHatchBlockEntity
                || be instanceof AdvancedAlloyFurnaceBlockEntity;
        if (!validTarget) return InteractionResult.PASS;

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            MyriadPatternStore store = MyriadPatternStore.get(serverLevel);
            UUID ref = MyriadMoldData.readPatternRef(stretcher);
            Collection<AEItemKey> patterns = ref == null ? List.of() : store.keys(ref);
            if (patterns.isEmpty()) {
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.no_patterns"), true);
                return InteractionResult.SUCCESS;
            }

            int inserted;
            if (be instanceof MePatternAssemblyBlockEntity assembly) {
                inserted = deliverToHandler(assembly.getPatterns(), patterns);
            } else if (be instanceof PassiveCraftingHatchBlockEntity hatch) {
                inserted = deliverToHandler(hatch.getPatterns(), patterns);
            } else if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                inserted = deliverToInventory(furnace.getTerminalPatternInventory(), patterns);
            } else {
                return InteractionResult.PASS;
            }

            if (inserted >= patterns.size()) {
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.sent", inserted), true);
            } else if (player instanceof ServerPlayer serverPlayer) {
                Network.sendFullSlots(serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static int deliverToHandler(RecoverableItemStackHandler handler, Collection<AEItemKey> patterns) {
        int activeSlots = handler.getActiveSlots();
        int inserted = 0;
        var emptySlots = new LinkedList<Integer>();
        for (int slot = 0; slot < activeSlots; slot++) {
            if (handler.getStackInSlot(slot).isEmpty()) emptySlots.add(slot);
        }
        for (AEItemKey key : patterns) {
            if (emptySlots.isEmpty()) break;
            ItemStack pattern = key.toStack(1);
            var slots = emptySlots.iterator();
            while (slots.hasNext()) {
                int slot = slots.next();
                if (handler.isItemValid(slot, pattern)) {
                    handler.setStackInSlot(slot, pattern);
                    slots.remove();
                    inserted++;
                    break;
                }
            }
        }
        return inserted;
    }

    private static int deliverToInventory(InternalInventory inventory, Collection<AEItemKey> patterns) {
        int inserted = 0;
        var emptySlots = new LinkedList<Integer>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) emptySlots.add(slot);
        }
        for (AEItemKey key : patterns) {
            if (emptySlots.isEmpty()) break;
            ItemStack pattern = key.toStack(1);
            var slots = emptySlots.iterator();
            while (slots.hasNext()) {
                int slot = slots.next();
                if (inventory.isItemValid(slot, pattern)) {
                    inventory.setItemDirect(slot, pattern);
                    slots.remove();
                    inserted++;
                    break;
                }
            }
        }
        return inserted;
    }
}
