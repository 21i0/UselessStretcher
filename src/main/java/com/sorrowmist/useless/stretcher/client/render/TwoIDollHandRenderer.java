package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** First person actually shows the owner's palm underneath the sitting doll. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class TwoIDollHandRenderer {
    private static PlayerModel<LivingEntity> wide;
    private static PlayerModel<LivingEntity> slim;

    private TwoIDollHandRenderer() { }

    static void reload() {
        wide = slim = null;
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (!event.getItemStack().is(ModItems.TWO_I_DOLL.get())) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float swing = Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI);
        pose.translate(side * (0.34F - 0.05F * swing), -0.35F - 0.62F * event.getEquipProgress()
                + 0.04F * swing, -0.88F);
        pose.mulPose(Axis.YP.rotationDegrees(side * (5 - 8 * swing)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -3 * swing));
        renderOnPalm(event.getItemStack(), arm, player.getSkin(), !player.isInvisible(),
                player.isModelPartShown(arm == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE),
                pose, event.getMultiBufferSource(), event.getPackedLight(),
                TwoIDollCarryMotion.sample(player, event.getPartialTick()), TwoIDollWeather.sample(player, player.level(),
                        player.blockPosition(), event.getPartialTick(), player.getUUID().hashCode()));
        pose.popPose();
        event.setCanceled(true);
    }

    /** Shared with the isolated visual fixture; (0,0,0) is the top of the palm. */
    public static void renderOnPalm(ItemStack stack, HumanoidArm arm, PlayerSkin skin, boolean visible,
                                    boolean sleeveVisible, PoseStack pose, MultiBufferSource buffers, int light) {
        renderOnPalm(stack, arm, skin, visible, sleeveVisible, pose, buffers, light, TwoIDollCarryMotion.Pose.STILL);
    }

    public static void renderOnPalm(ItemStack stack, HumanoidArm arm, PlayerSkin skin, boolean visible,
                                    boolean sleeveVisible, PoseStack pose, MultiBufferSource buffers, int light,
                                    TwoIDollCarryMotion.Pose motion) {
        renderOnPalm(stack, arm, skin, visible, sleeveVisible, pose, buffers, light, motion, TwoIDollWeather.Pose.CLEAR);
    }

    public static void renderOnPalm(ItemStack stack, HumanoidArm arm, PlayerSkin skin, boolean visible,
                                    boolean sleeveVisible, PoseStack pose, MultiBufferSource buffers, int light,
                                    TwoIDollCarryMotion.Pose motion, TwoIDollWeather.Pose weather) {
        renderOnPalm(stack,arm,skin,visible,sleeveVisible,pose,buffers,light,motion,weather,TwoIDollItemRenderer.animationTime());
    }

    public static void renderOnPalm(ItemStack stack, HumanoidArm arm, PlayerSkin skin, boolean visible,
                                    boolean sleeveVisible, PoseStack pose, MultiBufferSource buffers, int light,
                                    TwoIDollCarryMotion.Pose motion, TwoIDollWeather.Pose weather, float time) {
        if (visible) {
            boolean isSlim = skin.model() == PlayerSkin.Model.SLIM;
            var models = Minecraft.getInstance().getEntityModels();
            PlayerModel<LivingEntity> playerModel;
            if (isSlim) {
                if (slim == null) slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
                playerModel = slim;
            } else {
                if (wide == null) wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
                playerModel = wide;
            }
            ModelPart hand = arm == HumanoidArm.RIGHT ? playerModel.rightArm : playerModel.leftArm;
            ModelPart sleeve = arm == HumanoidArm.RIGHT ? playerModel.rightSleeve : playerModel.leftSleeve;
            hand.resetPose();
            hand.setPos(0, 0, 0);
            sleeve.copyFrom(hand);
            // Rotate the forearm toward the viewer. Its y=10 palm lies at z=0,
            // and the upper arm surface lies just underneath the doll's feet.
            pose.pushPose();
            int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(sign * (isSlim ? 0.5F : 1.0F) / 16.0F, -0.1375F, 0.625F);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            hand.render(pose, buffers.getBuffer(RenderType.entitySolid(skin.texture())), light, OverlayTexture.NO_OVERLAY);
            if (sleeveVisible) sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),
                    light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // Bypass generic item centering here: the shared doll feet are exactly
        // on the palm, regardless of texture, dominant hand or idle gesture.
        TwoIDollItemRenderer.get().renderCarried(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY, motion, weather,time);
    }
}
