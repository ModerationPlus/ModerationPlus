# Audit & Observability Events

## Purpose

Audit events provide **structured, read-only records** of moderation actions for:
- External logging systems (Elasticsearch, Splunk, etc.)
- Analytics and metrics
- Compliance and audit trails
- Webhook notifications (Discord, Slack, etc.)

**Critical**: Audit events are **informational only**. They do **NOT** enforce behavior.

## AuditEvent Interface

All audit events implement this common interface:

```java
public interface AuditEvent extends ModEvent {
    UUID getActor();       // Who performed the action
    UUID getTarget();      // Who was affected (nullable)
    String getAction();    // Action identifier
    Map<String, Object> getMetadata();  // Additional context
}
```

### Threading Guarantee

Like all ModerationPlus events:
```java
/**
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
```

## Event Types

### 1. PunishmentAuditEvent

**Fired**: When punishments are applied or expired  
**Action IDs**:
- `"PUNISH_APPLIED"` - Punishment was successfully applied
- `"PUNISH_EXPIRED"` - Punishment was revoked/expired

**Metadata**:
```java
{
    "type": "BAN",           // Punishment type
    "reason": "Griefing",    // Why it was issued
    "duration": 86400000,    // Milliseconds (null = permanent)
    "silent": false          // Whether broadcast was suppressed
}
```

**Example**:
```java
bus.register(PunishmentAuditEvent.class, EventPriority.MONITOR, event -> {
    if (event.getAction().equals("PUNISH_APPLIED")) {
        Map<String, Object> meta = event.getMetadata();
        
        sendToElasticsearch(Map.of(
            "timestamp", System.currentTimeMillis(),
            "actor", event.getActor().toString(),
            "target", event.getTarget().toString(),
            "type", meta.get("type"),
            "reason", meta.get("reason"),
            "duration_ms", meta.get("duration")
        ));
    }
});
```

### 2. StaffActionAuditEvent

**Fired**: When staff actions (freeze, jail, etc.) are executed  
**Action IDs**:
- `"STAFF_FREEZE"` - Player was frozen
- `"STAFF_UNFREEZE"` - Player was unfrozen
- `"STAFF_JAIL"` - Player was jailed
- `"STAFF_UNJAIL"` - Player was unjailed

**Metadata**:
```java
{
    "source": "COMMAND",       // "COMMAND" or "GUI"
    "duration": 600000,        // For JAIL (milliseconds)
    "reason": "Griefing spawn" // For JAIL
}
```

**Important**: StaffActionAuditEvent fires **only if the intent event was NOT cancelled**.

```
StaffJailEvent dispatched
    ↓
Listener cancels it
    ↓
NO StaffActionAuditEvent
    (action was prevented, nothing to audit)
```

### 3. StaffChatAuditEvent

**Fired**: After successful staff chat delivery  
**Action ID**: `"STAFF_CHAT"`

**Metadata**:
```java
{
    "channel": "staff",           // Channel ID
    "message": "Hello team",      // Final message text
    "recipient_count": 5          // How many players received it
}
```

**Target**: Always `null` (staff chat targets a group, not a single player)

**Example**:
```java
bus.register(StaffChatAuditEvent.class, EventPriority.MONITOR, event -> {
    Map<String, Object> meta = event.getMetadata();
    
    // Log to file
    auditLogger.info(
        "[STAFF_CHAT] {} sent to {} recipients in channel {}: {}",
        event.getActor(),
        meta.get("recipient_count"),
        meta.get("channel"),
        meta.get("message")
    );
});
```

## Intent vs Result Auditing

ModerationPlus uses a **dual auditing strategy**:

### Staff Actions: Intent Auditing

Staff actions (freeze, jail) are audited at the **intent** level:

```
[Staff types /jail player]
    ↓
StaffJailEvent (Intent - what staff tried to do)
    ↓ (if not cancelled)
StaffActionAuditEvent (STAFF_JAIL)
```

**Why?**
- Captures the staff member's action
- Records unsuccessful attempts (via MONITOR on StaffJailEvent)
- Distinguishes between "tried to jail" vs "punishment created"

### Punishments: Result Auditing

Regular punishments (ban, mute) are audited at the **result** level:

```
[Punishment created]
    ↓
PunishmentPreApplyEvent (Mutable)
    ↓ (if not cancelled)
Database write + enforcement
    ↓
PunishmentAppliedEvent (Confirmation)
    ↓
PunishmentAuditEvent (PUNISH_APPLIED)
```

**Why?**
- Captures what actually happened (after mutations)
- Ensures audit matches database state
- Includes automated punishments (not just staff commands)

### Hybrid: Jail Action

Jailing triggers **both** types of audits:

```
/jail command
    ↓
StaffJailEvent (Intent)
    ↓
StaffActionAuditEvent (STAFF_JAIL) ← Records staff action
    ↓
PunishmentPreApplyEvent (type=JAIL)
    ↓
PunishmentAppliedEvent (type=JAIL)
    ↓
PunishmentAuditEvent (PUNISH_APPLIED) ← Records punishment
```

**This is intentional**:
- `STAFF_JAIL` = "Staff member X jailed player Y"
- `PUNISH_APPLIED` = "Punishment record created in database"

Both provide different perspectives for complete auditability.

## Metadata Guarantees

The `metadata` map is **read-only** for listeners:

```java
Map<String, Object> meta = event.getMetadata();

// ✓ Allowed (read)
String reason = (String) meta.get("reason");

// ✗ NOT recommended (modifies internal state)
meta.put("custom_field", "value");
```

**Warning**: While you can technically modify the map, **don't**. It's meant for consumption, not mutation. Future versions may make it unmodifiable.

### Metadata Contents

Each event type has **specific metadata fields**:

#### PunishmentAuditEvent
- `type` (String): Punishment type ID
- `reason` (String): Why punishment was issued
- `duration` (Long, nullable): Milliseconds, null = permanent
- `silent` (Boolean): Whether broadcast was suppressed

#### StaffActionAuditEvent
- `source` (String): "COMMAND" or "GUI"
- `duration` (Long, optional): For JAIL actions only
- `reason` (String, optional): For JAIL actions only

#### StaffChatAuditEvent
- `channel` (String): Channel ID
- `message` (String): Final message text (after mutations)
- `recipient_count` (Integer): Number of recipients (excludes console)

**Fields are NOT guaranteed to exist** for all event instances. Always check:

```java
Object duration = meta.get("duration");
if (duration != null && duration instanceof Long) {
    long durationMs = (Long) duration;
    // Use it
}
```

## Non-Enforcement Rule

**CRITICAL**: Audit events do **NOT** affect system behavior.

```java
// ✗ WRONG - Don't use audit events for logic
bus.register(PunishmentAuditEvent.class, event -> {
    if (event.getAction().equals("PUNISH_APPLIED")) {
        // DON'T try to reverse the punishment here
        // DON'T modify game state
        // DON'T trigger other punishments
    }
});

// ✓ CORRECT - Use for observation only
bus.register(PunishmentAuditEvent.class, EventPriority.MONITOR, event -> {
    // Log it
    // Send webhook
    // Update metrics
    // Record to external system
});
```

**If you need to affect behavior**, use the **earlier events**:
- `PunishmentPreApplyEvent` to cancel/modify punishments
- `StaffActionEvent` to cancel staff actions
- `StaffChatEvent` to cancel/modify messages

Audit events exist **after decisions are made**.

##Example: Complete Audit Pipeline

```java
public class AuditLogger {
    
    public void init(EventBus bus) {
        // Register at MONITOR priority (read-only)
        bus.register(PunishmentAuditEvent.class, EventPriority.MONITOR, this::onPunishment);
        bus.register(StaffActionAuditEvent.class, EventPriority.MONITOR, this::onStaffAction);
        bus.register(StaffChatAuditEvent.class, EventPriority.MONITOR, this::onStaffChat);
    }
    
    private void onPunishment(PunishmentAuditEvent event) {
        AuditRecord record = AuditRecord.builder()
            .timestamp(Instant.now())
            .category("PUNISHMENT")
            .action(event.getAction())
            .actor(event.getActor())
            .target(event.getTarget())
            .metadata(event.getMetadata())
            .build();
            
        // Async write to database
        CompletableFuture.runAsync(() -> auditDb.insert(record));
    }
    
    private void onStaffAction(StaffActionAuditEvent event) {
        // Send to Discord webhook
        String message = String.format(
            "**%s** by <@%s> on <@%s>",
            event.getAction(),
            event.getActor(),
            event.getTarget()
        );
        
        discordWebhook.send(message, event.getMetadata());
    }
    
    private void onStaffChat(StaffChatAuditEvent event) {
        // Track metrics
        metrics.increment("staff_chat.messages", Map.of(
            "channel", event.getMetadata().get("channel")
        ));
    }
}
```

## Emission Timing

Audit events are emitted **after the action completes**:

```
1. Action initiated
2. Validation
3. Pre-apply event (cancellable)
4. ✓ Action executed
5. Applied event (confirmation)
6. → AUDIT EVENT FIRES HERE
```

This guarantees that audited actions **actually happened**.

## Relationship to Logging

Audit events are **not a replacement** for traditional logging:

| Audit Events | Traditional Logging |
|--------------|---------------------|
| Structured data | Unstructured text |
| For external systems | For developers |
| User actions only | Everything (errors, debug, etc.) |
| Guaranteed schema | Freeform messages |

**Use both**:
- Audit events for business-critical actions
- Traditional logging for debugging and errors
