package me.almana.moderationplus;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;

import com.hypixel.hytale.logger.HytaleLogger;

import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import me.almana.moderationplus.storage.StorageManager;
import me.almana.moderationplus.commands.*;

public class ModerationPlus extends JavaPlugin {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final StorageManager storageManager;

    public ModerationPlus(@Nonnull JavaPluginInit init) {
        super(init);
        this.storageManager = new StorageManager();
        this.configManager = new me.almana.moderationplus.config.ConfigManager();
        this.moderationService = new me.almana.moderationplus.service.ModerationService(this);
    }

    private final java.util.Map<java.util.UUID, Long> lastMuteFeedback = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean chatLocked = false;
    private final me.almana.moderationplus.config.ConfigManager configManager;
    private final java.util.Set<java.util.UUID> vanishedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final me.almana.moderationplus.service.ModerationService moderationService;
    private me.almana.moderationplus.web.WebPanelPollingService webPanelPollingService;

    @Override
    protected void setup() {
        super.setup();
        storageManager.init();
        configManager.saveConfig();

        me.almana.moderationplus.component.FrozenComponent.TYPE = getEntityStoreRegistry().registerComponent(
                me.almana.moderationplus.component.FrozenComponent.class,
                me.almana.moderationplus.component.FrozenComponent::new);
        me.almana.moderationplus.component.JailedComponent.TYPE = getEntityStoreRegistry().registerComponent(
                me.almana.moderationplus.component.JailedComponent.class,
                me.almana.moderationplus.component.JailedComponent::new);

        getEntityStoreRegistry().registerSystem(new me.almana.moderationplus.system.PlayerFreezeSystem());

        getCommandRegistry().registerCommand(new BanCommand(this));
        getCommandRegistry().registerCommand(new TempBanCommand(this));
        getCommandRegistry().registerCommand(new UnbanCommand(this));
        getCommandRegistry().registerCommand(new KickCommand(this));
        getCommandRegistry().registerCommand(new MuteCommand(this));

        getCommandRegistry().registerCommand(new TempMuteCommand(this));
        getCommandRegistry().registerCommand(new UnmuteCommand(this));
        getCommandRegistry().registerCommand(new WarnCommand(this));
        getCommandRegistry().registerCommand(new me.almana.moderationplus.commands.HistoryCommand(this));
        getCommandRegistry().registerCommand(new me.almana.moderationplus.commands.NoteCommand(this));
        getCommandRegistry().registerCommand(new me.almana.moderationplus.commands.NotesCommand(this));

        getCommandRegistry().registerCommand(new SetJailCommand(this));
        getCommandRegistry().registerCommand(new JailCommand(this));
        getCommandRegistry().registerCommand(new UnjailCommand(this));
        getCommandRegistry().registerCommand(new VanishCommand(this));
        getCommandRegistry().registerCommand(new StaffChatCommand(this));
        getCommandRegistry().registerCommand(new me.almana.moderationplus.commands.ReportCommand(this));
        getCommandRegistry().registerCommand(new FlushCommand(this));
        getCommandRegistry().registerCommand(new ChatLockdownCommand(this));
        getCommandRegistry().registerCommand(new FreezeCommand(this));
        getCommandRegistry().registerCommand(new UnfreezeCommand(this));

        getEventRegistry().register(PlayerSetupConnectEvent.class, event -> {
            try {
                me.almana.moderationplus.storage.StorageManager.PlayerData playerData = storageManager
                        .getOrCreatePlayer(event.getUuid(), event.getUsername());

                java.util.List<me.almana.moderationplus.storage.Punishment> mutes = storageManager
                        .getActivePunishmentsByType(playerData.id(), "MUTE");
                for (me.almana.moderationplus.storage.Punishment p : mutes) {
                    if (p.expiresAt() > 0 && System.currentTimeMillis() > p.expiresAt()) {
                        storageManager.deactivatePunishment(p.id());
                        notifyStaff(Message.raw("[Staff] " + event.getUsername() + " auto-unmuted (expired)")
                                .color(Color.GREEN));
                    }
                }

                java.util.List<me.almana.moderationplus.storage.Punishment> bans = storageManager
                        .getActivePunishmentsByType(playerData.id(), "BAN");
                for (me.almana.moderationplus.storage.Punishment p : bans) {
                    if (p.expiresAt() > 0 && System.currentTimeMillis() > p.expiresAt()) {
                        storageManager.deactivatePunishment(p.id());
                        notifyStaff(Message.raw("[Staff] " + event.getUsername() + " auto-unbanned (expired)")
                                .color(Color.GREEN));
                    }
                }

                java.util.List<me.almana.moderationplus.storage.Punishment> jails = storageManager
                        .getActivePunishmentsByType(playerData.id(), "JAIL");
                if (!jails.isEmpty() && configManager.hasJailLocation()) {

                    com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe
                            .get().getPlayer(event.getUsername(), com.hypixel.hytale.server.core.NameMatching.EXACT);
                    if (ref != null && ref.isValid()) {
                        double[] jailLoc = configManager.getJailLocation();
                        com.hypixel.hytale.math.vector.Vector3d jailPos = new com.hypixel.hytale.math.vector.Vector3d(
                                jailLoc[0], jailLoc[1], jailLoc[2]);

                        addJailedPlayer(event.getUuid(), jailPos);
                        addFrozenPlayer(event.getUuid(), jailPos);

                        com.hypixel.hytale.math.vector.Vector3f jailRot = new com.hypixel.hytale.math.vector.Vector3f(0,
                                0, 0);
                        com.hypixel.hytale.server.core.modules.entity.teleport.Teleport teleport = new com.hypixel.hytale.server.core.modules.entity.teleport.Teleport(
                                jailPos, jailRot);
                        ref.getReference().getStore().addComponent(ref.getReference(),
                                com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType(),
                                teleport);
                    }
                }
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to register player in database!");
            }
        });

        for (com.hypixel.hytale.server.core.universe.world.World world : com.hypixel.hytale.server.core.universe.Universe
                .get().getWorlds().values()) {
            world.getWorldMapManager().addMarkerProvider("playerIcons",
                    new me.almana.moderationplus.provider.VanishedPlayerIconMarkerProvider(this));
        }

        getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent.class,
                event -> {
                    event.getWorld().getWorldMapManager().addMarkerProvider("playerIcons",
                            new me.almana.moderationplus.provider.VanishedPlayerIconMarkerProvider(this));
                });

        getEventRegistry().register(com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent.class,
                event -> {

                    vanishedPlayers.remove(event.getPlayerRef().getUuid());
                });

        com.hypixel.hytale.server.core.HytaleServer.get()
                .getEventBus().<String, com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent>registerAsyncGlobal(
                        com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent.class,
                        future -> future.thenApply(event -> {
                            try {
                                java.util.UUID uuid = event.getSender().getUuid();
                                logger.at(Level.INFO).log(
                                        "Chat Event: " + event.getSender().getUsername() + ": " + event.getContent());

                                if (chatLocked) {
                                    if (!com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                                            .hasPermission(uuid, "moderation.chatlockdown.bypass")) {
                                        event.setCancelled(true);
                                        event.getSender().sendMessage(com.hypixel.hytale.server.core.Message
                                                .raw("Chat is currently locked by staff.").color(Color.RED));
                                        return event;
                                    }
                                }

                                me.almana.moderationplus.storage.StorageManager.PlayerData playerData = storageManager
                                        .getPlayerByUUID(uuid);
                                if (playerData == null) {
                                    logger.at(Level.INFO).log("Chat: No player data found");
                                    return event;
                                }

                                if (isVanished(uuid)) {
                                    event.setCancelled(true);
                                    Message format = Message
                                            .raw("[Vanished] " + event.getSender().getUsername() + ": "
                                                    + event.getContent())
                                            .color(Color.GRAY);
                                    notifyStaff(format);
                                    return event;
                                }

                                java.util.List<me.almana.moderationplus.storage.Punishment> mutes = storageManager
                                        .getActivePunishmentsByType(playerData.id(), "MUTE");

                                if (!mutes.isEmpty()) {
                                    for (me.almana.moderationplus.storage.Punishment p : mutes) {

                                        if (p.expiresAt() > 0) {
                                            if (System.currentTimeMillis() > p.expiresAt()) {
                                                storageManager.deactivatePunishment(p.id());
                                                continue;
                                            }

                                            event.setCancelled(true);
                                            long now = System.currentTimeMillis();
                                            long last = lastMuteFeedback.getOrDefault(uuid, 0L);
                                            if (now - last > 2000) {
                                                long remaining = p.expiresAt() - now;
                                                String durationStr = me.almana.moderationplus.utils.TimeUtils
                                                        .formatDuration(remaining);
                                                event.getSender().sendMessage(com.hypixel.hytale.server.core.Message
                                                        .raw("You are muted for " + durationStr + ". Reason: "
                                                                + p.reason())
                                                        .color(Color.RED));
                                                lastMuteFeedback.put(uuid, now);
                                            }
                                            return event;
                                        } else {

                                            event.setCancelled(true);
                                            long now = System.currentTimeMillis();
                                            long last = lastMuteFeedback.getOrDefault(uuid, 0L);
                                            if (now - last > 2000) {
                                                event.getSender().sendMessage(com.hypixel.hytale.server.core.Message
                                                        .raw("You are permanently muted. Reason: " + p.reason())
                                                        .color(Color.RED));
                                                lastMuteFeedback.put(uuid, now);
                                            }
                                            return event;
                                        }
                                    }
                                }

                                logger.at(Level.INFO).log("Chat: Allowed");
                                return event;
                            } catch (Exception e) {
                                logger.at(Level.SEVERE).withCause(e).log("Error in chat listener!");
                            }
                            return event;
                        }));

        long flushInterval = configManager.getDatabaseFlushIntervalSeconds();
        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                storageManager.flush();
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Error in scheduled database flush");
            }
        }, flushInterval, flushInterval, java.util.concurrent.TimeUnit.SECONDS);
        logger.at(Level.INFO).log("Database auto-flush scheduled every %d seconds", flushInterval);

        logger.at(Level.INFO).log("ModerationPlus has been enabled!");

        if (configManager.isWebPanelEnabled()) {
            new me.almana.moderationplus.web.WebPanelBootstrap().init(this);
            this.webPanelPollingService = new me.almana.moderationplus.web.WebPanelPollingService(this);
            this.webPanelPollingService.start();
        }
    }

    public void notifyStaff(String message) {
        notifyStaff(Message.raw(message));
    }

    public void notifyStaff(Message message) {
        com.hypixel.hytale.server.core.universe.Universe.get().getPlayers().forEach(p -> {
            if (com.hypixel.hytale.server.core.permissions.PermissionsModule.get().hasPermission(p.getUuid(),
                    "moderation.notify")) {
                p.sendMessage(message);
            }
        });
        com.hypixel.hytale.server.core.console.ConsoleSender.INSTANCE
                .sendMessage(message);
    }

    @Override
    protected void shutdown() {
        if (webPanelPollingService != null) {
            webPanelPollingService.stop();
        }
        if (storageManager != null) {
            storageManager.close();
        }
        super.shutdown();
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider getBanProvider() {
        try {

            com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule module = com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule
                    .get();

            try {
                java.lang.reflect.Method method = module.getClass().getMethod("getBanProvider");
                return (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider) method
                        .invoke(module);
            } catch (NoSuchMethodException e) {

                for (java.lang.reflect.Field field : module.getClass().getDeclaredFields()) {
                    if (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider.class
                            .isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return (com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider) field
                                .get(module);
                    }
                }
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Could not retrieve HytaleBanProvider!");
        }
        return null;
    }

    public boolean getChatLocked() {
        return chatLocked;
    }

    public void setChatLocked(boolean locked) {
        this.chatLocked = locked;
    }

    public java.util.concurrent.CompletableFuture<Void> addFrozenPlayer(java.util.UUID uuid,
            com.hypixel.hytale.math.vector.Vector3d pos) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.runAsync(() -> {
                    if (ref.isValid()) {

                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                me.almana.moderationplus.component.FrozenComponent.getComponentType()) != null) {
                            return;
                        }

                        ref.getReference().getStore().addComponent(
                                ref.getReference(),
                                me.almana.moderationplus.component.FrozenComponent.getComponentType(),
                                new me.almana.moderationplus.component.FrozenComponent(pos));
                    }
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture<Boolean> removeFrozenPlayer(java.util.UUID uuid) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        boolean has = ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                me.almana.moderationplus.component.FrozenComponent.getComponentType()) != null;
                        if (has) {
                            ref.getReference().getStore().removeComponent(
                                    ref.getReference(),
                                    me.almana.moderationplus.component.FrozenComponent.getComponentType());
                            return true;
                        }
                    }
                    return false;
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    public java.util.concurrent.CompletableFuture<Boolean> isFrozen(java.util.UUID uuid) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        return ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                me.almana.moderationplus.component.FrozenComponent.getComponentType()) != null;
                    }
                    return false;
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    public me.almana.moderationplus.config.ConfigManager getConfigManager() {
        return configManager;
    }

    public java.util.concurrent.CompletableFuture<Void> addJailedPlayer(java.util.UUID uuid,
            com.hypixel.hytale.math.vector.Vector3d jailLocation) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.runAsync(() -> {
                    if (ref.isValid()) {

                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                me.almana.moderationplus.component.JailedComponent.getComponentType()) != null) {
                            return;
                        }

                        double radius = configManager.getJailRadius();
                        ref.getReference().getStore().addComponent(
                                ref.getReference(),
                                me.almana.moderationplus.component.JailedComponent.getComponentType(),
                                new me.almana.moderationplus.component.JailedComponent(jailLocation, radius));
                    }
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public java.util.concurrent.CompletableFuture<Boolean> removeJailedPlayer(java.util.UUID uuid) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                me.almana.moderationplus.component.JailedComponent.getComponentType()) != null) {
                            ref.getReference().getStore().removeComponent(
                                    ref.getReference(),
                                    me.almana.moderationplus.component.JailedComponent.getComponentType());
                            return true;
                        }
                    }
                    return false;
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    public java.util.concurrent.CompletableFuture<Boolean> isJailed(java.util.UUID uuid) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = com.hypixel.hytale.server.core.universe.Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            com.hypixel.hytale.server.core.universe.world.World world = com.hypixel.hytale.server.core.universe.Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        return ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                me.almana.moderationplus.component.JailedComponent.getComponentType()) != null;
                    }
                    return false;
                }, (java.util.concurrent.Executor) world);
            }
        }
        return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    public java.util.Map<java.util.UUID, com.hypixel.hytale.math.vector.Vector3d> getJailedPlayers() {

        return new java.util.HashMap<>();
    }

    public void addVanishedPlayer(java.util.UUID uuid) {
        vanishedPlayers.add(uuid);
    }

    public void removeVanishedPlayer(java.util.UUID uuid) {
        vanishedPlayers.remove(uuid);
    }

    public boolean isVanished(java.util.UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public me.almana.moderationplus.service.ModerationService getModerationService() {
        return moderationService;
    }
}
