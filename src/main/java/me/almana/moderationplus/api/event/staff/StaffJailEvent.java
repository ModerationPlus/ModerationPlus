package me.almana.moderationplus.api.event.staff;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Fired when a staff member attempts to jail a player.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
public class StaffJailEvent extends StaffActionEvent {

    private final long duration;
    private final String reason;

    public StaffJailEvent(UUID actor, @Nullable UUID target, String source, long duration, String reason) {
        super(actor, target, source);
        this.duration = duration;
        this.reason = reason;
    }

    public long getDuration() {
        return duration;
    }

    public String getReason() {
        return reason;
    }
}
