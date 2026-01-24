package me.almana.moderationplus.commands;

import com.hypixel.hytale.server.core.Message;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UnfreezeCommand extends AbstractCommand {

    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public UnfreezeCommand(ModerationPlus plugin) {
        super("unfreeze", "Unfreeze a player");
        this.plugin = plugin;
        this.requirePermission("moderation.unfreeze");
        this.playerArg = withRequiredArg("player", "Player to unfreeze", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        CommandSender sender = ctx.sender();

        String targetName = ctx.get(playerArg);


        UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
        if (targetUuid == null) {
            ctx.sendMessage(Message.raw("Cannot resolve UUID for " + targetName).color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        String resolvedName = targetName;
        PlayerRef ref = Universe.get().getPlayer(targetUuid); // Safe lookup
        if (ref != null && ref.isValid()) {
            resolvedName = ref.getUsername();
        }

        final String finalResolvedName = resolvedName;


        return plugin.removeFrozenPlayer(targetUuid).thenCompose(wasFrozen -> {
            if (!wasFrozen) {
                ctx.sendMessage(Message.raw(finalResolvedName + " is not frozen.").color(java.awt.Color.RED));
                return CompletableFuture.completedFuture(null);
            }


            String issuerName = (sender instanceof Player) ? sender.getDisplayName() : "Console";
            plugin.notifyStaff(Message.raw("[Staff] " + issuerName + " unfroze " + finalResolvedName + ".")
                    .color(java.awt.Color.GREEN));

            ctx.sendMessage(Message.raw("Unfroze " + finalResolvedName).color(java.awt.Color.GREEN));

            return CompletableFuture.completedFuture(null);
        });
    }
}
