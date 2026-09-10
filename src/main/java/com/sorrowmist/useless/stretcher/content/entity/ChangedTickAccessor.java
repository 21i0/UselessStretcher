package com.sorrowmist.useless.stretcher.content.entity;

/**
 * Duck interface injected into every {@code BlockEntity} (by
 * {@code com.sorrowmist.useless.stretcher.mixin.BlockEntityChangedMixin}) so the wondrous staff
 * can tell whether a machine actually did something recently via {@code BlockEntity.setChanged()}.
 *
 * <p>This interface deliberately lives outside the mixin package: classes inside a package
 * declared by {@code useless_stretcher.mixins.json} must not be referenced directly.
 */
public interface ChangedTickAccessor {
    /** Game time at which {@code setChanged()} was last called, or -1 when never called. */
    long uselessStretcher$getLastChangedTick();
}
