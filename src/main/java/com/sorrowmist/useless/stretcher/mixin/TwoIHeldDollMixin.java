package com.sorrowmist.useless.stretcher.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollCarryMotion;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollThirdPerson;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollWeather;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class TwoIHeldDollMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$renderDoll(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!stack.is(ModItems.TWO_I_DOLL.get()) || !(entity instanceof AbstractClientPlayer player)) return;
        var parent = ((ItemInHandLayer<?, ?>)(Object)this).getParentModel();
        if (!(parent instanceof PlayerModel<?> model)) return;
        float partial = Minecraft.getInstance().getFrameTime();
        TwoIDollThirdPerson.render(stack, model, arm, "slim".equals(player.getModelName()),
                pose, buffers, light, TwoIDollCarryMotion.sample(player, partial),
                TwoIDollWeather.sample(player, player.level(), player.blockPosition(), partial, player.getUUID().hashCode()));
        ci.cancel();
    }
}
