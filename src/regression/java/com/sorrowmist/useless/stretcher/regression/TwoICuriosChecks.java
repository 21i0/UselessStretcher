package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TwoICompanionshipData;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.event.TwoICarriedCompanionship;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.event.CuriosEventHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import java.util.UUID;

/** Actual Curios slots, capability storage and equip callbacks, not a mocked compatibility API. */
public final class TwoICuriosChecks {
    private static final UUID OWNER = UUID.fromString("654f6479-13f5-426f-9817-d9103a8ba0cf");
    private static final UUID RESTART_DOLL = UUID.fromString("31c84040-51a1-4fab-95f0-6b3eec2d08b3");
    private TwoICuriosChecks() { }

    public static void run(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) {
            LogUtils.getLogger().info("2I CURIOS SKIPPED: optional mod absent");
            return;
        }
        var player = FakePlayerFactory.get(level, new GameProfile(OWNER, "TwoICurios"));
        player.setPos(59, 180, 59);
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
        check(inventory.getCurios().keySet().containsAll(java.util.List.of("back", "belt", "body", "bracelet",
                "charm", "hands", "head", "necklace", "ring", "two_i_custom")), "standard slots and custom slot registered");
        // Do not assign the generic curio type in entity data: its universal fallback would mask the custom deny validator.
        var slots = new java.util.LinkedHashMap<>(inventory.getCurios());
        slots.put("curio", new top.theillusivec4.curios.common.inventory.CurioStacksHandler(inventory, "curio"));
        inventory.setCurios(slots);
        long oldTime = level.getGameTime();
        try {
            ItemStack doll = new ItemStack(ModItems.TWO_I_DOLL.get());
            for (var entry : inventory.getCurios().entrySet()) {
                var handler = entry.getValue();
                for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                    var menuSlot = new CurioSlot(player, handler.getStacks(), i, entry.getKey(), 0, 0,
                            handler.getRenders(), handler.getActiveStates(), true, true, false);
                    check(menuSlot.mayPlace(doll) && handler.getStacks().isItemValid(i, doll), "real menu and handler accept " + entry.getKey() + i);
                    check(!doll.has(StretcherComponents.TWO_I_OWNER.get()) && !doll.has(StretcherComponents.TWO_I_DOLL_ID.get()),
                            "simulated validation does not bind/mutate an unequipped item");
                    check(handler.getCosmeticStacks().isItemValid(i, doll), "cosmetic validation accepts " + entry.getKey());
                }
            }
            var custom = inventory.getStacksHandler("two_i_custom").orElseThrow();
            var context = new SlotContext("two_i_custom", player, 0, false, true);
            check(!CuriosApi.isStackValid(context, doll), "restrictive custom validators really reject the raw doll");
            check(!custom.getStacks().isItemValid(0, new ItemStack(Items.DIAMOND)), "ordinary items remain rejected by restrictive custom slot");
            check(!CuriosApi.getCurio(doll).orElseThrow().canEquipFromUse(context), "right-click remains the doll panel, never Curios auto-equip");
            check(custom.getStacks().insertItem(0, doll, false).isEmpty(), "actual insert succeeds in restrictive slot");
            // Curios invokes inventoryTick and onEquip on the living-tick event.
            new CuriosEventHandler().tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(player));
            ItemStack equipped = custom.getStacks().getStackInSlot(0);
            check(OWNER.equals(equipped.get(StretcherComponents.TWO_I_OWNER.get())) && equipped.has(StretcherComponents.TWO_I_DOLL_ID.get()),
                    "real Curios tick claims first equip once");
            UUID id = equipped.get(StretcherComponents.TWO_I_DOLL_ID.get());
            equipped.set(StretcherComponents.TWO_I_BOND.get(), 37);
            equipped.set(StretcherComponents.TWO_I_MEMORY.get(), TwoIDollMemory.empty());
            var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoICurioOther"));
            var otherHandler = CuriosApi.getCuriosInventory(other).orElseThrow().getStacksHandler("two_i_custom").orElseThrow();
            check(!otherHandler.getStacks().isItemValid(0, equipped) && !otherHandler.getCosmeticStacks().isItemValid(0, equipped),
                    "both actual slot kinds reject another player's doll");
            ItemStack before = equipped.copy();
            custom.getRenders().set(0, false);
            for (int tick = 0; tick < 2000; tick++) { time(level, 240_000L + tick); tick(player); }
            check(ItemStack.isSameItemSameComponents(equipped, before), "2000 ticks of hidden accessory companionship cause zero item writes");
            var data = TwoICompanionshipData.get(level.getServer());
            check(data.progress(id, TwoIDollMemory.empty()).ticks() == 2000, "hidden display still earns only five-second samples");
            // Move the complete stack: accessory -> hands -> accessory, preserving UUID, memory and bond.
            equipped = custom.getStacks().extractItem(0, 1, false);
            check(custom.getStacks().getStackInSlot(0).isEmpty() && ItemStack.isSameItemSameComponents(equipped, before), "unequip preserves one complete doll without duplication");
            player.setItemSlot(EquipmentSlot.OFFHAND, equipped);
            for (int tick = 2000; tick < 4000; tick++) { time(level, 240_000L + tick); tick(player); }
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            check(custom.getStacks().insertItem(1, equipped, false).isEmpty(), "re-equip in a different accessory index");
            for (int tick = 4000; tick < 6000; tick++) { time(level, 240_000L + tick); tick(player); tick(player); }
            check(TwoIDollItem.getBond(equipped) == 38 && data.progress(id, TwoIDollMemory.empty()).ticks() == 0,
                    "6000 combined ticks award exactly once even with duplicate events");
            custom.getActiveStates().set(1, false);
            for (int tick = 6000; tick < 7000; tick++) { time(level, 240_000L + tick); tick(player); }
            check(data.progress(id, TwoIDollMemory.empty()).ticks() == 0, "inactive slots do not earn companionship");
            custom.getActiveStates().set(1, true);
            ItemStack cosmetic = new ItemStack(ModItems.TWO_I_DOLL.get());
            TwoIDollItem.claimOrCheckOwner(cosmetic, player);
            custom.getCosmeticStacks().setStackInSlot(0, cosmetic);
            var cosmeticBefore = cosmetic.copy();
            for (int tick = 7000; tick < 7100; tick++) { time(level, 240_000L + tick); tick(player); }
            check(ItemStack.isSameItemSameComponents(cosmetic, cosmeticBefore), "cosmetic-only slots give visual appearance without functional companion reward");

            var saved = inventory.saveInventory(false);
            custom.getStacks().setStackInSlot(1, ItemStack.EMPTY);
            inventory.loadInventory(saved);
            ItemStack restored = inventory.getStacksHandler("two_i_custom").orElseThrow().getStacks().getStackInSlot(1);
            check(ItemStack.isSameItemSameComponents(restored, equipped), "actual Curios inventory save/load preserves complete doll and identity");
            var decoded = TwoICompanionshipData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(decoded.progress(id, TwoIDollMemory.empty()).equals(data.progress(id, TwoIDollMemory.empty())), "world timer save/load matches Curios companion progress");

            java.nio.file.Path restartFile = java.nio.file.Path.of("curios-doll-restart.nbt");
            if (java.nio.file.Files.exists(restartFile)) {
                var nbt = net.minecraft.nbt.NbtIo.read(restartFile);
                inventory.loadInventory(nbt.getList("Inventory", 10));
                ItemStack previous = inventory.getStacksHandler("two_i_custom").orElseThrow().getStacks().getStackInSlot(0);
                check(RESTART_DOLL.equals(previous.get(StretcherComponents.TWO_I_DOLL_ID.get()))
                                && OWNER.equals(previous.get(StretcherComponents.TWO_I_OWNER.get())) && TwoIDollItem.getBond(previous) == 42,
                        "prior-process Curios item inventory retained ownership/identity/bond");
                check(data.progress(RESTART_DOLL, TwoIDollMemory.empty()).ticks() == 2300,
                        "prior-process world timer retains accessory progress");
                LogUtils.getLogger().info("2I CURIOS RESTART PASSED: real prior-process Curios inventory and world companionship timer restored");
            }
            clear(inventory);
            ItemStack restart = new ItemStack(ModItems.TWO_I_DOLL.get());
            restart.set(StretcherComponents.TWO_I_OWNER.get(), OWNER);
            restart.set(StretcherComponents.TWO_I_DOLL_ID.get(), RESTART_DOLL);
            restart.set(StretcherComponents.TWO_I_BOND.get(), 42);
            inventory.getStacksHandler("two_i_custom").orElseThrow().getStacks().setStackInSlot(0, restart);
            var fixture = new TwoIDollMemory(TwoIDollMemory.empty().cooldowns(),
                    new TwoIDollMemory.Interaction(10, 0, 0, 0, 0, 2300, -1, "", 0, 0), 240_000);
            data.progress(RESTART_DOLL, fixture);
            var nbt = new CompoundTag(); nbt.put("Inventory", inventory.saveInventory(false));
            net.minecraft.nbt.NbtIo.write(nbt, restartFile);
            LogUtils.getLogger().info("2I CURIOS PASSED: ten native slot types/custom deny-validator/menu validation/cosmetic/owner; actual equip, move/save/load; 2000 ticks zero item changes, 6000 combined ticks +1, hidden/inactive and duplicate samples");
        } finally {
            clear(inventory);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            time(level, oldTime);
        }
    }

    private static void clear(top.theillusivec4.curios.api.type.capability.ICuriosItemHandler inventory) {
        inventory.getCurios().values().forEach(handler -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                handler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        });
    }
    private static void time(ServerLevel level, long time) { level.getServer().getWorldData().overworldData().setGameTime(time); }
    private static void tick(ServerPlayer player) { TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player)); }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError("2i Curios: " + message); }
}
