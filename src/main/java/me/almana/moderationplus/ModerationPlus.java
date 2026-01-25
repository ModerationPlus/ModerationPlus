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

    private final me.almana.moderationplus.api.event.EventBus eventBus;

    public ModerationPlus(@Nonnull JavaPluginInit init) {
        super(init);
        this.eventBus = new me.almana.moderationplus.core.event.SyncEventBus();
        this.storageManager = new StorageManager();
        this.configManager = new me.almana.moderationplus.config.ConfigManager();
        this.moderationService = new me.almana.moderationplus.service.ModerationService(this);
        this.chatChannelRegistry = new me.almana.moderationplus.core.chat.SimpleChatChannelRegistry();
        this.chatChannelRegistry.register(new me.almana.moderationplus.core.chat.DefaultChatChannel("staff", "mod.staff.chat", "[SC] %s: %s"));
        this.staffChatDelivery = new me.almana.moderationplus.core.chat.DefaultStaffChatDelivery(this);
        this.moderationStateService = new me.almana.moderationplus.core.state.CoreModerationStateService(this);
        this.auditService = new me.almana.moderationplus.core.audit.CoreAuditService(this);
    }

    public me.almana.moderationplus.api.event.EventBus getEventBus() {
        return eventBus;
    }

    public me.almana.moderationplus.api.chat.ChatChannelRegistry getChatChannelRegistry() {
        return chatChannelRegistry;
    }

    public me.almana.moderationplus.api.chat.StaffChatDelivery getStaffChatDelivery() {
        return staffChatDelivery;
    }

    public me.almana.moderationplus.api.state.ModerationStateService getModerationStateService() {
        return moderationStateService;
    }

    private final java.util.Map<java.util.UUID, Long> lastMuteFeedback = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean chatLocked = false;
    private final me.almana.moderationplus.config.ConfigManager configManager;
    private final java.util.Set<java.util.UUID> vanishedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, me.almana.moderationplus.snapshot.VanishedPlayerSnapshot> vanishedSnapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private final me.almana.moderationplus.service.ModerationService moderationService;
    private final me.almana.moderationplus.api.chat.ChatChannelRegistry chatChannelRegistry;
    private final me.almana.moderationplus.api.chat.StaffChatDelivery staffChatDelivery;
    private final me.almana.moderationplus.core.state.CoreModerationStateService moderationStateService;
    private final me.almana.moderationplus.core.audit.CoreAuditService auditService;
    private me.almana.moderationplus.web.WebPanelPollingService webPanelPollingService;

    @Override
    protected void setup() {
        super.setup();
        storageManager.init();
        configManager.saveConfig();
        moderationStateService.init();
        auditService.init();

        me.almana.moderationplus.component.FrozenComponent.TYPE = getEntityStoreRegistry().registerComponent(
                me.almana.moderationplus.component.FrozenComponent.class,
                me.almana.moderationplus.component.FrozenComponent::new);
        me.almana.moderationplus.component.JailedComponent.TYPE = getEntityStoreRegistry().registerComponent(
                me.almana.moderationplus.component.JailedComponent.class,
                me.almana.moderationplus.component.JailedComponent::new);

        getEntityStoreRegistry().registerSystem(new me.almana.moderationplus.system.PlayerFreezeSystem(this));

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

        // Snapshot system registration
        getEntityStoreRegistry().registerSystem(new me.almana.moderationplus.system.VanishSnapshotSystem(this));

        getEventRegistry().register(PlayerSetupConnectEvent.class, event -> {
            moderationService.handleJoinPunishmentChecks(event.getUuid(), event.getUsername());
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
                    vanishedSnapshots.remove(event.getPlayerRef().getUuid());
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

                                if (isVanished(uuid)) {
                                    event.setCancelled(true);
                                    Message format = Message
                                            .raw("[Vanished] " + event.getSender().getUsername() + ": "
                                                    + event.getContent())
                                            .color(Color.GRAY);
                                    notifyStaff(format);
                                    return event;
                                }

                                java.util.Optional<me.almana.moderationplus.storage.Punishment> mute = moderationService
                                        .getActiveMute(uuid, event.getSender().getUsername());

                                if (mute.isPresent()) {
                                    me.almana.moderationplus.storage.Punishment p = mute.get();
                                    event.setCancelled(true);
                                    long now = System.currentTimeMillis();
                                    long last = lastMuteFeedback.getOrDefault(uuid, 0L);
                                    if (now - last > 2000) {
                                        if (p.expiresAt() > 0) {
                                            long remaining = p.expiresAt() - now;
                                            String durationStr = me.almana.moderationplus.utils.TimeUtils
                                                    .formatDuration(remaining);
                                            event.getSender().sendMessage(com.hypixel.hytale.server.core.Message
                                                    .raw("You are muted for " + durationStr + ". Reason: "
                                                            + p.reason())
                                                    .color(Color.RED));
                                        } else {
                                            event.getSender().sendMessage(com.hypixel.hytale.server.core.Message
                                                    .raw("You are permanently muted. Reason: " + p.reason())
                                                    .color(Color.RED));
                                        }
                                        lastMuteFeedback.put(uuid, now);
                                    }
                                    return event;
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
            com.hypixel.hytale.math.vector.Vector3d jailLocation, long expiresAt) {
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
                                new me.almana.moderationplus.component.JailedComponent(jailLocation, radius, expiresAt));
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

    public java.util.Map<java.util.UUID, me.almana.moderationplus.snapshot.VanishedPlayerSnapshot> getVanishedSnapshots() {
        return vanishedSnapshots;
    }

    public me.almana.moderationplus.service.ModerationService getModerationService() {
        return moderationService;
    }
}
