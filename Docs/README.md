# ModerationPlus Documentation

Complete documentation for ModerationPlus - A comprehensive moderation system for Hytale servers.

**Plugin Version**: 1.2.0  
**API Version**: 1.0.0

## Documentation Structure

### For Plugin Developers (External API)

**[📚 API Documentation](api/README.md)** - Complete developer reference for extending ModerationPlus

The `api/` directory contains everything you need to build plugins that integrate with ModerationPlus:
- Event system and lifecycle
- Punishment management
- Staff actions and chat
- State tracking and audit events
- Complete code examples

### For Server Administrators

- **[Commands Reference](COMMANDS.md)** - All available commands and their usage
- **[Permissions](PERMISSIONS.md)** - Permission nodes and their effects
- **[Configuration](CONFIGURATION.md)** - Config file options
- **[Setup Guide](SETUP_GUIDE.md)** - Installation and initial setup
- **[Features](FEATURES.md)** - Overview of available features

### For Database Administrators

- **[Database Overview](DATABASES.md)** - Supported database types
- **[Database Setup](DATABASE_SETUP.md)** - Configuration instructions
- **[Schema Reference](SCHEMA.md)** - Complete table and column documentation
- **[Database Notes](DATABASE_NOTES.md)** - Implementation details

### For Project Contributors

- **[Licensing](LICENSING.md)** - License information
- **[Publishing](PUBLISHING.md)** - Release and deployment process
- **[Release Checklist](RELEASE_CHECKLIST.md)** - Pre-release verification steps

## Quick Links

### I want to...

**Use ModerationPlus on my server**
→ Start with [Setup Guide](SETUP_GUIDE.md), then review [Commands](COMMANDS.md) and [Permissions](PERMISSIONS.md)

**Build a plugin that integrates with ModerationPlus**
→ Read [API Documentation](api/README.md), especially [API Overview](api/API_OVERVIEW.md) and [Example Usage](api/EXAMPLE_USAGE.md)

**Understand the event system**
→ See [Event System](api/EVENT_SYSTEM.md) and [Threading Model](api/THREADING_MODEL.md)

**Learn about punishments**
→ Review [Punishment System](api/PUNISHMENT_SYSTEM.md) for the complete lifecycle

**Configure the database**
→ Check [Database Setup](DATABASE_SETUP.md) and [Schema Reference](SCHEMA.md)

**Contribute to the project**
→ Review [Publishing](PUBLISHING.md) and [Release Checklist](RELEASE_CHECKLIST.md)

## What is ModerationPlus?

ModerationPlus provides:
- **Player Punishment Management** - Ban, mute, kick, warn with temporary/permanent durations
- **Staff Actions** - Freeze and jail players
- **Staff Communication** - Private staff chat channels
- **State Tracking** - Query player moderation status without database lookups
- **Audit Logging** - Complete audit trail of all moderation actions
- **Extensible API** - Full event-driven API for third-party plugins

## API Highlights

The ModerationPlus API allows external plugins to:
- **Intercept and cancel** moderation actions before they execute
- **Modify parameters** like punishment duration, reason, or recipients
- **Observe outcomes** for logging, metrics, or notifications
- **Track state changes** (when players become muted/frozen/jailed)
- **Integrate audit events** with external systems (Discord, Elasticsearch, etc.)

**All events are dispatched synchronously on the main server thread**, ensuring thread-safety and predictable execution order.

See [API Overview](api/API_OVERVIEW.md) for architecture details.

## Version Information

ModerationPlus maintains two separate versions:

### Plugin Version: 1.1.4
- The overall mod version
- Changes with bug fixes, new features, performance improvements
- Visible in Hytale's mod list

### API Version: 1.0.0
- The public API contract version
- Changes only when API interfaces change
- Used by external plugins for compatibility checks

**Why separate?** The plugin can evolve internally without breaking external plugins. API version follows strict [Semantic Versioning](api/VERSIONING.md).

## Getting Started

### Server Administrators
1. Follow the [Setup Guide](SETUP_GUIDE.md)
2. Configure [database settings](DATABASE_SETUP.md)
3. Review [available commands](COMMANDS.md)
4. Configure [permissions](PERMISSIONS.md) for your staff

### Plugin Developers
1. Add ModerationPlus as a dependency: `compileOnly("me.almana:moderationplus:1.0.0")`
2. Read [API Overview](api/API_OVERVIEW.md) to understand the architecture
3. Review [Event System](api/EVENT_SYSTEM.md) for registration patterns
4. Check [Example Usage](api/EXAMPLE_USAGE.md) for practical code samples
5. Consult specific guides: [Punishments](api/PUNISHMENT_SYSTEM.md), [Staff Chat](api/STAFF_CHAT.md), etc.

## Support

For issues, feature requests, or questions:
- Review the relevant documentation section
- Check [Example Usage](api/EXAMPLE_USAGE.md) for common patterns
- Verify your API version compatibility with [Versioning](api/VERSIONING.md)

## License

See [LICENSING.md](LICENSING.md) for license information.
