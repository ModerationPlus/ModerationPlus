# ModerationPlus API Overview

## What is ModerationPlus?

ModerationPlus is a comprehensive moderation system for Hytale servers that provides:
- Player punishment management (ban, mute, kick, warn)
- Staff-specific actions (freeze, jail/unjail)
- Staff chat channels
- Global chat locking
- Vanish functionality
- Player moderation state tracking
- Audit logging for all moderation actions

## What Problems It Solves

1. **Extensibility**: Third-party plugins can intercept, modify, or observe moderation actions through a well-defined event API
2. **Consistency**: All moderation actions follow the same event lifecycle and threading model
3. **Observability**: Structured audit events provide complete visibility into moderation activities
4. **State Management**: Centralized tracking of player moderation states (muted, frozen, jailed)
5. **Separation of Concerns**: Clear boundary between public API and internal implementation

## High-Level Architecture

ModerationPlus is structured into two distinct modules:

### API Module (`me.almana.moderationplus.api`)
- **Public interfaces** that third-party plugins depend on
- **Event definitions** for all moderation actions
- **Service interfaces** for punishment and state management
- **Data models** (Punishment, ChatChannel, etc.)
- **Zero implementation details** - pure contracts

### Core Module (`me.almana.moderationplus.core`)
- **Concrete implementations** of API interfaces
- **Command handlers** for staff commands
- **Storage integration** (SQLite/MySQL)
- **Internal business logic**
- **NOT exposed to external plugins**

```
┌─────────────────────────────────┐
│   Third-Party Plugin            │
│                                 │
│   depends on API only ↓         │
└─────────────────────────────────┘
           │
           ↓
┌─────────────────────────────────┐
│   API Module                    │
│   • Events                      │
│   • Service Interfaces          │
│   • Data Models                 │
└─────────────────────────────────┘
           │
           ↓ (implements)
┌─────────────────────────────────┐
│   Core Module                   │
│   • EventBus Implementation     │
│   • ModerationService           │
│   • Storage Manager             │
│   • Commands                    │
└─────────────────────────────────┘
```

## Event-Driven Design

All moderation actions flow through an event pipeline:

1. **Intent Event** (Cancellable) - Staff initiates action
2. **Pre-Apply Event** (Cancellable, Mutable) - Before execution
3. **Applied Event** (Observational) - After success
4. **Audit Event** (Informational) - For logging/analytics

External plugins can hook into any stage to:
- Cancel actions
- Modify parameters (reason, duration, etc.)
- Observe outcomes
- Collect metrics

## Explicit Non-Goals

ModerationPlus **does NOT**:
- Provide a web interface (use external web panels)
- Handle player chat events (only staff chat)
- Implement custom economy systems
- Bundle third-party dependencies beyond Hytale API

## Versioning

Current API Version: **1.0.0**

ModerationPlus follows [Semantic Versioning](https://semver.org/):
- **MAJOR**: Breaking API changes (signature changes, removed features)
- **MINOR**: New features, backward-compatible additions
- **PATCH**: Bug fixes, internal optimizations

Use `ModerationAPI.getVersion()` to check the API version programmatically.

## Threading Model

**CRITICAL**: All events are dispatched **synchronously on the main server thread**.

This means:
- Event handlers execute in order of priority
- The calling thread blocks until all listeners complete
- No race conditions between listeners
- Safe to access Hytale world state

See [THREADING_MODEL.md](THREADING_MODEL.md) for detailed guarantees.

## Getting Started

For external plugin developers:
1. Add ModerationPlus as a dependency (API module only)
2. Access the EventBus via `ModerationPlus.getEventBus()`
3. Register listeners for events you care about
4. Use `@Since` annotations to ensure API compatibility

Example:
```java
ModerationPlus plugin = /* obtain via PluginManager */;
plugin.getEventBus().register(PunishmentPreApplyEvent.class, event -> {
    if (event.getPunishment().reason().contains("badword")) {
        event.setCancelled(true);
    }
});
```

See [EXAMPLE_USAGE.md](EXAMPLE_USAGE.md) for complete examples.
