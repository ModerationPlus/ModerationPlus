package me.almana.moderationplus.listeners;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.provider.VanishedPlayerIconMarkerProvider;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.utils.TimeUtils;

public class ModerationListeners {

    private final ModerationPlus plugin;
    private final Map<UUID, Long> lastMuteFeedback = new ConcurrentHashMap<>();
    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    public ModerationListeners(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    // Handles join punishment checks
    public void onPlayerSetupConnect(PlayerSetupConnectEvent event) {
        plugin.getModerationService().handleJoinPunishmentChecks(event.getUuid(), event.getUsername());
    }

    // Adds marker provider to worlds
    public void onAddWorld(AddWorldEvent event) {
        event.getWorld().getWorldMapManager().addMarkerProvider("playerIcons",
                new VanishedPlayerIconMarkerProvider(plugin));
    }

    // Cleans up vanished player data
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        plugin.getVanishedSnapshots().remove(event.getPlayerRef().getUuid());
        plugin.removeVanishedPlayer(event.getPlayerRef().getUuid());
    }

    // Handles chat locking and mutes
    public PlayerChatEvent onPlayerChat(PlayerChatEvent event) {
        try {
            UUID uuid = event.getSender().getUuid();
            logger.at(Level.INFO).log(
                    "Chat Event: " + event.getSender().getUsername() + ": " + event.getContent());

            if (plugin.getChatLocked()) {
                if (!PermissionsModule.get()
                        .hasPermission(uuid, "moderation.chatlockdown.bypass")) {
                    event.setCancelled(true);
                    event.getSender().sendMessage(Message
                            .raw("Chat is currently locked by staff.").color(Color.RED));
                    return event;
                }
            }

            if (plugin.isVanished(uuid)) {
                event.setCancelled(true);
                Message format = Message
                        .raw("[Vanished] " + event.getSender().getUsername() + ": "
                                + event.getContent())
                        .color(Color.GRAY);
                plugin.notifyStaff(format);
                return event;
            }

            Optional<Punishment> mute = plugin.getModerationService()
                    .getActiveMute(uuid, event.getSender().getUsername());

            if (mute.isPresent()) {
                Punishment p = mute.get();
                event.setCancelled(true);
                long now = System.currentTimeMillis();
                long last = lastMuteFeedback.getOrDefault(uuid, 0L);
                if (now - last > 2000) {
                    if (p.expiresAt() > 0) {
                        long remaining = p.expiresAt() - now;
                        String durationStr = TimeUtils
                                .formatDuration(remaining);
                        event.getSender().sendMessage(Message
                                .raw("You are muted for " + durationStr + ". Reason: "
                                        + p.reason())
                                .color(Color.RED));
                    } else {
                        event.getSender().sendMessage(Message
                                .raw("You are permanently muted. Reason: " + p.reason())
                                .color(Color.RED));
                    }
                    lastMuteFeedback.put(uuid, now);
                }
                return event;
            }

            logger.at(Level.INFO).log("Chat: Allowed");
            return event;
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Error in chat listener!");
        }
        return event;
    }
}
