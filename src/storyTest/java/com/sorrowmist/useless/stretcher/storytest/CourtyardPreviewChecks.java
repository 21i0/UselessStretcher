package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldCourtyard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;

/** Isometric preview of the actual worldgen blueprint, not a substitute for an in-world sky test. */
public final class CourtyardPreviewChecks {
    private CourtyardPreviewChecks() { }
    public static Screen preview() {
        return new Screen(Component.literal("2i courtyard: actual block-model blueprint")) {
            @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
                g.fill(0, 0, width, height, 0xFF131C36);
                g.drawCenteredString(font, title, width / 2, 22, 0xFFF1E8FF);
                var pose = g.pose();
                pose.pushPose();
                pose.translate(width / 2.0, height * 0.53, 400);
                float scale = Math.min(width / 47.0F, height / 34.0F);
                pose.scale(scale, -scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(38));
                pose.mulPose(Axis.YP.rotationDegrees(-26));
                var buffers = minecraft.renderBuffers().bufferSource();
                for (int y = -1; y <= 6; y++) for (int z = -13; z <= 19; z++) for (int x = -13; x <= 13; x++) {
                    var state = TwoIStarfieldCourtyard.stateAt(x, y, z);
                    if (state == null) continue;
                    pose.pushPose();
                    pose.translate(x - 0.5D, y, z - 2);
                    minecraft.getBlockRenderer().renderSingleBlock(state, pose, buffers,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
                buffers.endBatch();
                pose.popPose();
                g.drawCenteredString(font, "Open arch / flower beds / seats / lantern pillars / starlight pedestal", width / 2, height - 32, 0xFFE0D9F9);
            }
        };
    }
}
