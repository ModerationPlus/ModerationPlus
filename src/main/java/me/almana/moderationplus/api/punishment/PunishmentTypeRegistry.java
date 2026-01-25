package me.almana.moderationplus.api.punishment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentTypeRegistry {
    
    private final Map<String, PunishmentType> types = new ConcurrentHashMap<>();

    public void register(PunishmentType type) {
        if (types.containsKey(type.id())) {
            throw new IllegalArgumentException("PunishmentType with id " + type.id() + " already registered.");
        }
        types.put(type.id(), type);
    }

    public Optional<PunishmentType> get(String id) {
        return Optional.ofNullable(types.get(id));
    }
}
