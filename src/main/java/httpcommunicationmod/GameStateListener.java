package httpcommunicationmod;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.VictoryRoom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GameStateListener {
    private static final Logger logger = LogManager.getLogger(GameStateListener.class.getName());
    private static TurnStateDetector detector = new TurnStateDetector();
    private static boolean waitingForCommand = false;
    private static AgentManager agentManager = null;

    /**
     * Sets the agent manager to use for making decisions.
     */
    public static void setAgentManager(AgentManager manager) {
        agentManager = manager;
    }

    /**
     * Used to indicate that something (in game logic, not external command) has been done that will change the game state,
     * and hasStateChanged() should indicate a state change when the state next becomes stable.
     */
    public static void registerStateChange() {
        detector.registerStateChange();
        waitingForCommand = false;
    }

    /**
     * Used to tell hasStateChanged() to indicate a state change after a specified number of frames.
     * @param newTimeout The number of frames to wait
     */
    public static void setTimeout(int newTimeout) {
        detector.setTimeout(newTimeout);
    }

    /**
     * Used to indicate that an external command has been executed
     */
    public static void registerCommandExecution() {
        waitingForCommand = false;
        detector.registerCommandExecution();
    }

    /**
     * Prevents hasStateChanged() from indicating a state change until resumeStateUpdate() is called.
     */
    public static void blockStateUpdate() {
        detector.blockStateUpdate();
    }

    /**
     * Removes the block instantiated by blockStateChanged()
     */
    public static void resumeStateUpdate() {
        detector.resumeStateUpdate();
    }

    /**
     * Used by a patch in the game to signal the start of your turn. We do not care about state changes
     * when it is not our turn in combat, as we cannot take action until then.
     */
    public static void signalTurnStart() {
        detector.signalTurnStart();
    }

    /**
     * Used by patches in the game to signal the end of your turn (or the end of combat).
     */
    public static void signalTurnEnd() {
        detector.signalTurnEnd();
    }

    /**
     * Resets all state detection variables for the start of a new run.
     */
    public static void resetStateVariables() {
        detector.resetStateVariables();
        waitingForCommand = false;
    }

    /**
     * Creates a snapshot of the current game state for turn detection.
     */
    private static GameStateSnapshot createSnapshot() {
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();

        boolean isEventRoom = currentRoom instanceof EventRoom;
        boolean isNeowRoom = currentRoom instanceof NeowRoom;
        boolean isHeartVictoryRoom = currentRoom instanceof VictoryRoom
                && ((VictoryRoom) currentRoom).eType == VictoryRoom.EventType.HEART;
        float eventWaitTimer = 0.0f;
        if (isEventRoom || isNeowRoom || isHeartVictoryRoom) {
            eventWaitTimer = currentRoom.event.waitTimer;
        }

        // Fix: Check if monsters exist before calling methods on them.
        // In non-combat rooms (shop, rest, treasure, event), getMonsters() returns null.
        boolean monstersBasicallyDead = false;
        if (AbstractDungeon.getMonsters() != null) {
            monstersBasicallyDead = AbstractDungeon.getMonsters().areMonstersBasicallyDead();
        }

        return new GameStateSnapshot(
            AbstractDungeon.screen,
            AbstractDungeon.isScreenUp,
            currentRoom.phase,
            AbstractDungeon.isFadingOut,
            AbstractDungeon.isFadingIn,
            AbstractDungeon.actionManager.phase,
            AbstractDungeon.actionManager.actions.isEmpty(),
            AbstractDungeon.actionManager.preTurnActions.isEmpty(),
            AbstractDungeon.actionManager.cardQueue.isEmpty(),
            monstersBasicallyDead,
            AbstractDungeon.player.endTurnQueued,
            AbstractDungeon.player.gold,
            AbstractDungeon.gridSelectScreen.confirmScreenUp,
            eventWaitTimer,
            isEventRoom,
            isNeowRoom,
            isHeartVictoryRoom
        );
    }

    /**
     * Detects whether the state of the game menu has changed.
     *
     * @return Whether the main menu has just been entered.
     */
    public static boolean checkForMenuStateChange() {
        // Game end detection is now handled by AbstractDungeonUpdatePatch and GameLifecycleController
        // This method only detects menu state changes for state publishing

        boolean stateChange = false;
        if (CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null) {
            stateChange = true;
        }
        if (stateChange) {
            waitingForCommand = true;
        }
        return stateChange;
    }

    /**
     * Detects a state change in AbstractDungeon, and updates all of the local variables used to detect
     * changes in the dungeon state. Sets waitingForCommand = true if a state change was registered since
     * the last command was sent.
     *
     * @return Whether a dungeon state change was detected
     */
    public static boolean checkForDungeonStateChange() {
        boolean stateChange = false;
        if (CommandExecutor.isInDungeon()) {
            // During transitions, the current room may be null
            if (AbstractDungeon.getCurrRoom() == null) {
                return false;
            }

            GameStateSnapshot snapshot = createSnapshot();
            stateChange = detector.detectStateChange(snapshot);

            if (stateChange) {
                waitingForCommand = true;
                detector.updatePreviousState(snapshot);

                // Log the current game state
                String currentState = GameStateConverter.getCommunicationState();
                ActionLogger.completeActionLog(currentState);

                // Trigger agent decision if enabled
                if (agentManager != null && agentManager.isEnabled()) {
                    agentManager.makeDecision();
                }
            }
        } else {
            detector.signalTurnEnd();
        }
        return stateChange;
    }

    public static boolean isWaitingForCommand() {
        return waitingForCommand;
    }
}