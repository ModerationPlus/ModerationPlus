package me.almana.moderationplus.api.state;

import me.almana.moderationplus.api.punishment.Punishment;
import me.almana.moderationplus.api.punishment.PunishmentType;

import java.util.Optional;
import me.almana.moderationplus.api.Since;

/**
 * Represents the observable moderation state of a player.
 */
@Since("1.0.0")
public interface ModerationState {

    /**
     * @return true if the player is currently muted.
     */
    boolean isMuted();

    /**
     * @return true if the player is currently frozen.
     */
    boolean isFrozen();

    /**
     * @return true if the player is currently jailed.
     */
    boolean isJailed();

    /**
     * Gets the active punishment of a specific type, if present.
     * 
     * @param type The punishment type to query.
     * @return An Optional containing the active punishment, or empty if none.
     */
    Optional<Punishment> getActive(PunishmentType type);
}
