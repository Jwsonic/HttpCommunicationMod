package httpcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import httpcommunicationmod.GamePhase;
import httpcommunicationmod.GameStateTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Patches AbstractDungeon.update() to detect DEATH_SCREEN and VICTORY_SCREEN phase transitions.
 *
 * This patch runs every frame during the dungeon phase (approximately 60 times per second),
 * checking the AbstractDungeon.screen enum value to detect when the player has died or achieved
 * victory. This is critical for endless runs as it triggers the appropriate game-end logging
 * and event handling within the HTTP communication module.
 *
 * The GameStateTracker's idempotent design ensures that duplicate phase notifications are safely
 * ignored, even though this method executes every frame. Phase change notifications only occur
 * when the screen state actually transitions to DEATH or VICTORY, preventing redundant state
 * updates despite the persistent nature of these screen states across multiple frames.
 *
 * The patch uses ModTheSpire's postfix approach to run after the dungeon's standard update logic,
 * allowing proper detection of game-end conditions that occur during the update cycle.
 */
@SpirePatch(
    clz = AbstractDungeon.class,
    method = "update"
)
public class AbstractDungeonUpdatePatch {
    private static final Logger logger = LogManager.getLogger(AbstractDungeonUpdatePatch.class);

    /**
     * Postfix patch executed after AbstractDungeon.update() completes.
     *
     * Examines the current dungeon screen state and notifies the GameStateTracker of game-end
     * phases (DEATH_SCREEN or VICTORY_SCREEN). Since AbstractDungeon uses static fields for its
     * screen state, no instance parameter is needed. The tracking occurs after the standard update,
     * ensuring that any screen transitions triggered by the update are properly detected and
     * communicated to listeners.
     */
    @SpirePostfixPatch
    public static void Postfix() {
        // Log current screen state for diagnostics
        AbstractDungeon.CurrentScreen currentScreen = AbstractDungeon.screen;

        // Check for death screen condition
        if (currentScreen == AbstractDungeon.CurrentScreen.DEATH) {
            logger.debug("DEATH screen detected - notifying DEATH_SCREEN phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.DEATH_SCREEN);
        }
        // Check for victory screen condition
        else if (currentScreen == AbstractDungeon.CurrentScreen.VICTORY) {
            logger.debug("VICTORY screen detected - notifying VICTORY_SCREEN phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.VICTORY_SCREEN);
        }
        else {
            // Log all other screen states to diagnose missing transitions
            logger.trace("AbstractDungeon.update() - current screen: {}", currentScreen);
        }
    }
}
