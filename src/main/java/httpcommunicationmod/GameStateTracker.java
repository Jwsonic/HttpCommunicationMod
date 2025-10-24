package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe singleton tracker for game phase transitions.
 *
 * This class manages the current game phase and notifies registered observers whenever
 * the phase changes. It implements the Observer pattern to enable decoupled, event-driven
 * lifecycle management throughout the application.
 *
 * The notifyPhaseChange() method is idempotent - it can be safely called every frame
 * (or multiple times in succession) without triggering duplicate notifications. Phase
 * change notifications only occur when the phase actually transitions to a different state.
 *
 * Thread Safety: This singleton uses eager initialization and maintains a thread-safe
 * listener list for multi-threaded environments.
 */
public class GameStateTracker {
    private static final GameStateTracker instance = new GameStateTracker();
    private static final Logger logger = LogManager.getLogger(GameStateTracker.class.getName());

    private GamePhase currentPhase = GamePhase.UNKNOWN;
    private final List<PhaseChangeListener> listeners = new ArrayList<>();

    /**
     * Private constructor for singleton pattern.
     */
    private GameStateTracker() {
        // Intentionally empty - logger cannot be used here due to static initialization order
    }

    /**
     * Returns the singleton instance of GameStateTracker.
     * Uses eager initialization pattern for thread safety.
     *
     * @return The singleton instance of GameStateTracker
     */
    public static GameStateTracker getInstance() {
        return instance;
    }

    /**
     * Notifies the tracker of a potential phase change.
     *
     * This method is idempotent and can be safely called every frame or multiple times
     * in succession. It only notifies listeners when the phase actually transitions to
     * a different state. If the new phase equals the current phase, this method returns
     * immediately without notifying listeners.
     *
     * @param newPhase The new game phase being transitioned to
     */
    public void notifyPhaseChange(GamePhase newPhase) {
        // Early return if phase hasn't changed - idempotent behavior
        if (newPhase == currentPhase) {
            logger.trace("notifyPhaseChange() called with same phase (idempotent rejection): {} [Thread: {}]",
                newPhase, Thread.currentThread().getName());
            return;
        }

        // Record the old phase for logging and notification
        GamePhase oldPhase = currentPhase;

        // Update the current phase
        currentPhase = newPhase;

        // Log the phase transition with thread info for race condition diagnosis
        logger.info("Phase transition: {} -> {} [Thread: {}]",
            oldPhase, newPhase, Thread.currentThread().getName());

        // Notify all registered listeners of the phase change
        for (PhaseChangeListener listener : listeners) {
            logger.trace("Notifying listener: {}", listener.getClass().getSimpleName());
            listener.onPhaseChanged(oldPhase, newPhase);
        }
    }

    /**
     * Registers a listener to be notified of phase changes.
     *
     * The listener will be called via onPhaseChanged() whenever the game phase
     * transitions. Multiple listeners can be registered and will all be notified
     * in the order they were added.
     *
     * @param listener The listener to register for phase change notifications
     */
    public void addListener(PhaseChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Returns the current game phase.
     *
     * @return The current GamePhase, or GamePhase.UNKNOWN if not yet set
     */
    public GamePhase getCurrentPhase() {
        return currentPhase;
    }
}
