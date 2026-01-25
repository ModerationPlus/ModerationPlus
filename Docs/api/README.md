# ModerationPlus API Documentation

## For External Plugin Developers

This directory contains complete documentation for the ModerationPlus public API.

**API Version**: 1.0.0  
**Plugin Version**: 1.2.0

## Quick Start

1. **[API Overview](API_OVERVIEW.md)** - Start here to understand the system
2. **[Example Usage](EXAMPLE_USAGE.md)** - Practical code examples
3. **[Event System](EVENT_SYSTEM.md)** - How to register and handle events

## Core Concepts

### Architecture
- **[API Overview](API_OVERVIEW.md)** - What ModerationPlus is, architecture, design philosophy
- **[Threading Model](THREADING_MODEL.md)** - Main thread guarantee, async patterns
- **[Versioning](VERSIONING.md)** - SemVer policy, compatibility rules

### Event System
- **[Event System](EVENT_SYSTEM.md)** - EventBus, priorities, cancellation, MONITOR rules
- **[Punishment System](PUNISHMENT_SYSTEM.md)** - Lifecycle events, mutation, types
- **[Staff Actions](STAFF_ACTIONS.md)** - Intent vs execution, command interception
- **[Staff Chat](STAFF_CHAT.md)** - Channels, recipient resolution, delivery
- **[Moderation State](MODERATION_STATE.md)** - State tracking, change events
- **[Audit Events](AUDIT_EVENTS.md)** - Structured logging, observability

### Practical Guides
- **[Example Usage](EXAMPLE_USAGE.md)** - Complete code examples for common tasks

## Event Reference

### Cancellable Events (Can Prevent Actions)

| Event | When | What You Can Do |
|-------|------|-----------------|
| `PunishmentPreApplyEvent` | Before punishment applied | Cancel, modify reason/duration/silent |
| `StaffChatEvent` | Before staff message sent | Cancel, modify message, change recipients |
| `StaffJailEvent` | Staff attempts to jail | Cancel before punishment created |
| `StaffFreezeEvent` | Staff attempts to freeze | Cancel freeze action |
| `StaffUnfreezeEvent` | Staff attempts to unfreeze | Cancel unfreeze action |
| `StaffUnjailEvent` | Staff attempts to unjail | Cancel unjail action |

### Observational Events (Read-Only)

| Event | When | What You Can Do |
|-------|------|-----------------|
| `PunishmentAppliedEvent` | After punishment applied | Log, notify, track metrics |
| `PunishmentExpiredEvent` | Punishment expires/revoked | Log, cleanup, notify |
| `PlayerModerationStateChangeEvent` | State changes (mute/freeze/jail) | Update UI, log transitions |
| `PunishmentAuditEvent` | Punishment applied/expired | External logging, analytics |
| `StaffActionAuditEvent` | Staff action executed | Audit trail, compliance |
| `StaffChatAuditEvent` | Staff chat sent | Message logging, monitoring |

## Priority Levels

```
LOWEST   (0) → First to execute, basic validation
LOW      (1) → Permission checks
NORMAL   (2) → Default for most plugins
HIGH     (3) → Override previous decisions
HIGHEST  (4) → Final modifications
MONITOR  (5) → Last to execute, read-only, always runs
```

## Key Interfaces

### EventBus
```java
<T extends ModEvent> void register(Class<T> type, Consumer<T> listener);
<T extends ModEvent> void register(Class<T> type, EventPriority priority, Consumer<T> listener);
<T extends ModEvent> void register(Class<T> type, EventPriority priority, boolean ignoreCancelled, Consumer<T> listener);
void dispatch(ModEvent event);
```

### ModerationState
```java
boolean isMuted();
boolean isFrozen();
boolean isJailed();
Optional<Punishment> getActive(PunishmentType type);
```

### ChatChannel
```java
String id();
String permission();
String format();
Set<UUID> resolveRecipients(UUID sender);
```

## Punishment Types

| Type | ID | Effect | Temporary? |
|------|-----|--------|------------|
| Ban | `BAN` | Player cannot join | Yes |
| Mute | `MUTE` | Cannot send chat | Yes |
| Kick | `KICK` | Disconnect immediately | No |
| Warn | `WARN` | Record only | No |
| Jail | `JAIL` | Teleport + freeze | Yes |
| Freeze | `FREEZE` | Cannot move/interact | Yes |

## Constants

```java
ModerationConstants.CONSOLE_UUID  // UUID for console actor
```

## Threading Guarantee

**ALL events are dispatched synchronously on the main server thread.**

This means:
- ✓ Safe to access world state
- ✓ No synchronization needed between listeners
- ✓ Predictable execution order
- ✗ Don't block with I/O operations

See [Threading Model](THREADING_MODEL.md) for details.

## Compatibility

ModerationPlus follows **strict Semantic Versioning**:
- **1.x.x → 1.y.x**: Backward compatible (safe to upgrade)
- **1.x.x → 2.0.0**: Breaking changes (review migration guide)

Use `@Since` annotations to check feature availability:
```java
@Since("1.0.0")  // Available since API 1.0.0
public interface ModerationState { ... }
```

See [Versioning](VERSIONING.md) for the complete policy.

## Getting Help

- Review [Example Usage](EXAMPLE_USAGE.md) for common patterns
- Check event flow diagrams in topic-specific guides
- Ensure you're using the correct event priority
- Verify threading assumptions (all events = main thread)

## Important Rules

### DO:
- ✓ Use `EventPriority.MONITOR` for read-only observation
- ✓ Offload expensive work to async threads
- ✓ Handle exceptions in your listeners
- ✓ Check `@Since` annotations for compatibility

### DON'T:
- ✗ Block the main thread with I/O
- ✗ Modify events in MONITOR listeners
- ✗ Rely on execution order within same priority level
- ✗ Throw unchecked exceptions

## API Stability Promise

For all `1.x.x` versions:
- Event signatures will NOT change
- Threading guarantees will NOT change
- Event dispatch rules will NOT change
- Existing events will remain functional

Breaking these promises requires version `2.0.0`.
