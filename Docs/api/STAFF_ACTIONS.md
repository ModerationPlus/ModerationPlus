# Staff Actions

## Intent vs Execution

Staff actions use a **two-phase model**:

1. **Intent Event** (StaffActionEvent) - Staff member *attempts* the action
2. **Punishment Event** (if applicable) - The actual enforcement happens

This separation allows plugins to:
- Cancel actions before they create punishments
- Distinguish between "tried to jail" vs "actually jailed"
- Audit both successful and failed attempts

## StaffActionEvent Hierarchy

Base class for all staff-initiated actions:

```java
public abstract class StaffActionEvent implements Cancellable {
    UUID getActor();      // Staff member performing action
    UUID getTarget();     // Player being acted upon (nullable)
    String getSource();   // "COMMAND" or "GUI"
    
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
```

### Concrete Event Types

| Event | Purpose | Target Required | Additional Fields |
|-------|---------|-----------------|-------------------|
| `StaffFreezeEvent` | Freeze player movement | Yes | None |
| `StaffUnfreezeEvent` | Unfreeze player | Yes | None |
| `StaffJailEvent` | Jail player | Yes | `duration`, `reason` |
| `StaffUnjailEvent` | Un-jail player | Yes | None |

**Note**: Ban, Mute, Kick, and Warn do **not** have StaffActionEvents. They go directly to `PunishmentPreApplyEvent`.

## StaffJailEvent Deep Dive

The most complex staff action event:

```java
public class StaffJailEvent extends StaffActionEvent {
    long getDuration();   // Milliseconds, 0 = permanent
    String getReason();   // Why they're being jailed
}
```

### Execution Flow

```
[Command: /jail player 10m griefing]
    ↓
1. Parse arguments → duration=600000ms, reason="griefing"
    ↓
2. Create StaffJailEvent
    ↓
3. Dispatch intent event (COMMAND thread → async hop → MAIN thread)
    ↓
4. Check cancellation
    ↓
5. If not cancelled:
   - Create Punishment (type=JAIL)
   - Dispatch PunishmentPreApplyEvent
   - Store to database
   - Teleport player to jail
   - Apply freeze
   - Dispatch PunishmentAppliedEvent
```

## Command-Layer Interception

StaffActionEvents fire **before** any punishment logic executes. This allows cancellation without side effects:

```java
// Cancel jail attempts on admins
bus.register(StaffJailEvent.class, event -> {
    if (isAdmin(event.getTarget())) {
        event.setCancelled(true);
        notifyStaff(event.getActor(), "Cannot jail admins");
    }
});
```

### Why This Pattern Exists

Without StaffActionEvents, you'd have to:
1. Hook `PunishmentPreApplyEvent`
2. Check if type is JAIL
3. Infer it came from a staff command (not automated system)
4. Cancel the punishment

With StaffActionEvents:
- Clear intent signal
- Know the source (COMMAND vs GUI)
- Cancel before creating punishment objects
- Separate "command attempted" from "punishment created"

## Cancellation Semantics

### What Happens When Cancelled

```java
if (event.isCancelled()) {
    // Stops HERE - no further processing
    // No punishment created
    // No database writes
    // No player teleportation
    // No freeze applied
    return CompletableFuture.completedFuture(null);
}
```

### Who Can Cancel

Any listener at LOWEST to HIGHEST priority. Common patterns:

```java
// LOWEST: Basic validation
bus.register(StaffJailEvent.class, EventPriority.LOWEST, event -> {
    if (event.getTarget() == null) {
        event.setCancelled(true);
    }
});

// NORMAL: Permission checks
bus.register(StaffJailEvent.class, event -> {
    if (!canJail(event.getActor(), event.getTarget())) {
        event.setCancelled(true);
    }
});

// HIGHEST: Final override
bus.register(StaffJailEvent.class, EventPriority.HIGHEST, event -> {
    if (isProtectedPlayer(event.getTarget())) {
        event.setCancelled(true);
    }
});
```

## The "source" Field

Indicates how the action was triggered:

```java
public enum ExecutionSource {
    COMMAND,  // Player typed /jail in chat
    GUI       // Clicked button in inventory menu (if implemented)
}
```

**Current Implementation**: Only `COMMAND` is used. `GUI` is reserved for future GUI-based moderation panels.

Use cases:
- Different cooldowns for commands vs GUIs
- Audit log formatting
- Permission requirements

## Freeze vs Jail

These are **separate** systems:

### Freeze
- **Purpose**: Prevent player movement/interaction temporarily
- **Storage**: In-memory only (`ModerationPlus.frozenPlayers`)
- **Duration**: Until explicitly unfrozen
- **Component**: Adds `FrozenComponent` to player entity
- **No punishment record**: Not stored in database

### Jail
- **Purpose**: Teleport player to jail location + freeze them
- **Storage**: Database (Punishment table)
- **Duration**: Can be temporary or permanent
- **Side Effect**: Also freezes the player
- **Un-jailing**: Removes punishment + unfreezes + teleports to original location

**Key Insight**: Jail **includes** freezing, but freezing does not require jailing.

## Example: Preventing Jail on VIPs

```java
plugin.getEventBus().register(StaffJailEvent.class, event -> {
    UUID target = event.getTarget();
    if (vipManager.isVIP(target)) {
        event.setCancelled(true);
        
        // Notify the staff member
        UUID actor = event.getActor();
        PlayerRef staffRef = Universe.get().getPlayer(actor);
        if (staffRef != null) {
            staffRef.sendMessage(
                Message.raw("Cannot jail VIP players!")
                    .color(Color.RED)
            );
        }
    }
});
```

## Example: Logging All Jail Attempts

```java
// Use MONITOR to see both successful and cancelled attempts
plugin.getEventBus().register(
    StaffJailEvent.class, 
    EventPriority.MONITOR, 
    event -> {
        boolean success = !event.isCancelled();
        String outcome = success ? "SUCCESS" : "CANCELLED";
        
        logger.info(
            "[JAIL] {} attempted to jail {} for {}ms - {}",
            event.getActor(),
            event.getTarget(),
            event.getDuration(),
            outcome
        );
    }
);
```

## Relationship to Punishment Events

```
StaffJailEvent (Intent) → Cancellable
    ↓ (if not cancelled)
PunishmentPreApplyEvent (type=JAIL) → Cancellable, Mutable
    ↓ (if not cancelled)
Database Write + Enforcement
    ↓
PunishmentAppliedEvent (type=JAIL) → Observational
```

**When to use which**:
- Use `StaffJailEvent` to prevent staff from issuing the command
- Use `PunishmentPreApplyEvent` to modify jail duration/reason
- Use `PunishmentAppliedEvent` to log final outcome

## Non-Goals

StaffActionEvents do **NOT**:
- Cover automated punishments (e.g., anti-cheat bans)
- Provide mutation of `duration` or `reason` (use PunishmentPreApplyEvent)
- Control enforcement logic (that's in ModerationService)

They are **intent signals only**.
