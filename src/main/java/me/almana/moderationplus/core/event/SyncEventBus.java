package me.almana.moderationplus.core.event;

import me.almana.moderationplus.api.event.Cancellable;
import me.almana.moderationplus.api.event.EventBus;
import me.almana.moderationplus.api.event.EventPriority;
import me.almana.moderationplus.api.event.ModEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SyncEventBus implements EventBus {

    private final Map<Class<? extends ModEvent>, List<RegisteredListener>> listeners = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(SyncEventBus.class);

    @Override
    public <T extends ModEvent> void register(Class<T> type, EventPriority priority, boolean ignoreCancelled, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new RegisteredListener(priority, ignoreCancelled, listener));
    }

    @Override
    public <T extends ModEvent> void register(Class<T> type, EventPriority priority, Consumer<T> listener) {
        register(type, priority, false, listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void dispatch(ModEvent event) {
        List<RegisteredListener> eventListeners = listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) return;

        List<RegisteredListener> sorted;
        synchronized (eventListeners) {
            sorted = new ArrayList<>(eventListeners);
        }
        sorted.sort((a, b) -> Integer.compare(a.priority.getSlot(), b.priority.getSlot()));

        for (RegisteredListener reg : sorted) {
            if (event instanceof Cancellable) {
                Cancellable c = (Cancellable) event;
                if (c.isCancelled() && !reg.ignoreCancelled && reg.priority != EventPriority.MONITOR) {
                    continue;
                }
            }
            try {
                if (reg.priority == EventPriority.MONITOR && event instanceof Cancellable) {
                   boolean wasCancelled = ((Cancellable) event).isCancelled();
                   ((Consumer<ModEvent>) reg.listener).accept(event);
                   if (((Cancellable) event).isCancelled() != wasCancelled) {
                        ((Cancellable) event).setCancelled(wasCancelled); // Revert
                        logger.warn("MONITOR listener attempted to change cancellation state!");
                   }
                } else {
                    ((Consumer<ModEvent>) reg.listener).accept(event);
                }
            } catch (Exception e) {
                logger.error("Error dispatching event " + event.getClass().getSimpleName(), e);
            }
        }
    }

    private static class RegisteredListener {
        final EventPriority priority;
        final boolean ignoreCancelled;
        final Consumer<?> listener;

        RegisteredListener(EventPriority priority, boolean ignoreCancelled, Consumer<?> listener) {
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
            this.listener = listener;
        }
    }
}
