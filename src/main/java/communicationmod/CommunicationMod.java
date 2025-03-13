package communicationmod;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import communicationmod.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.IOUtils;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber,
        PreUpdateSubscriber, OnStateChangeSubscriber {

    public static boolean messageReceived = false;
    private static final Logger logger = LogManager.getLogger(CommunicationMod.class.getName());
    private static final String MODNAME = "Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with an external program to play Slay the Spire.";
    public static boolean mustSendGameState = false;

    // SpireInitializer calls initialize
    public static void initialize() {
        @SuppressWarnings("unused")
        CommunicationMod mod = new CommunicationMod();
    }

    public static void dispose() {

    }

    public CommunicationMod() {
        BaseMod.subscribe(this);
    }

    @Override
    public void receivePostInitialize() {
        // TODO: Start web server

        System.out.println("Hello");

        new Thread(() -> {
            port(5555); // Set the port to 5555
            ipAddress("127.0.0.1"); // Listen on localhost
            Gson gson = new Gson();
            Map<String, Object> state = new HashMap<>();

            post("/command", (req, res) -> {
                res.type("application/json");
                try {
                    Map<String, Object> command = gson.fromJson(req.body(), HashMap.class);
                    if (command != null && command.containsKey("command")) {
                        System.out.println("Received command: " + command.get("command"));
                        res.status(200);
                        return "HELLO!";
                    } else {
                        res.status(400);
                        return "Bad request";
                    }
                } catch (Exception e) {
                    res.status(400);
                    return "Bad request";
                }
            });

            init(); // Initialize SparkJava and start the server

            System.out.println("Spark server started on port 5555 (localhost).");

        }).start();
    }

    // public static class CommandServlet extends HttpServlet {
    // private static final Gson gson = new Gson();

    // @Override
    // protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    // throws IOException {
    // try (BufferedReader reader = req.getReader()) {
    // String requestBody = IOUtils.toString(req.getReader());
    // Type mapType = new TypeToken<Map<String, String>>() {
    // }.getType();
    // Map<String, String> command = gson.fromJson(requestBody, mapType);

    // if (command != null && command.containsKey("command")) {
    // System.out.println("Received command: " + command.get("command"));
    // resp.setStatus(HttpServletResponse.SC_OK);
    // resp.getWriter().write(GameStateConverter.getCommunicationState());
    // } else {
    // resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    // }
    // } catch (Exception e) {
    // resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    // }
    // }

    // }

    @Override
    public void receivePreUpdate() {
        try {
            boolean stateChanged = CommandExecutor.executeCommand("STATE");
            if (stateChanged) {
                GameStateListener.registerCommandExecution();
            }
        } catch (InvalidCommandException e) {
            HashMap<String, Object> jsonError = new HashMap<>();
            jsonError.put("error", e.getMessage());
            jsonError.put("ready_for_command", GameStateListener.isWaitingForCommand());
            Gson gson = new Gson();
            // sendMessage(gson.toJson(jsonError));
        }
    }

    public void receivePostUpdate() {
        // if (!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
        // mustSendGameState = true;
        // }
        // if (mustSendGameState) {
        // publishOnGameStateChange();
        // mustSendGameState = false;
        // }
        // InputActionPatch.doKeypress = false;
    }

    @Override
    public void receiveOnStateChange() {
        // String state = GameStateConverter.getCommunicationState();
    }

    @Override
    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

}
