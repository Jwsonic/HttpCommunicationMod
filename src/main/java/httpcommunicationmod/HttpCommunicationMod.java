package httpcommunicationmod;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import httpcommunicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

@SpireInitializer
public class HttpCommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber,
        PostDungeonUpdateSubscriber, PreUpdateSubscriber, OnStateChangeSubscriber {

    private static final Logger logger = LogManager.getLogger(HttpCommunicationMod.class.getName());
    private static final String MODNAME = "HTTP Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with external programs via HTTP API to play Slay the Spire.";
    public static boolean mustSendGameState = false;
    private static ArrayList<OnStateChangeSubscriber> onStateChangeSubscribers;

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";
    private static final boolean DEFAULT_VERBOSITY = true;
    private static final String DEFAULT_LOG_PATH = "http_mod.log";

    private static WebServer webServer;
    private static String logFilePath;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public HttpCommunicationMod() {
        BaseMod.subscribe(this);
        onStateChangeSubscribers = new ArrayList<>();
        HttpCommunicationMod.subscribe(this);

        initializeLogFile();
        startWebServer();
    }

    public static void initialize() {
        HttpCommunicationMod mod = new HttpCommunicationMod();
    }

    public void receivePreUpdate() {
        // HTTP-based communication doesn't need to check for subprocess messages
        // Commands arrive via HTTP POST requests instead
    }

    public static void subscribe(OnStateChangeSubscriber sub) {
        onStateChangeSubscribers.add(sub);
    }

    public static void publishOnGameStateChange() {
        for (OnStateChangeSubscriber sub : onStateChangeSubscribers) {
            sub.receiveOnStateChange();
        }
    }

    public void receiveOnStateChange() {
        // State changes are now handled via HTTP GET /state requests
        // No automatic sending to subprocess needed
    }

    public static void queueCommand(String command) {
        try {
            boolean stateChanged = CommandExecutor.executeCommand(command);
            if (stateChanged) {
                GameStateListener.registerCommandExecution();
            }
        } catch (InvalidCommandException e) {
            logger.error("Error executing command: " + e.getMessage());
        }
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
    }

    public void receivePostUpdate() {
        if (!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if (mustSendGameState) {
            publishOnGameStateChange();
            mustSendGameState = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();

        ModLabel portLabel = new ModLabel(
                "", 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("Web Server Port: %d", getWebServerPort());
                });
        settingsPanel.addUIElement(portLabel);

        ModLabel hostLabel = new ModLabel(
                "", 350, 550, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("Web Server Host: %s", getWebServerHost());
                });
        settingsPanel.addUIElement(hostLabel);

        ModButton restartServerButton = new ModButton(
                350, 650, settingsPanel, modButton -> {
                    BaseMod.modSettingsUp = false;
                    restartWebServer();
                });
        settingsPanel.addUIElement(restartServerButton);

        ModLabel restartServerLabel = new ModLabel(
                "Restart Web Server",
                475, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                });
        settingsPanel.addUIElement(restartServerLabel);

        ModLabel envVarLabel = new ModLabel(
                "Configuration via environment variables: HTTP_MOD_PORT, HTTP_MOD_HOST, HTTP_MOD_LOG_PATH",
                350, 500, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                });
        settingsPanel.addUIElement(envVarLabel);

        BaseMod.registerModBadge(ImageMaster.loadImage("Icon.png"), "HTTP Communication Mod", "Forgotten Arbiter", null,
                settingsPanel);
    }

    private boolean startWebServer() {
        try {
            webServer = new WebServer();
            webServer.start(getWebServerHost(), getWebServerPort());
            logger.info(
                    "HTTP Communication Mod web server started on " + getWebServerHost() + ":" + getWebServerPort());
            return true;
        } catch (Exception e) {
            logger.error("Failed to start web server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void restartWebServer() {
        if (webServer != null) {
            webServer.stop();
        }
        startWebServer();
    }

    public static void dispose() {
        logger.info("Shutting down web server...");
        if (webServer != null) {
            webServer.stop();
        }
    }

    private static int getWebServerPort() {
        String envPort = System.getenv("HTTP_MOD_PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                int port = Integer.parseInt(envPort.trim());
                logger.info("Using HTTP_MOD_PORT environment variable: " + port);
                return port;
            } catch (NumberFormatException e) {
                logger.warn("Invalid HTTP_MOD_PORT value '" + envPort + "', using default: " + DEFAULT_PORT);
            }
        }

        logger.info("Using default port: " + DEFAULT_PORT);
        return DEFAULT_PORT;
    }

    private static String getWebServerHost() {
        String envHost = System.getenv("HTTP_MOD_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            logger.info("Using HTTP_MOD_HOST environment variable: " + envHost.trim());
            return envHost.trim();
        }

        logger.info("Using default host: " + DEFAULT_HOST);
        return DEFAULT_HOST;
    }

    private static boolean getVerbosityOption() {
        return DEFAULT_VERBOSITY;
    }

    public static String getCurrentGameState() {
        return GameStateConverter.getCommunicationState();
    }

    private void initializeLogFile() {
        String envLogPath = System.getenv("HTTP_MOD_LOG_PATH");
        if (envLogPath != null && !envLogPath.trim().isEmpty()) {
            logFilePath = envLogPath.trim();
            logger.info("Using HTTP_MOD_LOG_PATH environment variable: " + logFilePath);
        } else {
            logFilePath = DEFAULT_LOG_PATH;
            logger.info("HTTP_MOD_LOG_PATH environment variable not set, using default: " + logFilePath);
        }

        try {
            if (logFilePath.contains("/") || logFilePath.contains("\\")) {
                String directory = Paths.get(logFilePath).getParent().toString();
                Files.createDirectories(Paths.get(directory));
            }
            logger.info("File logging initialized with path: " + logFilePath);
        } catch (IOException e) {
            logger.error("Failed to initialize log file directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void logGameState(String gameState) {
        if (logFilePath == null)
            return;

        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] GAME_STATE: %s%n", timestamp, gameState);
        writeToLogFile(logEntry);
    }

    public static void logCommand(String command) {
        if (logFilePath == null)
            return;

        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format("[%s] COMMAND: %s%n", timestamp, command);
        writeToLogFile(logEntry);
    }

    private static void writeToLogFile(String content) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
            writer.print(content);
        } catch (IOException e) {
            logger.error("Failed to write to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getCurrentLogPath() {
        return logFilePath;
    }
}