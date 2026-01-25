package me.almana.moderationplus.core.chat;

import me.almana.moderationplus.api.chat.ChatChannel;
import me.almana.moderationplus.api.chat.ChatChannelRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleChatChannelRegistry implements ChatChannelRegistry {

    private final Map<String, ChatChannel> channels = new ConcurrentHashMap<>();

    @Override
    public void register(ChatChannel channel) {
        channels.put(channel.id(), channel);
    }

    @Override
    public Optional<ChatChannel> getChannel(String id) {
        return Optional.ofNullable(channels.get(id));
    }

    @Override
    public Collection<ChatChannel> getAllChannels() {
        return Collections.unmodifiableCollection(channels.values());
    }
}
