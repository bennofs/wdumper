package io.github.bennofs.wdumper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.ServerWebSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class LiveReloadServer implements Handler<ServerWebSocket> {
    private final String livereloadJs;
    private Path commonAncestorChanged = Path.of("");

    public LiveReloadServer(String livereloadJs) {
        this.livereloadJs = livereloadJs;
    }

    public static void main(String[] args) throws Exception {
        LiveReloadServer server = LiveReloadServer.create();
        server.startServer();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("{}");
            reader.lines().forEach(request -> {
                try {
                    server.handleGradleMessage(request);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("{}");
            });
        }
    }

    public void handleGradleMessage(String request) throws IOException {
        final JsonNode parsed = objectMapper.readTree(request);
        final String command = parsed.get("command").asText();

        if (command.equals("reload")) {
            final String[] changed = objectMapper.convertValue(parsed.get("changed"), String[].class);
            for (String path : changed) {
                if (this.commonAncestorChanged.toString().isEmpty()) {
                    this.commonAncestorChanged = Path.of(path);
                    continue;
                }

                while (!Path.of(path).startsWith(this.commonAncestorChanged)) {
                    this.commonAncestorChanged = this.commonAncestorChanged.getParent();
                }
            }
            return;
        }

        if (command.equals("buildFinished")) {
            this.reload(this.commonAncestorChanged.toString());
            this.commonAncestorChanged = Path.of("");
        }
    }

    public static LiveReloadServer create() throws IOException {
        try (final InputStream stream = LiveReloadServer.class.getResourceAsStream("/livereload/livereload.js")) {
            return new LiveReloadServer(new String(stream.readAllBytes()));
        }
    }

    public void startServer() {
        Vertx vertx = Vertx.vertx(new VertxOptions());
        vertx.createHttpServer().requestHandler(req -> {
            if (req.path().equals("/livereload.js")) {
                req.response()
                        .putHeader("Content-Type", "text/javascript")
                        .end(livereloadJs);
                return;
            }

            req.response()
                    .setStatusCode(404)
                    .end();
        }).webSocketHandler(this).listen(35729);
    }

    public void reload(String path) {
        broadcast(buildReloadMessage(path));
    }

    public void alert(String path) {
        broadcast(buildAlertMessage(path));
    }

    void broadcast(String message) {
        clients.forEach(c -> c.writeTextMessage(message));
    }

    private final static Logger LOGGER = Logger.getLogger(LiveReloadServer.class.getName());

    private final Set<ServerWebSocket> clients = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static String HELLO_MESSAGE = "" +
            "{\"command\": \"hello\", " +
            "\"protocols\": [\"http://livereload.com/protocols/official-7\"], " +
            "\"serverName\": \"java-livereload\"" +
            "}";

    private static String buildReloadMessage(String path) {
        return String.format("{" +
                "\"command\": \"reload\"," +
                "\"path\": \"%s\"," +
                "\"liveCSS\": true" +
                "}", "");
    }

    private static String buildAlertMessage(String msg) {
        return String.format("{" +
                "\"command\": \"alert\"," +
                "\"message\": \"%s\"" +
                "}", "");
    }


    @Override
    public void handle(ServerWebSocket socket) {
        LOGGER.fine("live reload client connected");
        this.clients.add(socket);
        socket.textMessageHandler(message -> {
            try {
                this.onMessage(socket, message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        socket.closeHandler(v -> {
            LOGGER.fine("live reload client disconnected");
            this.clients.remove(socket);
        });
    }

    public void onMessage(ServerWebSocket socket, String message) throws Exception {
        final Optional<String> command = parseCommand(message);
        if (command.isEmpty()) {
            LOGGER.info("ignoring livereload message without command");
            return;
        }

        if (command.get().equals("hello")) {
            socket.writeTextMessage(HELLO_MESSAGE);
        }
    }

    private Optional<String> parseCommand(String msg) throws JsonProcessingException {
        final JsonNode parsed = objectMapper.readTree(msg);
        final JsonNode command = parsed.get("command");
        if (command == null) return Optional.empty();

        if (!command.isTextual()) {
            throw new RuntimeException("livereload command value must be string");
        }
        return Optional.of(command.asText());
    }
}