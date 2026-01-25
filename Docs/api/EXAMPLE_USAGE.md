# Example Usage

## Overview

This document demonstrates how external plugins can use the ModerationPlus API. The examples are based on conceptual usage patterns, as the reference implementation was removed due to Hytale API limitations.

## Setup

### 1. Add Dependency

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("me.almana:moderationplus:1.0.0")
}
```

### 2. Obtain Plugin Instance

```java
public class MyModerationPlugin extends JavaPlugin {
    private ModerationPlus moderationPlus;
    
    @Override
    public void setup() {
        // Obtain ModerationPlus instance via PluginManager
        // (Exact method depends on Hytale's plugin discovery API)
        this.moderationPlus = /* lookup via PluginManager */;
        
        if (moderationPlus == null) {
            getLogger().error("ModerationPlus not found!");
            return;
        }
        
        registerListeners();
    }
}
```

### 3. Access EventBus

```java
EventBus bus = moderationPlus.getEventBus();
```

## Example 1: Cancellation

### Prevent Banning Admins

```java
public void registerListeners() {
    EventBus bus = moderationPlus.getEventBus();
    
    bus.register(PunishmentPreApplyEvent.class, event -> {
        Punishment punishment = event.getPunishment();
        
        // Check if it's a ban
        if (punishment.type().id().equals("BAN")) {
            UUID target = punishment.target();
            
            // Check if target is an admin
            if (hasPermission(target, "admin.immunity")) {
                event.setCancelled(true);
                
                // Notify the staff member
                notifyStaff(punishment.actor(), 
                    "Cannot ban admin players!");
            }
        }
    });
}
```

### Block Jail Commands on VIP Players

```java
bus.register(StaffJailEvent.class, event -> {
    if (isVIP(event.getTarget())) {
        event.setCancelled(true);
        
        // Send message to staff member
        UUID actor = event.getActor();
        sendMessage(actor, "VIP players cannot be jailed!");
    }
});
```

## Example 2: Mutation

### Extend Ban Duration for Repeat Offenders

```java
bus.register(PunishmentPreApplyEvent.class, event -> {
    Punishment p = event.getPunishment();
    
    if (p.type().id().equals("BAN") && p.duration() != null) {
        UUID target = p.target();
        int priorBans = countPriorBans(target);
        
        if (priorBans >= 3) {
            // Double the duration for repeat offenders
            Duration newDuration = p.duration().multipliedBy(2);
            event.setDuration(newDuration);
            
            // Add note to reason
            String newReason = p.reason() + 
                " [AUTO-EXTENDED: " + priorBans + " prior bans]";
            event.setReason(newReason);
        }
    }
});
```

### Censor Sensitive Info in Staff Chat

```java
bus.register(StaffChatEvent.class, event -> {
    String message = event.getMessage();
    
    // Remove IP addresses
    message = message.replaceAll(
        "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b", 
        "[IP REDACTED]"
    );
    
    // Remove UUIDs
    message = message.replaceAll(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        "[UUID REDACTED]"
    );
    
    event.setMessage(message);
});
```

### Auto-Silence Bans on New Players

```java
bus.register(PunishmentPreApplyEvent.class, event -> {
    Punishment p = event.getPunishment();
    
    if (p.type().id().equals("BAN")) {
        UUID target = p.target();
        long accountAge = getAccountAge(target);
        
        // If account is less than 24 hours old, make ban silent
        if (accountAge < Duration.ofHours(24).toMillis()) {
            event.setSilent(true);
        }
    }
});
```

## Example 3: Observation

### Log All Punishments to External System

```java
bus.register(
    PunishmentAppliedEvent.class, 
    EventPriority.MONITOR,  // Read-only
    event -> {
        Punishment p = event.getPunishment();
        
        // Async write to external database
        CompletableFuture.runAsync(() -> {
            externalDb.logPunishment(Map.of(
                "id", p.id().toString(),
                "target", p.target().toString(),
                "actor", p.actor().toString(),
                "type", p.type().id(),
                "reason", p.reason(),
                "timestamp", System.currentTimeMillis()
            ));
        });
    }
);
```

### Send Discord Webhook on Bans

```java
bus.register(
    PunishmentAppliedEvent.class,
    EventPriority.MONITOR,
    event -> {
        Punishment p = event.getPunishment();
        
        if (p.type().id().equals("BAN")) {
            CompletableFuture.runAsync(() -> {
                discordWebhook.send(
                    "**Player Banned**",
                    Map.of(
                        "Target", getUsername(p.target()),
                        "Staff", getUsername(p.actor()),
                        "Reason", p.reason(),
                        "Duration", formatDuration(p.duration())
                    )
                );
            });
        }
    }
);
```

### Track State Changes

```java
bus.register(PlayerModerationStateChangeEvent.class, event -> {
    UUID player = event.getPlayer();
    StateType type = event.getType();
    boolean enabled = event.isEnabled();
    
    // Update UI overlay
    if (type == StateType.MUTE && enabled) {
        showMutedIndicator(player);
    } else if (type == StateType.MUTE && !enabled) {
        hideMutedIndicator(player);
    }
    
    // Log state transition
    logger.info("{} is now {}{}",
        getUsername(player),
        enabled ? "" : "NOT ",
        type.name().toLowerCase()
    );
});
```

## Example 4: Audit Integration

### Send All Audit Events to Elasticsearch

```java
public class ElasticsearchAuditBridge {
    
    public void init(EventBus bus) {
        bus.register(PunishmentAuditEvent.class, 
            EventPriority.MONITOR, this::onAudit);
        bus.register(StaffActionAuditEvent.class, 
            EventPriority.MONITOR, this::onAudit);
        bus.register(StaffChatAuditEvent.class, 
            EventPriority.MONITOR, this::onAudit);
    }
    
    private void onAudit(AuditEvent event) {
        CompletableFuture.runAsync(() -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("@timestamp", Instant.now().toString());
            doc.put("action", event.getAction());
            doc.put("actor", event.getActor().toString());
            
            if (event.getTarget() != null) {
                doc.put("target", event.getTarget().toString());
            }
            
            doc.putAll(event.getMetadata());
            
            elasticsearchClient.index("moderation-audit", doc);
        });
    }
}
```

## Example 5: Custom Recipient Logic

### Add Specific Staff to All Staff Chat

```java
bus.register(StaffChatEvent.class, event -> {
    // Always include the server owner
    UUID ownerUuid = getServerOwnerUuid();
    event.getRecipients().add(ownerUuid);
    
    // If message contains "urgent", add all admins
    if (event.getMessage().toLowerCase().contains("urgent")) {
        event.getRecipients().addAll(getOnlineAdmins());
    }
});
```

### Channel-Specific Recipients

```java
bus.register(StaffChatEvent.class, event -> {
    String channelId = event.getChannel().id();
    
    if (channelId.equals("admin")) {
        // Override recipients - admins only
        event.setRecipients(getOnlineAdmins());
    } else if (channelId.equals("mod")) {
        // Moderators + admins
        Set<UUID> recipients = new HashSet<>();
        recipients.addAll(getOnlineModerators());
        recipients.addAll(getOnlineAdmins());
        event.setRecipients(recipients);
    }
});
```

## Example 6: Multi-Plugin Coordination

### Priority-Based Coordination

```java
// Plugin A: Core permission check (LOW priority)
bus.register(PunishmentPreApplyEvent.class, EventPriority.LOW, event -> {
    if (!hasPermission(event.getPunishment().actor(), "punish.ban")) {
        event.setCancelled(true);
    }
});

// Plugin B: VIP immunity (NORMAL priority)
bus.register(PunishmentPreApplyEvent.class, EventPriority.NORMAL, event -> {
    if (isVIP(event.getPunishment().target())) {
        event.setCancelled(true);
    }
});

// Plugin C: Admin override (HIGHEST priority)
bus.register(PunishmentPreApplyEvent.class, EventPriority.HIGHEST, event -> {
    // Admin can force-ban even VIPs if they include special flag
    if (event.getPunishment().reason().startsWith("[FORCE]")) {
        // Un-cancel if previously cancelled
        event.setCancelled(false);
    }
});
```

## Best Practices

### DO:
1. **Use appropriate priority**
   - `LOW` for permission checks
   - `NORMAL` for most plugins
   - `HIGH/HIGHEST` for overrides
   - `MONITOR` for logging only

2. **Offload async work**
   ```java
   bus.register(SomeEvent.class, event -> {
       // Quick validation (sync)
       if (!shouldProcess(event)) return;
       
       // Expensive work (async)
       CompletableFuture.runAsync(() -> {
           sendWebhook(event);
       });
   });
   ```

3. Handle exceptions
   ```java
   bus.register(SomeEvent.class, event -> {
       try {
           // Your logic
       } catch (Exception e) {
           logger.error("Failed to process event", e);
       }
   });
   ```

### DON'T:
1. **Don't block the thread**
   ```java
   // ✗ BAD
   bus.register(SomeEvent.class, event -> {
       Thread.sleep(5000);  // Blocks main thread!
   });
   ```

2. **Don't modify events in MONITOR**
   ```java
   // ✗ BAD
   bus.register(SomeEvent.class, EventPriority.MONITOR, event -> {
       event.setCancelled(true);  // Reverted by EventBus!
   });
   ```

3. **Don't throw unchecked exceptions**
   ```java
   // ✗ BAD (breaks other listeners)
   bus.register(SomeEvent.class, event -> {
       throw new RuntimeException("oops");
   });
   ```

## Debugging

### Enable Detailed Logging

```java
bus.register(PunishmentPreApplyEvent.class, EventPriority.LOWEST, event -> {
    logger.debug("PunishmentPreApplyEvent: {}", event.getPunishment());
});

bus.register(PunishmentAppliedEvent.class, EventPriority.MONITOR, event -> {
    logger.debug("PunishmentAppliedEvent: {} (was applied)", event.getPunishment());
});
```

### Check Cancellation Source

```java
bus.register(PunishmentPreApplyEvent.class, EventPriority.MONITOR, event -> {
    if (event.isCancelled()) {
        logger.warn("Punishment was cancelled: {}", event.getPunishment());
        // Check which plugin cancelled it (requires instrumentation)
    }
});
```
