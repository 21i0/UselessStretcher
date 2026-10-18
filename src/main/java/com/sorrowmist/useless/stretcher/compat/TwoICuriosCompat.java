package com.sorrowmist.useless.stretcher.compat;

import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.event.TwoICarriedCompanionship;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Loaded only when Curios is present. Validation never claims or modifies a simulated stack. */
public final class TwoICuriosCompat {
    private TwoICuriosCompat() { }

    public static void register() {
        CuriosApi.registerCurio(ModItems.TWO_I_DOLL.get(), new ICurioItem() {
            @Override
            public boolean canEquip(SlotContext context, ItemStack stack) {
                return mayWear(context, stack);
            }

            @Override
            public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
                // Keep right-click reserved for the doll's interaction panel/deployment.
                return false;
            }

            @Override
            public void onEquip(SlotContext context, ItemStack previous, ItemStack stack) {
                bindEquipped(context, stack);
            }

            @Override
            public void onEquipFromUse(SlotContext context, ItemStack stack) {
                bindEquipped(context, stack);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TwoICuriosCompat::allowAnySlot);
    }

    private static boolean mayWear(SlotContext context, ItemStack stack) {
        if (!(context.entity() instanceof Player player)) return false;
        var owner = stack.get(StretcherComponents.TWO_I_OWNER.get());
        return owner == null || owner.equals(player.getUUID());
    }

    private static void bindEquipped(SlotContext context, ItemStack stack) {
        if (context.entity() instanceof ServerPlayer player && mayWear(context, stack))
            TwoIDollItem.claimOrCheckOwner(stack, player);
    }

    private static void allowAnySlot(CurioCanEquipEvent event) {
        if (!event.getStack().is(ModItems.TWO_I_DOLL.get())) return;
        // Curios posts this after its slot validators, including custom non-tag validators.
        event.setEquipResult(mayWear(event.getSlotContext(), event.getStack()) ? TriState.TRUE : TriState.FALSE);
    }

    public static void sampleEquipped(ServerPlayer player, long time) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            for (var entry : inventory.getCurios().entrySet()) {
                var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (inventory.isSlotActive(entry.getKey(), i))
                        TwoICarriedCompanionship.sampleCarried(player, stacks.getStackInSlot(i), time);
                }
            }
        });
    }
}
