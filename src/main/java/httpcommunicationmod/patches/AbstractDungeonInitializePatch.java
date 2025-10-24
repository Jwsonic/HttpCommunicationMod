package httpcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import httpcommunicationmod.GamePhase;
import httpcommunicationmod.GameStateTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Patches AbstractDungeon.update() to detect IN_DUNGEON phase when the dungeon is fully initialized and playable.
 *
 * This patch distinguishes between the GAMEPLAY_MODE loading phase (where dungeon generation and initialization
 * are in progress) and the IN_DUNGEON phase (where the map is fully initialized, the player exists, and actual
 * dungeon exploration can begin).
 *
 * The patch runs every frame during the dungeon phase (approximately 60 times per second), checking both the
 * current map node and player existence to confirm full dungeon initialization. This critical distinction enables
 * the HTTP communication module to accurately track when the dungeon becomes playable and ready for agent interaction.
 *
 * The GameStateTracker's idempotent design ensures that duplicate phase notifications are safely ignored, even
 * though this method executes every frame. Phase change notifications only occur when the initialization conditions
 * first become true, preventing redundant state updates. Once IN_DUNGEON is set, subsequent frames will not trigger
 * additional notifications due to the idempotent behavior of notifyPhaseChange().
 *
 * The patch uses ModTheSpire's postfix approach to run after the dungeon's standard update logic, allowing proper
 * detection of initialization state that occurs during the update cycle.
 */
@SpirePatch(
    clz = AbstractDungeon.class,
    method = "update"
)
public class AbstractDungeonInitializePatch {
    private static final Logger logger = LogManager.getLogger(AbstractDungeonInitializePatch.class);

    /**
     * Postfix patch executed after AbstractDungeon.update() completes.
     *
     * Verifies that both the current map node and the player have been initialized. Both conditions must be true
     * to confirm that the dungeon is fully initialized and playable:
     * - currMapNode != null: The map has been generated and the dungeon has a valid starting node
     * - player != null: The player has been instantiated and is ready for gameplay
     * - Screen must be playable (not a terminal screen like DEATH, VICTORY, UNLOCK, or NEOW_UNLOCK)
     *
     * When both conditions are met, notifies the GameStateTracker of the IN_DUNGEON phase transition. Since
     * AbstractDungeon uses static fields for its map and player state, no instance parameter is needed.
     *
     * This check is safe to perform every frame due to GameStateTracker's idempotent design. The tracker will
     * only notify listeners once when the phase transitions from GAMEPLAY_MODE to IN_DUNGEON, ignoring subsequent
     * calls with the same phase.
     */
    @SpirePostfixPatch
    public static void Postfix() {
        // Check initialization conditions
        boolean mapNodeExists = AbstractDungeon.currMapNode != null;
        boolean playerExists = AbstractDungeon.player != null;
        boolean screenIsPlayable = isPlayableDungeonScreen();

        // All conditions must be true to confirm full dungeon initialization
        if (mapNodeExists && playerExists && screenIsPlayable) {
            logger.trace("Dungeon fully initialized (currMapNode, player, and playable screen all valid) - notifying IN_DUNGEON phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.IN_DUNGEON);
        } else {
            logger.trace("Dungeon initialization check - currMapNode: {}, player: {}, screen: {}",
                mapNodeExists ? "exists" : "null",
                playerExists ? "exists" : "null",
                screenIsPlayable ? "playable" : AbstractDungeon.screen.name());
        }
    }

    /**
     * Validates that the current screen state is a playable dungeon screen.
     *
     * Excludes terminal screens (game-end states) that should not be considered as valid IN_DUNGEON states:
     * - DEATH: Game over due to player death
     * - VICTORY: Game over due to winning
     * - UNLOCK: Post-game unlocks screen
     * - NEOW_UNLOCK: Neow event unlocks screen
     *
     * @return true if the current screen is not a terminal screen; false if in a game-end state
     */
    private static boolean isPlayableDungeonScreen() {
        AbstractDungeon.CurrentScreen currentScreen = AbstractDungeon.screen;
        return currentScreen != AbstractDungeon.CurrentScreen.DEATH
            && currentScreen != AbstractDungeon.CurrentScreen.VICTORY
            && currentScreen != AbstractDungeon.CurrentScreen.UNLOCK
            && currentScreen != AbstractDungeon.CurrentScreen.NEOW_UNLOCK;
    }
}
