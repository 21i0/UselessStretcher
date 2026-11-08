package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.crafting.PatternDetailsHelper;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native menu/slot/handler and vanilla stacks; only item identities and absent world services are substituted. */
class InstalledStretcherPlacementGuardTest {
    @BeforeAll static void initializeVanillaOnly() { VanillaTestRegistries.initialize(); }

    @Test void occupiedAssemblyRejectsEncodedPatternBeforeMenuCanExchangeIt() {
        try (var fixture = new Fixture(true)) {
            assertTrue(fixture.nativeSlot.mayPlace(fixture.pattern), "reproduce native slot bypass of handler validity");
            assertEquals(Boolean.FALSE, InstalledStretcherPlacementGuard.mayPlace(fixture.nativeSlot, fixture.pattern));
            assertFalse(fixture.guardedSlot.mayPlace(fixture.pattern));
            fixture.verifyNoSnapshot();
        }
    }

    @Test void occupiedAssemblyRejectsOtherAndSameUuidStretchers() {
        try (var fixture = new Fixture(true)) {
            ItemStack other = fixture.snapshot(UUID.randomUUID());
            assertFalse(fixture.guardedSlot.mayPlace(other));
            assertFalse(fixture.guardedSlot.mayPlace(fixture.installed.copy()));
            fixture.verifyNoSnapshot();
        }
    }

    @Test void emptyAssemblyLeavesEncodedPatternToNativeAcceptance() {
        try (var fixture = new Fixture(false)) {
            assertNull(InstalledStretcherPlacementGuard.mayPlace(fixture.nativeSlot, fixture.pattern));
            assertTrue(fixture.guardedSlot.mayPlace(fixture.pattern));
            assertFalse(fixture.guardedSlot.mayPlace(new ItemStack(Items.DIRT)));
            fixture.verifyNoSnapshot();
        }
    }

    @Test void emptyAssemblyAcceptsBoundStretcherUsingExistingHandlerValidation() {
        try (var fixture = new Fixture(false)) {
            assertTrue(fixture.guardedSlot.mayPlace(fixture.installed));
            assertFalse(fixture.guardedSlot.mayPlace(new ItemStack(Items.PAPER)), "unbound stretchers stay rejected");
            fixture.verifyNoSnapshot();
        }
    }

    @Test void nonAssemblyOccupiedSlotKeepsNativePatternAndExistingStretcherRules() {
        try (var fixture = new Fixture(false)) {
            var ordinary = new ItemStackHandler(1);
            ordinary.setStackInSlot(0, fixture.installed.copy());
            var slot = new GuardedPatternSlot(ordinary);
            assertNull(InstalledStretcherPlacementGuard.mayPlace(slot, fixture.pattern));
            assertTrue(slot.mayPlace(fixture.pattern));
            assertTrue(slot.mayPlace(fixture.installed));
            fixture.verifyNoSnapshot();
        }
    }

    @Test void emptyOfferAndExtractionStayAvailableWithoutForking() {
        try (var fixture = new Fixture(true)) {
            assertNull(InstalledStretcherPlacementGuard.mayPlace(fixture.nativeSlot, ItemStack.EMPTY));
            assertTrue(fixture.guardedSlot.mayPickup(fixture.player));
            ItemStack extracted = fixture.handler.extractItem(0, 1, false);
            assertEquals(fixture.uuid, MyriadMoldData.readPatternRef(extracted));
            assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
            fixture.verifyNoSnapshot();
        }
    }

    @Test void nativePickupSwapWithGuardConservesBothItemsAndLockedUuid() {
        try (var fixture = new Fixture(true)) {
            var menu = new TestMenu(fixture.guardedSlot);
            menu.setCarried(fixture.pattern.copy());
            menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            assertEquals(fixture.uuid, MyriadMoldData.readPatternRef(fixture.handler.getStackInSlot(0)));
            assertTrue(menu.getCarried().is(Items.DIAMOND));
            assertEquals(1, menu.getCarried().getCount());
            assertEquals(1, fixture.handler.getStackInSlot(0).getCount());
            assertTrue(fixture.inventory.isEmpty());
            fixture.verifyNoSnapshot();
        }
    }

    @Test void nativeHotbarSwapWithGuardConservesBothItemsAndLockedUuid() {
        try (var fixture = new Fixture(true)) {
            var menu = new TestMenu(fixture.guardedSlot);
            fixture.inventory.setItem(0, fixture.pattern.copy());
            menu.clicked(0, 0, ClickType.SWAP, fixture.player);
            assertEquals(fixture.uuid, MyriadMoldData.readPatternRef(fixture.handler.getStackInSlot(0)));
            assertTrue(fixture.inventory.getItem(0).is(Items.DIAMOND));
            assertEquals(1, fixture.inventory.getItem(0).getCount());
            assertEquals(1, fixture.handler.getStackInSlot(0).getCount());
            assertTrue(menu.getCarried().isEmpty());
            fixture.verifyNoSnapshot();
        }
    }

    @Test void nativePickupCanExtractInstalledStretcherWithEmptyCursor() {
        try (var fixture = new Fixture(true)) {
            var menu = new TestMenu(fixture.guardedSlot);
            menu.clicked(0, 0, ClickType.PICKUP, fixture.player);
            assertTrue(fixture.handler.getStackInSlot(0).isEmpty());
            assertEquals(fixture.uuid, MyriadMoldData.readPatternRef(menu.getCarried()));
            assertEquals(1, menu.getCarried().getCount());
            fixture.verifyNoSnapshot();
        }
    }

    private static final class GuardedPatternSlot extends PatternSlotItemHandler {
        GuardedPatternSlot(IItemHandler inventory) { super(inventory, 0, 0, 0); }
        @Override public boolean mayPlace(ItemStack offered) {
            Boolean decision = InstalledStretcherPlacementGuard.mayPlace(this, offered);
            return decision != null ? decision : super.mayPlace(offered);
        }
    }

    private static final class TestMenu extends AbstractContainerMenu {
        TestMenu(SlotItemHandler slot) { super(null, 0); addSlot(slot); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<InstalledStretcherPatterns> identities = mockStatic(InstalledStretcherPatterns.class);
        final MockedStatic<PatternDetailsHelper> encoded = mockStatic(PatternDetailsHelper.class);
        final MockedStatic<CommonHooks> itemHooks = mockStatic(CommonHooks.class);
        final UUID uuid = UUID.randomUUID();
        final BlockEntity owner = mock(BlockEntity.class);
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final Player player = mock(Player.class);
        final Inventory inventory = new Inventory(player);
        final ItemStack installed = snapshot(uuid);
        final ItemStack pattern = new ItemStack(Items.DIAMOND);
        final AssemblyStretcherInventory handler;
        final PatternSlotItemHandler nativeSlot;
        final GuardedPatternSlot guardedSlot;

        Fixture(boolean occupied) {
            identities.when(() -> InstalledStretcherPatterns.isStretcher(any(ItemStack.class)))
                    .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.PAPER));
            identities.when(() -> InstalledStretcherPatterns.isAssemblySnapshot(any(ItemStack.class)))
                    .thenCallRealMethod();
            identities.when(() -> InstalledStretcherPatterns.canSnapshotForAssembly(any(ServerLevel.class), any(ItemStack.class)))
                    .thenReturn(true);
            identities.when(() -> InstalledStretcherPatterns.snapshotForAssembly(any(ServerLevel.class), any(ItemStack.class)))
                    .thenThrow(new AssertionError("placement validation must never create a snapshot"));
            encoded.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                    .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.DIAMOND));
            handler = new AssemblyStretcherInventory(() -> 27, ignored -> true, () -> {}, owner);
            if (occupied) handler.setStackInSlot(0, installed.copy()); // pre-level NBT restoration
            when(owner.getLevel()).thenReturn(serverLevel);
            nativeSlot = new PatternSlotItemHandler(handler, 0, 0, 0);
            guardedSlot = new GuardedPatternSlot(handler);
            Level clientlessLevel = mock(Level.class);
            when(clientlessLevel.enabledFeatures()).thenReturn(FeatureFlags.DEFAULT_FLAGS);
            when(player.level()).thenReturn(clientlessLevel);
            when(player.getInventory()).thenReturn(inventory);
        }

        ItemStack snapshot(UUID id) {
            var stack = new ItemStack(Items.PAPER);
            MyriadMoldData.writePatternRef(stack, id);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putString("pattern_dimension", "minecraft:overworld");
                tag.putString("assembly_snapshot_dimension", "minecraft:overworld");
                tag.putString("assembly_snapshot_ref", id.toString());
            });
            return stack;
        }

        void verifyNoSnapshot() {
            identities.verify(() -> InstalledStretcherPatterns.snapshotForAssembly(any(ServerLevel.class), any(ItemStack.class)), never());
        }

        @Override public void close() { itemHooks.close(); encoded.close(); identities.close(); }
    }
}
