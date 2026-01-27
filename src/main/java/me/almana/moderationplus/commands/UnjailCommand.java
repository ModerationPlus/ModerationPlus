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
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UnjailCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public UnjailCommand(ModerationPlus plugin) {
        super("unjail", "Unjail a player");
        this.plugin = plugin;
        this.requirePermission("moderation.unjail");
        this.playerArg = withRequiredArg("player", "Player to unjail", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.unjail.player_not_found",
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

        me.almana.moderationplus.api.event.staff.StaffUnjailEvent event = new me.almana.moderationplus.api.event.staff.StaffUnjailEvent(
                context.issuerUuid(), targetUuid, context.source().name()
        );

        return CompletableFuture.runAsync(() -> plugin.getEventBus().dispatch(event), com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR)
                .thenCompose(v -> {
                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return plugin.getModerationService().unjail(targetUuid, targetName, context).thenAccept(success -> {
                        if (success) {
                            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                                "command.unjail.success",
                                (sender instanceof Player) ? sender.getUuid() : null,
                                java.util.Map.of("player", targetName)
                            ));
                        } else {
                            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                                "command.unjail.failed",
                                (sender instanceof Player) ? sender.getUuid() : null,
                                java.util.Map.of("player", targetName)
                            ));
                        }
                    });
                });
    }
}
