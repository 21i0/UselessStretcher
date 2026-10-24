package com.sorrowmist.useless.stretcher.content.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldChunkGenerator;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldCourtyard;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;

/** Real world/registry/item checks; transport uses a fake-player shim rather than a real network client. */
public final class TwoIStarfieldServerChecks {
    public static void run(ServerLevel overworld) throws Exception {
        TwoIStarfieldDataChecks.run(overworld.registryAccess());
        ServerLevel stars = overworld.getServer().getLevel(TwoIStarfieldDimensions.KEY);
        check(stars != null, "independent starfield dimension exists without retired dimensions");
        check(stars.getChunkSource().getGenerator() instanceof TwoIStarfieldChunkGenerator,
                "starfield has its independent flat generator");
        check(stars.dimensionType().fixedTime().orElse(-1L) == 18_000L,
                "starfield remains midnight independently of overworld time");
        check(stars.dimensionType().effectsLocation().equals(TwoIStarfieldDimensions.KEY.location()),
                "dedicated starfield effects are selected");
        ((TwoIStarfieldChunkGenerator) stars.getChunkSource().getGenerator()).resolveFloorState();
        int blueprintBlocks = 0;
        for (int x = -13; x <= 13; x++) for (int z = -13; z <= 19; z++) {
            for (int y = -1; y <= TwoIStarfieldCourtyard.MAX_HEIGHT; y++) {
                if (TwoIStarfieldCourtyard.stateAt(x, y, z) != null) blueprintBlocks++;
            }
        }
        check(blueprintBlocks >= 700 && blueprintBlocks <= 2_000, "courtyard build has a bounded sub-2000 block footprint");

        UUID id = UUID.randomUUID();
        var player = new TravelPlayer(overworld, new GameProfile(id, "StarfieldChecks"));
        TwoIStarfieldData data = TwoIStarfieldData.get(overworld.getServer());
        BlockPos gardenCenter = TwoIDollService.areaCenter(data.slotFor(id));
        BlockPos center = gardenCenter.offset(0, 0, 6);
        Vec3 landing = Vec3.atBottomCenterOf(center);
        Vec3 outsideGate = Vec3.atBottomCenterOf(TwoIStarfieldCourtyard.entrance(gardenCenter));
        var floor = BuiltInRegistries.BLOCK.get(new ResourceLocation("useless_mod:white_glow_plastic"));
        check(stars.getBlockState(gardenCenter.offset(20, -1, 0)).is(floor), "surrounding flat floor reuses the upstream white glow block");
        check(stars.getBlockState(gardenCenter).is(Blocks.SEA_LANTERN), "courtyard has a lighted central star pedestal");
        check(stars.getBlockState(gardenCenter.offset(0, 5, 12)).is(Blocks.SMOOTH_QUARTZ), "courtyard has a built white entrance arch");
        check(stars.getBlockState(gardenCenter.offset(6, -1, 5)).is(Blocks.MOSS_BLOCK), "courtyard has real planted garden beds");
        check(stars.getBlockState(center.below(4)).is(Blocks.BEDROCK), "flat floor has bedrock foundation");
        check(TwoIDollService.safeLocation(stars, player, landing).equals(landing), "empty ground keeps exact landing");
        check(TwoIDollService.safeLocation(stars, player, new Vec3(Double.NaN, 64, 0)) == null,
                "invalid coordinates are rejected before world access");
        check(TwoIDollService.safeLocation(stars, player, new Vec3(31_000_000D, 64, 0)) == null,
                "world-border coordinates are rejected");

        stars.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
        Vec3 moved = TwoIDollService.safeLocation(stars, player, landing);
        check(moved != null && !moved.equals(landing), "blocked landing relocates without trapping visitor");
        check(stars.getBlockState(center).is(Blocks.STONE), "landing search preserves blocking builds");
        stars.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        stars.setBlockAndUpdate(center.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        player.setPose(Pose.CROUCHING);
        moved = TwoIDollService.safeLocation(stars, player, landing);
        check(moved != null && !moved.equals(landing), "crouching visitor requires standing clearance after teleport");
        stars.setBlockAndUpdate(center.above(), Blocks.AIR.defaultBlockState());
        player.setPose(Pose.STANDING);
        var originalFloor = stars.getBlockState(center.below());
        stars.setBlockAndUpdate(center.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        moved = TwoIDollService.safeLocation(stars, player, landing);
        check(moved != null && !moved.equals(landing), "harmful floor is not accepted as a safe landing");
        stars.setBlockAndUpdate(center.below(), originalFloor);

        BlockPos origin = new BlockPos(48, 205, 48);
        overworld.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        overworld.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        overworld.setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
        player.moveTo(Vec3.atBottomCenterOf(origin), 63.0F, -12.0F);
        Vec3 originalPosition = player.position();
        player.fallDistance = 80.0F;
        player.setDeltaMovement(1.0D, -8.0D, 1.0D);
        var invitationConfig = com.sorrowmist.useless.stretcher.config.StretcherConfig.SERVER_STARFIELD_INVITATION;
        boolean enabled = invitationConfig.get();
        try {
            invitationConfig.set(false);
            check(!TwoIDollService.sendPlayerToStarfield(player, true), "disabled invitation blocks direct requests");
            check(player.level() == overworld && data.returnPoint(id) == null, "disabled invitation preserves position and visit data");
            var doll = com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(overworld);
            doll.setOwnerId(id); doll.restoreBond(100, false);
            check(!doll.canInvite(), "disabled invitation blocks full-affection doll");
            doll.restoreBond(100, true);
            check(!doll.canInvite(), "disabled invitation blocks revisits");
        } finally { invitationConfig.set(enabled); }
        check(TwoIDollService.sendPlayerToStarfield(player, true), "service accepts first invitation");
        check(player.level() == stars && player.position().equals(outsideGate), "invitation lands outside the courtyard gate");
        check(!TwoIDollService.tryStartCourtyardShow(player, data) && data.celebrationEnd(id) == 0L,
                "standing outside does not trigger show or reward rain");
        player.moveTo(landing);
        check(TwoIDollService.tryStartCourtyardShow(player, data) && data.celebrationEnd(id) > stars.getGameTime(),
                "walking into garden starts the thirty second show");
        check(!TwoIDollService.tryStartCourtyardShow(player, data), "inside polling cannot restart the show");
        check(player.fallDistance == 0.0F && player.getDeltaMovement().equals(Vec3.ZERO),
                "travel resets falling distance and momentum");
        check(!TwoIDollService.sendPlayerToStarfield(player, true), "nested invitation cannot overwrite return point");
        TwoIStarfieldData.ReturnPoint point = data.returnPoint(id);
        check(point != null && point.x() == originalPosition.x && point.yaw() == 63.0F,
                "server records original position and view direction");

        Method dropReward = TwoIDollService.class.getDeclaredMethod("dropReward", ServerPlayer.class, TwoIStarfieldData.class);
        dropReward.setAccessible(true);
        var creative = BuiltInRegistries.ITEM.get(new ResourceLocation("ae2:creative_storage_cell"));
        var drops = new ArrayList<ItemEntity>();
        // No real player watches this distant chunk during ServerStartedEvent. The persistent entity
        // manager accepts spawned items but deliberately excludes HIDDEN sections from spatial queries.
        // Capture actual join events, then verify accepted entities, not visibility of a fake player's chunk.
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (event.getLevel() == stars && event.getEntity() instanceof ItemEntity item
                    && item.getItem().is(creative) && id.equals(item.getTarget())) drops.add(item);
        };
        MinecraftForge.EVENT_BUS.addListener(capture);
        try {
            for (int i = 0; i < 3; i++) dropReward.invoke(null, player, data);
            TwoIStarfieldData restored = TwoIStarfieldData.load(data.save(new CompoundTag(), stars.registryAccess()), stars.registryAccess());
            check(restored.rewardsRemaining(id) == 5, "server drop progress persists after three real item spawns");
            check(restored.returnPoint(id).equals(point), "server return position survives save/read roundtrip");
            for (int i = 0; i < 20; i++) dropReward.invoke(null, player, restored);
            check(drops.size() == 8 && restored.rewardsRemaining(id) == 0,
                    "real drops cap at eight across saved and restored ledger");
            check(drops.stream().allMatch(item -> item.isAddedToLevel() && !item.isRemoved()
                            && id.equals(item.getTarget()) && item.isInvulnerable()),
                    "eight drops were actually accepted, owner-bound and protected from damage");
            restored.beginVisit(id, stars.getGameTime(), true);
            check(TwoIDollService.tryStartCourtyardShow(player, restored), "revisit can replay the courtyard show");
            dropReward.invoke(null, player, restored);
            check(drops.size() == 8 && restored.rewardsRemaining(id) == 0,
                    "revisit cannot create a ninth reward");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(capture);
            drops.forEach(ItemEntity::discard);
        }
        for (int i = 0; i < 5; i++) data.recordRewardDrop(id);

        invitationConfig.set(false);
        try { check(TwoIDollService.returnFromStarfield(player), "disabled invitation still allows return"); }
        finally { invitationConfig.set(enabled); }
        check(player.level() == overworld && player.position().equals(originalPosition),
                "service returns to exact safe original location");
        check(player.getYRot() == 63.0F && player.getXRot() == -12.0F, "return preserves original view direction");
        check(data.returnPoint(id) == null && data.celebrationEnd(id) == 0L,
                "completed return clears temporary visit data");
        check(!TwoIDollService.returnFromStarfield(player), "return handler does not steal ordinary doll recall");
        check(TwoIDollService.sendPlayerToStarfield(player, false), "spent invitation permits sightseeing revisit");
        check(data.rewardsRemaining(id) == 0, "service revisit leaves completed rewards spent");
        BlockPos preserved = gardenCenter.offset(8, 0, 8);
        stars.setBlockAndUpdate(preserved, Blocks.DIAMOND_BLOCK.defaultBlockState());
        overworld.setBlockAndUpdate(origin, Blocks.STONE.defaultBlockState());
        TwoIDollService.returnFromStarfield(player);
        check(player.level() == overworld && !player.position().equals(originalPosition),
                "return finds nearby safe ground when original position becomes obstructed");
        check(overworld.getBlockState(origin).is(Blocks.STONE), "return does not remove newly built blocks");
        overworld.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
        companionVisit(overworld, stars, player, originalPosition);
        check(stars.getBlockState(preserved).is(Blocks.DIAMOND_BLOCK), "revisits never regenerate over player garden edits");
        LogUtils.getLogger().info("REGRESSION starfield: independent registration, safe/no-edit landing, 8 persistent real drops and return/revisit passed (fake-player transport shim)");
    }

    private static void companionVisit(ServerLevel overworld, ServerLevel stars, TravelPlayer player, Vec3 origin) {
        player.moveTo(origin, 0.0F, 0.0F);
        TwoIDollEntity original = com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(overworld);
        original.setOwnerId(player.getUUID());
        original.restoreBond(100, false);
        original.setCustomName(net.minecraft.network.chat.Component.literal("Starfield original doll"));
        original.moveTo(origin.add(0.8D, 0.0D, 0.0D));
        check(overworld.addFreshEntity(original), "companion fixture joins source world");
        UUID dollId = original.getUUID();
        var guest = new TravelPlayer(overworld, new GameProfile(UUID.randomUUID(), "StarfieldGuest"));
        guest.moveTo(origin);
        check(!TwoIDollService.sendPlayerToStarfield(guest, true, original) && guest.level() == overworld,
                "a guest cannot take someone else's doll to the starfield");

        Consumer<EntityTravelToDimensionEvent> cancel = event -> {
            if (event.getEntity().getUUID().equals(dollId)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try {
            check(TwoIDollService.sendPlayerToStarfield(player, false, original), "visitor can enter when a mod cancels doll travel");
            check(!original.isRemoved() && original.level() == overworld && !TwoIDollService.hasCompanionVisit(original),
                    "cancelled companion travel retains the exact original without a stale visit marker");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancel);
        }
        TwoIDollService.returnFromStarfield(player);
        Consumer<EntityJoinLevelEvent> cancelArrival = event -> {
            if (event.getLevel() == stars && event.getEntity().getUUID().equals(dollId)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancelArrival);
        try {
            check(TwoIDollService.sendPlayerToStarfield(player, false, original), "visitor can enter when companion arrival is cancelled");
            check(!original.isRemoved() && original.isAddedToLevel() && original.level() == overworld,
                    "cancelled destination join revives and restores the exact source doll");
            check(currentDoll(stars, player.getUUID(), dollId) == null,
                    "cancelled destination join never leaves a duplicate companion");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(cancelArrival);
        }
        TwoIDollService.returnFromStarfield(player);
        original.acceptInvitation(player);
        TwoIDollEntity transferred = currentDoll(stars, player.getUUID(), dollId);
        check(original.isRemoved() && transferred != null, "native dimension transfer replaces only its source handle");
        check(transferred.getUUID().equals(dollId) && transferred.getGiftScore() == 100 && transferred.isInvitationUsed()
                        && transferred.getCustomName().getString().equals("Starfield original doll"),
                "companion UUID, bond, invitation flag and custom data survive native transfer");
        check(TwoIDollService.hasCompanionVisit(transferred), "transferred original carries its persistent visit marker");

        CompoundTag saved = transferred.saveWithoutId(new CompoundTag());
        transferred.discardForReclaim();
        TwoIDollEntity reloaded = com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(stars);
        reloaded.load(saved);
        check(stars.addFreshEntity(reloaded) && TwoIDollService.hasCompanionVisit(reloaded),
                "reloaded companion retains the visit marker and original UUID");
        TwoIDollService.returnFromStarfield(player);
        TwoIDollEntity returned = currentDoll(overworld, player.getUUID(), dollId);
        check(returned != null && !TwoIDollService.hasCompanionVisit(returned) && reloaded.isRemoved(),
                "normal return moves the reloaded original back and clears its visit marker");

        returned.acceptInvitation(player);
        TwoIDollEntity lastVisit = currentDoll(stars, player.getUUID(), dollId);
        check(lastVisit != null, "original can accompany later sightseeing visits");
        lastVisit.reclaimRemote(player);
        check(lastVisit.isRemoved(), "companion can be reclaimed as its item during a visit");
        TwoIDollService.returnFromStarfield(player);
        check(currentDoll(overworld, player.getUUID(), dollId) == null && currentDoll(stars, player.getUUID(), dollId) == null,
                "return never recreates a companion already reclaimed as an item");
    }

    private static TwoIDollEntity currentDoll(ServerLevel level, UUID owner, UUID dollId) {
        return TwoIDollEntity.trackedFor(level.getServer(), owner).stream()
                .filter(doll -> !doll.isRemoved() && doll.level() == level && doll.getUUID().equals(dollId))
                .findFirst().orElse(null);
    }

    private static final class TravelPlayer extends FakePlayer {
        private TravelPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void teleportTo(ServerLevel target, double x, double y, double z, float yaw, float pitch) {
            // FakePlayerNetHandler intentionally ignores teleports. Exercise service decisions without
            // placing a fake connection in the live server's player list or claiming client sync coverage.
            setLevel(target);
            moveTo(x, y, z, yaw, pitch);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
