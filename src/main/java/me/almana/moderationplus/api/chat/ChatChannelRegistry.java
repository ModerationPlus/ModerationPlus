package me.almana.moderationplus.api.chat;

import java.util.Collection;
import java.util.Optional;
import me.almana.moderationplus.api.Since;

/**
 * Registry for ChatChannels.
 */
@Since("1.0.0")
public interface ChatChannelRegistry {

    void register(ChatChannel channel);

    Optional<ChatChannel> getChannel(String id);

    Collection<ChatChannel> getAllChannels();
}
