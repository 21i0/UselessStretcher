package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.world.entity.Entity;

/** Narrow vanilla lifecycle scopes; direct storage purges cannot silently orphan a live doll. */
public final class TwoILifecycleGuard implements AutoCloseable {
    private static final ThreadLocal<Entity> CURRENT = new ThreadLocal<>();
    private final Entity previous;

    private TwoILifecycleGuard(Entity entity) {
        previous = CURRENT.get();
        CURRENT.set(entity);
    }

    public static TwoILifecycleGuard allow(Entity entity) { return new TwoILifecycleGuard(entity); }

    public static boolean allows(Entity entity) { return CURRENT.get() == entity; }

    public static boolean blocks(Object entity) {
        return entity instanceof TwoIDollEntity doll && !doll.level().isClientSide
                && doll.isAddedToWorld() && !doll.isRemoved() && CURRENT.get() != doll;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
