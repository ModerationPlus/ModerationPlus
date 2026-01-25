package me.almana.moderationplus.api.event;

public interface Cancellable extends ModEvent {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
