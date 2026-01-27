package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import java.awt.Color;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
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

public class TempMuteCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> durationArg;
    private final RequiredArg<String> reasonArg;

    public TempMuteCommand(ModerationPlus plugin) {
        super("tempmute", "Mute a player temporarily");
        this.plugin = plugin;
        this.requirePermission("moderation.tempmute");
        this.playerArg = withRequiredArg("player", "Player to mute", (ArgumentType<String>) ArgTypes.STRING);
        this.durationArg = withRequiredArg("duration", "Duration (e.g. 5m, 1h)",
                (ArgumentType<String>) ArgTypes.STRING);
        this.reasonArg = withRequiredArg("reason", "Mute reason", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);
        String durationStr = ctx.get(durationArg);

        String fullInput = ctx.getInputString();
        String reason = "Muted by an operator.";
        int dIdx = fullInput.toLowerCase().indexOf(" " + durationStr.toLowerCase() + " ");
        if (dIdx != -1) {
            String sub = fullInput.substring(dIdx + durationStr.length() + 2).trim();
            if (!sub.isEmpty())
                reason = sub;
        } else {
            reason = ctx.get(reasonArg);
        }
        if (reason == null)
            reason = "Muted by an operator.";

        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";

        long duration;
        try {
            duration = TimeUtils.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.generic.invalid_duration",
                (sender instanceof Player) ? sender.getUuid() : null
            ));
            return CompletableFuture.completedFuture(null);
        }

        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.tempmute.player_not_found",
                (sender instanceof Player) ? sender.getUuid() : null,
                java.util.Map.of("player", targetName)
            ));
            return CompletableFuture.completedFuture(null);
        }

        me.almana.moderationplus.service.ExecutionContext context = new me.almana.moderationplus.service.ExecutionContext(
                (sender instanceof Player) ? sender.getUuid() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                issuerName,
                me.almana.moderationplus.service.ExecutionContext.ExecutionSource.COMMAND);

        plugin.getModerationService().tempMute(targetUuid, targetName, reason, duration, context).thenAccept(success -> {
            UUID issuer = (sender instanceof Player) ? sender.getUuid() : null;
            if (success) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.tempmute.success",
                    issuer,
                    java.util.Map.of("player", targetName, "duration", me.almana.moderationplus.utils.TimeUtils.formatDuration(duration))
                ));
            } else {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.tempmute.failed",
                    issuer,
                    java.util.Map.of("player", targetName)
                ));
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
