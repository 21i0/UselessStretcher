package com.sorrowmist.useless.stretcher.regression;

import com.sorrowmist.useless.stretcher.content.blockentity.TwoIDollDisplayBlockEntity;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Invoked by the isolated regression server, never shipped in a release jar. */
public final class TwoIDollDisplayChecks {
    private TwoIDollDisplayChecks() { }

    public static void run(ServerLevel level) throws Exception {
        TwoIDollHeadwearChecks.run(level);
        var state = ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState();
        var display = new TwoIDollDisplayBlockEntity(BlockPos.ZERO, state);
        ItemStack original = new ItemStack(ModItems.TWO_I_DOLL.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_BOND.get(), -87);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_INVITATION_USED.get(), true);
        original.setHoverName(Component.literal("2i regression skin companion"));
        original.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
        display.setDoll(original);
        check(ItemStack.isSameItemSameTags(original, display.copyDoll()), "stored full stack");
        ItemStack copy = display.copyDoll();
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(copy, StretcherComponents.TWO_I_BOND.get(), 100);
        check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(display.copyDoll(), StretcherComponents.TWO_I_BOND.get(), 0) == -87,
                "returned copy cannot mutate storage");
        var restored = BlockEntity.loadStatic(BlockPos.ZERO, state,
                display.saveWithFullMetadata());
        check(restored instanceof TwoIDollDisplayBlockEntity, "registered type loads");
        check(ItemStack.isSameItemSameTags(original, ((TwoIDollDisplayBlockEntity)restored).copyDoll()),
                "save-load preserves signed bond, invitation, custom name and unrelated components");
        var legacy = new TwoIDollDisplayBlockEntity(BlockPos.ZERO, state);
        check(legacy.copyDoll().is(ModItems.TWO_I_DOLL.get()), "old stateless decoration has a safe fallback");
        check(((net.minecraft.world.level.block.EntityBlock)state.getBlock())
                .getTicker(level, state, display.getType()) == null, "no server animation ticker");
        display.setLevel(level);
        long now = level.getGameTime();
        display.toggleSpinning();
        check(display.isSpinning() && Math.abs(display.rotationAt(now + 50) - 90) < .001F,
                "one click starts continuous time-based rotation");
        var rotating = (TwoIDollDisplayBlockEntity) BlockEntity.loadStatic(BlockPos.ZERO, state,
                display.saveWithFullMetadata());
        check(rotating.isSpinning() && rotating.rotationAt(now + 87.5) == display.rotationAt(now + 87.5),
                "reload and client update preserve rotation phase");
        rotating.setLevel(level);
        rotating.toggleSpinning();
        float paused = rotating.rotationAt(now);
        check(!rotating.isSpinning() && rotating.rotationAt(now + 200) == paused,
                "second click pauses without introducing an animation ticker");
        checkOwnerInteractions(level, state);
    }

    private static void checkOwnerInteractions(ServerLevel level, net.minecraft.world.level.block.state.BlockState state) throws Exception {
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "TwoIDisplayOwner"));
        var guest = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "TwoIDisplayGuest"));
        BlockPos pos = new BlockPos(54, 180, 54);
        var old = level.getBlockState(pos);
        var oldBlockEntity = level.getBlockEntity(pos);
        var oldTag = oldBlockEntity == null ? null : oldBlockEntity.saveWithFullMetadata();
        var block = ModBlocks.TWO_I_DOLL_DISPLAY.get();
        try {
            level.setBlockAndUpdate(pos, state);
            ItemStack legacy = new ItemStack(ModItems.TWO_I_DOLL.get());
            block.setPlacedBy(level, pos, state, owner, legacy);
            var display = (TwoIDollDisplayBlockEntity)level.getBlockEntity(pos);
            check(owner.getUUID().equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(display.copyDoll(), StretcherComponents.TWO_I_OWNER.get())),
                    "placing a legacy doll records the placer as owner");
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos, false);
            check(block.use(state, level, pos, guest, net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.FAIL
                    && !display.isSpinning(), "stranger cannot rotate someone else's display");
            guest.setShiftKeyDown(true);
            check(block.use(state, level, pos, guest, net.minecraft.world.InteractionHand.MAIN_HAND, hit) == net.minecraft.world.InteractionResult.FAIL
                    && level.getBlockEntity(pos) == display, "stranger shift pickup refuses without removing the display");
            guest.setShiftKeyDown(false);
            block.use(state, level, pos, owner, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            check(display.isSpinning(), "owner may still rotate their display");
            check(owner.getUUID().equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(block.getCloneItemStack(level, pos, state), StretcherComponents.TWO_I_OWNER.get())),
                    "display pick keeps ownership");
            com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i display ownership: real placement, stranger rotate/pickup denial, owner rotation and pick component PASSED");
        } finally {
            guest.setShiftKeyDown(false);
            level.setBlockAndUpdate(pos, old);
            if (oldTag != null) level.setBlockEntity(BlockEntity.loadStatic(pos, old, oldTag));
        }
    }

    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError("2i display: " + name);
    }
}
