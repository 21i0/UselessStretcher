package com.sorrowmist.useless.stretcher.content.acceleration;

/** A target-owned clock for machines that schedule work against gameTime instead of tick counters. */
public interface ExtraTickClock {
    void uselessStretcher$advanceClock();
}
