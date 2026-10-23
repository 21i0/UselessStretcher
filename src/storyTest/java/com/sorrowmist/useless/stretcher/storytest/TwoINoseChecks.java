package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.client.render.*;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;

/** Nose anchoring through the shared standing, seated and carried model. */
final class TwoINoseChecks {
    private static int frames;
    private static Screen gallery;
    static boolean tick(Minecraft client) throws Exception {
        if (gallery == null) {
            DollRenderChecks.validate(client);
            var root = client.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER);
            var model = new TwoIDollRenderer.Model(root);
            var nose = root.getChild("nose_drop");
            var cubesField = ModelPart.class.getDeclaredField("cubes"); cubesField.setAccessible(true);
            var cubes = (java.util.List<ModelPart.Cube>)cubesField.get(nose);
            float anchor = nose.y;
            check(anchor < 0, "nose starts on the face");
            for (int i = 0; i < 240; i++) {
                if (i % 2 == 0) model.setupDecoration(i); else model.setupStanding(0,0,i,25,10,0,0);
                model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(0.1F, -0.1F, 0.1F, 0.4F, 0.3F), i);
                model.applyWeather(new TwoIDollWeather.Pose(TwoIDollEnvironment.COLD, 0, (i % 40) / 40F), i);
                check(model.noseVisible() && nose.y == anchor, "nose pivot remains fixed during breathing/sneezing");
                for (var cube : cubes) {
                    check(cube.minY >= 0 && cube.maxY > cube.minY, "drip extends toward the chin, not toward the forehead");
                    check(nose.y + cube.maxY * nose.yScale <= 0, "drip stays above the chin");
                }
            }
            LogUtils.getLogger().info("2I NOSE GEOMETRY PASSED: 240 standing/seated/carried cold poses, fixed nostril pivot, positive downward growth");
            gallery = new Screen(Component.literal("2i cold reaction: nose flows down from the nostrils")) {
                private final TwoIDollRenderer.Model doll = new TwoIDollRenderer.Model(client.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
                @Override public void render(GuiGraphics g, int mx, int my, float partial) {
                    g.fill(0,0,width,height,0xFFBDD0E0);
                    g.drawCenteredString(font,title,width/2,16,0xFF23303E);
                    String[] labels = {"standing / front", "standing / sneeze", "standing / profile", "seated / front", "carried / falling", "seated / sneeze"};
                    for (int i = 0; i < labels.length; i++) {
                        float time = 35;
                        if (i < 3) doll.setupStanding(0,0,time,0,0,0,0); else doll.setupDecoration(time);
                        if (i == 4) doll.applyCarriedMotion(new TwoIDollCarryMotion.Pose(0.1F,-0.1F,0.1F,0.8F,1),time);
                        doll.applyWeather(new TwoIDollWeather.Pose(TwoIDollEnvironment.COLD,0,i == 1 || i == 5 ? 0.5F : -1),time);
                        float scale = 160;
                        int x = width * (i % 3 * 2 + 1) / 6, base = i < 3 ? 355 : 735;
                        var pose = g.pose(); pose.pushPose();
                        pose.translate(x,base - 24 / 16F * scale,100);
                        pose.scale(scale,scale,scale); pose.mulPose(Axis.YP.rotationDegrees(i == 2 ? 110 : 175));
                        var buffers = minecraft.renderBuffers().bufferSource();
                        doll.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TwoIDollSkin.texture(doll.blink()))),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                        doll.renderAccessories(pose,buffers,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
                        buffers.endBatch(); pose.popPose();
                        g.drawCenteredString(font,labels[i],x,base+12,0xFF23303E);
                    }
                }
            };
            client.setScreen(gallery);
            return false;
        }
        if (++frames < 12) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/two-i-nose-down.png"));
        }
        LogUtils.getLogger().info("2I NOSE CHECKS PASSED: shared rig rendered standing, seated, falling, sneezing and profile");
        return true;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
