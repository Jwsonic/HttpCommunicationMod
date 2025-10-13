package httpcommunicationmod;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.helpers.TrialHelper;
import com.megacrit.cardcrawl.helpers.input.InputAction;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.*;
import httpcommunicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class CommandExecutor {

    private static final Logger logger = LogManager.getLogger(CommandExecutor.class.getName());

    public static boolean executeCommand(String command) throws InvalidCommandException {
        command = command.toLowerCase();
        String [] tokens = command.split("\\s+");
        if(tokens.length == 0) {
            return false;
        }
        if (!isCommandAvailable(tokens[0])) {
            throw new InvalidCommandException("Invalid command: " + tokens[0] + ". Possible commands: " + getAvailableCommands());
        }
        String command_tail = command.substring(tokens[0].length());
        switch(tokens[0]) {
            case "play":
                executePlayCommand(tokens);
                return true;
            case "end":
                executeEndCommand();
                return true;
            case "choose":
                executeChooseCommand(tokens);
                return true;
            case "potion":
                executePotionCommand(tokens);
                return true;
            case "confirm":
            case "proceed":
                executeConfirmCommand();
                return true;
            case "skip":
            case "cancel":
            case "return":
            case "leave":
                executeCancelCommand();
                return true;
            case "start":
                executeStartCommand(tokens);
                return true;
            case "key":
                executeKeyCommand(tokens);
                return true;
            case "click":
                executeClickCommand(tokens);
                return true;
            case "wait":
                executeWaitCommand(tokens);
                return true;

            default:
                logger.info("This should never happen.");
                throw new InvalidCommandException("Command not recognized.");
        }
    }

    public static ArrayList<String> getAvailableCommands() {
        ArrayList<String> availableCommands = new ArrayList<>();

        // Enumerate all play commands (e.g., "play 1", "play 1 0", "play 2 1")
        if (isPlayCommandAvailable()) {
            availableCommands.addAll(getPlayCommands());
        }

        // Enumerate all choose commands (e.g., "choose 0", "choose skip")
        if (isChooseCommandAvailable()) {
            availableCommands.addAll(getChooseCommands());
        }

        // Add end command
        if (isEndCommandAvailable()) {
            availableCommands.add("end");
        }

        // Enumerate all potion commands (e.g., "potion use 0", "potion use 0 1", "potion discard 0")
        if (isPotionCommandAvailable()) {
            availableCommands.addAll(getPotionCommands());
        }

        // Add confirm/proceed command with canonical name
        if (isConfirmCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getConfirmButtonText());
        }

        // Add skip/cancel/return/leave command with canonical name
        if (isCancelCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getCancelButtonText());
        }

        // Note: 'start' and 'reset' have been moved to their own endpoints
        // 'key', 'click', and 'wait' are low-level commands not included in available_commands

        return availableCommands;
    }

    public static boolean isCommandAvailable(String command) {
        if(command.equals("confirm") || command.equalsIgnoreCase("proceed")) {
            return isConfirmCommandAvailable();
        } else if (command.equals("skip") || command.equals("cancel") || command.equals("return") || command.equals("leave")) {
            return isCancelCommandAvailable();
        } else {
            return getAvailableCommands().contains(command);
        }
    }

    public static boolean isInDungeon() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.isPlayerInDungeon() && AbstractDungeon.currMapNode != null;
    }

    private static boolean isPlayCommandAvailable() {
        if(isInDungeon()) {
            if(AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp) {
                // Play command is not available if none of the cards are playable.
                // TODO: this does not check the case where there is no legal target for a target card.
                for (AbstractCard card : AbstractDungeon.player.hand.group) {
                    if (card.canUse(AbstractDungeon.player, null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isEndCommandAvailable() {
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp;
    }

    public static boolean isChooseCommandAvailable() {
        if(isInDungeon()) {
            return !isPlayCommandAvailable() && !ChoiceScreenUtils.getCurrentChoiceList().isEmpty();
        } else {
            return false;
        }
    }

    public static boolean isPotionCommandAvailable() {
        if(isInDungeon()) {
            for(AbstractPotion potion : AbstractDungeon.player.potions) {
                if(!(potion instanceof PotionSlot)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isConfirmCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isConfirmButtonAvailable();
        } else {
            return false;
        }
    }

    public static boolean isCancelCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isCancelButtonAvailable();
        } else {
            return false;
        }
    }

    public static boolean isStartCommandAvailable() {
        return !isInDungeon() && CardCrawlGame.mainMenuScreen != null;
    }

    private static void executeStateCommand() {
        HttpCommunicationMod.mustSendGameState = true;
    }

    private static void executePlayCommand(String[] tokens) throws InvalidCommandException {
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int card_index;
        try {
            card_index = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(card_index == 0) {
            card_index = 10;
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size())) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(card_index));
        }
        int monster_index = -1;
        if(tokens.length == 3) {
            try {
                monster_index = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
        }
        AbstractMonster target_monster = null;
        if (monster_index != -1) {
            if (monster_index < 0 || monster_index >= AbstractDungeon.getCurrRoom().monsters.monsters.size()) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index));
            } else {
                target_monster = AbstractDungeon.getCurrRoom().monsters.monsters.get(monster_index);
            }
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size()) || !(AbstractDungeon.player.hand.group.get(card_index - 1).canUse(AbstractDungeon.player, target_monster))) {
            throw new InvalidCommandException("Selected card cannot be played with the selected target.");
        }
        AbstractCard card = AbstractDungeon.player.hand.group.get(card_index - 1);
        if(card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
            if(target_monster == null) {
                throw new InvalidCommandException("Selected card requires an enemy target.");
            }
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target_monster));
        } else {
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, null));
        }
    }

    private static void executeEndCommand() throws InvalidCommandException {
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
    }

    private static void executeChooseCommand(String[] tokens) throws InvalidCommandException {
        ArrayList<String> validChoices = ChoiceScreenUtils.getCurrentChoiceList();
        if(validChoices.size() == 0) {
            throw new InvalidCommandException("The choice command is not implemented on this screen.");
        }
        int choice_index = getValidChoiceIndex(tokens, validChoices);
        ChoiceScreenUtils.executeChoice(choice_index);
    }

    private static void executePotionCommand(String[] tokens) throws  InvalidCommandException {
        int potion_index;
        boolean use;
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        if(tokens[1].equals("use")) {
            use = true;
        } else if (tokens[1].equals("discard")) {
            use = false;
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        try {
            potion_index = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        if(potion_index < 0 || potion_index >= AbstractDungeon.player.potionSlots) {
            throw new InvalidCommandException("Potion index out of bounds.");
        }
        AbstractPotion selectedPotion = AbstractDungeon.player.potions.get(potion_index);
        if(selectedPotion instanceof PotionSlot) {
            throw new InvalidCommandException("No potion in the selected slot.");
        }
        if(use && !selectedPotion.canUse()) {
            throw new InvalidCommandException("Selected potion cannot be used.");
        }
        if(!use && !selectedPotion.canDiscard()) {
            throw new InvalidCommandException("Selected potion cannot be discarded.");
        }
        int monster_index = -1;
        if (use) {
            if (selectedPotion.targetRequired) {
                if (tokens.length < 4) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " Selected potion requires a target.");
                }
                AbstractMonster target_monster;
                try {
                    monster_index = Integer.parseInt(tokens[3]);
                } catch (NumberFormatException e) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
                }
                if (monster_index < 0 || monster_index >= AbstractDungeon.getCurrRoom().monsters.monsters.size()) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index));
                } else {
                    target_monster = AbstractDungeon.getCurrRoom().monsters.monsters.get(monster_index);
                }
                selectedPotion.use(target_monster);
            } else {
                selectedPotion.use(AbstractDungeon.player);
            }
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onUsePotion();
            }
        }
        AbstractDungeon.topPanel.destroyPotion(selectedPotion.slot);
        GameStateListener.registerStateChange();
    }

    private static void executeConfirmCommand() {
        ChoiceScreenUtils.pressConfirmButton();
    }

    private static void executeCancelCommand() {
        ChoiceScreenUtils.pressCancelButton();
    }

    private static void executeStartCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int ascensionLevel = 0;
        boolean seedSet = false;
        long seed = 0;
        AbstractPlayer.PlayerClass selectedClass = null;
        for(AbstractPlayer.PlayerClass playerClass : AbstractPlayer.PlayerClass.values()) {
            if(playerClass.name().equalsIgnoreCase(tokens[1])) {
                selectedClass = playerClass;
            }
        }
        // Better to allow people to specify the character as "silent" rather than requiring "the_silent"
        if(tokens[1].equalsIgnoreCase("silent")) {
            selectedClass = AbstractPlayer.PlayerClass.THE_SILENT;
        }
        if(selectedClass == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(tokens.length >= 3) {
            try {
                ascensionLevel = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            if(ascensionLevel < 0 || ascensionLevel > 20) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        if(tokens.length >= 4) {
            String seedString = tokens[3].toUpperCase();
            if(!seedString.matches("^[A-Z0-9]+$")) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, seedString);
            }
            seedSet = true;
            seed = SeedHelper.getLong(seedString);
            boolean isTrialSeed = TrialHelper.isTrialSeed(seedString);
            if (isTrialSeed) {
                Settings.specialSeed = seed;
                Settings.isTrial = true;
                seedSet = false;
            }
        }
        if(!seedSet) {
            seed = SeedHelper.generateUnoffensiveSeed(new Random(System.nanoTime()));
        }
        Settings.seed = seed;
        Settings.seedSet = seedSet;
        AbstractDungeon.generateSeeds();
        AbstractDungeon.ascensionLevel = ascensionLevel;
        AbstractDungeon.isAscensionMode = ascensionLevel > 0;
        CardCrawlGame.startOver = true;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CharacterManager manager = new CharacterManager();
        manager.setChosenCharacter(selectedClass);
        CardCrawlGame.chosenCharacter = selectedClass;
        GameStateListener.resetStateVariables();
    }

    private static void executeKeyCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int keycode = getKeycode(tokens[1].toUpperCase());
        if (keycode == -1) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        int timeout = 100;
        if (tokens.length >= 3) {
            try {
                timeout = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        InputActionPatch.doKeypress = true;
        InputActionPatch.key = keycode;
        InputHelper.updateFirst();
        GameStateListener.setTimeout(timeout);
    }

    private static void executeClickCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 4) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        float x = 0;
        float y = 0;
        int timeout = 100;
        try {
            x = Float.parseFloat(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        try {
            y = Float.parseFloat(tokens[3]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
        }
        x = x * Settings.scale;
        y = y * Settings.scale;
        Gdx.input.setCursorPosition((int)x, (int)y);
        InputHelper.updateFirst();
        String token1 = tokens[1].toUpperCase();
        if (token1.equals("LEFT")) {
            InputHelper.justClickedLeft = true;
            InputHelper.isMouseDown = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown", true);
        } else if (token1.equals("RIGHT")) {
            InputHelper.justClickedRight = true;
            InputHelper.isMouseDown_R = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown_R", true);
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if (tokens.length >= 5) {
            try {
                timeout = Integer.parseInt(tokens[4]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[4]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[4]);
            }
        }
        GameStateListener.setTimeout(timeout);
    }

    private static void executeWaitCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int timeout = 0;
        try {
            timeout = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(timeout < 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        GameStateListener.setTimeout(timeout);
    }

    public static void executeStartOver(String[] tokens) {
        //Copying the functionality from VictoryScreen.update(), always skipping credits
        AbstractDungeon.unlocks.clear();
        Settings.isTrial = false;
        Settings.isDailyRun = false;
        Settings.isEndless = false;
        CardCrawlGame.trial = null;
        CardCrawlGame.startOver();

        GameStateListener.setTimeout(200);
    }

    private static int getKeycode(String keyName) {
        InputAction action;
        switch(keyName) {
            case "CONFIRM":
                action = InputActionSet.confirm;
                break;
            case "CANCEL":
                action = InputActionSet.cancel;
                break;
            case "MAP":
                action = InputActionSet.map;
                break;
            case "DECK":
                action = InputActionSet.masterDeck;
                break;
            case "DRAW_PILE":
                action = InputActionSet.drawPile;
                break;
            case "DISCARD_PILE":
                action = InputActionSet.discardPile;
                break;
            case "EXHAUST_PILE":
                action = InputActionSet.exhaustPile;
                break;
            case "END_TURN":
                action = InputActionSet.endTurn;
                break;
            case "UP":
                action = InputActionSet.up;
                break;
            case "DOWN":
                action = InputActionSet.down;
                break;
            case "LEFT":
                action = InputActionSet.left;
                break;
            case "RIGHT":
                action = InputActionSet.right;
                break;
            case "DROP_CARD":
                action = InputActionSet.releaseCard;
                break;
            case "CARD_1":
                action = InputActionSet.selectCard_1;
                break;
            case "CARD_2":
                action = InputActionSet.selectCard_2;
                break;
            case "CARD_3":
                action = InputActionSet.selectCard_3;
                break;
            case "CARD_4":
                action = InputActionSet.selectCard_4;
                break;
            case "CARD_5":
                action = InputActionSet.selectCard_5;
                break;
            case "CARD_6":
                action = InputActionSet.selectCard_6;
                break;
            case "CARD_7":
                action = InputActionSet.selectCard_7;
                break;
            case "CARD_8":
                action = InputActionSet.selectCard_8;
                break;
            case "CARD_9":
                action = InputActionSet.selectCard_9;
                break;
            case "CARD_10":
                action = InputActionSet.selectCard_10;
                break;
            default:
                action = null;
        }
        if (action == null) {
            return -1;
        } else {
            return (int) ReflectionHacks.getPrivate(action, InputAction.class, "keycode");
        }
    }

    private static int getValidChoiceIndex(String[] tokens, ArrayList<String> validChoices) throws InvalidCommandException {
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " A choice is required.");
        }
        String choice = merge_arguments(tokens);
        int choice_index = -1;
        if(validChoices.contains(choice)) {
            choice_index = validChoices.indexOf(choice);
        } else {
            try {
                choice_index = Integer.parseInt(choice);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, choice);
            }
            if(choice_index < 0 || choice_index >= validChoices.size()) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, choice);
            }
        }
        return choice_index;
    }

    private static String merge_arguments(String[] tokens) {
        StringBuilder builder = new StringBuilder();
        for(int i = 1; i < tokens.length; i++) {
            builder.append(tokens[i]);
            if(i != tokens.length - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    /**
     * Enumerates all valid play commands for the current game state.
     * @return A list of exact play command strings (e.g., "play 1", "play 2 0", "play 3 1")
     */
    private static ArrayList<String> getPlayCommands() {
        ArrayList<String> commands = new ArrayList<>();
        if (!isPlayCommandAvailable()) {
            return commands;
        }

        ArrayList<AbstractCard> hand = AbstractDungeon.player.hand.group;
        for (int cardIndex = 0; cardIndex < hand.size(); cardIndex++) {
            AbstractCard card = hand.get(cardIndex);
            int displayIndex = cardIndex + 1;

            // Check if card requires a target
            if (card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
                // Enumerate all valid monster targets
                for (int monsterIndex = 0; monsterIndex < AbstractDungeon.getCurrRoom().monsters.monsters.size(); monsterIndex++) {
                    AbstractMonster monster = AbstractDungeon.getCurrRoom().monsters.monsters.get(monsterIndex);
                    if (card.canUse(AbstractDungeon.player, monster)) {
                        commands.add("play " + displayIndex + " " + monsterIndex);
                    }
                }
            } else {
                // Card doesn't require a target
                if (card.canUse(AbstractDungeon.player, null)) {
                    commands.add("play " + displayIndex);
                }
            }
        }

        return commands;
    }

    /**
     * Enumerates all valid choose commands for the current game state.
     * Returns only numeric index forms to avoid functional duplicates.
     * @return A list of exact choose command strings (e.g., "choose 0", "choose 1", "choose 2")
     */
    private static ArrayList<String> getChooseCommands() {
        ArrayList<String> commands = new ArrayList<>();
        if (!isChooseCommandAvailable()) {
            return commands;
        }

        ArrayList<String> validChoices = ChoiceScreenUtils.getCurrentChoiceList();

        // Only add numeric index forms - text names are functional duplicates
        for (int i = 0; i < validChoices.size(); i++) {
            commands.add("choose " + i);
        }

        return commands;
    }

    /**
     * Enumerates all valid potion commands for the current game state.
     * @return A list of exact potion command strings (e.g., "potion use 0", "potion use 0 1", "potion discard 0")
     */
    private static ArrayList<String> getPotionCommands() {
        ArrayList<String> commands = new ArrayList<>();
        if (!isPotionCommandAvailable()) {
            return commands;
        }

        for (int potionIndex = 0; potionIndex < AbstractDungeon.player.potionSlots; potionIndex++) {
            AbstractPotion potion = AbstractDungeon.player.potions.get(potionIndex);

            // Skip empty potion slots
            if (potion instanceof PotionSlot) {
                continue;
            }

            // Handle potion use commands
            if (potion.canUse()) {
                if (potion.targetRequired) {
                    // Enumerate all valid monster targets
                    for (int monsterIndex = 0; monsterIndex < AbstractDungeon.getCurrRoom().monsters.monsters.size(); monsterIndex++) {
                        commands.add("potion use " + potionIndex + " " + monsterIndex);
                    }
                } else {
                    // Potion doesn't require a target
                    commands.add("potion use " + potionIndex);
                }
            }

            // Handle potion discard commands
            if (potion.canDiscard()) {
                commands.add("potion discard " + potionIndex);
            }
        }

        return commands;
    }

}
