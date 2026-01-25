package me.almana.moderationplus.api.event.chat;

import me.almana.moderationplus.api.chat.ChatChannel;
import me.almana.moderationplus.api.event.Cancellable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.almana.moderationplus.api.Since;

/**
 * Fired when a message is sent to a staff chat channel.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public class StaffChatEvent implements Cancellable {

    private final UUID sender;
    private final ChatChannel channel;
    private String message;
    private Set<UUID> recipients;
    private boolean cancelled = false;

    /**
     * Constructs a StaffChatEvent. 
     * Recipients are not yet resolved at construction time (can be null or empty initially, to be filled by command/delivery logic, or explicitly passed).
     * To support the Phase 6 flow ("Event constructed BEFORE recipient resolution"), we allow recipients to be set later.
     */
    public StaffChatEvent(UUID sender, ChatChannel channel, String message) {
        this(sender, channel, message, new HashSet<>());
    }

    public StaffChatEvent(UUID sender, ChatChannel channel, String message, Set<UUID> recipients) {
        this.sender = sender;
        this.channel = channel;
        this.message = message;
        this.recipients = recipients;
    }

    public UUID getSender() {
        return sender;
    }

    public ChatChannel getChannel() {
        return channel;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Set<UUID> getRecipients() {
        return recipients;
    }

    public void setRecipients(Set<UUID> recipients) {
        this.recipients = recipients;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
