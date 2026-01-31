# Permissions Reference

This document details all permission nodes available in **ModerationPlus**.

## Staff Permissions

| Permission Node | Description | Recommended Rank |
| :--- | :--- | :--- |
| `moderation.ban` | Allow banning players permanently | Admin/Mod |
| `moderation.tempban` | Allow temporarily banning players | Admin/Mod |
| `moderation.unban` | Allow unbanning players | Admin |
| `moderation.kick` | Allow kicking players | Helper/Mod |
| `moderation.mute` | Allow muting players permanently | Helper/Mod |
| `moderation.tempmute` | Allow temporarily muting players | Helper/Mod |
| `moderation.unmute` | Allow unmuting players | Admin/Mod |
| `moderation.warn` | Allow warning players | Helper |
| `moderation.history` | Allow viewing player punishment history | Helper |
| `moderation.note` | Allow adding staff notes | Helper |
| `moderation.notes` | Allow viewing staff notes | Helper |
| `moderation.jail` | Allow jailing players | Mod |
| `moderation.unjail` | Allow releasing players from jail | Mod |
| `moderation.setjail` | Allow setting the jail location | Admin |
| `moderation.freeze` | Allow freezing players | Mod |
| `moderation.unfreeze` | Allow unfreezing players | Mod |
| `moderation.vanish` | Allow toggling vanish mode | Admin/Mod |
| `moderation.vanish.see` | Allow seeing other vanished players | Admin/Mod |
| `moderation.staffchat.send` | Allow sending messages in staff chat | Helper |
| `moderation.chatlockdown` | Allow locking/unlocking global chat | Admin |
| `moderation.flush` | Allow manually flushing the database | Admin |
| `moderation.notify` | Receive staff notifications (bans, mutes, etc.) | Helper |
| `moderation.report.receive` | Receive player reports | Helper |
| `moderation.language` | Allow managing player language settings | Admin |
| `moderation.admin` | Allow reloading plugin configuration/languages | Admin |

## Bypass Permissions

| Permission Node | Description |
| :--- | :--- |
| `moderation.bypass` | Prevents the user from being banned, muted, kicked, or jailed. |
| `moderation.jail.bypass` | Allows the user to leave the jail area without being teleported back. |
| `moderation.freeze.bypass` | Allows the user to move while frozen. |
| `moderation.chatlockdown.bypass` | Allows the user to speak in global chat while it is locked. |
