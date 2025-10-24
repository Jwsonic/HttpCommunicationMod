package httpcommunicationmod;

/**
 * Enumeration representing the various phases of the game lifecycle.
 *
 * This enum defines the distinct states that the game progresses through, from initial
 * startup through menus, dungeon exploration, combat, and completion. Used by GameStateTracker
 * to manage state transitions and enable agents to make decisions appropriate to the current
 * game phase.
 *
 * The phases provide a granular view of the game state suitable for both lifecycle management
 * and intelligent agent decision-making.
 */
public enum GamePhase {
    /**
     * Initial or undefined game state. Used when the game phase cannot be determined
     * or hasn't been initialized yet.
     */
    UNKNOWN,

    /**
     * Player is at the character selection screen or main menu. Game is loaded but
     * no active gameplay has begun.
     */
    MAIN_MENU,

    /**
     * Game mode has transitioned to GAMEPLAY state and is in the loading phase.
     * Dungeon generation and initialization is in progress.
     */
    GAMEPLAY_MODE,

    /**
     * Player is actively in the dungeon with the map initialized and available.
     * This is the primary exploration state where the player can move and interact.
     */
    IN_DUNGEON,

    /**
     * Active combat is currently occurring in a dungeon room. The player is engaged
     * in a battle with enemies.
     */
    IN_COMBAT,

    /**
     * A dungeon room has been completed. The rewards phase is active where the player
     * receives loot, experience, or other rewards for completing the room.
     */
    ROOM_COMPLETE,

    /**
     * Player character has died. The death screen is displayed with options to retry
     * or return to the main menu.
     */
    DEATH_SCREEN,

    /**
     * Victory condition has been met. The victory screen is displayed, showing final
     * results and achievements.
     */
    VICTORY_SCREEN
}
