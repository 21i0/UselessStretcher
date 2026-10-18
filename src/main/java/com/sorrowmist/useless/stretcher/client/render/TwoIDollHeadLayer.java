package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** A real equipped item, rendered on the animated head bone without an entity or server ticker. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TwoIDollHeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // The player's outer head layer extends 0.5 pixels beyond the eight-pixel head.
    // This small clearance keeps the doll's lowest animated sole above the hat skin.
    public static final float HEAD_CLEARANCE = 8.65F / 16.0F;
    public static final float HEAD_DOLL_SCALE = 0.80F;

    public TwoIDollHeadLayer(PlayerRenderer renderer) {
        super(renderer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new TwoIDollHeadLayer(playerRenderer));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float yaw, float pitch) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!stack.is(ModItems.TWO_I_DOLL.get())) return;
        pose.pushPose();
        transformToHead(getParentModel().head, pose);
        TwoIDollItemRenderer.get().renderCarried(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                TwoIDollCarryMotion.sample(player, partialTick), TwoIDollWeather.sample(player, player.level(),
                        player.blockPosition(), partialTick, player.getUUID().hashCode()));
        pose.popPose();
    }

    /** The same transform is used by the isolated preview, including crouched and turned heads. */
    public static void renderOnHead(ItemStack stack, ModelPart head, PoseStack pose,
                                    MultiBufferSource buffers, int light) {
        pose.pushPose();
        transformToHead(head, pose);
        TwoIDollItemRenderer.get().renderAtFeet(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    public static void transformToHead(ModelPart head, PoseStack pose) {
        head.translateAndRotate(pose);
        pose.translate(0, -HEAD_CLEARANCE, 0);
        // Player model space points down and forward is -Z; renderAtFeet uses up/+Z.
        pose.scale(HEAD_DOLL_SCALE, -HEAD_DOLL_SCALE, -HEAD_DOLL_SCALE);
    }
}
