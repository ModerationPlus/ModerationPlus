# ModerationPlus

**ModerationPlus** is a comprehensive moderation suite for Hytale servers, designed to provide server administrators with robust tools for managing player behavior, enforcing rules, and maintaining a safe community environment.

Built with performance and reliability in mind, ModerationPlus utilizes a local SQLite database with automatic schema migrations to store player data, punishments, and staff notes securely.

## Key Features

- **Advanced Punishment System:** flexible banning, muting, kicking, and warning capabilities.
- **Temporary Punishments:** Support for temporary bans and mutes with automatic expiration.
- **Jail System:** Imprison players in a designated location with containment enforcement.
- **Freeze System:** Immobilize players for inspection or while screensharing.
- **Vanish Mode:** Allow staff to observe players invisibly without alerting them.
- **Chat Management:** Global chat lockdown and dedicated Staff Chat channel.
- **Player History:** View comprehensive punishment history and staff notes for any player.
- **Data Persistence:** Robust SQLite storage with automatic data flushing and backups.
- **Localization:** Per-player language settings with support for custom translations.
- **Configuration:** Simple JSON configuration for customization.

## Quick Start

1.  **Download** the latest `ModerationPlus.jar`.
2.  **Place** the jar file into your Hytale server's `mods` directory.
3.  **Restart** the server.
4.  **Configure** permissions (see [Docs/PERMISSIONS.md](Docs/PERMISSIONS.md)).
5.  **Set up** the jail location using `/setjail`.

## Documentation

- [**Commands**](Docs/COMMANDS.md): Full list of commands and usage.
- [**Permissions**](Docs/PERMISSIONS.md): Permission nodes for staff ranks.
- [**Configuration**](Docs/CONFIGURATION.md): How to configure the plugin.
- [**Databases**](Docs/DATABASES.md): Database information and schema.
- [**Features**](Docs/FEATURES.md): Detailed explanation of game mechanics.

## Reporting Issues

If you encounter any bugs or would like to request a feature, please open an issue on our [Issue Tracker](#).
