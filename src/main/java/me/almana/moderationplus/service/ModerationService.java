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
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import me.almana.moderationplus.api.event.punishment.PunishmentAppliedEvent;
import me.almana.moderationplus.api.event.punishment.PunishmentExpiredEvent;
import me.almana.moderationplus.api.event.punishment.PunishmentPreApplyEvent;
import me.almana.moderationplus.core.punishment.DefaultPunishmentTypes;
import java.time.Duration;
import com.hypixel.hytale.server.core.HytaleServer;
import me.almana.moderationplus.api.event.ModEvent;

public class ModerationService {

    private final ModerationPlus plugin;

    public ModerationService(ModerationPlus plugin) {
        this.plugin = plugin;
    }

    private void dispatchSync(ModEvent event) {
        // Enforce dispatch on the server's Scheduled Executor (Main/Scheduler Thread)
        // Blocks the async worker thread until the event is handled, ensuring thread safety and memory consistency.
        CompletableFuture.runAsync(() -> plugin.getEventBus().dispatch(event), HytaleServer.SCHEDULED_EXECUTOR).join();
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
                    me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                            UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.BAN, null, reason, false
                    );
                    PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                    dispatchSync(preEvent);
                    if (preEvent.isCancelled()) return false;
                    apiPunishment = preEvent.getPunishment();

                    InfiniteBan nativeBan = new InfiniteBan(targetUuid, context.issuerUuid(), Instant.now(), apiPunishment.reason());
                    banProvider.modify(bans -> {
                        bans.put(targetUuid, nativeBan);
                        return true;
                    });
                    
                    List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                            "BAN");
                    if (activeBans.isEmpty()) {
                        Punishment ban = new Punishment(0, playerData.id(), "BAN", context.issuerUuid().toString(), apiPunishment.reason(),
                                System.currentTimeMillis(), 0, true, "{}");
                        plugin.getStorageManager().createPunishment(ban);
                    }
                    
                    dispatchSync(new PunishmentAppliedEvent(apiPunishment));

                    // Update resolvedName and reason from event if changed
                    String finalReason = apiPunishment.reason();
                    plugin.notifyStaff(
                            Message.raw("[Staff] " + context.issuerName() + " banned " + resolvedName + " (" + finalReason + ")")
                                    .color(Color.GREEN));

                    if (isOnline && ref != null && ref.isValid()) {
                        UUID worldUuid = ref.getWorldUuid();
                        if (worldUuid != null) {
                             World world = Universe.get().getWorld(worldUuid);
                             if (world != null) {
                                  ((Executor) world).execute(() -> {
                                      if (ref.isValid()) {
                                          ref.getPacketHandler().disconnect("You are permanently banned.\nReason: " + finalReason);
                                      }
                                  });
                             }
                        }
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

                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.KICK, null, reason, false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                dispatchSync(preEvent);
                if (preEvent.isCancelled()) return false;
                apiPunishment = preEvent.getPunishment();

                Punishment kick = new Punishment(0, playerData.id(), "KICK", context.issuerUuid().toString(), apiPunishment.reason(),
                        System.currentTimeMillis(),
                        0, false, "{}");
                plugin.getStorageManager().createPunishment(kick);
                
                dispatchSync(new PunishmentAppliedEvent(apiPunishment));

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " kicked " + resolvedName + " (" + apiPunishment.reason() + ")")
                                .color(Color.GREEN));

                UUID worldUuid = ref.getWorldUuid();
                if (worldUuid != null) {
                    World world = Universe.get().getWorld(worldUuid);
                    if (world != null) {
                         String finalReason = apiPunishment.reason();
                        ((Executor) world).execute(() -> {
                            if (ref.isValid()) {
                                ref.getPacketHandler().disconnect("You have been kicked.\nReason: " + finalReason);
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

                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.MUTE, null, reason, false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                dispatchSync(preEvent);
                if (preEvent.isCancelled()) return false;
                apiPunishment = preEvent.getPunishment();

                Punishment mute = new Punishment(0, playerData.id(), "MUTE", context.issuerUuid().toString(), apiPunishment.reason(),
                        System.currentTimeMillis(),
                        0, true, "{}");
                plugin.getStorageManager().createPunishment(mute);
                
                dispatchSync(new PunishmentAppliedEvent(apiPunishment));

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " muted " + resolvedName + " (" + apiPunishment.reason() + ")")
                                .color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(
                            Message.raw("You have been permanently muted.\nReason: " + apiPunishment.reason()).color(Color.RED));
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
                        
                        me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                                UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.BAN, null, "Unbanned", false
                        );
                        dispatchSync(new PunishmentExpiredEvent(apiPunishment));
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
                    me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                            UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.MUTE, null, "Unmuted", false
                    );
                    plugin.getEventBus().dispatch(new PunishmentExpiredEvent(apiPunishment));
                    
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
                    me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                            UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.BAN, Duration.ofMillis(durationMillis), reason, false
                    );
                    PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                    plugin.getEventBus().dispatch(preEvent);
                    if (preEvent.isCancelled()) return false;
                    apiPunishment = preEvent.getPunishment();
                    
                    long finalDuration = apiPunishment.duration() != null ? apiPunishment.duration().toMillis() : durationMillis;
                    long finalExpiresAt = System.currentTimeMillis() + finalDuration;

                    TimedBan nativeBan = new TimedBan(targetUuid, context.issuerUuid(), Instant.now(),
                            Instant.ofEpochMilli(finalExpiresAt), apiPunishment.reason());
                    banProvider.modify(bans -> {
                        bans.put(targetUuid, nativeBan);
                        return true;
                    });
                    
                    List<Punishment> activeBans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(),
                             "BAN");
                    if (activeBans.isEmpty()) {
                        Punishment ban = new Punishment(0, playerData.id(), "BAN", context.issuerUuid().toString(), apiPunishment.reason(),
                                System.currentTimeMillis(), finalExpiresAt, true, "{}");
                        plugin.getStorageManager().createPunishment(ban);
                    }
                    
                    plugin.getEventBus().dispatch(new PunishmentAppliedEvent(apiPunishment));

                    String durationStr = TimeUtils.formatDuration(finalDuration);
                    plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " temp-banned " + resolvedName
                            + " for " + durationStr + " (" + apiPunishment.reason() + ")").color(Color.GREEN));

                    if (isOnline && ref != null && ref.isValid()) {
                        UUID worldUuid = ref.getWorldUuid();
                        if (worldUuid != null) {
                            World world = Universe.get().getWorld(worldUuid);
                            if (world != null) {
                                String finalReason = apiPunishment.reason();
                                ((Executor) world).execute(() -> {
                                    if (ref.isValid()) {
                                        ref.getPacketHandler()
                                                .disconnect("You are banned for " + durationStr + ".\nReason: " + finalReason);
                                    }
                                });
                            }
                        }
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
                
                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.MUTE, Duration.ofMillis(durationMillis), reason, false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                plugin.getEventBus().dispatch(preEvent);
                if (preEvent.isCancelled()) return false;
                apiPunishment = preEvent.getPunishment();

                long finalDuration = apiPunishment.duration() != null ? apiPunishment.duration().toMillis() : durationMillis;
                long finalExpiresAt = System.currentTimeMillis() + finalDuration;
                Punishment mute = new Punishment(0, playerData.id(), "MUTE", context.issuerUuid().toString(), apiPunishment.reason(),
                        System.currentTimeMillis(), finalExpiresAt, true, "{}");
                plugin.getStorageManager().createPunishment(mute);
                
                plugin.getEventBus().dispatch(new PunishmentAppliedEvent(apiPunishment));

                String durationStr = TimeUtils.formatDuration(finalDuration);
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " temp-muted " + resolvedName
                        + " for " + durationStr + " (" + apiPunishment.reason() + ")").color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(Message.raw("You have been muted for " + durationStr + ".\nReason: " + apiPunishment.reason())
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
                
                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.WARN, null, reason, false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                plugin.getEventBus().dispatch(preEvent);
                if (preEvent.isCancelled()) return false;
                apiPunishment = preEvent.getPunishment();
                
                Punishment warning = new Punishment(0, playerData.id(), "WARN", context.issuerUuid().toString(), apiPunishment.reason(),
                        System.currentTimeMillis(), 0, true, null);
                plugin.getStorageManager().createPunishment(warning);
                
                plugin.getEventBus().dispatch(new PunishmentAppliedEvent(apiPunishment));

                plugin.notifyStaff(
                        Message.raw("[Staff] " + context.issuerName() + " warned " + resolvedName + " (" + apiPunishment.reason() + ")")
                                .color(Color.GREEN));

                if (ref != null && ref.isValid()) {
                    ref.sendMessage(Message.raw("You have been warned. Reason: " + apiPunishment.reason()).color(Color.YELLOW));
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> jail(UUID targetUuid, String targetName, ExecutionContext context) {
        return jail(targetUuid, targetName, 0, "Jailed by staff", context);
    }

    public CompletableFuture<Boolean> jail(UUID targetUuid, String targetName, long durationMillis, ExecutionContext context) {
        return jail(targetUuid, targetName, durationMillis, "Jailed by staff", context);
    }

    public CompletableFuture<Boolean> jail(UUID targetUuid, String targetName, long durationMillis, String reason, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!plugin.getConfigManager().hasJailLocation()) {
                    return false;
                }

                if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(targetUuid,
                        "moderation.jail.bypass")) {
                    return false;
                }

                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.JAIL, durationMillis > 0 ? Duration.ofMillis(durationMillis) : null, reason, false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                plugin.getEventBus().dispatch(preEvent);
                if (preEvent.isCancelled()) return false;
                apiPunishment = preEvent.getPunishment();
                final me.almana.moderationplus.api.punishment.Punishment finalApiPunishment = apiPunishment;

                long finalDuration = apiPunishment.duration() != null ? apiPunishment.duration().toMillis() : (durationMillis > 0 ? durationMillis : 0);
                long finalExpiresAt = finalDuration > 0 ? System.currentTimeMillis() + finalDuration : 0;
                
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
                                                     context.issuerUuid().toString(), finalApiPunishment.reason(),
                                                     System.currentTimeMillis(), finalExpiresAt, true, finalLoc);
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

                                    plugin.addJailedPlayer(targetUuid, jailPos, finalExpiresAt);
                                    plugin.addFrozenPlayer(targetUuid, jailPos);

                                    EventTitleUtil.showEventTitleToPlayer(
                                            ref,
                                            Message.raw("JAILED").color(Color.RED),
                                            Message.raw("Reason: " + finalApiPunishment.reason() + " / Time: " + (finalDuration > 0 ? TimeUtils.formatDuration(finalDuration) : "Permanent")),
                                            true);
                                }
                            });
                        }
                    }
                } else {
                    Punishment punishment = new Punishment(0, playerData.id(), "JAIL", context.issuerUuid().toString(),
                            finalApiPunishment.reason(),
                            System.currentTimeMillis(), finalExpiresAt, true, null);
                    plugin.getStorageManager().insertPunishment(punishment);
                }
                
                plugin.getEventBus().dispatch(new PunishmentAppliedEvent(apiPunishment));

                String durationStr = finalDuration > 0 ? TimeUtils.formatDuration(finalDuration) : "permanent";
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " jailed " + resolvedName + " (" + durationStr + ").")
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
                    
                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.JAIL, null, "Unjailed", false
                );
                dispatchSync(new PunishmentExpiredEvent(apiPunishment));

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

                                            EventTitleUtil.showEventTitleToPlayer(
                                                    ref,
                                                    Message.raw("UNJAILED").color(Color.GREEN),
                                                    Message.raw("You are free to go."),
                                                    true);
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

                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.FREEZE, null, "Frozen", false
                );
                PunishmentPreApplyEvent preEvent = new PunishmentPreApplyEvent(apiPunishment);
                dispatchSync(preEvent);
                if (preEvent.isCancelled()) return false;
                
                // Freeze doesn't store permanent record in DB in original code, so just notify staff and apply effect
                
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
                                
                                EventTitleUtil.showEventTitleToPlayer(
                                        ref,
                                        Message.raw("FROZEN").color(Color.RED),
                                        Message.raw("Do not log out."),
                                        true);
                            }
                        });
                    }
                }
                
                dispatchSync(new PunishmentAppliedEvent(apiPunishment));

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
                me.almana.moderationplus.api.punishment.Punishment apiPunishment = new me.almana.moderationplus.api.punishment.Punishment(
                        UUID.randomUUID(), targetUuid, context.issuerUuid(), DefaultPunishmentTypes.FREEZE, null, "Unfrozen", false
                );
                dispatchSync(new PunishmentExpiredEvent(apiPunishment));
                
                plugin.notifyStaff(Message.raw("[Staff] " + context.issuerName() + " unfroze " + targetName + ".")
                        .color(Color.GREEN));
                
                PlayerRef ref = Universe.get().getPlayer(targetUuid);
                if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
                    World world = Universe.get().getWorld(ref.getWorldUuid());
                    if (world != null) {
                         ((Executor) world).execute(() -> {
                             if (ref.isValid()) {
                                 EventTitleUtil.showEventTitleToPlayer(
                                        ref,
                                        Message.raw("UNFROZEN").color(Color.GREEN),
                                        Message.raw("You have been unfrozen."),
                                        true);
                             }
                         });
                    }
                }
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
    public java.util.Optional<Punishment> getActiveMute(UUID uuid, String username) {
        try {
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(uuid, username);
            List<Punishment> mutes = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "MUTE");
            for (Punishment p : mutes) {
                if (p.expiresAt() > 0 && System.currentTimeMillis() > p.expiresAt()) {
                    plugin.getStorageManager().deactivatePunishment(p.id());
                    plugin.notifyStaff(Message.raw("[Staff] " + username + " auto-unmuted (expired)").color(Color.GREEN));
                    continue;
                }
                return java.util.Optional.of(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return java.util.Optional.empty();
    }

    public void handleJoinPunishmentChecks(UUID uuid, String username) {
        try {
            // Check Mutes (Trigger auto-expire)
            getActiveMute(uuid, username);

            // Check Bans (Trigger auto-expire)
            PlayerData playerData = plugin.getStorageManager().getOrCreatePlayer(uuid, username);
            List<Punishment> bans = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "BAN");
            for (Punishment p : bans) {
                if (p.expiresAt() > 0 && System.currentTimeMillis() > p.expiresAt()) {
                    plugin.getStorageManager().deactivatePunishment(p.id());
                    plugin.notifyStaff(Message.raw("[Staff] " + username + " auto-unbanned (expired)").color(Color.GREEN));
                }
            }

            // Check Jails
            List<Punishment> jails = plugin.getStorageManager().getActivePunishmentsByType(playerData.id(), "JAIL");
            Punishment activeJail = jails.isEmpty() ? null : jails.get(0);
            
            if (activeJail != null) {
                if (activeJail.expiresAt() > 0 && System.currentTimeMillis() > activeJail.expiresAt()) {
                     plugin.getStorageManager().deactivatePunishment(activeJail.id());
                     // Trigger unjail logic (restore location)
                     // Since we are in join handler, we can assume player is here or connecting.
                     // But unjail is ASYNC. We should call unjail.
                     ExecutionContext ctx = ExecutionContext.console();
                     unjail(uuid, username, ctx); 
                     return;
                }


                if (plugin.getConfigManager().hasJailLocation()) {
                    // Delay check to allow player entity to spawn
                    com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        PlayerRef ref = Universe.get().getPlayer(username, com.hypixel.hytale.server.core.NameMatching.EXACT);
                        if (ref != null && ref.isValid()) {
                            double[] jailLoc = plugin.getConfigManager().getJailLocation();
                            Vector3d jailPos = new Vector3d(jailLoc[0], jailLoc[1], jailLoc[2]);
                            Vector3f jailRot = new Vector3f(0, 0, 0);

                            long expiresAt = activeJail.expiresAt();
                            plugin.addJailedPlayer(uuid, jailPos, expiresAt);
                            plugin.addFrozenPlayer(uuid, jailPos);

                            Teleport teleport = new Teleport(jailPos, jailRot);
                            ref.getReference().getStore().addComponent(ref.getReference(), Teleport.getComponentType(), teleport);

                            EventTitleUtil.showEventTitleToPlayer(
                                    ref,
                                    Message.raw("JAILED").color(Color.RED),
                                    Message.raw("Reason: " + activeJail.reason() + " / Time: " + (expiresAt > 0 ? TimeUtils.formatDuration(expiresAt - System.currentTimeMillis()) : "Permanent")),
                                    true);

                            plugin.notifyStaff(Message.raw("[Staff] Jailed player " + username + " joined the server.").color(Color.RED));
                        }
                    }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
