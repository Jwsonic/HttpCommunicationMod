package httpcommunicationmod;

import com.google.gson.Gson;
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

public class CommandHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(CommandHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"POST".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            logger.info("Handling POST /command request");

            // Read command from request body
            String command = readRequestBody(exchange);
            if (command == null || command.trim().isEmpty()) {
                sendBadRequest(exchange, "Command cannot be empty");
                return;
            }

            logger.info("Executing command: " + command);

            // Execute command and get response
            HashMap<String, Object> response = executeCommand(command.trim());

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

            logger.info("Successfully executed command and sent response");

        } catch (Exception e) {
            logger.error("Error handling /command request: " + e.getMessage());
            e.printStackTrace();
            sendInternalServerError(exchange, e.getMessage());
        }
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

    private HashMap<String, Object> executeCommand(String command) {
        HashMap<String, Object> response = new HashMap<>();

        try {
            // Execute the command using existing CommandExecutor
            boolean stateChanged = CommandExecutor.executeCommand(command);

            // Build successful response
            response.put("success", true);
            response.put("command", command);
            response.put("state_changed", stateChanged);

            // Include current game state in response
            String currentState = HttpCommunicationMod.getCurrentGameState();
            Gson gson = new Gson();
            Object gameStateObject = gson.fromJson(currentState, Object.class);
            response.put("game_state", gameStateObject);

            if (stateChanged) {
                GameStateListener.registerCommandExecution();
            }

        } catch (InvalidCommandException e) {
            // Build error response
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("command", command);

            // Still include current game state even on error
            try {
                String currentState = HttpCommunicationMod.getCurrentGameState();
                Gson gson = new Gson();
                Object gameStateObject = gson.fromJson(currentState, Object.class);
                response.put("game_state", gameStateObject);
            } catch (Exception stateException) {
                logger.error("Error getting game state for error response: " + stateException.getMessage());
            }
        } catch (Exception e) {
            // Build general error response
            response.put("success", false);
            response.put("error", "Unexpected error: " + e.getMessage());
            response.put("command", command);
        }

        return response;
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
        String response = "{\"error\":\"" + errorMessage.replace("\"", "\\\"") + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(400, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendInternalServerError(HttpExchange exchange, String errorMessage) throws IOException {
        String response = "{\"error\":\"Internal server error: " + errorMessage.replace("\"", "\\\"") + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}