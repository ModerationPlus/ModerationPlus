package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import me.almana.moderationplus.ModerationPlus;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VanishCommand extends AbstractCommand {

    private final ModerationPlus plugin;

    public VanishCommand(ModerationPlus plugin) {
        super("staffvanish", "Toggle vanish mode");
        this.plugin = plugin;
        this.requirePermission("moderation.vanish");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();


        if (!(sender instanceof Player)) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage("command.generic.player_only", null));
            return CompletableFuture.completedFuture(null);
        }

        UUID playerUuid = sender.getUuid();
        String playerName = sender.getDisplayName();
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);

        if (playerRef == null || !playerRef.isValid()) {
            ctx.sendMessage(plugin.getLanguageManager().translateToMessage("command.vanish.ref_failed", playerUuid));
            return CompletableFuture.completedFuture(null);
        }

        me.almana.moderationplus.service.ExecutionContext context = new me.almana.moderationplus.service.ExecutionContext(
                playerUuid,
                playerName,
                me.almana.moderationplus.service.ExecutionContext.ExecutionSource.COMMAND);

        plugin.getModerationService().toggleVanish(playerUuid, playerName, context)
                .thenAccept(vanished -> {
                    if (vanished) {
                        ctx.sendMessage(plugin.getLanguageManager().translateToMessage("command.vanish.toggle_on", playerUuid));
                    } else {
                        ctx.sendMessage(plugin.getLanguageManager().translateToMessage("command.vanish.toggle_off", playerUuid));
                    }
                });

        return CompletableFuture.completedFuture(null);
    }
}
