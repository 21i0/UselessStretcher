package com.sorrowmist.useless.stretcher.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.sorrowmist.useless.stretcher.content.entity.TwoICaptureProtection;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** noSummon filters the command argument; also reject nested passenger NBT and direct API calls. */
@Mixin(SummonCommand.class)
public abstract class TwoISummonCommandMixin {
    @Inject(method = "createEntity", at = @At("HEAD"))
    private static void uselessStretcher$rejectDoll(CommandSourceStack source, Holder.Reference<EntityType<?>> type,
            Vec3 pos, CompoundTag tag, boolean randomize, CallbackInfoReturnable<Entity> cir) throws CommandSyntaxException {
        if (type.value() == ModEntities.TWO_I_DOLL.get() || TwoICaptureProtection.containsDoll(tag))
            throw new SimpleCommandExceptionType(Component.translatable("commands.summon.failed")).create();
    }
}
