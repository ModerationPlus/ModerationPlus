package me.almana.moderationplus.service;

import java.util.UUID;

public record ExecutionContext(UUID issuerUuid, String issuerName, ExecutionSource source) {

    public enum ExecutionSource {
        COMMAND,
        CONSOLE,
        WEB
    }

    public static ExecutionContext console() {
        return new ExecutionContext(UUID.nameUUIDFromBytes("CONSOLE".getBytes()), "Console", ExecutionSource.CONSOLE);
    }
}
