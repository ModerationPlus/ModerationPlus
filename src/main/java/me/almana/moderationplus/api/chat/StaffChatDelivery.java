package me.almana.moderationplus.api.chat;

import me.almana.moderationplus.api.event.chat.StaffChatEvent;
import me.almana.moderationplus.api.Since;

/**
 * Handles the physical delivery of staff chat messages.
 */
@Since("1.0.0")
public interface StaffChatDelivery {

    /**
     * Delivers the message described in the event.
     * 
     * @param event The processed StaffChatEvent containing final recipients and message.
     */
    void deliver(StaffChatEvent event);
}
