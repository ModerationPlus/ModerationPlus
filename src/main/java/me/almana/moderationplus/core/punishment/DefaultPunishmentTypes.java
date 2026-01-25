package me.almana.moderationplus.core.punishment;

import me.almana.moderationplus.api.punishment.PunishmentType;
import me.almana.moderationplus.api.punishment.PunishmentTypeRegistry;

public class DefaultPunishmentTypes {

    public static final PunishmentType BAN = new SimplePunishmentType("BAN");
    public static final PunishmentType MUTE = new SimplePunishmentType("MUTE");
    public static final PunishmentType KICK = new SimplePunishmentType("KICK");
    public static final PunishmentType WARN = new SimplePunishmentType("WARN");
    public static final PunishmentType JAIL = new SimplePunishmentType("JAIL");
    public static final PunishmentType FREEZE = new SimplePunishmentType("FREEZE");

    public static void registerDefaults(PunishmentTypeRegistry registry) {
        registry.register(BAN);
        registry.register(MUTE);
        registry.register(KICK);
        registry.register(WARN);
        registry.register(JAIL);
        registry.register(FREEZE);
    }

    private record SimplePunishmentType(String id) implements PunishmentType {}
}
