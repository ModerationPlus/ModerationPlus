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

public class FirstJoinCommand extends AbstractCommand {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final RequiredArg<String> playerArg;

    public FirstJoinCommand(ModerationPlus plugin) {
        super("firstjoin", "Check player's first join time");
        this.plugin = plugin;
        this.requirePermission("moderation.firstjoin");
        this.playerArg = withRequiredArg("player", "Player to check", (ArgumentType<String>) ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        String targetName = ctx.get(playerArg);

        return CompletableFuture.runAsync(() -> {
            StorageManager.PlayerData data = null;
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

            try {
                PlayerRef onlineRef = Universe.get().getPlayer(targetName, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE);
                if (onlineRef != null) {
                    data = plugin.getStorageManager().getPlayerByUUID(onlineRef.getUuid());
                } else {
                    UUID offlineUuid = plugin.getStorageManager().getUuidByUsername(targetName);
                    if (offlineUuid != null) {
                        data = plugin.getStorageManager().getPlayerByUUID(offlineUuid);
                    }
                }

                if (data == null) {
                    String msg = plugin.getLanguageManager().translate(
                            "command.firstjoin.not_found",
                            currentLocale,
                            Map.of("player", targetName)
                    );
                    ctx.sendMessage(me.almana.moderationplus.utils.ColorUtils.parse(msg));
                    return;
                }

                if (data.firstSeen() <= 0) {
                     String msg = plugin.getLanguageManager().translate(
                            "command.firstjoin.never_joined",
                            currentLocale,
                            Map.of("player", data.username())
                    );
                    ctx.sendMessage(me.almana.moderationplus.utils.ColorUtils.parse(msg));
                    return;
                }

                String msg = plugin.getLanguageManager().translate(
                        "command.firstjoin.success",
                        currentLocale,
                        Map.of("player", data.username(), "time", me.almana.moderationplus.utils.TimeUtils.formatTime(data.firstSeen(), currentLocale))
                );
                ctx.sendMessage(me.almana.moderationplus.utils.ColorUtils.parse(msg));

            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Error executing /firstjoin for %s", targetName);
                String errorMsg = plugin.getLanguageManager().translate(
                    "command.firstjoin.error",
                    currentLocale,
                    Map.of()
                );
                ctx.sendMessage(Message.raw(errorMsg).color(Color.RED));
            }
        });
    }
}
