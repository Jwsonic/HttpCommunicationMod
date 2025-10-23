package httpcommunicationmod;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

/**
 * Immutable snapshot of the current game state relevant for turn detection.
 * This class encapsulates all the game state needed to determine if the player can act.
 */
public class GameStateSnapshot {
    public final AbstractDungeon.CurrentScreen screen;
    public final boolean isScreenUp;
    public final AbstractRoom.RoomPhase phase;
    public final boolean isFadingOut;
    public final boolean isFadingIn;
    public final GameActionManager.Phase actionManagerPhase;
    public final boolean actionsEmpty;
    public final boolean preTurnActionsEmpty;
    public final boolean cardQueueEmpty;
    public final boolean monstersBasicallyDead;
    public final boolean endTurnQueued;
    public final int gold;
    public final boolean gridSelectConfirmUp;
    public final float eventWaitTimer;
    public final boolean isEventRoom;
    public final boolean isNeowRoom;
    public final boolean isHeartVictoryRoom;

    public GameStateSnapshot(
            AbstractDungeon.CurrentScreen screen,
            boolean isScreenUp,
            AbstractRoom.RoomPhase phase,
            boolean isFadingOut,
            boolean isFadingIn,
            GameActionManager.Phase actionManagerPhase,
            boolean actionsEmpty,
            boolean preTurnActionsEmpty,
            boolean cardQueueEmpty,
            boolean monstersBasicallyDead,
            boolean endTurnQueued,
            int gold,
            boolean gridSelectConfirmUp,
            float eventWaitTimer,
            boolean isEventRoom,
            boolean isNeowRoom,
            boolean isHeartVictoryRoom) {
        this.screen = screen;
        this.isScreenUp = isScreenUp;
        this.phase = phase;
        this.isFadingOut = isFadingOut;
        this.isFadingIn = isFadingIn;
        this.actionManagerPhase = actionManagerPhase;
        this.actionsEmpty = actionsEmpty;
        this.preTurnActionsEmpty = preTurnActionsEmpty;
        this.cardQueueEmpty = cardQueueEmpty;
        this.monstersBasicallyDead = monstersBasicallyDead;
        this.endTurnQueued = endTurnQueued;
        this.gold = gold;
        this.gridSelectConfirmUp = gridSelectConfirmUp;
        this.eventWaitTimer = eventWaitTimer;
        this.isEventRoom = isEventRoom;
        this.isNeowRoom = isNeowRoom;
        this.isHeartVictoryRoom = isHeartVictoryRoom;
    }
}
