package com.sorrowmist.useless.stretcher.client.render;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
public record NativePlayerSkin(ResourceLocation texture, String model) {
    public static NativePlayerSkin of(AbstractClientPlayer player) { return new NativePlayerSkin(player.getSkinTextureLocation(),player.getModelName()); }
}
