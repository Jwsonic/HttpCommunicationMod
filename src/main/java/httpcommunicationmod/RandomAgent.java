package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Random;

/**
 * A simple agent implementation that randomly selects from available commands.
 * Useful for testing and as a baseline for more sophisticated agents.
 */
public class RandomAgent implements IAgent {
    private static final Logger logger = LogManager.getLogger(RandomAgent.class.getName());
    private final Random random;

    public RandomAgent() {
        this.random = new Random();
        logger.info("RandomAgent initialized");
    }

    @Override
    public String chooseAction(String state, ArrayList<String> availableCommands) {
        if (availableCommands == null || availableCommands.isEmpty()) {
            logger.warn("No available commands to choose from");
            return null;
        }

        int index = random.nextInt(availableCommands.size());
        String chosen = availableCommands.get(index);

        logger.debug("RandomAgent chose: " + chosen + " from " + availableCommands.size() + " options");

        return chosen;
    }
}
