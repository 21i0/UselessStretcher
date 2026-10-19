package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.client.render.*;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Real reloaded skin, actual item/block particle sprites and a rendered motion/contact sheet. */
final class TwoISkinTailChecks {
    private static boolean started, finished;
    private static int ticks, frames, shot;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static Gallery gallery;

    static void tick(Minecraft client) {
        if (finished) return;
        try {
            check(++ticks < 2400, "client timeout");
            if (client.getOverlay() != null || client.screen == null) return;
            if (!started) {
                started = true;
                client.options.guiScale().set(1); client.resizeDisplay();
                client.options.pauseOnLostFocus = false;
                reload = client.reloadResourcePacks();
                return;
            }
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join(); reload = null;
                validate(client);
                DollRenderChecks.validate(client);
                gallery = new Gallery(); client.setScreen(gallery); frames = 12;
                return;
            }
            if (--frames > 0) return;
            Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
            try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/skin-tail-" + shot + ".png"));
            }
            if (++shot < 3) { gallery.frame = shot; frames = 12; return; }
            if (shot == 3) { client.setScreen(DollRenderChecks.preview()); frames=12; return; }
            if (shot == 4) { client.setScreen(DollRenderChecks.environmentPreview()); frames=12; return; }
            if (shot == 5) { client.setScreen(new Closeup()); frames=12; return; }
            LogUtils.getLogger().info("2I SKIN TAIL CHECKS PASSED: exact artist skin, reloaded blink palette, both real particle sprites, bounded idle/run/jump/fall tail, unchanged hand swaps/grip and all visual contexts");
            finished = true; client.stop();
        } catch (Throwable failure) {
            LogUtils.getLogger().error("2I SKIN TAIL CHECKS FAILED", failure);
            finished = true; client.stop();
        }
    }

    private static void validate(Minecraft client) throws Exception {
        try (var input = client.getResourceManager().open(TwoIDollRenderer.TEXTURE)) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            check(hash.equals("597dbe84e55288118666187e127167f30686e426d3c0693b398aa63ea6b4e44d"), "exact new skin bytes");
        }
        try (var input = client.getResourceManager().open(TwoIDollRenderer.TEXTURE);
             var image = NativeImage.read(input); var closed = TwoIDollSkin.blinkCopy(image, true)) {
            for (int x : new int[]{9,10,13,14}) for (int y = 12; y <= 14; y++) {
                check(closed.getPixelRGBA(x,y) == (y == 14 ? image.getPixelRGBA(8,8) : image.getPixelRGBA(11,15)),
                        "gold eyes fully close with artist complexion and dark slate lashes");
            }
            var itemSprite = client.getItemRenderer().getModel(new ItemStack(ModItems.TWO_I_DOLL.get()),null,null,0).getParticleIcon();
            var blockSprite = client.getBlockRenderer().getBlockModel(ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState()).getParticleIcon();
            for (var sprite : new net.minecraft.client.renderer.texture.TextureAtlasSprite[]{itemSprite,blockSprite}) {
                check(sprite.contents().name().equals(ResourceLocation.parse("useless_stretcher:entity/two_i")), "shared new particle sprite");
                var pixels = sprite.contents().getOriginalImage();
                for (int y=0;y<64;y++) for(int x=0;x<64;x++) check(pixels.getPixelRGBA(x,y)==image.getPixelRGBA(x,y),
                        "actual baked atlas pixels equal new skin, no old mining/item particle texture");
            }
        }
        var model = new TwoIDollRenderer.Model(client.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
        model.setupDecoration(60); float idleYaw=model.tailYaw(),idlePitch=model.tailPitch();
        var idleTip=model.tailTip();
        model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(.08F,-.12F,.12F,1,1,1),60);
        check(model.tailTip().distanceTo(idleTip)>.15 && Math.abs(model.tailYaw()-idleYaw)>.1F,
                "tail tip moves under gravity and acceleration while support-plane correction may cancel pitch: "+model.tailTip().distanceTo(idleTip));
        model.setupDecoration(60);
        check(model.tailYaw()==idleYaw && model.tailPitch()==idlePitch,"tail pose resets between items and forms");
        model.setupDecoration(90); check(model.tailYaw()!=idleYaw,"living idle tail");
        var spring = new TwoIDollCarryMotion.Spring();
        for (int i=0;i<60;i++) spring.step(.35F,0,0,false,0);
        check(spring.pose(1).speed()>.9F,"steady sprint retains tail motion after initial acceleration");
        for (int i=0;i<120;i++) spring.step(0,0,0,false,0);
        check(spring.pose(1).speed()<.001F,"motion settles without hand jitter");
        for (int i=0;i<400;i++) {
            model.setupStanding(i*.2F,.4F,i,0,0,0,0);
            check(Float.isFinite(model.tailYaw())&&Float.isFinite(model.tailPitch()),"finite tail locomotion");
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y,"walking tail stays above ground");
            model.setupDecoration(i);
            model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(.1F,-.16F,.14F,1,1,1),i);
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y,"gravity tail stays above hand/head/shelf support plane");
            model.setupDecoration(i*5);
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y-.1F,"seated tail and bow retain support-plane clearance throughout idle gestures");
        }
        LogUtils.getLogger().info("2I NEW SKIN AND PARTICLE ATLAS PASSED: artist SHA256, reload, all 4096 pixels on item/block sprites, full gold-eye closure; tail gravity/sprint/reset/idle checks passed");
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError("2i skin/tail: " + reason); }

    private static final class Closeup extends Screen {
        private TwoIDollRenderer.Model model;
        Closeup() { super(Component.literal("2i tail: side / back / three-quarter / seated")); }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            if(model==null)model=new TwoIDollRenderer.Model(minecraft.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
            g.fill(0,0,width,height,0xFFCAD4E0);
            g.drawCenteredString(font,title,width/2,18,0xFF24364C);
            for(int i=0;i<4;i++) {
                if(i==3) model.setupDecoration(60);else model.setupStanding(0,0,60,0,0,0,0);
                float scale=195;int x=width*(i+1)/5,base=460;
                var pose=g.pose();pose.pushPose();pose.translate(x,base-24/16F*scale,100);pose.scale(scale,scale,scale);
                pose.mulPose(Axis.YP.rotationDegrees(i==0||i==3?90:i==1?0:40));
                var buffers=minecraft.renderBuffers().bufferSource();
                model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                model.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);buffers.endBatch();pose.popPose();
                g.fill(x-100,base+1,x+100,base+2,0xFF65758A);
            }
        }
    }

    private static final class Gallery extends Screen {
        private TwoIDollRenderer.Model model;
        private int frame;
        private final ItemStack item = new ItemStack(ModItems.TWO_I_DOLL.get());
        Gallery() { super(Component.literal("2i artist skin / tail / gravity / all forms")); }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            if(model==null)model=new TwoIDollRenderer.Model(minecraft.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
            g.fill(0,0,width,height,0xFFCAD4E0);
            g.drawCenteredString(font,title,width/2,10,0xFF24364C);
            String[] names={"standing front","standing side","standing back","seated side","seated back","running","jump","fall","eyes open","eyes closed","rain palette","shared rain"};
            for(int i=0;i<12;i++) {
                float time=(i==9?42:60)+frame*7;
                if(i==9)time=42;
                if(i==3||i==4||i==6||i==7)model.setupDecoration(time);else model.setupStanding(i==5?time*.5F:0,i==5?.4F:0,time,0,0,0,0);
                if(i==6||i==7)model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(.1F,-.12F,.08F,1,i==7?1:0,1),time);
                if(i==10||i==11){model.setSharedUmbrella(i==11,true);model.applyWeather(new TwoIDollWeather.Pose(1,1,-1),time);}
                else model.setSharedUmbrella(false,true);
                int x=width*(i%6+1)/7,base=i<6?225:455;
                float scale=82;
                var pose=g.pose();pose.pushPose();
                pose.translate(x,base-24/16F*scale,100);pose.scale(scale,scale,scale);
                pose.mulPose(Axis.YP.rotationDegrees(i==1||i==3?90:i==2||i==4?0:165));
                var buffers=minecraft.renderBuffers().bufferSource();
                model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                model.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                buffers.endBatch();pose.popPose();
                g.fill(x-42,base+1,x+42,base+2,0xFF65758A);g.drawCenteredString(font,names[i],x,base+8,0xFF24364C);
            }
            var buffers=minecraft.renderBuffers().bufferSource();
            for(int i=0;i<4;i++) {
                var player=new PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
                player.young=false;
                var pose=g.pose();pose.pushPose();pose.translate(width*(i+1)/5F,510,100);pose.scale(85,85,85);
                pose.mulPose(Axis.YP.rotationDegrees(150));
                TwoIDollThirdPerson.poseArm(player,HumanoidArm.RIGHT,60);
                TwoIDollThirdPerson.poseArm(player,HumanoidArm.LEFT,60);
                player.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                if(i==0)TwoIDollHeadLayer.renderOnHead(item,player.head,pose,buffers,LightTexture.FULL_BRIGHT);
                else if(i==1||i==2){TwoIDollThirdPerson.render(item,player,HumanoidArm.RIGHT,false,pose,buffers,LightTexture.FULL_BRIGHT,TwoIDollCarryMotion.Pose.STILL);TwoIDollThirdPerson.render(item,player,HumanoidArm.LEFT,false,pose,buffers,LightTexture.FULL_BRIGHT,TwoIDollCarryMotion.Pose.STILL);}
                else {pose.scale(.7F,.7F,.7F);TwoIDollItemRenderer.get().renderByItem(item,ItemDisplayContext.GUI,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);}
                buffers.endBatch();pose.popPose();
            }
        }
    }
}
