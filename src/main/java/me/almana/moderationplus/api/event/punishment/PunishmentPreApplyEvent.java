package me.almana.moderationplus.api.event.punishment;

import me.almana.moderationplus.api.event.Cancellable;
import me.almana.moderationplus.api.punishment.Punishment;

import me.almana.moderationplus.api.Since;

import java.time.Duration;

/**
 * Fired before a punishment is applied.
 * <p>
 * This event is cancellable. If cancelled, the punishment will not be applied.
 * </p>
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public class PunishmentPreApplyEvent implements Cancellable {

    private Punishment punishment;
    private boolean cancelled = false;

    public PunishmentPreApplyEvent(Punishment punishment) {
        this.punishment = punishment;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public void setDuration(Duration duration) {
        this.punishment = new Punishment(
                punishment.id(),
                punishment.target(),
                punishment.actor(),
                punishment.type(),
                duration,
                punishment.reason(),
                punishment.silent()
        );
    }

    public void setReason(String reason) {
        this.punishment = new Punishment(
                punishment.id(),
                punishment.target(),
                punishment.actor(),
                punishment.type(),
                punishment.duration(),
                reason,
                punishment.silent()
        );
    }

    public void setSilent(boolean silent) {
        this.punishment = new Punishment(
                punishment.id(),
                punishment.target(),
                punishment.actor(),
                punishment.type(),
                punishment.duration(),
                punishment.reason(),
                silent
        );
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
