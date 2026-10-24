package com.sorrowmist.useless.stretcher.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Detach native callback/capability NBT before the capsule chunk reaches async IO. */
@Mixin(ChunkMap.class)
public abstract class CapsuleChunkSaveSnapshotMixin {
    @Shadow @Final private ServerLevel level;
    @ModifyArg(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"), index = 1)
    private CompoundTag uselessStretcher$snapshot(CompoundTag tag) {
        var dimension = level.dimension().location();
        if (dimension.getNamespace().equals("useless_stretcher") && dimension.getPath().startsWith("capsule"))
            return tag.copy();
        return tag;
    }
}
