package me.almana.moderationplus.api;

/**
 * Main entry point for the ModerationPlus API information.
 */
@Since("1.0.0")
public interface ModerationAPI {

    /**
     * Gets the current semantic version of the ModerationPlus API.
     * 
     * @return The version string (e.g., "1.0.0").
     */
    static String getVersion() {
        return "1.0.0";
    }
}
