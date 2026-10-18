package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Vanilla keeps archaeology candidates only in memory; capsule callbacks span saves. */
@Mixin(DesertPyramidPiece.class)
public abstract class DesertPyramidCheckpointMixin {
    @Shadow @Final private List<BlockPos> potentialSuspiciousSandWorldPositions;
    @Shadow private BlockPos randomCollapsedRoofPos;

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveArchaeology(StructurePieceSerializationContext context, CompoundTag tag, CallbackInfo ci) {
        ListTag positions = new ListTag();
        for (BlockPos pos : potentialSuspiciousSandWorldPositions) {
            CompoundTag entry = new CompoundTag(); entry.putLong("pos", pos.asLong()); positions.add(entry);
        }
        tag.put("useless_stretcher_archaeology", positions);
        tag.putLong("useless_stretcher_collapsed_roof", randomCollapsedRoofPos.asLong());
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void loadArchaeology(CompoundTag tag, CallbackInfo ci) {
        for (Tag entry : tag.getList("useless_stretcher_archaeology", Tag.TAG_COMPOUND))
            potentialSuspiciousSandWorldPositions.add(BlockPos.of(((CompoundTag) entry).getLong("pos")));
        if (tag.contains("useless_stretcher_collapsed_roof"))
            randomCollapsedRoofPos = BlockPos.of(tag.getLong("useless_stretcher_collapsed_roof"));
    }
}
