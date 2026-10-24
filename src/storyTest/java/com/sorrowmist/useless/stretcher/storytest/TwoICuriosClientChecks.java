package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollCurioRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollSkin;
import com.sorrowmist.useless.stretcher.content.item.TwoIDollItem;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.client.render.CuriosLayer;

import java.util.UUID;

/** Uses an isolated integrated world, real Curios synchronization and actual Curios render layer. */
public final class TwoICuriosClientChecks {
    private static boolean active, requested, displayed, finished;
    private static int ticks, frames;
    private static final UUID DOLL = UUID.randomUUID();
    private TwoICuriosClientChecks() { }

    public static void start(Minecraft client) {
        check(CuriosRendererRegistry.getRenderer(ModItems.TWO_I_DOLL.get()).orElseThrow() instanceof TwoIDollCurioRenderer,
                "optional renderer registered with real Curios registry");
        active = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(TwoICuriosClientChecks::tick);
        var settings = new LevelSettings("Curios isolated check", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("curios-preview-" + System.currentTimeMillis(), settings,
                new WorldOptions(2L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!active || finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            check(++ticks < 2400, "integrated Curios preview timeout");
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            if (!requested) {
                requested = true;
                UUID owner = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new AssertionError("missing Curios capability"));
                    var stack = new ItemStack(ModItems.TWO_I_DOLL.get());
                    TwoIDollItem.claimOrCheckOwner(stack, player);
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.TWO_I_DOLL_ID.get(), DOLL);
                    inventory.getStacksHandler("ring").orElseThrow().getStacks().insertItem(0, stack, false);
                });
                return;
            }
            var syncedInventory = CuriosApi.getCuriosInventory(client.player);
            if (!syncedInventory.isPresent()) return;
            var inventory = syncedInventory.orElseThrow(() -> new AssertionError("missing Curios capability"));
            if (inventory.getStacksHandler("ring").isEmpty()) return;
            if (!displayed) {
                ItemStack stack = inventory.getStacksHandler("ring").orElseThrow().getStacks().getStackInSlot(0);
                if (!DOLL.equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.TWO_I_DOLL_ID.get()))) return;
                check(client.player.getUUID().equals(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.TWO_I_OWNER.get())), "server-equip identity and owner actually synchronized");
                validate(client);
                UsefulArmorClientChecks.validate(client);
                displayed = true; frames = 12;
                client.setScreen(preview());
                return;
            }
            if (--frames > 0) return;
            try (NativeImage shot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                shot.writeToFile(client.gameDirectory.toPath().resolve("screenshots/curios-native-layer.png"));
            }
            LogUtils.getLogger().info("2I CURIOS CLIENT PASSED: integrated-server ring equip synced UUID/owner; every slot renderer, visibility/cosmetic/vanilla-head priority; actual CuriosLayer screenshot");
            finished = true; CapsuleClientChecks.start(client);
        } catch (Throwable failure) {
            finished = true;
            LogUtils.getLogger().error("2I CURIOS CLIENT FAILED", failure);
            client.stop();
        }
    }

    private static void validate(Minecraft client) {
        var player = client.player;
        var inventory = CuriosApi.getCuriosInventory(player).orElseThrow(() -> new AssertionError("missing Curios capability"));
        var saved = inventory.saveInventory(false);
        ItemStack headBefore = player.getItemBySlot(EquipmentSlot.HEAD);
        try {
            clear(inventory);
            for (var entry : inventory.getCurios().entrySet()) {
                var handler = entry.getValue();
                ItemStack doll = new ItemStack(ModItems.TWO_I_DOLL.get());
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(doll, StretcherComponents.TWO_I_OWNER.get(), player.getUUID());
                var context = new SlotContext(entry.getKey(), player, 0, false, true);
                check(handler.getStacks().isItemValid(0, doll), "actual client validation accepts " + entry.getKey());
                handler.getStacks().setStackInSlot(0, doll);
                handler.getRenders().set(0, true);
                check(TwoIDollCurioRenderer.isDisplayed(context), "all slots select the same head perch: " + entry.getKey());
                handler.getRenders().set(0, false);
                check(!TwoIDollCurioRenderer.isDisplayed(context), "Curios render toggle hides the doll");
                handler.getRenders().set(0, true);
                player.setItemSlot(EquipmentSlot.HEAD, doll);
                check(!TwoIDollCurioRenderer.isDisplayed(context), "vanilla head slot does not draw duplicate doll");
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                handler.getCosmeticStacks().setStackInSlot(0, doll);
                check(!TwoIDollCurioRenderer.isDisplayed(context)
                                && TwoIDollCurioRenderer.isDisplayed(new SlotContext(entry.getKey(), player, 0, true, true)),
                        "cosmetic uses native Curios replacement without duplicate render");
                handler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                handler.getCosmeticStacks().setStackInSlot(0, ItemStack.EMPTY);
            }
        } finally { inventory.loadInventory(saved); player.setItemSlot(EquipmentSlot.HEAD, headBefore); }
    }

    private static Screen preview() {
        return new Screen(Component.literal("Curios native render layer")) {
            @Override public boolean isPauseScreen() { return false; }
            @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.fill(0, 0, width, height, 0xFF152236);
                graphics.drawCenteredString(font, "Curios: actual synchronized ring slot, head perch", width / 2, 32, 0xFFFFFFFF);
                UsefulArmorClientChecks.renderIcons(graphics, width / 2 - 44, height - 30);
                var player = minecraft.player;
                var renderer = (PlayerRenderer)minecraft.getEntityRenderDispatcher().getRenderer(player);
                PlayerModel<AbstractClientPlayer> model = renderer.getModel();
                for (int i = 0; i < 3; i++) {
                    model.head.resetPose();
                    model.head.yRot = i == 0 ? -0.25F : 0.35F;
                    model.head.xRot = i == 2 ? 0.20F : -0.10F;
                    model.head.y = i == 2 ? 4.2F : 0;
                    model.hat.copyFrom(model.head);
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(width * (i + 1) / 4F, height * 0.56F, 100);
                    pose.scale(170, 170, 170);
                    pose.mulPose(Axis.YP.rotationDegrees(i == 1 ? 90 : 165));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
                    model.head.render(pose, vertices, LightTexture.FULL_BRIGHT, 0);
                    model.hat.render(pose, vertices, LightTexture.FULL_BRIGHT, 0);
                    new CuriosLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(renderer).render(pose, buffers,
                            LightTexture.FULL_BRIGHT, player, 0, 0, partialTick, player.tickCount + partialTick, 0, 0);
                    buffers.endBatch(); pose.popPose();
                    graphics.drawCenteredString(font, i == 1 ? "side" : i == 2 ? "crouched + turn" : "front", width * (i + 1) / 4, height - 55, 0xFFFFFFFF);
                }
            }
        };
    }

    private static void clear(top.theillusivec4.curios.api.type.capability.ICuriosItemHandler inventory) {
        inventory.getCurios().values().forEach(handler -> {
            for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                handler.getCosmeticStacks().setStackInSlot(i, ItemStack.EMPTY);
            }
        });
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError("2i Curios client: " + message); }
}
