package httpcommunicationmod;

import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Core controller for managing game lifecycle and endless runs functionality.
 *
 * <p>This class implements the {@link PhaseChangeListener} interface to observe and respond to
 * game phase transitions. It is responsible for:
 * <ul>
 *   <li>Tracking game lifecycle state (first game started, game ended, etc.)</li>
 *   <li>Managing endless runs functionality based on environment configuration</li>
 *   <li>Handling game end scenarios (death or victory) by logging final state</li>
 *   <li>Deciding when to start new games based on phase transitions</li>
 *   <li>Initiating new game runs through {@link HttpCommunicationMod}</li>
 * </ul>
 *
 * <p><strong>Architecture:</strong> This class acts as an Observer in the Observer design pattern,
 * receiving notifications of phase changes and executing the appropriate business logic. It maintains
 * minimal state (only what is necessary for decision-making) and delegates specific actions to
 * appropriate collaborators like {@link JSONLLogger} and {@link GameStateConverter}.
 *
 * <p><strong>Endless Runs:</strong> When endless runs are enabled (via the {@code ENDLESS_RUNS}
 * environment variable), the controller will automatically restart games after they end. The first
 * game is always started when the main menu is reached for the first time. Subsequent games are
 * started only if endless runs are enabled.
 *
 * <p><strong>Thread Safety:</strong> This class maintains mutable state (hasStartedFirstGame)
 * but is designed to be used in a single-threaded game loop context. If used in
 * a multi-threaded environment, synchronization would be required.
 *
 * @author HttpCommunicationMod Team
 * @version 1.0
 */
public class GameLifecycleController implements PhaseChangeListener {

    private static final Logger logger = LogManager.getLogger(GameLifecycleController.class);

    private final boolean endlessRunsEnabled;
    private boolean hasStartedFirstGame = false;

    /**
     * Constructs a new GameLifecycleController instance.
     *
     * <p>Initializes the controller by reading the {@code ENDLESS_RUNS} environment variable
     * and logging the initial state. The endless runs setting is determined at construction time
     * and cannot be changed during the controller's lifetime.
     *
     * <p>The environment variable is expected to be a boolean string (e.g., "true", "false").
     * If the variable is not set or is null, it defaults to {@code false}.
     */
    public GameLifecycleController() {
        String envValue = System.getenv("ENDLESS_RUNS");
        this.endlessRunsEnabled = Boolean.parseBoolean(envValue != null ? envValue.trim() : "false");
        logger.info("GameLifecycleController initialized. Endless runs enabled: " + endlessRunsEnabled);
    }

    /**
     * Handles a phase change notification from the {@link PhaseChangeListener} interface.
     *
     * <p>This method is called whenever the game phase changes. It delegates to
     * {@link #handlePhaseTransition(GamePhase, GamePhase)} to perform the actual transition logic.
     *
     * @param oldPhase the previous game phase (before transition)
     * @param newPhase the new game phase (after transition)
     */
    @Override
    public void onPhaseChanged(GamePhase oldPhase, GamePhase newPhase) {
        handlePhaseTransition(oldPhase, newPhase);
    }

    /**
     * Processes a game phase transition and executes appropriate lifecycle actions.
     *
     * <p>This method examines the new phase and determines what lifecycle actions are needed:
     * <ul>
     *   <li>{@code DEATH_SCREEN} or {@code VICTORY_SCREEN}: Game has ended, log final state</li>
     *   <li>{@code MAIN_MENU}: Check if we should start a new game based on endless runs config</li>
     *   <li>Other phases: No special lifecycle handling required</li>
     * </ul>
     *
     * @param oldPhase the previous game phase
     * @param newPhase the new game phase after the transition
     */
    private void handlePhaseTransition(GamePhase oldPhase, GamePhase newPhase) {
        logger.info("Handling phase transition: {} -> {}", oldPhase, newPhase);

        switch (newPhase) {
            case DEATH_SCREEN:
            case VICTORY_SCREEN:
                handleGameEnd();
                break;
            case MAIN_MENU:
                handleMainMenuReached();
                break;
            default:
                // No special handling for other phases
                break;
        }
    }

    /**
     * Handles game end scenarios (death or victory).
     *
     * <p>When a game ends, this method:
     * <ol>
     *   <li>Retrieves the final game state using {@link GameStateConverter#getCommunicationState()}</li>
     *   <li>Logs the final state to the JSON lines log via {@link JSONLLogger#logState(String)}</li>
     *   <li>Closes the current game log via {@link JSONLLogger#endGame()}</li>
     * </ol>
     *
     * <p>This ensures that the final game state is properly recorded before the game is closed,
     * allowing external systems to see the terminal state of the run.
     */
    private void handleGameEnd() {
        logger.info("Game ended (death or victory). Logging final state and closing log.");

        // Get final game state
        String finalState = GameStateConverter.getCommunicationState();

        // Log final state
        JSONLLogger.logState(finalState);

        // End the game log
        JSONLLogger.endGame();

        logger.info("Final state logged and game log closed");
    }

    /**
     * Handles transitions to the main menu phase.
     *
     * <p>This method determines whether a new game should be started based on:
     * <ul>
     *   <li>Whether the first game has been started yet (always start first game)</li>
     *   <li>Whether endless runs are enabled for subsequent games</li>
     * </ul>
     *
     * <p><strong>Decision Logic:</strong> Start a new game if:
     * <ul>
     *   <li>The first game has never been started, OR</li>
     *   <li>Endless runs are enabled</li>
     * </ul>
     *
     * <p><strong>Simplified Approach:</strong> Since this is a fully automated bot with no human
     * interaction, we do not need to track previous phase states. We simply decide based on whether
     * we're starting the first game or have endless runs enabled. This decouples the logic from
     * fragile phase history tracking and makes the decision deterministic and robust.
     *
     * <p>The {@link #hasStartedFirstGame} flag is set to {@code true} after successfully
     * starting the first game, ensuring that even if game startup fails, the next main menu
     * transition will attempt to start a game again.
     */
    private void handleMainMenuReached() {
        logger.info("Reached main menu. First game started: {}, Endless runs: {}",
                hasStartedFirstGame, endlessRunsEnabled);

        // Simplified decision: start if this is the first game OR endless runs are enabled
        if (!hasStartedFirstGame || endlessRunsEnabled) {
            logger.info("Starting new game - First game: {}, Endless runs: {}",
                    !hasStartedFirstGame, endlessRunsEnabled);
            startNewGame();
            hasStartedFirstGame = true;
        } else {
            logger.info("Not starting game (endless runs disabled and already played first game)");
        }
    }

    /**
     * Initiates a new game run.
     *
     * <p>This method delegates to {@link HttpCommunicationMod#startGame()} to begin a new game.
     * It is called from {@link #handleMainMenuReached()} when the decision has been made to
     * start a new game.
     *
     * <p>Any errors or exceptions that occur during game startup will be handled by the
     * {@link HttpCommunicationMod#startGame()} method and its error handling chain.
     */
    private void startNewGame() {
        logger.info("Starting new game via HttpCommunicationMod.startGame()");
        HttpCommunicationMod.startGame();
    }
}
