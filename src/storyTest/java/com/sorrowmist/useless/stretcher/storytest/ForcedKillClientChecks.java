package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.concurrent.atomic.AtomicInteger;

/** The supplied third-party sword incorrectly loads client classes on dedicated servers. */
final class ForcedKillClientChecks {
    private static boolean requested,started,finished;
    private static int ticks;
    private static volatile int dollId;
    private static int stage;
    private static com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity visibleDoll;
    private static final AtomicInteger done=new AtomicInteger();
    static void tick(Minecraft client) {
        if(finished)return;
        try {
            if(++ticks>3000)throw new AssertionError("forced-kill client timed out");
            if(ticks%100==0)LogUtils.getLogger().info("2I FORCED KILL CLIENT PROGRESS stage={} done={} player={} screen={}",stage,done.get(),
                    client.player==null?"none":client.player.position(),client.screen==null?"none":client.screen.getClass().getSimpleName());
            if(client.getOverlay()!=null)return;
            if(!requested) {
                if(client.screen==null)return;
                requested=true;client.options.pauseOnLostFocus=false;
                var settings=new LevelSettings("2i forced kill",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("forced-kill-"+System.currentTimeMillis(),settings,
                        new WorldOptions(2L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if(client.player==null||client.level==null||client.getSingleplayerServer()==null)return;
            if(!started) {
                started=true;client.setScreen(null);
                client.getSingleplayerServer().execute(()->{
                    try {
                        var level=client.getSingleplayerServer().overworld();
                        // The sword's real area attack also includes the connected test player.
                        // Its own documented inventory exemption keeps that control player alive.
                        var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        var sword=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("forever_love_sword:forever_love_sword"));
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(sword));
                        // Reflection keeps the ordinary story source set independent of regression sources.
                        Class.forName("com.sorrowmist.useless.stretcher.regression.TwoIForcedKillChecks")
                                .getMethod("run",net.minecraft.server.level.ServerLevel.class).invoke(null,level);
                        Class.forName("com.sorrowmist.useless.stretcher.regression.TwoIDollProtectionChecks")
                                .getMethod("run",net.minecraft.server.level.ServerLevel.class).invoke(null,level);
                        Class.forName("com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldServerChecks")
                                .getMethod("run",net.minecraft.server.level.ServerLevel.class).invoke(null,level);
                        LogUtils.getLogger().info("2I REAL SWORD AND GENERIC PROTECTION CHECKS PASSED");
                        var doll=com.sorrowmist.useless.stretcher.init.ModEntities.TWO_I_DOLL.get().create(level);
                        doll.setOwnerId(player.getUUID());doll.setPos(player.position().add(2,0,0));
                        if(!level.addFreshEntity(doll))throw new AssertionError("client guard fixture spawn");
                        dollId=doll.getId();
                        done.set(1);
                    }catch(Throwable failure){LogUtils.getLogger().error("2I REAL SWORD AND GENERIC PROTECTION CHECKS FAILED",failure);done.set(-1);}
                });return;
            }
            if(done.get()==0)return;
            if(done.get()<0){finished=true;client.stop();return;}
            if(stage==0) {
                if(!(client.level.getEntity(dollId) instanceof com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity doll))return;
                visibleDoll=doll;var position=doll.position();
                var kill=Class.forName("net.wzz.forever_love_sword.util.ForeverUtil").getMethod("killEntity",net.minecraft.world.entity.Entity.class,boolean.class);
                kill.invoke(null,doll,true);kill.invoke(null,doll,false);
                if(doll.isRemoved()||!doll.isAlive()||doll.isInvisible()||!doll.position().equals(position)
                        ||client.level.getEntity(dollId)!=doll)throw new AssertionError("real client-side sword cannot hide/remove/exile tracked doll");
                LogUtils.getLogger().info("2I REAL CLIENT SWORD GUARD PASSED: true/false killEntity on actual tracked client copy preserves identity, visibility and position");
                stage=1;
                client.getSingleplayerServer().execute(()->{
                    var level=client.getSingleplayerServer().overworld();
                    var serverDoll=(com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity)level.getEntity(dollId);
                    if(serverDoll==null||!serverDoll.discardForReclaim()){done.set(-1);return;}
                    done.set(2);
                });return;
            }
            if(done.get()!=2||client.level.getEntity(dollId)!=null)return;
            if(!visibleDoll.isRemoved())throw new AssertionError("server-authorized removal still removes tracked client copy");
            LogUtils.getLogger().info("2I CLIENT AUTHORITATIVE REMOVAL PASSED: real server recall packet removes protected client copy without ghost");
            finished=true;client.stop();
        }catch(Throwable failure){finished=true;LogUtils.getLogger().error("2I FORCED KILL CLIENT CHECKS FAILED",failure);client.stop();}
    }
}
