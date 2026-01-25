package me.almana.moderationplus.api.punishment;

import java.time.Duration;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record Punishment(
    UUID id,
    UUID target,
    UUID actor,
    PunishmentType type,
    @Nullable Duration duration,
    String reason,
    boolean silent
) {}
