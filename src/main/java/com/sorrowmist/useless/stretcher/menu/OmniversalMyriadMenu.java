package com.sorrowmist.useless.stretcher.menu;

import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class OmniversalMyriadMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public OmniversalMyriadMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public OmniversalMyriadMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.OMNIVERSAL_MYRIAD.get(), containerId);
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
