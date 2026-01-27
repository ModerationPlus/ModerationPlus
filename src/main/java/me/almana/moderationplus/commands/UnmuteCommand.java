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
import com.hypixel.hytale.server.core.entity.entities.Player;
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
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.unmute.player_not_found",
                (sender instanceof Player) ? sender.getUuid() : null,
                java.util.Map.of("player", targetName)
            ));
            return CompletableFuture.completedFuture(null);
        }

        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
        me.almana.moderationplus.service.ExecutionContext context = new me.almana.moderationplus.service.ExecutionContext(
                (sender instanceof Player) ? sender.getUuid() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                issuerName,
                me.almana.moderationplus.service.ExecutionContext.ExecutionSource.COMMAND);

        plugin.getModerationService().unmute(targetUuid, targetName, context).thenAccept(success -> {
            UUID issuer = (sender instanceof Player) ? sender.getUuid() : null;
            if (success) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.unmute.success",
                    issuer,
                    java.util.Map.of("player", targetName)
                ));
            } else {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.unmute.failed",
                    issuer,
                    java.util.Map.of("player", targetName)
                ));
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
