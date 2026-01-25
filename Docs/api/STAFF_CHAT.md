# Staff Chat System

## What is Staff Chat?

Staff chat provides **isolated communication channels** for server moderators. It is **NOT** related to player chat events (`PlayerChatEvent`).

Key characteristics:
- **Sender-initiated**: Staff type `/sc <message>` to send
- **Permission-gated**: Requires channel permission to see messages
- **Event-driven**: Full event lifecycle with cancellation
- **Extensible**: Custom channels can be registered

## ChatChannel Abstraction

A `ChatChannel` defines a communication channel:

```java
public interface ChatChannel {
    String id();                             // e.g., "staff"
    String permission();                     // e.g., "mod.staff.chat"
    String format();                         // e.g., "[SC] %s: %s"
    Set<UUID> resolveRecipients(UUID sender); // Who receives messages
}
```

### Default Staff Channel

ModerationPlus registers one default channel:

| Field | Value |
|-------|-------|
| ID | `"staff"` |
| Permission | `"mod.staff.chat"` |
| Format | `"[SC] %s: %s"` (name, message) |
| Recipients | All online players with the permission |

## Channel Registry

Channels are registered at plugin startup:

```java
public interface ChatChannelRegistry {
    void register(ChatChannel channel);
    Optional<ChatChannel> getChannel(String id);
    Collection<ChatChannel> getAllChannels();
}
```

### Custom Channels

Plugins can register additional channels:

```java
ChatChannel adminChannel = new CustomChatChannel(
    "admin",
    "mod.admin.chat",
    "[ADMIN] %s: %s",
    sender -> getOnlineAdmins()  // Custom recipient logic
);

plugin.getChatChannelRegistry().register(adminChannel);
```

## StaffChatEvent Lifecycle

### Construction

Event is created **BEFORE** recipient resolution:

```java
// In StaffChatCommand.execute()
StaffChatEvent event = new StaffChatEvent(
    senderUuid,
    channel,
    message,
    new HashSet<>()  // Recipients NOT yet resolved
);
```

### Event Flow

```
[Command: /sc Hello team]
    ↓
1. Look up channel ("staff")
    ↓
2. Create StaffChatEvent (recipients = empty)
    ↓
3. Dispatch event on MAIN thread
    ↓
4. Listeners can:
   - Cancel (no message sent)
   - Modify message text
   - Modify recipient set
    ↓
5. If not cancelled:
   - Resolve recipients (channel.resolveRecipients())
   - Merge with event.getRecipients()
   - Call delivery.deliver(event)
    ↓
6. Delivery sends to all recipients
```

### Event Definition

```java
public class StaffChatEvent implements Cancellable {
    UUID getSender();
    ChatChannel getChannel();
    
    String getMessage();
    void setMessage(String message);  // Mutable!
    
    Set<UUID> getRecipients();
    void setRecipients(Set<UUID> recipients);  // Mutable!
    
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
```

## Recipient Resolution Timing

**Critical**: Recipients are resolved **AFTER** the event is dispatched.

```java
// 1. Event dispatched with empty recipients
StaffChatEvent event = new StaffChatEvent(..., new HashSet<>());
bus.dispatch(event);

// 2. Listeners can pre-populate recipients
bus.register(StaffChatEvent.class, e -> {
    e.getRecipients().add(someSpecialUuid);
});

// 3. After event, channel resolves default recipients
if (!event.isCancelled()) {
    Set<UUID> channelRecipients = channel.resolveRecipients(sender);
    event.getRecipients().addAll(channelRecipients);
    
    // 4. Deliver to merged recipient set
    delivery.deliver(event);
}
```

**Why this design?**
- Allows listeners to **add** recipients before resolution
- Allows listeners to **override** recipients entirely (`setRecipients()`)
- Avoids resolving recipients if event is cancelled

## Delivery Abstraction

The `StaffChatDelivery` interface separates **logic** from **transport**:

```java
public interface StaffChatDelivery {
    void deliver(StaffChatEvent event);
}
```

### Default Implementation

```java
public class DefaultStaffChatDelivery implements StaffChatDelivery {
    @Override
    public void deliver(StaffChatEvent event) {
        String formatted = String.format(
           event.getChannel().format(),
            senderName,
            event.getMessage()
        );
        
        Message msgObj = Message.raw(formatted).color(Color.MAGENTA);
        
        // Send to all recipients
        for (UUID uuid : event.getRecipients()) {
            PlayerRef ref = Universe.get().getPlayer(uuid);
            if (ref != null && ref.isValid()) {
                ref.sendMessage(msgObj);
            }
        }
        
        // Always send to console
        ConsoleSender.INSTANCE.sendMessage(msgObj);
        
        // Emit audit event
        // (see AUDIT_EVENTS.md)
    }
}
```

**Key Points**:
- Console **always** receives messages (not in recipient list)
- Offline recipients are skipped
- Formatting happens at delivery time

## Console Identity

The console has a special UUID:

```java
public static final UUID CONSOLE_UUID = 
    UUID.nameUUIDFromBytes("CONSOLE".getBytes());
```

**Usage**:
```java
if (event.getSender().equals(ModerationConstants.CONSOLE_UUID)) {
    senderName = "Console";
}
```

**Important**: Console messages are sent via `/sc` from the server console, not by players.

## Message Mutation

Listeners can modify the message before delivery:

```java
// Censor bad words
bus.register(StaffChatEvent.class, event -> {
    String msg = event.getMessage();
    msg = msg.replaceAll("badword", "****");
    event.setMessage(msg);
});

// Append timestamp
bus.register(StaffChatEvent.class, EventPriority.LOW, event -> {
    String timestamp = LocalTime.now().toString();
    event.setMessage("[" + timestamp + "] " + event.getMessage());
});
```

## Recipient Customization

### Add Recipients

```java
bus.register(StaffChatEvent.class, event -> {
    // Always include the server owner
    event.getRecipients().add(SERVER_OWNER_UUID);
});
```

### Override Recipients

```java
bus.register(StaffChatEvent.class, EventPriority.HIGHEST, event -> {
    // Send ONLY to moderators, ignore channel default
    Set<UUID> moderators = getOnlineModerators();
    event.setRecipients(moderators);
});
```

### Conditional Recipients

```java
bus.register(StaffChatEvent.class, event -> {
    If (event.getMessage().startsWith("!admin")) {
        // Restrict to admins only
        event.setRecipients(getOnlineAdmins());
        event.setMessage(event.getMessage().substring(6).trim());
    }
});
```

## Cancellation

Cancel to prevent message delivery:

```java
bus.register(StaffChatEvent.class, event -> {
    // Prevent sending links in staff chat
    if (event.getMessage().contains("http://")) {
        event.setCancelled(true);
        
        PlayerRef sender = Universe.get().getPlayer(event.getSender());
        if (sender != null) {
            sender.sendMessage(Message.raw("Links not allowed in staff chat!"));
        }
    }
});
```

## Explicit Non-Goal: PlayerChatEvent

Staff chat is **NOT** integrated with player chat. It is a completely separate system.

**Player Chat** (not handled by ModerationPlus):
- Sent to all players
- Subject to chat filters, cooldowns
- Fires `PlayerChatEvent` (Hytale core)

**Staff Chat** (handled by ModerationPlus):
- Sent only to staff members
- Permission-gated
- Fires `StaffChatEvent` (ModerationPlus API)

Do **NOT** expect `PlayerChatEvent` when a staff member uses `/sc`.

## Example: Complete Flow

```java
// 1. Player types: /sc Server will restart in 5 minutes

// 2. Command creates event
StaffChatEvent event = new StaffChatEvent(
    playerUuid,
    staffChannel,
    "Server will restart in 5 minutes",
    new HashSet<>()
);

// 3. Dispatch event (main thread)
bus.dispatch(event);

// 4. Listener adds admin
bus.register(StaffChatEvent.class, e -> {
    e.getRecipients().add(ADMIN_UUID);
});

// 5. Check cancellation
if (!event.isCancelled()) {
    // 6. Resolve channel recipients
    Set<UUID> defaultRecipients = staffChannel.resolveRecipients(playerUuid);
    // defaultRecipients = {uuid1, uuid2, uuid3} (all staff online)
    
    // 7. Merge
    event.getRecipients().addAll(defaultRecipients);
    // event.getRecipients() = {uuid1, uuid2, uuid3, ADMIN_UUID}
    
    // 8. Deliver
    delivery.deliver(event);
    // Sends to uuid1, uuid2, uuid3, ADMIN_UUID + console
}
```

## Audit Logging

Staff chat messages emit `StaffChatAuditEvent` after successful delivery. See [AUDIT_EVENTS.md](AUDIT_EVENTS.md) for details.
