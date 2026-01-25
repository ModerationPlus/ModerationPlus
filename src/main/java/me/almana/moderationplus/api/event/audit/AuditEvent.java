package me.almana.moderationplus.api.event.audit;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import me.almana.moderationplus.api.Since;

/**
 * Represents a structured audit event for observability.
 * These events are informational and should be used for logging/analytics.
 * 
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
@Since("1.0.0")
public interface AuditEvent extends me.almana.moderationplus.api.event.ModEvent {

    /**
     * @return The UUID of the actor who performed the action.
     */
    UUID getActor();

    /**
     * @return The UUID of the target, if any.
     */
    @Nullable
    UUID getTarget();

    /**
     * @return A string identifier for the action (e.g. "PUNISH_BAN", "STAFF_CHAT").
     */
    String getAction();

    /**
     * @return A map of metadata associated with the event.
     */
    Map<String, Object> getMetadata();
}
