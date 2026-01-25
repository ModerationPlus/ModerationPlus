package me.almana.moderationplus.api.event.punishment;

import me.almana.moderationplus.api.event.ModEvent;
import me.almana.moderationplus.api.punishment.Punishment;

import me.almana.moderationplus.api.Since;

/**
 * Fired when a punishment is expired (revoked).
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public class PunishmentExpiredEvent implements ModEvent {

    private final Punishment punishment;

    public PunishmentExpiredEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }
}
