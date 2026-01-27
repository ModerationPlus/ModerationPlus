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
import java.util.concurrent.CompletableFuture;

public class WarnCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> reasonArg;

    public WarnCommand(ModerationPlus plugin) {
        super("warn", "Warn a player");
        this.plugin = plugin;
        this.requirePermission("moderation.warn");
        this.playerArg = withRequiredArg("player", "Player to warn", (ArgumentType<String>) ArgTypes.STRING);
        this.reasonArg = withRequiredArg("reason", "Warning reason", (ArgumentType<String>) ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        String fullInput = ctx.getInputString();
        String reason = "Warned by an operator.";
        String cmdPrefix = "warn " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            reason = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {
            reason = ctx.get(reasonArg);
        }
        if (reason == null || reason.isEmpty())
            reason = "Warned by an operator.";

        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            String msg = plugin.getLanguageManager().translate(
                "command.warn.player_not_found",
                plugin.getLanguageManager().getDefaultLocale(),
                java.util.Map.of("player", targetName)
            );
            ctx.sendMessage(Message.raw(msg).color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        me.almana.moderationplus.service.ExecutionContext context = new me.almana.moderationplus.service.ExecutionContext(
                (sender instanceof Player) ? sender.getUuid() : UUID.nameUUIDFromBytes("CONSOLE".getBytes()),
                issuerName,
                me.almana.moderationplus.service.ExecutionContext.ExecutionSource.COMMAND);

        plugin.getModerationService().warn(targetUuid, targetName, reason, context).thenAccept(success -> {
            String msg = plugin.getLanguageManager().translate(
                "command.warn.success",
                plugin.getLanguageManager().getDefaultLocale(),
                java.util.Map.of("player", targetName)
            );
            ctx.sendMessage(Message.raw(msg).color(Color.GREEN));
        });

        return CompletableFuture.completedFuture(null);
    }
}
