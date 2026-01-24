package me.almana.moderationplus.web;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import me.almana.moderationplus.ModerationPlus;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class WebPanelPollingService {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final WebCommandExecutor commandExecutor;
    private final WebAcknowledgementService ackService;
    private ScheduledFuture<?> pollingTask;

    public WebPanelPollingService(ModerationPlus plugin) {
        this.plugin = plugin;
        this.ackService = new WebAcknowledgementService(plugin);
        this.commandExecutor = new me.almana.moderationplus.web.WebCommandExecutor(plugin, ackService);
    }

    public void start() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            return;
        }

        long interval = plugin.getConfigManager().getWebPanelPollIntervalSeconds();
        if (interval <= 0) {
            logger.at(Level.WARNING).log("Web Panel polling interval is %d, polling disabled.", interval);
            return;
        }

        logger.at(Level.INFO).log("Starting Web Panel polling service (Interval: %ds)...", interval);
        pollingTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::poll, interval, interval,
                TimeUnit.SECONDS);
    }

    public void stop() {
        if (pollingTask != null) {
            logger.at(Level.INFO).log("Stopping Web Panel polling service...");
            pollingTask.cancel(false);
            pollingTask = null;
        }
    }

    private void poll() {
        try {

            java.util.List<WebCommandIntent> intents = fetchIntents();


            if (intents != null && !intents.isEmpty()) {
                commandExecutor.processIntents(intents);
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Error during Web Panel polling cycle");
        }
    }

    private java.util.List<WebCommandIntent> fetchIntents() {
        // Transport layer placeholder
        return java.util.Collections.emptyList();
    }
}
