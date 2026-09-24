package com.sorrowmist.useless.stretcher.content.entity;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.item.StaffTutorialData;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Server-side tick driver for the wondrous staff. Blocks tick through their normal block-entity
 * ticker; AE2 machines are ticked through their own {@link IGridTickable} grid service, which is
 * what makes AE machines "run on their own AE tick".
 *
 * <p>Lightning rods are special-cased: vanilla redirects natural thunderstorm lightning to a rod
 * through a rare random roll, so the staff simply rolls that chance faster.
 */
public final class WondrousStaffAcceleration {
    public static final int STAFF_MODE_NORMAL = 0;
    public static final int STAFF_MODE_PERMANENT = 1;
    public static final int STAFF_MODE_PERMANENT_NO_THROTTLE = 2;
    public static final int STAFF_MODE_COUNT = 3;

    public static final int DEFAULT_DURATION_TICKS = 600;
    /** Vanilla-ish natural lightning rarity; divided by the gear multiplier for the rod. */
    private static final int LIGHTNING_ROD_BASE_CHANCE = 20000;
    /** Never strike faster than once per this many game ticks. */
    private static final int LIGHTNING_ROD_MIN_CHANCE = 10;
    /** The multiplier gear a freshly crafted staff starts with (x2). */
    public static final int DEFAULT_GEAR = 2;
    /** Vanilla's private ServerLevel.THUNDER_DELAY provider. */
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    private static final Direction[] DIRECTIONS = Direction.values();

    private WondrousStaffAcceleration() {
    }

    /** The inherited G-menu "时间加速" toggle is the master switch for this staff. */
    public static boolean isEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
    }

    public static InteractionResult tryUse(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!isEnabled(ctx.getItemInHand())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        sendAccelerationTutorial(player);

        BlockPos pos = ctx.getClickedPos().immutable();
        int speed = getSpeed(ctx.getItemInHand());
        int staffMode = getMode(ctx.getItemInHand());
        boolean permanent = isPermanentMode(staffMode);
        boolean noIdleThrottle = skipsIdleThrottle(staffMode);
        List<WondrousStaffAccelerationEntity> existing = serverLevel.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                new AABB(pos),
                entity -> entity.getTargetPos().equals(pos));
        WondrousStaffAccelerationEntity effect = existing.stream().findFirst().orElse(null);
        if (speed <= 0) {
            if (effect != null) effect.discard();
            return InteractionResult.sidedSuccess(false);
        }
        if (effect == null) {
            WondrousStaffAccelerationEntity created =
                    new WondrousStaffAccelerationEntity(serverLevel, pos, speed);
            if (permanent) created.setPermanent();
            created.setIdleThrottleDisabled(noIdleThrottle);
            serverLevel.addFreshEntity(created);
        } else {
            effect.setSpeed(speed);
            effect.setIdleThrottleDisabled(noIdleThrottle);
            if (permanent) effect.setPermanent();
            else effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        playUseSound(serverLevel, pos, speed);
        return InteractionResult.sidedSuccess(false);
    }

    /** Accelerates an entity. Never targets players or an acceleration marker itself. */
    public static InteractionResult tryUseEntity(Player player, Entity target, ItemStack staff) {
        if (target == null || target instanceof Player || target instanceof WondrousStaffAccelerationEntity
                || target.isRemoved()) return InteractionResult.PASS;
        if (!isEnabled(staff)) return InteractionResult.PASS;
        Level level = target.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        sendAccelerationTutorial(player);

        AABB area = target.getBoundingBox().inflate(4.0D);
        int speed = getSpeed(staff);
        int staffMode = getMode(staff);
        boolean permanent = isPermanentMode(staffMode);
        boolean noIdleThrottle = skipsIdleThrottle(staffMode);
        List<WondrousStaffAccelerationEntity> existing = serverLevel.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                area,
                entity -> target.getUUID().equals(entity.getTargetUuid()));
        WondrousStaffAccelerationEntity effect = existing.stream().findFirst().orElse(null);
        if (speed <= 0) {
            if (effect != null) effect.discard();
            return InteractionResult.sidedSuccess(false);
        }
        if (effect == null) {
            WondrousStaffAccelerationEntity created =
                    new WondrousStaffAccelerationEntity(serverLevel, target, speed);
            if (permanent) created.setPermanent();
            created.setIdleThrottleDisabled(noIdleThrottle);
            created.setEntityAiDisabled(StretcherConfig.entityDisableAi());
            serverLevel.addFreshEntity(created);
        } else {
            effect.setSpeed(speed);
            effect.setIdleThrottleDisabled(noIdleThrottle);
            effect.setEntityAiDisabled(StretcherConfig.entityDisableAi());
            if (permanent) effect.setPermanent();
            else effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        playUseSound(serverLevel, target.blockPosition(), speed);
        return InteractionResult.sidedSuccess(false);
    }

    /** Accelerates the dimension's day/night and weather cycles while looking at the sky. */
    public static InteractionResult tryUseTime(Player player, ItemStack staff) {
        Level level = player.level();
        if (!isEnabled(staff)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        sendAccelerationTutorial(player);

        int speed = getSpeed(staff);
        // Daytime and weather are dimension-wide, so distant players must update one shared effect
        // instead of accidentally stacking several global clocks.
        WondrousStaffAccelerationEntity effect = null;
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof WondrousStaffAccelerationEntity candidate && candidate.isTimeMode()) {
                effect = candidate;
                break;
            }
        }
        if (speed <= 0) {
            if (effect != null) effect.discard();
            else Network.sendTimeAccelerationState(serverLevel, 0);
            return InteractionResult.sidedSuccess(false);
        }
        if (effect == null) {
            WondrousStaffAccelerationEntity created = new WondrousStaffAccelerationEntity(
                    serverLevel, player.blockPosition(), speed, true);
            // Sun/moon acceleration is blacklisted from permanent mode: always 30s.
            serverLevel.addFreshEntity(created);
        } else {
            effect.setTargetPos(player.blockPosition());
            effect.setPos(player.position());
            effect.setSpeed(speed);
            effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        Network.sendTimeAccelerationState(serverLevel, speed);
        playUseSound(serverLevel, player.blockPosition(), speed);
        return InteractionResult.sidedSuccess(false);
    }

    public static int getSpeed(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), DEFAULT_GEAR);
    }

    public static boolean isAutoSmeltEnabled(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_AUTO_SMELT.get(), false);
    }

    public static boolean isSummonEnabled(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), false);
    }

    public static boolean isLootRefreshEnabled(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_LOOT_REFRESH.get(), false);
    }

    /**
     * Reads the new three-state mode, falling back to the old boolean component for existing
     * stacks made before the mode key was added.
     */
    public static int getMode(ItemStack stack) {
        Integer mode = stack.get(StretcherComponents.WONDROUS_STAFF_MODE.get());
        if (mode != null) return normalizeMode(mode);
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), false)
                ? STAFF_MODE_PERMANENT : STAFF_MODE_NORMAL;
    }

    /** Writes both the new mode and the legacy boolean so old stacks remain interoperable. */
    public static void setMode(ItemStack stack, int mode) {
        int normalized = normalizeMode(mode);
        stack.set(StretcherComponents.WONDROUS_STAFF_MODE.get(), normalized);
        stack.set(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), isPermanentMode(normalized));
    }

    public static boolean isPermanent(ItemStack stack) {
        return isPermanentMode(getMode(stack));
    }

    public static boolean skipsIdleThrottle(ItemStack stack) {
        return skipsIdleThrottle(getMode(stack));
    }

    public static boolean isPermanentMode(int mode) {
        return normalizeMode(mode) != STAFF_MODE_NORMAL;
    }

    public static boolean skipsIdleThrottle(int mode) {
        // Normal mode is intentionally also full-speed: its 30-second lifetime already limits
        // the total cost. Only the explicitly named permanent mode uses dynamic idle throttling.
        return normalizeMode(mode) != STAFF_MODE_PERMANENT;
    }

    private static int normalizeMode(int mode) {
        return mode >= STAFF_MODE_NORMAL && mode < STAFF_MODE_COUNT ? mode : STAFF_MODE_NORMAL;
    }

    /**
     * True only when the player's crosshair is on the sun or moon with an unobstructed
     * line of sight (not merely "looking at the sky").
     */
    public static boolean isLookingAtCelestial(Level level, Player player) {
        HitResult hit = player.pick(256.0D, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 sun = sunDirection(level);
        return angleDegrees(look, sun) < 8.0D || angleDegrees(look, sun.scale(-1.0D)) < 8.0D;
    }

    /**
     * World direction to the sun, matching vanilla's sky rendering exactly:
     * {@code (-sin(sunAngle), cos(sunAngle), 0)}. Overhead at noon, below at midnight.
     */
    public static Vec3 sunDirection(Level level) {
        double theta = level.getSunAngle(1.0F);
        return new Vec3(-Math.sin(theta), Math.cos(theta), 0.0D).normalize();
    }

    private static double angleDegrees(Vec3 a, Vec3 b) {
        double dot = a.dot(b) / (a.length() * b.length());
        return Math.toDegrees(Math.acos(Math.clamp(dot, -1.0D, 1.0D)));
    }

    /**
     * True when the block can actually be accelerated: it either has a block-entity ticker, is
     * an AE grid node host, or randomly ticks (e.g. grass blocks and crops). Static blocks like
     * dirt or stone return false.
     */
    public static boolean isValidTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LightningRodBlock) return true;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            if (blockEntity instanceof IInWorldGridNodeHost) return true;
            return state.getTicker(level, blockEntity.getType()) != null;
        }
        return state.isRandomlyTicking();
    }

    /**
     * Advances only vanilla's weather state machine. This deliberately does not touch game time,
     * scheduled ticks, chunks, block entities or entities. Replaying the small state machine keeps
     * clear/rain/thunder durations and transitions identical to vanilla while coalescing all
     * client updates into at most one final packet of each kind per real server tick.
     */
    public static void tickWeather(ServerLevel level, int extraTicks) {
        int steps = Math.max(0, Math.min(WondrousStaffAccelerationEntity.MAX_MULTIPLIER, extraTicks));
        if (steps == 0 || !level.dimensionType().hasSkyLight()) return;
        if (!(level.getLevelData() instanceof ServerLevelData data)) return;

        boolean wasVisiblyRaining = level.isRaining();
        float oldRainLevel = level.rainLevel;
        float oldThunderLevel = level.thunderLevel;
        boolean weatherCycle = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);

        int clearTime = data.getClearWeatherTime();
        int thunderTime = data.getThunderTime();
        int rainTime = data.getRainTime();
        boolean thundering = data.isThundering();
        boolean raining = data.isRaining();

        for (int tick = 0; tick < steps; tick++) {
            if (weatherCycle) {
                if (clearTime > 0) {
                    clearTime--;
                    thunderTime = thundering ? 0 : 1;
                    rainTime = raining ? 0 : 1;
                    thundering = false;
                    raining = false;
                } else {
                    if (thunderTime > 0) {
                        if (--thunderTime == 0) thundering = !thundering;
                    } else {
                        thunderTime = (thundering ? ServerLevel.THUNDER_DURATION : THUNDER_DELAY)
                                .sample(level.random);
                    }

                    if (rainTime > 0) {
                        if (--rainTime == 0) raining = !raining;
                    } else {
                        rainTime = (raining ? ServerLevel.RAIN_DURATION : ServerLevel.RAIN_DELAY)
                                .sample(level.random);
                    }
                }
            }

            level.oThunderLevel = level.thunderLevel;
            level.thunderLevel = Math.clamp(level.thunderLevel + (thundering ? 0.01F : -0.01F), 0.0F, 1.0F);
            level.oRainLevel = level.rainLevel;
            level.rainLevel = Math.clamp(level.rainLevel + (raining ? 0.01F : -0.01F), 0.0F, 1.0F);
        }

        data.setClearWeatherTime(clearTime);
        data.setThunderTime(thunderTime);
        data.setRainTime(rainTime);
        data.setThundering(thundering);
        data.setRaining(raining);

        boolean visiblyRaining = level.isRaining();
        if (wasVisiblyRaining != visiblyRaining) {
            ClientboundGameEventPacket.Type type = visiblyRaining
                    ? ClientboundGameEventPacket.START_RAINING
                    : ClientboundGameEventPacket.STOP_RAINING;
            level.getServer().getPlayerList().broadcastAll(new ClientboundGameEventPacket(type, 0.0F),
                    level.dimension());
        }
        if (oldRainLevel != level.rainLevel || wasVisiblyRaining != visiblyRaining) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.rainLevel),
                    level.dimension());
        }
        if (oldThunderLevel != level.thunderLevel || wasVisiblyRaining != visiblyRaining) {
            level.getServer().getPlayerList().broadcastAll(
                    new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.thunderLevel),
                    level.dimension());
        }
    }

    /** Ticks a block or AE node {@code speed} extra times. */
    public static int tickTarget(ServerLevel level, BlockPos pos, int speed) {
        long started = System.nanoTime();
        long deadline = com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget.deadline(level.getServer());
        try {
            return tickTargetWithinBudget(level, pos, speed, deadline);
        } finally {
            com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget.recordWork(level.getServer(), started);
        }
    }

    private static int tickTargetWithinBudget(ServerLevel level, BlockPos pos, int speed, long deadline) {
        if (System.nanoTime() >= deadline) return 0;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof LightningRodBlock) {
            tickLightningRod(level, pos, speed);
            return speed;
        }

        if (blockEntity == null) {
            if (!state.isRandomlyTicking()) return speed;
            int executed = 0;
            for (; executed < speed && System.nanoTime() < deadline; executed++) {
                // One requested virtual tick means one randomTick invocation, matching JDTE's
                // Ultimate Time Wand. Applying vanilla's section-level 1/1365 selection chance a
                // second time made crops/saplings roughly 1365 times slower than JDTE at x1024.
                BlockState current = level.getBlockState(pos);
                if (!current.isRandomlyTicking()) return speed;
                current.randomTick(level, pos, level.getRandom());
            }
            return executed;
        }

        // 1. AE2 machines that expose IGridTickable endpoints run on their own AE grid ticks.
        int aeExecuted = tickAeNodes(level, pos, speed, deadline);
        if (aeExecuted >= 0) return aeExecuted;

        // 2. Everything else uses its normal block-entity ticker. This fallback matters for
        //    AE machines (e.g. AE2 Crystal Science) that are grid node hosts but do NOT
        //    implement IGridTickable — they would otherwise never be accelerated.
        @SuppressWarnings("rawtypes")
        BlockEntityTicker ticker = state.getTicker(level, blockEntity.getType());
        if (ticker == null) return speed;
        int executed = 0;
        for (; executed < speed && System.nanoTime() < deadline; executed++) {
            if (blockEntity.isRemoved() || level.getBlockState(pos) != state) return executed;
            //noinspection unchecked
            ticker.tick(level, pos, state, blockEntity);
        }
        return executed;
    }

    /**
     * Accelerates a lightning rod's natural thunderstorm lightning: while it is thundering and
     * the rod sits on the world surface (the same condition vanilla uses for its electric spark
     * particles), roll the lightning chance {@code speed} times faster and strike the rod with a
     * real {@link LightningBolt}.
     */
    private static void tickLightningRod(ServerLevel level, BlockPos pos, int speed) {
        if (!level.isThundering() || !level.canSeeSky(pos)) return;
        if (pos.getY() != level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1) {
            return;
        }
        int chance = Math.max(LIGHTNING_ROD_MIN_CHANCE, LIGHTNING_ROD_BASE_CHANCE / Math.max(1, speed));
        if (level.getRandom().nextInt(chance) != 0) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(pos.above()));
        level.addFreshEntity(bolt);
    }

    /**
     * Ticks every distinct active AE endpoint exposed by this block. A multipart host may return
     * the same node from several faces, so identity de-duplication is required. An endpoint that
     * reports {@link TickRateModulation#SLEEP} is removed immediately instead of receiving up to
     * another 1023 empty calls during the same real server tick.
     *
     * @return consumed virtual ticks, or -1 when no {@link IGridTickable} endpoint was found
     */
    private static int tickAeNodes(ServerLevel level, BlockPos pos, int speed, long deadline) {
        IInWorldGridNodeHost host = GridHelper.getNodeHost(level, pos);
        if (host == null) return -1;

        Set<IGridNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<AeEndpoint> active = new ArrayList<>();
        for (Direction direction : DIRECTIONS) {
            IGridNode node = host.getGridNode(direction);
            // A number of AE-compatible machines run entirely on FE until they are connected
            // to an AE network. Their node has no grid in that state, but the public AE2
            // IGridTickable service is still the machine's real tick entrypoint.
            if (node == null || !seen.add(node)) continue;
            IGridTickable tickable = node.getService(IGridTickable.class);
            if (tickable != null) active.add(new AeEndpoint(node, tickable));
        }
        if (active.isEmpty()) return -1;

        int executed = 0;
        for (; executed < speed && !active.isEmpty() && System.nanoTime() < deadline; executed++) {
            Iterator<AeEndpoint> iterator = active.iterator();
            while (iterator.hasNext()) {
                AeEndpoint endpoint = iterator.next();
                try {
                    TickRateModulation modulation = endpoint.tickable.tickingRequest(endpoint.node, 1);
                    if (modulation == TickRateModulation.SLEEP) iterator.remove();
                } catch (RuntimeException ignored) {
                    // A device may reject an out-of-band tick (e.g. while not loaded/active).
                    // Remove only that endpoint; other faces/nodes can continue normally.
                    iterator.remove();
                }
            }
        }
        return active.isEmpty() ? speed : executed;
    }

    private record AeEndpoint(IGridNode node, IGridTickable tickable) {
    }

    /** True when an AE device currently requests ticks instead of reporting itself asleep. */
    public static boolean isAeDeviceWorking(IInWorldGridNodeHost host) {
        for (Direction direction : DIRECTIONS) {
            IGridNode node = host.getGridNode(direction);
            if (node == null || node.getGrid() == null || !node.isActive()) continue;
            IGridTickable tickable = node.getService(IGridTickable.class);
            if (tickable == null) continue;
            try {
                if (node.getGrid().getTickManager() instanceof TickManagerService manager) {
                    if (!manager.getStatus(node).sleeping()) return true;
                    continue;
                }
                if (!tickable.getTickingRequest(node).isSleeping()) return true;
            } catch (RuntimeException ignored) {
                // If a device cannot expose its state safely, keep it at full speed.
                return true;
            }
        }
        return false;
    }

    /** Mirrors Useless Mod's own time-acceleration sound, pitched by the multiplier gear. */
    private static void playUseSound(ServerLevel level, BlockPos pos, int multiplier) {
        int levelIndex = Integer.numberOfTrailingZeros(multiplier);
        if (levelIndex < 1) levelIndex = 1;
        float pitch = (float) Math.pow(2.0D, (levelIndex - 5) / 12.0D);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(),
                SoundSource.PLAYERS, 1.0F, pitch);
    }

    private static void sendAccelerationTutorial(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && StaffTutorialData.get(serverPlayer.getServer()).markHintShown(serverPlayer.getUUID(),
                StaffTutorialData.HINT_ACCELERATION)) {
            Network.sendStaffTutorial(serverPlayer);
        }
    }
}
