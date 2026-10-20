package com.sorrowmist.useless.stretcher.init;

import net.minecraft.resources.ResourceLocation;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredHolder;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;

/** Server-addressable sounds are registered, not just listed in the client's sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, UselessStretcherMod.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> TWO_I_PAT =
            SOUNDS.register("two_i_pat", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(UselessStretcherMod.MODID,"two_i_pat")));

    private ModSounds() {}
}
