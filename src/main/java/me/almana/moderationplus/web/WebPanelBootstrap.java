package me.almana.moderationplus.web;

import com.hypixel.hytale.logger.HytaleLogger;
import me.almana.moderationplus.ModerationPlus;

import java.util.logging.Level;

public class WebPanelBootstrap {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    public void init(ModerationPlus plugin) {
        logger.at(Level.INFO).log("Web Panel integration enabled. Initializing...");

        try {
            me.almana.moderationplus.storage.StorageManager.ServerIdentity identity = plugin.getStorageManager()
                    .getOrGenerateServerIdentity();
            logger.at(Level.INFO).log("Web Panel Identity loaded. Server ID: %s", identity.serverId());

            if (identity.isClaimed()) {
                logger.at(Level.INFO).log("Web Panel Status: CLAIMED");
            } else {
                String token = plugin.getStorageManager().getOrGenerateClaimToken();
                logger.at(Level.INFO).log("Web Panel Status: UNCLAIMED");
                logger.at(Level.INFO).log("CLAIM TOKEN: %s", token);
                logger.at(Level.WARNING).log("Use this token to claim your server in the web panel.");
            }
        } catch (Exception e) {
            logger.at(Level.SEVERE).withCause(e).log("Failed to load Web Panel Identity");
        }
    }
}
