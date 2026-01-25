package me.almana.moderationplus.web;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.storage.StorageManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class WebPanelPollingService {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final WebCommandExecutor commandExecutor;
    private final WebAcknowledgementService ackService;
    private final HttpClient httpClient;
    private final Gson gson;
    private ScheduledFuture<?> pollingTask;

    public WebPanelPollingService(ModerationPlus plugin) {
        this.plugin = plugin;
        this.ackService = new WebAcknowledgementService(plugin);
        this.commandExecutor = new me.almana.moderationplus.web.WebCommandExecutor(plugin, ackService);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
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
        if (!plugin.getConfigManager().isWebPanelEnabled()) {
            return;
        }

        try {
            StorageManager.ServerIdentity identity = plugin.getStorageManager().getOrGenerateServerIdentity();
            if (!identity.isClaimed() || identity.claimToken() != null) {
                // Not claimed yet, attempt to claim if URL is configured
                String claimUrl = plugin.getConfigManager().getWebPanelClaimUrl();
                if (claimUrl == null || claimUrl.isEmpty()) {
                    // No claim URL, cannot proceed.
                    return;
                }

                // Construct simplified JSON body
                String jsonBody = gson.toJson(java.util.Collections.singletonMap("claim_token", identity.claimToken()));

                HttpRequest claimRequest = HttpRequest.newBuilder()
                        .uri(URI.create(claimUrl))
                        .header("X-Server-ID", identity.serverId())
                        .header("X-Server-Secret", identity.serverSecret())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(claimRequest, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        if (plugin.getStorageManager().completeClaim(identity.claimToken())) {
                            logger.at(Level.INFO).log("Server successfully claimed! Web Panel integration is now active.");
                        } else {
                            logger.at(Level.WARNING).log("Server claimed on backend, but local DB update failed.");
                        }
                    } else {
                        logger.at(Level.WARNING).log("Failed to claim server. Backend returned status: %d", response.statusCode());
                    }
                } catch (Exception e) {
                    logger.at(Level.SEVERE).log("Exception during server claim attempt: %s", e.getMessage());
                }
                return; // Stop here
            }

            String pollUrl = plugin.getConfigManager().getWebPanelPollUrl();
            if (pollUrl == null || pollUrl.isEmpty()) {
                logger.at(Level.WARNING).log("Web Panel polling is enabled but 'poll_url' is not configured. Polling skipped.");
                return;
            }

            URI uri = URI.create(pollUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("X-Server-ID", identity.serverId())
                    .header("X-Server-Secret", identity.serverSecret())
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(this::handleResponse)
                    .exceptionally(ex -> {
                        logger.at(Level.SEVERE).log("Web Panel Poll Failed: %s", ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Error initiating Web Panel polling cycle");
        }
    }

    private void handleResponse(HttpResponse<String> response) {
        if (response.statusCode() == 200) {
            try {
                String body = response.body();
                if (body == null || body.isBlank()) {
                    return;
                }

                List<WebCommandIntent> intents = gson.fromJson(body, new TypeToken<List<WebCommandIntent>>() {}.getType());
                
                if (intents != null && !intents.isEmpty()) {
                    logger.at(Level.INFO).log("Received %d web commands.", intents.size());
                    commandExecutor.processIntents(intents);
                }
            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Failed to parse web commands response");
            }
        } else if (response.statusCode() == 401 || response.statusCode() == 403) {
            logger.at(Level.WARNING).log("Web Panel Polling Unauthorized (Status: %d). Check server credentials.", response.statusCode());
        } else {
            logger.at(Level.WARNING).log("Web Panel Polling returned unexpectedly (Status: %d)", response.statusCode());
        }
    }
}
