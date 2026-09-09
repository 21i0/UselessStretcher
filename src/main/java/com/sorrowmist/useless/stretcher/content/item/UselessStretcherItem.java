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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IInWorldGridNodeHost;

import java.util.List;
import java.util.UUID;

public final class UselessStretcherItem extends Item {
    public UselessStretcherItem(Properties properties) {
        super(properties);
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
                    int count = ref == null ? 0 : myriad.getPatterns().size();
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
            List<ItemStack> patterns = ref == null ? List.of() : store.flatten(ref);
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

    private static int deliverToHandler(RecoverableItemStackHandler handler, List<ItemStack> patterns) {
        int activeSlots = handler.getActiveSlots();
        int inserted = 0;
        for (ItemStack pattern : patterns) {
            for (int slot = 0; slot < activeSlots; slot++) {
                if (handler.getStackInSlot(slot).isEmpty() && handler.isItemValid(slot, pattern)) {
                    handler.setStackInSlot(slot, pattern.copy());
                    inserted++;
                    break;
                }
            }
        }
        return inserted;
    }

    private static int deliverToInventory(InternalInventory inventory, List<ItemStack> patterns) {
        int inserted = 0;
        for (ItemStack pattern : patterns) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                if (inventory.getStackInSlot(slot).isEmpty() && inventory.isItemValid(slot, pattern)) {
                    inventory.setItemDirect(slot, pattern.copy());
                    inserted++;
                    break;
                }
            }
        }
        return inserted;
    }
}
