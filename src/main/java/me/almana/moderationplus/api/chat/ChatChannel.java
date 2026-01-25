package me.almana.moderationplus.api.chat;

import java.util.Set;
import java.util.UUID;
import me.almana.moderationplus.api.Since;

/**
 * Represents a chat channel for staff communication.
 */
@Since("1.0.0")
public interface ChatChannel {

    /**
     * @return The unique identifier of the channel (e.g. "staff").
     */
    String id();

    /**
     * @return The permission required to read/write to this channel.
     */
    String permission();

    /**
     * @return The format string for messages (e.g. "[SC] %s: %s").
     */
    String format();

    /**
     * Resolves the recipients for a message sent by the given sender.
     * 
     * @param sender The UUID of the sender.
     * @return A set of UUIDs representing the recipients.
     */
    Set<UUID> resolveRecipients(UUID sender);
}
