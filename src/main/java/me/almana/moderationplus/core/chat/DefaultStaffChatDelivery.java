package me.almana.moderationplus.core.chat;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import me.almana.moderationplus.api.chat.StaffChatDelivery;
import me.almana.moderationplus.api.event.chat.StaffChatEvent;

import java.awt.Color;
import java.util.UUID;

public class DefaultStaffChatDelivery implements StaffChatDelivery {

    private final me.almana.moderationplus.ModerationPlus plugin;

    public DefaultStaffChatDelivery(me.almana.moderationplus.ModerationPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void deliver(StaffChatEvent event) {
        String senderName = "Unknown";
        if (event.getSender().equals(me.almana.moderationplus.api.ModerationConstants.CONSOLE_UUID)) {
            senderName = "Console";
        } else {
            PlayerRef senderRef = Universe.get().getPlayer(event.getSender());
            if (senderRef != null) {
                senderName = senderRef.getUsername();
            }
        }

        String formatted = String.format(event.getChannel().format(), senderName, event.getMessage());
        Message msgObj = Message.raw(formatted).color(Color.MAGENTA);

        for (UUID uuid : event.getRecipients()) {
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.isValid()) {
                ref.sendMessage(msgObj);
            }
        }

        ConsoleSender.INSTANCE.sendMessage(msgObj);

        // Audit Event
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("channel", event.getChannel().id());
        meta.put("message", event.getMessage());
        meta.put("recruit_count", event.getRecipients().size());
        
        plugin.getEventBus().dispatch(new me.almana.moderationplus.api.event.audit.StaffChatAuditEvent(
                event.getSender(), "STAFF_CHAT", meta
            ));
    }
}
