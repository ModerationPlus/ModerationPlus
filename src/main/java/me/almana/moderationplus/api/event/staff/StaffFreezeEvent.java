package me.almana.moderationplus.api.event.staff;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Fired when a staff member attempts to freeze a player.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
public class StaffFreezeEvent extends StaffActionEvent {

    public StaffFreezeEvent(UUID actor, @Nullable UUID target, String source) {
        super(actor, target, source);
    }
}
