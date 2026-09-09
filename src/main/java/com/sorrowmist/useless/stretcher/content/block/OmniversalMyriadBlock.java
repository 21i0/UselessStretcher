package com.sorrowmist.useless.stretcher.content.block;

import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class OmniversalMyriadBlock extends Block implements EntityBlock {
    public OmniversalMyriadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OmniversalMyriadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof OmniversalMyriadBlockEntity be) {
            if (!level.isClientSide) {
                player.openMenu(be, buffer -> buffer.writeBlockPos(pos));
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof OmniversalMyriadBlockEntity be) {
            be.loadFromItem(stack, level.registryAccess());
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof OmniversalMyriadBlockEntity be) {
            be.saveToItem(stack, level.registryAccess());
        }
        return stack;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            if (level.getBlockEntity(pos) instanceof OmniversalMyriadBlockEntity myriad) {
                ItemStack drop = new ItemStack(this);
                myriad.saveToItem(drop, level.registryAccess());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        // The NBT-carrying drop is already spawned in playerWillDestroy; skip the default
        // dropResources so we don't drop a second, empty copy.
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
    }
}
