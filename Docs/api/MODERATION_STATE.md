# Moderation State API

## Overview

The Moderation State API provides **read-only access** to a player's current moderation status without needing to query the database.

## ModerationState Interface

```java
public interface ModerationState {
    boolean isMuted();
    boolean isFrozen();
    boolean isJailed();
    
    Optional<Punishment> getActive(PunishmentType type);
}
```

### Boolean State Methods

These provide **quick checks** for common moderation states:

```java
ModerationState state = stateService.getState(playerUuid);

if (state.isMuted()) {
    // Player cannot send chat messages
}

if (state.isFrozen()) {
    // Player cannot move or interact
}

if (state.isJailed()) {
    // Player is in jail location
}
```

**Important**: These methods return `true` only for the specific punishment types (MUTE, FREEZE, JAIL). Other types like BAN, KICK, WARN do not affect these methods.

### getActive(PunishmentType)

For more detailed queries, use `getActive`:

```java
// Check if player has any active ban
Optional<Punishment> activeBan = state.getActive(DefaultPunishmentTypes.BAN);
if (activeBan.isPresent()) {
    Punishment ban = activeBan.get();
    if (ban.duration() != null) {
        // Temporary ban - show remaining time
        long remaining = calculateRemaining(ban);
    } else {
        // Permanent ban
    }
}
```

**Returns**:
- `Optional.of(punishment)` if an active punishment of that type exists
- `Optional.empty()` if no active punishment

## State Authority

The `CoreModerationStateService` is the **single source of truth** for player moderation state.

### Initialization

State is **lazy-loaded** on first access:

```java
public ModerationState getState(UUID player) {
    return stateCache.computeIfAbsent(player, k -> new CoreModerationState());
}
```

**Key Points**:
- First call to `getState(uuid)` creates the state object
- Subsequent calls return the **same instance** from cache
- States are **not pre-loaded** from database (for performance)

### State Updates

State updates happen **automatically** via event listeners:

```
PunishmentAppliedEvent → State.addPunishment()
PunishmentExpiredEvent → State.removePunishment()
```

External plugins do **NOT** need to manually update state. It's maintained internally.

## State Change Events

When a player's state changes, `PlayerModerationStateChangeEvent` is dispatched:

```java
public class PlayerModerationStateChangeEvent implements ModEvent {
    UUID getPlayer();
    StateType getType();    // MUTE, FREEZE, or JAIL
    boolean isEnabled();    // true = applied, false = removed
    
    enum StateType { MUTE, FREEZE, JAIL }
}
```

### When It Fires

```
Player gets muted:
  → PunishmentAppliedEvent (type=MUTE)
  → State cache updated
  → PlayerModerationStateChangeEvent (type=MUTE, enabled=true)

Player gets unmuted:
  → PunishmentExpiredEvent (type=MUTE)
  → State cache updated
  → PlayerModerationStateChangeEvent (type=MUTE, enabled=false)
```

### Threading

Like all ModerationPlus events, `PlayerModerationStateChangeEvent` is dispatched **synchronously on the main thread**.

## De-duplication Behavior

State change events fire **only when the boolean state actually changes**.

### Example: Multiple Mutes

```java
// Player is not muted
// State: isMuted() = false

// 1. Apply first mute
moderationService.mute(player, "spam");
// → PlayerModerationStateChangeEvent (MUTE, enabled=true)

// 2. Apply second mute (while still muted)
moderationService.mute(player, "caps");
// → NO PlayerModerationStateChangeEvent
//    (isMuted() was already true)

// 3. Unmute first punishment
moderationService.unmute(player);
// → Still have second mute active
// → NO PlayerModerationStateChangeEvent
//    (isMuted() still true)

// 4. Unmute second punishment
moderationService.unmute(player);
// → No more active mutes
// → PlayerModerationStateChangeEvent (MUTE, enabled=false)
```

**Why De-duplicate?**:
- Prevents spam of state change events
- Listeners care about "became muted" vs "already muted"
- Simplifies UI updates (show/hide indicators)

### Implementation

```java
private void onPunishmentApplied(PunishmentAppliedEvent event) {
    boolean wasActive = checkActive(state, p.type());
    state.addPunishment(p);
    
    if (!wasActive) {  // Only fire if transitioning false → true
        fireChange(p.target(), stateType, true);
    }
}
```

## Example: Observing State Changes

```java
// React to players being muted
bus.register(PlayerModerationStateChangeEvent.class, event -> {
    if (event.getType() == StateType.MUTE && event.isEnabled()) {
        UUID player = event.getPlayer();
        
        // Clear their message queue
        messageQueue.clearPlayer(player);
        
        // Notify them
        PlayerRef ref = Universe.get().getPlayer(player);
        if (ref != null) {
            ref.sendMessage(Message.raw("You have been muted."));
        }
    }
});

// Unmute notification
bus.register(PlayerModerationStateChangeEvent.class, event -> {
    if (event.getType() == StateType.MUTE && !event.isEnabled()) {
        PlayerRef ref = Universe.get().getPlayer(event.getPlayer());
        if (ref != null) {
            ref.sendMessage(Message.raw("You have been unmuted."));
        }
    }
});
```

## Example: Checking State Before Action

```java
// In a custom command
if (stateService.getState(playerUuid).isFrozen()) {
    sender.sendMessage("Player is frozen, cannot teleport them.");
    return;
}

teleportPlayer(playerUuid, destination);
```

## State Persistence

**In-Memory Only**: The `ModerationState` cache is **not persisted**. On server restart:
- Cache is empty
- States are rebuilt from database on first access (via `getActive()` queries)

**Why?**:
- State is derived from punishments (which ARE persisted)
- Rebuilding is fast (single DB query per player)
- Avoids cache staleness issues

## Read-Only Guarantee

`ModerationState` is **read-only** for external plugins. You cannot:
- Directly set `isMuted()` to true/false
- Manually add/remove punishments from state
- Clear the state cache

**To modify state, use the ModerationService**:

```java
// ✓ Correct
moderationService.mute(playerUuid, duration, reason, context);

// ✗ Wrong (no such method)
state.setMuted(true);
```

## Relationship to Punishments

```
Punishment (Database)
    ↓
PunishmentAppliedEvent
    ↓
State Cache Update
    ↓
Query via ModerationState interface
    ↓
External plugin reads isMuted(), getActive(), etc.
```

State is a **cache** and **convenience layer** over the underlying punishment data.

## State Types

Only **three** punishment types are tracked as boolean states:

| PunishmentType | ModerationState Method | StateType |
|----------------|------------------------|-----------|
| MUTE | `isMuted()` | `MUTE` |
| FREEZE | `isFrozen()` | `FREEZE` |
| JAIL | `isJailed()` | `JAIL` |

**Not tracked as boolean states**:
- BAN (use `getActive(DefaultPunishmentTypes.BAN)`)
- KICK (instant, no state)
- WARN (no enforcement)

**Why?**:
- MUTE, FREEZE, JAIL have ongoing effects that need quick checks
- BAN prevents login (checked at join time, not during gameplay)
- KICK and WARN are record-only

## Thread Safety

The `stateCache` is a `ConcurrentHashMap`, making it safe for concurrent reads. However:
- **Writes** (addPunishment, removePunishment) happen only on the main thread (via event listeners)
- **Reads** can happen from any thread

This is safe because:
- State updates are atomic (add/remove single punishment)
- Event dispatch is synchronous (no race conditions)
- ConcurrentHashMap handles concurrent reads
