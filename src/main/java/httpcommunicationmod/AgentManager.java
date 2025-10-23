package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Manages the agent lifecycle and coordinates decision-making with the game state.
 * When the game is ready for player input, this manager asks the agent to choose an action
 * and then executes it.
 */
public class AgentManager {
    private static final Logger logger = LogManager.getLogger(AgentManager.class.getName());
    private IAgent agent;
    private boolean enabled = false;

    public AgentManager() {
        this.agent = AgentConfig.createAgent();
        logger.info("AgentManager initialized with agent: " + agent.getClass().getSimpleName());
    }

    /**
     * Enable the agent to start making decisions
     */
    public void enable() {
        this.enabled = true;
        logger.info("Agent enabled");
    }

    /**
     * Disable the agent from making decisions
     */
    public void disable() {
        this.enabled = false;
        logger.info("Agent disabled");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when the game is ready for a player action.
     * Gets the current state, asks the agent to choose an action, and executes it.
     */
    public void makeDecision() {
        if (!enabled) {
            return;
        }

        try {
            // Get current game state
            String state = GameStateConverter.getCommunicationState();

            // Get available commands
            ArrayList<String> availableCommands = CommandExecutor.getAvailableCommands();

            if (availableCommands.isEmpty()) {
                logger.warn("No available commands - agent cannot act");
                return;
            }

            // Log state before action
            String stateBefore = state;

            // Ask agent to choose action
            String chosenAction = agent.chooseAction(state, availableCommands);

            if (chosenAction == null || chosenAction.isEmpty()) {
                logger.error("Agent returned null or empty action");
                return;
            }

            if (!availableCommands.contains(chosenAction)) {
                logger.error("Agent chose invalid action: " + chosenAction + ". Available: " + availableCommands);
                return;
            }

            logger.info("Agent chose action: " + chosenAction);

            // Execute the chosen action
            boolean success = CommandExecutor.executeCommand(chosenAction);

            if (success) {
                GameStateListener.registerCommandExecution();

                // Log the action tuple after a brief delay to get the resulting state
                // This will be handled by ActionLogger
                ActionLogger.logAction(stateBefore, chosenAction);
            } else {
                logger.error("Failed to execute action: " + chosenAction);
            }

        } catch (InvalidCommandException e) {
            logger.error("Invalid command exception during agent decision: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during agent decision: " + e.getMessage(), e);
        }
    }
}
