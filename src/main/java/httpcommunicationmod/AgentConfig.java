package httpcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration for creating agent instances.
 * Currently hardcoded to always return RandomAgent.
 * Future versions could read from environment variables or config files.
 */
public class AgentConfig {
    private static final Logger logger = LogManager.getLogger(AgentConfig.class.getName());

    /**
     * Creates and returns an agent instance.
     * Currently always returns a RandomAgent.
     *
     * @return A new agent instance
     */
    public static IAgent createAgent() {
        logger.info("Creating RandomAgent");
        return new RandomAgent();
    }
}
