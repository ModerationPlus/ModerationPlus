# Event System

## EventBus Interface

The `EventBus` is the central nervous system of ModerationPlus. It provides type-safe event registration and dispatching.

### Registration Methods

```java
public interface EventBus {
    // Full control registration
    <T extends ModEvent> void register(
        Class<T> type,
        EventPriority priority,
        boolean ignoreCancelled,
        Consumer<T> listener
    );
    
    // Simplified registration (ignoreCancelled = false)
    <T extends ModEvent> void register(
        Class<T> type,
        EventPriority priority,
        Consumer<T> listener
    );
    
    // Convenience method (priority = NORMAL, ignoreCancelled = false)
    default <T extends ModEvent> void register(
        Class<T> type,
        Consumer<T> listener
    );
    
    void dispatch(ModEvent event);
}
```

### Type Safety

The EventBus is **strongly typed**. Your listener receives exactly the event type you registered for:

```java
bus.register(PunishmentAppliedEvent.class, event -> {
    // event is PunishmentAppliedEvent, not ModEvent
    Punishment p = event.getPunishment();
});
```

## Event Priority

Listeners are executed in strict priority order:

```java
public enum EventPriority {
    LOWEST(0),    // Runs first
    LOW(1),
    NORMAL(2),    // Default priority
    HIGH(3),
    HIGHEST(4),
    MONITOR(5);   // Runs last, special rules
}
```

### Priority Semantics

- **LOWEST to HIGHEST**: Can modify and cancel events
- **MONITOR**: Read-only observation, cannot cancel

**Example Flow**:
```
LOWEST    → Cancel check, basic validation
LOW       → Permission checks
NORMAL    → Most plugins (default)
HIGH      → Override previous decisions
HIGHEST   → Final modifications before execution
MONITOR   → Logging, metrics (read-only)
```

## Cancellation Semantics

Events implementing `Cancellable` can be cancelled to prevent the action:

```java
public interface Cancellable extends ModEvent {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
```

### Cancellation Rules

1. **Any priority can cancel** (except MONITOR)
2. **Once cancelled, subsequent listeners of the same or lower priority are skipped** (unless `ignoreCancelled = true`)
3. **HIGHEST priority can un-cancel** events
4. **MONITOR listeners always run**, but cannot change cancellation state

### ignoreCancelled Flag

```java
// This listener runs even if event is already cancelled
bus.register(SomeEvent.class, EventPriority.NORMAL, true, event -> {
    // Will execute regardless of cancellation state
});
```

Use cases:
- Logging (need to record cancelled actions)
- Cleanup (must run even if action was prevented)
- Metrics (track both successful and cancelled attempts)

## MONITOR Priority Special Rules

MONITOR listeners have **strict read-only enforcement**:

```java
// In SyncEventBus implementation:
if (reg.priority == EventPriority.MONITOR && event instanceof Cancellable) {
    boolean wasCancelled = ((Cancellable) event).isCancelled();
    listener.accept(event);
    if (((Cancellable) event).isCancelled() != wasCancelled) {
        ((Cancellable) event).setCancelled(wasCancelled); // Reverted!
        logger.warn("MONITOR listener attempted to change cancellation state!");
    }
}
```

**MONITOR listeners CANNOT**:
- Cancel events
- Un-cancel events
- **Their cancellation changes are automatically reverted**

**MONITOR listeners SHOULD**:
- Log events
- Collect metrics
- Update analytics
- Trigger webhooks

**WARNING**: While cancellation is enforced, MONITOR listeners can still modify mutable fields. This is **not recommended** as it defeats the purpose of MONITOR.

## Threading Guarantees

All event dispatch happens **synchronously on the main server thread**. See [THREADING_MODEL.md](THREADING_MODEL.md).

## Event Execution Order

For a single event dispatch:

1. **Sort listeners** by priority (LOWEST → MONITOR)
2. **For each listener**:
   - Check if event is cancelled and listener doesn't ignore cancellations → skip
   - Execute listener
   - If MONITOR, enforce cancellation state
   - Catch and log any exceptions (don't propagate)
3. **Return** to caller

**Exception Handling**: Individual listener exceptions do not stop event propagation. They are logged and execution continues.

## Example: Complete Event Lifecycle

```java
// Initial state
PunishmentPreApplyEvent event = new PunishmentPreApplyEvent(punishment);

// Dispatch
bus.dispatch(event);

// Execution order:
// 1. LOWEST listener → validates basic data
// 2. LOW listener    → checks if player is admin → cancels
// 3. NORMAL listener → (skipped, event cancelled)
// 4. HIGH listener   → (skipped, event cancelled)
// 5. HIGHEST listener→ (skipped, event cancelled)
// 6. MONITOR listener→ runs anyway, logs cancellation

// After dispatch
if (event.isCancelled()) {
    // Don't proceed with punishment
    return;
}
```

## Best Practices

### DO:
- Use NORMAL priority for most plugins
- Use MONITOR for logging only
- Handle exceptions in your listeners
- Keep listeners fast (offload async work)

### DON'T:
- Don't use HIGHEST unless you need final say
- Don't modify events in MONITOR listeners
- Don't block the thread with I/O
- Don't throw unchecked exceptions

### Debugging

If your listener isn't being called:
1. Check event type matches exactly
2. Verify registration happened before dispatch
3. Check if event is being cancelled by higher-priority listener
4. Enable debug logging to see execution order
