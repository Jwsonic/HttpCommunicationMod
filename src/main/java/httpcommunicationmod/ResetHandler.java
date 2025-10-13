package httpcommunicationmod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ResetHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(ResetHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"POST".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            logger.info("Handling POST /reset request");

            // Execute the reset logic
            CommandExecutor.executeStartOver(new String[]{"reset"});

            logger.info("Game reset successfully");

            // Send 204 No Content response
            exchange.sendResponseHeaders(204, -1);

        } catch (Exception e) {
            logger.error("Error handling /reset request: " + e.getMessage());
            e.printStackTrace();
            sendInternalServerError(exchange, e.getMessage());
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
