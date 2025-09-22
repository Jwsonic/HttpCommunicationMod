package httpcommunicationmod;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class HealthHandler implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(HealthHandler.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"GET".equals(method)) {
            sendMethodNotAllowed(exchange);
            return;
        }

        try {
            HashMap<String, Object> healthResponse = new HashMap<>();
            healthResponse.put("status", "healthy");
            healthResponse.put("mod_name", "HTTP Communication Mod");
            healthResponse.put("version", "3.0.0");
            healthResponse.put("endpoints", new String[]{"/state", "/command", "/health"});

            Gson gson = new Gson();
            String jsonResponse = gson.toJson(healthResponse);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

        } catch (Exception e) {
            logger.error("Error handling /health request: " + e.getMessage());
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