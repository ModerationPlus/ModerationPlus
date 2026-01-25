package me.almana.moderationplus.core.chat;

import me.almana.moderationplus.api.chat.ChatChannel;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DefaultChatChannel implements ChatChannel {

    private final String id;
    private final String permission;
    private final String format;

    public DefaultChatChannel(String id, String permission, String format) {
        this.id = id;
        this.permission = permission;
        this.format = format;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String permission() {
        return permission;
    }

    @Override
    public String format() {
        return format;
    }

    @Override
    public Set<UUID> resolveRecipients(UUID sender) {
        Set<UUID> recipients = new HashSet<>();
        Universe.get().getPlayers().forEach(ref -> {
            if (ref != null && ref.isValid()) {
                if (PermissionsModule.get().hasPermission(ref.getUuid(), permission)) {
                    recipients.add(ref.getUuid());
                }
            }
        });
        return recipients;
    }
}
