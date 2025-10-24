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
 * game is always started regardless of the endless runs setting. Subsequent games are started only
 * if endless runs are enabled and the previous game ended (reached death or victory screen).
 *
 * <p><strong>Thread Safety:</strong> This class maintains mutable state (hasStartedFirstGame,
 * previousPhase) but is designed to be used in a single-threaded game loop context. If used in
 * a multi-threaded environment, synchronization would be required.
 *
 * @author HttpCommunicationMod Team
 * @version 1.0
 */
public class GameLifecycleController implements PhaseChangeListener {

    private static final Logger logger = LogManager.getLogger(GameLifecycleController.class);

    private final boolean endlessRunsEnabled;
    private boolean hasStartedFirstGame = false;
    private GamePhase previousPhase = GamePhase.UNKNOWN;

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
     * <p>After handling the appropriate actions, the {@code newPhase} is stored as the previous phase
     * for future transition decisions.
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

        // Always update previousPhase after handling to track state for next transition
        previousPhase = newPhase;
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
     *   <li>Whether the first game has been started yet</li>
     *   <li>Whether we just came from a game end (death or victory screen)</li>
     *   <li>Whether endless runs are enabled in the configuration</li>
     * </ul>
     *
     * <p><strong>Decision Logic:</strong>
     * <ul>
     *   <li>If the first game has never been started: Always start the first game</li>
     *   <li>If endless runs are enabled AND the previous phase was a game end: Start a new game</li>
     *   <li>Otherwise: Do not start a new game</li>
     * </ul>
     *
     * <p>The {@link #hasStartedFirstGame} flag is only set to {@code true} after successfully
     * starting the first game, ensuring that even if game startup fails, the next main menu
     * transition will attempt to start a game again.
     */
    private void handleMainMenuReached() {
        // Check if we just came from a game end (death or victory)
        boolean justEndedGame = (previousPhase == GamePhase.DEATH_SCREEN ||
                                 previousPhase == GamePhase.VICTORY_SCREEN);

        // Log detailed state information for diagnostics
        logger.info("Reached main menu. Previous phase: {} (game end: {}), First game started: {}, Endless runs: {}",
                previousPhase, justEndedGame, hasStartedFirstGame, endlessRunsEnabled);

        // Debug: Inspect actual game state to verify phase detection accuracy
        try {
            CardCrawlGame.GameMode currentMode = CardCrawlGame.mode;
            AbstractDungeon.CurrentScreen dungeonScreen = AbstractDungeon.screen;
            logger.debug("Actual game state - CardCrawlGame.mode: {}, AbstractDungeon.screen: {}",
                currentMode, dungeonScreen);
        } catch (Exception e) {
            logger.debug("Could not inspect game state (may be null during transition): {}", e.getMessage());
        }

        // Decide if we should start a new game
        boolean shouldStart = false;

        if (!hasStartedFirstGame) {
            // Always start the first game
            shouldStart = true;
            logger.info("Starting first game");
        } else if (endlessRunsEnabled && justEndedGame) {
            // Restart if endless runs enabled and we just finished a game
            shouldStart = true;
            logger.info("Endless runs enabled - restarting game");
        } else {
            logger.info("Not starting game (endless runs disabled or didn't just finish game)");
        }

        if (shouldStart) {
            startNewGame();
            hasStartedFirstGame = true;
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
