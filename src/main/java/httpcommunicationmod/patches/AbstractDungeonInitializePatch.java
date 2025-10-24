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
        // Both conditions must be true to confirm full dungeon initialization
        boolean mapNodeExists = AbstractDungeon.currMapNode != null;
        boolean playerExists = AbstractDungeon.player != null;

        if (mapNodeExists && playerExists) {
            logger.trace("Dungeon fully initialized (currMapNode and player both exist) - notifying IN_DUNGEON phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.IN_DUNGEON);
        } else {
            logger.trace("Dungeon initialization check - currMapNode: {}, player: {}",
                mapNodeExists ? "exists" : "null", playerExists ? "exists" : "null");
        }
    }
}
