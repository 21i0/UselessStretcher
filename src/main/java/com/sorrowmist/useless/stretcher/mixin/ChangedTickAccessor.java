package com.sorrowmist.useless.stretcher.mixin;

/**
 * Duck interface injected into every {@code BlockEntity} so the wondrous staff can tell whether
 * a machine actually did something recently (via {@code BlockEntity.setChanged()}).
 */
public interface ChangedTickAccessor {
    /** Game time at which {@code setChanged()} was last called, or -1 when never called. */
    long uselessStretcher$getLastChangedTick();
}
