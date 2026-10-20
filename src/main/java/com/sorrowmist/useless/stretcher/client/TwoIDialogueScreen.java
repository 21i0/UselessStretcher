package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A transparent, character-centered interaction panel for 2i. */
public final class TwoIDialogueScreen extends Screen implements TwoIInteractionScreen {
    /** The compact AE-style interaction window leaves the world visible around it. */
    private static final int PANEL_HEIGHT = 250;
    private final int entityId;
    private final boolean itemMode;
    private final InteractionHand itemHand;
    private boolean inviteReady;
    private final boolean invitationUsed;
    private int bond;
    private Button inviteButton;
    private TwoIDollEntity preview;
    private Level previewLevel;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private Button talkButton;
    private Button patButton;
    private Button giftButton;
    private Button reclaimButton;
    private int startWait;

    public TwoIDialogueScreen(int entityId, boolean inviteReady, int giftScore) {
        this(entityId, false, null, inviteReady, false, giftScore);
    }

    private TwoIDialogueScreen(int entityId, boolean itemMode, InteractionHand itemHand,
                               boolean inviteReady, boolean invitationUsed, int bond) {
        super(Component.translatable("gui.useless_stretcher.two_i.title"));
        this.entityId = entityId;
        this.itemMode = itemMode;
        this.itemHand = itemHand;
        this.inviteReady = inviteReady;
        this.invitationUsed = invitationUsed;
        this.bond = Math.max(-100, Math.min(100, bond));
    }

    public static TwoIDialogueScreen forItem(InteractionHand hand, int giftScore, boolean invitationUsed) {
        return new TwoIDialogueScreen(-1, true, hand,
                giftScore >= 100 || invitationUsed,
                invitationUsed, giftScore);
    }

    @Override
    protected void init() {
        panelWidth = Math.min(430, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        int actionX = panelX + 145;
        int actionWidth = panelWidth - 157;
        int gap = 6;
        int buttonWidth = (actionWidth - gap) / 2;
        int firstY = panelY + 167;

        int libraryWidth = Math.min(86, (panelWidth - 95) / 2);
        addAction(panelX + panelWidth - 2 * libraryWidth - 16, panelY + 5, libraryWidth,
                "library", button -> minecraft.setScreen(new TwoIStoryLibraryScreen(this, bond)))
                .setHeight(18);
        addAction(panelX + panelWidth - libraryWidth - 10, panelY + 5, libraryWidth,
                "gallery", button -> minecraft.setScreen(new TwoIStoryGalleryScreen(this)))
                .setHeight(18);

        if (itemMode) {
            addRenderableWidget(Button.builder(Component.translatable("gui.useless_stretcher.two_i.deploy"), button -> {
                Network.sendTwoIDollDeploy(itemHand);
                onClose();
            }).bounds(actionX, firstY, Math.min(actionWidth, 132), 20).build());
            return;
        }

        talkButton = addAction(actionX, firstY, buttonWidth, "talk", button -> startStory());
        patButton = addAction(actionX + buttonWidth + gap, firstY, buttonWidth, "pat",
                button -> Network.sendTwoIDollPat(entityId));
        giftButton = addAction(actionX, firstY + 26, buttonWidth, "gift",
                button -> Network.sendTwoIDollGift(entityId));
        inviteButton = addAction(actionX + buttonWidth + gap, firstY + 26, buttonWidth,
                invitationUsed || inviteReady && bond < 100 ? "revisit" : "invite", button -> {
            Network.sendTwoIDollInvite(entityId);
            onClose();
        });
        inviteButton.active = inviteReady && com.sorrowmist.useless.stretcher.config.StretcherConfig.starfieldInvitationEnabled();
        reclaimButton = addAction(actionX, firstY + 52, buttonWidth, "reclaim", button -> {
            Network.sendTwoIDollReclaim(entityId);
            onClose();
        });

        addAction(actionX + buttonWidth + gap, firstY + 52, buttonWidth, "smalltalk",
                button -> Network.sendTwoIDollTalk(entityId));
        talkButton.active = startWait == 0;
    }

    private Button addAction(int x, int y, int buttonWidth, String action,
                             Button.OnPress onPress) {
        return addRenderableWidget(Button.builder(Component.translatable(
                        "gui.useless_stretcher.two_i." + action), onPress)
                .bounds(x, y, buttonWidth, 22).build());
    }

    private void startStory() {
        if (itemMode || startWait > 0) return;
        startWait = 100;
        talkButton.active = false;
        Network.sendTwoIDollStoryStart(entityId);
    }

    public void openStory(long sessionId, int chapter, int bond) {
        openStory(sessionId, chapter, bond, 0);
    }

    public void openStory(long sessionId, int chapter, int bond, int round) {
        if (startWait == 0 || minecraft == null) return;
        onStoryUnavailable();
        this.bond = Math.max(-100, Math.min(100, bond));
        minecraft.setScreen(new TwoIStoryScreen(this, entityId, sessionId, chapter, bond, round));
    }

    public void onStoryUnavailable() {
        startWait = 0;
        if (talkButton != null) talkButton.active = true;
    }

    public void updateStoryBond(int bond) { this.bond = Math.max(-100, Math.min(100, bond)); }

    public int entityId() {
        return entityId;
    }

    @Override public int interactionEntityId() { return itemMode ? -1 : entityId; }

    @Override
    public void tick() {
        super.tick();
        if (startWait > 0 && --startWait == 0) onStoryUnavailable();
        if (inviteButton != null) inviteButton.active = inviteReady
                && com.sorrowmist.useless.stretcher.config.StretcherConfig.starfieldInvitationEnabled();
    }

    public void updateGiftStatus(boolean inviteReady, int bond) {
        this.inviteReady = inviteReady;
        this.bond = Math.max(-100, Math.min(100, bond));
        if (inviteButton != null) inviteButton.active = inviteReady
                && com.sorrowmist.useless.stretcher.config.StretcherConfig.starfieldInvitationEnabled();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // Leave the game world visible around the character panel.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        panelWidth = Math.min(430, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        // Match the rest of the mod's AE-inspired screens instead of using an opaque black box.
        graphics.fill(0, 0, width, height, 0x28000000);
        StretcherScreenStyle.drawPanel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
        graphics.fill(panelX + 5, panelY + 4, panelX + panelWidth - 5, panelY + 25,
                StretcherScreenStyle.SLOT_COLOR);
        StretcherScreenStyle.drawInset(graphics, panelX + 10, panelY + 31,
                panelX + 137, panelY + PANEL_HEIGHT - 10);
        graphics.fill(panelX + 145, panelY + 31, panelX + panelWidth - 10, panelY + 32,
                StretcherScreenStyle.SLOT_SHADOW_COLOR);

        graphics.drawString(font, Component.translatable("gui.useless_stretcher.two_i.title"),
                panelX + 10, panelY + 9, StretcherScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.two_i.interaction"),
                panelX + 145, panelY + 40, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);

        LivingEntity doll = previewEntity();
        LivingEntity status = statusEntity();
        if (doll != null) {
            Minecraft minecraft = Minecraft.getInstance();
            // Vanilla's inventory helper maps the current cursor position to yaw
            // and pitch.  Render a client-only doll copy so network updates cannot
            // overwrite that orbit while the mouse is moving.
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    panelX + 74, panelY + PANEL_HEIGHT - 18, 60,
                    panelX + 74 - mouseX, panelY + PANEL_HEIGHT - 68 - mouseY, doll);
            if (doll instanceof TwoIDollEntity twoI && !twoI.getBubbleText().isEmpty()) {
                Component bubble = twoI.getBubbleMessage();
                int bubbleWidth = Math.min(118, font.width(bubble) + 12);
                int bubbleHeight = font.split(bubble, bubbleWidth - 12).size() * font.lineHeight + 10;
                int bubbleX = panelX + 18;
                int bubbleY = panelY + 38;
                graphics.fill(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleY + bubbleHeight, 0xEDEFF7FF);
                graphics.fill(bubbleX + 5, bubbleY + bubbleHeight, bubbleX + 10, bubbleY + bubbleHeight + 4, 0xEDEFF7FF);
                graphics.drawWordWrap(font, bubble, bubbleX + 6, bubbleY + 5, bubbleWidth - 12,
                        0xFF242631);
            }
            renderEntityStatus(graphics, status == null ? doll : status,
                    panelX + 13, panelY + PANEL_HEIGHT - 42, 121);
        }

        int textX = panelX + 145;
        int textWidth = panelWidth - 157;
        int bondColor = bond < 0 ? 0xFF9B3F55 : bond > 0 ? StretcherScreenStyle.SUCCESS_COLOR
                : StretcherScreenStyle.SUBTLE_TEXT_COLOR;
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.two_i.bond", bond),
                textX, panelY + 55, bondColor, false);
        // A small, readable bond meter gives immediate feedback without adding another widget.
        int meterLeft = textX;
        int meterRight = panelX + panelWidth - 10;
        int meterTop = panelY + 68;
        graphics.fill(meterLeft, meterTop, meterRight, meterTop + 4, StretcherScreenStyle.SLOT_SHADOW_COLOR);
        int meterWidth = Math.max(0, meterRight - meterLeft - 2);
        int fill = Mth.clamp((bond + 100) * meterWidth / 200, 0, meterWidth);
        if (fill > 0) graphics.fill(meterLeft + 1, meterTop + 1, meterLeft + 1 + fill,
                meterTop + 3, bond < 0 ? 0xFF9B3F55 : StretcherScreenStyle.ACTIVE_COLOR);
        Component dialogue = inviteReady
                ? Component.translatable("gui.useless_stretcher.two_i.invite_hint")
                : itemMode
                    ? Component.translatable("gui.useless_stretcher.two_i.item_dialogue")
                    : bondDialogue();
        graphics.drawWordWrap(font, dialogue, textX, panelY + 80, textWidth,
                StretcherScreenStyle.TEXT_COLOR);
        if (invitationUsed) {
            graphics.drawWordWrap(font, Component.translatable("gui.useless_stretcher.two_i.invitation_used"),
                    textX, panelY + 116, textWidth, StretcherScreenStyle.MUTED_TEXT_COLOR);
        }
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.two_i.relationship",
                        Component.translatable("gui.useless_stretcher.two_i.relationship." + relationship())),
                textX, panelY + 148, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= textX && mouseX < textX + textWidth && mouseY >= panelY + 145 && mouseY < panelY + 161) {
            graphics.renderTooltip(font, font.split(Component.translatable("help.useless_stretcher.two_i.relationship"),
                    240), mouseX, mouseY);
        }
    }

    private String relationship() {
        if (bond <= -50) return "hurt";
        if (bond < 0) return "wary";
        if (bond >= 70) return "close";
        if (bond >= 40) return "trusted";
        if (bond >= 20) return "friend";
        return "acquaintance";
    }

    private Component bondDialogue() {
        if (bond <= -50) return Component.translatable("gui.useless_stretcher.two_i.dialogue.low");
        if (bond < 0) return Component.translatable("gui.useless_stretcher.two_i.dialogue.uneasy");
        if (bond >= 70) return Component.translatable("gui.useless_stretcher.two_i.dialogue.close");
        if (bond >= 25) return Component.translatable("gui.useless_stretcher.two_i.dialogue.warm");
        return Component.translatable("gui.useless_stretcher.two_i.dialogue");
    }

    private void renderEntityStatus(GuiGraphics graphics, LivingEntity doll, int x, int y, int width) {
        graphics.fill(x - 2, y - 2, x + width + 2, y + 29, 0xB8101014);
        Component action = Component.translatable("gui.useless_stretcher.two_i.status.action",
                Component.translatable("gui.useless_stretcher.two_i.status.action."
                        + actionKey(doll instanceof TwoIDollEntity entity ? entity.getPoseAction() : TwoIDollEntity.ACTION_NONE)));
        Component environment = Component.translatable("gui.useless_stretcher.two_i.status.environment",
                Component.translatable("gui.useless_stretcher.two_i.status.environment."
                        + environmentKey(doll instanceof TwoIDollEntity entity ? entity.getEnvironmentAction() : 0)));
        Component umbrella = Component.translatable("gui.useless_stretcher.two_i.status.umbrella",
                doll instanceof TwoIDollEntity entity && entity.hasSharedUmbrella()
                        ? Component.translatable("gui.useless_stretcher.two_i.status.shared")
                        : Component.translatable("gui.useless_stretcher.two_i.status.single"));
        graphics.drawString(font, action, x, y, 0xFFE5E5E5, false);
        graphics.drawString(font, environment, x, y + 10, 0xFFB9C0CC, false);
        graphics.drawString(font, umbrella, x, y + 20, 0xFFB9C0CC, false);
    }

    private static String actionKey(int action) {
        return switch (action) {
            case TwoIDollEntity.ACTION_GREETING -> "greeting";
            case TwoIDollEntity.ACTION_CURIOUS -> "curious";
            case TwoIDollEntity.ACTION_PATTED -> "patted";
            case TwoIDollEntity.ACTION_RECEIVE_GIFT -> "gift";
            case TwoIDollEntity.ACTION_INVITE -> "invite";
            case TwoIDollEntity.ACTION_SWING_FEET -> "swing_feet";
            case TwoIDollEntity.ACTION_UMBRELLA -> "umbrella";
            case TwoIDollEntity.ACTION_SNEEZE -> "sneeze";
            case TwoIDollEntity.ACTION_SHIVER -> "shiver";
            case TwoIDollEntity.ACTION_STRETCH -> "stretch";
            case TwoIDollEntity.ACTION_DUST -> "dust";
            case TwoIDollEntity.ACTION_SHY -> "shy";
            default -> "idle";
        };
    }

    private static String environmentKey(int environment) {
        if ((environment & com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment.THUNDER) != 0)
            return "thunder";
        if ((environment & com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment.COLD) != 0)
            return "cold";
        if ((environment & com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment.RAIN) != 0)
            return "rain";
        return "clear";
    }

    private LivingEntity previewEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        if (previewLevel != minecraft.level) {
            previewLevel = minecraft.level;
            preview = null;
        }
        if (preview == null) {
            preview = ModEntities.TWO_I_DOLL.get().create(minecraft.level);
            if (preview != null) {
                preview.setNoAi(true);
                preview.setNoGravity(true);
                preview.noPhysics = true;
                preview.setPos(0.0D, 0.0D, 0.0D);
                preview.setYRot(180.0F);
                preview.yRotO = 180.0F;
                preview.yBodyRot = 180.0F;
                preview.yBodyRotO = 180.0F;
            }
        }
        if (preview == null) return null;
        if (!itemMode && minecraft.level.getEntity(entityId) instanceof TwoIDollEntity entity) {
            preview.restoreFromItem(entity.toDollItem());
            preview.copyClientPreviewStateFrom(entity);
        } else if (itemMode) {
            ItemStack held = minecraft.player == null ? ItemStack.EMPTY
                    : minecraft.player.getItemInHand(itemHand);
            if (held.is(ModItems.TWO_I_DOLL.get())) preview.restoreFromItem(held);
        }
        return preview;
    }

    private LivingEntity statusEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!itemMode && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof TwoIDollEntity entity) return entity;
        return preview;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
