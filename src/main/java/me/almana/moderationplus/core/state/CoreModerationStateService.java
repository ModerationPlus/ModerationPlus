package me.almana.moderationplus.core.state;

import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.api.event.punishment.PunishmentAppliedEvent;
import me.almana.moderationplus.api.event.punishment.PunishmentExpiredEvent;
import me.almana.moderationplus.api.event.state.PlayerModerationStateChangeEvent;
import me.almana.moderationplus.api.punishment.Punishment;
import me.almana.moderationplus.api.punishment.PunishmentType;
import me.almana.moderationplus.api.state.ModerationState;
import me.almana.moderationplus.api.state.ModerationStateService;
import me.almana.moderationplus.core.punishment.DefaultPunishmentTypes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoreModerationStateService implements ModerationStateService {

    private final ModerationPlus plugin;
    private final Map<UUID, CoreModerationState> stateCache = new ConcurrentHashMap<>();

    public CoreModerationStateService(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public ModerationState getState(UUID player) {
        return stateCache.computeIfAbsent(player, k -> new CoreModerationState());
    }

    public void init() {
        plugin.getEventBus().register(PunishmentAppliedEvent.class, this::onPunishmentApplied);
        plugin.getEventBus().register(PunishmentExpiredEvent.class, this::onPunishmentExpired);
    }

    private void onPunishmentApplied(PunishmentAppliedEvent event) {
        Punishment p = event.getPunishment();
        if (p == null || p.target() == null) return;

        CoreModerationState state = (CoreModerationState) getState(p.target());
        
        // Determine type
        PlayerModerationStateChangeEvent.StateType stateType = mapToStateType(p.type());
        if (stateType == null) return; // Warning, Ban, Kick don't map to MUTE/FREEZE/JAIL directly in boolean status?
        // Wait, Is BAN a state? The task asked for isMuted, isFrozen, isJailed.
        // It didn't ask for isBanned. But getActive(PunishmentType) should work for BAN too.
        // The boolean StateType enum only had MUTE, FREEZE, JAIL in the Prompt Task 2.
        
        boolean wasActive = checkActive(state, p.type());
        state.addPunishment(p);

        if (!wasActive) {
            fireChange(p.target(), stateType, true);
        }
    }

    private void onPunishmentExpired(PunishmentExpiredEvent event) {
        Punishment p = event.getPunishment();
        if (p == null || p.target() == null) return;

        CoreModerationState state = (CoreModerationState) getState(p.target());
        
        PlayerModerationStateChangeEvent.StateType stateType = mapToStateType(p.type());
        if (stateType == null) return;

        boolean wasActive = checkActive(state, p.type());
        state.removePunishment(p.type());

        if (wasActive) {
            fireChange(p.target(), stateType, false);
        }
    }

    private boolean checkActive(CoreModerationState state, PunishmentType type) {
        return state.getActive(type).isPresent();
    }

    private PlayerModerationStateChangeEvent.StateType mapToStateType(PunishmentType type) {
        if (type.id().equals(DefaultPunishmentTypes.MUTE.id())) return PlayerModerationStateChangeEvent.StateType.MUTE;
        if (type.id().equals(DefaultPunishmentTypes.FREEZE.id())) return PlayerModerationStateChangeEvent.StateType.FREEZE;
        if (type.id().equals(DefaultPunishmentTypes.JAIL.id())) return PlayerModerationStateChangeEvent.StateType.JAIL;
        return null;
    }

    private void fireChange(UUID player, PlayerModerationStateChangeEvent.StateType type, boolean enabled) {
        // Events are guaranteed main thread, and listeners are triggered on main thread via EventBus.
        // But we are ALREADY in a listener (PunishmentAppliedEvent), so we are on the main thread.
        // So we can dispatch directly.
        plugin.getEventBus().dispatch(new PlayerModerationStateChangeEvent(player, type, enabled));
    }
}
