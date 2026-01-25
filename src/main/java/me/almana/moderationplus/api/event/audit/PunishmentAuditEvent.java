package me.almana.moderationplus.api.event.audit;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Audit event for punishments (Ban, Mute, etc.).
 */
public class PunishmentAuditEvent implements AuditEvent {

    private final UUID actor;
    private final UUID target;
    private final String action;
    private final Map<String, Object> metadata;

    public PunishmentAuditEvent(UUID actor, String action, @Nullable UUID target, Map<String, Object> metadata) {
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.metadata = metadata;
    }

    @Override
    public UUID getActor() {
        return actor;
    }
    
    @Override
    public UUID getTarget() {
        return target;
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
