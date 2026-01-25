package me.almana.moderationplus.core.audit;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.api.event.punishment.PunishmentAppliedEvent;
import me.almana.moderationplus.api.event.punishment.PunishmentExpiredEvent;
import me.almana.moderationplus.api.event.staff.*;
import me.almana.moderationplus.api.event.audit.*;
import me.almana.moderationplus.api.punishment.Punishment;

import java.util.HashMap;
import java.util.Map;

public class CoreAuditService {

    private final ModerationPlus plugin;

    public CoreAuditService(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getEventBus().register(PunishmentAppliedEvent.class, this::onPunishmentApplied);
        plugin.getEventBus().register(PunishmentExpiredEvent.class, this::onPunishmentExpired);
        
        // Staff Actions
        plugin.getEventBus().register(StaffFreezeEvent.class, e -> onStaffAction(e, "FREEZE"));
        plugin.getEventBus().register(StaffUnfreezeEvent.class, e -> onStaffAction(e, "UNFREEZE"));
        plugin.getEventBus().register(StaffJailEvent.class, e -> onStaffAction(e, "JAIL"));
        plugin.getEventBus().register(StaffUnjailEvent.class, e -> onStaffAction(e, "UNJAIL"));
    }

    private void onPunishmentApplied(PunishmentAppliedEvent event) {
        Punishment p = event.getPunishment();
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", p.type().id());
        meta.put("reason", p.reason());
        if (p.duration() != null) meta.put("duration", p.duration().toString());
        meta.put("silent", p.silent());

        plugin.getEventBus().dispatch(new PunishmentAuditEvent(p.actor(), "PUNISH_APPLIED", p.target(), meta));
    }

    private void onPunishmentExpired(PunishmentExpiredEvent event) {
        Punishment p = event.getPunishment();
        Map<String, Object> meta = new HashMap<>();
        meta.put("type", p.type().id());
        // For expired/revoked, act is done by p.actor()? Wait.
        // Punishment object in ExpiredEvent is created in ModerationService.
        // In ModerationService, we pass context.issuerUuid() as actor for the "Unbanned" punishment object.
        // So p.actor() is correct.
        
        plugin.getEventBus().dispatch(new PunishmentAuditEvent(p.actor(), "PUNISH_EXPIRED", p.target(), meta));
    }

    private void onStaffAction(StaffActionEvent event, String actionName) {
        // Only audit if intent was not cancelled (though cancellations might be interesting, instructions imply action)
        if (event.isCancelled()) return;

        Map<String, Object> meta = new HashMap<>();
        meta.put("source", event.getSource());
        if (event instanceof StaffJailEvent) {
             meta.put("duration", ((StaffJailEvent) event).getDuration());
             meta.put("reason", ((StaffJailEvent) event).getReason());
        }

        plugin.getEventBus().dispatch(new StaffActionAuditEvent(event.getActor(), "STAFF_" + actionName, event.getTarget(), meta));
    }
}
