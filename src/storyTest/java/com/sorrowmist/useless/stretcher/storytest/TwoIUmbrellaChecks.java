package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollCarryMotion;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollWeather;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Actual baked arm/head/accessory transforms across simultaneous gestures and weather. */
public final class TwoIUmbrellaChecks {
    private TwoIUmbrellaChecks() { }

    public static void run(Minecraft client) throws Exception {
        var model = new TwoIDollRenderer.Model(client.getEntityModels().bakeLayer(TwoIDollRenderer.LAYER));
        var field = TwoIDollRenderer.Model.class.getDeclaredField("umbrella");
        field.setAccessible(true);
        ModelPart umbrella = (ModelPart)field.get(model);
        var cubesField = ModelPart.class.getDeclaredField("cubes"); cubesField.setAccessible(true);
        @SuppressWarnings("unchecked") var cubes = (java.util.List<ModelPart.Cube>)cubesField.get(umbrella);
        check(cubes.stream().map(cube -> cube.minY).min(Float::compare).orElseThrow() > -20.45F,
                "shaft and hub terminate inside the canopy and cannot protrude through the apex");
        int poses = 0;
        for (boolean seated : new boolean[]{false, true}) for (int gesture = 0; gesture < 12; gesture++) {
            for (float open : new float[]{0.15F, 0.5F, 1}) for (float falling : new float[]{0, 1}) {
                float time = gesture * 200 + 60;
                if (seated) {
                    model.setupDecoration(time);
                    model.applyCarriedMotion(new TwoIDollCarryMotion.Pose(0.1F, 0, 0, 1, falling), time);
                } else {
                    model.setupStanding(gesture, 0.4F, time, gesture % 2 == 0 ? 40 : -40,
                            gesture % 2 == 0 ? 30 : -30, gesture == 0 ? TwoIDollEntity.ACTION_PATTED : gesture, 0.5F);
                }
                model.applyWeather(new TwoIDollWeather.Pose(TwoIDollEnvironment.RAIN | TwoIDollEnvironment.THUNDER
                        | TwoIDollEnvironment.COLD, open, 0.5F), time);
                PoseStack arm = new PoseStack(); model.rightArm.translateAndRotate(arm);
                Vector3f palm = new Vector3f(-1F / 16, 10F / 16, 0).mulPosition(arm.last().pose());
                check(palm.distance(new Vector3f(umbrella.x / 16, umbrella.y / 16, umbrella.z / 16)) < 0.00001F,
                        "umbrella grip remains exactly in the actual palm: " + palm + " / "
                                + umbrella.x/16 + "," + umbrella.y/16 + "," + umbrella.z/16);
                PoseStack head = new PoseStack(); model.hat.translateAndRotate(head);
                float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
                for (float x : new float[]{-4.25F, 4.25F}) for (float y : new float[]{-8.25F, 0.25F}) {
                    for (float z : new float[]{-4.25F, 4.25F}) {
                        var corner = new Vector3f(x / 16, y / 16, z / 16).mulPosition(head.last().pose());
                        minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y);
                    }
                }
                check((umbrella.x + 0.18F) / 16 < minX - 0.01F,
                        "thin mast stays clear of the animated head including the outer hat layer: mast="
                                + umbrella.x + ", headMinX=" + minX + ", armZ=" + model.rightArm.zRot);
                float canopyBottom = (umbrella.y - 20 + 4.9F * umbrella.getChild("canopy").yScale) / 16;
                check(canopyBottom < minY, "dome's lowest rim stays above the whole animated head");
                poses++;
            }
        }
        LogUtils.getLogger().info("2I UMBRELLA GRIP PASSED: {} baked poses; attached palm, head/hat clearance, canopy clearance and shaft cap", poses);
        var renderer = com.sorrowmist.useless.stretcher.client.render.TwoIDollItemRenderer.get();
        var buffers = client.renderBuffers().bufferSource();
        renderer.renderCarried(new net.minecraft.world.item.ItemStack(
                        com.sorrowmist.useless.stretcher.init.ModItems.TWO_I_DOLL.get()), new PoseStack(), buffers,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                new TwoIDollCarryMotion.Pose(0.1F, 0, 0, 1, 1),
                new TwoIDollWeather.Pose(TwoIDollEnvironment.RAIN | TwoIDollEnvironment.THUNDER, 1, -1));
        buffers.endBatch();
        var itemModelField = renderer.getClass().getDeclaredField("model"); itemModelField.setAccessible(true);
        var carriedModel = (TwoIDollRenderer.Model)itemModelField.get(renderer);
        var carriedUmbrella = (ModelPart)field.get(carriedModel);
        PoseStack carriedArm = new PoseStack(); carriedModel.rightArm.translateAndRotate(carriedArm);
        Vector3f carriedPalm = new Vector3f(-1F / 16, 10F / 16, 0).mulPosition(carriedArm.last().pose());
        check(carriedPalm.distance(new Vector3f(carriedUmbrella.x / 16, carriedUmbrella.y / 16,
                        carriedUmbrella.z / 16)) < 0.00001F,
                "actual carried renderer must resolve falling before applying the final umbrella grip");
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError("2i umbrella: " + message);
    }
}
