# Commands Reference

This document lists all commands available in **ModerationPlus**, along with their syntax, usage, and required permissions.

## Moderation Commands

### Ban
Permanently ban a player from the server.
- **Syntax:** `/ban <player> [reason]`
- **Permission:** `moderation.ban`
- **Example:** `/ban Steve Hacking`

### TempBan
Temporarily ban a player for a specified duration.
- **Syntax:** `/tempban <player> <duration> [reason]`
- **Permission:** `moderation.tempban`
- **Example:** `/tempban Steve 7d X-Ray`

### Unban
Unban a previously banned player.
- **Syntax:** `/unban <player>`
- **Permission:** `moderation.unban`
- **Example:** `/unban Steve`

### Kick
Kick a player from the server.
- **Syntax:** `/kick <player> [reason]`
- **Permission:** `moderation.kick`
- **Example:** `/kick Steve AFK`

### Mute
Permanently mute a player in chat.
- **Syntax:** `/mute <player> [reason]`
- **Permission:** `moderation.mute`
- **Example:** `/mute Steve Toxic behavior`

### TempMute
Temporarily mute a player in chat.
- **Syntax:** `/tempmute <player> <duration> [reason]`
- **Permission:** `moderation.tempmute`
- **Example:** `/tempmute Steve 1h Spam`

### Unmute
Unmute a player.
- **Syntax:** `/unmute <player>`
- **Permission:** `moderation.unmute`
- **Example:** `/unmute Steve`

### Warn
Issue a warning to a player.
- **Syntax:** `/warn <player> [reason]`
- **Permission:** `moderation.warn`
- **Example:** `/warn Steve Please stop spamming`

### Freeze
Freeze a player in their current location. They will be teleported back if they try to move.
- **Syntax:** `/freeze <player>`
- **Permission:** `moderation.freeze`
- **Example:** `/freeze Steve`

### Unfreeze
Unfreeze a player.
- **Syntax:** `/unfreeze <player>`
- **Permission:** `moderation.unfreeze`
- **Example:** `/unfreeze Steve`

### Jail
Send a player to the defined jail location. Optionally specify duration and reason.
- **Syntax:** `/jail <player> [duration] [reason]` or `/jail <player> [reason] [duration]`
- **Permission:** `moderation.jail`
- **Examples:** 
  - `/jail Steve` - Permanent jail, no reason
  - `/jail Steve 10m Griefing` - 10 minute jail
  - `/jail Steve Griefing 10m` - Same as above (duration can be first or last)

### Unjail
Release a player from jail.
- **Syntax:** `/unjail <player>`
- **Permission:** `moderation.unjail`
- **Example:** `/unjail Steve`

### SetJail
Set the jail location to your current position.
- **Syntax:** `/setjail`
- **Permission:** `moderation.setjail`
- **Example:** `/setjail`

## Administrative Commands

### StaffVanish
Toggle invisibility mode. While vanished, you are hidden from other players, and your chat messages are redirected to staff notifications.
- **Syntax:** `/staffvanish`
- **Permission:** `moderation.vanish`
- **Example:** `/staffvanish`

### Staff Chat
Send a message to the staff-only chat channel.
- **Syntax:** `/schat <message>`
- **Permission:** `moderation.staffchat.send`
- **Example:** `/schat Watch out for Steve`

### Chat Lockdown
Toggle global chat lockdown. When enabled, only staff can speak in public chat.
- **Syntax:** `/chatlockdown`
- **Permission:** `moderation.chatlockdown`
- **Example:** `/chatlockdown`

### History
View a player's punishment history.
- **Syntax:** `/history <player>`
- **Permission:** `moderation.history`
- **Example:** `/history Steve`

### Note
Add a staff note to a player.
- **Syntax:** `/note <player> <message>`
- **Permission:** `moderation.note`
- **Example:** `/note Steve Suspected of using macro`

### Notes
View staff notes for a player.
- **Syntax:** `/notes <player>`
- **Permission:** `moderation.notes`
- **Example:** `/notes Steve`

### Report
Report a player to online staff. Available to all players (no permission required).
- **Syntax:** `/report <player> <reason>`
- **Permission:** None (all players can report)
- **Staff Receive Permission:** `moderation.report.receive` (staff need this to see reports)
- **Example:** `/report Steve Killaura`
- **Cooldown:** 30 seconds between reports

### FlushDB
Manually flush the database changes to disk.
- **Syntax:** `/flushdb`
- **Permission:** `moderation.flush`
- **Example:** `/flushdb`

### Language
Manage language settings for yourself.
- **Syntax:** `/language [list|set|reset] [locale]`
- **Permission:** `moderation.language`
- **Subcommands:**
  - `list` - List available languages
  - `set <locale>` - Set your preferred language
  - `reset` - Reset your language to server default
- **Example:** `/language set es_ES`

### ModerationPlus
Admin commands for the plugin.
- **Syntax:** `/moderationplus <subcommand>`
- **Permission:** `moderation.admin`
- **Subcommands:**
  - `lang reload` - Reload language files
- **Example:** `/moderationplus lang reload`
