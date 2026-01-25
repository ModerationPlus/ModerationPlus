package me.almana.moderationplus.api.event.state;

import java.util.UUID;
import me.almana.moderationplus.api.Since;

/**
 * Fired when a player's moderation state changes (e.g., they get muted or unmuted).
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public class PlayerModerationStateChangeEvent implements me.almana.moderationplus.api.event.ModEvent {

    private final UUID player;
    private final StateType type;
    private final boolean enabled;

    public PlayerModerationStateChangeEvent(UUID player, StateType type, boolean enabled) {
        this.player = player;
        this.type = type;
        this.enabled = enabled;
    }

    public UUID getPlayer() {
        return player;
    }

    public StateType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public enum StateType {
        MUTE,
        FREEZE,
        JAIL
    }
}
