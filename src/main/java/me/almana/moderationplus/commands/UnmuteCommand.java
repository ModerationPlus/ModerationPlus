package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.awt.Color;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UnmuteCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public UnmuteCommand(ModerationPlus plugin) {
        super("unmute", "Unmute a player");
        this.plugin = plugin;
        this.requirePermission("moderation.unmute");
        this.playerArg = withRequiredArg("player", "Player to unmute", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Player '" + targetName + "' not found.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid);
        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
        }

        try {
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);
            List<Punishment> activeMutes = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                    "MUTE");

            if (activeMutes.isEmpty()) {
                ctx.sendMessage(Message.raw("Player " + resolvedName + " is not muted.").color(Color.RED));
                return CompletableFuture.completedFuture(null);
            }

            for (Punishment p : activeMutes) {
                plugin.getStorageManager().deactivatePunishment(p.id());
            }

            // Send messages
            String staffMsg = "[Staff] " + sender.getDisplayName() + " unmuted " + resolvedName;
            Universe.get().getPlayers().forEach(p -> {
                if (p.getPacketHandler().getAuth() != null
                        && com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(p.getUuid(),
                                "moderation.notify")) {
                    p.sendMessage(Message.raw(staffMsg).color(Color.GREEN));
                }
            });
            com.hypixel.hytale.server.core.console.ConsoleSender.INSTANCE
                    .sendMessage(Message.raw(staffMsg).color(Color.GREEN));

            if (ref != null && ref.isValid()) {
                ref.sendMessage(Message.raw("You have been unmuted.").color(Color.GREEN));
            }

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing unmute: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
