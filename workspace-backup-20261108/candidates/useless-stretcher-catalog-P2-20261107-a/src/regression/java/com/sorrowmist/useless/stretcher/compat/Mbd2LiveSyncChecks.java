package com.sorrowmist.useless.stretcher.compat;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JavaOps;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real MBD2 definition/node/LDLib dispatcher checks, excluded from the production jar. */
public final class Mbd2LiveSyncChecks {
    private static final String DEFINITION = "com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition";
    private static final String LEDGER = "com.moakiee.ae2lt.logic.energy.WirelessEnergyLedger";
    private static final ResourceLocation ID = ResourceLocation.parse("useless_stretcher:mbd2_sync_fixture");
    private static final BlockPos POS = new BlockPos(18, 180, 3);
    private static Object definition;
    private static BlockEntity holder;
    private static Object trait;
    private static Object managedNode;
    private static Object nodeRef;
    private static FixtureData data;
    private static int ticks;
    private static int stage;
    private static boolean restart;

    private Mbd2LiveSyncChecks() { }

    @EventBusSubscriber(modid = UselessStretcherMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterEvent event) throws Exception {
            if (!Boolean.getBoolean("useless_stretcher.watchdog_mbd2")) return;
            if (!ModList.get().isLoaded("mbd2")) throw new IllegalStateException("MBD2 fixture dependency missing");
            if (definition == null) {
                Class<?> type = Class.forName(DEFINITION);
                Object builder = type.getMethod("builder").invoke(null);
                call(builder, "id", new Class<?>[]{ResourceLocation.class}, ID);
                definition = call(builder, "build");
                call(definition, "loadFactory");
                Object settings = call(definition, "machineSettings");
                Object aeTrait = Class.forName("com.lowdragmc.mbd2.integration.ae2.trait.MEInterfaceTraitDefinition")
                        .getConstructor().newInstance();
                check((Boolean) call(settings, "addTraitDefinition",
                        new Class<?>[]{Class.forName("com.lowdragmc.mbd2.common.trait.TraitDefinition")}, aeTrait),
                        "real MBD2 definition accepts AE2 interface trait");
            }
            call(definition, "onRegistry", new Class<?>[]{RegisterEvent.class}, event);
        }
    }

    /** Call once per server tick alongside the other watchdog checks. Run twice on the same save. */
    public static boolean tick(ServerLevel level) throws Exception {
        if (!Boolean.getBoolean("useless_stretcher.watchdog_mbd2")) return true;
        if (stage == 3) return true;
        check(++ticks < 240, "MBD2 live fixture completed within 240 ticks");
        MinecraftServer server = level.getServer();
        check(server.isSameThread(), "test runs on the server thread");
        if (stage == 0) {
            data = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FixtureData::new,
                    FixtureData::load), "useless_stretcher_mbd2_sync_fixture");
            restart = data.initialized;
            level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true);
            level.getChunkAt(POS);
            if (!restart) level.setBlockAndUpdate(POS, BuiltInRegistries.BLOCK.get(ID).defaultBlockState());
            holder = level.getBlockEntity(POS);
            check(holder != null && holder.getClass().getName().equals("com.lowdragmc.mbd2.common.blockentity.MachineBlockEntity"),
                    "registered production MachineBlockEntity exists");
            check(holder.getClass().getMethod("asyncTick", long.class).getDeclaringClass().getName()
                            .equals("com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder"),
                    "fixture uses the real default async callback");
            Object machine = call(holder, "getMetaMachine");
            for (Object candidate : (List<?>) call(machine, "getAdditionalTraits")) {
                if (candidate.getClass().getName().endsWith(".MEInterfaceTrait")) trait = candidate;
            }
            check(trait != null, "production AE2 interface trait was constructed");
            managedNode = call(trait, "getMainNode");
            stage = 1;
            return false;
        }
        if (stage == 1) {
            IGridNode node = (IGridNode) call(managedNode, "getNode");
            if (node == null) return false;
            GenericInternalInventory inventory = inventory();
            Object ledger = Class.forName(LEDGER).getMethod("get", MinecraftServer.class).invoke(null, server);
            Class<?> serviceType = Class.forName("com.moakiee.ae2lt.logic.energy.WirelessEnergyGridService");
            Object service = node.getGrid().getService((Class) serviceType);
            Object escrow = call(service, "escrow", new Class<?>[]{IGridNode.class}, node);
            CompoundTag nodeTag = nodeNbt(level);
            UUID claim = findUuid(nodeTag, "ae2ltEnergyClaim");
            check(claim != null && findUuid(nodeTag, "ae2ltEnergyTickBudget") != null,
                    "native node NBT preserves AE2LT claim and tick budget");
            if (restart) {
                check(claim.equals(data.claim), "same claim is restored after a full process restart");
                check(inventory.getAmount(0) == 37 && AEItemKey.of(Items.DIAMOND).equals(inventory.getKey(0)),
                        "all 37 interface items survive restart without duplication");
                check(((Number) call(escrow, "available")).longValue() == 431
                                && ((Number) call(escrow, "uncertain")).longValue() == 7,
                        "AE2LT confirmed and uncertain balances both survive restart");
                LogUtils.getLogger().info("MBD2 LIVE RESTART CONTENTS VERIFIED: claim={}, items=37, available=431, uncertain=7", claim);
            } else {
                inventory.setStack(0, new GenericStack(AEItemKey.of(Items.DIAMOND), 37));
                call(escrow, "credit", new Class<?>[]{long.class}, 431L);
                call(escrow, "quarantine", new Class<?>[]{long.class}, 7L);
                data.claim = claim;
            }
            call(holder, "detachAsyncLogic");
            Object storage = call(holder, "getRootStorage");
            for (Object ref : (Object[]) call(storage, "getNonLazyFields")) {
                if (call(ref, "readRaw") == managedNode) nodeRef = ref;
            }
            check(nodeRef != null, "native node is a non-lazy LDLib managed field");
            Field oldMark = field(nodeRef.getClass(), "oldValueMark");
            // LDLib stores this comparison snapshot with JavaOps, and calls old.equals(new).
            // Keep a non-null, different JavaOps sentinel rather than breaking its contract.
            Object sentinel = Map.of("stretcher_fixture_sentinel", "pending");
            oldMark.set(nodeRef, sentinel);
            Class<?> dispatcherClass = Class.forName("com.lowdragmc.lowdraglib2.async.AsyncThreadData");
            var constructor = dispatcherClass.getDeclaredConstructor(ServerLevel.class);
            constructor.setAccessible(true);
            Object dispatcher = constructor.newInstance(level);
            ((List<Object>) field(dispatcherClass, "asyncLogics").get(dispatcher)).add(holder);
            Method searching = dispatcherClass.getDeclaredMethod("searchingTask");
            searching.setAccessible(true);
            AtomicReference<Throwable> workerFailure = new AtomicReference<>();
            AtomicBoolean guardKept = new AtomicBoolean();
            Thread worker = new Thread(() -> {
                try {
                    try {
                        Class.forName(LEDGER).getMethod("get", MinecraftServer.class).invoke(null, server);
                        throw new AssertionError("AE2LT worker guard was removed");
                    } catch (InvocationTargetException expected) {
                        check(expected.getCause() instanceof IllegalStateException
                                && "Server thread required".equals(expected.getCause().getMessage()),
                                "AE2LT still rejects worker access");
                        guardKept.set(true);
                    }
                    for (int i = 0; i < 5_000; i++) searching.invoke(dispatcher);
                } catch (Throwable failure) { workerFailure.set(failure); }
            }, "MBD2 actual dispatcher regression");
            worker.setDaemon(true);
            worker.start();
            worker.join(2_000);
            check(!worker.isAlive() && workerFailure.get() == null && guardKept.get(),
                    "real transformed dispatcher worker completed without blocking on the server");
            check(oldMark.get(nodeRef) == sentinel, "dispatcher did not serialize node NBT off-thread");
            var queues = (Map<?, ?>) field(Mbd2MainThreadSyncCompat.class, "QUEUES").get(null);
            var queue = (CoalescedMainThreadSyncQueue<?>) queues.get(server);
            check(queue != null && queue.pendingCount() == 1, "5000 real dispatches coalesce into one pending holder");
            LogUtils.getLogger().info("MBD2 LIVE MIXIN DISPATCH VERIFIED: 5000 real LDLib dispatches, one pending holder, ledger worker guard retained");
            stage = 2;
            return false;
        }
        Object exported = field(nodeRef.getClass(), "oldValueMark").get(nodeRef);
        if (exported instanceof Map<?, ?> map && map.containsKey("stretcher_fixture_sentinel")) return false;
        Tag exportedTag = JavaOps.INSTANCE.convertTo(NbtOps.INSTANCE, exported);
        check(data.claim.equals(findUuid(exportedTag, "ae2ltEnergyClaim"))
                        && findUuid(exportedTag, "ae2ltEnergyTickBudget") != null,
                "main-thread native field snapshot includes current claim and budget");
        check(inventory().getAmount(0) == 37, "sync preserves interface contents");
        holder.setChanged();
        CompoundTag machineTag = holder.saveWithoutMetadata(level.registryAccess());
        check(data.claim.equals(findUuid(machineTag, "ae2ltEnergyClaim")), "machine save retains native claim NBT");
        data.initialized = true;
        data.setDirty();
        LogUtils.getLogger().info("MBD2 LIVE SYNC CHECKS PASSED: restart={}, native node={}, persisted machine={}",
                restart, exportedTag, machineTag);
        stage = 3;
        return true;
    }

    private static GenericInternalInventory inventory() throws Exception {
        return (GenericInternalInventory) call(call(trait, "getInterfaceLogic"), "getStorage");
    }

    private static CompoundTag nodeNbt(ServerLevel level) throws Exception {
        return (CompoundTag) call(managedNode, "serializeNBT", new Class<?>[]{HolderLookup.Provider.class}, level.registryAccess());
    }

    private static UUID findUuid(Tag tag, String name) {
        if (tag instanceof CompoundTag compound) {
            if (compound.hasUUID(name)) return compound.getUUID(name);
            for (String key : compound.getAllKeys()) {
                UUID found = findUuid(compound.get(key), name);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) { UUID found = findUuid(child, name); if (found != null) return found; }
        }
        return null;
    }

    private static Object call(Object owner, String name) throws Exception {
        return call(owner, name, new Class<?>[0]);
    }

    private static Object call(Object owner, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        return owner.getClass().getMethod(name, parameters).invoke(owner, arguments);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    private static final class FixtureData extends SavedData {
        boolean initialized;
        UUID claim;
        static FixtureData load(CompoundTag tag, HolderLookup.Provider provider) {
            FixtureData data = new FixtureData();
            data.initialized = tag.getBoolean("initialized");
            if (tag.hasUUID("claim")) data.claim = tag.getUUID("claim");
            return data;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("initialized", initialized);
            if (claim != null) tag.putUUID("claim", claim);
            return tag;
        }
    }
}
