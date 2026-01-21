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
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.Executor;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class KickCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> reasonArg;

    public KickCommand(ModerationPlus plugin) {
        super("kick", "Kick a player from the server");
        this.plugin = plugin;
        this.requirePermission("moderation.kick");
        this.playerArg = withRequiredArg("player", "Player to kick", (ArgumentType<String>) ArgTypes.STRING);
        this.reasonArg = withRequiredArg("reason", "Kick reason", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        // Parse reason
        String fullInput = ctx.getInputString();
        String reason = "Kicked by an operator.";
        String cmdPrefix = "kick " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            reason = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {
            reason = ctx.get(reasonArg);
        }
        if (reason == null || reason.isEmpty())
            reason = "Kicked by an operator.";

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";

        // Check online
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Player '" + targetName + "' not found in database.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("Player '" + targetName + "' is not online.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        if (sender instanceof Player && ref.getUuid().equals(sender.getUuid())) {
            ctx.sendMessage(Message.raw("You cannot kick yourself.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = ref.getUsername();

        // Check bypass
        if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                "moderation.bypass")) {
            ctx.sendMessage(Message.raw(resolvedName + " cannot be punished.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        try {
            // Record punishment
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);
            // Kick inactive
            Punishment kick = new Punishment(0, playerData.id(), "KICK", issuerUuid, reason, System.currentTimeMillis(),
                    0, false, "{}");
            plugin.getStorageManager().createPunishment(kick);

            // Notify staff
            String staffMsg = "[Staff] " + sender.getDisplayName() + " kicked " + resolvedName + " (" + reason + ")";
            plugin.notifyStaff(Message.raw(staffMsg).color(Color.GREEN));

            // Execute kick

            if (ref != null && ref.isValid()) {
                final String finalReason = reason;
                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                ref.getPacketHandler().disconnect("You have been kicked.\nReason: " + finalReason);
                            }
                        });
                    }
                }
            }

            ctx.sendMessage(Message.raw("Kicked " + resolvedName).color(Color.GREEN));

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing kick: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
