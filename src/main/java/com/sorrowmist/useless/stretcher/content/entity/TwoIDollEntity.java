package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Small invulnerable mascot that keeps a soft connection to its owner. */
public final class TwoIDollEntity extends PathfinderMob {
    private static final Map<net.minecraft.server.MinecraftServer, Map<UUID, Set<TwoIDollEntity>>> TRACKED =
            new HashMap<>();
    private static final EntityDataAccessor<Integer> POSE_ACTION =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> POSE_STARTED_AT =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> ENVIRONMENT_ACTION =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ENVIRONMENT_STARTED_AT =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> SHARED_UMBRELLA =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHARED_UMBRELLA_HOLDER =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> BUBBLE_TEXT =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> BUBBLE_UNTIL =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Component> BUBBLE_MESSAGE =
            SynchedEntityData.defineId(TwoIDollEntity.class, EntityDataSerializers.COMPONENT);
    public static final int ACTION_NONE = 0;
    public static final int ACTION_GREETING = 1;
    public static final int ACTION_CURIOUS = 2;
    public static final int ACTION_PATTED = 3;
    public static final int ACTION_RECEIVE_GIFT = 4;
    public static final int ACTION_INVITE = 5;
    public static final int ACTION_SWING_FEET = 6;
    public static final int ACTION_UMBRELLA = 7;
    public static final int ACTION_SNEEZE = 8;
    public static final int ACTION_SHIVER = 9;
    public static final int ACTION_STRETCH = 10;
    public static final int ACTION_DUST = 11;
    public static final int ACTION_SHY = 12;
    private static final int MIN_BOND = -100;
    private static final int MAX_BOND = 100;
    private static final int MIN_PRANK_COOLDOWN = 300;
    private static final int MAX_PRANK_COOLDOWN = 4800;
    private static final int TIME_STOP_COOLDOWN = 24_000;
    private static final int TIME_STOP_CHANCE_DENOMINATOR = 10_000;
    private static final int PANIC_COOLDOWN = 6_000;
    /** Center spacing while two owned dolls share one umbrella; their plush bodies visibly touch. */
    public static final double SHARED_UMBRELLA_DISTANCE = 0.34D;
    private static final int GIFT_COOLDOWN_TICKS = 1_200;
    private static final int TALK_COOLDOWN_TICKS = 200;
    private static final Set<String> SPECIAL_GIFTS = Set.of(
            "avaritia:infinity_ingot",
            "mekanism_extras:qio_drive_singularity",
            "modern_industrialization:quantum_upgrade",
            "bigreactors:inanite_block",
            "draconicevolution:chaotic_core",
            "occultism:ritual_dummy/misc_celestial_chalice",
            "appflux:core_256m",
            "advanced_ae:data_entangler",
            "advanced_ae:quantum_multi_threader",
            "megacells:cell_component_256m",
            "ae2omnicells:quantum_omni_cell_component_256m",
            "mekanism_extras:infinite_induction_cell",
            "mekanism_extras:infinite_induction_provider",
            "mekanism:pellet_antimatter",
            "mekanism_extras:infinite_control_circuit",
            "ars_nouveau:wilden_tribute",
            "minecraft:elytra",
            "minecraft:heavy_core",
            "minecraft:dragon_head",
            "ae2:256k_crafting_storage",
            "mekanism:ultimate_induction_cell",
            "mekanism:ultimate_induction_provider",
            "pneumaticcraft:micromissiles",
            "minecraft:dragon_egg",
            "minecraft:heart_of_the_sea",
            "minecraft:echo_shard",
            "minecraft:nether_star",
            "minecraft:torchflower",
            "minecraft:recovery_compass");
    private static final List<List<EntityType<?>>> PRANK_MOB_TIERS = List.of(
            List.of(
            EntityType.CHICKEN, EntityType.COW, EntityType.SHEEP, EntityType.PIG,
            EntityType.RABBIT, EntityType.BEE, EntityType.CAT, EntityType.WOLF,
            EntityType.FOX, EntityType.GOAT, EntityType.AXOLOTL, EntityType.LLAMA,
            EntityType.PARROT, EntityType.TURTLE),
            List.of(EntityType.FOX, EntityType.GOAT, EntityType.AXOLOTL, EntityType.LLAMA,
                    EntityType.PARROT, EntityType.TURTLE, EntityType.PANDA, EntityType.MOOSHROOM),
            List.of(EntityType.SLIME, EntityType.SILVERFISH, EntityType.ENDERMITE,
                    EntityType.CAVE_SPIDER, EntityType.PHANTOM, EntityType.MAGMA_CUBE),
            List.of(EntityType.WITCH, EntityType.WARDEN));
    private static final String PRANK_OWNER_TAG = "useless_stretcher_2i_prank_owner";
    private UUID ownerId;
    private int giftScore;
    private boolean invitationUsed;
    private int prankCooldown;
    private int lastPrank = -1;
    private int patCooldown;
    private int giftCooldown;
    private int talkCooldown;
    private final Map<UUID, TwoIStorySession> storySessions = new HashMap<>();
    private int timeStopCooldown;
    private int panicCooldown;
    private boolean panicDragonSpawned;
    private ItemStack itemTemplate = ItemStack.EMPTY;
    private TwoIDollMemory.Interaction interaction = TwoIDollMemory.Interaction.empty();
    private boolean authorizedReclaim;
    /** Transient partner link; both entities re-meet after a restart. */
    private UUID friendId;
    private final Map<UUID, Long> interactionViewers = new HashMap<>();

    public TwoIDollEntity(EntityType<? extends TwoIDollEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoGravity(false);
        prankCooldown = 1200 + random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }
            @Override public boolean canUse() { return isInteractionHeld(); }
            @Override public void start() { getNavigation().stop(); }
            @Override public void tick() {
                getNavigation().stop();
                setDeltaMovement(0, isNoGravity() ? 0 : getDeltaMovement().y, 0);
            }
        });
        goalSelector.addGoal(1, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.55D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        if (this.ownerId != null && !this.ownerId.equals(ownerId)) clearFriendship();
        untrack();
        this.ownerId = ownerId;
        if (isAddedToWorld()) track();
    }

    public int getGiftScore() {
        return giftScore;
    }

    @Override
    public ItemStack getPickResult() { return toDollItem(); }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason, net.minecraft.world.entity.SpawnGroupData group, CompoundTag spawnTag) {
        // This companion is deployed from its item, never generated by a mob spawner/summoner.
        setSpawnCancelled(true);
        return group;
    }

    public boolean isInvitationUsed() {
        return invitationUsed;
    }

    public boolean hasSharedUmbrella() {
        return entityData.get(SHARED_UMBRELLA);
    }

    public boolean isSharedUmbrellaHolder() {
        return entityData.get(SHARED_UMBRELLA_HOLDER);
    }

    /** Copies synchronized pose/environment flags to a client-only UI preview entity. */
    public void copyClientPreviewStateFrom(TwoIDollEntity source) {
        if (source == null || source == this) return;
        entityData.set(POSE_ACTION, source.getPoseAction());
        entityData.set(POSE_STARTED_AT, source.entityData.get(POSE_STARTED_AT));
        entityData.set(ENVIRONMENT_ACTION, source.getEnvironmentAction());
        entityData.set(ENVIRONMENT_STARTED_AT, source.entityData.get(ENVIRONMENT_STARTED_AT));
        entityData.set(SHARED_UMBRELLA, source.hasSharedUmbrella());
        entityData.set(SHARED_UMBRELLA_HOLDER, source.isSharedUmbrellaHolder());
        entityData.set(BUBBLE_TEXT, source.entityData.get(BUBBLE_TEXT));
        entityData.set(BUBBLE_UNTIL, source.entityData.get(BUBBLE_UNTIL));
        entityData.set(BUBBLE_MESSAGE, source.entityData.get(BUBBLE_MESSAGE));
    }

    public static List<TwoIDollEntity> trackedFor(net.minecraft.server.MinecraftServer server, UUID owner) {
        Map<UUID, Set<TwoIDollEntity>> byOwner = TRACKED.get(server);
        if (byOwner == null) return List.of();
        Set<TwoIDollEntity> entities = byOwner.get(owner);
        return entities == null ? List.of() : new ArrayList<>(entities);
    }

    public static void clearTracking() {
        TRACKED.clear();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        track();
    }

    @Override
    public void onRemovedFromWorld() {
        if (TwoILifecycleGuard.blocks(this)) return;
        storySessions.clear();
        interactionViewers.clear();
        clearFriendship();
        untrack();
        super.onRemovedFromWorld();
    }

    private void track() {
        if (level().isClientSide || ownerId == null || !(level() instanceof ServerLevel level)) return;
        TRACKED.computeIfAbsent(level.getServer(), ignored -> new HashMap<>())
                .computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(this);
    }

    private void untrack() {
        if (!(level() instanceof ServerLevel level) || ownerId == null) return;
        Map<UUID, Set<TwoIDollEntity>> byOwner = TRACKED.get(level.getServer());
        if (byOwner == null) return;
        Set<TwoIDollEntity> entities = byOwner.get(ownerId);
        if (entities != null) {
            entities.remove(this);
            if (entities.isEmpty()) byOwner.remove(ownerId);
        }
        if (byOwner.isEmpty()) TRACKED.remove(level.getServer());
    }

    public void restoreBond(int score, boolean invitationUsed) {
        this.giftScore = clampBond(score);
        this.invitationUsed = invitationUsed;
    }

    /** Keep all components (name, skin and future extensions), not just the relationship score. */
    public void restoreFromItem(ItemStack stack) {
        itemTemplate = stack.copyWithCount(1);
        UUID savedOwner = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.TWO_I_OWNER.get());
        if (ownerId == null && savedOwner != null) setOwnerId(savedOwner);
        restoreBond(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.TWO_I_BOND.get(),
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.TWO_I_GIFT_SCORE.get(), 0) * 10),
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.TWO_I_INVITATION_USED.get(), false));
        TwoIDollMemory memory = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.TWO_I_MEMORY.get());
        if (memory != null) restoreMemory(memory);
        UUID dollId = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.TWO_I_DOLL_ID.get());
        if (dollId != null && level() instanceof ServerLevel server)
            restoreMemory(TwoICompanionshipData.get(server.getServer()).progress(dollId, memory()).merge(memory()));
    }

    public ItemStack toDollItem() {
        ItemStack stack = itemTemplate.isEmpty() ? new ItemStack(ModItems.TWO_I_DOLL.get())
                : itemTemplate.copyWithCount(1);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_BOND.get(), giftScore);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_INVITATION_USED.get(), invitationUsed);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_MEMORY.get(), memory());
        if (ownerId != null) com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_OWNER.get(), ownerId);
        if (!level().isClientSide) {
            if (itemTemplate.isEmpty()) itemTemplate = stack.copyWithCount(1);
            UUID dollId = com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.ensureIdentity(itemTemplate);
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_DOLL_ID.get(), dollId);
        }
        return stack;
    }

    private TwoIDollMemory memory() {
        return new TwoIDollMemory(new TwoIDollMemory.Cooldowns(patCooldown, giftCooldown, talkCooldown,
                prankCooldown, timeStopCooldown, panicCooldown, lastPrank, panicDragonSpawned), interaction, lastCompanionSample);
    }

    private void restoreMemory(TwoIDollMemory memory) {
        TwoIDollMemory.Cooldowns c = memory.cooldowns();
        patCooldown = c.pat(); giftCooldown = c.gift(); talkCooldown = c.talk();
        prankCooldown = c.prank(); timeStopCooldown = c.timeStop(); panicCooldown = c.panic();
        lastPrank = c.lastPrank(); panicDragonSpawned = c.panicDragonSpawned();
        interaction = memory.interaction();
        lastCompanionSample = memory.lastCompanionSample();
    }

    /** Reward budgets use monotonic world game time, never the editable day/night clock. */
    private void refreshInteractionDay() {
        interaction = interaction.forDay(level().getGameTime());
    }

    private long lastCompanionSample = -1;

    private void tickCompanionship(ServerLevel server) {
        long time = server.getGameTime();
        if (time % TwoICompanionshipData.CHECK_INTERVAL != 0 || ownerId == null) return;
        refreshInteractionDay();
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || owner.serverLevel() != server || distanceToSqr(owner) > 144.0D) return;
        if (itemTemplate.isEmpty()) itemTemplate = new ItemStack(ModItems.TWO_I_DOLL.get());
        UUID dollId = com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.ensureIdentity(itemTemplate);
        var sample = TwoICompanionshipData.get(server.getServer()).sample(dollId, memory(), time);
        var merged = sample.progress().merge(memory());
        interaction = merged.interaction();
        lastCompanionSample = merged.lastCompanionSample();
        if (sample.awarded()) {
            changeBond(1);
            owner.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.companionship"), true);
            Network.updateTwoIDialogue(owner, getId(), canInvite(), giftScore);
        }
    }

    /** Pairs nearby owned dolls for a shared umbrella and a friend interaction. */
    private void tickDollFriendship(ServerLevel server) {
        if (friendId != null) {
            Entity found = server.getEntity(friendId);
            if (!(found instanceof TwoIDollEntity friend) || friend.isRemoved()
                    || friend.friendId == null || !friend.friendId.equals(getUUID())
                    || !friendshipOwnerPresent(server) || !friend.friendshipOwnerPresent(server)) {
                clearFriendship();
            } else {
                maintainSharedUmbrella(friend);
            }
            return;
        }
        if (tickCount % 5 != 0) return;
        if (ownerId == null || !friendshipOwnerPresent(server)) return;
        TwoIDollEntity candidate = null;
        double best = 2.25D * 2.25D;
        for (TwoIDollEntity other : server.getEntitiesOfClass(TwoIDollEntity.class,
                getBoundingBox().inflate(2.25D), other -> other != this && other.friendId == null
                        && other.ownerId != null && other.friendshipOwnerPresent(server))) {
            double distance = distanceToSqr(other);
            if (distance <= best) {
                best = distance;
                candidate = other;
            }
        }
        if (candidate != null) establishFriendship(candidate, server);
    }

    private boolean friendshipOwnerPresent(ServerLevel server) {
        if (ownerId == null) return false;
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(ownerId);
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.serverLevel() == server
                && distanceToSqr(owner) <= 48.0D * 48.0D;
    }

    private void establishFriendship(TwoIDollEntity other, ServerLevel server) {
        friendId = other.getUUID();
        other.friendId = getUUID();
        boolean thisHolder = getUUID().compareTo(other.getUUID()) < 0;
        entityData.set(SHARED_UMBRELLA, true);
        entityData.set(SHARED_UMBRELLA_HOLDER, thisHolder);
        other.entityData.set(SHARED_UMBRELLA, true);
        other.entityData.set(SHARED_UMBRELLA_HOLDER, !thisHolder);
        playPoseAction(ACTION_GREETING);
        other.playPoseAction(ACTION_GREETING);
        maintainSharedUmbrella(other);
    }

    /**
     * Keep the paired dolls together without using the normal entity push solver.
     * UUID ordering gives one deterministic anchor, so both sides never fight
     * over the same correction on alternating ticks.
     */
    private void maintainSharedUmbrella(TwoIDollEntity other) {
        if (getUUID().compareTo(other.getUUID()) > 0) return;
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            dx = 1.0D;
            dz = 0.0D;
            horizontal = 1.0D;
        }
        double nx = dx / horizontal;
        double nz = dz / horizontal;
        double midX = (getX() + other.getX()) * 0.5D;
        double midY = (getY() + other.getY()) * 0.5D;
        double midZ = (getZ() + other.getZ()) * 0.5D;
        double half = SHARED_UMBRELLA_DISTANCE * 0.5D;
        // A wall, fence or slab must not leave the friends separated.  The pair is
        // a purely visual friendship, so temporarily fly through collision geometry
        // and place both plush bodies at the same shared-umbrella anchor every tick.
        noPhysics = true;
        other.noPhysics = true;
        setNoGravity(true);
        other.setNoGravity(true);
        setPos(midX - nx * half, midY, midZ - nz * half);
        other.setPos(midX + nx * half, midY, midZ + nz * half);
        setDeltaMovement(Vec3.ZERO);
        other.setDeltaMovement(Vec3.ZERO);
    }

    private void clearFriendship() {
        UUID previous = friendId;
        friendId = null;
        noPhysics = false;
        setNoGravity(false);
        entityData.set(SHARED_UMBRELLA, false);
        entityData.set(SHARED_UMBRELLA_HOLDER, true);
        if (previous == null || !(level() instanceof ServerLevel server)) return;
        Entity found = server.getEntity(previous);
        if (found instanceof TwoIDollEntity other && getUUID().equals(other.friendId)) {
            other.friendId = null;
            other.noPhysics = false;
            other.setNoGravity(false);
            other.entityData.set(SHARED_UMBRELLA, false);
            other.entityData.set(SHARED_UMBRELLA_HOLDER, true);
        }
    }

    /** A short server lease prevents abandoned/disconnected screens from freezing a companion forever. */
    public void keepInteraction(ServerPlayer player, boolean active) {
        if (!active) {
            interactionViewers.remove(player.getUUID());
        } else if (canReadStory(player)) {
            interactionViewers.put(player.getUUID(), level().getGameTime() + 60L);
            getNavigation().stop();
            setDeltaMovement(0, isNoGravity() ? 0 : getDeltaMovement().y, 0);
        }
    }

    public boolean isInteractionHeld() { return !interactionViewers.isEmpty(); }

    private void expireInteractionViewers() {
        if (!(level() instanceof ServerLevel server) || interactionViewers.isEmpty()) return;
        long now = server.getGameTime();
        interactionViewers.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            return entry.getValue() <= now || player == null || !canReadStory(player);
        });
    }

    private static int clampBond(int score) {
        return Math.max(MIN_BOND, Math.min(MAX_BOND, score));
    }

    private void changeBond(int delta) {
        giftScore = clampBond(giftScore + delta);
    }

    @Override
    public void tick() {
        expireInteractionViewers();
        if (isNoGravity() && !isInteractionHeld() && level() instanceof ServerLevel server) {
            ServerPlayer owner = ownerId == null ? null : server.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != level() || !owner.isAlive() || owner.isSpectator()
                    || !owner.getAbilities().flying && !owner.isFallFlying()) setNoGravity(false);
        }
        setInvulnerable(true);
        clearFire();
        // Repair direct health-data writes without reviving or re-adding an entity.
        // Legitimate removal remains final, including chunk unloads and transfers.
        if (!isRemoved()) {
            if (super.getHealth() != 20.0F) super.setHealth(20.0F);
            dead = false;
            deathTime = 0;
            hurtTime = 0;
        }
        super.tick();
        if (isInteractionHeld()) {
            getNavigation().stop();
            setDeltaMovement(0, isNoGravity() ? 0 : getDeltaMovement().y, 0);
        }
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            updateEnvironmentReaction(server);
            TwoIDollService.tickCompanion(this);
            if (isRemoved()) return;
            tickDollFriendship(server);
            tickCompanionship(server);
            if (prankCooldown > 0) prankCooldown--;
            if (patCooldown > 0) patCooldown--;
            if (giftCooldown > 0) giftCooldown--;
            if (talkCooldown > 0) talkCooldown--;
            if (tickCount % 20 == 0) storySessions.values().removeIf(session -> session.expired(server.getGameTime()));
            if (timeStopCooldown > 0) timeStopCooldown--;
            if (panicCooldown > 0) panicCooldown--;
            if (getPoseAction() != ACTION_NONE && getPoseActionProgress(0.0F) >= 1.0F) {
                playPoseAction(ACTION_NONE);
            }
            if (getPoseAction() == ACTION_NONE && tickCount % 20 == 0
                    && server.getRandom().nextInt(18) == 0) {
                int[] idle = {ACTION_SWING_FEET, ACTION_CURIOUS, ACTION_GREETING, ACTION_STRETCH, ACTION_DUST, ACTION_SHY};
                playPoseAction(idle[server.getRandom().nextInt(idle.length)]);
            }
            if (!isInteractionHeld() && prankCooldown == 0 && tickCount % 20 == 0) {
                performPrank(server);
                prankCooldown = prankCooldownForBond(giftScore, server.getRandom());
            }
        }
    }

    private void updateEnvironmentReaction(ServerLevel server) {
        int flags = TwoIDollEnvironment.conditions(server, blockPosition());
        if (flags != getEnvironmentAction()) {
            entityData.set(ENVIRONMENT_ACTION, flags);
            entityData.set(ENVIRONMENT_STARTED_AT, server.getGameTime());
        }
        long now = server.getGameTime();
        // Same deterministic world-time schedule as the visible sneeze; never a reload-triggered sound.
        if ((flags & TwoIDollEnvironment.COLD) != 0
                && Math.floorMod(now + getUUID().hashCode(), 420) == 18) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.62D, getZ(), 4,
                    0.08D, 0.04D, 0.08D, 0.01D);
            playSound(SoundEvents.PLAYER_HURT_FREEZE, 0.22F, 1.8F);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public void setInvulnerable(boolean invulnerable) {
        super.setInvulnerable(true);
    }

    @Override
    public float getHealth() {
        // Direct setHealth/health-data attacks must not poison AI/interactions.
        return 20.0F;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // Manaita's launch transformer replaces LivingEntity.getHealth calls
        // with a helper that reads DATA_HEALTH_ID directly. Fix that backing
        // value immediately, before observers can infer a dead/NaN doll. The
        // second notification sees 20 and stops; no per-tick packets or revival.
        var healthKey = com.sorrowmist.useless.stretcher.mixin.LivingHealthDataAccessor.uselessStretcher$healthKey();
        if (key.equals(healthKey) && getEntityData().get(healthKey) != 20.0F) {
            getEntityData().set(healthKey, 20.0F);
        }
        if (key.equals(DATA_SHARED_FLAGS_ID)) {
            byte flags = getEntityData().get(DATA_SHARED_FLAGS_ID);
            if ((flags & 32) != 0) getEntityData().set(DATA_SHARED_FLAGS_ID, (byte) (flags & ~32));
        } else if (key.equals(DATA_POSE) && getPose() == net.minecraft.world.entity.Pose.DYING) {
            getEntityData().set(DATA_POSE, net.minecraft.world.entity.Pose.STANDING);
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void setInvisible(boolean invisible) {
        // Forced-kill fallbacks often hide a target after its health/removal guards reject death.
        super.setInvisible(false);
    }

    @Override
    public void setPose(net.minecraft.world.entity.Pose pose) {
        if (pose != net.minecraft.world.entity.Pose.DYING) super.setPose(pose);
    }

    /** Reject exile/NaN before the final setPosRaw updates section indexes or loads a remote chunk. */
    public boolean blocksInvalidPosition(double x, double y, double z) {
        return isAddedToWorld() && !isRemoved() && (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || Math.abs(x) > 30_000_000D || Math.abs(z) > 30_000_000D
                || y < level().getMinBuildHeight() - 16D);
    }

    @Override
    public void handleEntityEvent(byte id) {
        // A death animation packet must not make a healthy, protected doll look killed.
        if (id != 3) super.handleEntityEvent(id);
    }

    @Override
    public void setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback callback) {
        if (callback == net.minecraft.world.level.entity.EntityInLevelCallback.NULL && TwoILifecycleGuard.blocks(this)) return;
        super.setLevelCallback(callback);
    }

    @Override
    public boolean isAlive() {
        // Health cannot kill this mascot; actual lifecycle removal still can.
        return !isRemoved();
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(20.0F);
    }

    @Override
    public double getAttributeValue(net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        // Some instant-kill tools lower MAX_HEALTH before zeroing synchronized health.
        return attribute == Attributes.MAX_HEALTH ? 20.0D : super.getAttributeValue(attribute);
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

    @Override
    protected void tickDeath() {
        deathTime = 0;
        dead = false;
    }

    /** Used by the final Entity.setRemoved mixin as well as the normal remove path. */
    public boolean blocksDestructiveRemoval(Entity.RemovalReason reason) {
        return isAddedToWorld() && !authorizedReclaim && !TwoILifecycleGuard.allows(this)
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!blocksDestructiveRemoval(reason)) super.remove(reason);
    }

    /** Call only after the owning workflow authorizes a recall/conversion; never respawns. */
    public boolean discardForReclaim() {
        if (isRemoved()) return false;
        authorizedReclaim = true;
        try {
            // Avoid LivingEntity.remove's death-effect callbacks during a normal recall.
            setRemoved(Entity.RemovalReason.DISCARDED);
            return isRemoved();
        } finally {
            authorizedReclaim = false;
        }
    }

    @Override
    public void die(DamageSource source) {
        // The mascot is intentionally never killed by damage, commands, or accidents.
    }

    @Override
    public void kill() {
        // Keep the entity alive; the dialogue action is the supported way to reclaim it.
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    /** Keep the collision box ray-pickable so a player can open the dialogue. */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isInteractionHeld() && !hasSharedUmbrella();
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public void openDialogue(ServerPlayer player) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.owner_only"), true);
            return;
        }
        if (!canReadStory(player) || distanceToSqr(player) > 64.0D) return;
        keepInteraction(player, true);
        getLookControl().setLookAt(player, 45.0F, 45.0F);
        playPoseAction(ACTION_CURIOUS);
        Network.sendTwoIDialogue(player, getId(), canInvite(), giftScore);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(POSE_ACTION, ACTION_NONE);
        this.entityData.define(POSE_STARTED_AT, 0L);
        this.entityData.define(ENVIRONMENT_ACTION, ACTION_NONE);
        this.entityData.define(ENVIRONMENT_STARTED_AT, 0L);
        this.entityData.define(SHARED_UMBRELLA, false);
        this.entityData.define(SHARED_UMBRELLA_HOLDER, true);
        this.entityData.define(BUBBLE_TEXT, "");
        this.entityData.define(BUBBLE_UNTIL, 0L);
        this.entityData.define(BUBBLE_MESSAGE, Component.empty());
    }

    public int getPoseAction() {
        return entityData.get(POSE_ACTION);
    }

    public int getEnvironmentAction() {
        return entityData.get(ENVIRONMENT_ACTION);
    }

    /** Translation key for the short head bubble; empty means no bubble. */
    public String getBubbleText() {
        if (entityData.get(BUBBLE_TEXT).isEmpty() || level().getGameTime() > entityData.get(BUBBLE_UNTIL)) return "";
        return entityData.get(BUBBLE_TEXT);
    }

    public Component getBubbleMessage() {
        return getBubbleText().isEmpty() ? Component.empty() : entityData.get(BUBBLE_MESSAGE);
    }

    private void setBubbleText(String key, Object... arguments) {
        if (level().isClientSide) return;
        entityData.set(BUBBLE_TEXT, key == null ? "" : key);
        entityData.set(BUBBLE_MESSAGE, key == null ? Component.empty() : Component.translatable(key, arguments));
        entityData.set(BUBBLE_UNTIL, level().getGameTime() + 200L);
    }

    public float getEnvironmentActionProgress(float partialTick) {
        float elapsed = Math.max(0.0F, level().getGameTime() - entityData.get(ENVIRONMENT_STARTED_AT) + partialTick);
        return Math.min(1.0F, elapsed / 40.0F);
    }

    public float getPoseActionProgress(float partialTick) {
        int duration = switch (getPoseAction()) {
            case ACTION_GREETING -> 36;
            case ACTION_CURIOUS -> 42;
            case ACTION_PATTED -> 40;
            case ACTION_RECEIVE_GIFT -> 48;
            case ACTION_INVITE -> 38;
            case ACTION_SWING_FEET, ACTION_STRETCH, ACTION_DUST, ACTION_SHY -> 64;
            default -> 1;
        };
        long now = level().getGameTime();
        float elapsed = Math.max(0.0F, now - entityData.get(POSE_STARTED_AT) + partialTick);
        return Math.min(1.0F, elapsed / duration);
    }

    void playPoseAction(int action) {
        if (level().isClientSide) return;
        entityData.set(POSE_ACTION, action);
        entityData.set(POSE_STARTED_AT, level().getGameTime());
    }

    public boolean patBy(ServerPlayer player) {
        if (!canReadStory(player) || distanceToSqr(player) > 64.0D || patCooldown > 0) return false;
        patCooldown = 30;
        refreshInteractionDay();
        boolean rewarded = interaction.pats() < 1;
        if (rewarded) {
            changeBond(1);
            interaction = new TwoIDollMemory.Interaction(interaction.day(), interaction.pats() + 1,
                    interaction.talks(), interaction.stories(), interaction.companionship(), interaction.companionTicks(),
                    interaction.lastChat(), interaction.lastGift(), interaction.repeatedGift(), interaction.gifts());
        }
        getLookControl().setLookAt(player, 45.0F, 45.0F);
        playPoseAction(ACTION_PATTED);
        if (level() instanceof ServerLevel server) {
            // Self-contained original synthesized melodies; no external doll mod or audio assets.
            var sound = com.sorrowmist.useless.stretcher.init.ModSounds.TWO_I_PAT.get();
            server.playSound(null, blockPosition(), sound,
                    SoundSource.NEUTRAL, 0.8F, 1.05F + random.nextFloat() * 0.2F);
        }
        player.displayClientMessage(Component.translatable(rewarded ? "msg.useless_stretcher.two_i.patted"
                : "msg.useless_stretcher.two_i.patted_familiar"), true);
        Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) patBy(serverPlayer);
            else openDialogue(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    public void reclaim(ServerPlayer player) {
        if (distanceToSqr(player) > 64.0D || isRemoved()) return;
        if (ownerId == null || !ownerId.equals(player.getUUID())) return;
        ItemStack stack = toDollItem();
        ItemStack acquired = stack.copy();
        if (!discardForReclaim()) return;
        ItemStackFactory.give(player, stack);
        com.sorrowmist.useless.stretcher.content.item.TwoIDollAcquisition.notifyAcquired(player, acquired);
    }

    public void reclaimRemote(ServerPlayer player) {
        if (isRemoved() || ownerId == null || !ownerId.equals(player.getUUID())) return;
        ItemStack stack = toDollItem();
        ItemStack acquired = stack.copy();
        if (!discardForReclaim()) return;
        ItemStackFactory.give(player, stack);
        com.sorrowmist.useless.stretcher.content.item.TwoIDollAcquisition.notifyAcquired(player, acquired);
        player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.reclaimed"), true);
    }

    public void acceptGift(ServerPlayer player) {
        if (!canReadStory(player)) return;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            setBubbleText("msg.useless_stretcher.two_i.no_gift");
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.no_gift"), true);
            return;
        }
        if (isCockroachGift(stack)) {
            stack.shrink(1);
            giftScore = MIN_BOND;
            giftCooldown = 0;
            playPoseAction(ACTION_CURIOUS);
            player.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.two_i.cockroach_panic"), true);
            if (panicCooldown == 0) {
                panicCooldown = PANIC_COOLDOWN;
                panicDragonSpawned = false;
                spawnPanicDragon(player);
                TimeStopManager.startPanic(player);
            }
            Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
            return;
        }
        boolean specialGift = isSpecialGift(stack);
        int bondChange = giftBondChange(stack);
        refreshInteractionDay();
        // Precious gifts have no cooldown or daily limit; the budget is for ordinary gifts only.
        if (!specialGift && bondChange > 0 && interaction.gifts() >= 1) {
            acceptGiftWithoutBond(player, stack, "msg.useless_stretcher.two_i.gift_daily_limit");
            return;
        }
        if (!specialGift && giftCooldown > 0) {
            acceptGiftWithoutBond(player, stack, "msg.useless_stretcher.two_i.gift_cooldown");
            return;
        }
        String giftId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int repetitions = giftId.equals(interaction.lastGift()) ? Math.min(4, interaction.repeatedGift() + 1) : 1;
        boolean repeated = !specialGift && bondChange > 0 && repetitions >= 3;
        if (repeated) {
            acceptGiftWithoutBond(player, stack, "msg.useless_stretcher.two_i.gift_repeated");
            return;
        }
        if (giftScore >= MAX_BOND && bondChange > 0) {
            setBubbleText("msg.useless_stretcher.two_i.gift_full");
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.gift_full"), true);
            return;
        }
        stack.shrink(1);
        interaction = new TwoIDollMemory.Interaction(interaction.day(), interaction.pats(),
                interaction.talks(), interaction.stories(), interaction.companionship(), interaction.companionTicks(),
                interaction.lastChat(), giftId, repetitions, !specialGift && bondChange > 0 ? 1 : interaction.gifts());
        changeBond(bondChange);
        if (!specialGift) giftCooldown = GIFT_COOLDOWN_TICKS;
        playPoseAction(ACTION_RECEIVE_GIFT);
        setBubbleText("msg.useless_stretcher.two_i.gift." + (bondChange > 0 ? "liked" : "disliked"),
                Math.abs(bondChange), giftScore, MAX_BOND);
        player.displayClientMessage(bondChange == 0
                ? Component.translatable("msg.useless_stretcher.two_i.gift.neutral", giftScore, MAX_BOND)
                : Component.translatable("msg.useless_stretcher.two_i.gift." + (bondChange > 0 ? "liked" : "disliked"),
                        Math.abs(bondChange), giftScore, MAX_BOND), true);
        if (repeated) player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.gift_repeated"), false);
        Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
    }

    private void acceptGiftWithoutBond(ServerPlayer player, ItemStack stack, String bubbleKey) {
        stack.shrink(1);
        playPoseAction(ACTION_RECEIVE_GIFT);
        setBubbleText(bubbleKey);
        player.displayClientMessage(Component.translatable(bubbleKey), true);
        Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
    }

    public void talkTo(ServerPlayer player) {
        if (!canReadStory(player)) return;
        if (talkCooldown > 0) {
            setBubbleText("msg.useless_stretcher.two_i.talk_cooldown", Math.max(1, (talkCooldown + 19) / 20));
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.talk_cooldown",
                    Math.max(1, (talkCooldown + 19) / 20)), true);
            return;
        }
        talkCooldown = TALK_COOLDOWN_TICKS;
        refreshInteractionDay();
        int line;
        // Common chatter plus relationship-specific lines. Never penalize a random line.
        do {
            line = random.nextInt(4) == 0 ? (giftScore < 0 ? 34 : giftScore >= 70 ? 38 : 30) + random.nextInt(4)
                    : random.nextInt(30);
        } while (line == interaction.lastChat());
        int bondChange = interaction.talks() < 2 ? 1 : 0;
        changeBond(bondChange);
        interaction = new TwoIDollMemory.Interaction(interaction.day(), interaction.pats(),
                Math.min(2, interaction.talks() + 1), interaction.stories(), interaction.companionship(),
                interaction.companionTicks(), line, interaction.lastGift(), interaction.repeatedGift(), interaction.gifts());
        getLookControl().setLookAt(player, 45.0F, 45.0F);
        playPoseAction(bondChange > 0 ? ACTION_GREETING : ACTION_CURIOUS);
        setBubbleText("msg.useless_stretcher.two_i.talk." + line);
        if (bondChange == 0) entityData.set(BUBBLE_MESSAGE, getBubbleMessage().copy().append("\n")
                .append(Component.translatable("msg.useless_stretcher.two_i.talk_daily_limit")));
        player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.talk." + line), false);
        Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
    }

    private static boolean isSpecialGift(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && SPECIAL_GIFTS.contains(key.toString());
    }

    private static int giftBondChange(ItemStack stack) {
        if (isSpecialGift(stack)) return 5;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return 0;
        String path = key.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("ginger")) return -6;
        if (path.contains("roach") || path.contains("cockroach")) return -5;
        if (isBugGift(path)) return -2;
        if (path.contains("flower") || path.contains("cookie") || path.contains("cake")
                || path.contains("honey") || path.contains("berry") || path.contains("berries") || path.contains("sweet")
                || path.contains("chocolate") || path.contains("candy")) return 1;
        if (Set.of("minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
                "minecraft:netherite_ingot", "minecraft:golden_apple", "minecraft:enchanted_golden_apple")
                .contains(key.toString())) return 1;
        return 1;
    }

    private static boolean isCockroachGift(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        String id = key.toString().toLowerCase(Locale.ROOT);
        String path = key.getPath().toLowerCase(Locale.ROOT);
        return id.contains("cockroach") || id.contains("roach")
                || path.contains("蜚蠊") || path.contains("蟑螂")
                || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains("cockroach")
                || stack.getHoverName().getString().contains("蟑螂")
                || stack.getHoverName().getString().contains("蜚蠊");
    }

    private void spawnPanicDragon(ServerPlayer player) {
        if (panicDragonSpawned || !(level() instanceof ServerLevel server)) return;
        Entity dragon = EntityType.ENDER_DRAGON.create(server);
        if (dragon == null) return;
        Vec3 look = player.getLookAngle().normalize();
        Vec3 spawn = player.position().add(look.x * 12.0D, 3.0D, look.z * 12.0D);
        double y = Math.max(server.getMinBuildHeight() + 8.0D,
                Math.min(server.getMaxBuildHeight() - 8.0D, spawn.y));
        dragon.moveTo(spawn.x, y, spawn.z, player.getYRot(), 0.0F);
        dragon.getPersistentData().putUUID(PRANK_OWNER_TAG, player.getUUID());
        if (server.addFreshEntity(dragon)) panicDragonSpawned = true;
    }

    private static boolean isBugGift(String path) {
        // Match words rather than substrings: beef, beetroot and enchanted items are not insects.
        Set<String> bugs = Set.of("roach", "cockroach", "insect", "bug", "beetle", "spider", "ant",
                "termite", "mosquito", "wasp", "bee", "beehive", "cricket", "centipede", "millipede",
                "locust", "mantis", "scorpion", "fly", "butterfly", "caterpillar", "moth", "silverfish");
        for (String word : path.split("[/_\\-.]")) if (bugs.contains(word)) return true;
        return false;
    }

    public boolean canInvite() {
        return com.sorrowmist.useless.stretcher.config.StretcherConfig.starfieldInvitationEnabled()
                && (giftScore >= MAX_BOND || invitationUsed);
    }

    public void acceptInvitation(ServerPlayer player) {
        if (!canReadStory(player)) return;
        if (!canInvite()) return;
        boolean usedBefore = invitationUsed;
        // Set before vanilla serializes this entity for its dimension transfer.
        invitationUsed = true;
        if (!TwoIDollService.sendPlayerToStarfield(player, !usedBefore, this)) invitationUsed = usedBefore;
    }

    private static int prankCooldownForBond(int bond, RandomSource random) {
        int clampedBond = clampBond(bond);
        int normalizedBond = clampedBond - MIN_BOND;
        int range = MAX_PRANK_COOLDOWN - MIN_PRANK_COOLDOWN;
        int baseline = MIN_PRANK_COOLDOWN + range * normalizedBond / (MAX_BOND - MIN_BOND);
        return baseline + random.nextInt(Math.max(1, baseline / 4));
    }

    private void performPrank(ServerLevel server) {
        RandomSource random = server.getRandom();
        playPoseAction(ACTION_GREETING);
        int intensity = Math.min(4, Math.max(0, -giftScore / 25));
        int action;
        // These extra actions mirror safe parts of the base staff. They are cosmetic or
        // reversible, so 2i never breaks blocks, opens machines, or changes containers.
        do { action = random.nextInt(12); } while (action == lastPrank);
        lastPrank = action;
        ServerPlayer owner = ownerId == null ? null : server.getServer().getPlayerList().getPlayer(ownerId);
        Vec3 center = owner != null && owner.serverLevel() == server ? owner.position() : position();

        if (action == 1) {
            spawnPrankMobs(server, center, intensity, random);
        } else if (action == 3) {
            performToolPrank(server, owner, center, random);
        } else if (action == 4) {
            performMagnetPrank(server, owner, center);
        } else if (action == 5) {
            performBuffPrank(owner);
        } else if (action == 6) {
            performSparkPrank(server, center, random);
        } else if (action == 7) {
            performBlinkPrank(server, owner, center, random);
        } else if (action == 8) {
            performSoundPrank(server, center, random);
        } else if (action == 9) {
            performEmotionPrank(server, owner, center);
        } else if (action == 10) {
            performGlowPrank(server, center);
        } else if (action == 11) {
            performTrailPrank(server, owner, center);
        } else {
            ParticleOptions particle = action == 0 ? ParticleTypes.WITCH : ParticleTypes.END_ROD;
            int count = 8 + intensity * 8;
            server.sendParticles(particle, center.x, center.y + 1.0D, center.z,
                    count, 0.45D + intensity * 0.08D, 0.55D, 0.45D + intensity * 0.08D, 0.02D);
            server.playSound(null, BlockPos.containing(center),
                    action == 0 ? SoundEvents.ENDERMAN_TELEPORT : SoundEvents.VILLAGER_AMBIENT,
                    SoundSource.NEUTRAL, 0.35F + intensity * 0.08F,
                    0.8F + random.nextFloat() * (0.25F + intensity * 0.08F));
        }

        if (owner != null && timeStopCooldown == 0 && StretcherConfig.serverTimeStop()
                && random.nextInt(TIME_STOP_CHANCE_DENOMINATOR) == 0
                && TimeStopManager.start(owner)) {
            timeStopCooldown = TIME_STOP_COOLDOWN;
            owner.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.time_stop_prank"), true);
        }
        if (owner != null) owner.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.prank"), true);
    }

    /** Safe visual version of the staff's shears, brush, and mining-tool mischief. */
    private void performToolPrank(ServerLevel server, ServerPlayer owner, Vec3 center, RandomSource random) {
        BlockPos origin = BlockPos.containing(center);
        boolean foundLeaves = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 3, 3))) {
            BlockState state = server.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES)) continue;
            foundLeaves = true;
            server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 8, 0.35D, 0.35D, 0.35D, 0.02D);
        }
        if (!foundLeaves) {
            server.sendParticles(ParticleTypes.CRIT, center.x, center.y + 1.0D, center.z,
                    14, 0.45D, 0.6D, 0.45D, 0.03D);
        }
        server.playSound(null, origin, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.55F,
                1.25F + random.nextFloat() * 0.15F);
        if (owner != null) owner.displayClientMessage(Component.translatable(
                "msg.useless_stretcher.two_i.staff_prank.tool"), true);
    }

    /** Reuses the staff's magnet idea without moving items into an inventory or deleting them. */
    private static void performMagnetPrank(ServerLevel server, ServerPlayer owner, Vec3 center) {
        AABB area = new AABB(center, center).inflate(8.0D);
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, area)) {
            Vec3 delta = center.add(0.0D, 0.8D, 0.0D).subtract(item.position());
            if (delta.lengthSqr() < 0.01D) continue;
            item.setDeltaMovement(item.getDeltaMovement().scale(0.55D)
                    .add(delta.normalize().scale(0.08D)));
            item.hasImpulse = true;
        }
        server.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.8D, center.z,
                12, 0.55D, 0.35D, 0.55D, 0.025D);
        if (owner != null) owner.displayClientMessage(Component.translatable(
                "msg.useless_stretcher.two_i.staff_prank.magnet"), true);
    }

    private void performBuffPrank(ServerPlayer owner) {
        if (owner == null) return;
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, true, false, true));
        owner.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 0, true, false, true));
        owner.displayClientMessage(Component.translatable(
                "msg.useless_stretcher.two_i.staff_prank.buff"), true);
    }

    private static void performSparkPrank(ServerLevel server, Vec3 center, RandomSource random) {
        server.sendParticles(ParticleTypes.FLAME, center.x, center.y + 1.0D, center.z,
                18, 0.35D, 0.5D, 0.35D, 0.025D);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 1.0D, center.z,
                12, 0.45D, 0.55D, 0.45D, 0.04D);
        server.playSound(null, BlockPos.containing(center), SoundEvents.FLINTANDSTEEL_USE,
                SoundSource.NEUTRAL, 0.45F, 1.0F + random.nextFloat() * 0.2F);
    }

    /** A harmless blink that puts 2i somewhere visible beside its owner. */
    private void performBlinkPrank(ServerLevel server, ServerPlayer owner, Vec3 center, RandomSource random) {
        if (owner != null && owner.serverLevel() == server) {
            Vec3 side = owner.position().add(owner.getLookAngle().cross(new Vec3(0, 1, 0))
                    .normalize().scale(random.nextBoolean() ? 1.5D : -1.5D));
            double y = owner.getY();
            BlockPos feet = BlockPos.containing(side.x, y, side.z);
            if (server.isEmptyBlock(feet) && server.isEmptyBlock(feet.above())) {
                moveTo(side.x, y, side.z, getYRot() + 180.0F, getXRot());
            }
        }
        server.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.6D, getZ(),
                16, 0.3D, 0.45D, 0.3D, 0.08D);
        server.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.NEUTRAL, 0.35F, 1.25F + random.nextFloat() * 0.15F);
    }

    /** Plays a fake creature sound without spawning or modifying anything. */
    private static void performSoundPrank(ServerLevel server, Vec3 center, RandomSource random) {
        var sounds = new net.minecraft.sounds.SoundEvent[] {SoundEvents.VILLAGER_AMBIENT, SoundEvents.ALLAY_AMBIENT_WITH_ITEM,
                SoundEvents.ENDER_PEARL_THROW, SoundEvents.EXPERIENCE_ORB_PICKUP};
        server.playSound(null, BlockPos.containing(center), sounds[random.nextInt(sounds.length)],
                SoundSource.NEUTRAL, 0.45F, 0.7F + random.nextFloat() * 0.6F);
        server.sendParticles(ParticleTypes.NOTE, center.x, center.y + 1.2D, center.z,
                8, 0.35D, 0.4D, 0.35D, 0.02D);
    }

    /** Gives the owner a tiny, reversible reaction and makes 2i visibly celebrate. */
    private static void performEmotionPrank(ServerLevel server, ServerPlayer owner, Vec3 center) {
        if (owner != null) {
            owner.addEffect(new MobEffectInstance(MobEffects.JUMP, 70, 0, true, false, true));
            owner.addEffect(new MobEffectInstance(MobEffects.GLOWING, 70, 0, true, false, true));
        }
        server.sendParticles(ParticleTypes.HEART, center.x, center.y + 1.0D, center.z,
                10, 0.45D, 0.55D, 0.45D, 0.02D);
    }

    /** Makes one nearby ordinary mob glow briefly, without changing its AI or position. */
    private static void performGlowPrank(ServerLevel server, Vec3 center) {
        AABB area = new AABB(center, center).inflate(8.0D);
        List<Mob> mobs = server.getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && !(mob instanceof TwoIDollEntity));
        if (!mobs.isEmpty()) {
            Mob target = mobs.get(server.getRandom().nextInt(mobs.size()));
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false, true));
            server.sendParticles(ParticleTypes.GLOW, target.getX(), target.getY() + target.getBbHeight(),
                    target.getZ(), 10, 0.3D, 0.25D, 0.3D, 0.01D);
        } else {
            server.sendParticles(ParticleTypes.GLOW, center.x, center.y + 1.0D, center.z,
                    10, 0.4D, 0.5D, 0.4D, 0.01D);
        }
    }

    /** Leaves a small, bounded trail of particles around the owner as a visual trick. */
    private static void performTrailPrank(ServerLevel server, ServerPlayer owner, Vec3 center) {
        Vec3 origin = owner == null ? center : owner.position();
        server.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y + 0.15D, origin.z,
                12, 0.65D, 0.08D, 0.65D, 0.015D);
        server.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, origin.x, origin.y + 1.0D, origin.z,
                8, 0.55D, 0.5D, 0.55D, 0.01D);
    }

    /** Opening a story never rolls the ordinary chat's random bond reward. */
    public void startStory(ServerPlayer player) {
        if (!canReadStory(player)) {
            Network.sendTwoIStoryState(player, getId(), 0L, 0, giftScore, 2);
            return;
        }
        long now = level().getGameTime();
        keepInteraction(player, true);
        TwoIStorySession session = storySessions.get(player.getUUID());
        if (session != null && session.expired(now)) {
            storySessions.remove(player.getUUID());
            session = null;
        }
        // Reopening an unfinished conversation resumes the same unspent session.
        if (session == null) {
            if (talkCooldown > 0) {
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i.talk_cooldown",
                        Math.max(1, (talkCooldown + 19) / 20)), true);
                Network.sendTwoIStoryState(player, getId(), 0L, 0, giftScore, 2);
                return;
            }
            TwoIStoryData progress = TwoIStoryData.get(player.serverLevel().getServer());
            int chapter = progress.nextChapter(player.getUUID(), giftScore);
            session = new TwoIStorySession(player.getUUID(), chapter, now);
            storySessions.put(player.getUUID(), session);
            talkCooldown = TALK_COOLDOWN_TICKS;
        }
        getLookControl().setLookAt(player, 45.0F, 45.0F);
        playPoseAction(ACTION_CURIOUS);
        Network.sendTwoIStoryState(player, getId(), session.id(), session.chapter(), giftScore, 0, session.expectedRound());
    }

    private boolean canReadStory(ServerPlayer player) {
        return !level().isClientSide && !isRemoved() && isAlive() && player.isAlive()
                && isOwner(player) && level() == player.level() && distanceToSqr(player) <= 100.0D;
    }

    public boolean isOwner(Player player) {
        return ownerId != null && ownerId.equals(player.getUUID());
    }

    /** Only the starter can consume a live session, and its reward is applied once. */
    public void storyChoice(ServerPlayer player, long sessionId, int choice) {
        TwoIStorySession session = storySessions.get(player.getUUID());
        if (!canReadStory(player) || session == null
                || !session.accept(player.getUUID(), sessionId, choice, level().getGameTime())) {
            Network.sendTwoIStoryState(player, getId(), sessionId, 0, giftScore, 2, TwoIStorySession.choiceRound(choice));
            return;
        }
        if (session.finished()) storySessions.remove(player.getUUID());
        int delta = TwoIStorySession.bondDelta(TwoIStorySession.choiceIndex(choice));
        refreshInteractionDay();
        if (delta > 0) {
            if (interaction.stories() >= 3) delta = 0;
            else interaction = new TwoIDollMemory.Interaction(interaction.day(), interaction.pats(),
                    interaction.talks(), interaction.stories() + 1, interaction.companionship(),
                    interaction.companionTicks(), interaction.lastChat(), interaction.lastGift(), interaction.repeatedGift(), interaction.gifts());
        }
        changeBond(delta);
        talkCooldown = Math.max(talkCooldown, TALK_COOLDOWN_TICKS);
        if (session.finished()) TwoIStoryData.get(player.serverLevel().getServer()).complete(player.getUUID(), session.chapter());
        getLookControl().setLookAt(player, 45.0F, 45.0F);
        playPoseAction(delta < 0 ? ACTION_CURIOUS : ACTION_GREETING);
        Network.sendTwoIStoryState(player, getId(), sessionId, session.chapter(), giftScore, 1, TwoIStorySession.choiceRound(choice));
        Network.updateTwoIDialogue(player, getId(), canInvite(), giftScore);
    }

    private void spawnPrankMobs(ServerLevel server, Vec3 center, int intensity, RandomSource random) {
        if (ownerId == null) return;
        long nearbySummons = server.getEntities(this, getBoundingBox().inflate(48.0D),
                entity -> entity.getPersistentData().hasUUID(PRANK_OWNER_TAG)
                        && ownerId.equals(entity.getPersistentData().getUUID(PRANK_OWNER_TAG))).size();
        if (nearbySummons >= 6) return;

        int count = 1 + intensity / 2;
        int attemptsPerMob = 4;
        List<EntityType<?>> candidates = PRANK_MOB_TIERS.get(Math.min(3, intensity));
        for (int i = 0; i < count; i++) {
            if (nearbySummons >= 6) break;
            EntityType<?> type = candidates.get(random.nextInt(candidates.size()));
            if (!isSafePrankMob(type)) continue;
            Entity created = type.create(server);
            if (!(created instanceof Mob mob)) {
                if (created != null) created.discard();
                continue;
            }
            mob.setNoAi(false);

            for (int attempt = 0; attempt < attemptsPerMob; attempt++) {
                int radius = 1 + intensity / 2;
                BlockPos feet = BlockPos.containing(center.x + random.nextInt(radius * 2 + 1) - radius,
                        center.y, center.z + random.nextInt(radius * 2 + 1) - radius);
                if (!server.hasChunkAt(feet) || !server.getBlockState(feet.below())
                        .isFaceSturdy(server, feet.below(), net.minecraft.core.Direction.UP)
                        || !server.isEmptyBlock(feet) || !server.isEmptyBlock(feet.above())) continue;
                created.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D,
                        random.nextFloat() * 360.0F, 0.0F);
                created.getPersistentData().putUUID(PRANK_OWNER_TAG, ownerId);
                if (server.addFreshEntity(created)) {
                    nearbySummons++;
                    break;
                }
            }
            if (!created.isAddedToWorld()) created.discard();
        }
    }

    private static boolean isSafePrankMob(EntityType<?> type) {
        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER
                || PRANK_MOB_TIERS.stream().noneMatch(tier -> tier.contains(type))) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return false;
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return !path.contains("dragon") && !path.contains("chaos") && !path.contains("boss");
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        itemTemplate = ItemStack.EMPTY;
        interaction = TwoIDollMemory.Interaction.empty();
        patCooldown = 0;
        ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        // New saves use the signed -100..100 bond field. Older 1.5.x saves
        // stored a 0..10 gift counter, so preserve their relative progress.
        giftScore = tag.contains("bond")
                ? clampBond(tag.getInt("bond"))
                : clampBond(Math.max(0, tag.getInt("gift_score")) * 10);
        invitationUsed = tag.getBoolean("invitation_used");
        prankCooldown = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.contains("prank_cooldown") ? tag.getInt("prank_cooldown") : 1200, 0, 24000);
        lastPrank = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("last_prank"), -1, 11);
        timeStopCooldown = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("time_stop_cooldown"), 0, 24000);
        panicCooldown = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("panic_cooldown"), 0, 24000);
        panicDragonSpawned = tag.getBoolean("panic_dragon_spawned");
        giftCooldown = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("gift_cooldown"), 0, 24000);
        talkCooldown = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("talk_cooldown"), 0, 24000);
        if (tag.contains("doll_item")) {
            com.sorrowmist.useless.compat.forge1201.nbt.NativeStackNbt.parseItem(level().registryAccess(), tag.getCompound("doll_item")).ifPresent(this::restoreFromItem);
        }
        storySessions.clear();
        // Flight follows a live owner, never a stale saved NoGravity flag after restart.
        setNoGravity(false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("doll_item", com.sorrowmist.useless.compat.forge1201.nbt.NativeStackNbt.saveItem(toDollItem(), level().registryAccess()));
        if (ownerId != null) tag.putUUID("owner", ownerId);
        tag.putInt("bond", giftScore);
        tag.putBoolean("invitation_used", invitationUsed);
        tag.putInt("prank_cooldown", prankCooldown);
        tag.putInt("last_prank", lastPrank);
        tag.putInt("time_stop_cooldown", timeStopCooldown);
        tag.putInt("panic_cooldown", panicCooldown);
        tag.putBoolean("panic_dragon_spawned", panicDragonSpawned);
        tag.putInt("gift_cooldown", giftCooldown);
        tag.putInt("talk_cooldown", talkCooldown);
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.useless_stretcher.two_i_doll");
    }

    private static final class ItemStackFactory {
        private static void give(ServerPlayer player, ItemStack stack) {
            if (!player.getInventory().add(stack) && !stack.isEmpty())
                player.drop(stack, false);
            player.getInventory().setChanged();
        }
    }

    private static final class FollowOwnerGoal extends Goal {
        private static final double START_DISTANCE_SQR = 100.0D;
        private static final double STOP_DISTANCE_SQR = 16.0D;
        private final TwoIDollEntity doll;
        private Player owner;
        private int recalc;

        private FollowOwnerGoal(TwoIDollEntity doll) {
            this.doll = doll;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!(doll.level() instanceof ServerLevel level) || doll.ownerId == null) return false;
            owner = level.getServer().getPlayerList().getPlayer(doll.ownerId);
            return !doll.isInteractionHeld() && owner != null && owner.isAlive() && !owner.isSpectator()
                    && level.getServer().getPlayerList().getPlayer(doll.ownerId) == owner
                    && owner.level() == doll.level()
                    && (doll.distanceToSqr(owner) > START_DISTANCE_SQR || doll.isNoGravity() && ownerFlying());
        }

        @Override
        public boolean canContinueToUse() {
            return !doll.isInteractionHeld() && owner != null && owner.isAlive() && !owner.isSpectator()
                    && ((ServerLevel)doll.level()).getServer().getPlayerList().getPlayer(doll.ownerId) == owner
                    && owner.level() == doll.level()
                    && (doll.distanceToSqr(owner) > STOP_DISTANCE_SQR || doll.isNoGravity() && ownerFlying());
        }

        @Override
        public void tick() {
            if (owner == null) return;
            doll.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (ownerFlying()) {
                doll.setNoGravity(true);
                doll.getNavigation().stop();
                doll.fallDistance = 0;
                double distance = doll.distanceToSqr(owner);
                if (distance > 1024.0D && catchUpFlying()) return;
                Vec3 offset = owner.position().add(0, 0.3D, 0).subtract(doll.position());
                Vec3 desired = distance > STOP_DISTANCE_SQR
                        ? offset.normalize().scale(Math.min(1.1D, 0.35D + Math.sqrt(distance) * 0.025D)) : Vec3.ZERO;
                doll.setDeltaMovement(doll.getDeltaMovement().scale(0.65D).add(desired.scale(0.35D)));
                faceOwnerDirectly();
                return;
            }
            doll.setNoGravity(false);
            if (++recalc % 5 != 0) return;
            double distance = doll.distanceToSqr(owner);
            if (distance > 196.0D && catchUp()) return;
            doll.getNavigation().moveTo(owner, distance > 64.0D ? 1.8D : 1.35D);
        }

        private boolean ownerFlying() {
            // Covers creative/ability-based mod flight and elytra, without mistaking a jump for flight.
            return owner != null && (owner.getAbilities().flying || owner.isFallFlying());
        }

        /** LookControl can be one tick behind while a no-gravity mob is airborne. */
        private void faceOwnerDirectly() {
            if (owner == null) return;
            Vec3 target = owner.getEyePosition();
            Vec3 delta = target.subtract(doll.getEyePosition());
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float)(-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
            doll.setYRot(yaw);
            doll.setXRot(pitch);
            doll.yRotO = yaw;
            doll.xRotO = pitch;
            doll.yHeadRot = yaw;
            doll.yHeadRotO = yaw;
            doll.yBodyRot = yaw;
            doll.yBodyRotO = yaw;
        }

        private boolean catchUpFlying() {
            ServerLevel level = (ServerLevel)doll.level();
            for (int attempt = 0; attempt < 12; attempt++) {
                Vec3 destination = owner.position().add(doll.random.nextInt(7) - 3,
                        doll.random.nextInt(3), doll.random.nextInt(7) - 3);
                BlockPos pos = BlockPos.containing(destination);
                if (destination.distanceToSqr(owner.position()) < 4.0D || !level.hasChunkAt(pos)
                        || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !level.getFluidState(pos).isEmpty()
                        || !level.noCollision(doll, doll.getBoundingBox().move(destination.subtract(doll.position())))) continue;
                doll.teleportTo(destination.x, destination.y, destination.z);
                doll.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            return false;
        }

        private boolean catchUp() {
            // Only inspect already loaded, supported, dry spaces. No tickets or terrain edits.
            ServerLevel level = (ServerLevel)doll.level();
            BlockPos center = owner.blockPosition();
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = doll.random.nextInt(7) - 3;
                int z = doll.random.nextInt(7) - 3;
                if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
                BlockPos pos = center.offset(x, doll.random.nextInt(3) - 1, z);
                if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)
                        || !level.getFluidState(pos).isEmpty()) continue;
                Vec3 destination = Vec3.atBottomCenterOf(pos);
                if (!level.noCollision(doll, doll.getBoundingBox().move(destination.subtract(doll.position())))) continue;
                doll.getNavigation().stop();
                doll.teleportTo(destination.x, destination.y, destination.z);
                doll.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            return false;
        }

        @Override
        public void stop() {
            owner = null;
            recalc = 0;
            doll.getNavigation().stop();
            // An open owner panel holds an airborne doll in place too.
            if (!doll.isInteractionHeld()) doll.setNoGravity(false);
            doll.setDeltaMovement(Vec3.ZERO);
        }
    }
}
