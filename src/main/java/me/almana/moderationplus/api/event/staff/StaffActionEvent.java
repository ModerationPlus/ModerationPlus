package me.almana.moderationplus.api.event.staff;

import me.almana.moderationplus.api.event.Cancellable;

import java.util.UUID;
import javax.annotation.Nullable;
import me.almana.moderationplus.api.Since;

/**
 * Base event for staff actions.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public abstract class StaffActionEvent implements Cancellable {

    private final UUID actor;
    private final UUID target;
    private final String source;
    private boolean cancelled = false;

    public StaffActionEvent(UUID actor, @Nullable UUID target, String source) {
        this.actor = actor;
        this.target = target;
        this.source = source;
    }

    public UUID getActor() {
        return actor;
    }

    @Nullable
    public UUID getTarget() {
        return target;
    }

    public String getSource() {
        return source;
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
