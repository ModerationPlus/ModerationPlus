package me.almana.moderationplus.api.event.audit;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Audit event for staff chat messages.
 */
public class StaffChatAuditEvent implements AuditEvent {

    private final UUID actor;
    private final String action;
    private final Map<String, Object> metadata;

    public StaffChatAuditEvent(UUID actor, String action, Map<String, Object> metadata) {
        this.actor = actor;
        this.action = action;
        this.metadata = metadata;
    }

    @Override
    public UUID getActor() {
        return actor;
    }

    @Nullable
    @Override
    public UUID getTarget() {
        return null; // Staff chat typically targets a channel/group, not a single UUID
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
