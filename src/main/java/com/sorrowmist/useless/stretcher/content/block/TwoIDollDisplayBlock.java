package com.sorrowmist.useless.stretcher.content.block;

import com.sorrowmist.useless.stretcher.content.blockentity.TwoIDollDisplayBlockEntity;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Animated client-side decoration. The complete doll item survives placement and pickup. */
public final class TwoIDollDisplayBlock extends Block implements EntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public TwoIDollDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int rotation = Math.floorMod(Math.round(context.getRotation() / 22.5F) + 8, 16);
        return defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TwoIDollDisplayBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ItemStack owned = stack.copyWithCount(1);
        if (placer instanceof Player player)
            com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.claimOrCheckOwner(owned, player);
        if (level.getBlockEntity(pos) instanceof TwoIDollDisplayBlockEntity display) display.setDoll(owned);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TwoIDollDisplayBlockEntity display
                ? display.copyDoll() : new ItemStack(ModItems.TWO_I_DOLL.get());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof TwoIDollDisplayBlockEntity display ? display.copyDoll()
                : new ItemStack(ModItems.TWO_I_DOLL.get()));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                                Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(state, level, pos, player);
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof TwoIDollDisplayBlockEntity display) {
                ItemStack owned = display.copyDoll();
                if (!com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.claimOrCheckOwner(owned, player))
                    return InteractionResult.FAIL;
                if (!ItemStack.isSameItemSameTags(owned, display.copyDoll())) display.setDoll(owned);
            }
            if (player.isShiftKeyDown()) {
                ItemStack doll = getCloneItemStack(level, pos, state);
                ItemStack acquired = doll.copy();
                // Remove first, then hand back exactly one stack. No loot path runs twice.
                if (level.removeBlock(pos, false)) {
                    boolean inserted = player.getInventory().add(doll);
                    if (!inserted && !doll.isEmpty()) popResource(level, pos, doll);
                    player.getInventory().setChanged();
                    if (inserted && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                        com.sorrowmist.useless.stretcher.content.item.TwoIDollAcquisition.notifyAcquired(serverPlayer, acquired);
                }
            } else if (level.getBlockEntity(pos) instanceof TwoIDollDisplayBlockEntity display) {
                display.toggleSpinning();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
