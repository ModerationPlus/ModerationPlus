package me.almana.moderationplus.commands;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import com.hypixel.hytale.server.core.universe.world.World;

public class JailCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public JailCommand(ModerationPlus plugin) {
        super("jail", "Jail a player");
        this.plugin = plugin;
        this.requirePermission("moderation.jail");
        this.playerArg = withRequiredArg("player", "Player to jail", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        if (!plugin.getConfigManager().hasJailLocation()) {
            ctx.sendMessage(Message.raw("Jail location is not set. Use /setjail to configure.").color(Color.RED));
            if (sender.hasPermission("moderation.setjail")) {
                ctx.sendMessage(Message.raw("Run /setjail to set the jail location.").color(Color.YELLOW));
            }
            return CompletableFuture.completedFuture(null);
        }

        String fullInput = ctx.getInputString();
        String cmdPrefix = "jail " + targetName;
        String argsStr = "";
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            argsStr = fullInput.substring(idx + cmdPrefix.length()).trim();
        }

        long durationMillis = 0;
        String reason = "No reason specified";

        if (!argsStr.isEmpty()) {
            String[] tokens = argsStr.trim().split("\\s+");
            String firstToken = tokens[0];
            String lastToken = tokens[tokens.length - 1];
            boolean parsed = false;

            // Try first token as duration
            if (firstToken.matches("^[0-9]+.*")) {
                try {
                    durationMillis = me.almana.moderationplus.utils.TimeUtils.parseDuration(firstToken);
                    if (durationMillis <= 0) {
                        ctx.sendMessage(Message.raw("Duration must be positive.").color(Color.RED));
                        return CompletableFuture.completedFuture(null);
                    }
                    String candidateReason = argsStr.substring(firstToken.length()).trim();
                    if (!candidateReason.isEmpty()) reason = candidateReason;
                    parsed = true;
                } catch (IllegalArgumentException e) {
                     ctx.sendMessage(Message.raw("Invalid duration format: " + firstToken + " (Example: 10m, 2h, 1d)").color(Color.RED));
                     return CompletableFuture.completedFuture(null);
                }
            } 
            
            // If not first, try last token
            if (!parsed && tokens.length > 1 && lastToken.matches("^[0-9]+.*")) {
                try {
                    long duration = me.almana.moderationplus.utils.TimeUtils.parseDuration(lastToken);
                    if (duration > 0) {
                        durationMillis = duration;
                        String candidateReason = argsStr.substring(0, argsStr.length() - lastToken.length()).trim();
                        if (!candidateReason.isEmpty()) reason = candidateReason;
                        parsed = true;
                    }
                } catch (IllegalArgumentException e) {
                     // If last token looks like duration but fails, notify user to avoid confusion
                     ctx.sendMessage(Message.raw("Invalid duration format: " + lastToken + " (Example: 10m, 2h, 1d)").color(Color.RED));
                     return CompletableFuture.completedFuture(null);
                }
            }

            if (!parsed) {
                reason = argsStr;
            }
        }

        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        me.almana.moderationplus.storage.StorageManager.PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, targetName);
        try {
            List<Punishment> activeJails = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "JAIL");
            if (!activeJails.isEmpty()) {
                ctx.sendMessage(Message.raw("Player is already jailed.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }
        } catch (Exception e) {
             e.printStackTrace();
        }

        me.almana.moderationplus.service.ExecutionContext context = new me.almana.moderationplus.service.ExecutionContext(
                (sender instanceof Player) ? sender.getUuid() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                issuerName,
                me.almana.moderationplus.service.ExecutionContext.ExecutionSource.COMMAND);

        me.almana.moderationplus.api.event.staff.StaffJailEvent event = new me.almana.moderationplus.api.event.staff.StaffJailEvent(
                context.issuerUuid(), targetUuid, context.source().name(), durationMillis, reason
        );

        final long finalDuration = durationMillis;
        final String finalReason = reason;

        return CompletableFuture.runAsync(() -> plugin.getEventBus().dispatch(event), com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR)
                .thenCompose(v -> {
                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return plugin.getModerationService().jail(targetUuid, targetName, finalDuration, finalReason, context).thenAccept(success -> {
                        if (success) {
                            // Success message handled by service
                        } else {
                            ctx.sendMessage(Message.raw("Failed to jail " + targetName + ".").color(Color.RED));
                        }
                    });
                });
    }
}
