package httpcommunicationmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megacrit.cardcrawl.core.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes gameplay logs in JSONL format (JSON Lines).
 * Creates one .jsonl file per game with filename format: {timestamp}-{seed}.jsonl
 * Each line in the file is a JSON object representing a (state_before, action, state_after) tuple.
 */
public class JSONLLogger {
    private static final Logger logger = LogManager.getLogger(JSONLLogger.class.getName());
    private static final Gson gson = new GsonBuilder().create();
    private static BufferedWriter writer = null;
    private static String currentLogFilePath = null;
    private static final DateTimeFormatter filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Starts a new log file for a new game.
     * File will be created in the AGENT_LOG_PATH directory with format: {timestamp}-{seed}.jsonl
     *
     * @param seed The game seed
     */
    public static void startNewGame(long seed) {
        closeCurrentLog();

        String logDir = System.getenv("AGENT_LOG_PATH");
        if (logDir == null || logDir.isEmpty()) {
            logDir = ".";
            logger.warn("AGENT_LOG_PATH not set, using current directory");
        }

        try {
            // Create directory if it doesn't exist
            Path dirPath = Paths.get(logDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                logger.info("Created log directory: " + logDir);
            }

            // Generate filename: timestamp-seed.jsonl
            String timestamp = LocalDateTime.now().format(filenameFormatter);
            String filename = timestamp + "-" + seed + ".jsonl";
            currentLogFilePath = Paths.get(logDir, filename).toString();

            // Create new writer
            writer = new BufferedWriter(new FileWriter(currentLogFilePath, false));
            logger.info("Started new JSONL log file: " + currentLogFilePath);

        } catch (IOException e) {
            logger.error("Failed to create JSONL log file: " + e.getMessage(), e);
            writer = null;
            currentLogFilePath = null;
        }
    }

    /**
     * Logs a single action tuple: (state_before, action, state_after)
     *
     * @param stateBefore The game state before the action (JSON string)
     * @param action The action taken
     * @param stateAfter The game state after the action (JSON string)
     */
    public static void logAction(String stateBefore, String action, String stateAfter) {
        if (writer == null) {
            logger.warn("Cannot log action - no log file open");
            return;
        }

        try {
            // Create the JSON object for this entry
            Map<String, Object> entry = new HashMap<>();
            entry.put("state_before", stateBefore);
            entry.put("action", action);
            entry.put("state_after", stateAfter);
            entry.put("timestamp", LocalDateTime.now().toString());

            // Write as a single line of JSON
            String jsonLine = gson.toJson(entry);
            writer.write(jsonLine);
            writer.newLine();
            writer.flush(); // Flush immediately for real-time logging

            logger.debug("Logged action: " + action);

        } catch (IOException e) {
            logger.error("Failed to write to JSONL log: " + e.getMessage(), e);
        }
    }

    /**
     * Closes the current log file.
     */
    public static void closeCurrentLog() {
        if (writer != null) {
            try {
                writer.close();
                logger.info("Closed JSONL log file: " + currentLogFilePath);
            } catch (IOException e) {
                logger.error("Error closing JSONL log file: " + e.getMessage(), e);
            }
            writer = null;
            currentLogFilePath = null;
        }
    }

    /**
     * Gets the path to the current log file.
     *
     * @return The current log file path, or null if no log is active
     */
    public static String getCurrentLogFilePath() {
        return currentLogFilePath;
    }

    /**
     * Checks if a log file is currently open.
     *
     * @return true if a log file is open, false otherwise
     */
    public static boolean isLogging() {
        return writer != null;
    }
}
