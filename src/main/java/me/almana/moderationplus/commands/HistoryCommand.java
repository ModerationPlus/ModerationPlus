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

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import me.almana.moderationplus.utils.TimeUtils;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class HistoryCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public HistoryCommand(ModerationPlus plugin) {
        super("history", "View player punishment history");
        this.plugin = plugin;
        this.requirePermission("moderation.history");
        this.playerArg = withRequiredArg("player", "Player name", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {

        String targetName = ctx.get(playerArg);



        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.history.player_not_found",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("player", targetName)
            ));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getPlayerByUUID(targetUuid);
            if (playerData == null) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.history.none",
                    (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                    java.util.Map.of("player", resolvedName)
                ));
                return CompletableFuture.completedFuture(null);
            }

            List<Punishment> punishments = plugin.getStorageManager().getPunishmentsForPlayer(playerData.id());
            if (punishments.isEmpty()) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.history.none",
                    (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                    java.util.Map.of("player", resolvedName)
                ));
                return CompletableFuture.completedFuture(null);
            }


            punishments.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));


            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.history.header",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("player", resolvedName)
            ));


            int index = 1;
            for (Punishment p : punishments) {
                String details = formatDetails(p);
                String timestamp = DATE_FORMATTER.format(Instant.ofEpochMilli(p.createdAt()));
                String line = String.format("&f%d) %s — %s (%s) (%s)",
                        index, p.type(), p.reason(), details, timestamp);
                ctx.sendMessage(me.almana.moderationplus.utils.ColorUtils.parse(line));
                index++;
            }

        } catch (Exception e) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.history.failed",
                (ctx.sender() instanceof Player) ? ctx.sender().getUuid() : null,
                java.util.Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown")
            ));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }

    private String formatDetails(Punishment p) {

        if (p.expiresAt() == 0) {
            return "Permanent";
        }


        long duration = p.expiresAt() - p.createdAt();
        return TimeUtils.formatDuration(duration);
    }
}
