package httpcommunicationmod;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TurnStateDetector.
 * These tests verify that the turn detection logic correctly prevents the agent from
 * acting during enemy turns while allowing it to act during player turns.
 */
public class TurnStateDetectorTest {
    private TurnStateDetector detector;

    @Before
    public void setUp() {
        detector = new TurnStateDetector();
    }

    /**
     * Helper method to create a basic combat snapshot.
     */
    private GameStateSnapshot createCombatSnapshot(
            boolean myTurn,
            boolean actionsComplete,
            boolean screenUp,
            boolean externalChange) {

        if (externalChange) {
            detector.registerStateChange();
        }
        if (myTurn) {
            detector.signalTurnStart();
        } else {
            detector.signalTurnEnd();
        }

        return new GameStateSnapshot(
            AbstractDungeon.CurrentScreen.NONE,    // screen
            screenUp,                               // isScreenUp
            AbstractRoom.RoomPhase.COMBAT,         // phase
            false,                                  // isFadingOut
            false,                                  // isFadingIn
            GameActionManager.Phase.WAITING_ON_USER, // actionManagerPhase
            actionsComplete,                        // actionsEmpty
            actionsComplete,                        // preTurnActionsEmpty
            actionsComplete,                        // cardQueueEmpty
            false,                                  // monstersBasicallyDead
            false,                                  // endTurnQueued
            50,                                     // gold
            false,                                  // gridSelectConfirmUp
            0.0f,                                   // eventWaitTimer
            false,                                  // isEventRoom
            false,                                  // isNeowRoom
            false                                   // isHeartVictoryRoom
        );
    }

    @Test
    public void shouldBlockStateChangeDuringEnemyTurnWithExternalChange() {
        // Given: We're in combat, it's the enemy's turn, and an external change was registered
        GameStateSnapshot snapshot = createCombatSnapshot(
            false,  // myTurn = false (enemy turn)
            true,   // actionsComplete = true
            false,  // screenUp = false
            true    // externalChange = true
        );

        // When: We check for state change
        boolean stateChanged = detector.detectStateChange(snapshot);

        // Then: Should NOT trigger agent during enemy turn
        assertThat(stateChanged)
            .as("Should not detect state change during enemy turn even with external change")
            .isFalse();
    }

    @Test
    public void shouldBlockStateChangeDuringEnemyTurnWhenActionsComplete() {
        // Given: Initial state with no changes
        GameStateSnapshot initialSnapshot = createCombatSnapshot(true, false, false, false);
        detector.detectStateChange(initialSnapshot);
        detector.updatePreviousState(initialSnapshot);

        // When: Enemy turn with actions completing
        GameStateSnapshot snapshot = createCombatSnapshot(
            false,  // myTurn = false (enemy turn)
            true,   // actionsComplete = true
            false,  // screenUp = false
            false   // externalChange = false
        );

        boolean stateChanged = detector.detectStateChange(snapshot);

        // Then: Should NOT trigger (this is the bug we're fixing)
        assertThat(stateChanged)
            .as("Should not detect state change when actions complete during enemy turn")
            .isFalse();
    }

    @Test
    public void shouldAllowStateChangeDuringPlayerTurnWithActionsComplete() {
        // Given: Initial player turn state
        GameStateSnapshot initialSnapshot = createCombatSnapshot(true, false, false, false);
        detector.detectStateChange(initialSnapshot);
        detector.updatePreviousState(initialSnapshot);

        // When: Player plays a card (registers state change), then actions complete
        GameStateSnapshot snapshot = createCombatSnapshot(
            true,   // myTurn = true (player turn)
            true,   // actionsComplete = true
            false,  // screenUp = false
            true    // externalChange = true (card was played)
        );

        boolean stateChanged = detector.detectStateChange(snapshot);

        // Then: Should trigger agent
        assertThat(stateChanged)
            .as("Should detect state change when actions complete during player turn")
            .isTrue();
    }

    @Test
    public void shouldAllowStateChangeWhenScreenUpEvenDuringEnemyTurn() {
        // Given: Initial state
        GameStateSnapshot initialSnapshot = createCombatSnapshot(false, false, false, false);
        detector.detectStateChange(initialSnapshot);
        detector.updatePreviousState(initialSnapshot);

        // When: Enemy turn but screen comes up (e.g., card selection prompt)
        GameStateSnapshot snapshot = createCombatSnapshot(
            false,  // myTurn = false (enemy turn)
            false,  // actionsComplete = false
            true,   // screenUp = true (important!)
            false   // externalChange = false
        );

        boolean stateChanged = detector.detectStateChange(snapshot);

        // Then: Should trigger because screen is up (requires player input)
        assertThat(stateChanged)
            .as("Should detect state change when screen comes up even during enemy turn")
            .isTrue();
    }

    @Test
    public void shouldBlockStateChangeWhenEndTurnQueued() {
        // Given: Player turn with end turn queued
        GameStateSnapshot snapshot = new GameStateSnapshot(
            AbstractDungeon.CurrentScreen.NONE,
            false,
            AbstractRoom.RoomPhase.COMBAT,
            false,
            false,
            GameActionManager.Phase.WAITING_ON_USER,
            false,
            false,
            false,
            false,
            true,   // endTurnQueued = true
            50,
            false,
            0.0f,
            false,
            false,
            false
        );
        detector.signalTurnStart();
        detector.registerStateChange();

        // When: We check for state change
        boolean stateChanged = detector.detectStateChange(snapshot);

        // Then: Should NOT trigger (avoid acting after end turn is queued)
        assertThat(stateChanged)
            .as("Should not detect state change when end turn is queued")
            .isFalse();
    }

    @Test
    public void shouldAllowStateChangeOnDeath() {
        // Given: Initial state
        GameStateSnapshot initialSnapshot = createCombatSnapshot(true, false, false, false);
        detector.detectStateChange(initialSnapshot);
        detector.updatePreviousState(initialSnapshot);

        // When: Death screen appears
        GameStateSnapshot deathSnapshot = new GameStateSnapshot(
            AbstractDungeon.CurrentScreen.DEATH,  // Death screen
            true,
            AbstractRoom.RoomPhase.COMBAT,
            false,
            false,
            GameActionManager.Phase.WAITING_ON_USER,
            true,
            true,
            true,
            false,
            false,
            0,   // gold = 0 (died)
            false,
            0.0f,
            false,
            false,
            false
        );
        detector.signalTurnStart();

        boolean stateChanged = detector.detectStateChange(deathSnapshot);

        // Then: Should trigger (death screen requires acknowledgment)
        assertThat(stateChanged)
            .as("Should detect state change when death screen appears")
            .isTrue();
    }

    @Test
    public void shouldBlockDuringFadeOutAndFadeIn() {
        // Given: Combat state with fade out
        GameStateSnapshot fadeOutSnapshot = new GameStateSnapshot(
            AbstractDungeon.CurrentScreen.NONE,
            false,
            AbstractRoom.RoomPhase.COMBAT,
            true,   // isFadingOut = true
            false,
            GameActionManager.Phase.WAITING_ON_USER,
            true,
            true,
            true,
            false,
            false,
            50,
            false,
            0.0f,
            false,
            false,
            false
        );
        detector.signalTurnStart();

        boolean stateChanged = detector.detectStateChange(fadeOutSnapshot);

        // Then: Should NOT trigger during fade
        assertThat(stateChanged)
            .as("Should not detect state change during fade out")
            .isFalse();

        // Given: Fade in
        GameStateSnapshot fadeInSnapshot = new GameStateSnapshot(
            AbstractDungeon.CurrentScreen.NONE,
            false,
            AbstractRoom.RoomPhase.COMBAT,
            false,
            true,   // isFadingIn = true
            GameActionManager.Phase.WAITING_ON_USER,
            true,
            true,
            true,
            false,
            false,
            50,
            false,
            0.0f,
            false,
            false,
            false
        );

        stateChanged = detector.detectStateChange(fadeInSnapshot);

        // Then: Should NOT trigger during fade in
        assertThat(stateChanged)
            .as("Should not detect state change during fade in")
            .isFalse();
    }
}
