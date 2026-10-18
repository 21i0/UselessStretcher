package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.capsule.CapsuleGenerationContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Capsule plans already anchored the complete piece, including worlds with negative terrain Y. */
@Mixin(ScatteredFeaturePiece.class)
public abstract class CapsuleScatteredHeightMixin {
    @org.spongepowered.asm.mixin.Unique private boolean uselessStretcher$anchored;

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceType;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void readAnchor(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType type,
                            net.minecraft.nbt.CompoundTag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        uselessStretcher$anchored = tag.getBoolean("useless_stretcher_ground_anchor");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveAnchor(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                           net.minecraft.nbt.CompoundTag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (uselessStretcher$anchored) tag.putBoolean("useless_stretcher_ground_anchor", true);
    }
    @Inject(method = "updateAverageGroundHeight", at = @At("HEAD"), cancellable = true)
    private void retainAverageAnchor(LevelAccessor level, BoundingBox bounds, int height, CallbackInfoReturnable<Boolean> cir) {
        if (uselessStretcher$anchored && CapsuleGenerationContext.activeFor(level)) cir.setReturnValue(true);
    }

    @Inject(method = "updateHeightPositionToLowestGroundHeight", at = @At("HEAD"), cancellable = true)
    private void retainLowestAnchor(LevelAccessor level, int height, CallbackInfoReturnable<Boolean> cir) {
        if (uselessStretcher$anchored && CapsuleGenerationContext.activeFor(level)) cir.setReturnValue(true);
    }
}
