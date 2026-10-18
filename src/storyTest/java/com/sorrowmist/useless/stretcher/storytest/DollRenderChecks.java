package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollSkin;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollThirdPerson;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollWeather;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollHandRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollCarryMotion;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollHeadLayer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollItemRenderer;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Standalone fixture helpers for the opt-in isolated story client. */
public final class DollRenderChecks {
    private DollRenderChecks() { }

    public static void validate(Minecraft client) throws Exception {
        TwoIHandStabilityChecks.run();
        TwoIUmbrellaChecks.run(client);
        String expectedName = client.getLanguageManager().getSelected().equals("en_us") ? "2i Doll" : "2i玩偶";
        check(com.sorrowmist.useless.stretcher.init.ModBlocks.TWO_I_DOLL_DISPLAY.get().getName().getString().equals(expectedName),
                "placed doll block has a short localized name instead of its registry key");
        check(new ItemStack(ModItems.TWO_I_DOLL.get()).getHoverName().getString().equals(expectedName),
                "doll item shares the short localized block name");
        var tooltip = new java.util.ArrayList<Component>();
        ModItems.TWO_I_DOLL.get().appendHoverText(new ItemStack(ModItems.TWO_I_DOLL.get()),
                net.minecraft.world.item.Item.TooltipContext.EMPTY, tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
        check(tooltip.stream().noneMatch(line -> line.getString().contains("two_i.png")
                        || line.getString().contains("Replaceable skin") || line.getString().contains("可替换皮肤")),
                "player tooltip retains mascot identity without skin replacement instructions");
        try (var stream = client.getResourceManager().open(TwoIDollRenderer.TEXTURE);
             NativeImage source = NativeImage.read(stream);
             NativeImage normalized = TwoIDollSkin.normalizedCopy(source)) {
            check(TwoIDollSkin.usesSlimArms(source), "bundled artist skin is correctly detected as Alex");
            for (int[] palm : new int[][]{{48, 16}, {40, 48}}) {
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                    check((normalized.getPixelRGBA(palm[0] + x, palm[1] + y) >>> 24) == 255,
                            "normalized full-width palm is opaque");
                }
            }
            for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
                check(source.getPixelRGBA(x, y) == normalized.getPixelRGBA(x, y),
                        "normalization preserves face and hair pixels");
            }
            check((normalized.getPixelRGBA(56, 48) >>> 24) == 0,
                    "transparent sleeve overlay stays transparent");
        }
        try (NativeImage steve = new NativeImage(64, 64, false)) {
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) steve.setPixelRGBA(x, y, 0xFF6F8DA5);
            check(!TwoIDollSkin.usesSlimArms(steve), "opaque wide-arm replacement is Steve");
            try (NativeImage copy = TwoIDollSkin.normalizedCopy(steve)) {
                check(copy.getPixelRGBA(54, 20) == steve.getPixelRGBA(54, 20), "Steve UVs are unchanged");
            }
        }
        var model = new TwoIDollRenderer.Model(client.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
        for (int gesture = 0; gesture < 12; gesture++) {
            float time = gesture * 200 + 60;
            model.setupDecoration(time);
            check(Float.isFinite(model.head.xRot) && Float.isFinite(model.head.yRot), "finite idle transform");
            sameTransform(model.head, model.hat);
            sameTransform(model.body, model.jacket);
            sameTransform(model.leftArm, model.leftSleeve);
            sameTransform(model.rightArm, model.rightSleeve);
            sameTransform(model.leftLeg, model.leftPants);
            sameTransform(model.rightLeg, model.rightPants);
            float headY = model.head.y;
            model.setupDecoration(time);
            check(model.head.y == headY, "render pose must not accumulate between frames");
            check(model.footBottom(model.leftLeg) <= TwoIDollRenderer.FEET_Y + 0.001F
                    && model.footBottom(model.rightLeg) <= TwoIDollRenderer.FEET_Y + 0.001F,
                    "seated feet never sink into the shelf");
            check(model.leftLeg.x > 2.3F && model.rightLeg.x < -2.3F
                            && model.leftLeg.yRot < 0 && model.rightLeg.yRot > 0,
                    "seated knees and feet open outward instead of folding together");
            check(innerPantsEdge(model.leftPants, true) > innerPantsEdge(model.rightPants, false),
                    "even the outer pants cubes stay apart throughout seated poses");
            check(Math.abs(model.body.y + 12.25F * model.body.yScale - TwoIDollRenderer.FEET_Y) < 0.001F,
                    "rump outer skin rests on the same support plane in front, side and rear");
        }
        float seatedHeadScale = model.head.yScale;
        float seatedArmScale = model.leftArm.yScale;
        float seatedLegScale = model.leftLeg.yScale;
        model.setupStanding(0, 0, 42, 0, 0, 0, 0);
        check(model.blink() > 0.9F && model.head.yScale == seatedHeadScale,
                "blink closes eyes without changing head proportions");
        try (var stream = client.getResourceManager().open(TwoIDollRenderer.TEXTURE);
             NativeImage original = NativeImage.read(stream);
             NativeImage closed = TwoIDollSkin.blinkCopy(original, true)) {
            int changed = 0;
            for (int y=0;y<64;y++) for (int x=0;x<64;x++) {
                if (original.getPixelRGBA(x,y) != closed.getPixelRGBA(x,y)) {
                    changed++;
                    check(y == 14 && ((x>=9 && x<=10)||(x>=13 && x<=14)||(x>=41 && x<=42)||(x>=45 && x<=46)),
                            "blink changes only eye pixels");
                }
            }
            check(changed >= 4, "closed eye texture really changes both eyes");
        }
        model.setupStanding(0, 0, 60, 0, 0, 0, 0);
        check(Math.abs(model.body.yScale - 0.75F) < 0.001F
                        && Math.abs(model.leftLeg.yScale - 0.50F) < 0.001F
                        && Math.abs(model.leftArm.yScale - 0.50F) < 0.001F,
                "doll proportions match the local Poppet reference with a longer nine-pixel torso");
        model.setupStanding(0, 0, 0, 0, 0, 0, 0, TwoIDollEntity.ACTION_UMBRELLA, 1.0F);
        check(model.umbrellaVisible() && model.rightArm.xRot < -0.1F,
                "rain reaction opens the umbrella in the right hand");
        model.setupStanding(0, 0, 0, 0, 0, 0, 0, TwoIDollEntity.ACTION_SNEEZE, 0.5F);
        check(!model.umbrellaVisible() && model.head.xRot > 0.05F,
                "snow reaction produces a sneeze pose");
        model.setupStanding(0, 0, 2, 0, 0, 0, 0, TwoIDollEntity.ACTION_SHIVER, 0.5F);
        check(Math.abs(model.body.zRot) > 0.001F, "thunder reaction shivers the body");
        model.setupDecoration(60);
        model.applyWeather(new TwoIDollWeather.Pose(TwoIDollEnvironment.RAIN | TwoIDollEnvironment.THUNDER, 1, -1),2);
        check(model.umbrellaVisible() && Math.abs(model.body.zRot) > 0.001F, "rain and thunder coexist for seated dolls");
        model.setupDecoration(60);
        model.applyWeather(new TwoIDollWeather.Pose(TwoIDollEnvironment.COLD,0,0.5F),2);
        check(model.noseVisible() && model.blink() > 0.9F, "cold doll has a runny nose and closes eyes to sneeze");
        for (int frame = 0; frame < 80; frame++) {
            model.setupStanding(frame * 0.2F, 0.4F, frame, 0, 0, 0, 0);
            check(model.head.yScale <= seatedHeadScale && model.head.yScale == seatedHeadScale
                    && model.leftArm.yScale == seatedArmScale
                    && model.leftLeg.yScale == seatedLegScale, "standing and sitting use identical proportions");
            sameTransform(model.head, model.hat);
            sameTransform(model.body, model.jacket);
            sameTransform(model.leftArm, model.leftSleeve);
            sameTransform(model.rightArm, model.rightSleeve);
            sameTransform(model.leftLeg, model.leftPants);
            sameTransform(model.rightLeg, model.rightPants);
            float left = model.footBottom(model.leftLeg), right = model.footBottom(model.rightLeg);
            check(left <= TwoIDollRenderer.FEET_Y + 0.001F && right <= TwoIDollRenderer.FEET_Y + 0.001F,
                    "walking soles never sink into the ground");
            check(Math.abs(Math.max(left, right) - TwoIDollRenderer.FEET_Y) < 0.001F,
                    "walking keeps one supporting foot on the ground");
        }
        model.setupStanding(0, 0, 0, 0, 0, TwoIDollEntity.ACTION_PATTED, 0.5F);
        check(model.head.yScale < seatedHeadScale && model.head.xScale > seatedHeadScale,
                "pat produces a soft head squash");
        sameTransform(model.head, model.hat);
        model.setupStanding(0, 0, 0, 0, 0, 0, 0);
        check(model.head.yScale == seatedHeadScale, "head squash resets on the next idle pose");
        check(Math.abs(model.footBottom(model.leftLeg) - TwoIDollRenderer.FEET_Y) < 0.001F,
                "standing soles align to the same item/block ground plane");
        var spring = new TwoIDollCarryMotion.Spring();
        spring.step(0, 0, 0, false, 0);
        spring.step(0, 0, -0.5F, true, 3.0F);
        check(spring.pose(1).lift() > 0 && spring.pose(1).falling() > 0,
                "carried doll lifts and raises its arms during a fall");
        ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());
        check(client.getItemRenderer().getModel(stack, null, null, 0).isCustomRenderer(),
                "all item contexts route to the animated custom renderer");
        check(client.getResourceManager().getResource(TwoIDollRenderer.TEXTURE).isPresent(), "shared skin is present");
        check(!client.getItemRenderer().getModel(stack, null, null, 0).getParticleIcon().contents().name()
                        .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),
                "particle atlas also resolves the shared skin");
        for (boolean slim : new boolean[]{false, true}) {
            var playerModel = new PlayerModel<LivingEntity>(client.getEntityModels().bakeLayer(
                    slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            for (float crouch : new float[]{0, 4.2F}) for (float pitch : new float[]{-0.7F, 0, 0.7F}) {
                playerModel.head.y = crouch;
                playerModel.head.xRot = pitch;
                playerModel.head.yRot = 0.6F;
                PoseStack headPose = new PoseStack();
                playerModel.head.translateAndRotate(headPose);
                var expected = new org.joml.Vector3f(0, -TwoIDollHeadLayer.HEAD_CLEARANCE, 0)
                        .mulPosition(headPose.last().pose());
                PoseStack dollPose = new PoseStack();
                TwoIDollHeadLayer.transformToHead(playerModel.head, dollPose);
                var actual = new org.joml.Vector3f().mulPosition(dollPose.last().pose());
                check(actual.distance(expected) < 0.00001F, "head-mounted doll follows pitch, yaw and crouch pivot");
                var above = new org.joml.Vector3f(0, 0.1F, 0).mulPosition(dollPose.last().pose());
                var localAbove = above.mulPosition(new org.joml.Matrix4f(headPose.last().pose()).invert());
                check(localAbove.y < -8.5F / 16F, "entire upright doll stays above outer hat layer");
            }
        }
        for (boolean slim : new boolean[]{false,true}) for (HumanoidArm side : HumanoidArm.values()) {
            var playerModel = new PlayerModel<LivingEntity>(client.getEntityModels().bakeLayer(
                    slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER),slim);
            TwoIDollThirdPerson.poseArm(playerModel,side,20);
            ModelPart arm = side == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm;
            PoseStack loweredArm = new PoseStack();
            arm.translateAndRotate(loweredArm);
            var handHeight = new org.joml.Vector3f(0, 10F / 16, 0).mulPosition(loweredArm.last().pose());
            check(handHeight.y - arm.y / 16 > 0.35F,
                    "third-person holding hand rests below the shoulder rather than raising like a zombie");
            PoseStack initialPalm = new PoseStack();
            TwoIDollThirdPerson.transformToPalm(arm, side, slim, initialPalm);
            var stationaryPalm = new org.joml.Matrix4f(initialPalm.last().pose());
            for (int age = 0; age < 600; age += 3) {
                TwoIDollThirdPerson.poseArm(playerModel, side, age);
                PoseStack currentPalm = new PoseStack();
                TwoIDollThirdPerson.transformToPalm(arm, side, slim, currentPalm);
                check(stationaryPalm.equals(currentPalm.last().pose()),
                        "idle time must not shake the third-person supporting arm or palm on either Steve/Alex hand");
            }
            for(float crouch : new float[]{0,4.2F}) {
                arm.y = 2 + crouch;
                PoseStack palm = new PoseStack();
                TwoIDollThirdPerson.transformToPalm(arm,side,slim,palm);
                var origin = new org.joml.Vector3f().mulPosition(palm.last().pose());
                var up = new org.joml.Vector3f(0,0.1F,0).mulPosition(palm.last().pose()).sub(origin);
                check(up.y < -0.079F && Math.abs(up.x) < 0.001F && Math.abs(up.z) < 0.001F,
                        "third-person doll sits upright on either Steve/Alex palm, including crouch");
            }
        }
        net.minecraft.client.renderer.MultiBufferSource forbidden = renderType -> {
            throw new AssertionError("2i HEAD item path must not double-render vanilla custom head layer");
        };
        TwoIDollItemRenderer.get().renderByItem(stack, ItemDisplayContext.HEAD, new PoseStack(), forbidden,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static void sameTransform(ModelPart base, ModelPart layer) {
        check(base.x == layer.x && base.y == layer.y && base.z == layer.z
                        && base.xRot == layer.xRot && base.yRot == layer.yRot && base.zRot == layer.zRot
                        && base.xScale == layer.xScale && base.yScale == layer.yScale && base.zScale == layer.zScale,
                "outer skin layer follows the complete base transform");
    }

    private static float innerPantsEdge(ModelPart part, boolean left) {
        PoseStack pose = new PoseStack();
        part.translateAndRotate(pose);
        float edge = left ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        for (float x : new float[]{-2.25F, 2.25F}) for (float y : new float[]{-0.25F, 12.25F}) {
            for (float z : new float[]{-2.25F, 2.25F}) {
                float value = new org.joml.Vector3f(x / 16, y / 16, z / 16).mulPosition(pose.last().pose()).x;
                edge = left ? Math.min(edge, value) : Math.max(edge, value);
            }
        }
        return edge;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("2i visual: " + name);
    }

    /** Same-rig sit/stand/walk/pat comparison, idle variants, and real item contexts. */
    public static Screen preview() {
        return new Screen(Component.literal("2i animated doll visual fixture")) {
            private TwoIDollRenderer.Model model;
            private final ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());

            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (model == null) model = new TwoIDollRenderer.Model(minecraft.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
                graphics.fill(0, 0, width, height, 0xFFBDD0E0);
                graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
                String[] names = {"seated doll", "standing doll", "short step L", "short step R", "head pat", "wave",
                        "look around", "swing feet", "stretch", "sleepy", "dust off", "shy"};
                int[] idle = {0, 1, 3, 4, 5, 7};
                for (int variant = 0; variant < 12; variant++) {
                    int x = width * (variant % 6 + 1) / 7;
                    int row = variant / 6;
                    int baseY = row == 0 ? 220 : height - 245;
                    switch (variant) {
                        case 0 -> model.setupDecoration(60);
                        case 1 -> model.setupStanding(0, 0, 0, 0, 0, 0, 0);
                        case 2 -> model.setupStanding(1.7F, 0.4F, 20, 0, 0, 0, 0);
                        case 3 -> model.setupStanding(5.1F, 0.4F, 40, 0, 0, 0, 0);
                        case 4 -> model.setupStanding(0, 0, 20, 0, 0, TwoIDollEntity.ACTION_PATTED, 0.5F);
                        case 5 -> model.setupStanding(0, 0, 20, 0, 0, TwoIDollEntity.ACTION_GREETING, 0.5F);
                        default -> model.setupDecoration(idle[variant - 6] * 200 + 60);
                    }
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    float scale = Math.min(70, width / 15.0F);
                    pose.translate(x, baseY - TwoIDollRenderer.FEET_Y / 16.0F * scale, 100);
                    pose.scale(scale, scale, scale);
                    pose.mulPose(Axis.YP.rotationDegrees(165));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.fill(x - 30, baseY + 1, x + 30, baseY + 2, 0xFF71899B);
                    graphics.drawCenteredString(font, names[variant], x, baseY + 8, 0xFFFFFFFF);
                }
                PlayerSkin fixtureSkin = new PlayerSkin(ResourceLocation.withDefaultNamespace(
                        "textures/entity/player/wide/steve.png"), null, null, null, PlayerSkin.Model.WIDE, true);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    int x = width * (arm == HumanoidArm.LEFT ? 2 : 5) / 7;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, 380, 150);
                    pose.scale(105, -105, -105);
                    pose.mulPose(Axis.XP.rotationDegrees(-16));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    TwoIDollHandRenderer.renderOnPalm(stack, arm, fixtureSkin, true, true, pose, buffers, LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, "actual palm: " + arm.name(), x, 410, 0xFFFFFFFF);
                }
                for (boolean slim : new boolean[]{false, true}) {
                    int x = width * (slim ? 4 : 3) / 7;
                    var playerModel = new PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(
                            slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                    playerModel.head.y = slim ? 4.2F : 0;
                    playerModel.head.yRot = slim ? 0.4F : -0.3F;
                    playerModel.head.xRot = slim ? 0.2F : -0.1F;
                    playerModel.hat.copyFrom(playerModel.head);
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, 360, 150);
                    pose.scale(80, 80, 80);
                    pose.mulPose(Axis.YP.rotationDegrees(170));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    ResourceLocation skinTexture = slim ? ResourceLocation.withDefaultNamespace(
                            "textures/entity/player/slim/alex.png") : fixtureSkin.texture();
                    var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(skinTexture));
                    playerModel.head.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    playerModel.hat.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    TwoIDollHeadLayer.renderOnHead(stack, playerModel.head, pose, buffers, LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, slim ? "head: Alex crouch" : "head: Steve", x, 410, 0xFFFFFFFF);
                }
                ItemDisplayContext[] contexts = {ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED};
                for (int i = 0; i < contexts.length; i++) {
                    int x = width * (i + 1) / 8;
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, height - 115, 150);
                    pose.scale(90, -90, 90);
                    var buffers = minecraft.renderBuffers().bufferSource();
                    minecraft.getItemRenderer().renderStatic(stack, contexts[i], LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, contexts[i].name().replace("PERSON", "P"), x, height - 40, 0xFFFFFFFF);
                }
            }
        };
    }

    /** Production rendering paths for the fixes that cannot be judged from a frontal item icon. */
    public static Screen environmentPreview() {
        return new Screen(Component.literal("2i proportions, weather and third-person fixture")) {
            private TwoIDollRenderer.Model model;
            private final ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());
            @Override
            public void render(GuiGraphics g,int mx,int my,float partial) {
                if(model==null) model=new TwoIDollRenderer.Model(minecraft.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
                g.fill(0,0,width,height,0xFFBDD0E0);
                g.drawCenteredString(font,title,width/2,12,0xFFFFFFFF);
                String[] names={"seat front","seat side","seat back","rain companion","cold sneeze","rain + thunder"};
                for(int i=0;i<6;i++) {
                    float time=i==4 ? 20 : 60;
                    if(i<3) model.setupDecoration(time); else model.setupStanding(0,0,time,0,0,0,0);
                    int flags=i==3 ? TwoIDollEnvironment.RAIN : i==4 ? TwoIDollEnvironment.COLD
                            : i==5 ? TwoIDollEnvironment.RAIN | TwoIDollEnvironment.THUNDER : 0;
                    model.applyWeather(new TwoIDollWeather.Pose(flags,flags==0||i==4?0:1,i==4?0.5F:-1),time);
                    int x=width*(i+1)/7, base=210;
                    PoseStack pose=g.pose(); pose.pushPose();
                    float scale=Math.min(85,width/14F);
                    pose.translate(x,base-24/16F*scale,100); pose.scale(scale,scale,scale);
                    pose.mulPose(Axis.YP.rotationDegrees(i==1?90:i==2?0:165));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),
                            LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    model.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    buffers.endBatch(); pose.popPose();
                    g.fill(x-35,base+1,x+35,base+2,0xFF71899B);
                    g.drawCenteredString(font,names[i],x,base+10,0xFFFFFFFF);
                }
                for(int i=0;i<4;i++) {
                    boolean slim=i>=2; HumanoidArm side=i%2==0?HumanoidArm.RIGHT:HumanoidArm.LEFT;
                    var player=new PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(
                            slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);
                    player.young=false;
                    if(slim) { player.body.xRot=0.35F; player.head.y=4.2F; player.leftArm.y=player.rightArm.y=5.2F; }
                    TwoIDollThirdPerson.poseArm(player,side,20);
                    PoseStack pose=g.pose(); pose.pushPose();
                    int x=width*(i+1)/5;
                    pose.translate(x,320,100); pose.scale(90,90,90); pose.mulPose(Axis.YP.rotationDegrees(145));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    var skin=ResourceLocation.withDefaultNamespace(slim?"textures/entity/player/slim/alex.png":"textures/entity/player/wide/steve.png");
                    player.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(skin)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    TwoIDollThirdPerson.render(stack,player,side,slim,pose,buffers,LightTexture.FULL_BRIGHT,TwoIDollCarryMotion.Pose.STILL);
                    buffers.endBatch(); pose.popPose();
                    g.drawCenteredString(font,(slim?"Alex crouch ":"Steve ")+side,x,470,0xFFFFFFFF);
                }
                for(int i=0;i<6;i++) {
                    int x=width*(i+1)/7,base=height-70;
                    float time=i==3?42:60;
                    if(i==2||i==3) model.setupStanding(0,0,time,0,0,0,0); else model.setupDecoration(time);
                    if(i==0||i==1) model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(0.1F,0,0,1,i==1?1:0),time);
                    if(i>=4) model.applyWeather(new TwoIDollWeather.Pose(i==4?TwoIDollEnvironment.RAIN:TwoIDollEnvironment.COLD,
                            i==4?1:0,i==5?0.5F:-1),time);
                    PoseStack pose=g.pose(); pose.pushPose(); float scale=80;
                    pose.translate(x,base-24/16F*scale,100); pose.scale(scale,scale,scale); pose.mulPose(Axis.YP.rotationDegrees(170));
                    var buffers=minecraft.renderBuffers().bufferSource();
                    model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(model.blink()))),
                            LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    model.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                    buffers.endBatch(); pose.popPose();
                    g.drawCenteredString(font,new String[]{"jump: look around","fall: arms up","eyes open","eyes closed","sitting rain","sitting sneeze"}[i],x,base+12,0xFFFFFFFF);
                }
            }
        };
    }
}
