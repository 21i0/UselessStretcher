package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.UUID;

/** Real optional weapon entrypoints plus equivalent generic destructive API chains. */
public final class TwoIForcedKillChecks {
    public static void run(ServerLevel level) throws Exception {
        var owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ForcedKillOwner"));
        var attacker=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ForcedKillAttacker"));
        Vec3 origin=new Vec3(12.5,100,12.5);
        owner.setPos(origin);attacker.setPos(origin);
        var doll=ModEntities.TWO_I_DOLL.get().create(level);
        check(doll!=null,"doll factory");
        doll.setOwnerId(owner.getUUID());doll.setPos(origin);
        level.getChunkAt(doll.blockPosition());check(level.addFreshEntity(doll),"registered doll");
        var tickField=ServerLevel.class.getDeclaredField("entityTickList");tickField.setAccessible(true);
        var ticking=(EntityTickList)tickField.get(level);ticking.add(doll);
        UUID uuid=doll.getUUID();int id=doll.getId();
        try {
            if(ModList.get().isLoaded("forever_love_sword")) {
                var util=Class.forName("net.wzz.forever_love_sword.util.ForeverUtil");
                var kill=util.getMethod("killEntity",Entity.class,boolean.class);
                var sword=BuiltInRegistries.ITEM.get(new ResourceLocation("forever_love_sword:forever_love_sword"));
                check(sword.getClass().getName().equals("net.wzz.forever_love_sword.item.ForeverLoveSwordItem"),"actual registered sword");
                var stack=new ItemStack(sword);attacker.setItemInHand(InteractionHand.MAIN_HAND,stack);
                for(boolean direct:new boolean[]{true,false,true}) {
                    kill.invoke(null,doll,direct);
                    preserved(level,doll,ticking,uuid,id,origin,"actual utility killEntity("+direct+")");
                }
                sword.onLeftClickEntity(stack,attacker,doll);
                preserved(level,doll,ticking,uuid,id,origin,"actual direct sword left-click hook");
                attacker.attack(doll);
                preserved(level,doll,ticking,uuid,id,origin,"actual Player.attack with sword");
                var control=EntityType.CHICKEN.create(level);check(control!=null,"ordinary area control");
                control.setPos(origin.add(2,0,0));check(level.addFreshEntity(control),"ordinary area target registered");
                try {
                    sword.releaseUsing(stack,level,attacker,0);
                    preserved(level,doll,ticking,uuid,id,origin,"actual 400-block range releaseUsing");
                    check(control.isDeadOrDying()||control.isRemoved(),"actual area ability still kills ordinary target");
                } finally {control.discard();}
                var directControl=EntityType.CHICKEN.create(level);check(directControl!=null,"ordinary direct control");
                try {
                    kill.invoke(null,directControl,true);
                    check(directControl.isDeadOrDying()||directControl.isRemoved(),"utility still kills ordinary target");
                } finally {directControl.discard();}
                LogUtils.getLogger().info("2I FOREVER LOVE SWORD PASSED: real killEntity true/false/repeated, direct item hook, Player.attack, 400-block area release; original UUID/id/position/visibility/indexes/ticking retained; ordinary controls still die");
            } else LogUtils.getLogger().info("2I FORCED KILL optional sword absent: generic checks still active");

            int sources=0;
            for(var type:level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).holders().toList()) {
                var source=new net.minecraft.world.damagesource.DamageSource(type,attacker);
                for(float amount:new float[]{Float.MAX_VALUE,Float.POSITIVE_INFINITY,Float.NaN}) {
                    check(!doll.hurt(source,amount)&&doll.isInvulnerableTo(source),"every registered damage type ignores finite/infinite/NaN damage");
                }
                sources++;
            }
            var healthField=LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");healthField.setAccessible(true);
            @SuppressWarnings("unchecked") var health=(EntityDataAccessor<Float>)healthField.get(null);
            for(float amount:new float[]{0,-Float.MAX_VALUE,Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY}) {
                doll.setHealth(amount);doll.getEntityData().set(health,amount);
                check(doll.getEntityData().get(health)==20,"health data cannot be poisoned");
            }
            doll.die(level.damageSources().fellOutOfWorld());doll.kill();doll.discard();
            doll.remove(Entity.RemovalReason.KILLED);doll.setRemoved(Entity.RemovalReason.DISCARDED);
            doll.setInvisible(true);doll.setPose(Pose.DYING);doll.onClientRemoval();
            doll.setPos(-999,-999,-999);
            preserved(level,doll,ticking,uuid,id,origin,"generic kill/health/remove/hide/exile chain before repair tick");
            var flagsField=Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");flagsField.setAccessible(true);
            @SuppressWarnings("unchecked") var flags=(EntityDataAccessor<Byte>)flagsField.get(null);
            doll.getEntityData().set(flags,(byte)(doll.getEntityData().get(flags)|32));
            check(!doll.isInvisible()&&(doll.getEntityData().get(flags)&32)==0,"direct invisibility flag normalized immediately");
            var poseField=Entity.class.getDeclaredField("DATA_POSE");poseField.setAccessible(true);
            @SuppressWarnings("unchecked") var pose=(EntityDataAccessor<Pose>)poseField.get(null);
            doll.getEntityData().set(pose,Pose.DYING);
            check(doll.getPose()!=Pose.DYING,"direct tracked death pose rejected immediately");
            for(Vec3 invalid:new Vec3[]{new Vec3(Double.NaN,100,0),new Vec3(0,Double.NEGATIVE_INFINITY,0),
                    new Vec3(0,level.getMinBuildHeight()-1024,0),new Vec3(30_000_001,100,0)}) {
                doll.setPosRaw(invalid.x,invalid.y,invalid.z);
                check(doll.position().equals(origin),"raw invalid/void coordinates rejected");
            }
            Vec3 moved=origin.add(2,3,2);doll.setPos(moved);
            check(doll.position().equals(moved),"ordinary follow/teleport position updates remain usable");
            doll.setPos(origin);
            preserved(level,doll,ticking,uuid,id,origin,"all generic destructive paths");
            storageGuards(level,doll,ticking,uuid,id,origin);
            LogUtils.getLogger().info("2I GENERIC DESTRUCTIVE CHAINS PASSED: {} registered damage types with finite/infinite/NaN damage, health data poisoning, kill/die/discard/final removal, invisibility/pose raw data, invalid/void exile; ordinary movement preserved",sources);
        } finally {doll.discardForReclaim();attacker.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
    }
    private static void storageGuards(ServerLevel level,TwoIDollEntity doll,EntityTickList ticking,UUID uuid,int id,Vec3 origin) throws Exception {
        var field=ServerLevel.class.getDeclaredField("entityManager");field.setAccessible(true);var manager=field.get(level);
        var lookupField=manager.getClass().getDeclaredField("visibleEntityStorage");lookupField.setAccessible(true);
        @SuppressWarnings("unchecked") var lookup=(net.minecraft.world.level.entity.EntityLookup<Entity>)lookupField.get(manager);
        var sectionField=manager.getClass().getDeclaredField("sectionStorage");sectionField.setAccessible(true);
        @SuppressWarnings("unchecked") var sections=(net.minecraft.world.level.entity.EntitySectionStorage<Entity>)sectionField.get(manager);
        var section=sections.getSection(net.minecraft.core.SectionPos.asLong(doll.blockPosition()));
        var callbackField=Entity.class.getDeclaredField("levelCallback");callbackField.setAccessible(true);
        var callback=(net.minecraft.world.level.entity.EntityInLevelCallback)callbackField.get(doll);
        ticking.remove(doll);lookup.remove(doll);check(!section.remove(doll),"direct spatial-section purge rejected");
        callback.onRemove(Entity.RemovalReason.KILLED);
        doll.setLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback.NULL);doll.onRemovedFromLevel();
        check(callbackField.get(doll)==callback&&doll.isAddedToLevel(),"direct callback/lifecycle detachment rejected");
        preserved(level,doll,ticking,uuid,id,origin,"direct tick/lookup/section/callback purge");
        var callbacksField=manager.getClass().getDeclaredField("callbacks");callbacksField.setAccessible(true);
        @SuppressWarnings("unchecked") var callbacks=(net.minecraft.world.level.entity.LevelCallback<Entity>)callbacksField.get(manager);
        callbacks.onTickingEnd(doll);callbacks.onTrackingEnd(doll);
        preserved(level,doll,ticking,uuid,id,origin,"direct level callback purge");
        var ordinary=EntityType.CHICKEN.create(level);check(ordinary!=null,"ordinary storage control");
        ordinary.setPos(origin.add(3,0,0));check(level.addFreshEntity(ordinary),"ordinary storage control registered");
        try {
            ticking.add(ordinary);ticking.remove(ordinary);
            check(!ticking.contains(ordinary),"ordinary tick-list removal still works");
            lookup.remove(ordinary);
            check(level.getEntity(ordinary.getUUID())==null&&level.getEntity(ordinary.getId())==null,"ordinary world-index removal still works");
        } finally {ordinary.discard();}

        // Vanilla visibility changes and section moves are scoped, so unloads cannot leak ghosts.
        var stopTicking=manager.getClass().getDeclaredMethod("stopTicking",net.minecraft.world.level.entity.EntityAccess.class);stopTicking.setAccessible(true);
        var startTicking=manager.getClass().getDeclaredMethod("startTicking",net.minecraft.world.level.entity.EntityAccess.class);startTicking.setAccessible(true);
        var stopTracking=manager.getClass().getDeclaredMethod("stopTracking",net.minecraft.world.level.entity.EntityAccess.class);stopTracking.setAccessible(true);
        var startTracking=manager.getClass().getDeclaredMethod("startTracking",net.minecraft.world.level.entity.EntityAccess.class);startTracking.setAccessible(true);
        stopTicking.invoke(manager,doll);check(!ticking.contains(doll),"vanilla visibility may stop ticking");
        startTicking.invoke(manager,doll);stopTracking.invoke(manager,doll);
        check(level.getEntity(uuid)==null&&level.getEntity(id)==null,"vanilla visibility may stop tracking");
        startTracking.invoke(manager,doll);
        Vec3 moved=origin.add(20,0,0);level.getChunkAt(net.minecraft.core.BlockPos.containing(moved));
        doll.setPos(moved);check(doll.position().equals(moved),"cross-section movement allowed");
        check(!section.getEntities().anyMatch(e->e==doll),"vanilla section move removes old spatial entry");
        doll.setPos(origin);ticking.add(doll);
        preserved(level,doll,ticking,uuid,id,origin,"after vanilla visibility and section move");
        LogUtils.getLogger().info("2I GENERIC STORAGE GUARDS PASSED: direct lookup/tick-list/spatial-section/removal-callback/level-callback purges blocked; native visibility stop/start and cross-section movement still work");
    }
    private static void preserved(ServerLevel level,TwoIDollEntity doll,EntityTickList ticking,UUID uuid,int id,Vec3 pos,String step) throws Exception {
        var dead=LivingEntity.class.getDeclaredField("dead");dead.setAccessible(true);
        check(doll.getUUID().equals(uuid)&&doll.getId()==id&&doll.isAlive()&&!doll.isRemoved()
                &&doll.getHealth()==20&&!dead.getBoolean(doll)&&doll.getPose()!=Pose.DYING,step+": original live entity");
        check(!doll.isInvisible()&&doll.position().equals(pos),step+": visible at original position "+doll.position()+" invisible="+doll.isInvisible());
        check(level.getEntity(uuid)==doll&&level.getEntity(id)==doll&&ticking.contains(doll),step+": both indexes and actual ticking entry");
        check(level.getEntitiesOfClass(TwoIDollEntity.class,doll.getBoundingBox().inflate(.01)).stream().anyMatch(e->e==doll),step+": spatial lookup");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("2i forced kill: "+message);}
}
