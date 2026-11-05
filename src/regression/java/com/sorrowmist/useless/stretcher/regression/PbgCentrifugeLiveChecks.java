package com.sorrowmist.useless.stretcher.regression;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.compat.CoalescedMachineAccelerationCompat;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in, isolated-world checks against the installed PBG Extra factory, its native recipes and
 * its real AE storage-service integration. No substituted recipe/ticker, world-clock edits or
 * invented PBG contract are used. The mounted storage is an instrumented test backend, not an
 * AE disk cell. Native block-entity NBT reload is covered; a process restart is a separate run.
 */
public final class PbgCentrifugeLiveChecks {
    private static final ResourceLocation FACTORY = ResourceLocation.parse(
            "productivebeesgenesis:absolute_extra_mek_centrifuge_factory");
    private static final Map<MinecraftServer, Run> RUNS = new WeakHashMap<>();
    private static final ClassValue<Map<CallSignature, Method>> METHODS = new ClassValue<>() {
        @Override protected Map<CallSignature, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    private PbgCentrifugeLiveChecks() { }

    /** Invoke once per real server tick from the opt-in watchdog regression entry point. */
    public static boolean tick(ServerLevel level) throws Exception {
        for (String mod : List.of("productivebeesgenesis", "productivebees", "mekanism", "mekanism_extras", "ae2")) {
            check(ModList.get().isLoaded(mod), "PBG live regression requires its recorded fixture: missing " + mod);
        }
        Run run = RUNS.get(level.getServer());
        if (run == null) {
            run = new Run(level);
            RUNS.put(level.getServer(), run);
        }
        try {
            return run.tick(level);
        } catch (Exception | AssertionError failure) {
            run.close(level);
            throw failure;
        }
    }

    /** Separate two-process balance check; the caller chooses its isolated saved world. */
    public static boolean tickRestart(ServerLevel level) throws Exception {
        return PbgCentrifugeRestartChecks.tick(level);
    }

    private static final class Run {
        final List<Session> sessions = new ArrayList<>();
        final PartialFluidStore store = new PartialFluidStore();
        final IStorageProvider provider = mounts -> mounts.mount(store, 0);
        final AENetworkedBlockEntity power;
        final BlockPos powerPos;
        IStorageService storage;
        int lastServerTick = Integer.MIN_VALUE;
        int ticks;
        int recipeStarted;
        int blockedAt = -1;
        int reloadTick = -1;
        int settledTick = -1;
        boolean finished;
        long maxSubmissionNanos;
        long maxFlushNanos;

        Run(ServerLevel level) throws Exception {
            check(BuiltInRegistries.BLOCK.get(FACTORY) != Blocks.AIR, "official Extra factory is registered");
            for (int route = 0; route < 4; route++) {
                sessions.add(new Session(level, new BlockPos(128 + route * 16, 180, 32), route, false));
            }
            sessions.add(new Session(level, new BlockPos(192, 180, 32), 3, true));
            powerPos = sessions.getLast().pos.east();
            level.setBlockAndUpdate(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            power = (AENetworkedBlockEntity) level.getBlockEntity(powerPos);
            check(power != null, "real creative energy cell placed");
        }

        boolean tick(ServerLevel level) throws Exception {
            if (finished) return true;
            int serverTick = level.getServer().getTickCount();
            if (lastServerTick == serverTick) return false;
            lastServerTick = serverTick;
            ticks++;
            check(ticks < 1200, "native and accelerated PBG recipes finish within 1200 real ticks");
            Session fluid = sessions.getLast();

            // AE2 schedules onReady itself. Calling it manually initializes its managed node
            // twice when the scheduled lifecycle later runs. Wait for actual world ticks.
            if (ticks == 20) connect(fluid);
            if (ticks < 40) return false;
            if (recipeStarted == 0) {
                check(node(fluid).isOnline(), "native PBG managed node has power and channel");
                mount(fluid);
                for (Session session : sessions) session.start(level);
                recipeStarted = ticks;
                return false;
            }

            for (Session session : sessions) {
                session.topUpEnergy();
                session.checkConservation(store.amount);
            }
            long submissionStarted = System.nanoTime();
            for (Session session : sessions) session.accelerate(level);
            maxSubmissionNanos = Math.max(maxSubmissionNanos, System.nanoTime() - submissionStarted);
            long flushStarted = System.nanoTime();
            CoalescedMachineAccelerationCompat.flush(level.getServer());
            CoalescedMachineAccelerationCompat.flush(level.getServer());
            maxFlushNanos = Math.max(maxFlushNanos, System.nanoTime() - flushStarted);
            for (Session session : sessions) {
                session.checkConservation(store.amount);
                if (session.complete() && session.finishedAt == 0) session.finishedAt = ticks - recipeStarted;
            }

            if (reloadTick < 0 && fluid.complete()) {
                check(store.amount == 250, "partially accepting AE backend holds exactly its 250mB capacity");
                check(fluid.fluidAmount() == 550, "800mB native recipe honey retains rejected 550mB locally");
                if (blockedAt < 0) blockedAt = ticks;
                // Keep the real AE node/provider connected and continue both staff routes while
                // storage is full. This exercises the native rejection/backoff path over time,
                // instead of unloading the factory immediately after its first partial insert.
                if (ticks - blockedAt < 40) return false;
                check(store.partialAccept > 0, "real AE MODULATE insertion partially accepted native honey");
                check(store.zeroReject > 0, "full connected AE backend rejected a real MODULATE insertion");
                LogUtils.getLogger().info("PBG LIVE FULL STORAGE PRESSURE PASSED: realTicks={} AE=250mB local=550mB "
                                + "MODULATE partialAccept={} zeroReject={} maxInsertsPerTick={}",
                        ticks - blockedAt, store.partialAccept, store.zeroReject, store.maxInsertsPerTick);
                storage.removeGlobalStorageProvider(provider);
                storage = null;
                CompoundTag saved = fluid.machine.saveWithFullMetadata(level.registryAccess());
                fluid.machine.onChunkUnloaded();
                level.removeBlockEntity(fluid.pos);
                fluid.machine = BlockEntity.loadStatic(fluid.pos, level.getBlockState(fluid.pos), saved, level.registryAccess());
                check(fluid.machine != null, "official Extra factory loads from its own persisted NBT");
                level.setBlockEntity(fluid.machine);
                level.invalidateCapabilities(fluid.pos);
                fluid.checkConservation(store.amount);
                check(fluid.fluidAmount() == 550 && fluid.outputCount() == 8 && fluid.inputCount() == 0,
                        "native reload retains exact rejected fluid, wax outputs and consumed inputs");
                connect(fluid);
                reloadTick = ticks;
                LogUtils.getLogger().info("PBG LIVE NBT RELOAD PASSED: wax=8 AE-honey=250mB local-honey=550mB, native factory NBT");
            }
            if (reloadTick >= 0 && storage == null && ticks - reloadTick >= 30 && node(fluid).isOnline()) {
                mount(fluid);
                store.capacity = 800;
            }
            if (reloadTick >= 0 && store.amount == 800 && fluid.fluidAmount() == 0 && settledTick < 0) {
                settledTick = ticks;
            }
            boolean recipesComplete = true;
            for (Session session : sessions) if (session.finishedAt == 0) recipesComplete = false;
            if (!recipesComplete || settledTick < 0 || ticks - settledTick < 40) return false;

            for (int i = 1; i < 4; i++) check(sessions.get(i).finishedAt < sessions.getFirst().finishedAt,
                    "real acceleration route " + i + " finishes before the native-only factory");
            for (Session session : sessions) session.checkConservation(store.amount);
            check(store.amount == 800 && fluid.fluidAmount() == 0 && fluid.outputCount() == 8,
                    "idle and repeated acceleration never replay settled fluid or item outputs");
            LogUtils.getLogger().info("PBG LIVE CENTRIFUGE CHECKS PASSED: PBG={} factory={} native/single/range/combined-ticks={}/{}/{}/{} "
                            + "native honeycomb=8 wax=8 honey=800mB partial=250+550 native-NBT-reload=true "
                            + "full-storage-ticks=40 partialAccept={} zeroReject={} max-network-inserts-per-tick={} max-submit-ms={} max-flush-ms={}",
                    ModList.get().getModContainerById("productivebeesgenesis").orElseThrow().getModInfo().getVersion(), FACTORY,
                    sessions.get(0).finishedAt, sessions.get(1).finishedAt, sessions.get(2).finishedAt, sessions.get(3).finishedAt,
                    store.partialAccept, store.zeroReject, store.maxInsertsPerTick,
                    maxSubmissionNanos / 1_000_000.0, maxFlushNanos / 1_000_000.0);
            finished = true;
            close(level);
            return true;
        }

        private void connect(Session session) throws Exception {
            Object lifecycle = call(session.machine, "productivebeesgenesis$getAe2LifecycleHandler");
            call(lifecycle, "prepareForLoad", session.machine);
            call(lifecycle, "tryConnectNode", session.machine);
            var machineNode = node(session).getNode();
            var powerNode = power.getMainNode().getNode();
            check(machineNode != null && powerNode != null, "PBG and creative cell expose native AE nodes");
            if (machineNode.getGrid() != powerNode.getGrid()) GridHelper.createConnection(machineNode, powerNode);
        }

        private void mount(Session session) throws Exception {
            storage = node(session).getGrid().getStorageService();
            storage.addGlobalStorageProvider(provider);
            store.level = session.machine.getLevel() instanceof ServerLevel level ? level : null;
        }

        private void close(ServerLevel level) {
            if (storage != null) {
                storage.removeGlobalStorageProvider(provider);
                storage = null;
            }
            for (Session session : sessions) session.close(level);
            level.removeBlock(powerPos, false);
            CoalescedMachineAccelerationCompat.clear(level.getServer());
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static final class Session {
        final BlockPos pos;
        final int route;
        final boolean honey;
        final UUID owner = UUID.randomUUID();
        final RangeAccelerationSavedData ranges = new RangeAccelerationSavedData();
        BlockEntity machine;
        UUID rangeId;
        Item input;
        Item output;
        int initialCount;
        int finishedAt;

        Session(ServerLevel level, BlockPos pos, int route, boolean honey) throws Exception {
            this.pos = pos;
            this.route = route;
            this.honey = honey;
            level.getChunkAt(pos);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(FACTORY).defaultBlockState());
            machine = level.getBlockEntity(pos);
            check(machine != null && machine.getClass().getName().endsWith("TileEntityExtraMekCentrifugeFactory"),
                    "test uses the actual reported Extra factory implementation");
            Object holder = call(machine, "productivebeesgenesis$getAe2StateHolder");
            call(holder, "setSmeltingCompatEnabled", !honey);
            call(holder, "setAeItemOutputEnabled", false);
            call(holder, "setAeFluidOutputEnabled", honey);
            call(holder, "setAeItemInputEnabled", false);
            call(machine, "productivebeesgenesis$onSmeltingCompatChanged");
        }

        void start(ServerLevel level) throws Exception {
            input = honey ? Items.HONEYCOMB : Items.IRON_ORE;
            output = honey ? BuiltInRegistries.ITEM.get(ResourceLocation.parse("productivebees:wax")) : Items.IRON_INGOT;
            check(output != Items.AIR, "native recipe output is registered");
            initialCount = honey ? 8 : 4;
            slot("inputSlot", 0).setStack(new ItemStack(input, initialCount));
            check(inputCount() == initialCount, "actual factory accepts native recipe input");
            topUpEnergy();
            if ((route & 2) != 0) {
                ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
                staff.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 512);
                staff.set(StretcherComponents.RANGE_SIZE_X.get(), 1);
                staff.set(StretcherComponents.RANGE_SIZE_Y.get(), 1);
                staff.set(StretcherComponents.RANGE_SIZE_Z.get(), 1);
                staff.set(StretcherComponents.RANGE_IDLE_THROTTLE_DISABLED.get(), true);
                var player = FakePlayerFactory.get(level, new GameProfile(owner, "PbgLiveRange"));
                var placed = ranges.place(level, player, pos, staff);
                check(placed.status() == RangeAccelerationSavedData.PlacementStatus.CREATED, "real one-block range placed");
                rangeId = placed.id();
            }
        }

        void topUpEnergy() throws Exception {
            IEnergyContainer energy = (IEnergyContainer) call(machine, "getEnergyContainer");
            energy.setEnergy(energy.getMaxEnergy());
        }

        void accelerate(ServerLevel level) {
            if ((route & 1) != 0) WondrousStaffAcceleration.tickTarget(level, pos, 1024, this);
            if ((route & 2) != 0) ranges.tick(level.getServer());
        }

        int inputCount() throws Exception { return count("inputSlot", input); }

        int outputCount() throws Exception {
            return count("primaryOutputSlot", output) + count("secondaryOutputSlot", output) + count("tertiaryOutputSlot", output);
        }

        private int count(String accessor, Item expected) throws Exception {
            int total = 0;
            int lanes = ((Number) call(machine, "processes")).intValue();
            for (int lane = 0; lane < lanes; lane++) {
                ItemStack stack = slot(accessor, lane).getStack();
                check(stack.isEmpty() || stack.is(expected), "factory contains only the expected native recipe item");
                total += stack.getCount();
            }
            return total;
        }

        private IInventorySlot slot(String name, int index) throws Exception {
            return (IInventorySlot) call(machine, name, index);
        }

        long fluidAmount() throws Exception {
            long total = 0;
            int count = ((Number) call(machine, "getFluidTankCount")).intValue();
            for (int index = 0; index < count; index++) {
                IExtendedFluidTank tank = (IExtendedFluidTank) call(machine, "getFluidTank", index);
                if (!tank.isEmpty()) check(BuiltInRegistries.FLUID.getKey(tank.getFluid().getFluid()).equals(
                        ResourceLocation.parse("productivebees:honey")), "native honeycomb produces honey fluid");
                total += tank.getFluidAmount();
            }
            return total;
        }

        void checkConservation(long externalFluid) throws Exception {
            int consumed = initialCount - inputCount();
            check(consumed >= 0 && outputCount() == consumed,
                    "real factory conserves recipe input/output, route=" + route + " honey=" + honey);
            if (honey) check(fluidAmount() + externalFluid == consumed * 100L,
                    "native recipe honey is conserved across the local tanks and real AE storage service");
        }

        boolean complete() throws Exception { return inputCount() == 0 && outputCount() == initialCount; }

        void close(ServerLevel level) {
            if (rangeId != null) {
                ranges.reclaim(level.getServer(), owner, rangeId);
                rangeId = null;
            }
            level.removeBlock(pos, false);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
        }
    }

    private static IManagedGridNode node(Session session) throws Exception {
        return (IManagedGridNode) call(session.machine, "productivebeesgenesis$getAe2GridNode");
    }

    private static final class PartialFluidStore implements MEStorage {
        ServerLevel level;
        long amount;
        long capacity = 250;
        long lastTick = Long.MIN_VALUE;
        int insertsThisTick;
        int maxInsertsPerTick;
        long partialAccept;
        long zeroReject;

        @Override public Component getDescription() { return Component.literal("PBG live partial fluid storage"); }

        @Override public long insert(AEKey what, long requested, Actionable action, IActionSource source) {
            if (!(what instanceof AEFluidKey fluid) || !BuiltInRegistries.FLUID.getKey(fluid.getFluid()).equals(
                    ResourceLocation.parse("productivebees:honey"))) return 0;
            long accepted = Math.min(requested, Math.max(0, capacity - amount));
            if (action == Actionable.MODULATE) {
                amount += accepted;
                if (requested > 0 && accepted == 0) zeroReject++;
                else if (accepted > 0 && accepted < requested) partialAccept++;
                long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
                if (lastTick != tick) { lastTick = tick; insertsThisTick = 0; }
                maxInsertsPerTick = Math.max(maxInsertsPerTick, ++insertsThisTick);
            }
            return accepted;
        }

        @Override public void getAvailableStacks(KeyCounter out) {
            if (amount > 0) out.add(AEFluidKey.of(BuiltInRegistries.FLUID.get(ResourceLocation.parse("productivebees:honey"))), amount);
        }
    }

    static Object call(Object target, String name, Object... args) throws Exception {
        List<Class<?>> arguments = new ArrayList<>(args.length);
        for (Object argument : args) arguments.add(argument == null ? Void.class : argument.getClass());
        CallSignature signature = new CallSignature(name, List.copyOf(arguments));
        Map<CallSignature, Method> methods = METHODS.get(target.getClass());
        Method method = methods.get(signature);
        if (method == null) {
            for (Method candidate : target.getClass().getMethods()) {
                if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) continue;
                Class<?>[] types = candidate.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < args.length; i++) {
                    Class<?> type = types[i] == int.class ? Integer.class : types[i] == boolean.class ? Boolean.class : types[i];
                    if (args[i] != null && !type.isInstance(args[i])) { matches = false; break; }
                }
                if (!matches) continue;
                method = candidate;
                methods.put(signature, method);
                break;
            }
        }
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private record CallSignature(String name, List<Class<?>> arguments) { }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
