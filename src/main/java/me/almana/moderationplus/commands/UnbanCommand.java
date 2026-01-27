package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import java.awt.Color;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;

import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.entity.entities.Player;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unchecked")
public class UnbanCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public UnbanCommand(ModerationPlus plugin) {
        super("unban", "Unban a player");
        this.plugin = plugin;
        this.requirePermission("moderation.unban");
        this.playerArg = withRequiredArg("player", "Player to unban", (ArgumentType) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);

        UUID targetUuid = null;
        String resolvedName = targetName;

        PlayerRef ref = Universe.get().getPlayer(targetName, NameMatching.EXACT);
        if (ref != null) {
            targetUuid = ref.getUuid();
            resolvedName = ref.getUsername();
        } else {
            targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        }

        if (targetUuid == null) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                "command.unban.player_not_found",
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

        plugin.getModerationService().unban(targetUuid, resolvedName, context).thenAccept(success -> {
            UUID issuer = (sender instanceof Player) ? sender.getUuid() : null;
            if (success) {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.unban.success",
                    issuer,
                    java.util.Map.of("player", targetName)
                ));
            } else {
                ctx.sendMessage(plugin.getLanguageManager().translateToMessage(
                    "command.unban.failed",
                    issuer,
                    java.util.Map.of("player", targetName)
                ));
            }
        });

        return CompletableFuture.completedFuture(null);
    }
}
