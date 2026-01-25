package me.almana.moderationplus.web;

import com.hypixel.hytale.logger.HytaleLogger;
import me.almana.moderationplus.ModerationPlus;
import me.almana.moderationplus.service.ExecutionContext;
import me.almana.moderationplus.service.ModerationService;

import java.util.List;
import java.util.logging.Level;

public class WebCommandExecutor {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();
    private final ModerationPlus plugin;
    private final WebAcknowledgementService ackService;

    public WebCommandExecutor(ModerationPlus plugin, WebAcknowledgementService ackService) {
        this.plugin = plugin;
        this.ackService = ackService;
    }

    public void processIntents(List<WebCommandIntent> intents) {
        // A2: Runtime Guard
        if (!plugin.getConfigManager().isWebPanelEnabled()) {
            logger.at(Level.WARNING).log("Attempted to process web intents while disabled. Ignoring.");
            return;
        }

        if (intents == null || intents.isEmpty()) {
            return;
        }

        for (WebCommandIntent intent : intents) {
            processIntent(intent);
        }
    }

    private void processIntent(WebCommandIntent intent) {
        if (intent.id() == null) {
            return;
        }

        if (plugin.getStorageManager().hasWebCommandProcessed(intent.id())) {
            ackService.sendAck(intent.id(), true, "Duplicate: Already processed");
            return;
        }

        plugin.getStorageManager().markWebCommandProcessed(intent.id());
        logger.at(Level.INFO).log("Processing Web Command: %s (Action: %s)", intent.id(), intent.action());

        ExecutionContext context = new ExecutionContext(
                intent.issuerUuid(),
                intent.issuerName() != null ? intent.issuerName() : "WebPanel",
                ExecutionContext.ExecutionSource.WEB);

        ModerationService service = plugin.getModerationService();
        String action = intent.action().toUpperCase();

        try {
            java.util.concurrent.CompletableFuture<Boolean> future = switch (action) {
                case "BAN" -> service.ban(intent.targetUuid(), intent.targetName(), intent.reason(), context);
                case "TEMPBAN" -> service.tempBan(intent.targetUuid(), intent.targetName(), intent.reason(),
                        intent.duration(), context);
                case "MUTE" -> service.mute(intent.targetUuid(), intent.targetName(), intent.reason(), context);
                case "TEMPMUTE" -> service.tempMute(intent.targetUuid(), intent.targetName(), intent.reason(),
                        intent.duration(), context);
                case "KICK" -> service.kick(intent.targetUuid(), intent.targetName(), intent.reason(), context);
                case "WARN" -> service.warn(intent.targetUuid(), intent.targetName(), intent.reason(), context);
                case "UNBAN" -> service.unban(intent.targetUuid(), intent.targetName(), context);
                case "UNMUTE" -> service.unmute(intent.targetUuid(), intent.targetName(), context);
                case "JAIL" -> service.jail(intent.targetUuid(), intent.targetName(), 0, intent.reason(), context);
                case "UNJAIL" -> service.unjail(intent.targetUuid(), intent.targetName(), context);
                case "FREEZE" -> service.freeze(intent.targetUuid(), intent.targetName(), context);
                case "UNFREEZE" -> service.unfreeze(intent.targetUuid(), intent.targetName(), context);
                case "VANISH" -> service.toggleVanish(intent.targetUuid(), intent.targetName(), context);
                default -> {
                    logger.at(Level.WARNING).log("Unknown web action: %s", action);
                    ackService.sendAck(intent.id(), false, "Unknown or unsupported action: " + action);
                    yield java.util.concurrent.CompletableFuture.completedFuture(false);
                }
            };

            future.thenAccept(success -> {
                if (success) {
                    ackService.sendAck(intent.id(), true, "Command executed successfully");
                } else {
                    ackService.sendAck(intent.id(), false,
                            "Command execution returned false (e.g., player offline or bypassed)");
                }
            }).exceptionally(ex -> {
                logger.at(Level.SEVERE).withCause(ex).log("Exception processing command %s", intent.id());
                ackService.sendAck(intent.id(), false, "Exception: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to execute web command %s", intent.id());
            ackService.sendAck(intent.id(), false, "Immediate Failure: " + e.getMessage());
        }
    }
}
