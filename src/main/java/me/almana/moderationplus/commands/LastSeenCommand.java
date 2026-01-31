package me.almana.moderationplus.commands;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StorageManager;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.awt.Color;
import java.util.logging.Level;
import me.almana.moderationplus.utils.ColorUtils;
import me.almana.moderationplus.utils.TimeUtils;
public class LastSeenCommand extends AbstractCommand {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public LastSeenCommand(ModerationPlus plugin) {
        super("lastseen", "Check player's last seen time");
        this.plugin = plugin;
        this.requirePermission("moderation.lastseen");
        this.playerArg = withRequiredArg("player", "Player to check", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String targetName = ctx.get(playerArg);
        String currentLocale = plugin.getLanguageManager().getDefaultLocale(); // Default fallback

        if (ctx.isPlayer()) {
            try {
                PlayerRef senderRef = Universe.get().getPlayer(ctx.sender().getUuid());
                if (senderRef != null) {
                    currentLocale = senderRef.getLanguage();
                }
            } catch (Exception ignored) {
                // Fallback to default
            }
        }

        String finalCurrentLocale = currentLocale;

        return CompletableFuture.runAsync(() -> {
            try {
                UUID targetUuid = plugin.getStorageManager().getUuidByUsername(targetName);
                if (targetUuid == null) {
                    sendNotFound(ctx, targetName, finalCurrentLocale);
                    return;
                }

                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                
                if (ref != null && ref.isValid()) { 
                     String msg = plugin.getLanguageManager().translate(
                            "command.lastseen.online",
                            finalCurrentLocale,
                            Map.of("player", ref.getUsername())
                     );
                     ctx.sendMessage(ColorUtils.parse(msg));
                     return;
                }

                StorageManager.PlayerData data = plugin.getStorageManager().getPlayerByUUID(targetUuid);

                if (data.lastSeen() <= 0) {
                     String msg = plugin.getLanguageManager().translate(
                            "command.lastseen.never_seen",
                            finalCurrentLocale,
                            Map.of("player", data.username())
                    );
                    ctx.sendMessage(ColorUtils.parse(msg));
                    return;
                }

                String formattedTime = TimeUtils.formatTime(data.lastSeen(), finalCurrentLocale);
                String msg = plugin.getLanguageManager().translate(
                        "command.lastseen.success",
                        finalCurrentLocale,
                        Map.of("player", data.username(), "time", formattedTime)
                );
                ctx.sendMessage(ColorUtils.parse(msg));

            } catch (Exception e) {
                 logger.at(Level.SEVERE).withCause(e).log("Error executing /lastseen for %s", targetName);
                String errorMsg = plugin.getLanguageManager().translate(
                    "command.lastseen.error",
                    finalCurrentLocale,
                    Map.of()
                );
                ctx.sendMessage(ColorUtils.parse(errorMsg));
            }
        });
    }

    private void sendNotFound(CommandContext ctx, String name, String locale) {
        String msg = plugin.getLanguageManager().translate(
                "command.lastseen.not_found",
                locale,
                Map.of("player", name)
        );
        ctx.sendMessage(ColorUtils.parse(msg));
    }
}
