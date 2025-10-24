package httpcommunicationmod;

/**
 * Observer interface for game phase transitions.
 *
 * Implementations of this interface will be notified by GameStateTracker when the game
 * transitions from one phase to another. This follows the Observer pattern to enable
 * decoupled lifecycle management and event-driven architecture.
 *
 * Note: This interface is effectively a functional interface with a single abstract method,
 * following Java 8 conventions. Implementations will be called only when the game phase
 * actually changes, not on every frame.
 */
public interface PhaseChangeListener {
    /**
     * Called when the game phase transitions from one state to another.
     *
     * This method is invoked only when the game phase actually changes (e.g., from IN_DUNGEON
     * to IN_COMBAT), not on every frame. Implementations should handle the phase change
     * appropriately for their specific use case.
     *
     * @param oldPhase The phase the game was in before the transition
     * @param newPhase The phase the game has transitioned to
     */
    void onPhaseChanged(GamePhase oldPhase, GamePhase newPhase);
}
