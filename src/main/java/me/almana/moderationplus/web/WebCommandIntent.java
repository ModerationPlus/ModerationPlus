package me.almana.moderationplus.web;

import java.util.UUID;

public record WebCommandIntent(
        String id,
        String action,
        UUID targetUuid,
        String targetName,
        String reason,
        long duration,
        UUID issuerUuid,
        String issuerName) {
}
