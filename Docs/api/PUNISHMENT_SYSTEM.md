# Punishment System

## Punishment Data Model

A `Punishment` is an immutable record representing a moderation action:

```java
public record Punishment(
    UUID id,              // Unique punishment ID
    UUID target,          // Player being punished
    UUID actor,           // Staff member who issued it
    PunishmentType type,  // BAN, MUTE, KICK, WARN, JAIL, FREEZE
    @Nullable Duration duration,  // null = permanent
    String reason,        // Why this punishment was issued
    boolean silent        // If true, don't broadcast to other players
) {}
```

### Immutability

`Punishment` objects are **immutable**. To "modify" a punishment, create a new instance:

```java
// In PunishmentPreApplyEvent
Punishment original = event.getPunishment();
Punishment modified = new Punishment(
    original.id(),
    original.target(),
    original.actor(),
    original.type(),
    original.duration(),
    "MODIFIED: " + original.reason(),  // Changed field
    original.silent()
);
event.setPunishment(modified);  // Event stores the new instance
```

## PunishmentType

`PunishmentType` is a simple interface for identifying punishment categories:

```java
public interface PunishmentType {
    String id();
}
```

### Default Types

ModerationPlus registers six default types:

| Type ID | Description | Temporary? | Logged? |
|---------|-------------|------------|---------|
| `BAN` | Player cannot join server | Yes | Yes |
| `MUTE` | Player cannot send chat messages | Yes | Yes |
| `KICK` | Forcibly disconnect player | No | Yes |
| `WARN` | Record warning (no effect) | No | Yes |
| `JAIL` | Teleport to jail location + freeze | Yes | Yes |
| `FREEZE` | Prevent movement/interaction | Yes | Yes |

**NOTE**: `KICK` is instant and doesn't have a duration. `WARN` is a record-only punishment.

### Custom Types

Plugins can register custom punishment types:

```java
PunishmentTypeRegistry registry = plugin.getPunishmentTypeRegistry();
PunishmentType customType = () -> "CUSTOM_TIMEOUT";
registry.register(customType);
```

**WARNING**: Custom types may not have enforcement implemented. The `ModerationService` only enforces default types.

## Punishment Lifecycle Events

Every punishment flows through a three-stage event lifecycle:

### 1. PunishmentPreApplyEvent (Cancellable, Mutable)

**Fired**: Before punishment is written to storage or enforced  
**Thread**: Main thread  
**Cancellable**: Yes  
**Mutable**: Yes (via setter methods)

```java
public class PunishmentPreApplyEvent implements Cancellable {
    Punishment getPunishment();
    
    // Mutation methods (create new Punishment internally)
    void setDuration(Duration duration);
    void setReason(String reason);
    void setSilent(boolean silent);
    
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
```

**Use Cases**:
- Cancel punishment if target has immunity
- Extend/reduce duration based on history
- Append notes to reason
- Force silent mode for specific staff

**Critical**: Modifying `duration`, `reason`, or `silent` creates a **new `Punishment` instance**. The original is replaced.

### 2. PunishmentAppliedEvent (Observational)

**Fired**: After punishment is successfully stored and enforced  
**Thread**: Main thread  
**Cancellable**: No  
**Mutable**: No (read-only)

```java
public class PunishmentAppliedEvent implements ModEvent {
    Punishment getPunishment();
}
```

**Use Cases**:
- Send notifications to Discord/webhooks
- Update external dashboards
- Track moderation statistics
- Trigger automated follow-up actions

**Important**: This event guarantees the punishment was **successfully applied**. If pre-apply was cancelled, this won't fire.

### 3. PunishmentExpiredEvent (Observational)

**Fired**: When a temporary punishment expires OR is manually revoked  
**Thread**: Main thread  
**Cancellable**: No  
**Mutable**: No

```java
public class PunishmentExpiredEvent implements ModEvent {
    Punishment getPunishment();
}
```

**Use Cases**:
- Notify staff when bans expire
- Clean up state associated with punishment
- Log expiration for records

**Important**: The `Punishment` object contains the **original** punishment data. For manual unbans, the `actor` field reflects who issued the unban.

## Permanent vs Temporary Punishments

Determined entirely by the `duration` field:

```java
if (punishment.duration() == null) {
    // Permanent punishment (never expires automatically)
} else {
    // Temporary punishment expires after duration
    long expiresAt = System.currentTimeMillis() + punishment.duration().toMillis();
}
```

### Permanent Punishments
- Must be manually revoked via unban/unmute commands
- `duration()` returns `null`
- Stored with `expiresAt = 0` in database

### Temporary Punishments
- Automatically expire after

 duration
- `duration()` returns non-null `Duration`
- Stored with `expiresAt = currentTime + duration` in database

## Mutation & Cancellation Rules

### In PunishmentPreApplyEvent

**Allowed**:
```java
event.setReason("New reason");         // ✓ Allowed
event.setDuration(Duration.ofHours(2)); // ✓ Allowed
event.setSilent(true);                  // ✓ Allowed
event.setCancelled(true);               // ✓ Allowed
```

**Not Allowed** (no setters for these):
```java
// Cannot change:
// - id
// - target
// - actor
// - type
```

**Reason**: Changing `target`, `actor`, or `type` would fundamentally change the punishment's identity. This is disallowed to prevent confusion.

### In PunishmentAppliedEvent

**Allowed**:
```java
punishment.reason();   // ✓ Read-only access
```

**Not Allowed**:
```java
event.setCancelled(true);  // ✗ Not Cancellable
// No mutation methods exist
```

## Example: Complete Punishment Flow

### Issuing a Ban

```java
// 1. Command layer creates Punishment
Punishment punishment = new Punishment(
    UUID.randomUUID(),
    targetUuid,
    staffUuid,
    DefaultPunishmentTypes.BAN,
    Duration.ofDays(7),
    "Griefing spawn",
    false  // not silent
);

// 2. Dispatch PunishmentPreApplyEvent
PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(punishment);
plugin.getEventBus().dispatch(preEvent);

// 3. Check cancellation
if (preEvent.isCancelled()) {
    sender.sendMessage("Ban was cancelled by another plugin");
    return;
}

// 4. Use potentially-modified punishment
punishment = preEvent.getPunishment();

// 5. Apply punishment (write to DB, kick player, etc.)
moderationService.applyPunishment(punishment);

// 6. Dispatch PunishmentAppliedEvent
plugin.getEventBus().dispatch(new PunishmentAppliedEvent(punishment));
```

### Listening for Bans

```java
// Cancel bans on VIP players
bus.register(PunishmentPreApplyEvent.class, event -> {
    if (event.getPunishment().type().id().equals("BAN")) {
        UUID target = event.getPunishment().target();
        if (isVIP(target)) {
            event.setCancelled(true);
        }
    }
});

// Log all applied bans
bus.register(PunishmentAppliedEvent.class, EventPriority.MONITOR, event -> {
    if (event.getPunishment().type().id().equals("BAN")) {
        logger.info("Ban applied: " + event.getPunishment());
    }
});
```

## Silent Punishments

The `silent` flag controls whether the punishment is publicly announced:

```java
if (punishment.silent()) {
    // Don't broadcast to other players
    // Only notify target and staff
} else {
    // Broadcast: "Player was banned for griefing"
}
```

**Use Cases**:
- Secret bans for suspected cheaters
- Staff testing punishments
- Quiet removal of disruptive players

**Note**: Silent punishments are still logged to the database and audit events.

## Storage

Punishments are persisted to SQLite or MySQL with the following schema:

- `id`: INTEGER PRIMARY KEY
- `player_id`: INTEGER (foreign key to players table)
- `type`: TEXT (e.g., "BAN", "MUTE")
- `issuer_uuid`: TEXT
- `reason`: TEXT
- `timestamp`: INTEGER (Unix epoch)
- `expires_at`: INTEGER (Unix epoch, 0 = permanent)
- `active`: BOOLEAN
- `metadata`: TEXT (optional JSON, for types like JAIL)

See [SCHEMA.md](SCHEMA.md) for full database documentation.
