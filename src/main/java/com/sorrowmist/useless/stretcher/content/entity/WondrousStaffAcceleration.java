package com.sorrowmist.useless.stretcher.content.entity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Server-side tick driver for the wondrous staff. Blocks tick through their normal block-entity
 * ticker; AE2 machines are ticked through their own {@link IGridTickable} grid service, which is
 * what makes AE machines "run on their own AE tick".
 *
 * <p>Lightning rods are special-cased: vanilla redirects natural thunderstorm lightning to a rod
 * through a rare random roll, so the staff simply rolls that chance faster.
 */
public final class WondrousStaffAcceleration {
    public static final int DEFAULT_DURATION_TICKS = 600;
    /** Vanilla-ish natural lightning rarity; divided by the gear multiplier for the rod. */
    private static final int LIGHTNING_ROD_BASE_CHANCE = 20000;
    /** Never strike faster than once per this many game ticks. */
    private static final int LIGHTNING_ROD_MIN_CHANCE = 10;
    /** The multiplier gear a freshly crafted staff starts with (x2). */
    public static final int DEFAULT_GEAR = 2;
    private static final int RANDOM_TICK_CHANCE = 1365;

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

        BlockPos pos = ctx.getClickedPos().immutable();
        int speed = getSpeed(ctx.getItemInHand());
        boolean permanent = isPermanent(ctx.getItemInHand());
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
            serverLevel.addFreshEntity(created);
        } else {
            effect.setSpeed(speed);
            if (permanent) effect.setPermanent();
            else effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        playUseSound(serverLevel, pos, speed);
        return InteractionResult.sidedSuccess(false);
    }

    /** Accelerates a living entity (baby growth or adult breeding cooldown). Never targets players. */
    public static InteractionResult tryUseEntity(Player player, LivingEntity target) {
        if (target instanceof Player) return InteractionResult.PASS;
        if (!isEnabled(player.getMainHandItem())) return InteractionResult.PASS;
        Level level = target.level();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        AABB area = target.getBoundingBox().inflate(4.0D);
        int speed = getSpeed(player.getMainHandItem());
        boolean permanent = isPermanent(player.getMainHandItem());
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
            serverLevel.addFreshEntity(created);
        } else {
            effect.setSpeed(speed);
            if (permanent) effect.setPermanent();
            else effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        playUseSound(serverLevel, target.blockPosition(), speed);
        return InteractionResult.sidedSuccess(false);
    }

    /** Accelerates world time (the day/night cycle) while the player looks at the sky. */
    public static InteractionResult tryUseTime(Player player) {
        Level level = player.level();
        if (!isEnabled(player.getMainHandItem())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        int speed = getSpeed(player.getMainHandItem());
        List<WondrousStaffAccelerationEntity> existing = serverLevel.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                player.getBoundingBox().inflate(24.0D),
                WondrousStaffAccelerationEntity::isTimeMode);
        WondrousStaffAccelerationEntity effect = existing.stream().findFirst().orElse(null);
        if (speed <= 0) {
            if (effect != null) effect.discard();
            return InteractionResult.sidedSuccess(false);
        }
        if (effect == null) {
            WondrousStaffAccelerationEntity created = new WondrousStaffAccelerationEntity(
                    serverLevel, player.blockPosition(), speed, true);
            // Sun/moon acceleration is blacklisted from permanent mode: always 30s.
            serverLevel.addFreshEntity(created);
        } else {
            effect.setSpeed(speed);
            effect.setRemainingTime(DEFAULT_DURATION_TICKS);
        }
        playUseSound(serverLevel, player.blockPosition(), speed);
        return InteractionResult.sidedSuccess(false);
    }

    public static int getSpeed(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), DEFAULT_GEAR);
    }

    public static boolean isPermanent(ItemStack stack) {
        return stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), false);
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
     * 取消挂在 {@code pos} 上的加速（如果有）。用于"手杖总开关已经关掉、但存档里还留着
     * 一个（通常是永久）加速"的情况——否则玩家再也没有办法把它关掉。
     *
     * <p>返回 true 表示确实找到了加速并已移除；客户端只做存在性判断（返回是否有），
     * 真正的移除在服务端发生。
     */
    public static boolean cancelEffectAt(Level level, BlockPos pos) {
        BlockPos target = pos.immutable();
        List<WondrousStaffAccelerationEntity> existing = level.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                new AABB(target),
                entity -> entity.getTargetPos().equals(target));
        if (existing.isEmpty()) return false;
        if (level instanceof ServerLevel) {
            existing.forEach(WondrousStaffAccelerationEntity::discard);
        }
        return true;
    }

    /** Ticks a block or AE node {@code speed} extra times. */
    public static void tickTarget(ServerLevel level, BlockPos pos, int speed) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof LightningRodBlock) {
            tickLightningRod(level, pos, speed);
            return;
        }

        if (blockEntity == null) {
            if (!state.isRandomlyTicking()) return;
            RandomSource random = level.getRandom();
            for (int i = 0; i < speed; i++) {
                if (random.nextInt(RANDOM_TICK_CHANCE) == 0) {
                    state.randomTick(level, pos, random);
                }
            }
            return;
        }

        // 1. AE2 machines that expose an IGridTickable run on their own AE grid tick.
        if (blockEntity instanceof IInWorldGridNodeHost host && tickAeNode(host, speed)) {
            return;
        }

        // 2. Everything else uses its normal block-entity ticker. This fallback matters for
        //    AE machines (e.g. AE2 Crystal Science) that are grid node hosts but do NOT
        //    implement IGridTickable — they would otherwise never be accelerated.
        @SuppressWarnings("rawtypes")
        BlockEntityTicker ticker = state.getTicker(level, blockEntity.getType());
        if (ticker == null) return;
        for (int i = 0; i < speed; i++) {
            //noinspection unchecked
            ticker.tick(level, pos, state, blockEntity);
        }
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

    /** @return true when an active {@link IGridTickable} was found and ticked. */
    private static boolean tickAeNode(IInWorldGridNodeHost host, int speed) {
        for (Direction direction : Direction.values()) {
            IGridNode node = host.getGridNode(direction);
            if (node == null || node.getGrid() == null || !node.isActive()) continue;
            IGridTickable tickable = node.getService(IGridTickable.class);
            if (tickable == null) continue;
            for (int i = 0; i < speed; i++) {
                try {
                    tickable.tickingRequest(node, 1);
                } catch (RuntimeException ignored) {
                    // A device may reject an out-of-band tick (e.g. while not loaded/active).
                    break;
                }
            }
            return true;
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
}
