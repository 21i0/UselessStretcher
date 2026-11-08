package com.sorrowmist.useless.stretcher.regression;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Real-server dispatch/accounting checks using PBG's actual public batching interface.
 * The counting fixture does not substitute for a real PBG recipe/AE-storage conservation run.
 * Its optional interface implementation is loaded only when PBG is installed.
 */
public final class CoalescedAccelerationChecks {
    private static final Map<MinecraftServer, Run> RUNS = new WeakHashMap<>();
    private static boolean absentReported;

    private CoalescedAccelerationChecks() { }

    /** Call once per real server tick. True means all stages have completed. */
    public static boolean tick(ServerLevel level) throws Exception {
        if (!ModList.get().isLoaded("productivebeesgenesis")) {
            if (!absentReported) {
                LogUtils.getLogger().info("COALESCED ACCELERATION OPTIONAL INTERFACE SKIPPED: Productive Bees Genesis absent");
                absentReported = true;
            }
            return true;
        }
        return RUNS.computeIfAbsent(level.getServer(), ignored -> new Run()).tick(level);
    }

    private static final class Run {
        private final List<Placed> placed = new ArrayList<>();
        private int lastServerTick = Integer.MIN_VALUE;
        private int stage;
        private ProbeMachine delayed;
        private ProbeMachine realRoutes;
        private ProbeMachine accumulationFailure;
        private ProbeMachine flushFailure;
        private ProbeMachine budgetDeferred;

        private boolean tick(ServerLevel level) throws Exception {
            if (stage == 10) return true;
            int now = level.getServer().getTickCount();
            if (lastServerTick == now) return false;
            lastServerTick = now;
            try {
                switch (stage) {
                    case 0 -> {
                        // Prime optional reflection/class loading separately from budget assertions.
                        ProbeMachine warm = place(level, new BlockPos(20, 200, 12));
                        check(Compat.accumulate(warm, 1) == 1, "official PBG interface is recognized");
                        Compat.flush(level.getServer());
                        check(warm.acceptedTicks == 1 && warm.flushCalls == 1,
                                "warm-up reaches the actual interface implementation");

                        delayed = place(level, new BlockPos(22, 200, 12));
                        check(WondrousStaffAcceleration.tickTarget(level, delayed.getBlockPos(), 3, delayed) == 3,
                                "first coalesced contribution consumes three virtual ticks");
                        check(WondrousStaffAcceleration.tickTarget(level, delayed.getBlockPos(), 4, delayed) == 4,
                                "second contribution consumes four virtual ticks");
                        check(delayed.acceptedTicks == 7 && delayed.ordinaryCalls == 0 && delayed.aeCalls == 0,
                                "accepted batch does not also replay ordinary or AE production");
                        Compat.flush(level.getServer());
                        check(delayed.flushCalls == 1 && delayed.outputItems == 7 && delayed.outputFluidMb == 1750,
                                "multiple contributions flush exactly once");
                        check(WondrousStaffAcceleration.tickTarget(level, delayed.getBlockPos(), 5, delayed) == 5,
                                "late same-tick contribution is accepted");
                        Compat.flush(level.getServer());
                        check(delayed.flushCalls == 1 && delayed.bank == 5 && delayed.outputItems == 7,
                                "a target already flushed this real tick defers its later contribution");
                    }
                    case 1 -> {
                        Compat.flush(level.getServer());
                        check(delayed.flushCalls == 2 && delayed.outputItems == 12 && delayed.outputFluidMb == 3000
                                        && delayed.bank == 0,
                                "next real tick settles the deferred contribution once");
                        Compat.flush(level.getServer());
                        delayed.nativeTick();
                        check(delayed.flushCalls == 2 && delayed.outputItems == 12 && delayed.outputFluidMb == 3000,
                                "repeated flush and native bank drain do not replay settled output");
                        realRoutes = place(level, new BlockPos(24, 200, 12));
                        actualRoutes(level, realRoutes);
                        check(realRoutes.acceptedTicks == 24 && realRoutes.flushCalls == 0 && realRoutes.bank == 24,
                                "actual single-marker and range routes queue their combined work");
                        check(realRoutes.ordinaryCalls == 0 && realRoutes.aeCalls == 0,
                                "actual routes do not fall through to complete/AE tickers");
                        // Deliberately no manual flush: the production tick-end event must settle this.
                    }
                    case 2 -> {
                        check(realRoutes.flushCalls == 1 && realRoutes.bank == 0
                                        && realRoutes.outputItems == 24 && realRoutes.outputFluidMb == 6000,
                                "the real server-tick-end event flushes both routes exactly once");
                        realRoutes.nativeTick();
                        check(realRoutes.outputItems == 24 && realRoutes.outputFluidMb == 6000,
                                "native processing after tick-end cannot duplicate the batch");
                        accumulationFailure = place(level, new BlockPos(26, 200, 12));
                        accumulationFailure.failAccumulateAfterMutation = true;
                        check(WondrousStaffAcceleration.tickTarget(level, accumulationFailure.getBlockPos(), 9, accumulationFailure) == 9,
                                "unknown accumulation commit is consumed, not replayed");
                        check(accumulationFailure.accumulateCalls == 1 && accumulationFailure.acceptedTicks == 9
                                        && accumulationFailure.ordinaryCalls == 0 && accumulationFailure.aeCalls == 0,
                                "mutating accumulation failure never falls back to ordinary/AE tickers");
                        // Logging/class loading on the deliberate failure may consume this tick's
                        // wall-clock budget. Test retries on the next real tick, not a reset budget.
                    }
                    case 3 -> {
                        accumulationFailureDoesNotReplay(level);
                        flushFailure = place(level, new BlockPos(28, 200, 12));
                        flushFailure.failFlushAfterMutation = true;
                        check(WondrousStaffAcceleration.tickTarget(level, flushFailure.getBlockPos(), 11, flushFailure) == 11,
                                "flush failure fixture accepts work");
                        Compat.flush(level.getServer());
                        check(flushFailure.accumulateCalls == 1 && flushFailure.flushCalls == 1
                                        && flushFailure.outputItems == 11 && flushFailure.outputFluidMb == 2750,
                                "deliberately failing flush mutates its outputs exactly once");
                    }
                    case 4 -> flushFailureDoesNotReplay(level);
                    case 5 -> replacementAndDimensions(level);
                    case 6 -> {
                        budgetDeferred = place(level, new BlockPos(36, 200, 12));
                        check(WondrousStaffAcceleration.tickTarget(level, budgetDeferred.getBlockPos(), 6, budgetDeferred) == 6,
                                "budget-deferred target first accepts its bounded bank");
                        // Account synthetic work far above the production cap; no sleep or tight
                        // elapsed-time assertion is required to prove that the guard is honored.
                        AccelerationExecutionBudget.recordWork(level.getServer(), System.nanoTime() - 1_000_000_000L);
                        Compat.flush(level.getServer());
                        check(budgetDeferred.flushCalls == 0 && budgetDeferred.bank == 6 && budgetDeferred.outputItems == 0,
                                "exhausted shared budget defers flush without mutating or losing the accepted bank");
                    }
                    case 7 -> {
                        check(budgetDeferred.flushCalls == 1 && budgetDeferred.bank == 0,
                                "real tick-start event settles older accepted work before fresh ordinary competition");
                        budgetDeferred.nativeTick();
                        check(budgetDeferred.flushCalls == 1 && budgetDeferred.bank == 0
                                        && budgetDeferred.outputItems == 6 && budgetDeferred.outputFluidMb == 1500,
                                "tick-start settlement and native processing do not duplicate the deferred bank");
                        deferUnderCompetition(level, 4, 1, 6);
                    }
                    case 8 -> {
                        check(budgetDeferred.flushCalls == 2 && budgetDeferred.bank == 0
                                        && budgetDeferred.outputItems == 10 && budgetDeferred.outputFluidMb == 2500,
                                "tick-start settlement remains live when the previous real tick exhausted its full budget");
                        deferUnderCompetition(level, 5, 2, 10);
                    }
                    case 9 -> {
                        check(budgetDeferred.flushCalls == 3 && budgetDeferred.bank == 0
                                        && budgetDeferred.outputItems == 15 && budgetDeferred.outputFluidMb == 3750,
                                "successive competing real ticks cannot starve already accepted coalesced work");
                        Compat.flush(level.getServer());
                        budgetDeferred.nativeTick();
                        check(budgetDeferred.flushCalls == 3 && budgetDeferred.outputItems == 15,
                                "tick-start, repeated tick-end flush and native processing preserve one settlement per tick");
                        expiredDeadline(level);
                        Compat.clear(level.getServer());
                        cleanup();
                        LogUtils.getLogger().info("COALESCED ACCELERATION CHECKS PASSED: official PBG batching interface, real marker/range, tick-start fairness, tick-end deduplication, unknown commits, replacement, dimensions and deadlines");
                    }
                    default -> throw new AssertionError("unexpected coalesced regression stage");
                }
                stage++;
                return stage == 10;
            } catch (Throwable failure) {
                try { Compat.clear(level.getServer()); } catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                cleanup();
                if (failure instanceof Exception exception) throw exception;
                if (failure instanceof Error error) throw error;
                throw new RuntimeException(failure);
            }
        }

        private void deferUnderCompetition(ServerLevel level, int ticks, int expectedFlushCalls,
                                           int settledItems) throws Exception {
            check(WondrousStaffAcceleration.tickTarget(level, budgetDeferred.getBlockPos(), ticks, budgetDeferred) == ticks,
                    "fresh work is accepted after the current tick-start settlement");
            // Model fresh ordinary accelerators taking all remaining time every real tick.
            // Only the next real tick's production Pre hook may rescue this older queued bank.
            AccelerationExecutionBudget.recordWork(level.getServer(), System.nanoTime() - 1_000_000_000L);
            Compat.flush(level.getServer());
            check(budgetDeferred.flushCalls == expectedFlushCalls && budgetDeferred.bank == ticks
                            && budgetDeferred.outputItems == settledItems,
                    "late work remains pending without a second flush in a fully occupied real tick");
        }

        private void actualRoutes(ServerLevel level, ProbeMachine machine) {
            UUID owner = UUID.randomUUID();
            var player = FakePlayerFactory.get(level, new GameProfile(owner, "CoalescedRoutes"));
            ItemStack single = staff(16);
            player.setItemInHand(InteractionHand.MAIN_HAND, single);
            player.setShiftKeyDown(true);
            WondrousStaffAccelerationEntity marker = null;
            RangeAccelerationSavedData ranges = new RangeAccelerationSavedData();
            UUID fieldId = null;
            try {
                BlockPos pos = machine.getBlockPos();
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                check(WondrousStaffAcceleration.tryUse(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                        "real enabled staff interaction creates a single-target marker");
                marker = level.getEntitiesOfClass(WondrousStaffAccelerationEntity.class, new AABB(pos).inflate(1),
                                candidate -> pos.equals(candidate.getTargetPos()))
                        .stream().findFirst().orElseThrow(() -> new AssertionError("single-target marker missing"));
                marker.tick();
                check(machine.acceptedTicks == 16, "real single-target marker contributes its selected multiplier");
                fieldId = ranges.place(level, player, pos, staff(8)).id();
                check(fieldId != null, "real range placement succeeds");
                ranges.tick(level.getServer());
                check(machine.acceptedTicks == 24, "real range contributes its own selected multiplier");
            } finally {
                if (marker != null) marker.discard();
                if (fieldId != null) ranges.reclaim(level.getServer(), owner, fieldId);
                player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        private void accumulationFailureDoesNotReplay(ServerLevel level) throws Exception {
            int nativeBefore = accumulationFailure.ordinaryCalls;
            check(WondrousStaffAcceleration.tickTarget(level, accumulationFailure.getBlockPos(), 9, accumulationFailure) == 9,
                    "paused failed instance consumes future requests without retry");
            Compat.flush(level.getServer());
            Compat.flush(level.getServer());
            accumulationFailure.nativeTick();
            check(accumulationFailure.accumulateCalls == 1 && accumulationFailure.acceptedTicks == 9
                            && accumulationFailure.outputItems == 9 && accumulationFailure.outputFluidMb == 2250,
                    "mutating accumulation failure is neither fallback-ticked nor submitted twice");
            check(accumulationFailure.aeCalls == 0 && accumulationFailure.ordinaryCalls == nativeBefore + 1,
                    "failed coalesced instance only receives the explicitly requested native tick");
        }

        private void flushFailureDoesNotReplay(ServerLevel level) throws Exception {
            int nativeBefore = flushFailure.ordinaryCalls;
            Compat.flush(level.getServer());
            Compat.flush(level.getServer());
            check(WondrousStaffAcceleration.tickTarget(level, flushFailure.getBlockPos(), 11, flushFailure) == 11,
                    "unknown flush commit pauses the failed instance");
            Compat.flush(level.getServer());
            flushFailure.nativeTick();
            check(flushFailure.accumulateCalls == 1 && flushFailure.flushCalls == 1
                            && flushFailure.outputItems == 11 && flushFailure.outputFluidMb == 2750 && flushFailure.bank == 0,
                    "mutating flush failure is not flushed, fallback-ticked or credited twice");
            check(flushFailure.aeCalls == 0 && flushFailure.ordinaryCalls == nativeBefore + 1,
                    "failed flush never replays independent AE production");

            BlockPos replacementPos = accumulationFailure.getBlockPos();
            level.setBlockAndUpdate(replacementPos, Blocks.AIR.defaultBlockState());
            ProbeMachine replacement = place(level, replacementPos);
            check(WondrousStaffAcceleration.tickTarget(level, replacementPos, 3, replacement) == 3,
                    "new block entity at failed position can still accelerate");
            Compat.flush(level.getServer());
            check(replacement.acceptedTicks == 3 && replacement.outputItems == 3,
                    "failure isolation is by block-entity identity");
        }

        private void replacementAndDimensions(ServerLevel level) throws Exception {
            ProbeMachine stale = place(level, new BlockPos(30, 200, 12));
            check(WondrousStaffAcceleration.tickTarget(level, stale.getBlockPos(), 15, stale) == 15,
                    "queued replacement fixture accepts work");
            level.setBlockAndUpdate(stale.getBlockPos(), Blocks.STONE.defaultBlockState());
            Compat.flush(level.getServer());
            check(stale.flushCalls == 0 && stale.outputItems == 0,
                    "removed/replaced block entity is never flushed later");
            ProbeMachine replacement = place(level, stale.getBlockPos());
            check(WondrousStaffAcceleration.tickTarget(level, replacement.getBlockPos(), 2, replacement) == 2,
                    "replacement has no stale pending attribution");
            Compat.flush(level.getServer());
            check(replacement.outputItems == 2 && replacement.outputFluidMb == 500,
                    "replacement only produces its own contribution");

            ResourceKey<Level> endKey = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "capsule_end"));
            ServerLevel other = level.getServer().getLevel(endKey);
            check(other != null && other != level, "second real capsule ServerLevel exists");
            BlockPos samePosition = new BlockPos(32, 200, 12);
            ProbeMachine first = place(level, samePosition);
            ProbeMachine second = place(other, samePosition);
            check(WondrousStaffAcceleration.tickTarget(level, samePosition, 7, first) == 7
                            && WondrousStaffAcceleration.tickTarget(other, samePosition, 13, second) == 13,
                    "same coordinates in distinct dimensions both accept work");
            Compat.flush(level.getServer());
            check(first.flushCalls == 1 && first.outputItems == 7 && first.outputFluidMb == 1750
                            && second.flushCalls == 1 && second.outputItems == 13 && second.outputFluidMb == 3250,
                    "queue and once-per-tick keys include block-entity/dimension identity");
        }

        private void expiredDeadline(ServerLevel level) throws Exception {
            ProbeMachine machine = place(level, new BlockPos(34, 200, 12));
            Method withinBudget = WondrousStaffAcceleration.class.getDeclaredMethod("tickTargetWithinBudget",
                    ServerLevel.class, BlockPos.class, int.class, long.class);
            withinBudget.setAccessible(true);
            check(((Number) withinBudget.invoke(null, level, machine.getBlockPos(), 32, 0L)).intValue() == 0,
                    "expired deadline returns before coalesced mutation");
            Compat.flush(level.getServer());
            check(machine.accumulateCalls == 0 && machine.flushCalls == 0
                            && machine.ordinaryCalls == 0 && machine.aeCalls == 0,
                    "expired deadline cannot enter any production route");
        }

        private ProbeMachine place(ServerLevel level, BlockPos pos) {
            level.getChunkAt(pos);
            level.setBlockAndUpdate(pos, Fixtures.block.defaultBlockState());
            BlockEntity blockEntity = level.getBlockEntity(pos);
            check(blockEntity instanceof ProbeMachine, "official-interface fixture registered and placed");
            ProbeMachine machine = (ProbeMachine) blockEntity;
            placed.add(new Placed(level, pos, machine));
            return machine;
        }

        private void cleanup() {
            for (Placed target : placed) {
                if (target.level.getBlockEntity(target.pos) == target.machine) target.level.removeBlock(target.pos, false);
            }
            placed.clear();
        }
    }

    private record Placed(ServerLevel level, BlockPos pos, ProbeMachine machine) { }

    private static ItemStack staff(int speed) {
        ItemStack stack = new ItemStack(ModItems.WONDROUS_STAFF.get());
        stack.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        stack.set(StretcherComponents.RANGE_SIZE_X.get(), 1);
        stack.set(StretcherComponents.RANGE_SIZE_Y.get(), 1);
        stack.set(StretcherComponents.RANGE_SIZE_Z.get(), 1);
        stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), true);
        return stack;
    }

    /** Optional fixture registration must not resolve PBG types when that mod is absent. */
    @EventBusSubscriber(modid = UselessStretcherMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Fixtures {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
                UselessStretcherMod.MODID, "regression_coalesced_machine");
        private static ProbeBlock block;
        private static BlockEntityType<ProbeMachine> type;

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            if (!Boolean.getBoolean("useless_stretcher.regression")
                    || !ModList.get().isLoaded("productivebeesgenesis")) return;
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                block = new ProbeBlock();
                event.register(Registries.BLOCK, ID, () -> block);
            } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
                type = BlockEntityType.Builder.of(ProbeMachine::new, block).build(null);
                event.register(Registries.BLOCK_ENTITY_TYPE, ID, () -> type);
            }
        }
    }

    private static final class ProbeBlock extends Block implements EntityBlock {
        private ProbeBlock() { super(Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ProbeMachine(pos, state); }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            return (world, pos, blockState, target) -> ((ProbeMachine) target).nativeTick();
        }
    }

    public static final class ProbeMachine extends BlockEntity implements IInWorldGridNodeHost,
            com.ayoshiko.productivebeesgenesis.mek.IJdteCentrifugeFactory {
        private int ordinaryCalls;
        private int aeCalls;
        private int accumulateCalls;
        private int flushCalls;
        private int acceptedTicks;
        private int bank;
        private int outputItems;
        private long outputFluidMb;
        private boolean failAccumulateAfterMutation;
        private boolean failFlushAfterMutation;
        private final IGridNode node;

        private ProbeMachine(BlockPos pos, BlockState state) {
            super(Fixtures.type, pos, state);
            IGridTickable tickable = new IGridTickable() {
                @Override public TickingRequest getTickingRequest(IGridNode gridNode) { return new TickingRequest(1, 20, false); }
                @Override public TickRateModulation tickingRequest(IGridNode gridNode, int ticksSinceLastCall) {
                    aeCalls++;
                    return TickRateModulation.URGENT;
                }
            };
            node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getService" -> args[0] == IGridTickable.class ? tickable : null;
                        case "getGrid" -> null;
                        case "isActive", "isOnline", "hasGridBooted", "isPowered", "meetsChannelRequirements" -> false;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "CoalescedRegressionGridNode";
                        default -> throw new AssertionError("unexpected coalesced AE query: " + method.getName());
                    });
        }

        @Override public IGridNode getGridNode(Direction side) { return node; }

        @Override public void productivebeesgenesis$accumulateAcceleratedTicks(int ticks) {
            accumulateCalls++;
            acceptedTicks += ticks;
            bank += ticks;
            if (failAccumulateAfterMutation) {
                failAccumulateAfterMutation = false;
                throw new IllegalStateException("expected regression failure after accumulator mutation");
            }
        }

        @Override public void productivebeesgenesis$flushAcceleratedTicks() {
            flushCalls++;
            commitBank();
            if (failFlushAfterMutation) {
                failFlushAfterMutation = false;
                throw new IllegalStateException("expected regression failure after output mutation");
            }
        }

        private void nativeTick() { ordinaryCalls++; commitBank(); }

        private void commitBank() {
            int work = bank;
            bank = 0;
            outputItems += work;
            outputFluidMb += (long) work * 250L;
        }
    }

    private static final class Compat {
        private static Class<?> type() throws ClassNotFoundException {
            return Class.forName("com.sorrowmist.useless.stretcher.compat.CoalescedMachineAccelerationCompat");
        }

        private static int accumulate(BlockEntity machine, int ticks) throws Exception {
            Object result = invoke(type().getMethod("accumulate", BlockEntity.class, int.class), machine, ticks);
            check(result instanceof Integer, "supported official-interface target returns an accepted count");
            return (Integer) result;
        }

        private static void flush(MinecraftServer server) throws Exception {
            invoke(type().getMethod("flush", MinecraftServer.class), server);
        }

        private static void clear(MinecraftServer server) throws Exception {
            invoke(type().getMethod("clear", MinecraftServer.class), server);
        }

        private static Object invoke(Method method, Object... arguments) throws Exception {
            try {
                return method.invoke(null, arguments);
            } catch (InvocationTargetException wrapped) {
                Throwable failure = wrapped.getCause();
                if (failure instanceof Exception exception) throw exception;
                if (failure instanceof Error error) throw error;
                throw new RuntimeException(failure);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
