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
 * Creates one .jsonl file per game with filename format: {timestamp}.jsonl
 * Format: metadata header, then alternating game_state and action lines.
 * First line: {"type":"game_start","seed":...,"timestamp":"..."}
 * Second line: initial game state
 * Then alternates: action, game_state, action, game_state, ...
 * Final line before game_end: final game state
 * Last line: {"type":"game_end","timestamp":"..."}
 */
public class JSONLLogger {
    private static final Logger logger = LogManager.getLogger(JSONLLogger.class.getName());
    private static final Gson gson = new GsonBuilder().create();
    private static BufferedWriter writer = null;
    private static String currentLogFilePath = null;
    private static final DateTimeFormatter filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static long currentSeed = 0;

    /**
     * Starts a new log file for a new game.
     * File will be created in the LOG_DIR directory with format: {timestamp}.jsonl
     * Writes metadata header line: {"type": "game_start", "seed": <seed>, "timestamp": "..."}
     * Note: Initial game state should be logged separately after calling this method.
     *
     * @param seed The game seed
     */
    public static void startNewGame(long seed) {
        closeCurrentLog();

        currentSeed = seed;

        String logDir = System.getenv("LOG_DIR");
        if (logDir == null || logDir.isEmpty()) {
            logDir = ".";
            logger.warn("LOG_DIR not set, using current directory");
        }

        try {
            // Create directory if it doesn't exist
            Path dirPath = Paths.get(logDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                logger.info("Created log directory: " + logDir);
            }

            // Generate filename: timestamp.jsonl (no seed in filename)
            String timestamp = LocalDateTime.now().format(filenameFormatter);
            String filename = timestamp + ".jsonl";
            currentLogFilePath = Paths.get(logDir, filename).toString();

            // Create new writer
            writer = new BufferedWriter(new FileWriter(currentLogFilePath, false));

            // Write metadata header line
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "game_start");
            metadata.put("seed", seed);
            metadata.put("timestamp", LocalDateTime.now().toString());
            String metadataLine = gson.toJson(metadata);
            writer.write(metadataLine);
            writer.newLine();
            writer.flush();

            logger.info("Started new JSONL log file: " + currentLogFilePath);

        } catch (IOException e) {
            logger.error("Failed to create JSONL log file: " + e.getMessage(), e);
            writer = null;
            currentLogFilePath = null;
        }
    }

    /**
     * Logs a game state as a JSON line.
     *
     * @param gameState The game state as a JSON string
     */
    public static void logState(String gameState) {
        if (writer == null) {
            logger.warn("Cannot log state - no log file open");
            return;
        }

        try {
            writer.write(gameState);
            writer.newLine();
            writer.flush(); // Flush immediately for real-time logging

            logger.debug("Logged game state");

        } catch (IOException e) {
            logger.error("Failed to write state to JSONL log: " + e.getMessage(), e);
        }
    }

    /**
     * Logs an action as a JSON line.
     *
     * @param action The action as a JSON string
     */
    public static void logAction(String action) {
        if (writer == null) {
            logger.warn("Cannot log action - no log file open");
            return;
        }

        try {
            writer.write(action);
            writer.newLine();
            writer.flush(); // Flush immediately for real-time logging

            logger.debug("Logged action: " + action);

        } catch (IOException e) {
            logger.error("Failed to write action to JSONL log: " + e.getMessage(), e);
        }
    }

    /**
     * Ends the current game log by writing a game_end metadata line, then closes the file.
     * Should be called when the game ends (death or victory).
     */
    public static void endGame() {
        if (writer == null) {
            logger.warn("Cannot end game - no log file open");
            return;
        }

        try {
            // Write game_end metadata line
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "game_end");
            metadata.put("timestamp", LocalDateTime.now().toString());
            String metadataLine = gson.toJson(metadata);
            writer.write(metadataLine);
            writer.newLine();
            writer.flush();

            logger.info("Wrote game_end metadata to: " + currentLogFilePath);

        } catch (IOException e) {
            logger.error("Failed to write game_end metadata: " + e.getMessage(), e);
        }

        closeCurrentLog();
    }

    /**
     * Closes the current log file without writing game_end metadata.
     * Typically called internally or when starting a new game.
     */
    private static void closeCurrentLog() {
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
