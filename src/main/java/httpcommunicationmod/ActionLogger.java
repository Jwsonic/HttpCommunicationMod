package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Captures (state_A, action, state_B) tuples and writes them to JSONL logs.
 * Coordinates between the agent actions and the JSONL logger.
 */
public class ActionLogger {
    private static final Logger logger = LogManager.getLogger(ActionLogger.class.getName());
    private static String pendingStateBefore = null;
    private static String pendingAction = null;

    /**
     * Logs an action that was just taken.
     * Stores the state before and the action, then waits for the next state update
     * to complete the tuple.
     *
     * @param stateBefore The game state before the action
     * @param action The action that was taken
     */
    public static void logAction(String stateBefore, String action) {
        pendingStateBefore = stateBefore;
        pendingAction = action;
        logger.debug("Pending action log: " + action);
    }

    /**
     * Called when a new stable state is reached.
     * If there's a pending action, logs the complete (state_before, action, state_after) tuple.
     *
     * @param stateAfter The current game state after the action
     */
    public static void completeActionLog(String stateAfter) {
        if (pendingStateBefore != null && pendingAction != null) {
            JSONLLogger.logAction(pendingStateBefore, pendingAction, stateAfter);
            logger.debug("Completed action log: " + pendingAction);
            pendingStateBefore = null;
            pendingAction = null;
        }
    }

    /**
     * Checks if there's a pending action waiting to be logged.
     *
     * @return true if an action is pending, false otherwise
     */
    public static boolean hasPendingAction() {
        return pendingStateBefore != null && pendingAction != null;
    }

    /**
     * Clears any pending action without logging it.
     * Used when starting a new game or resetting state.
     */
    public static void clearPending() {
        if (pendingStateBefore != null || pendingAction != null) {
            logger.debug("Clearing pending action log");
        }
        pendingStateBefore = null;
        pendingAction = null;
    }
}
