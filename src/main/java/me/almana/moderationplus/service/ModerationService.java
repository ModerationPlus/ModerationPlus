package me.almana.moderationplus.service;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.InfiniteBan;
import com.hypixel.hytale.server.core.modules.accesscontrol.ban.TimedBan;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager.PlayerData;
import me.almana.moderationplus.utils.TimeUtils;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ModerationService {

    private final ModerationPlus plugin;

    public ModerationService(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Boolean> ban(UUID targetUuid, String targetName, String reason, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.bypass")) {
                    return false;
                }

                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                boolean isOnline = false;
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                    isOnline = true;
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                HytaleBanProvider banProvider = plugin.getBanProvider();
                if (banProvider != null && !banProvider.hasBan(targetUuid)) {
                    InfiniteBan nativeBan = new InfiniteBan(targetUuid, context.issuerUuid(), Instant.now(), reason);
                    banProvider.modify(bans -> {
                        bans.put(targetUuid, nativeBan);
                        return true;
                    });
                }

                List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                        "BAN");
                if (activeBans.isEmpty()) {
                    Punishment ban = new Punishment(0, playerData.id(), "BAN", context.issuerUuid().toString(), reason,
                            System.currentTimeMillis(), 0, true, "{}");
                    plugin.getStorageManager().createPunishment(ban);
                }

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " banned " + resolvedName + " (" + reason + ")")
                                .color(Color.GREEN));

                if (isOnline && ref != null && ref.isValid()) {
                    UUID worldUuid = ref.getWorldUuid();
                    if (worldUuid != null) {
                        World world = Universe.get().getWorld(worldUuid);
                        if (world != null) {
                            ((Executor) world).execute(() -> {
                                if (ref.isValid()) {
                                    ref.getPacketHandler().disconnect("You are permanently banned.\nReason: " + reason);
                                }
                            });
                        }
                    }
                }
                return true;

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> kick(UUID targetUuid, String targetName, String reason,
            ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.bypass")) {
                    return false;
                }

                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref == null || !ref.isValid()) {
                    return false;
                }

                String resolvedName = ref.getUsername();
                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                Punishment kick = new Punishment(0, playerData.id(), "KICK", context.issuerUuid().toString(), reason,
                        System.currentTimeMillis(),
                        0, false, "{}");
                plugin.getStorageManager().createPunishment(kick);

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " kicked " + resolvedName + " (" + reason + ")")
                                .color(Color.GREEN));

                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                ref.getPacketHandler().disconnect("You have been kicked.\nReason: " + reason);
                            }
                        });
                    }
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> mute(UUID targetUuid, String targetName, String reason,
            ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.bypass")) {
                    return false;
                }

                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                List<Punishment> activeMutes = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                        "MUTE");
                if (!activeMutes.isEmpty()) {
                    return false;
                }

                Punishment mute = new Punishment(0, playerData.id(), "MUTE", context.issuerUuid().toString(), reason,
                        System.currentTimeMillis(),
                        0, true, "{}");
                plugin.getStorageManager().createPunishment(mute);

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " muted " + resolvedName + " (" + reason + ")")
                                .color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(
                            Message.raw("You have been permanently muted.\nReason: " + reason).color(Color.RED));
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> unban(UUID targetUuid, String targetName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resolvedName = targetName;
                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                boolean nativeUnbanned = false;
                HytaleBanProvider banProvider = plugin.getBanProvider();
                if (banProvider != null && banProvider.hasBan(targetUuid)) {
                    banProvider.modify(bans -> {
                        bans.remove(targetUuid);
                        return true;
                    });
                    nativeUnbanned = true;
                }

                List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                        "BAN");
                boolean dbUnbanned = false;
                if (!activeBans.isEmpty()) {
                    for (Punishment p : activeBans) {
                        plugin.getStorageManager().deactivatePunishment(p.id());
                    }
                    dbUnbanned = true;
                }

                if (nativeUnbanned || dbUnbanned) {
                    plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " unbanned " + resolvedName)
                            .color(Color.GREEN));
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> unmute(UUID targetUuid, String targetName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                int rows = plugin.getStorageManager().deactivatePunishmentsByType(playerData.id(), "MUTE");

                if (rows > 0) {
                    plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " unmuted " + resolvedName)
                            .color(Color.GREEN));
                    if (ref != null && ref.isValid()) {
                        ref.sendMessage(Message.raw("You have been unmuted.").color(Color.GREEN));
                    }
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> tempBan(UUID targetUuid, String targetName, String reason, long durationMillis,
            ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.bypass")) {
                    return false;
                }

                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                boolean isOnline = false;
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                    isOnline = true;
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);
                long expiresAt = System.currentTimeMillis() + durationMillis;

                HytaleBanProvider banProvider = plugin.getBanProvider();
                if (banProvider != null && !banProvider.hasBan(targetUuid)) {
                    TimedBan nativeBan = new TimedBan(targetUuid, context.issuerUuid(), Instant.now(),
                            Instant.ofEpochMilli(expiresAt), reason);
                    banProvider.modify(bans -> {
                        bans.put(targetUuid, nativeBan);
                        return true;
                    });
                }

                List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                        "BAN");
                if (activeBans.isEmpty()) {
                    Punishment ban = new Punishment(0, playerData.id(), "BAN", context.issuerUuid().toString(), reason,
                            System.currentTimeMillis(), expiresAt, true, "{}");
                    plugin.getStorageManager().createPunishment(ban);
                }

                String durationStr = TimeUtils.formatDuration(durationMillis);
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " temp-banned " + resolvedName
                        + " for " + durationStr + " (" + reason + ")").color(Color.GREEN));

                if (isOnline && ref != null && ref.isValid()) {
                    UUID worldUuid = ref.getWorldUuid();
                    if (worldUuid != null) {
                        World world = Universe.get().getWorld(worldUuid);
                        if (world != null) {
                            ((Executor) world).execute(() -> {
                                if (ref.isValid()) {
                                    ref.getPacketHandler()
                                            .disconnect("You are banned for " + durationStr + ".\nReason: " + reason);
                                }
                            });
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> tempMute(UUID targetUuid, String targetName, String reason, long durationMillis,
            ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.bypass")) {
                    return false;
                }

                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);
                List<Punishment> activeMutes = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                        "MUTE");
                if (!activeMutes.isEmpty()) {
                    return false;
                }

                long expiresAt = System.currentTimeMillis() + durationMillis;
                Punishment mute = new Punishment(0, playerData.id(), "MUTE", context.issuerUuid().toString(), reason,
                        System.currentTimeMillis(), expiresAt, true, "{}");
                plugin.getStorageManager().createPunishment(mute);

                String durationStr = TimeUtils.formatDuration(durationMillis);
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " temp-muted " + resolvedName
                        + " for " + durationStr + " (" + reason + ")").color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(Message.raw("You have been muted for " + durationStr + ".\nReason: " + reason)
                            .color(Color.RED));
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> warn(UUID targetUuid, String targetName, String reason,
            ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);
                Punishment warning = new Punishment(0, playerData.id(), "WARN", context.issuerUuid().toString(), reason,
                        System.currentTimeMillis(), 0, true, null);
                plugin.getStorageManager().createPunishment(warning);

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " warned " + resolvedName + " (" + reason + ")")
                                .color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(Message.raw("You have been warned. Reason: " + reason).color(Color.YELLOW));
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> jail(UUID targetUuid, String targetName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!plugin.getConfigManager().hasJailLocation()) {
                    return false;
                }

                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.jail.bypass")) {
                    return false;
                }

                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                boolean isOnline = (ref != null && ref.isValid());
                if (isOnline) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(targetUuid, resolvedName);

                if (isOnline) {
                    UUID worldUuid = ref.getWorldUuid();
                    if (worldUuid != null) {
                        World world = Universe.get().getWorld(worldUuid);
                        if (world != null) {
                            ((Executor) world).execute(() -> {
                                if (ref.isValid()) {
                                    TransformComponent transform = ref.getReference().getStore()
                                            .getComponent(ref.getReference(), TransformComponent.getComponentType());
                                    String originalLocStr = "";
                                    if (transform != null) {
                                        Vector3d pos = transform.getPosition();
                                        Vector3f rot = transform.getRotation();
                                        originalLocStr = worldUuid.toString() + ":" + pos.x + "," + pos.y + "," + pos.z
                                                + "," + rot.x + "," + rot.y + "," + rot.z;
                                    }

                                    final String finalLoc = originalLocStr;
                                    CompletableFuture.runAsync(() -> {
                                        try {
                                            Punishment punishment = new Punishment(0, playerData.id(), "JAIL",
                                                    context.issuerUuid().toString(), "Jailed by staff",
                                                    System.currentTimeMillis(), 0, true, finalLoc);
                                            plugin.getStorageManager().insertPunishment(punishment);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });

                                    double[] jailLoc = plugin.getConfigManager().getJailLocation();
                                    Vector3d jailPos = new Vector3d(jailLoc[0], jailLoc[1], jailLoc[2]);
                                    Vector3f jailRot = new Vector3f(0, 0, 0);

                                    Teleport teleport = new Teleport(jailPos, jailRot);
                                    ref.getReference().getStore().addComponent(ref.getReference(),
                                            Teleport.getComponentType(), teleport);

                                    plugin.addJailedPlayer(targetUuid, jailPos);
                                    plugin.addFrozenPlayer(targetUuid, jailPos);
                                }
                            });
                        }
                    }
                } else {
                    Punishment punishment = new Punishment(0, playerData.id(), "JAIL", context.issuerUuid().toString(),
                            "Jailed by staff",
                            System.currentTimeMillis(), 0, true, null);
                    plugin.getStorageManager().insertPunishment(punishment);
                }

                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " jailed " + resolvedName + ".")
                        .color(Color.RED));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> unjail(UUID targetUuid, String targetName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resolvedName = targetName;
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid()) {
                    resolvedName = ref.getUsername();
                }

                PlayerData playerData = plugin.getStorageManager().getPlayerByUUID(targetUuid);
                if (playerData == null)
                    return false;

                List<Punishment> jails = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "JAIL");
                String restoreLocData = null;
                if (!jails.isEmpty()) {
                    restoreLocData = jails.get(0).extraData();
                }

                int deactivated = plugin.getStorageManager().deactivatePunishmentsByType(playerData.id(), "JAIL");
                if (deactivated == 0)
                    return false;

                plugin.removeJailedPlayer(targetUuid);
                plugin.removeFrozenPlayer(targetUuid);

                if (restoreLocData != null && !restoreLocData.isEmpty() && ref != null && ref.isValid()) {
                    try {
                        String[] parts = restoreLocData.split(":");
                        if (parts.length == 2) {
                            UUID worldUuid = UUID.fromString(parts[0]);
                            String[] coords = parts[1].split(",");
                            if (coords.length >= 6) {
                                double x = Double.parseDouble(coords[0]);
                                double y = Double.parseDouble(coords[1]);
                                double z = Double.parseDouble(coords[2]);
                                float yaw = Float.parseFloat(coords[3]);
                                float pitch = Float.parseFloat(coords[4]);

                                World world = Universe.get().getWorld(worldUuid);
                                if (world != null) {
                                    Vector3d targetPos = new Vector3d(x, y, z);
                                    Vector3f targetRot = new Vector3f(yaw, pitch, 0);
                                    ((Executor) world).execute(() -> {
                                        if (ref.isValid()) {
                                            Teleport teleport = new Teleport(targetPos, targetRot);
                                            ref.getReference().getStore().addComponent(ref.getReference(),
                                                    Teleport.getComponentType(), teleport);
                                        }
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " unjailed " + resolvedName + ".")
                        .color(Color.GREEN));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> freeze(UUID targetUuid, String targetName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.freeze.bypass")) {
                    return false;
                }

                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref == null || !ref.isValid())
                    return false;

                String resolvedName = ref.getUsername();

                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                TransformComponent transform = ref.getReference().getStore()
                                        .getComponent(ref.getReference(), TransformComponent.getComponentType());
                                if (transform != null) {
                                    plugin.addFrozenPlayer(targetUuid, transform.getPosition());
                                } else {
                                    plugin.addFrozenPlayer(targetUuid, new Vector3d(0, 100, 0));
                                }
                                ref.sendMessage(
                                        Message.raw("You have been frozen by staff. Do not log out.").color(Color.RED));
                            }
                        });
                    }
                }

                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " froze " + resolvedName + ".")
                        .color(Color.YELLOW));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> unfreeze(UUID targetUuid, String targetName, ExecutionContext context) {
        return plugin.removeFrozenPlayer(targetUuid).thenApply(wasFrozen -> {
            if (wasFrozen) {
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " unfroze " + targetName + ".")
                        .color(Color.GREEN));
                return true;
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> toggleVanish(UUID playerUuid, String playerName, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            if (plugin.isVanished(playerUuid)) {
                plugin.removeVanishedPlayer(playerUuid);
                Universe.get().getPlayers().forEach(observer -> {
                    if (observer != null && observer.isValid()) {
                        observer.getHiddenPlayersManager().showPlayer(playerUuid);
                    }
                });
                plugin.notifyStaff(Message.raw("[Staff] " + playerName + " is no longer vanished.").color(Color.GRAY));
                return false; // Not vanished
            } else {
                plugin.addVanishedPlayer(playerUuid);
                Universe.get().getPlayers().forEach(observer -> {
                    if (observer != null && observer.isValid()) {
                        UUID observerUuid = observer.getUuid();
                        if (!observerUuid.equals(playerUuid)) {
                            boolean canSee = com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                                    .hasPermission(observerUuid, "moderation.vanish.see");
                            if (canSee) {
                                observer.getHiddenPlayersManager().showPlayer(playerUuid);
                            } else {
                                observer.getHiddenPlayersManager().hidePlayer(playerUuid);
                            }
                        }
                    }
                });
                plugin.notifyStaff(Message.raw("[Staff] " + playerName + " vanished.").color(Color.GRAY));
                return true; // Vanished
            }
        });
    }
}
