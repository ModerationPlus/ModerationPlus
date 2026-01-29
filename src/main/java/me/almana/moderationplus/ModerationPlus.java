package me.almana.moderationplus;

import java.awt.Color;
import com.hypixel.hytale.server.core.Message;

import com.hypixel.hytale.logger.HytaleLogger;

import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.logging.Level;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.accesscontrol.AccessControlModule;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleBanProvider;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import me.almana.moderationplus.api.chat.ChatChannelRegistry;
import me.almana.moderationplus.api.chat.StaffChatDelivery;
import me.almana.moderationplus.api.event.EventBus;
import me.almana.moderationplus.api.state.ModerationStateService;
import me.almana.moderationplus.commands.*;
import me.almana.moderationplus.component.FrozenComponent;
import me.almana.moderationplus.component.JailedComponent;
import me.almana.moderationplus.config.ConfigManager;
import me.almana.moderationplus.core.audit.CoreAuditService;
import me.almana.moderationplus.core.chat.DefaultChatChannel;
import me.almana.moderationplus.core.chat.DefaultStaffChatDelivery;
import me.almana.moderationplus.core.chat.SimpleChatChannelRegistry;
import me.almana.moderationplus.core.event.SyncEventBus;
import me.almana.moderationplus.core.state.CoreModerationStateService;
import me.almana.moderationplus.i18n.LanguageManager;
import me.almana.moderationplus.provider.VanishedPlayerIconMarkerProvider;
import me.almana.moderationplus.service.ModerationService;
import me.almana.moderationplus.snapshot.VanishedPlayerSnapshot;
import me.almana.moderationplus.storage.Punishment;
import me.almana.moderationplus.storage.StorageManager;
import me.almana.moderationplus.system.PlayerFreezeSystem;
import me.almana.moderationplus.system.VanishSnapshotSystem;
import me.almana.moderationplus.utils.TimeUtils;
import me.almana.moderationplus.web.WebPanelBootstrap;
import me.almana.moderationplus.web.WebPanelPollingService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

public class ModerationPlus extends JavaPlugin {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final StorageManager storageManager;

    private final EventBus eventBus;

    public ModerationPlus(@Nonnull JavaPluginInit init) {
        super(init);
        this.eventBus = new SyncEventBus();
        this.storageManager = new StorageManager();
        this.configManager = new ConfigManager();
        this.moderationService = new ModerationService(this);
        this.chatChannelRegistry = new SimpleChatChannelRegistry();
        this.chatChannelRegistry.register(new DefaultChatChannel("staff", "mod.staff.chat", "[SC] %s: %s"));
        this.staffChatDelivery = new DefaultStaffChatDelivery(this);
        this.moderationStateService = new CoreModerationStateService(this);
        this.auditService = new CoreAuditService(this);
        this.languageManager = new LanguageManager();
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ChatChannelRegistry getChatChannelRegistry() {
        return chatChannelRegistry;
    }

    public StaffChatDelivery getStaffChatDelivery() {
        return staffChatDelivery;
    }

    public ModerationStateService getModerationStateService() {
        return moderationStateService;
    }

    private final Map<UUID, Long> lastMuteFeedback = new ConcurrentHashMap<>();
    private volatile boolean chatLocked = false;
    private final ConfigManager configManager;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, VanishedPlayerSnapshot> vanishedSnapshots = new ConcurrentHashMap<>();
    private final ModerationService moderationService;
    private final ChatChannelRegistry chatChannelRegistry;
    private final StaffChatDelivery staffChatDelivery;
    private final ModerationStateService moderationStateService;
    private final CoreAuditService auditService;
    private final LanguageManager languageManager;
    private WebPanelPollingService webPanelPollingService;

    @Override
    protected void setup() {
        super.setup();
        storageManager.init();
        configManager.saveConfig();
        
        // Initialize language system
        String defaultLocale = configManager.getLanguageDefaultLocale();
        languageManager.init(defaultLocale);
        languageManager.setStorageManager(storageManager);
        
        ((CoreModerationStateService) moderationStateService).init();
        auditService.init();

        me.almana.moderationplus.component.FrozenComponent.TYPE = getEntityStoreRegistry().registerComponent(
                FrozenComponent.class,
                FrozenComponent::new);
        me.almana.moderationplus.component.JailedComponent.TYPE = getEntityStoreRegistry().registerComponent(
                JailedComponent.class,
                JailedComponent::new);

        getEntityStoreRegistry().registerSystem(new PlayerFreezeSystem(this));

        getCommandRegistry().registerCommand(new BanCommand(this));
        getCommandRegistry().registerCommand(new TempBanCommand(this));
        getCommandRegistry().registerCommand(new UnbanCommand(this));
        getCommandRegistry().registerCommand(new KickCommand(this));
        getCommandRegistry().registerCommand(new MuteCommand(this));

        getCommandRegistry().registerCommand(new TempMuteCommand(this));
        getCommandRegistry().registerCommand(new UnmuteCommand(this));
        getCommandRegistry().registerCommand(new WarnCommand(this));
        getCommandRegistry().registerCommand(new HistoryCommand(this));
        getCommandRegistry().registerCommand(new NoteCommand(this));
        getCommandRegistry().registerCommand(new NotesCommand(this));
        getCommandRegistry().registerCommand(new LanguageCommand(this));
        getCommandRegistry().registerCommand(new ModerationPlusCommand(this));


        getCommandRegistry().registerCommand(new SetJailCommand(this));
        getCommandRegistry().registerCommand(new JailCommand(this));
        getCommandRegistry().registerCommand(new UnjailCommand(this));
        getCommandRegistry().registerCommand(new VanishCommand(this));
        getCommandRegistry().registerCommand(new StaffChatCommand(this));
        getCommandRegistry().registerCommand(new ReportCommand(this));
        getCommandRegistry().registerCommand(new FlushCommand(this));
        getCommandRegistry().registerCommand(new ChatLockdownCommand(this));
        getCommandRegistry().registerCommand(new FreezeCommand(this));
        getCommandRegistry().registerCommand(new UnfreezeCommand(this));

        // Snapshot system registration
        getEntityStoreRegistry().registerSystem(new VanishSnapshotSystem(this));

        getEventRegistry().register(PlayerSetupConnectEvent.class, event -> {
            moderationService.handleJoinPunishmentChecks(event.getUuid(), event.getUsername());
        });

        for (World world : Universe
                .get().getWorlds().values()) {
            world.getWorldMapManager().addMarkerProvider("playerIcons",
                    new VanishedPlayerIconMarkerProvider(this));
        }

        getEventRegistry().registerGlobal(AddWorldEvent.class,
                event -> {
                    event.getWorld().getWorldMapManager().addMarkerProvider("playerIcons",
                            new VanishedPlayerIconMarkerProvider(this));
                });

        getEventRegistry().register(PlayerDisconnectEvent.class,
                event -> {
                    vanishedSnapshots.remove(event.getPlayerRef().getUuid());
                    vanishedPlayers.remove(event.getPlayerRef().getUuid());
                });

        HytaleServer.get()
                .getEventBus().<String, PlayerChatEvent>registerAsyncGlobal(
                        PlayerChatEvent.class,
                        future -> future.thenApply(event -> {
                            try {
                                UUID uuid = event.getSender().getUuid();
                                logger.at(Level.INFO).log(
                                        "Chat Event: " + event.getSender().getUsername() + ": " + event.getContent());

                                if (chatLocked) {
                                    if (!PermissionsModule.get()
                                            .hasPermission(uuid, "moderation.chatlockdown.bypass")) {
                                        event.setCancelled(true);
                                        event.getSender().sendMessage(Message
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

                                Optional<Punishment> mute = moderationService
                                        .getActiveMute(uuid, event.getSender().getUsername());

                                if (mute.isPresent()) {
                                    Punishment p = mute.get();
                                    event.setCancelled(true);
                                    long now = System.currentTimeMillis();
                                    long last = lastMuteFeedback.getOrDefault(uuid, 0L);
                                    if (now - last > 2000) {
                                        if (p.expiresAt() > 0) {
                                            long remaining = p.expiresAt() - now;
                                            String durationStr = TimeUtils
                                                    .formatDuration(remaining);
                                            event.getSender().sendMessage(Message
                                                    .raw("You are muted for " + durationStr + ". Reason: "
                                                            + p.reason())
                                                    .color(Color.RED));
                                        } else {
                                            event.getSender().sendMessage(Message
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
                        }
                        ));

        long flushInterval = configManager.getDatabaseFlushIntervalSeconds();
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                storageManager.flush();
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Error in scheduled database flush");
            }
        }, flushInterval, flushInterval, TimeUnit.SECONDS);
        logger.at(Level.INFO).log("Database auto-flush scheduled every %d seconds", flushInterval);

        logger.at(Level.INFO).log("ModerationPlus has been enabled!");

        if (configManager.isWebPanelEnabled()) {
            new WebPanelBootstrap().init(this);
            this.webPanelPollingService = new WebPanelPollingService(this);
            this.webPanelPollingService.start();
        }
    }


    public void notifyStaff(String message) {
        notifyStaff(Message.raw(message));
    }

    public void notifyStaff(Message message) {
        Universe.get().getPlayers().forEach(p -> {
            if (PermissionsModule.get().hasPermission(p.getUuid(),
                    "moderation.notify")) {
                p.sendMessage(message);
            }
        });
        ConsoleSender.INSTANCE
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

    public HytaleBanProvider getBanProvider() {
        try {

            AccessControlModule module = AccessControlModule
                    .get();

            try {
                Method method = module.getClass().getMethod("getBanProvider");
                return (HytaleBanProvider) method
                        .invoke(module);
            } catch (NoSuchMethodException e) {

                for (Field field : module.getClass().getDeclaredFields()) {
                    if (HytaleBanProvider.class
                            .isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return (HytaleBanProvider) field
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

    public CompletableFuture<Void> addFrozenPlayer(UUID uuid,
            Vector3d pos) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.runAsync(() -> {
                    if (ref.isValid()) {

                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                FrozenComponent.getComponentType()) != null) {
                            return;
                        }

                        ref.getReference().getStore().addComponent(
                                ref.getReference(),
                                FrozenComponent.getComponentType(),
                                new FrozenComponent(pos));
                    }
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> removeFrozenPlayer(UUID uuid) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        boolean has = ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                FrozenComponent.getComponentType()) != null;
                        if (has) {
                            ref.getReference().getStore().removeComponent(
                                    ref.getReference(),
                                    FrozenComponent.getComponentType());
                            return true;
                        }
                    }
                    return false;
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> isFrozen(UUID uuid) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        return ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                FrozenComponent.getComponentType()) != null;
                    }
                    return false;
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CompletableFuture<Void> addJailedPlayer(UUID uuid,
            Vector3d jailLocation, long expiresAt) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.runAsync(() -> {
                    if (ref.isValid()) {

                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                JailedComponent.getComponentType()) != null) {
                            return;
                        }

                        double radius = configManager.getJailRadius();
                        ref.getReference().getStore().addComponent(
                                ref.getReference(),
                                JailedComponent.getComponentType(),
                                new JailedComponent(jailLocation, radius, expiresAt));
                    }
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Boolean> removeJailedPlayer(UUID uuid) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        if (ref.getReference().getStore().getComponent(ref.getReference(),
                                JailedComponent.getComponentType()) != null) {
                            ref.getReference().getStore().removeComponent(
                                    ref.getReference(),
                                    JailedComponent.getComponentType());
                            return true;
                        }
                    }
                    return false;
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> isJailed(UUID uuid) {
        PlayerRef ref = Universe.get()
                .getPlayer(uuid);
        if (ref != null && ref.isValid() && ref.getWorldUuid() != null) {
            World world = Universe
                    .get().getWorld(ref.getWorldUuid());
            if (world != null) {
                return CompletableFuture.supplyAsync(() -> {
                    if (ref.isValid()) {
                        return ref.getReference().getStore().getComponent(
                                ref.getReference(),
                                JailedComponent.getComponentType()) != null;
                    }
                    return false;
                }, (Executor) world);
            }
        }
        return CompletableFuture.completedFuture(false);
    }

    public Map<UUID, Vector3d> getJailedPlayers() {

        return new java.util.HashMap<>();
    }

    public void addVanishedPlayer(UUID uuid) {
        vanishedPlayers.add(uuid);
    }

    public void removeVanishedPlayer(UUID uuid) {
        vanishedPlayers.remove(uuid);
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public Map<UUID, VanishedPlayerSnapshot> getVanishedSnapshots() {
        return vanishedSnapshots;
    }

    public ModerationService getModerationService() {
        return moderationService;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}
