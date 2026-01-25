package me.almana.moderationplus.api.state;

import java.util.UUID;
import me.almana.moderationplus.api.Since;

/**
 * Service for retrieving player moderation state.
 */
@Since("1.0.0")
public interface ModerationStateService {

    /**
     * Retrieves the moderation state for a player.
     * 
     * @param player The UUID of the player.
     * @return The moderation state.
     */
    ModerationState getState(UUID player);
}
