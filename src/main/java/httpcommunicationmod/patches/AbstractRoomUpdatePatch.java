package httpcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import httpcommunicationmod.GamePhase;
import httpcommunicationmod.GameStateTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Patches AbstractRoom.update() to detect IN_COMBAT and ROOM_COMPLETE phases for agent decision-making.
 *
 * <p>This patch intercepts the AbstractRoom's update lifecycle to track granular phase transitions.
 * The agent requires real-time awareness of when combat begins and when a room is completed to:
 * <ul>
 *   <li>Make tactical decisions during combat (action selection, turn planning)</li>
 *   <li>Prepare for reward phase selection when a room completes</li>
 *   <li>React appropriately to dynamic game state changes</li>
 * </ul>
 *
 * <p>The patch is idempotent and safe to execute every frame. The GameStateTracker handles
 * deduplication of redundant phase notifications, ensuring that the agent is not overwhelmed
 * with repeated state changes. Phase transitions are delivered to listeners only when the
 * phase actually changes from the previous state.
 *
 * @see GameStateTracker#notifyPhaseChange(GamePhase)
 * @see GamePhase
 */
@SpirePatch(
    clz = AbstractRoom.class,
    method = "update"
)
public class AbstractRoomUpdatePatch {
    private static final Logger logger = LogManager.getLogger(AbstractRoomUpdatePatch.class);

    /**
     * Postfix patch executed after AbstractRoom.update() completes.
     *
     * <p>Examines the room's current phase and notifies the GameStateTracker of any
     * relevant phase transitions. The __instance parameter provides direct access to
     * the patched AbstractRoom instance, allowing inspection of its RoomPhase enum value.
     *
     * <p>Phase Detection:
     * <ul>
     *   <li>COMBAT: Indicates active combat encounter - agent should be making decisions</li>
     *   <li>COMPLETE: Indicates room encounter is finished - agent should prepare for rewards</li>
     * </ul>
     *
     * @param __instance the patched AbstractRoom instance whose update() method just completed;
     *                   provides access to the room's current phase via the phase field
     */
    @SpirePostfixPatch
    public static void Postfix(AbstractRoom __instance) {
        // Log current room phase for diagnostics
        AbstractRoom.RoomPhase currentPhase = __instance.phase;

        // Check room phase for combat or completion
        if (currentPhase == AbstractRoom.RoomPhase.COMBAT) {
            logger.trace("COMBAT room phase detected - notifying IN_COMBAT phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.IN_COMBAT);
        } else if (currentPhase == AbstractRoom.RoomPhase.COMPLETE) {
            logger.trace("COMPLETE room phase detected - notifying ROOM_COMPLETE phase");
            GameStateTracker.getInstance().notifyPhaseChange(GamePhase.ROOM_COMPLETE);
        } else {
            logger.trace("AbstractRoom.update() - current room phase: {}", currentPhase);
        }
    }
}
