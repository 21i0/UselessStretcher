package com.sorrowmist.useless.stretcher.mixin;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(RandomizableContainerBlockEntity.class)
public interface LootContainerAccessor {
    @Accessor("lootTable") ResourceLocation uselessStretcher$getLootTable();
}
