package com.sorrowmist.useless.stretcher.client.dialogue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Plays precisely the cue that just revealed a character. No independent cooldown or delayed tasks. */
public final class TwoIStoryVoice {
    private static final SoundEvent[] SOUNDS = {
            sound("two_i_voice"), sound("two_i_voice_soft"), sound("two_i_voice_bright"), sound("player_voice")
    };
    private SimpleSoundInstance active;
    private long pulses;

    private static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation("useless_stretcher", name));
    }

    public void play(TwoIStoryTimeline.Cue cue, boolean enabled) {
        if (!enabled || !cue.voiced()) return;
        stop();
        active = SimpleSoundInstance.forUI(SOUNDS[cue.timbre().ordinal()], cue.pitch(), cue.volume());
        Minecraft.getInstance().getSoundManager().play(active);
        pulses++;
    }

    public void stop() {
        if (active != null) {
            Minecraft.getInstance().getSoundManager().stop(active);
            active = null;
        }
    }

    public boolean hasActiveCue() { return active != null; }
    public long pulses() { return pulses; }
}
