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
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MuteCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    // Greedy parsing

    public MuteCommand(ModerationPlus plugin) {
        super("mute", "Mute a player permanently");
        this.plugin = plugin;
        this.requirePermission("moderation.mute");
        this.playerArg = withRequiredArg("player", "Player to mute", (ArgumentType<String>) ArgTypes.STRING);
        // Greedy reason
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        // Parse reason
        String fullInput = ctx.getInputString();
        String reason = "Muted by an operator.";
        String cmdPrefix = "mute " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            reason = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {
            reason = "Muted by an operator.";
        }
        if (reason == null || reason.isEmpty())
            reason = "Muted by an operator.";

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";

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

            // Create mute
            Punishment mute = new Punishment(0, playerData.id(), "MUTE", issuerUuid, reason, System.currentTimeMillis(),
                    0, true, "{}");
            plugin.getStorageManager().createPunishment(mute);

            // Notify staff
            String staffMsg = "[Staff] " + sender.getDisplayName() + " muted " + resolvedName + " (" + reason + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.GREEN));

            // Notify target
            if (ref != null && ref.isValid()) {
                ref.sendMessage(Message.raw("You have been permanently muted.\nReason: " + reason).color(Color.RED));
            }

            ctx.sendMessage(Message.raw("Muted " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing mute: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
