package com.sorrowmist.useless.stretcher.regression;

import com.sorrowmist.useless.stretcher.content.blockentity.TwoIDollDisplayBlockEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Registered-world round trips: all item components and bounded relationship memory survive each form. */
public final class TwoIBondMemoryChecks {
    private TwoIBondMemoryChecks() {}

    public static void run(ServerLevel level) {
        var memory = new TwoIDollMemory(new TwoIDollMemory.Cooldowns(27, 1190, 190, 4790,
                23999, 5999, 11, true), new TwoIDollMemory.Interaction(17L, 2, 2, 3,
                2, 5900, 41, "minecraft:cookie", 4, 1));
        ItemStack original = new ItemStack(ModItems.TWO_I_DOLL.get());
        com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.ensureIdentity(original);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_BOND.get(), -42);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_INVITATION_USED.get(), true);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_MEMORY.get(), memory);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("A named friend"));
        original.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "entity creates");
        doll.restoreFromItem(original);
        check(ItemStack.isSameItemSameComponents(original, doll.toDollItem()), "item to entity to item");
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_BOND.get(), 100);
        check(doll.getGiftScore() == -42, "entity owns a copy of its complete stack");

        CompoundTag saved = doll.saveWithoutId(new CompoundTag());
        TwoIDollEntity loaded = ModEntities.TWO_I_DOLL.get().create(level);
        check(loaded != null, "restored entity creates");
        loaded.load(saved);
        ItemStack returned = loaded.toDollItem();
        check(memory.equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(returned, StretcherComponents.TWO_I_MEMORY.get())), "entity codec keeps cooldowns and daily budgets");
        check(returned.getHoverName().getString().equals("A named friend"), "entity keeps custom name");
        check(loaded.canInvite(), "already invited friend can revisit with negative bond");

        var state = ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState();
        var display = new TwoIDollDisplayBlockEntity(BlockPos.ZERO, state);
        display.setDoll(returned);
        var loadedDisplay = BlockEntity.loadStatic(BlockPos.ZERO, state,
                display.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        check(loadedDisplay instanceof TwoIDollDisplayBlockEntity, "display round trip");
        loaded.restoreFromItem(((TwoIDollDisplayBlockEntity) loadedDisplay).copyDoll());
        check(ItemStack.isSameItemSameComponents(returned, loaded.toDollItem()),
                "entity to item to display to item to entity keeps all components");

        CompoundTag legacy = new CompoundTag();
        legacy.putInt("gift_score", 7);
        legacy.putBoolean("invitation_used", true);
        loaded.readAdditionalSaveData(legacy);
        check(loaded.getGiftScore() == 70 && loaded.isInvitationUsed(), "old gift score migrates without losing invitation");
    }

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError("2i relationship memory: " + name);
    }
}
