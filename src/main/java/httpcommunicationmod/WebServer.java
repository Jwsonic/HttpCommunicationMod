package httpcommunicationmod;

import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class WebServer {
    private static final Logger logger = LogManager.getLogger(WebServer.class.getName());
    private HttpServer server;
    private String host;
    private int port;

    public void start(String host, int port) throws IOException {
        this.host = host;
        this.port = port;

        server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // Set up endpoints
        server.createContext("/state", new StateHandler());
        server.createContext("/command", new CommandHandler());
        server.createContext("/start", new StartHandler());
        server.createContext("/reset", new ResetHandler());
        server.createContext("/health", new HealthHandler());

        // Use a thread pool for handling requests
        server.setExecutor(Executors.newCachedThreadPool());

        server.start();
        logger.info("HTTP Communication Mod web server started on http://" + host + ":" + port);
    }

    public void stop() {
        if (server != null) {
            logger.info("Stopping HTTP Communication Mod web server...");
            server.stop(1); // Stop with 1 second delay
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getAddress() {
        if (server != null) {
            return "http://" + host + ":" + port;
        }
        return null;
    }
}