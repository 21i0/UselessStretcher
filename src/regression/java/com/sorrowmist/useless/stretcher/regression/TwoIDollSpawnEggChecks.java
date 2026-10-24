package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Real upstream capture and real MinecraftForge spawn paths, including NBT spawners which skip finalizeSpawn. */
final class TwoIDollSpawnEggChecks {
    private TwoIDollSpawnEggChecks() { }

    static void run(ServerLevel level) throws Exception {
        check(SpawnEggItem.byId(ModEntities.TWO_I_DOLL.get()) == null, "2i has no registered spawn egg");
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem egg) {
                check(egg.getType(null) != ModEntities.TWO_I_DOLL.get(),
                        "no item registry egg defaults to 2i");
            }
        }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIEggCheck"));
        player.getAbilities().instabuild = false;
        BlockPos testSpawn = level.getSharedSpawnPos();
        player.setPos(testSpawn.getX() + .5D, testSpawn.getY() + 1.0D, testSpawn.getZ() + .5D);
        player.setYRot(0);
        player.setXRot(0);
        AABB area = new AABB(player.blockPosition()).inflate(8);
        Set<UUID> originalDrops = itemIds(level, area);
        var capture = Class.forName("com.sorrowmist.useless.utils.UselessItemUtils")
                .getMethod("tryCaptureSpawnEgg", LivingEntity.class, ItemStack.class, Player.class);
        ItemStack staff = new ItemStack(com.sorrowmist.useless.init.ModItems.ENDLESS_BEAF_ITEM.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, UComponents.BeefCaptureEnabledComponent.get(), true);
        ItemStack mascot = new ItemStack(ModItems.TWO_I_DOLL.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(mascot, StretcherComponents.TWO_I_BOND.get(), -23);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(mascot, StretcherComponents.TWO_I_INVITATION_USED.get(), true);
        mascot.setHoverName(Component.literal("Pick keeps my custom skin"));
        CompoundTag future = new CompoundTag();
        future.putString("future_skin", "2i-custom.png");
        mascot.getOrCreateTag().merge(future);
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "normal direct factory remains available");
        doll.setPos(player.position());
        doll.setOwnerId(player.getUUID());
        doll.restoreFromItem(mascot);
        level.getChunkAt(doll.blockPosition());
        check(level.addFreshEntity(doll), "direct normal doll registers");
        UUID id = doll.getUUID();
        int numericId = doll.getId();
        ItemStack expectedPick = doll.toDollItem();
        var chicken = EntityType.CHICKEN.create(level);
        check(chicken != null, "ordinary capture control exists");
        chicken.setPos(player.position());
        check(level.addFreshEntity(chicken), "ordinary capture control registers");
        try {
            unchanged(expectedPick, doll.getPickResult(), "middle pick returns full doll item, never a pig carrier egg");
            for (boolean magnet : new boolean[]{false, true}) {
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, UComponents.BeefMagnetEnabledComponent.get(), magnet);
                for (EnchantMode mode : new EnchantMode[]{EnchantMode.FORTUNE, EnchantMode.SILK_TOUCH}) {
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, UComponents.EnchantModeComponent.get(), mode);
                    int before = eggCount(level, player, area);
                    capture.invoke(null, doll, staff, player);
                    capture.invoke(null, doll, staff, player);
                    check(eggCount(level, player, area) == before,
                            "actual capture creates no egg with magnet=" + magnet + ", mode=" + mode);
                    check(doll.getUUID().equals(id) && doll.getId() == numericId && !doll.isRemoved()
                                    && level.getEntity(id) == doll && level.getEntity(numericId) == doll,
                            "capture preserves the same doll and both world indexes");
                    unchanged(expectedPick, doll.toDollItem(), "capture preserves all doll state");
                    capture.invoke(null, chicken, staff, player);
                    check(eggCount(level, player, area) == before + 1,
                            "ordinary chicken still yields one egg through actual upstream capture");
                }
            }

            ItemStack forged = new ItemStack(Items.PIG_SPAWN_EGG);
            CompoundTag data = new CompoundTag();
            data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.TWO_I_DOLL.get()).toString());
            forged.getOrCreateTag().put("EntityTag", data);
            EntityType<?> decoded = ((SpawnEggItem) forged.getItem()).getType(forged.getTag());
            check(decoded == ModEntities.TWO_I_DOLL.get(), "forged pig-carrier fixture resolves actual 2i type");
            BlockPos spawnPos = player.blockPosition().offset(3, 0, 0);
            Set<UUID> dollsBefore = dollIds(level, area);
            Entity eggResult = decoded.spawn(level, forged, player, spawnPos, MobSpawnType.SPAWN_EGG, false, false);
            check(rejected(eggResult, level) && dollIds(level, area).equals(dollsBefore),
                    "actual EntityType.spawn cancels forged carrier spawn before world insertion");
            if (eggResult instanceof TwoIDollEntity unexpected) unexpected.discardForReclaim();
            Entity spawnerResult = ModEntities.TWO_I_DOLL.get().spawn(level, spawnPos, MobSpawnType.SPAWNER);
            check(rejected(spawnerResult, level) && dollIds(level, area).equals(dollsBefore),
                    "ordinary spawner finalize cannot introduce another doll");
            if (spawnerResult instanceof TwoIDollEntity unexpected) unexpected.discardForReclaim();

            var nbtSpawner = ModEntities.TWO_I_DOLL.get().create(level);
            check(nbtSpawner != null, "NBT-spawner candidate factory");
            nbtSpawner.setPos(spawnPos.getX() + .5, spawnPos.getY(), spawnPos.getZ() + .5);
            BaseSpawner source = new BaseSpawner() {
                @Override public void broadcastEvent(Level ignored, BlockPos pos, int eventId) { }
            };
            ForgeEventFactory.onFinalizeSpawnSpawner(nbtSpawner, level, level.getCurrentDifficultyAt(spawnPos),
                    null, null, source);
            try {
                check(nbtSpawner.isSpawnCancelled(), "NBT spawner is cancelled even when its finalizeSpawn is skipped");
                level.tryAddFreshEntityWithPassengers(nbtSpawner);
                check(!nbtSpawner.isAddedToWorld() && level.getEntity(nbtSpawner.getUUID()) == null
                                && dollIds(level, area).equals(dollsBefore),
                        "MinecraftForge world insertion also rejects the cancelled NBT-spawner candidate");
            } finally {
                nbtSpawner.discardForReclaim();
            }

            ItemStack deployStack = expectedPick.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, deployStack);
            check(TwoIDollItem.deploy(player, InteractionHand.MAIN_HAND), "normal doll deployment still succeeds");
            check(player.getMainHandItem().isEmpty(), "survival deployment consumes exactly the held doll");
            var deployed = level.getEntitiesOfClass(TwoIDollEntity.class, area,
                    entity -> !dollsBefore.contains(entity.getUUID()));
            check(deployed.size() == 1, "normal deployment creates exactly one companion");
            var companion = deployed.get(0);
            try {
                check(player.getUUID().equals(companion.getOwnerId()), "normal deployed doll retains owner");
                unchanged(expectedPick, companion.getPickResult(), "deployed doll middle pick preserves all components");
            } finally {
                companion.discardForReclaim();
            }
            com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i spawn eggs: real capture suppressed (magnet and fortune/silk), ordinary chicken capture preserved, forged pig-carrier and NBT spawner rejected, normal deployment and component-preserving middle pick passed");
        } finally {
            doll.discardForReclaim();
            chicken.discard();
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (!originalDrops.contains(drop.getUUID()) && drop.getItem().getItem() instanceof SpawnEggItem) drop.discard();
            }
            player.getInventory().clearContent();
        }
    }

    private static int eggCount(ServerLevel level, ServerPlayer player, AABB area) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SpawnEggItem) count += stack.getCount();
        }
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
            if (drop.getItem().getItem() instanceof SpawnEggItem) count += drop.getItem().getCount();
        }
        return count;
    }

    private static boolean rejected(Entity candidate, ServerLevel level) {
        // Forge 1.20 returns its cancelled candidate even when world insertion was
        // refused. Assert cancellation and both indexes before any fixture cleanup.
        return candidate == null || candidate instanceof TwoIDollEntity doll
                && doll.isSpawnCancelled() && !doll.isAddedToWorld()
                && level.getEntity(doll.getUUID()) == null && level.getEntity(doll.getId()) == null;
    }

    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>();
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(entity -> result.add(entity.getUUID()));
        return result;
    }

    private static Set<UUID> dollIds(ServerLevel level, AABB area) {
        Set<UUID> result = new HashSet<>();
        level.getEntitiesOfClass(TwoIDollEntity.class, area).forEach(entity -> result.add(entity.getUUID()));
        return result;
    }

    private static void unchanged(ItemStack expected, ItemStack actual, String message) {
        check(actual.getCount() == 1 && ItemStack.isSameItemSameTags(expected, actual), message);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("2i spawn egg guard: " + message);
    }
}
