package httpcommunicationmod;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.random.Random;
import httpcommunicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

@SpireInitializer
public class HttpCommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber,
        PostDungeonUpdateSubscriber, PreUpdateSubscriber, OnStateChangeSubscriber {

    private static final Logger logger = LogManager.getLogger(HttpCommunicationMod.class.getName());
    private static final String MODNAME = "Agent Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod uses an agent to autonomously play Slay the Spire and log gameplay.";
    public static boolean mustSendGameState = false;
    private static ArrayList<OnStateChangeSubscriber> onStateChangeSubscribers;

    private static AgentManager agentManager;
    private static boolean waitingForMainMenu = true;
    private static boolean gameStarted = false;

    public HttpCommunicationMod() {
        BaseMod.subscribe(this);
        onStateChangeSubscribers = new ArrayList<>();
        HttpCommunicationMod.subscribe(this);

        initializeAgent();
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
        // Check for main menu and auto-start game
        if (waitingForMainMenu && CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT &&
            CardCrawlGame.mainMenuScreen != null && !gameStarted) {
            logger.info("Main menu detected - starting game automatically");
            startGame();
            waitingForMainMenu = false;
            gameStarted = true;
        }

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
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();

        ModLabel agentLabel = new ModLabel(
                "Agent: RandomAgent (autonomous play)",
                350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                });
        settingsPanel.addUIElement(agentLabel);

        ModLabel logLabel = new ModLabel(
                "", 350, 550, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    if (JSONLLogger.isLogging()) {
                        modLabel.text = "Current log: " + JSONLLogger.getCurrentLogFilePath();
                    } else {
                        modLabel.text = "No active game log";
                    }
                });
        settingsPanel.addUIElement(logLabel);

        ModLabel envVarLabel = new ModLabel(
                "Configuration via environment variable: AGENT_LOG_PATH (directory for JSONL logs)",
                350, 500, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                });
        settingsPanel.addUIElement(envVarLabel);

        BaseMod.registerModBadge(ImageMaster.loadImage("Icon.png"), "Agent Communication Mod", "Forgotten Arbiter", null,
                settingsPanel);
    }

    private void initializeAgent() {
        logger.info("Initializing agent system...");
        agentManager = new AgentManager();
        GameStateListener.setAgentManager(agentManager);
        logger.info("Agent system initialized");
    }

    private void startGame() {
        logger.info("Starting new game...");

        // Choose a random character
        AbstractPlayer.PlayerClass[] classes = {
            AbstractPlayer.PlayerClass.IRONCLAD,
            AbstractPlayer.PlayerClass.THE_SILENT,
            AbstractPlayer.PlayerClass.DEFECT,
            AbstractPlayer.PlayerClass.WATCHER
        };
        java.util.Random rand = new java.util.Random();
        AbstractPlayer.PlayerClass selectedClass = classes[rand.nextInt(classes.length)];

        // Generate seed
        long seed = SeedHelper.generateUnoffensiveSeed(new Random(System.nanoTime()));
        Settings.seed = seed;
        Settings.seedSet = false;
        AbstractDungeon.generateSeeds();

        // Set ascension level to 0
        AbstractDungeon.ascensionLevel = 0;
        AbstractDungeon.isAscensionMode = false;

        // Start the game
        CardCrawlGame.startOver = true;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CharacterManager manager = new CharacterManager();
        manager.setChosenCharacter(selectedClass);
        CardCrawlGame.chosenCharacter = selectedClass;

        // Reset state variables
        GameStateListener.resetStateVariables();
        GameStateConverter.resetStateId();
        ActionLogger.clearPending();

        // Start JSONL logging for this game
        JSONLLogger.startNewGame(seed);

        // Enable the agent
        agentManager.enable();

        logger.info("Game started: " + selectedClass.name() + " with seed " + seed);
    }

    public static void dispose() {
        logger.info("Shutting down agent system...");
        if (agentManager != null) {
            agentManager.disable();
        }
        JSONLLogger.closeCurrentLog();
    }

    public static String getCurrentGameState() {
        return GameStateConverter.getCommunicationState();
    }
}