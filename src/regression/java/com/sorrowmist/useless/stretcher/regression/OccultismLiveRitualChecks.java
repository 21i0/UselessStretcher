package com.sorrowmist.useless.stretcher.regression;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.neoforge.common.NeoForge;
import net.minecraftforge.neoforge.common.util.FakePlayerFactory;
import net.minecraftforge.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real server ticks, event-bus staff interaction and native block ticking; never shipped. */
final class OccultismLiveRitualChecks {
    private static final List<Session> SESSIONS = new ArrayList<>();
    private static long start;
    private static boolean delayed;
    private static boolean completedIdle;
    private static boolean restarted;

    static boolean tick(ServerLevel level) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("occultism")) return true;
        if (SESSIONS.isEmpty()) {
            start = level.getGameTime();
            for (int mode = 0; mode < 4; mode++) SESSIONS.add(new Session(level, new BlockPos(64 + 20 * mode, 180, 80), mode));
            return false;
        }
        long elapsed = level.getGameTime() - start;
        if (restarted) return tickRestarted(level, elapsed);
        if (completedIdle) return tickCompletedIdle(level, elapsed);
        if (delayed) return tickDelayed(level, elapsed);
        if (elapsed == 100) {
            int baseline = SESSIONS.getFirst().bowl.currentTime;
            check(baseline >= 4 && baseline <= 6, "native golden bowl follows real twenty-tick seconds");
            for (int i = 1; i < SESSIONS.size(); i++) {
                Session session = SESSIONS.get(i);
                check(session.bowl.currentTime >= baseline * 2,
                        "x2 extra ticks accelerate a live ritual through player route " + i + ": " + session.bowl.currentTime);
                if (session.marker != null) check(!session.marker.isRemoved() && !session.marker.isIdleThrottled(),
                        "active golden bowl marker stays present and awake");
                LogUtils.getLogger().info("OCCULTISM LIVE LOW SPEED PASSED: route={} realTicks={} native={} accelerated={}",
                        i, elapsed, baseline, session.bowl.currentTime);
                session.stack.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 1024);
                if (session.marker != null) session.marker.setSpeed(1024);
                else session.ranges.place(level, session.player, session.pos, session.stack);
            }
        }
        if (elapsed <= 100) return false;
        boolean complete = true;
        for (int i = 1; i < SESSIONS.size(); i++) {
            Session session = SESSIONS.get(i);
            if (session.bowl.getCurrentRitualRecipe() != null) { complete = false; continue; }
            int outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(session.pos).inflate(5)).stream()
                    .filter(e -> e.getItem().is(OccultismBlocks.DIMENSIONAL_MINESHAFT.get().asItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            check(outputs == 1, "live accelerated ritual has one native output, route=" + i);
        }
        check(elapsed < 300, "x1024 ritual must finish within the bounded real-time window");
        if (!complete) return false;
        LogUtils.getLogger().info("OCCULTISM LIVE RITUAL CHECKS PASSED: exactModVersion={} lowSpeedTicks=100 highSpeedTicks={} normal/permanent/range, native outputs",
                net.minecraftforge.fml.ModList.get().getModContainerById("occultism").orElseThrow().getModInfo().getVersion(), elapsed - 100);
        for (Session session : SESSIONS) session.close(level);
        SESSIONS.clear();
        delayed = true;
        start = level.getGameTime();
        for (int mode = 1; mode < 4; mode++)
            SESSIONS.add(new Session(level, new BlockPos(64 + 20 * mode, 180, 80), mode, false));
        return false;
    }

    private static boolean tickDelayed(ServerLevel level, long elapsed) {
        if (elapsed == 220) {
            check(SESSIONS.get(1).marker.isIdleThrottled(), "permanent marker reaches genuine idle throttle before activation");
            for (Session session : SESSIONS) {
                session.stack.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 1024);
                if (session.marker != null) session.marker.setSpeed(1024);
                else session.ranges.place(level, session.player, session.pos, session.stack);
                session.activate(level);
            }
        }
        if (elapsed <= 220) return false;
        boolean complete = true;
        for (Session session : SESSIONS) {
            if (session.bowl.getCurrentRitualRecipe() != null) {
                complete = false;
                if (session.marker != null) check(!session.marker.isIdleThrottled(), "native activation wakes idle marker immediately");
                continue;
            }
            int outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(session.pos).inflate(5)).stream()
                    .filter(e -> e.getItem().is(OccultismBlocks.DIMENSIONAL_MINESHAFT.get().asItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            check(outputs == 1, "idle-then-activated ritual yields exactly one native result");
        }
        check(elapsed < 420, "idle-then-activated rituals finish within bounded real time");
        if (!complete) return false;
        LogUtils.getLogger().info("OCCULTISM DELAYED ACTIVATION CHECKS PASSED: idleTicks=220 acceleratedTicks={} normal/permanent/range, native player activation",
                elapsed - 220);
        completedIdle = true;
        start = level.getGameTime();
        return false;
    }

    private static boolean tickCompletedIdle(ServerLevel level, long elapsed) {
        // Some integrations mark a block dirty while syncing or polling without doing work.
        // A known empty ritual must not be kept awake by that generic activity heuristic.
        for (Session session : SESSIONS) {
            check(session.bowl.getCurrentRitualRecipe() == null, "completed mineshaft ritual stays completed");
            session.bowl.setChanged();
        }
        if (elapsed < 220) return false;
        check(SESSIONS.get(1).marker.isIdleThrottled(),
                "completed mineshaft ritual sleeps even while an integration marks its block dirty");
        check(SESSIONS.get(2).ranges.getField(SESSIONS.get(2).rangeId).idleThrottled(),
                "range recognizes the completed mineshaft ritual as idle despite dirty notifications");
        LogUtils.getLogger().info("OCCULTISM COMPLETED IDLE CHECKS PASSED: idleTicks=220 normal/permanent/range, repeated dirty notifications");
        for (Session session : SESSIONS) {
            int n = 0;
            for (var ingredient : session.recipe.value().getIngredients())
                ((SacrificialBowlBlockEntity) level.getBlockEntity(session.offerings.get(n++))).itemStackHandler
                        .setStackInSlot(0, ingredient.getItems()[0].copyWithCount(1));
            session.activate(level);
        }
        restarted = true;
        start = level.getGameTime();
        return false;
    }

    private static boolean tickRestarted(ServerLevel level, long elapsed) {
        if (elapsed == 1) {
            for (Session session : SESSIONS) {
                check(session.bowl.getCurrentRitualRecipe() != null, "restarted ritual is still running before reload");
                int progress = session.bowl.currentTime;
                var saved = session.bowl.saveWithFullMetadata(level.registryAccess());
                session.bowl.onChunkUnloaded();
                level.removeBlockEntity(session.pos);
                session.bowl = (GoldenSacrificialBowlBlockEntity) net.minecraft.world.level.block.entity.BlockEntity
                        .loadStatic(session.pos, level.getBlockState(session.pos), saved, level.registryAccess());
                check(session.bowl != null, "native bowl NBT loads");
                level.setBlockEntity(session.bowl);
                level.invalidateCapabilities(session.pos);
                check(session.bowl.getCurrentRitualRecipe() != null && session.bowl.currentTime == progress,
                        "native recipe and partial progress survive reload");
            }
        }
        boolean complete = true;
        for (Session session : SESSIONS) {
            if (session.bowl.getCurrentRitualRecipe() != null) {
                complete = false;
                if (session.marker != null) check(!session.marker.isIdleThrottled(), "restarted/reloaded ritual stays awake");
                else check(!session.ranges.getField(session.rangeId).idleThrottled(), "range wakes after native restart/reload");
                continue;
            }
            int outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(session.pos).inflate(5)).stream()
                    .filter(e -> e.getItem().is(OccultismBlocks.DIMENSIONAL_MINESHAFT.get().asItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            check(outputs == 2, "two native rituals on the same bowl yield exactly two mineshafts across reload");
        }
        check(elapsed < 200, "restarted/reloaded ritual finishes in bounded real time");
        if (!complete) return false;
        LogUtils.getLogger().info("OCCULTISM RESTART RELOAD CHECKS PASSED: realTicks={} normal/permanent/range, two rituals two results, native NBT", elapsed);
        for (Session session : SESSIONS) session.close(level);
        return true;
    }

    private static final class Session {
        final BlockPos pos;
        GoldenSacrificialBowlBlockEntity bowl;
        final List<BlockPos> offerings = new ArrayList<>();
        final net.minecraftforge.neoforge.common.util.FakePlayer player;
        final ItemStack stack = new ItemStack(ModItems.WONDROUS_STAFF.get());
        final RangeAccelerationSavedData ranges;
        final UUID rangeId;
        final WondrousStaffAccelerationEntity marker;
        final RecipeHolder<RitualRecipe> recipe;

        Session(ServerLevel level, BlockPos pos, int mode) {
            this(level, pos, mode, true);
        }

        @SuppressWarnings("unchecked")
        Session(ServerLevel level, BlockPos pos, int mode, boolean activateNow) {
            this.pos = pos;
            recipe = (RecipeHolder<RitualRecipe>)(RecipeHolder<?>) level.getRecipeManager()
                    .byKey(ResourceLocation.parse("occultism:ritual/craft_dimensional_mineshaft")).orElseThrow();
            recipe.value().getPentacle().place(level, pos, Rotation.NONE);
            level.setBlockAndUpdate(pos, OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get().defaultBlockState());
            bowl = (GoldenSacrificialBowlBlockEntity) level.getBlockEntity(pos);
            int n = 0;
            for (var ingredient : recipe.value().getIngredients()) {
                BlockPos offering = pos.offset(-7, 0, n++ - 3);
                offerings.add(offering);
                level.setBlockAndUpdate(offering, OccultismBlocks.SACRIFICIAL_BOWL.get().defaultBlockState());
                ((SacrificialBowlBlockEntity) level.getBlockEntity(offering)).itemStackHandler
                        .setStackInSlot(0, ingredient.getItems()[0].copyWithCount(1));
            }
            player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RitualLive" + mode));
            if (activateNow) activate(level);
            stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), true);
            stack.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 2);
            WondrousStaffAcceleration.setMode(stack, mode == 2 ? WondrousStaffAcceleration.STAFF_MODE_PERMANENT : 0);
            if (mode == 3) {
                stack.set(StretcherComponents.RANGE_SIZE_X.get(), 1);
                stack.set(StretcherComponents.RANGE_SIZE_Y.get(), 1);
                stack.set(StretcherComponents.RANGE_SIZE_Z.get(), 1);
                ranges = RangeAccelerationSavedData.get(level.getServer());
                rangeId = ranges.place(level, player, pos, stack).id();
                marker = null;
            } else {
                ranges = null;
                rangeId = null;
                if (mode == 0) { marker = null; return; }
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.setShiftKeyDown(true);
                var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                NeoForge.EVENT_BUS.post(event);
                check(event.isCanceled() && event.getCancellationResult().consumesAction(), "real staff event owns bowl click");
                marker = level.getEntitiesOfClass(WondrousStaffAccelerationEntity.class, new AABB(pos).inflate(1),
                        e -> pos.equals(e.getTargetPos())).stream().findFirst().orElseThrow();
            }
        }

        void activate(ServerLevel level) {
            player.setPos(Vec3.atCenterOf(pos.above()));
            player.setItemInHand(InteractionHand.MAIN_HAND, recipe.value().getActivationItemStack()[0].copyWithCount(1));
            check(bowl.activate(level, pos, player, InteractionHand.MAIN_HAND, Direction.UP), "native player activates real ritual");
            check(bowl.getCurrentRitualRecipe() != null && bowl.getCurrentRitualRecipe().id().equals(recipe.id()),
                    "native player activation selects intended recipe");
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }

        void close(ServerLevel level) {
            if (marker != null) marker.discard();
            if (ranges != null) ranges.reclaim(level.getServer(), player.getUUID(), rangeId);
            if (bowl.getCurrentRitualRecipe() != null) bowl.stopRitual(false);
            offerings.forEach(p -> level.removeBlock(p, false));
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(10)).forEach(ItemEntity::discard);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
