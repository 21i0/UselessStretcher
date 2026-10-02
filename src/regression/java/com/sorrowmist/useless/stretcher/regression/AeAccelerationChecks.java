package com.sorrowmist.useless.stretcher.regression;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.AEBaseBlockEntity;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public final class AeAccelerationChecks {
    static void run(ServerLevel level) throws Exception {
        realEntryHybrid(level);
        BlockPos pos = new BlockPos(12, 240, 12);
        level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        try {
            var working = new CountingTickable(TickRateModulation.URGENT);
            IGridNode offlineNode = node(working, false, false);
            IInWorldGridNodeHost allFaces = side -> offlineNode;
            check(endpoints(allFaces).size() == 1, "primary and six faces deduplicate by node identity");
            check(tick(level, pos, blockEntity, state, null, allFaces, 32, Long.MAX_VALUE) == 32
                            && working.calls == 32 && working.elapsedTicks == 32,
                    "FE-only node runs without a grid or online state; elapsed ticks stay target-local");

            working.reset();
            IInWorldGridNodeHost nonNullableHost = side -> {
                if (side == null || side == Direction.DOWN) throw new IllegalArgumentException("bad face");
                return offlineNode;
            };
            check(tick(level, pos, blockEntity, state, null, nonNullableHost, 32, Long.MAX_VALUE) == 32
                            && working.calls == 32,
                    "optional primary and broken side never hide healthy faces");
            check(!WondrousStaffAcceleration.isAeDeviceWorking(nonNullableHost),
                    "offline activity probe survives non-nullable host");

            working.reset();
            IGridNode brokenService = node(working, true, false);
            IInWorldGridNodeHost faultyService = side -> side == null ? brokenService : offlineNode;
            check(endpoints(faultyService).size() == 1,
                    "throwing service lookup only excludes its own node");
            IGridNode brokenMetadata = node(working, false, true);
            check(WondrousStaffAcceleration.isAeDeviceWorking(side -> brokenMetadata),
                    "throwing grid metadata keeps unknown activity at full speed");

            int[] ordinaryCalls = {0};
            BlockEntityTicker<BlockEntity> ordinary = (world, location, blockState, target) -> ordinaryCalls[0]++;
            check(tick(level, pos, blockEntity, state, ordinary, allFaces, 32, Long.MAX_VALUE) == 32
                            && ordinaryCalls[0] == 32 && working.calls == 32,
                    "active ME upgrade cannot replace ordinary machine production");

            entryRoutePolicy(level, pos, blockEntity, state, ordinary, ordinaryCalls, working, allFaces);

            ordinaryCalls[0] = 0;
            var sleeping = new CountingTickable(TickRateModulation.SLEEP);
            IGridNode sleepingNode = node(sleeping, false, false);
            check(tick(level, pos, blockEntity, state, ordinary, side -> sleepingNode, 32, Long.MAX_VALUE) == 32
                            && sleeping.calls == 1 && ordinaryCalls[0] == 32,
                    "SLEEP completion is not replayed and never stops the normal ticker");
            sleeping.reset();
            check(tick(level, pos, blockEntity, state, null, side -> sleepingNode, 32, Long.MAX_VALUE) == 32
                            && sleeping.calls == 1,
                    "all sleeping endpoints consume idle virtual time without accumulating backlog");

            var rejected = new CountingTickable(null);
            IGridNode rejectedNode = node(rejected, false, false);
            working.reset();
            ordinaryCalls[0] = 0;
            IInWorldGridNodeHost partialFailure = side -> side == null ? rejectedNode : offlineNode;
            check(tick(level, pos, blockEntity, state, ordinary, partialFailure, 32, Long.MAX_VALUE) == 32
                            && rejected.calls == 1 && working.calls == 32 && ordinaryCalls[0] == 32,
                    "rejected AE callback leaves other nodes and ordinary production running");

            working.reset();
            ordinaryCalls[0] = 0;
            check(tick(level, pos, blockEntity, state, ordinary, allFaces, 32, 0) == 0
                            && working.calls == 0 && ordinaryCalls[0] == 0,
                    "expired deadline prevents both callback paths");
            BlockEntityTicker<BlockEntity> replacement = (world, location, blockState, target) ->
                    world.setBlockAndUpdate(location, Blocks.STONE.defaultBlockState());
            check(tick(level, pos, blockEntity, state, replacement, allFaces, 32, Long.MAX_VALUE) == 1
                            && working.calls == 0,
                    "block replacement stops stale AE callbacks immediately");
        } finally {
            level.removeBlock(pos, false);
        }
        LogUtils.getLogger().info("REGRESSION AE acceleration: offline FE, deduplication, hybrid tickers, SLEEP, exception isolation and deadline passed");
    }

    private static void realEntryHybrid(ServerLevel level) {
        BlockPos pos = new BlockPos(13, 240, 12);
        level.setBlockAndUpdate(pos, Fixtures.block.defaultBlockState());
        var machine = (HybridBlockEntity) level.getBlockEntity(pos);
        try {
            check(machine != null, "regression hybrid AE block exists in the real world");
            Object budgetKey = new Object();
            // Warm the public route before asserting a full batch under its wall-clock guard.
            AccelerationExecutionBudget.beginTick(level.getServer());
            WondrousStaffAcceleration.tickTarget(level, pos, 1, budgetKey);
            machine.ordinaryCalls = 0;
            machine.ae.reset();
            AccelerationExecutionBudget.beginTick(level.getServer());
            int executed = WondrousStaffAcceleration.tickTarget(level, pos, 16, budgetKey);
            check(executed == 16 && machine.ordinaryCalls == 16 && machine.ae.calls == 16,
                    "public target entry preserves both production paths for unknown AEBaseBlockEntity");
        } finally {
            level.removeBlock(pos, false);
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    @EventBusSubscriber(modid = UselessStretcherMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Fixtures {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
                UselessStretcherMod.MODID, "regression_ae_hybrid");
        private static HybridBlock block;
        private static BlockEntityType<HybridBlockEntity> type;

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            if (!Boolean.getBoolean("useless_stretcher.regression")) return;
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                block = new HybridBlock();
                event.register(Registries.BLOCK, ID, () -> block);
            } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
                type = BlockEntityType.Builder.of(HybridBlockEntity::new, block).build(null);
                event.register(Registries.BLOCK_ENTITY_TYPE, ID, () -> type);
            }
        }
    }

    private static final class HybridBlock extends Block implements EntityBlock {
        private HybridBlock() { super(Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new HybridBlockEntity(pos, state);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            return (world, pos, blockState, target) -> ((HybridBlockEntity) target).ordinaryCalls++;
        }
    }

    private static final class HybridBlockEntity extends AEBaseBlockEntity implements IInWorldGridNodeHost {
        private final CountingTickable ae = new CountingTickable(TickRateModulation.URGENT);
        private final IGridNode node = node(ae, false, false);
        private int ordinaryCalls;

        private HybridBlockEntity(BlockPos pos, BlockState state) { super(Fixtures.type, pos, state); }
        @Override public IGridNode getGridNode(Direction side) { return node; }
        @Override public void onLoad() { }
    }

    private static List<?> endpoints(IInWorldGridNodeHost host) throws Exception {
        Method method = WondrousStaffAcceleration.class.getDeclaredMethod("findAeEndpoints", IInWorldGridNodeHost.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, host);
    }

    private static void entryRoutePolicy(ServerLevel level, BlockPos pos, BlockEntity blockEntity,
                                          BlockState state, BlockEntityTicker<?> ordinary,
                                          int[] ordinaryCalls, CountingTickable working,
                                          IInWorldGridNodeHost host) throws Exception {
        for (String id : List.of("ae2lt:lightning_simulation_room", "ae2lt:lightning_assembly_chamber",
                "ae2lt:overload_processing_factory")) {
            working.reset();
            ordinaryCalls[0] = 0;
            var selected = selectOrdinaryTicker(id, ordinary, true);
            check(selected == null, "confirmed AE-only recipe machine skips maintenance: " + id);
            check(tick(level, pos, blockEntity, state, selected, host, 32, Long.MAX_VALUE) == 32
                            && working.calls == 32 && ordinaryCalls[0] == 0,
                    "entry routing executes AE production without repeated maintenance: " + id);

            ordinaryCalls[0] = 0;
            selected = selectOrdinaryTicker(id, ordinary, false);
            check(selected == ordinary, "missing AE endpoint retains ordinary ticker: " + id);
            check(tick(level, pos, blockEntity, state, selected, side -> null, 32, Long.MAX_VALUE) == 32
                            && ordinaryCalls[0] == 32,
                    "entry routing falls back to ordinary work with no AE endpoint: " + id);
        }
        for (String id : List.of("ae2lt:overloaded_interface", "ae2lt:overloaded_pattern_provider",
                "ae2lt:pigmee_crystal_catalyzer", "ae2cs:crystal_pulverizer", "ae2cs:crystal_growth_chamber",
                "unknown_ae_addon:hybrid_machine", "other_mod:lightning_simulation_room")) {
            working.reset();
            ordinaryCalls[0] = 0;
            var selected = selectOrdinaryTicker(id, ordinary, true);
            check(selected == ordinary, "unverified AE/addon ticker is preserved: " + id);
            check(tick(level, pos, blockEntity, state, selected, host, 32, Long.MAX_VALUE) == 32
                            && working.calls == 32 && ordinaryCalls[0] == 32,
                    "entry policy preserves both independent work paths: " + id);
        }
        working.reset();
        ordinaryCalls[0] = 0;
    }

    private static BlockEntityTicker<?> selectOrdinaryTicker(String id, BlockEntityTicker<?> ticker,
                                                             boolean hasAeEndpoints) throws Exception {
        Method method = WondrousStaffAcceleration.class.getDeclaredMethod("selectOrdinaryTicker",
                ResourceLocation.class, BlockEntityTicker.class, boolean.class);
        method.setAccessible(true);
        return (BlockEntityTicker<?>) method.invoke(null, ResourceLocation.parse(id), ticker, hasAeEndpoints);
    }

    private static int tick(ServerLevel level, BlockPos pos, BlockEntity blockEntity, BlockState state,
                            BlockEntityTicker<?> ticker, IInWorldGridNodeHost host, int speed,
                            long deadline) throws Exception {
        Method method = WondrousStaffAcceleration.class.getDeclaredMethod("tickBlockAndAeNodes", ServerLevel.class,
                BlockPos.class, BlockEntity.class, BlockState.class, BlockEntityTicker.class,
                List.class, int.class, long.class);
        method.setAccessible(true);
        return (int) method.invoke(null, level, pos, blockEntity, state, ticker, endpoints(host), speed, deadline);
    }

    private static IGridNode node(IGridTickable tickable, boolean failService, boolean failMetadata) {
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getService" -> {
                        if (failService) throw new IllegalStateException("service unavailable");
                        yield args[0] == IGridTickable.class ? tickable : null;
                    }
                    case "getGrid" -> {
                        if (failMetadata) throw new IllegalStateException("grid unavailable");
                        yield null;
                    }
                    case "isActive", "isOnline", "hasGridBooted", "isPowered", "meetsChannelRequirements" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "RegressionGridNode";
                    default -> throw new AssertionError("unexpected AE node query: " + method.getName());
                });
    }

    private static final class CountingTickable implements IGridTickable {
        private final TickRateModulation result;
        private int calls;
        private int elapsedTicks;

        private CountingTickable(TickRateModulation result) { this.result = result; }
        private void reset() { calls = 0; elapsedTicks = 0; }
        @Override public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(1, 20, false);
        }
        @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            calls++;
            elapsedTicks += ticksSinceLastCall;
            if (result == null) throw new IllegalStateException("out-of-band tick rejected");
            return result;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
