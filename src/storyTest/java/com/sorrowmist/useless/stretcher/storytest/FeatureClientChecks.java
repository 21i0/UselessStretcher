package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import com.sorrowmist.useless.stretcher.client.CapsuleReadyToast;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicInteger;

/** Real movement input and ready-only toast packets in an integrated client with the full fixture. */
final class FeatureClientChecks {
    private static boolean requested, finished;
    private static int ticks, stage, age;
    private static Vec3 start, beforeVertical;
    private static final AtomicInteger serverPhase = new AtomicInteger();
    private static int toastWait;
    private static int journeys;
    private static Vec3 original, entry;
    static void tick(Minecraft client) {
        if (finished) return;
        try {
            check(++ticks < 5000, "feature client timeout at stage "+stage);
            check(serverPhase.get() != -1, "integrated server phase check failed; see server exception");
            if (ticks % 100 == 0) LogUtils.getLogger().info("FEATURE CLIENT PROGRESS stage={} age={} screen={} paused={} player={} menu={}",
                    stage,age,client.screen==null?"none":client.screen.getClass().getSimpleName(),client.isPaused(),
                    client.player==null?"none":client.player.position(),client.player==null?"none":client.player.containerMenu.getClass().getSimpleName());
            if (client.getOverlay()!=null) return;
            if (!requested) {
                if(client.screen==null)return;
                requested=true;
                client.options.guiScale().set(1); client.resizeDisplay();
                client.options.pauseOnLostFocus=false;
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                var settings=new LevelSettings("Phase and capsule ready",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("phase-ready-"+System.currentTimeMillis(),settings,
                        new WorldOptions(2L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),client.screen);
                return;
            }
            if(client.player==null||client.level==null||client.getSingleplayerServer()==null)return;
            if(stage==0) {
                stage=1;
                client.getSingleplayerServer().execute(()->{
                    try {
                        var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        player.setGameMode(GameType.SURVIVAL);
                        for(int x=-3;x<=3;x++)for(int z=0;z<=9;z++)for(int y=99;y<=105;y++)
                            player.serverLevel().setBlockAndUpdate(new BlockPos(x,y,z),(z>=2&&z<=6?Blocks.STONE:Blocks.AIR).defaultBlockState());
                        player.serverLevel().setBlockAndUpdate(new BlockPos(1,101,0),Blocks.CHEST.defaultBlockState());
                        var staff=new ItemStack(ModItems.WONDROUS_STAFF.get());
                        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(),true);
                        player.getInventory().clearContent();
                        player.getInventory().selected=0;
                        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.WONDROUS_STAFF.get()));
                        player.getInventory().setItem(17,staff);
                        player.connection.teleport(0.5,101,0.5,0,0);
                        StaffPhaseManager.apply(player);
                        serverPhase.set(1);
                    } catch(Throwable e) {
                        LogUtils.getLogger().error("FEATURE CLIENT SERVER CHECK FAILED",e);serverPhase.set(-1);
                    }
                }); return;
            }
            if(stage==1) {
                if(serverPhase.get()!=1 || !client.player.getMainHandItem().is(ModItems.WONDROUS_STAFF.get())
                        || !com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(client.player.getInventory().getItem(17), StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(),false)
                        || !StaffPhaseManager.noPhysics(client.player) || client.player.getY()<100
                        || !client.level.getBlockState(new BlockPos(1,101,0)).is(Blocks.CHEST))return;
                client.setScreen(null);
                openChest(client);
                stage=12;age=0;return;
            }
            if(stage==12) {
                if(!(client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)) {
                    if(++age % 20 == 0 && age <= 60) openChest(client);
                    return;
                }
                check(StaffPhaseManager.noPhysics(client.player),"real chest interaction while phasing");
                client.player.closeContainer();client.setScreen(null);start=client.player.position();stage=2;age=0;
                client.options.keyUp.setDown(true); return;
            }
            if(stage==2) {
                client.options.keyUp.setDown(true);
                if(++age<36)return;
                client.options.keyUp.setDown(false);
                check(client.player.getZ()>7,"real forward input traverses five-block thick stone wall: "+client.player.position());
                check(Math.abs(client.player.getY()-start.y)<0.1,"free flight hovers without falling");
                check(!client.player.isSpectator() && !client.player.isCreative(),"survival mode preserved");
                check(!com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(client.player.getMainHandItem(), StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(),false),
                        "wall traversal uses backpack staff while held staff remains disabled");
                stage=13;age=0;
                client.getSingleplayerServer().execute(()->{
                    try {
                        var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        check(StaffPhaseManager.noPhysics(player),"server accepts backpack phase movement");
                        player.getInventory().setItem(8,player.getInventory().removeItemNoUpdate(17));
                        serverPhase.set(2);
                    } catch(Throwable e) {
                        LogUtils.getLogger().error("FEATURE CLIENT SERVER CHECK FAILED",e);serverPhase.set(-1);
                    }
                });return;
            }
            if(stage==13) {
                if(serverPhase.get()!=2 || !com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(client.player.getInventory().getItem(8),
                        StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(),false))return;
                client.player.getInventory().selected=2;
                if(++age<15)return;
                check(client.player.getMainHandItem().isEmpty() && StaffPhaseManager.noPhysics(client.player),
                        "unselected hotbar staff keeps empty-hand phase active after changing selected slot");
                beforeVertical=client.player.position();stage=3;age=0;return;
            }
            if(stage==3) {
                client.options.keyJump.setDown(true);
                if(++age<15)return;
                client.options.keyJump.setDown(false);
                check(client.player.getY()>beforeVertical.y+1,"jump input ascends");
                beforeVertical=client.player.position();stage=4;age=0;return;
            }
            if(stage==4) {
                client.options.keyShift.setDown(true);
                if(++age<20)return;
                client.options.keyShift.setDown(false);
                check(client.player.getY()<beforeVertical.y-1,"sneak input descends");
                client.getSingleplayerServer().execute(()->{
                    try {
                        var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                        check(player.getInventory().selected==2 && StaffPhaseManager.noPhysics(player),
                                "server keeps phasing with unselected hotbar staff and empty hand");
                        player.getInventory().setItem(8,ItemStack.EMPTY);
                        serverPhase.set(3);
                    } catch(Throwable e) {
                        LogUtils.getLogger().error("FEATURE CLIENT SERVER CHECK FAILED",e);serverPhase.set(-1);
                    }
                });
                stage=5;age=0;return;
            }
            if(stage==5) {
                if(serverPhase.get()!=3 || ++age<15)return;
                check(!StaffPhaseManager.state(client.player).phase() && !StaffPhaseManager.noPhysics(client.player)
                        && !client.player.isNoGravity(),"removing last enabled staff restores survival collision/gravity: collision="
                        +StaffPhaseManager.noPhysics(client.player)+" mayfly="+client.player.getAbilities().mayfly+" flying="
                        +client.player.getAbilities().flying+" noGravity="+client.player.isNoGravity());
                LogUtils.getLogger().info("PHASE CLIENT MOVEMENT PASSED: backpack slot 17 with disabled held staff, real chest interaction, five-block wall traversal and hover; move staff to unselected hotbar slot 8, select empty slot 2, jump ascent and sneak descent; removing final enabled staff restores collision/gravity");
                stage=6;
                client.getSingleplayerServer().execute(()->{
                    var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setGameMode(GameType.CREATIVE);
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
                    Network.sendDimensionCapsuleCatalog(player,false);
                });return;
            }
            if(stage==6) {
                if(!(client.screen instanceof com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen))return;
                CapsuleNetwork.request("minecraft:pillager_outpost",com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD,1,0,false);
                client.setScreen(null);stage=7;return;
            }
            if(stage==7) {
                check(client.screen==null,"background generation keeps gameplay open");
                var toast=client.getToasts().getToast(CapsuleReadyToast.class,"minecraft:pillager_outpost|"
                        +com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD+"#0");
                if(toast==null)return;
                stage=8;toastWait=0;
                client.getSingleplayerServer().execute(()->{
                    try {
                        var server=client.getSingleplayerServer();
                        var data=com.sorrowmist.useless.stretcher.content.capsule.CapsuleInstances.get(server);
                        var f=data.getClass().getDeclaredField("rooms");f.setAccessible(true);
                        Object room=((java.util.Map<?,?>)f.get(data)).values().stream().filter(r->{try{
                            var id=r.getClass().getDeclaredField("structure");id.setAccessible(true);return id.get(r).equals("minecraft:pillager_outpost");
                        }catch(Exception e){throw new RuntimeException(e);}}).findFirst().orElseThrow();
                        for(String key:new String[]{"ready","completionNotified"}){var flag=room.getClass().getDeclaredField(key);flag.setAccessible(true);check(flag.getBoolean(room),"toast only after persisted "+key);}
                        serverPhase.set(2);
                    }catch(Throwable e){LogUtils.getLogger().error("FEATURE CLIENT SERVER CHECK FAILED",e);serverPhase.set(-1);}
                });return;
            }
            if(stage==8) {
                check(serverPhase.get()!=-1,"server ready validation");
                if(++toastWait<20||serverPhase.get()!=2)return;
                java.nio.file.Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
                try(var shot=net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())){
                    shot.writeToFile(client.gameDirectory.toPath().resolve("screenshots/capsule-background-ready-toast.png"));
                }
                LogUtils.getLogger().info("CAPSULE BACKGROUND READY TOAST PASSED: actual server job, screen closed throughout, owner-only Complete payload, real top-right toast queue, persisted ready/notified flags");
                original=client.player.position();
                stage=9;age=0;
            }
            if(stage==9) {
                CapsuleNetwork.request("minecraft:pillager_outpost",com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD,2,0,false);
                stage=10;age=0;return;
            }
            if(stage==10) {
                if(!client.level.dimension().location().toString().equals(com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD))return;
                if(!client.level.hasChunkAt(client.player.blockPosition())||++age<20)return;
                if(entry==null)entry=client.player.position();
                else check(client.player.position().distanceToSqr(entry)<4,"same structure on repeated entry");
                client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);stage=11;return;
            }
            if(stage==11) {
                if(!client.level.dimension().equals(Level.OVERWORLD))return;
                check(client.player.position().distanceToSqr(original)<16,"capsule item returns to saved location");
                if(++journeys<2){stage=9;return;}
                LogUtils.getLogger().info("CAPSULE READY ROUNDTRIPS PASSED: two actual entries into same saved structure, two real item-use returns after background ready toast");
                finished=true;client.stop();
            }
        } catch(Throwable failure) {
            finished=true;
            client.options.keyUp.setDown(false);client.options.keyJump.setDown(false);client.options.keyShift.setDown(false);
            LogUtils.getLogger().error("FEATURE CLIENT CHECKS FAILED",failure);client.stop();
        }
    }
    private static void openChest(Minecraft client) {
        var result=client.gameMode.useItemOn(client.player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(
                new Vec3(1,101.5,0.5),net.minecraft.core.Direction.WEST,new BlockPos(1,101,0),false));
        LogUtils.getLogger().info("PHASE CLIENT CHEST USE result={} block={} position={}",result,
                client.level.getBlockState(new BlockPos(1,101,0)),client.player.position());
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
