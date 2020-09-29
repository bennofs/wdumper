package io.github.bennofs.wdumper;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.websocket.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class LiveReloadServer implements WebSocketHandler<WebSocket>, Action<Chain> {
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

    public void startServer() throws Exception {
        RatpackServer.of(s -> s
                .serverConfig(ServerConfig.builder().findBaseDir("livereload/livereload.js").port(35729).development(true))
                .handlers(chain -> {
                    chain.insert(this);
                })
        ).start();
    }

    @Override
    public void execute(Chain chain) throws Exception {
        chain.get("livereload.js", ctx -> {
            ctx.getResponse().send("application/javascript", livereloadJs);
        });
        chain.get("livereload", ctx -> WebSockets.websocket(ctx, this));
    }

    public void reload(String path) {
        broadcast(buildReloadMessage(path));
    }

    public void alert(String path) {
        broadcast(buildAlertMessage(path));
    }

    void broadcast(String message) {
        clients.forEach(c -> c.send(message));
    }

    private final static Logger LOGGER = Logger.getLogger(LiveReloadServer.class.getName());

    private final Set<WebSocket> clients = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static String HELLO_MESSAGE = "" +
            "{\"command\": \"hello\", " +
            "\"protocols\": [\"http://livereload.com/protocols/official-7\"], " +
            "\"serverName\": \"ratpack-livereload\"" +
            "}";

    private static String buildReloadMessage(String path) {
        return String.format("{" +
                "\"command\": \"reload\"," +
                "\"path\": \"%s\"," +
                "\"liveCSS\": true" +
                "}", ""); // new String(quoteAsJsonText(path)));
    }

    private static String buildAlertMessage(String msg) {
        return String.format("{" +
                "\"command\": \"alert\"," +
                "\"message\": \"%s\"" +
                "}", ""); // new String(quoteAsJsonText(msg)));
    }

    @Override
    public WebSocket onOpen(WebSocket webSocket) throws Exception {
        LOGGER.fine("live reload client connected");
        this.clients.add(webSocket);
        return webSocket;
    }

    @Override
    public void onClose(WebSocketClose<WebSocket> close) throws Exception {
        LOGGER.fine("live reload client disconnected");
        this.clients.remove(close.getOpenResult());
    }

    @Override
    public void onMessage(WebSocketMessage<WebSocket> frame) throws Exception {
        final Optional<String> command = parseCommand(frame.getText());
        if (command.isEmpty()) {
            LOGGER.info("ignoring livereload message without command");
            return;
        }

        if (command.get().equals("hello")) {
            frame.getOpenResult().send(HELLO_MESSAGE);
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