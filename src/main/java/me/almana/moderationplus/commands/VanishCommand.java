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
        super("vanish", "Toggle vanish mode");
        this.plugin = plugin;
        this.requirePermission("moderation.vanish");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        // Players only
        if (!(sender instanceof Player)) {
            ctx.sendMessage(Message.raw("Only players can use this command.").color(java.awt.Color.RED));

            return CompletableFuture.completedFuture(null);
        }

        UUID playerUuid = sender.getUuid();
        String playerName = sender.getDisplayName();
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);

        if (playerRef == null || !playerRef.isValid()) {
            ctx.sendMessage(Message.raw("Failed to get your player reference.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        // Toggle vanish
        if (plugin.isVanished(playerUuid)) {
            // Unvanish
            plugin.removeVanishedPlayer(playerUuid);

            // Show player
            Universe.get().getPlayers().forEach(observer -> {
                if (observer != null && observer.isValid()) {
                    observer.getHiddenPlayersManager().showPlayer(playerUuid);
                }
            });

            ctx.sendMessage(Message.raw("You are no longer vanished.").color(java.awt.Color.GREEN));
            plugin.notifyStaff(
                    Message.raw("[Staff] " + playerName + " is no longer vanished.").color(java.awt.Color.GRAY));

        } else {
            // Vanish
            plugin.addVanishedPlayer(playerUuid);

            // Update visibility
            Universe.get().getPlayers().forEach(observer -> {
                if (observer != null && observer.isValid()) {
                    UUID observerUuid = observer.getUuid();

                    // Skip self
                    if (observerUuid.equals(playerUuid)) {
                        return;
                    }

                    // Check permission
                    boolean canSeeVanished = com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                            .hasPermission(observerUuid, "moderation.vanish.see");

                    if (canSeeVanished) {
                        observer.getHiddenPlayersManager().showPlayer(playerUuid);
                    } else {
                        observer.getHiddenPlayersManager().hidePlayer(playerUuid);
                    }
                }
            });

            ctx.sendMessage(Message.raw("You are now vanished.").color(java.awt.Color.GREEN));
            plugin.notifyStaff(Message.raw("[Staff] " + playerName + " vanished.").color(java.awt.Color.GRAY));
        }

        return CompletableFuture.completedFuture(null);
    }
}
