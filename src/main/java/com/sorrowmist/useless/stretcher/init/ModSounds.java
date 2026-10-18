package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Server-addressable sounds are registered, not just listed in the client's sounds.json. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, UselessStretcherMod.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> TWO_I_PAT =
            SOUNDS.register("two_i_pat", SoundEvent::createVariableRangeEvent);

    private ModSounds() {}
}
