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
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.InfiniteBan;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.Executor;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BanCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public BanCommand(ModerationPlus plugin) {
        super("ban", "Ban a player permanently");
        this.plugin = plugin;
        this.requirePermission("moderation.ban");
        this.playerArg = withRequiredArg("player", "Player to ban", (ArgumentType<String>) ArgTypes.STRING);

        setAllowsExtraArguments(true);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        String fullInput = ctx.getInputString();
        String reason = "Banned by an operator.";
        String cmdPrefix = "ban " + targetName;
        int idx = fullInput.toLowerCase().indexOf(cmdPrefix.toLowerCase());
        if (idx != -1 && fullInput.length() > idx + cmdPrefix.length()) {
            reason = fullInput.substring(idx + cmdPrefix.length()).trim();
        } else {
            reason = "Banned by an operator.";
        }
        if (reason == null || reason.isEmpty())
            reason = "Banned by an operator.";

        String issuerUuid = (sender instanceof Player) ? sender.getUuid().toString() : "CONSOLE";
        String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);

        if (targetUuid == null) {
            String msg = plugin.getLanguageManager().translate(
                "command.ban.player_not_found",
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

        plugin.getModerationService().ban(targetUuid, targetName, reason, context).thenAccept(success -> {
            String locale = plugin.getLanguageManager().getDefaultLocale();
            if (success) {
                String msg = plugin.getLanguageManager().translate(
                    "command.ban.success",
                    locale,
                    java.util.Map.of("player", targetName)
                );
                ctx.sendMessage(Message.raw(msg).color(Color.GREEN));
            } else {
                String msg = plugin.getLanguageManager().translate(
                    "command.ban.failed",
                    locale,
                    java.util.Map.of("player", targetName)
                );
                ctx.sendMessage(Message.raw(msg).color(Color.RED));
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
