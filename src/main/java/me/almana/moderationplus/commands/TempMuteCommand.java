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

        // Parse reason
        String fullInput = ctx.getInputString();
        String reason = "Muted by an operator.";
        int dIdx = fullInput.toLowerCase().indexOf(" " + durationStr.toLowerCase() + " ");
        if (dIdx != -1) {
            // Parse substring
            String sub = fullInput.substring(dIdx + durationStr.length() + 2).trim();
            if (!sub.isEmpty())
                reason = sub;
        } else {
            reason = ctx.get(reasonArg);
        }
        if (reason == null)
            reason = "Muted by an operator.";

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";

        long duration;
        try {
            duration = TimeUtils.parseDuration(durationStr);
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("Invalid duration format. Use 5m, 1h, 1d, etc.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        long expiresAtMillis = System.currentTimeMillis() + duration;

        // Resolve UUID
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
        }

        // Check bypass
        if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                "moderation.bypass")) {
            ctx.sendMessage(Message.raw(resolvedName + " cannot be punished.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

            // Check active
            List<Punishment> activeMutes = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                    "MUTE");
            if (!activeMutes.isEmpty()) {
                ctx.sendMessage(Message.raw("Player is already muted.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            // Create punishment
            Punishment mute = new Punishment(0, playerData.id(), "MUTE", issuerUuid, reason, System.currentTimeMillis(),
                    expiresAtMillis, true, "{}");
            plugin.getStorageManager().createPunishment(mute);

            // Notify staff
            String staffMsg = "[Staff] " + sender.getDisplayName() + " temp-muted " + resolvedName + " for "
                    + durationStr + " (" + reason + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.GREEN));

            // Notify target
            if (ref != null && ref.isValid()) {
                ref.sendMessage(Message.raw("You have been muted for " + durationStr + ".\nReason: " + reason)
                        .color(Color.RED));
            }

            ctx.sendMessage(Message.raw("Temp-muted " + resolvedName + " for " + durationStr).color(Color.GREEN));
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing tempmute: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
