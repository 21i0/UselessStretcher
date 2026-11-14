package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Administrator command adapter; all inspection and recovery policy stays in the manager. */
@EventBusSubscriber(modid = "useless_stretcher")
public final class CapsuleGravityRecoveryCommands {
    private static final int MAX_REPLY_CHARS = 4096;
    private static final int MAX_FIELD_CHARS = 192;
    private static final String SUMMARY_FOOTER = "\nBounded summary; nested/other fields omitted. Truncated values are marked.";

    private CapsuleGravityRecoveryCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("capsulegravity")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("room", StringArgumentType.string())
                                .executes(context -> inspect(context.getSource(), StringArgumentType.getString(context, "room")))))
                .then(Commands.literal("resume-preflight")
                        .then(Commands.argument("room", StringArgumentType.string())
                                .then(Commands.argument("pause", StringArgumentType.word())
                                        .executes(context -> resume(context.getSource(),
                                                StringArgumentType.getString(context, "room"),
                                                StringArgumentType.getString(context, "pause")))))));
    }

    private static int inspect(CommandSourceStack source, String room) {
        try {
            var report = CapsuleGravityRecovery.inspect(source.getServer(), room);
            String summary = summarize(report);
            source.sendSuccess(() -> Component.literal(summary), false);
            return 1;
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Capsule gravity inspection failed for room {}", room, failure);
            source.sendFailure(Component.literal("Capsule gravity inspection failed; see the server log."));
            return 0;
        }
    }

    private static int resume(CommandSourceStack source, String room, String pause) {
        try {
            String result = CapsuleGravityRecovery.resumePreflight(source.getServer(), room, pause);
            if ("resumed".equals(result)) {
                String message = "Capsule preflight recovery queued; execution checks pending: " + shortValue(room);
                source.sendSuccess(() -> Component.literal(message), false);
                return 1;
            }
            source.sendFailure(Component.literal("Capsule preflight recovery refused: " + shortValue(result)));
            return 0;
        } catch (RuntimeException failure) {
            LogUtils.getLogger().error("Capsule gravity recovery failed for room {} pause {}", room, pause, failure);
            source.sendFailure(Component.literal("Capsule preflight recovery failed; see the server log."));
            return 0;
        }
    }

    /** Fixed field traversal: never materializes SNBT, unknown payloads, lists, or nested state. */
    private static String summarize(CompoundTag report) {
        StringBuilder output = new StringBuilder(MAX_REPLY_CHARS);
        for (String key : new String[]{"key", "journal_read_only", "journal_error", "pause_status", "room_error",
                "phase", "chunk", "piece", "ready", "generating", "same_epoch_authority", "runtime_busy", "legacy_journal_unresolved", "journal_mutation_error"})
            appendField(output, key, report.get(key));
        if (report.get("pause") instanceof CompoundTag pause) {
            for (String key : new String[]{"pause", "status", "category", "reason", "proven_before", "fingerprint_complete",
                    "epoch", "callback", "failure_dimension", "failure_x", "failure_z", "format"})
                appendField(output, "pause." + key, pause.get(key));
            if (pause.get("cleanup") instanceof CompoundTag cleanup) {
                appendField(output, "cleanup.dimension", cleanup.get("dimension"));
                appendField(output, "cleanup.complete", cleanup.get("complete"));
                appendField(output, "cleanup.planner", cleanup.get("planner"));
                var tickets = cleanup.getList("tickets", Tag.TAG_COMPOUND);
                appendField(output, "cleanup.ticket_count", net.minecraft.nbt.IntTag.valueOf(tickets.size()));
                for (int i = 0; i < Math.min(8, tickets.size()); i++) {
                    var ticket = tickets.getCompound(i);
                    for (String key : new String[]{"type", "x", "z", "radius", "released", "error"})
                        appendField(output, "ticket[" + i + "]." + key, ticket.get(key));
                }
            }
        } else appendField(output, "pause", report.get("pause"));
        output.append(SUMMARY_FOOTER);
        return output.toString();
    }

    private static void appendField(StringBuilder output, String key, Tag value) {
        int budget = MAX_REPLY_CHARS - SUMMARY_FOOTER.length();
        if (value == null || output.length() >= budget) return;
        String text;
        if (value instanceof StringTag string) text = shortValue(string.getAsString());
        else if (value instanceof NumericTag number) text = number.getAsNumber().toString();
        else text = "<omitted tag type " + value.getId() + ">";
        String line = (output.isEmpty() ? "" : "\n") + key + "=" + text;
        int remaining = budget - output.length();
        if (line.length() <= remaining) output.append(line);
        else if (remaining >= 12) output.append(line, 0, remaining - 12).append(" [truncated]");
    }

    private static String shortValue(String value) {
        if (value == null) return "<null>";
        StringBuilder output = new StringBuilder(MAX_FIELD_CHARS + 16);
        int end = Math.min(value.length(), MAX_FIELD_CHARS);
        if (end < value.length() && end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        for (int i = 0; i < end; i++) {
            char c = value.charAt(i);
            output.append(Character.isISOControl(c) ? ' ' : c);
        }
        if (end < value.length()) output.append(" [truncated]");
        return output.toString();
    }
}
