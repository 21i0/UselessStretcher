package com.sorrowmist.useless.stretcher.content.block;

import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;

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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.flavor")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_open")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_molds")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_patterns")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_select")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_ae")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.useless_stretcher.omniversal_myriad.hint_move")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
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
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack drop = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof OmniversalMyriadBlockEntity block) {
            block.saveToItem(drop, builder.getLevel().registryAccess());
        }
        return List.of(drop);
    }
}
