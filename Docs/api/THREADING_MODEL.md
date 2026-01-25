# Threading Model

## Main Thread Guarantee

**CRITICAL CONTRACT**: All ModerationPlus events are dispatched **synchronously on the main server thread**.

This is an **unbreakable contract** specified in every event's `@apiNote` documentation:

```java
/**
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
```

## What This Means

### For Event Listeners

1. **Synchronous Execution**: When you register a listener, it will be called on the same thread that dispatched the event
2. **Ordered Execution**: Listeners execute in priority order (LOWEST → HIGHEST → MONITOR)
3. **Blocking Behavior**: The dispatching thread waits for all listeners to complete
4. **Safe World Access**: You can safely access Hytale's world state, entities, and components

### For Performance

- **Do NOT** perform long-running operations in event handlers
- **Do NOT** block on I/O operations (network, disk)
- **Do NOT** call `Thread.sleep()` or similar blocking calls

If you need to perform expensive operations:
```java
plugin.getEventBus().register(PunishmentAppliedEvent.class, event -> {
    // Quick validation
    if (!shouldProcessPunishment(event.getPunishment())) {
        return;
    }
    
    // Offload expensive work asynchronously
    CompletableFuture.runAsync(() -> {
        sendWebhook(event.getPunishment());
        updateExternalDatabase(event.getPunishment());
    });
});
```

## Why Async-to-Sync Hopping Exists

You may notice code like this in ModerationService:

```java
CompletableFuture.runAsync(() -> 
    plugin.getEventBus().dispatch(event), 
    HytaleServer.SCHEDULED_EXECUTOR // Main thread executor
);
```

**Reason**: Commands and some services execute on worker threads. To honor the main thread guarantee, we explicitly hop to the main thread before dispatching events.

This pattern ensures:
- Events are **always** on the main thread
- External plugins can rely on the threading contract
- No race conditions when accessing world state

## Event Dispatch Flow

```
[Command Thread]
    ↓
CompletableFuture.runAsync(..., SCHEDULED_EXECUTOR)
    ↓
[Main Thread] ← EventBus.dispatch(event)
    ↓
[Main Thread] → Listener 1 (LOWEST)
    ↓
[Main Thread] → Listener 2 (NORMAL)
    ↓
[Main Thread] → Listener 3 (HIGHEST)
    ↓
[Main Thread] → Listener 4 (MONITOR)
    ↓
[Main Thread] ← Returns to caller
```

## Thread Safety of Event Objects

Event objects (e.g., `PunishmentPreApplyEvent`, `StaffChatEvent`) are:
- **Not thread-safe for concurrent access**
- **Safe for sequential access** (single-threaded dispatch)
- **Mutable** (listeners can modify fields like `reason`, `message`)

Since all listeners execute on the same thread, no synchronization is needed when modifying event fields.

## Breaking This Contract is a Major Version Change

If a future version of ModerationPlus were to dispatch events asynchronously, it would be a **MAJOR version bump** (e.g., 1.x.x → 2.0.0) because:
- External plugins depend on the threading guarantee
- Code that accesses world state would break
- Synchronization would suddenly be required

## Verifying Thread Context

If you need to assert you're on the main thread:

```java
plugin.getEventBus().register(SomeEvent.class, event -> {
    // Hytale provides utilities for this
    if (!HytaleServer.SCHEDULED_EXECUTOR.inExecutionContext()) {
        throw new IllegalStateException("Expected main thread!");
    }
    // ... safe to access world state
});
```

## Internal Implementation Detail

The `SyncEventBus` implementation dispatches events directly in the calling thread:

```java
@Override
public void dispatch(ModEvent event) {
    // No thread hopping - executes in caller's thread
    for (RegisteredListener listener : sorted) {
        listener.accept(event);
    }
}
```

As long as callers invoke `dispatch()` on the main thread, listeners execute on the main thread.
