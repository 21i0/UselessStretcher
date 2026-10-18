package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.render.TwoIDollThirdPerson;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class TwoIPlayerArmMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void uselessStretcher$carryDoll(LivingEntity entity, float swing, float amount, float age,
                                           float yaw, float pitch, CallbackInfo ci) {
        var model = (PlayerModel<?>)(Object)this;
        if (entity.getMainHandItem().is(ModItems.TWO_I_DOLL.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm(), age);
        if (entity.getOffhandItem().is(ModItems.TWO_I_DOLL.get()))
            TwoIDollThirdPerson.poseArm(model, entity.getMainArm().getOpposite(), age);
    }
}
