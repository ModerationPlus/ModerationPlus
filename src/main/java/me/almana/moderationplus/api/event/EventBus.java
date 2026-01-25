package me.almana.moderationplus.api.event;

import java.util.function.Consumer;

public interface EventBus {
    <T extends ModEvent> void register(Class<T> type, EventPriority priority, boolean ignoreCancelled, Consumer<T> listener);
    <T extends ModEvent> void register(Class<T> type, EventPriority priority, Consumer<T> listener);
    
    default <T extends ModEvent> void register(Class<T> type, Consumer<T> listener) {
        register(type, EventPriority.NORMAL, listener);
    }

    void dispatch(ModEvent event);
}
