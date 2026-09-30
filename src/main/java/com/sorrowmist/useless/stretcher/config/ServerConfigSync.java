package com.sorrowmist.useless.stretcher.config;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Login uses NeoForge's SERVER sync; this payload also updates clients already playing. */
public final class ServerConfigSync {
    public static final String FILE_NAME = UselessStretcherMod.MODID + "-server.toml";
    private static final int MAX_BYTES = 262144;

    private ServerConfigSync() {}

    public static ModConfig config() {
        return ModConfigs.getFileMap().get(FILE_NAME);
    }

    /** Preserve legacy common settings without overwriting an existing server config or template. */
    public static void migrateLegacy(Path configDirectory, Path defaultsDirectory) {
        Path legacy = configDirectory.resolve(UselessStretcherMod.MODID + "-common.toml");
        Path target = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(legacy) || Files.exists(target)
                || Files.exists(defaultsDirectory.resolve(FILE_NAME))) return;
        try {
            var migrated = new TomlParser().parse(Files.readString(legacy));
            StretcherConfig.SERVER_SPEC.correct(migrated);
            Files.writeString(target, new TomlWriter().writeToString(migrated),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            LogUtils.getLogger().info("Migrated legacy Stretcher config to {}; original retained", target);
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Could not migrate legacy Stretcher config {}; original retained", legacy, exception);
        }
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != StretcherConfig.SERVER_SPEC) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return; // A remote client must never send configuration back to the server.
        server.execute(() -> {
            if (!StretcherConfig.SERVER_SPEC.isLoaded() || server.getPlayerList() == null) return;
            var payload = snapshot();
            if (payload != null) PacketDistributor.sendToAllPlayers(payload);
        });
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var payload = snapshot();
            if (payload != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static Payload snapshot() {
        ModConfig config = config();
        if (config == null || config.getLoadedConfig() == null) return null;
        byte[] contents = new TomlWriter().writeToString(config.getLoadedConfig().config())
                .getBytes(StandardCharsets.UTF_8);
        if (contents.length > MAX_BYTES) {
            LogUtils.getLogger().warn("Stretcher server config exceeds live sync limit ({} bytes)", contents.length);
            return null;
        }
        return new Payload(contents);
    }

    public record Payload(byte[] contents) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "server_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeByteArray(payload.contents()),
                buf -> new Payload(buf.readByteArray(MAX_BYTES)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
