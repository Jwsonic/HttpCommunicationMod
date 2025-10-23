package httpcommunicationmod;

import java.util.ArrayList;

/**
 * Interface for agent implementations that play Slay the Spire.
 * Agents receive the current game state and available commands,
 * then choose which action to take.
 */
public interface IAgent {
    /**
     * Choose an action from the list of available commands.
     *
     * @param state The current game state as a JSON string
     * @param availableCommands List of valid commands that can be executed (e.g., "play 1", "end", "choose 0")
     * @return The chosen command string, must be one of the availableCommands
     */
    String chooseAction(String state, ArrayList<String> availableCommands);
}
