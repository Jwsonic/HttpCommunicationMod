package httpcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import httpcommunicationmod.GamePhase;
import httpcommunicationmod.GameStateTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Patches CardCrawlGame.update() to detect transitions between MAIN_MENU and GAMEPLAY_MODE phases.
 *
 * This patch runs every frame (approximately 60 times per second), checking the current game mode
 * and notifying the GameStateTracker of phase changes. The GameStateTracker's idempotent design
 * ensures that duplicate phase notifications are safely ignored, preventing redundant state updates
 * despite this method executing every frame.
 *
 * The patch uses ModTheSpire's postfix approach to run after the game's standard update logic,
 * allowing proper detection of mode transitions that occur during the update cycle.
 */
@SpirePatch(
    clz = CardCrawlGame.class,
    method = "update"
)
public class CardCrawlGameUpdatePatch {
    private static final Logger logger = LogManager.getLogger(CardCrawlGameUpdatePatch.class);

    /**
     * Postfix patch executed after CardCrawlGame.update() completes.
     *
     * Examines the current game mode and notifies the GameStateTracker of the corresponding phase.
     * The tracking occurs after the standard update, ensuring that any mode transitions triggered
     * by the update are properly detected and communicated to listeners.
     *
     * IMPORTANT: For GAMEPLAY mode, this method checks the AbstractDungeon.screen state to avoid
     * notifying GAMEPLAY_MODE when death or victory screens are active. Terminal screen states are
     * handled by AbstractDungeonUpdatePatch instead, preventing phase oscillation between
     * DEATH_SCREEN/VICTORY_SCREEN and GAMEPLAY_MODE.
     *
     * @param __instance the CardCrawlGame instance being updated
     */
    @SpirePostfixPatch
    public static void Postfix(CardCrawlGame __instance) {
        // Log current game mode for diagnostics
        CardCrawlGame.GameMode currentMode = __instance.mode;

        // Check current game mode and notify appropriate phase
        if (currentMode == CardCrawlGame.GameMode.CHAR_SELECT) {
            logger.debug("CHAR_SELECT mode detected - notifying MAIN_MENU phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.MAIN_MENU);
        } else if (currentMode == CardCrawlGame.GameMode.GAMEPLAY) {
            // Check if we're in a terminal screen state (death or victory)
            try {
                AbstractDungeon.CurrentScreen dungeonScreen = AbstractDungeon.screen;
                if (dungeonScreen == AbstractDungeon.CurrentScreen.DEATH) {
                    logger.debug("DEATH screen active in GAMEPLAY mode - skipping GAMEPLAY_MODE notification");
                    return;
                } else if (dungeonScreen == AbstractDungeon.CurrentScreen.VICTORY) {
                    logger.debug("VICTORY screen active in GAMEPLAY mode - skipping GAMEPLAY_MODE notification");
                    return;
                }
            } catch (Exception e) {
                // Screen may be null during transitions, continue with normal notification
                logger.trace("Exception checking dungeon screen state during GAMEPLAY mode check", e);
            }

            logger.trace("GAMEPLAY mode detected - notifying GAMEPLAY_MODE phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.GAMEPLAY_MODE);
        } else {
            // Log other game modes to diagnose unexpected states
            logger.trace("CardCrawlGame.update() - current mode: {}", currentMode);
        }
    }
}
