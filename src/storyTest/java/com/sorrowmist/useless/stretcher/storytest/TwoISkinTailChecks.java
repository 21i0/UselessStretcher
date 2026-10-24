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
    private static MotionGallery motionGallery;
    private static int motionFrame;
    private static boolean bowShown;

    static void tick(Minecraft client) {
        if (finished) return;
        try {
            check(++ticks < 3600, "client timeout");
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
            if (shot == 6) { motionGallery=new MotionGallery();client.setScreen(motionGallery);frames=3;return; }
            if (shot >= 7 && !bowShown) {
                try (NativeImage image=Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/motion-%03d.png".formatted(motionFrame)));
                }
                if(++motionFrame<96){motionGallery.frame=motionFrame;frames=3;return;}
                bowShown=true;client.setScreen(new BowCloseup());frames=12;return;
            }
            if(bowShown)try(NativeImage image=Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/bow-closed.png"));
            }
            LogUtils.getLogger().info("2I SKIN TAIL CHECKS PASSED: exact artist skin, reloaded blink palette, both real particle sprites, bounded idle/run/jump/fall tail, unchanged hand swaps/grip and all visual contexts");
            finished = true; client.stop();
        } catch (Throwable failure) {
            LogUtils.getLogger().error("2I SKIN TAIL CHECKS FAILED", failure);
            finished = true; client.stop();
        }
    }

    private static void validate(Minecraft client) throws Exception {
        // Inspect the rendered mesh, including mirrored loops and both solid ribbon streamers.
        var ribbon=TwoIDollTail.class.getDeclaredField("RIBBON");ribbon.setAccessible(true);
        java.util.Map<String,Integer> edges=new java.util.HashMap<>();
        for(Object face:(java.util.List<?>)ribbon.get(null)) {
            Object[] points=new Object[4];
            for(int i=0;i<4;i++) {
                var accessor=face.getClass().getDeclaredMethod(new String[]{"a","b","c","d"}[i]);accessor.setAccessible(true);
                points[i]=accessor.invoke(face);
            }
            for(int i=0;i<4;i++) {
                String a=points[i].toString(),b=points[(i+1)%4].toString();if(a.equals(b))continue;
                String key=a.compareTo(b)<0?a+"|"+b:b+"|"+a;
                edges.merge(key,1,Integer::sum);
            }
        }
        check(edges.values().stream().allMatch(count->count==2),"bow mesh closed: every non-degenerate edge has two faces");
        LogUtils.getLogger().info("2I CLOSED BOW PASSED: {} mesh edges shared by exactly two faces",edges.size());
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
                check(sprite.contents().name().equals(new ResourceLocation("useless_stretcher:entity/two_i")), "shared new particle sprite");
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
        spring.step(0,0,.42F,true,0);
        float jump=spring.pose(1).tailPitch();
        for(int i=0;i<12;i++)spring.step(0,0,-.65F,true,i*.65F);
        float fall=spring.pose(1).tailPitch();
        spring.step(0,0,0,false,0);
        check(Math.abs(fall-jump)>.25F,"dedicated tail spring distinguishes jump and fall");
        for(int i=0;i<30;i++)spring.step(0,0,0,false,0,12);
        check(Math.abs(spring.pose(1).tailYaw())>.25F,"turning produces visible tail inertia");
        for(int i=0;i<150;i++)spring.step(0,0,0,false,0);
        check(Math.abs(spring.pose(1).tailYaw())<.001F&&Math.abs(spring.pose(1).tailPitch())<.001F,"tail springs settle");
        double minX=100,maxX=-100,meanX=0;double maxStep=0;net.minecraft.world.phys.Vec3 previous=null;
        for(int i=0;i<960;i++) {
            model.setupDecoration(i);
            var anchor=model.tailAttachment();
            check(Math.abs(anchor.x)<.001 && Math.abs(anchor.y-model.body.y-5.8)<.001 && Math.abs(anchor.z-1.25)<.001,
                    "tail base remains embedded inside jacket, ground clearance never moves the root");
            var tip=model.tailTip();minX=Math.min(minX,tip.x);maxX=Math.max(maxX,tip.x);meanX+=tip.x;
            check(tip.distanceTo(anchor)<14,"ground contact does not stretch tail away from its root");
            if(previous!=null)maxStep=Math.max(maxStep,tip.distanceTo(previous));previous=tip;
        }
        check(minX<-5&&maxX>5,"tail sweeps visibly past both body sides: "+minX+" / "+maxX);
        check(Math.abs(meanX/960)<.6,"seated tail has no permanent left bias: "+meanX/960);
        check(maxStep<1.2,"no abrupt gesture/ground correction snapping: "+maxStep);
        LogUtils.getLogger().info("2I LIVELY TAIL PASSED: left/right tip {}/{}, mean {}, max tick step {}; jump/fall/turn/settle springs",minX,maxX,meanX/960,maxStep);
        for (int i=0;i<960;i++) {
            model.setupStanding(i*.2F,.4F,i,0,0,0,0);
            check(Float.isFinite(model.tailYaw())&&Float.isFinite(model.tailPitch()),"finite tail locomotion");
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y,"walking tail stays above ground");
            model.setupDecoration(i);
            model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(.1F,-.16F,.14F,1,1,1),i);
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y,"gravity tail stays above hand/head/shelf support plane");
            model.setupDecoration(i*5);
            check(model.tailBottom()<=TwoIDollRenderer.FEET_Y-.1F,"seated tail and bow retain support-plane clearance throughout idle gestures");
        }
        long start=System.nanoTime();
        for(int i=0;i<2000;i++)model.setupDecoration(i*.33F);
        LogUtils.getLogger().info("2I TAIL POSE TIMING: {} microseconds/pose (2000 local samples, no server work)",(System.nanoTime()-start)/2_000_000.0);
        LogUtils.getLogger().info("2I NEW SKIN AND PARTICLE ATLAS PASSED: artist SHA256, reload, all 4096 pixels on item/block sprites, full gold-eye closure; tail gravity/sprint/reset/idle checks passed");
    }

    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError("2i skin/tail: " + reason); }

    private static final class BowCloseup extends Screen {
        private final TwoIDollTail tail=new TwoIDollTail();
        BowCloseup(){super(Component.literal("2i closed bow: front / side / back"));}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
            g.fill(0,0,width,height,0xFFCAD4E0);g.drawCenteredString(font,title,width/2,20,0xFF24364C);
            tail.pose(false,9,0,0,0,0,TwoIDollCarryMotion.Pose.STILL);
            var tip=tail.tip();
            for(int i=0;i<3;i++) {
                int x=width*(i+1)/4;
                g.enableScissor(x-180,80,x+180,height-65);
                var pose=g.pose();pose.pushPose();pose.translate(x,height/2,100);pose.scale(750,750,750);
                pose.mulPose(Axis.YP.rotationDegrees(i*90));pose.translate(-tip.x/16,-tip.y/16,-tip.z/16);
                var buffers=minecraft.renderBuffers().bufferSource();tail.render(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                buffers.endBatch();pose.popPose();g.disableScissor();
            }
        }
    }

    private static final class MotionGallery extends Screen {
        private TwoIDollRenderer.Model model;
        private int frame;
        private final TwoIDollCarryMotion.Pose[] motion=new TwoIDollCarryMotion.Pose[96];
        MotionGallery(){
            super(Component.literal("2i | soft tail / hand-front visibility / jump, fall, land / turn inertia"));
            var spring=new TwoIDollCarryMotion.Spring();
            for(int f=0;f<96;f++) {
                for(int t=0;t<5;t++) {
                    int tick=f*5+t;int loop=tick%160;
                    float vertical=loop<20?0:loop<30?.42F-(loop-20)*.04F:loop<65?-.35F-(loop-30)*.015F:0;
                    spring.step(loop<20?.25F:0,0,vertical,loop>=20&&loop<65,loop>30&&loop<65?(loop-30)*.45F:0,
                            loop>85&&loop<100?10:loop>110&&loop<125?-10:0);
                }
                motion[f]=spring.pose(1);
            }
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            if(model==null)model=new TwoIDollRenderer.Model(minecraft.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
            g.fill(0,0,width,height,0xFFCAD4E0);
            g.drawCenteredString(font,title,width/2,18,0xFF24364C);
            String[] names={"seated back: relaxed sweep","seated side: tip curls","hand front: tail peeks both sides","standing back: same soft rig","jump / fall / landing (side)","turn inertia (front)"};
            for(int i=0;i<6;i++) {
                float time=frame*5;
                if(i==3)model.setupStanding(0,0,time,0,0,0,0);else model.setupDecoration(time);
                if(i>=4)model.applyCarriedMotion(motion[frame],time);
                float scale=125;int x=width*(i%3+1)/4,base=i<3?355:705;
                if(i==2||i==5) {
                    var pose=g.pose();pose.pushPose();pose.translate(x,base,100);pose.scale(220,-220,-220);
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    TwoIDollHandRenderer.renderOnPalm(new ItemStack(ModItems.TWO_I_DOLL.get()),HumanoidArm.RIGHT,
                            new com.sorrowmist.useless.stretcher.client.render.NativePlayerSkin(new ResourceLocation("textures/entity/player/wide/steve.png"),
                                    "default"),true,true,pose,buffers,LightTexture.FULL_BRIGHT,
                            i==5?motion[frame]:TwoIDollCarryMotion.Pose.STILL,TwoIDollWeather.Pose.CLEAR,time);
                    buffers.endBatch();pose.popPose();
                    g.drawCenteredString(font,i==2?"actual palm: tail peeks on both sides":"actual palm: jump / fall / land / turn",x,base+10,0xFF24364C);
                    continue;
                }
                var pose=g.pose();pose.pushPose();pose.translate(x,base-24/16F*scale,100);pose.scale(scale,scale,scale);
                pose.mulPose(Axis.YP.rotationDegrees(i==1||i==4?90:i==2||i==5?180:0));
                var buffers=minecraft.renderBuffers().bufferSource();
                model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                model.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);buffers.endBatch();pose.popPose();
                g.fill(x-120,base+1,x+120,base+2,0xFF65758A);g.drawCenteredString(font,names[i],x,base+10,0xFF24364C);
            }
            g.drawString(font,"t="+(frame*5)+"   jump 20-30 / fall 30-65 / land 65 / turn 85-125 (160 tick cycle)",18,height-20,0xFF24364C);
        }
    }

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
                model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
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
                model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
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
                player.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("textures/entity/player/wide/steve.png"))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                if(i==0)TwoIDollHeadLayer.renderOnHead(item,player.head,pose,buffers,LightTexture.FULL_BRIGHT);
                else if(i==1||i==2){TwoIDollThirdPerson.render(item,player,HumanoidArm.RIGHT,false,pose,buffers,LightTexture.FULL_BRIGHT,TwoIDollCarryMotion.Pose.STILL);TwoIDollThirdPerson.render(item,player,HumanoidArm.LEFT,false,pose,buffers,LightTexture.FULL_BRIGHT,TwoIDollCarryMotion.Pose.STILL);}
                else {pose.scale(.7F,.7F,.7F);TwoIDollItemRenderer.get().renderByItem(item,ItemDisplayContext.GUI,pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);}
                buffers.endBatch();pose.popPose();
            }
        }
    }
}
