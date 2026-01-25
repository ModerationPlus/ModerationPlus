package me.almana.moderationplus.api.event.punishment;

import me.almana.moderationplus.api.event.ModEvent;
import me.almana.moderationplus.api.punishment.Punishment;

import me.almana.moderationplus.api.Since;

/**
 * Fired after a punishment has been successfully applied.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public class PunishmentAppliedEvent implements ModEvent {

    private final Punishment punishment;

    public PunishmentAppliedEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }
}
