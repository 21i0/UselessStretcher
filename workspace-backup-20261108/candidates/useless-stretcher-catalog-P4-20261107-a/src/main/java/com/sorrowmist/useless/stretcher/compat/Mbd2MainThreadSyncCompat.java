package com.sorrowmist.useless.stretcher.compat;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Moves MBD2 managed-field sync off LDLib's worker before it can serialize a live AE2 node. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class Mbd2MainThreadSyncCompat {
    private static final String MACHINE_HOLDER = "com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity";
    private static final String SYNC_HOLDER = "com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder";
    private static final int MAX_SYNCS_PER_TICK = 128;
    private static final long SYNC_BUDGET_NANOS = 2_000_000L;
    private static final ConcurrentHashMap<MinecraftServer, CoalescedMainThreadSyncQueue<BlockEntity>> QUEUES =
            new ConcurrentHashMap<>();
    private static final Object QUEUE_LIFECYCLE = new Object();
    // Weak keys remember shutdown for late workers without retaining stopped servers.
    private static final Map<MinecraftServer, Boolean> STOPPED_SERVERS = new WeakHashMap<>();
    private static final ClassValue<SyncContract> MBD2_HOLDERS = new ClassValue<>() {
        @Override protected SyncContract computeValue(Class<?> type) {
            if (!implementsMachineHolder(type)) return new SyncContract(null, null, null);
            try {
                // Only the standard callback is a replaceable periodic sync request. Never
                // coalesce a third-party subclass's own async processing or counters.
                if (!SYNC_HOLDER.equals(type.getMethod("asyncTick", long.class).getDeclaringClass().getName()))
                    return new SyncContract(null, null, null);
                Method storage = type.getMethod("getRootStorage");
                return new SyncContract(type.getMethod("sync", boolean.class), storage,
                        storage.getReturnType().getMethod("markAllDirty"));
            } catch (NoSuchMethodException ignored) {
                return new SyncContract(null, null, null);
            }
        }
    };

    private Mbd2MainThreadSyncCompat() { }

    /** No managed state, block lookup, NBT or SavedData is touched on the calling async thread. */
    public static boolean defer(ServerLevel dispatcherLevel, Object holder, Runnable original) {
        if (!(holder instanceof BlockEntity blockEntity)) return false;
        SyncContract contract = MBD2_HOLDERS.get(holder.getClass());
        if (contract.fullSync == null) return false;
        MinecraftServer server = dispatcherLevel.getServer();
        if (server.isSameThread()) return false;
        CoalescedMainThreadSyncQueue<BlockEntity> queue;
        synchronized (QUEUE_LIFECYCLE) {
            // A worker may reach this lock after ServerStoppedEvent has removed the queue.
            // Do not rely on an unsynchronized read of MinecraftServer.stopped to republish it.
            if (STOPPED_SERVERS.containsKey(server) || server.isStopped()) return true;
            queue = QUEUES.computeIfAbsent(server, ignored -> new CoalescedMainThreadSyncQueue<>());
        }
        queue.submit(blockEntity, () -> {
                    // Do not revive an unloaded/replaced machine or load a chunk just to sync it.
                    if (blockEntity.getLevel() == dispatcherLevel && !blockEntity.isRemoved()
                            && dispatcherLevel.hasChunkAt(blockEntity.getBlockPos())
                            && dispatcherLevel.getBlockEntity(blockEntity.getBlockPos()) == blockEntity) original.run();
                }, () -> {
                    if (blockEntity.getLevel() == dispatcherLevel && !blockEntity.isRemoved()
                            && dispatcherLevel.hasChunkAt(blockEntity.getBlockPos())
                            && dispatcherLevel.getBlockEntity(blockEntity.getBlockPos()) == blockEntity)
                        contract.forceSync(blockEntity);
                });
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        var queue = QUEUES.get(server);
        if (queue == null) return;
        if (!server.isSameThread()) throw new IllegalStateException("MBD2 sync bridge requires the server thread");
        queue.drain(server.getTickCount(), MAX_SYNCS_PER_TICK, SYNC_BUDGET_NANOS, System::nanoTime,
                blockEntity -> !blockEntity.isRemoved() && blockEntity.getLevel() instanceof ServerLevel level
                        && level.getServer() == server,
                (blockEntity, failure) -> LogUtils.getLogger().error(
                        "MBD2 managed sync failed on server thread at {}; keeping a full sync pending, retry in at least 20 ticks",
                        blockEntity.getBlockPos(), failure));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CoalescedMainThreadSyncQueue<BlockEntity> queue;
        synchronized (QUEUE_LIFECYCLE) {
            STOPPED_SERVERS.put(event.getServer(), Boolean.TRUE);
            queue = QUEUES.remove(event.getServer());
        }
        if (queue != null) queue.clear();
    }

    private static boolean implementsMachineHolder(Class<?> type) {
        if (type == null) return false;
        if (MACHINE_HOLDER.equals(type.getName())) return true;
        for (Class<?> contract : type.getInterfaces()) if (implementsMachineHolder(contract)) return true;
        return implementsMachineHolder(type.getSuperclass());
    }

    private record SyncContract(Method fullSync, Method rootStorage, Method markAllDirty) {
        void forceSync(Object holder) {
            try {
                // sync(true) still exits early when hasDirtySyncFields() is false. A failure
                // in custom packet construction can happen after every field was cleared.
                // Restore the sync dirty set before retrying the complete current snapshot.
                markAllDirty.invoke(rootStorage.invoke(holder));
                fullSync.invoke(holder, true);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot invoke MBD2 full managed sync", failure);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("MBD2 full managed sync failed", cause);
            }
        }
    }
}
