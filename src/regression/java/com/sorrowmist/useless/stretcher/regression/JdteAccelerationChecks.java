package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real registered third-party clocks and real range/legacy-marker paths; no shipped test stubs. */
final class JdteAccelerationChecks {
    static void run(ServerLevel level) throws Exception {
        BlockPos pos = new BlockPos(64, 160, 64);
        level.getChunkAt(pos);
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 16));
        furnace.setItem(1, new ItemStack(Items.COAL, 16));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "JdteCarrierChecks"));
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_SPEED.get(), 1024);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_X.get(), 3);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_Y.get(), 3);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_Z.get(), 3);
        var ranges = RangeAccelerationSavedData.get(level.getServer());
        UUID fieldId = ranges.place(level, player, pos, staff).id();
        check(fieldId != null, "real range created");
        var carriers = new ArrayList<Entity>();
        var lifetimes = new ArrayList<Integer>();
        var types = List.of("jdte:time_accelerator_effect", "jdte:ultimate_time_wand", "justdirethings:time_wand_entity");
        var ordinary = new ItemEntity(level, pos.getX() + .5, pos.getY() + .25, pos.getZ() + .5, new ItemStack(Items.STONE));
        ordinary.setNoGravity(true);
        level.addFreshEntity(ordinary);
        long gameTime = level.getGameTime(), dayTime = level.getDayTime();
        try {
            for (String id : types) {
                var key = new ResourceLocation(id);
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) continue;
                Entity carrier = create(id, level, pos);
                carriers.add(carrier);
                check(level.addFreshEntity(carrier), "real carrier spawned: " + id);
                int remaining = remaining(carrier);
                lifetimes.add(remaining);
                check(!WondrousStaffAcceleration.isEntityTargetAllowed(carrier), "shared blacklist: " + id);
                check(WondrousStaffAcceleration.tryUseEntity(player, carrier, staff) == InteractionResult.FAIL,
                        "direct click rejected without touching carrier: " + id);
                // Load real old NBT, both an independent permanent marker and a field-owned marker.
                for (boolean range : new boolean[]{false, true}) {
                    var old = new WondrousStaffAccelerationEntity(level, carrier, 1024);
                    old.setPermanent();
                    old.setOwnerUuid(player.getUUID());
                    if (range) old.setRangeFieldUuid(fieldId);
                    CompoundTag tag = new CompoundTag();
                    check(old.save(tag), "legacy marker serialized");
                    old.discard();
                    Entity loaded = EntityType.loadEntityRecursive(tag, level, entity -> entity);
                    check(loaded instanceof WondrousStaffAccelerationEntity, "legacy marker restored");
                    level.addFreshEntity(loaded);
                    loaded.tick();
                    check(loaded.isRemoved() && !carrier.isRemoved(), "only our old marker removed: " + id);
                    check(remaining(carrier) == remaining, "legacy cleanup preserves third-party clock: " + id);
                }
            }
            int expected = (ModList.get().isLoaded("jdte") ? 2 : 0)
                    + (ModList.get().isLoaded("justdirethings") ? 1 : 0);
            check(carriers.size() == expected, "all expected real registry IDs covered");
            int age = ordinary.getAge();
            // Several high-speed scans/work batches must never spend a third-party lifetime.
            for (int i = 0; i < 3; i++) {
                AccelerationExecutionBudget.removeServer(level.getServer());
                AccelerationExecutionBudget.beginTick(level.getServer());
                ranges.tick(level.getServer());
                for (var marker : level.getEntitiesOfClass(WondrousStaffAccelerationEntity.class, new AABB(pos).inflate(4))) {
                    if (!fieldId.equals(marker.getRangeFieldUuid())) continue;
                    check(carriers.stream().noneMatch(c -> c.getUUID().equals(marker.getTargetUuid())),
                            "range never creates a carrier target");
                    marker.tick();
                }
            }
            check(ordinary.getAge() > age, "ordinary dropped item remains accelerated");
            check(!furnace.getItem(2).isEmpty(), "real furnace remains accelerated in same range");
            for (int i = 0; i < carriers.size(); i++) {
                Entity carrier = carriers.get(i);
                check(!carrier.isRemoved() && carrier.tickCount == 0 && remaining(carrier) == lifetimes.get(i),
                        "1024-speed range preserves lifetime and tick count: " + carrier.getType());
                carrier.tick();
                check(!carrier.isRemoved() && remaining(carrier) == lifetimes.get(i) - 1,
                        "native tick still decrements exactly once");
                if (BuiltInRegistries.ENTITY_TYPE.getKey(carrier.getType()).toString().equals("jdte:ultimate_time_wand")) {
                    var wandItem = BuiltInRegistries.ITEM.get(new ResourceLocation("jdte:ultimate_time_wand"));
                    try {
                        var enableOverclock = wandItem.getClass().getMethod("setUltimateOverclock", ItemStack.class, boolean.class);
                        ItemStack wand = new ItemStack(wandItem);
                        enableOverclock.invoke(null, wand, true);
                        player.setGameMode(GameType.CREATIVE);
                        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
                        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                        check(wandItem.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                                "real JDTE wand interaction succeeds after range scan");
                        check(((Number) carrier.getClass().getMethod("getExponent").invoke(carrier)).intValue() == 11,
                                "same protected JDTE wand carrier upgrades 1024 to 2048 through real item use");
                        LogUtils.getLogger().info("JDTE REAL WAND 2048 UPGRADE PASSED: same UUID={}, exponent=11, native item interaction", carrier.getUUID());
                    } catch (NoSuchMethodException oldVersion) {
                        LogUtils.getLogger().info("JDTE old wand version lacks ultimate-overclock upgrade; native 2048 effect renewal checked separately");
                    }
                }
                if (BuiltInRegistries.ENTITY_TYPE.getKey(carrier.getType()).toString().equals("jdte:time_accelerator_effect")) {
                    carrier.getClass().getMethod("renew", int.class, int.class).invoke(carrier, 2048, 15);
                    check(((Number) carrier.getClass().getMethod("getAccelerationRate").invoke(carrier)).intValue() == 2048,
                            "native 2048 effect renewal works after protected range");
                    for (int tick = 0; tick < 14; tick++) carrier.tick();
                    check(!carrier.isRemoved() && remaining(carrier) == 1, "renewed effect survives first 14 native ticks");
                    carrier.tick();
                    check(carrier.isRemoved(), "effect expires on its native fifteenth tick");
                }
            }
            check(gameTime == level.getGameTime() && dayTime == level.getDayTime(), "world clocks unchanged");
            LogUtils.getLogger().info("JDTE CARRIER DETAIL PASSED: carriers={} single/range/loaded-NBT, normal item and furnace output={}, native 2048 renewal (when JDTE present)",
                    carriers.size(), furnace.getItem(2));
        } finally {
            ranges.reclaim(level.getServer(), player.getUUID(), fieldId);
            carriers.forEach(Entity::discard);
            ordinary.discard();
            level.removeBlock(pos, false);
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static Entity create(String id, ServerLevel level, BlockPos pos) throws Exception {
        String className = switch (id) {
            case "jdte:time_accelerator_effect" -> "com.jdte.common.entities.TimeAcceleratorEffectEntity";
            case "jdte:ultimate_time_wand" -> "com.jdte.common.entities.UltimateTimeWandEntity";
            default -> "com.direwolf20.justdirethings.common.entities.TimeWandEntity";
        };
        Class<?> clazz = Class.forName(className);
        if (id.equals("jdte:time_accelerator_effect"))
            return (Entity) clazz.getConstructor(Level.class, BlockPos.class, int.class).newInstance(level, pos, 1024);
        if (id.equals("jdte:ultimate_time_wand"))
            return (Entity) clazz.getConstructor(Level.class, BlockPos.class, int.class).newInstance(level, pos, 10);
        return (Entity) clazz.getConstructor(Level.class, BlockPos.class).newInstance(level, pos);
    }

    private static int remaining(Entity carrier) throws Exception {
        String method = carrier.getClass().getSimpleName().equals("TimeAcceleratorEffectEntity")
                ? "getServerRemainingTicks" : "getRemainingTime";
        return ((Number) carrier.getClass().getMethod(method).invoke(carrier)).intValue();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
