package me.almana.moderationplus.commands;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.NameMatching;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StaffNote;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class NotesCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public NotesCommand(ModerationPlus plugin) {
        super("notes", "View staff notes for a player");
        this.plugin = plugin;
        this.requirePermission("moderation.notes");
        this.playerArg = withRequiredArg("player", "Player name", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {

        String targetName = ctx.get(playerArg);


        UUID targetUuid = null;
        String resolvedName = targetName;

        PlayerRef ref = Universe.get().getPlayer(targetName, NameMatching.EXACT);
        if (ref != null) {
            targetUuid = ref.getUuid();
            resolvedName = ref.getUsername();
        } else {
            targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        }

        if (targetUuid == null) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.notes.player_not_found",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("player", targetName)
            ));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getPlayerByUUID(targetUuid);
            if (playerData == null) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.notes.none",
                    (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                    java.util.Map.of("player", resolvedName)
                ));
                return CompletableFuture.completedFuture(null);
            }

            List<StaffNote> notes = plugin.getStorageManager().getStaffNotes(playerData.id());
            if (notes.isEmpty()) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.notes.none",
                    (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                    java.util.Map.of("player", resolvedName)
                ));
                return CompletableFuture.completedFuture(null);
            }


            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.notes.header",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("player", resolvedName)
            ));


            int index = 1;
            for (StaffNote note : notes) {
                String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(note.createdAt()));
                String issuerName = "Console";
                if (!note.issuerUuid().equals("CONSOLE")) {
                    try {
                        UUID issuerId = UUID.fromString(note.issuerUuid());
                        me.almana.moderationplus.storage.StorageManager.PlayerData issuerData = plugin
                                .getStorageManager().getPlayerByUUID(issuerId);
                        if (issuerData != null) {
                            issuerName = issuerData.username();
                        } else {

                            PlayerRef issuerRef = Universe.get().getPlayer(issuerId);
                            if (issuerRef != null)
                                issuerName = issuerRef.getUsername();
                        }
                    } catch (Exception ignored) {
                    }
                }

                String line = String.format("&f%d) (%s) %s — by %s",
                        index, timestamp, note.message(), issuerName);
                ctx.sendMessage(me.almana.moderationplus.utils.ColorUtils.parse(line));

                index++;
            }

        } catch (Exception e) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.notes.failed",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown")
            ));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
