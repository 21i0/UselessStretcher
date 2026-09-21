package com.sorrowmist.useless.stretcher.menu;

import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class OmniversalMyriadMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private Set<ResourceLocation> pendingSelection;
    private int pendingAction;
    private int lastSelectionVersion = Integer.MIN_VALUE;

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

    /** No partially received selection is ever applied to the block or its dropped item. */
    public Set<ResourceLocation> receiveSelection(String phase, List<String> values) {
        return receiveSelection(0, phase, values);
    }

    public Set<ResourceLocation> receiveSelection(int action, String phase, List<String> values) {
        if ("single".equals(phase) || "begin".equals(phase)) {
            pendingSelection = new LinkedHashSet<>();
            pendingAction = action;
        }
        if (pendingSelection == null || pendingAction != action) return null;
        for (String value : values) {
            var id = ResourceLocation.tryParse(value);
            if (id != null) pendingSelection.add(id);
        }
        if (!"single".equals(phase) && !"end".equals(phase)) return null;
        var result = pendingSelection;
        pendingSelection = null;
        return result;
    }

    public boolean needsSelections(int version) {
        if (lastSelectionVersion == version) return false;
        lastSelectionVersion = version;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getCenter()) <= 64.0
                && player.level().getBlockEntity(pos) instanceof
                com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
    }
}
