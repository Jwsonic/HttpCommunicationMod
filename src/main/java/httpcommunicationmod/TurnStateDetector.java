package httpcommunicationmod;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

/**
 * Detects when the game state has changed and the player can act.
 * Extracted from GameStateListener for testability.
 *
 * <p><b>State Machine Overview:</b></p>
 * <p>This class implements a state machine that determines when the agent should be allowed to act.
 * The core principle is: <b>the agent should ONLY act when it's the player's turn</b>.</p>
 *
 * <p><b>Key Rules:</b></p>
 * <ul>
 *   <li><b>Player Turn:</b> Agent can act when actions are complete or screens require input</li>
 *   <li><b>Enemy Turn:</b> Agent CANNOT act, except when a screen pops up requiring immediate input
 *       (e.g., card selection prompts from enemy effects)</li>
 *   <li><b>Turn Tracking:</b> Turn state is managed via signalTurnStart() and signalTurnEnd() calls
 *       from game patches (EnableEndTurnPatch and EndOfTurnAction)</li>
 * </ul>
 *
 * <p><b>State Change Detection Paths:</b></p>
 * <ol>
 *   <li><b>Death Screen:</b> Always triggers (highest priority)</li>
 *   <li><b>Screen/Phase Changes:</b> Triggers when screen state or room phase changes:
 *       <ul>
 *         <li>In combat: Triggers if screen pops up OR (player turn AND actions complete)</li>
 *         <li>Out of combat: Waits one update cycle for transitions to settle</li>
 *       </ul>
 *   </li>
 *   <li><b>External Changes:</b> Triggers when registerStateChange() was called (e.g., from card effects):
 *       <ul>
 *         <li>Requires: actions complete AND (not in combat OR player's turn)</li>
 *         <li><b>Critical Fix:</b> The (!inCombat || myTurn) check prevents agent from acting during enemy turns</li>
 *       </ul>
 *   </li>
 *   <li><b>Grid Select:</b> Triggers when confirm screen appears/disappears</li>
 *   <li><b>Timeout:</b> Triggers after specified number of frames</li>
 * </ol>
 *
 * <p><b>Blocking Conditions:</b></p>
 * <ul>
 *   <li>Fade in/out transitions</li>
 *   <li>Door unlock or NO_INTERACT screens</li>
 *   <li>Event wait timers (> 0)</li>
 *   <li>End turn queued (prevents acting after player ends turn but before it processes)</li>
 *   <li>Enemy turn without screen up</li>
 *   <li>Manual block via blockStateUpdate()</li>
 * </ul>
 */
public class TurnStateDetector {
    // Previous state variables
    private AbstractDungeon.CurrentScreen previousScreen = null;
    private boolean previousScreenUp = false;
    private AbstractRoom.RoomPhase previousPhase = null;
    private boolean previousGridSelectConfirmUp = false;
    private int previousGold = 99;

    // Control flags
    private boolean externalChange = false;
    private boolean myTurn = false;
    private boolean blocked = false;
    private boolean waitOneUpdate = false;
    private int timeout = 0;

    /**
     * Used to indicate that something (in game logic, not external command) has been done that will change the game state,
     * and detectStateChange() should indicate a state change when the state next becomes stable.
     */
    public void registerStateChange() {
        externalChange = true;
    }

    /**
     * Used to tell detectStateChange() to indicate a state change after a specified number of frames.
     * @param newTimeout The number of frames to wait
     */
    public void setTimeout(int newTimeout) {
        timeout = newTimeout;
    }

    /**
     * Used to indicate that an external command has been executed
     */
    public void registerCommandExecution() {
        // Currently unused, but kept for API compatibility
    }

    /**
     * Prevents detectStateChange() from indicating a state change until resumeStateUpdate() is called.
     */
    public void blockStateUpdate() {
        blocked = true;
    }

    /**
     * Removes the block instantiated by blockStateUpdate()
     */
    public void resumeStateUpdate() {
        blocked = false;
    }

    /**
     * Used by a patch in the game to signal the start of your turn. We do not care about state changes
     * when it is not our turn in combat, as we cannot take action until then.
     */
    public void signalTurnStart() {
        myTurn = true;
    }

    /**
     * Used by patches in the game to signal the end of your turn (or the end of combat).
     */
    public void signalTurnEnd() {
        myTurn = false;
    }

    /**
     * Resets all state detection variables for the start of a new run.
     */
    public void resetStateVariables() {
        previousScreen = null;
        previousScreenUp = false;
        previousPhase = null;
        previousGridSelectConfirmUp = false;
        previousGold = 99;
        externalChange = false;
        myTurn = false;
        blocked = false;
        waitOneUpdate = false;
        timeout = 0;
    }

    /**
     * Detects whether the game state is stable and we are ready to receive a command from the user.
     *
     * @param snapshot The current game state
     * @return whether the state has changed and is stable
     */
    public boolean detectStateChange(GameStateSnapshot snapshot) {
        if (blocked) {
            return false;
        }

        AbstractDungeon.CurrentScreen newScreen = snapshot.screen;
        boolean newScreenUp = snapshot.isScreenUp;
        AbstractRoom.RoomPhase newPhase = snapshot.phase;
        boolean inCombat = isInCombat(snapshot);

        // Lots of stuff can happen while the dungeon is fading out, but nothing that requires input from the user.
        if (snapshot.isFadingOut || snapshot.isFadingIn) {
            return false;
        }

        // This check happens before the rest since dying can happen in combat and messes with the other cases.
        if (newScreen == AbstractDungeon.CurrentScreen.DEATH && newScreen != previousScreen) {
            return true;
        }

        // These screens have no interaction available.
        if (newScreen == AbstractDungeon.CurrentScreen.DOOR_UNLOCK || newScreen == AbstractDungeon.CurrentScreen.NO_INTERACT) {
            return false;
        }

        // We are not ready to receive commands when it is not our turn, except for some pesky screens
        if (inCombat && ((!myTurn && !isWaitingOnUser(snapshot)) || snapshot.monstersBasicallyDead)) {
            if (!newScreenUp) {
                return false;
            }
        }

        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        if ((snapshot.isEventRoom || snapshot.isNeowRoom || snapshot.isHeartVictoryRoom)
                && snapshot.eventWaitTimer != 0.0F
                && newScreen != AbstractDungeon.CurrentScreen.MAP) {
            return false;
        }

        // The state has always changed in some way when one of these variables is different.
        // However, the state may not be finished changing, so we need to do some additional checks.
        if (newScreen != previousScreen || newScreenUp != previousScreenUp || newPhase != previousPhase) {
            if (inCombat) {
                // In combat, newScreenUp being true indicates an action that requires our immediate attention.
                if (newScreenUp) {
                    return true;
                }
                // In combat, if no screen is up, we should wait for all actions to complete before indicating a state change.
                else if (myTurn && isWaitingOnUser(snapshot) && areActionsComplete(snapshot)) {
                    return true;
                }

            // Out of combat, we want to wait one update cycle, as some screen transitions trigger further updates.
            } else {
                waitOneUpdate = true;
                previousScreenUp = newScreenUp;
                previousScreen = newScreen;
                previousPhase = newPhase;
                return false;
            }
        } else if (waitOneUpdate) {
            waitOneUpdate = false;
            return true;
        }

        // We are assuming that commands are only being submitted through our interface. Some actions that require
        // our attention, like retaining a card, occur after the end turn is queued, but the previous cases
        // cover those actions. We would like to avoid registering other state changes after the end turn
        // command but before the game actually ends your turn.
        if (inCombat && snapshot.endTurnQueued) {
            return false;
        }

        // If some other code registered a state change through registerStateChange(), or if we notice a state
        // change through the gold amount changing, we still need to wait until all actions are finished
        // resolving to claim a stable state and ask for a new command.
        if ((externalChange || previousGold != snapshot.gold)
                && (!inCombat || myTurn)
                && isWaitingOnUser(snapshot)
                && areActionsComplete(snapshot)) {
            return true;
        }

        // In a grid select screen, if a confirm screen comes up or goes away, it doesn't change any other state.
        if (newScreen == AbstractDungeon.CurrentScreen.GRID) {
            if (previousScreen == AbstractDungeon.CurrentScreen.GRID && snapshot.gridSelectConfirmUp != previousGridSelectConfirmUp) {
                return true;
            }
        }

        // Sometimes, we need to register an external change in combat while an action is resolving which brings
        // the screen up. Because the screen did not change, this is not covered by other cases.
        if (externalChange && inCombat && newScreenUp) {
            return true;
        }

        if (timeout > 0) {
            timeout -= 1;
            if(timeout == 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Updates the previous state variables after a state change is detected.
     * Must be called after detectStateChange() returns true.
     */
    public void updatePreviousState(GameStateSnapshot snapshot) {
        externalChange = false;
        previousPhase = snapshot.phase;
        previousScreen = snapshot.screen;
        previousScreenUp = snapshot.isScreenUp;
        previousGold = snapshot.gold;
        previousGridSelectConfirmUp = snapshot.gridSelectConfirmUp;
        timeout = 0;
    }

    // Helper methods

    private boolean isInCombat(GameStateSnapshot snapshot) {
        return snapshot.phase == AbstractRoom.RoomPhase.COMBAT;
    }

    private boolean isWaitingOnUser(GameStateSnapshot snapshot) {
        return snapshot.actionManagerPhase.equals(GameActionManager.Phase.WAITING_ON_USER);
    }

    private boolean areActionsComplete(GameStateSnapshot snapshot) {
        return snapshot.preTurnActionsEmpty && snapshot.actionsEmpty && snapshot.cardQueueEmpty;
    }

    public boolean isMyTurn() {
        return myTurn;
    }
}
