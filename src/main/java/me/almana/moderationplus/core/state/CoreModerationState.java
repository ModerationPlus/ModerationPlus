package me.almana.moderationplus.core.state;

import me.almana.moderationplus.api.punishment.Punishment;
import me.almana.moderationplus.api.punishment.PunishmentType;
import me.almana.moderationplus.api.state.ModerationState;
import me.almana.moderationplus.core.punishment.DefaultPunishmentTypes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of ModerationState.
 */
public class CoreModerationState implements ModerationState {

    private final Map<String, Punishment> activePunishments = new ConcurrentHashMap<>();

    @Override
    public boolean isMuted() {
        return activePunishments.containsKey(DefaultPunishmentTypes.MUTE.id());
    }

    @Override
    public boolean isFrozen() {
        return activePunishments.containsKey(DefaultPunishmentTypes.FREEZE.id());
    }

    @Override
    public boolean isJailed() {
        return activePunishments.containsKey(DefaultPunishmentTypes.JAIL.id());
    }

    @Override
    public Optional<Punishment> getActive(PunishmentType type) {
        return Optional.ofNullable(activePunishments.get(type.id()));
    }

    // Internal modification methods
    void addPunishment(Punishment punishment) {
        activePunishments.put(punishment.type().id(), punishment);
    }

    void removePunishment(PunishmentType type) {
        activePunishments.remove(type.id());
    }
}
