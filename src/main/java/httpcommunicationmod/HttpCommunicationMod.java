package httpcommunicationmod;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import httpcommunicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

@SpireInitializer
public class HttpCommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber, OnStateChangeSubscriber {

    private static final Logger logger = LogManager.getLogger(HttpCommunicationMod.class.getName());
    private static final String MODNAME = "HTTP Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with external programs via HTTP API to play Slay the Spire.";
    public static boolean mustSendGameState = false;
    private static ArrayList<OnStateChangeSubscriber> onStateChangeSubscribers;

    private static SpireConfig communicationConfig;
    private static final String WEB_SERVER_PORT_OPTION = "webServerPort";
    private static final String WEB_SERVER_HOST_OPTION = "webServerHost";
    private static final String VERBOSE_OPTION = "verbose";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "localhost";
    private static final boolean DEFAULT_VERBOSITY = true;

    private static WebServer webServer;

    public HttpCommunicationMod(){
        BaseMod.subscribe(this);
        onStateChangeSubscribers = new ArrayList<>();
        HttpCommunicationMod.subscribe(this);

        try {
            Properties defaults = new Properties();
            defaults.put(WEB_SERVER_PORT_OPTION, Integer.toString(DEFAULT_PORT));
            defaults.put(WEB_SERVER_HOST_OPTION, DEFAULT_HOST);
            defaults.put(VERBOSE_OPTION, Boolean.toString(DEFAULT_VERBOSITY));
            communicationConfig = new SpireConfig("HttpCommunicationMod", "config", defaults);
            communicationConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
        for(OnStateChangeSubscriber sub : onStateChangeSubscribers) {
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
            if(stateChanged) {
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
        if(!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if(mustSendGameState) {
            publishOnGameStateChange();
            mustSendGameState = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if(AbstractDungeon.getCurrRoom().isBattleOver) {
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
                settingsPanel, modLabel -> {});
        settingsPanel.addUIElement(restartServerLabel);

        ModLabeledToggleButton verbosityOption = new ModLabeledToggleButton(
                "Suppress verbose log output",
                350, 500, Settings.CREAM_COLOR, FontHelper.charDescFont,
                getVerbosityOption(), settingsPanel, modLabel -> {},
                modToggleButton -> {
                    if (communicationConfig != null) {
                        communicationConfig.setBool(VERBOSE_OPTION, modToggleButton.enabled);
                        try {
                            communicationConfig.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        settingsPanel.addUIElement(verbosityOption);

        BaseMod.registerModBadge(ImageMaster.loadImage("Icon.png"), "HTTP Communication Mod", "Forgotten Arbiter", null, settingsPanel);
    }

    private boolean startWebServer() {
        try {
            webServer = new WebServer();
            webServer.start(getWebServerHost(), getWebServerPort());
            logger.info("HTTP Communication Mod web server started on " + getWebServerHost() + ":" + getWebServerPort());
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
        if(webServer != null) {
            webServer.stop();
        }
    }

    private static int getWebServerPort() {
        if (communicationConfig == null) {
            return DEFAULT_PORT;
        }
        return communicationConfig.getInt(WEB_SERVER_PORT_OPTION);
    }

    private static String getWebServerHost() {
        if (communicationConfig == null) {
            return DEFAULT_HOST;
        }
        return communicationConfig.getString(WEB_SERVER_HOST_OPTION);
    }

    private static boolean getVerbosityOption() {
        if (communicationConfig == null) {
            return DEFAULT_VERBOSITY;
        }
        return communicationConfig.getBool(VERBOSE_OPTION);
    }

    public static String getCurrentGameState() {
        return GameStateConverter.getCommunicationState();
    }
}