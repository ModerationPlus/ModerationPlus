package me.almana.moderationplus.api.event.staff;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Fired when a staff member attempts to unjail a player.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
public class StaffUnjailEvent extends StaffActionEvent {

    public StaffUnjailEvent(UUID actor, @Nullable UUID target, String source) {
        super(actor, target, source);
    }
}
