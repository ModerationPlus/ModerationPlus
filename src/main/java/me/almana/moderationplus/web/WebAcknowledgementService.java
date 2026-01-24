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
                            if (response.statusCode() != 200 && response.statusCode() != 201) {
                                logger.at(Level.WARNING).log("Failed to send Ack for command %s (Status: %d)",
                                        commandId,
                                        response.statusCode());
                            }
                        });

            } catch (Exception e) {
                logger.at(Level.SEVERE).withCause(e).log("Error sending Ack for command %s", commandId);
            }
        });
    }
}
