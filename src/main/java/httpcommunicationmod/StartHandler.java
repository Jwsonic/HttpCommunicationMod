package httpcommunicationmod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.helpers.TrialHelper;
import com.megacrit.cardcrawl.random.Random;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class StartHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(StartHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"POST".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            logger.info("Handling POST /start request");

            // Read JSON request body
            String requestBody = readRequestBody(exchange);
            if (requestBody == null || requestBody.trim().isEmpty()) {
                sendBadRequest(exchange, "Request body cannot be empty");
                return;
            }

            // Parse JSON
            JsonObject json;
            try {
                json = new JsonParser().parse(requestBody).getAsJsonObject();
            } catch (Exception e) {
                sendBadRequest(exchange, "Invalid JSON format");
                return;
            }

            // Extract parameters
            if (!json.has("character")) {
                sendBadRequest(exchange, "Missing required field: character");
                return;
            }

            String characterName = json.get("character").getAsString();
            int ascensionLevel = json.has("ascension_level") ? json.get("ascension_level").getAsInt() : 0;
            String seedString = json.has("seed") ? json.get("seed").getAsString() : null;

            // Validate ascension level
            if (ascensionLevel < 0 || ascensionLevel > 20) {
                sendBadRequest(exchange, "Ascension level must be between 0 and 20");
                return;
            }

            // Validate and parse character
            AbstractPlayer.PlayerClass selectedClass = parseCharacter(characterName);
            if (selectedClass == null) {
                sendBadRequest(exchange, "Invalid character: " + characterName + ". Valid options: IRONCLAD, THE_SILENT, DEFECT, WATCHER, or SILENT");
                return;
            }

            // Validate and parse seed
            long seed;
            boolean seedSet = false;
            if (seedString != null && !seedString.trim().isEmpty()) {
                String upperSeed = seedString.toUpperCase();
                if (!upperSeed.matches("^[A-Z0-9]+$")) {
                    sendBadRequest(exchange, "Invalid seed format. Seed must contain only letters and numbers");
                    return;
                }
                seedSet = true;
                seed = SeedHelper.getLong(upperSeed);
                boolean isTrialSeed = TrialHelper.isTrialSeed(upperSeed);
                if (isTrialSeed) {
                    Settings.specialSeed = seed;
                    Settings.isTrial = true;
                    seedSet = false;
                }
            } else {
                seed = SeedHelper.generateUnoffensiveSeed(new Random(System.nanoTime()));
            }

            // Start the game
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

            logger.info("Starting game: character=" + selectedClass + ", ascension=" + ascensionLevel + ", seed=" + seed);

            // Build response
            HashMap<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("character", selectedClass.name());
            response.put("ascension_level", ascensionLevel);
            response.put("seed", seed);
            response.put("seed_string", SeedHelper.getString(seed));

            // Send JSON response
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(response);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            logger.info("Successfully started game");

        } catch (Exception e) {
            logger.error("Error handling /start request: " + e.getMessage());
            e.printStackTrace();
            sendInternalServerError(exchange, e.getMessage());
        }
    }

    private AbstractPlayer.PlayerClass parseCharacter(String characterName) {
        String upperName = characterName.toUpperCase();

        // Handle "SILENT" as an alias for "THE_SILENT"
        if (upperName.equals("SILENT")) {
            return AbstractPlayer.PlayerClass.THE_SILENT;
        }

        // Try to match against PlayerClass enum values
        for (AbstractPlayer.PlayerClass playerClass : AbstractPlayer.PlayerClass.values()) {
            if (playerClass.name().equals(upperName)) {
                return playerClass;
            }
        }

        return null;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
                if (body.length() > 1000) { // Prevent overly large requests
                    throw new IOException("Request body too large");
                }
            }
            return body.toString();
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        String response = "{\"error\":\"Method not allowed. Use POST.\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(405, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendBadRequest(HttpExchange exchange, String errorMessage) throws IOException {
        HashMap<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMessage);

        Gson gson = new Gson();
        String jsonResponse = gson.toJson(response);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(400, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendInternalServerError(HttpExchange exchange, String errorMessage) throws IOException {
        HashMap<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Internal server error: " + errorMessage);

        Gson gson = new Gson();
        String jsonResponse = gson.toJson(response);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
