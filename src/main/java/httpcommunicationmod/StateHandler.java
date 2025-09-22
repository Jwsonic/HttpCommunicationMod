package httpcommunicationmod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class StateHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(StateHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"GET".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            logger.info("Handling GET /state request");

            // Get current game state
            String gameState = HttpCommunicationMod.getCurrentGameState();

            // Set response headers
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            // Send response
            byte[] response = gameState.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }

            logger.info("Successfully sent game state response");

        } catch (Exception e) {
            logger.error("Error handling /state request: " + e.getMessage());
            e.printStackTrace();
            sendInternalServerError(exchange, e.getMessage());
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        String response = "{\"error\":\"Method not allowed. Use GET.\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(405, responseBytes.length);
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