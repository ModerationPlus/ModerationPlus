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
            ctx.sendMessage(Message.raw("Player '" + targetName + "' not found.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

        try {
            boolean nativeUnbanned = false;
            // Native unban
            HytaleBanProvider banProvider = plugin.getBanProvider();
            if (banProvider != null) {
                if (banProvider.hasBan(targetUuid)) {
                    final UUID finalUuid = targetUuid;
                    banProvider.modify(bans -> {
                        bans.remove(finalUuid);
                        return true;
                    });
                    nativeUnbanned = true;
                }
            } else {
                ctx.sendMessage(Message.raw("Error: Native Ban Provider not found. Unban might be incomplete.")
                        .color(Color.RED));
            }

            // Database unban
            List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "BAN");
            boolean dbUnbanned = false;
            if (!activeBans.isEmpty()) {
                for (Punishment p : activeBans) {
                    plugin.getStorageManager().deactivatePunishment(p.id());
                }
                dbUnbanned = true;
            }

            if (nativeUnbanned || dbUnbanned) {
                ctx.sendMessage(Message.raw("[Staff] " + sender.getDisplayName() + " unbanned " + resolvedName)
                        .color(Color.GREEN));
            } else {
                ctx.sendMessage(Message.raw("Player " + resolvedName + " is not banned.").color(Color.RED));
            }

        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error processing unban: " + e.getMessage()).color(Color.RED));
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(null);
    }
}
