package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the real armor layer with a draw-call counter and checks baked animated item sprites. */
final class UsefulArmorClientChecks {
    private UsefulArmorClientChecks() { }

    static void validate(Minecraft client) throws Exception {
        var pieces = List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS);
        var suffixes = List.of("helmet", "chestplate", "leggings", "boots");
        for (int i = 0; i < pieces.size(); i++) {
            var piece = pieces.get(i);
            var texture = ResourceLocation.fromNamespaceAndPath("useless_stretcher", "textures/item/" + piece.getId().getPath() + ".png");
            var vanilla = ResourceLocation.withDefaultNamespace("textures/item/diamond_" + suffixes.get(i) + ".png");
            try (var stream = client.getResourceManager().open(texture);
                 var maskStream = client.getResourceManager().open(vanilla);
                 NativeImage image = NativeImage.read(stream); NativeImage mask = NativeImage.read(maskStream)) {
                check(image.getWidth() == 16 && image.getHeight() == 32, "two useful-ingot frames");
                int changes = 0;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    check(image.getPixelRGBA(x, y) >>> 24 == mask.getPixelRGBA(x, y) >>> 24,
                            "diamond armor silhouette preserved");
                    if (image.getPixelRGBA(x, y) != image.getPixelRGBA(x, y + 16)) changes++;
                }
                check(changes > 4, "armor animation frames differ");
            }
            var model = client.getItemRenderer().getModel(new ItemStack(piece.get()), client.level, client.player, 0);
            check(model.getParticleIcon().contents().name().getPath().equals("item/" + piece.getId().getPath()),
                    "baked model resolves its own armor icon");
            check(model.getParticleIcon().contents().getUniqueFrames().count() == 2, "baked sprite retains animation metadata");
        }

        var player = client.player;
        var renderer = (PlayerRenderer) client.getEntityRenderDispatcher().getRenderer(player);
        var layer = new HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidModel<AbstractClientPlayer>>(
                renderer, new HumanoidModel<>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), client.getModelManager());
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        var saved = new java.util.ArrayList<ItemStack>();
        AtomicInteger draws = new AtomicInteger();
        var buffers = client.renderBuffers().bufferSource();
        MultiBufferSource counted = type -> { draws.incrementAndGet(); return buffers.getBuffer(type); };
        try {
            for (int i = 0; i < slots.length; i++) {
                saved.add(player.getItemBySlot(slots[i]));
                player.setItemSlot(slots[i], new ItemStack(pieces.get(i).get()));
            }
            layer.render(new PoseStack(), counted, LightTexture.FULL_BRIGHT, player, 0, 0, 0, 0, 0, 0);
            check(draws.get() == 0, "actual equipped useful armor emits zero armor/trim/glint draw calls");
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            layer.render(new PoseStack(), counted, LightTexture.FULL_BRIGHT, player, 0, 0, 0, 0, 0, 0);
            check(draws.get() > 0, "ordinary diamond armor control still renders");
        } finally {
            buffers.endBatch();
            for (int i = 0; i < saved.size(); i++) player.setItemSlot(slots[i], saved.get(i));
        }
        LogUtils.getLogger().info("USEFUL ARMOR CLIENT CHECKS PASSED: four baked animated diamond silhouettes; real player armor layer emits zero draws; diamond control remains visible");
    }

    static void renderIcons(GuiGraphics graphics, int left, int top) {
        var items = List.of(ModItems.USEFUL_ARMOR_HELMET, ModItems.USEFUL_ARMOR_CHESTPLATE,
                ModItems.USEFUL_ARMOR_LEGGINGS, ModItems.USEFUL_ARMOR_BOOTS);
        for (int i = 0; i < items.size(); i++) graphics.renderItem(new ItemStack(items.get(i).get()), left + i * 24, top);
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError("Useful armor client: " + message);
    }
}
