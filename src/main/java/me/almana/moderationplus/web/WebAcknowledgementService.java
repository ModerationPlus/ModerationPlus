package me.almana.moderationplus.web;

import com.hypixel.hytale.logger.HytaleLogger;
import me.almana.moderationplus.ModerationPlus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class WebAcknowledgementService {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final HttpClient httpClient;

    public WebAcknowledgementService(ModerationPlus plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendAck(String commandId, boolean success, String message) {
        attemptAck(commandId, success, message, 0);
    }

    private void attemptAck(String commandId, boolean success, String message, int retryCount) {
        // A2: Runtime Guard
        if (!plugin.getConfigManager().isWebPanelEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                me.almana.moderationplus.storage.StorageManager.ServerIdentity identity = plugin.getStorageManager()
                        .getOrGenerateServerIdentity();

                if (!identity.isClaimed() || identity.claimToken() != null) {
                    return;
                }

                String baseUrl = plugin.getConfigManager().getWebPanelUrl();
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                URI uri = URI.create(baseUrl + "/api/v1/server/commands/" + commandId + "/ack");

                WebCommandAck ack = new WebCommandAck(commandId, success ? "SUCCESS" : "FAILED", message);
                String json = new com.google.gson.Gson().toJson(ack);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .header("Content-Type", "application/json")
                        .header("X-Server-ID", identity.serverId())
                        .header("X-Server-Secret", identity.serverSecret())
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            int status = response.statusCode();
                            if (status >= 200 && status < 300) {
                                // Success - No op
                            } else if (status >= 400 && status < 500) {
                                // Terminal failure (Auth, Bad Request) - Do NOT retry
                                logger.at(Level.WARNING).log("Terminal ACK failure for %s (Status: %d). Not retrying.",
                                        commandId, status);
                            } else {
                                // Transient failure (5xx) - Retry
                                scheduleRetry(commandId, success, message, retryCount, "Server Error " + status);
                            }
                        })
                        .exceptionally(ex -> {
                            // Network failure - Retry
                            scheduleRetry(commandId, success, message, retryCount, "Exception: " + ex.getMessage());
                            return null;
                        });

            } catch (Exception e) {
                scheduleRetry(commandId, success, message, retryCount, "Setup Exception: " + e.getMessage());
            }
        });
    }

    private void scheduleRetry(String commandId, boolean success, String message, int currentRetries, String reason) {
        if (currentRetries >= 3) {
            logger.at(Level.SEVERE).log("Failed to send ACK for %s after %d attempts. Last error: %s",
                    commandId, currentRetries + 1, reason);
            return;
        }

        long delay = 3L;

        com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            attemptAck(commandId, success, message, currentRetries + 1);
        }, delay, java.util.concurrent.TimeUnit.SECONDS);
    }
}
