package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thin wrapper around JSONLLogger for logging actions and states.
 * Coordinates between the agent actions and the JSONL logger.
 */
public class ActionLogger {
    private static final Logger logger = LogManager.getLogger(ActionLogger.class.getName());

    /**
     * Logs an action that was just taken.
     * Writes the action directly to the JSONL log.
     *
     * @param action The action that was taken (as JSON string)
     */
    public static void logAction(String action) {
        JSONLLogger.logAction(action);
        logger.debug("Logged action: " + action);
    }

    /**
     * Called when a new stable state is reached.
     * Logs the current game state to the JSONL log.
     *
     * @param state The current game state (as JSON string)
     */
    public static void completeActionLog(String state) {
        JSONLLogger.logState(state);
        logger.debug("Logged state");
    }

    /**
     * Clears any pending action without logging it.
     * This is now a no-op since we don't track pending state anymore.
     * Kept for backward compatibility.
     */
    public static void clearPending() {
        // No-op: we no longer track pending state
        logger.debug("clearPending called (no-op)");
    }
}
