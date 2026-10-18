package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.*;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.event.TwoICarriedCompanionship;
import com.sorrowmist.useless.stretcher.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.UUID;

final class TwoIFinalPolishChecks {
    static void run(ServerLevel level) throws Exception {
        check(!ModEntities.TWO_I_DOLL.get().canSummon()
                && !WondrousStaffSummoning.canSummonType(ModEntities.TWO_I_DOLL.get()), "summon catalog excludes 2i");
        var source = level.getServer().createCommandSourceStack().withLevel(level);
        boolean refused = false;
        try {
            SummonCommand.createEntity(source, ModEntities.TWO_I_DOLL.get().builtInRegistryHolder(),
                    new Vec3(4,180,4), new CompoundTag(), false);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { refused = true; }
        check(refused, "direct summon API refuses 2i");
        var passenger = new CompoundTag(); passenger.putString("id", "useless_stretcher:two_i_doll");
        var passengers = new ListTag(); passengers.add(passenger);
        var nested = new CompoundTag(); nested.put("Passengers", passengers);
        refused = false;
        try {
            SummonCommand.createEntity(source, EntityType.CHICKEN.builtInRegistryHolder(),
                    new Vec3(4,180,4), nested, false);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { refused = true; }
        check(refused, "summon passenger NBT refuses hidden 2i");
        Entity chicken = SummonCommand.createEntity(source, EntityType.CHICKEN.builtInRegistryHolder(),
                new Vec3(4,180,4), new CompoundTag(), false);
        check(chicken != null, "normal summon still works"); chicken.discard();

        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIPolish"));
        player.setPos(4,180,4); player.setShiftKeyDown(true);
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        staff.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), true);
        player.setItemInHand(InteractionHand.MAIN_HAND,staff);
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        doll.setPos(4,180,4); check(level.addFreshEntity(doll), "normal doll registration works");
        Class<?> upstream = Class.forName("com.sorrowmist.useless.content.items.BeefTimeAcceleration");
        var useEntity = upstream.getMethod("tryUseOnEntity",ItemStack.class,net.minecraft.world.entity.player.Player.class,
                net.minecraft.world.entity.LivingEntity.class);
        check(useEntity.invoke(null,staff,player,doll)==InteractionResult.FAIL,
                "inherited upstream entity path also refuses 2i");
        var flow = new TimeFlowEntity(level,UUID.randomUUID()); flow.setPos(4,180,4); level.addFreshEntity(flow);
        for (Entity target : new Entity[]{doll,flow}) {
            check(WondrousStaffAcceleration.tryUseEntity(player,target,staff) == InteractionResult.FAIL,
                    "single acceleration rejects protected entities");
            var legacy = new WondrousStaffAccelerationEntity(level,target,16);
            level.addFreshEntity(legacy);
            CompoundTag saved = legacy.saveWithoutId(new CompoundTag()); legacy.discard();
            var restored = ModEntities.WONDROUS_STAFF_ACCELERATION.get().create(level);
            restored.load(saved); level.addFreshEntity(restored);
            int before = target.tickCount; restored.tick();
            check(restored.isRemoved() && target.tickCount == before, "saved acceleration self-cleans before target tick");
        }
        var range = RangeAccelerationSavedData.get(level.getServer());
        var placed = range.place(level,player,new BlockPos(4,180,4),staff);
        range.tick(level.getServer());
        for(Entity e : level.getAllEntities()) if(e instanceof WondrousStaffAccelerationEntity marker) {
            check(!doll.getUUID().equals(marker.getTargetUuid()) && !flow.getUUID().equals(marker.getTargetUuid()),
                    "range discovery excludes 2i and time flow");
        }
        range.reclaim(level.getServer(),player.getUUID(),placed.id());
        doll.discardForReclaim(); flow.discard();

        BlockPos machine = new BlockPos(4,185,4);
        level.setBlockAndUpdate(machine,Blocks.HOPPER.defaultBlockState());
        var ctx = new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(machine),Direction.UP,machine,false));
        staff.set(StretcherComponents.RANGE_PLACEMENT_MODE.get(),true);
        var item = (WondrousStaffItem)staff.getItem();
        check(WondrousStaffAcceleration.tryUse(ctx)==InteractionResult.FAIL
                && item.onItemUseFirst(staff,ctx)==InteractionResult.FAIL && item.useOn(ctx)==InteractionResult.FAIL,
                "placement blocks independent acceleration at all item entry points");
        staff.set(StretcherComponents.RANGE_PLACEMENT_MODE.get(),false);
        check(WondrousStaffAcceleration.tryUse(ctx).consumesAction(),"closing placement restores independent acceleration");
        for(Entity e: level.getAllEntities()) if(e instanceof WondrousStaffAccelerationEntity m
                && machine.equals(m.getTargetPos())) m.discard();
        level.removeBlock(machine,false);
        companionship(level,player);
        weather(level);
        LogUtils.getLogger().info("REGRESSION 2i final polish: summon/passengers, acceleration/legacy/range/placement and carried companionship PASSED");
    }

    private static void weather(ServerLevel level) {
        BlockPos pos=new BlockPos(4,252,4);
        String oldBiome=level.getBiome(pos).unwrapKey().orElseThrow().location().toString();
        boolean oldRain=level.isRaining(),oldThunder=level.isThundering();
        var oldRoof=level.getBlockState(pos.above());
        var commands=level.getServer().getCommands();
        var source=level.getServer().createCommandSourceStack().withLevel(level);
        try {
            // BiomeManager samples neighboring quart cells; fill their whole local cube.
            commands.performPrefixedCommand(source,"fillbiome 0 248 0 8 256 8 minecraft:plains");
            level.setWeatherParameters(0,6000,true,true); level.setRainLevel(1); level.setThunderLevel(1);
            check((TwoIDollEnvironment.conditions(level,pos) & 5)==5,"exposed plains supports umbrella and thunder together");
            level.setBlockAndUpdate(pos.above(),Blocks.STONE.defaultBlockState());
            check((TwoIDollEnvironment.conditions(level,pos) & 1)==0,"shelter closes umbrella");
            check((TwoIDollEnvironment.conditions(level,pos) & 4)!=0,"thunder is heard under shelter");
            commands.performPrefixedCommand(source,"fillbiome 0 248 0 8 256 8 minecraft:snowy_plains");
            check((TwoIDollEnvironment.conditions(level,pos) & 2)!=0,"real snow biome triggers a runny nose");
            var nether=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            check((TwoIDollEnvironment.conditions(nether,pos) & 5)==0,"weatherless dimensions never open umbrellas or thunder-shiver");
            check(TwoIDollEnvironment.sneezeProgress(18,0)==0.5F
                    && TwoIDollEnvironment.sneezeProgress(100,0)<0,"sneeze schedule uses a bounded world-clock window");
            LogUtils.getLogger().info("REGRESSION 2i weather: real plains/snow, rain+thunder, shelter and Nether PASSED");
        } finally {
            commands.performPrefixedCommand(source,"fillbiome 0 248 0 8 256 8 "+oldBiome);
            level.setBlockAndUpdate(pos.above(),oldRoof);
            level.setWeatherParameters(0,6000,oldRain,oldThunder);
            level.setRainLevel(oldRain?1:0); level.setThunderLevel(oldThunder?1:0);
        }
    }

    private static void companionship(ServerLevel level, net.minecraft.server.level.ServerPlayer player) throws Exception {
        long oldTime=level.getGameTime();
        try {
            var data = com.sorrowmist.useless.stretcher.content.entity.TwoICompanionshipData.get(level.getServer());
            level.getServer().getWorldData().overworldData().setGameTime(96000);
            player.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD}) {
                ItemStack stack=new ItemStack(ModItems.TWO_I_DOLL.get());
                var base=TwoIDollMemory.empty();
                var near=new TwoIDollMemory.Interaction(4,0,0,0,0,5900,-1,"",0);
                stack.set(StretcherComponents.TWO_I_MEMORY.get(),new TwoIDollMemory(base.cooldowns(),near));
                player.setItemSlot(slot,stack);
            }
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD}) {
                ItemStack stack=player.getItemBySlot(slot);
                var memory=stack.get(StretcherComponents.TWO_I_MEMORY.get());
                var stored=data.progress(stack.get(StretcherComponents.TWO_I_DOLL_ID.get()),memory);
                check(TwoIDollItem.getBond(stack)==1 && stored.ticks()==0,
                        "each held/head doll awards once per real sample");
                ItemStack reloaded=ItemStack.parseOptional(level.registryAccess(),(CompoundTag)stack.save(level.registryAccess()));
                player.setItemSlot(slot,reloaded);
            }
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            check(TwoIDollItem.getBond(player.getMainHandItem())==1,"encoded relog memory cannot double-award");
            var entity=ModEntities.TWO_I_DOLL.get().create(level);
            var formStack=player.getMainHandItem().copy();
            var formMemory=formStack.get(StretcherComponents.TWO_I_MEMORY.get());
            formStack.set(StretcherComponents.TWO_I_BOND.get(),0);
            // Separate legacy fixture: first access imports the saved sample without overwriting existing progress.
            formStack.set(StretcherComponents.TWO_I_DOLL_ID.get(), UUID.randomUUID());
            formStack.set(StretcherComponents.TWO_I_MEMORY.get(),new TwoIDollMemory(formMemory.cooldowns(),
                    new TwoIDollMemory.Interaction(4,0,0,0,0,5900,-1,"",0),96000));
            entity.restoreFromItem(formStack);
            entity.setOwnerId(player.getUUID()); entity.setPos(player.position());
            var tick=TwoIDollEntity.class.getDeclaredMethod("tickCompanionship",ServerLevel.class);
            tick.setAccessible(true);
            var indexField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            indexField.setAccessible(true);
            @SuppressWarnings("unchecked") var index=(java.util.Map<UUID,net.minecraft.server.level.ServerPlayer>)
                    indexField.get(level.getServer().getPlayerList());
            index.put(player.getUUID(),player);
            try {
            tick.invoke(entity,level);
            check(entity.getGiftScore()==0,"item to entity cannot resample the same timer interval");
            level.getServer().getWorldData().overworldData().setGameTime(96100);
            tick.invoke(entity,level);
            check(entity.getGiftScore()==1,"entity advances the carried progress on the next five-second check");
            player.setItemSlot(EquipmentSlot.HEAD,entity.toDollItem());
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            ItemStack head=player.getItemBySlot(EquipmentSlot.HEAD);
            check(data.progress(head.get(StretcherComponents.TWO_I_DOLL_ID.get()),
                    head.get(StretcherComponents.TWO_I_MEMORY.get())).ticks()==0,
                    "entity to head cannot resample the same timer interval");
            } finally { index.remove(player.getUUID()); entity.discardForReclaim(); }
            for(int i=2;i<=121;i++) {
                level.getServer().getWorldData().overworldData().setGameTime(96000+i*100);
                TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            }
            check(TwoIDollItem.getBond(player.getMainHandItem())==2,"companionship uses 6000 ticks and caps at two per day");
            level.getServer().getWorldData().overworldData().setGameTime(120000);
            TwoICarriedCompanionship.tick(new PlayerTickEvent.Post(player));
            check(data.progress(player.getMainHandItem().get(StretcherComponents.TWO_I_DOLL_ID.get()),
                    player.getMainHandItem().get(StretcherComponents.TWO_I_MEMORY.get())).ticks()==100,
                    "next day starts a new carried companionship cycle");
        } finally {
            level.getServer().getWorldData().overworldData().setGameTime(oldTime);
            for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD})
                player.setItemSlot(slot,ItemStack.EMPTY);
        }
    }

    private static void check(boolean result,String message) {
        if(!result) throw new AssertionError("2i final polish: "+message);
    }
}
