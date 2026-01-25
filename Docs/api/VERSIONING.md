# Versioning & Compatibility

## Semantic Versioning

ModerationPlus follows [Semantic Versioning 2.0.0](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

### Version Components

- **MAJOR** (1.x.x): Breaking API changes
- **MINOR** (x.1.x): New features, backward-compatible
- **PATCH** (x.x.1): Bug fixes, internal optimizations

**Current API Version**: `1.0.0`  
**Current Plugin Version**: `1.2.0` (from `gradle.properties`)

Query programmatically:
```java
String apiVersion = ModerationAPI.getVersion();  // Returns "1.0.0"
```

## API Version vs Plugin Version

ModerationPlus maintains **two separate versions**:

### API Version (1.0.0)
- Represents the **public API contract** stability
- Changes only when the API interface changes
- External plugins depend on this version
- Follows strict SemVer for compatibility guarantees

### Plugin Version (1.1.4)
- Represents the **overall mod version**
- Includes internal implementation changes
- Changes with bug fixes, performance improvements, new commands
- Can change even when API remains stable

**Why separate?**
- Plugin can evolve (1.1.x → 1.2.x) without breaking external plugins
- API stability is independent of implementation improvements
- External plugins know exactly when they need to update

**Example**:
- Plugin `1.1.0` → `1.1.4`: Bug fixes, no API changes → API stays `1.0.0`
- Plugin `1.2.0`: New event added → API bumps to `1.1.0` (minor change)
- Plugin `2.0.0`: Breaking API change → API bumps to `2.0.0` (major change)

## What Counts as a Breaking Change

Breaking changes **REQUIRE** a major version bump:

### API Signature Changes

```java
// 1.0.0
void register(Class<T> type, Consumer<T> listener);

// 2.0.0 (BREAKING - changed signature)
void register(Class<T> type, BiConsumer<T, Context> listener);
```

### Removed Methods/Classes

```java
// 1.0.0
public class PunishmentPreApplyEvent {
    void setReason(String reason);  // Exists
}

// 2.0.0 (BREAKING - method removed)
public class PunishmentPreApplyEvent {
    // setReason() no longer exists
}
```

### Changed Threading Guarantees

```java
// 1.0.0
@apiNote This event is guaranteed to be fired on the main server thread.

// 2.0.0 (BREAKING - threading contract changed)
@apiNote This event may be fired on any thread.
```

Changing the threading model is a **major** break because external plugins rely on this guarantee.

### Changed Event Behavior

```java
// 1.0.0
// MONITOR listeners cannot change cancellation

// 2.0.0 (BREAKING - behavior change)
// MONITOR listeners CAN now change cancellation
```

### Removed Events

```java
// 1.0.0
PunishmentPreApplyEvent exists

// 2.0.0 (BREAKING - event removed)
// PunishmentPreApplyEvent no longer fires
```

## What is NOT a Breaking Change

These changes can happen in **minor** or **patch** versions:

### Adding New Events

```java
// 1.0.0
// Only PunishmentPreApplyEvent exists

// 1.1.0 (NOT breaking - new event added)
// PunishmentPreApplyEvent still exists
// NEW: PlayerReportEvent added
```

### Adding New Methods

```java
// 1.0.0
public interface EventBus {
    void register(Class<T> type, EventPriority priority, Consumer<T> listener);
}

// 1.1.0 (NOT breaking - new overload added)
public interface EventBus {
    void register(Class<T> type, EventPriority priority, Consumer<T> listener);
    
    // New method
    void register(Class<T> type, Consumer<T> listener);  // Default priority
}
```

### Adding New Fields to Events

```java
// 1.0.0
public class StaffChatEvent {
    String getMessage();
    void setMessage(String message);
}

// 1.1.0 (NOT breaking - new field added)
public class StaffChatEvent {
    String getMessage();
    void setMessage(String message);
    
    // New getter
    String getChannelId();  // Can return null for old events
}
```

### Internal Implementation Changes

Changing how `SyncEventBus` is implemented internally is **NOT** breaking as long as the `EventBus` interface contract is honored.

### Bug Fixes

Fixing incorrect behavior is **NOT** breaking:

```java
// 1.0.0 - BUG: MONITOR listeners can modify events
// 1.0.1 - FIX: MONITOR listeners now correctly prevented from modifying
```

This is a **patch** because the original behavior was a bug, not a feature.

## @Since Annotation

The `@Since` annotation marks when an API element was introduced:

```java
@Since("1.0.0")
public interface ModerationAPI {
    static String getVersion();
}
```

### Usage

```java
@Since("1.0.0")  // Available since version 1.0.0
public class PunishmentPreApplyEvent {
    
    @Since("1.0.0")
    void setReason(String reason);
    
    @Since("1.2.0")  // Added in version 1.2.0
    void setNote(String note);
}
```

### Checking Compatibility

```java
// Your plugin's build.gradle.kts
dependencies {
    compileOnly("me.almana:moderationplus:1.0.0")
}

// At runtime, verify version
String requiredVersion = "1.2.0";
String apiVersion = ModerationAPI.getVersion();

if (!isCompatible(apiVersion, requiredVersion)) {
    throw new IllegalStateException(
        "Requires ModerationPlus API " + requiredVersion + " but found " + apiVersion
    );
}
```

## @DeprecatedSince Annotation

Marks when an API element was deprecated:

```java
@Since("1.0.0")
@DeprecatedSince("1.5.0")  // Deprecated in 1.5.0
@Deprecated  // Standard Java deprecation
public void oldMethod() {
    // Will be removed in 2.0.0
}
```

### Deprecation Policy

1. Element is marked `@DeprecatedSince("x.y.z")` in minor version
2. Element remains functional for **at least one major version**
3. Element is removed in next major version

**Example Timeline**:
- `1.5.0`: Method deprecated
- `1.6.0`, `1.7.0`, `1.8.0`: Method still works, warning issued
- `2.0.0`: Method removed

## Threading Contract Stability

The threading guarantee is **part of the API contract**:

```java
/**
 * @apiNote This event is guaranteed to be fired on the main server thread.
 */
```

**This means**:
- External plugins can safely access world state in handlers
- No synchronization needed between listeners
- Blocks the calling thread (predictable execution order)

Changing this to asynchronous dispatch would be a **MAJOR** version change.

## Compatibility Testing

External plugins should test against multiple API versions:

```java
// Test matrix
compileOnly("me.almana:moderationplus:1.0.0")  // Minimum supported
compileOnly("me.almana:moderationplus:1.5.0")  // Current stable
compileOnly("me.almana:moderationplus:2.0.0-SNAPSHOT")  // Next major (if available)
```

## Event Schema Stability

Event field types are **stable across minor versions**:

```java
// 1.0.0
String getReason();

// 1.1.0
String getReason();  // Type unchanged

// 2.0.0 (BREAKING if type changed)
ReasonObject getReason();  // Type changed to custom object
```

Changing return types is **breaking**.

## Behavioral Consistency

Event dispatch rules are **stable across minor versions**:

| Rule | Stability | Version Impact |
|------|-----------|----------------|
| Priority ordering (LOWEST → MONITOR) | Guaranteed | Breaking if changed |
| MONITOR cannot cancel | Guaranteed | Breaking if changed |
| Synchronous dispatch | Guaranteed | Breaking if changed |
| Exception isolation | Guaranteed | Breaking if changed |

## Forward Compatibility

New minor versions are **forward compatible**:

```java
// Plugin compiled against 1.0.0
dependencies {
    compileOnly("me.almana:moderationplus:1.0.0")
}

// Runs on server with 1.5.0
// ✓ All 1.0.0 APIs still work
// ✓ New 1.5.0 features not available (compile error if used)
```

## Backward Compatibility

Server downgrades are **NOT supported**:

```java
// Plugin compiled against 1.5.0
dependencies {
    compileOnly("me.almana:moderationplus:1.5.0")
}

// Runs on server with 1.0.0
// ✗ May crash if plugin uses 1.5.0 features
// ✗ Runtime NoSuchMethodError possible
```

**Best Practice**: Specify minimum required version in your plugin metadata.

## Deprecation Warnings

When using deprecated methods:

```java
@DeprecatedSince("1.5.0")
public void oldMethod() {
    // Compiler warning:
    // The method oldMethod() from ModerationAPI is deprecated since 1.5.0
}
```

**Action**: Migrate to the recommended alternative (see Javadoc).

## Version Negotiation

At runtime, check compatibility:

```java
public class MyModerationPlugin extends JavaPlugin {
    
    @Override
    public void setup() {
        String apiVersion = ModerationAPI.getVersion();
        
        // Parse version
        String[] parts = apiVersion.split("\\.");
        int major = Integer.parseInt(parts[0]);
        
        if (major < 1) {
            logger.error("Requires ModerationPlus 1.x, found " + apiVersion);
            return;
        }
        
        if (major > 1) {
            logger.warn("Compiled for 1.x, running on " + apiVersion + " - may have issues");
        }
        
        // Proceed with registration
    }
}
```

## API Stability Promise

For versions `1.x.x`:
- **Events**: All existing events will remain functional
- **Signatures**: Method signatures will not change
- **Threading**: Main thread guarantee will not change
- **Behavior**: Event dispatch rules will not change

Breaking these promises **requires** version `2.0.0`.
