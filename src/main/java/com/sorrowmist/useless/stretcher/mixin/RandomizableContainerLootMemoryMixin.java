package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.item.WondrousStaffLootRefresh;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class RandomizableContainerLootMemoryMixin {
    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V", at = @At("TAIL"))
    private void uselessStretcher$rememberLootTable(ResourceKey<LootTable> lootTable, CallbackInfo ci) {
        WondrousStaffLootRefresh.remember((RandomizableContainerBlockEntity) (Object) this, lootTable);
    }
}
