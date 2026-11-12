package com.couchcrypt.lobby;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.security.SecureRandom;

/** One local port serves the controller and its WebSocket. No cloud service is involved. */
public final class JoinServer extends NanoWSD {
    public record Asset(byte[] data, String type) {}
    private final Map<String, Asset> assets;
    private final LobbyState state;
    private final Map<String, Phone> phones = new ConcurrentHashMap<>();
    private final String room;
    private ScheduledExecutorService timer;
    private long lastRevision = -1;

    public JoinServer(int port, LobbyState state, Map<String, Asset> assets) {
        super(port);
        this.state = state;
        this.assets = Map.copyOf(assets);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        room = code.toString();
    }

    public void open() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "lobby-heartbeat"); thread.setDaemon(true); return thread;
        });
        timer.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
    }

    public String room() { return room; }
    public String url(String host) { return "http://" + host + ":" + getListeningPort() + "/"; }

    @Override protected Response serveHttp(IHTTPSession session) {
        if (session.getMethod() != Method.GET && session.getMethod() != Method.HEAD)
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "GET only");
        Response response;
        if ("/session".equals(session.getUri())) {
            response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"room\":\"" + room + "\"}");
        } else {
            Asset asset = assets.get(session.getUri());
            if (asset == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
            response = newFixedLengthResponse(Response.Status.OK, asset.type(),
                new java.io.ByteArrayInputStream(asset.data()), asset.data().length);
        }
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        response.addHeader("Content-Security-Policy", "default-src 'self'; connect-src 'self' ws:; img-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'");
        return response;
    }

    @Override protected WebSocket openWebSocket(IHTTPSession session) { return new Phone(session); }

    private String status(int slot) {
        LobbyState.Snapshot snapshot = state.snapshot(System.currentTimeMillis());
        LobbyState.Feedback feedback = state.feedback(slot);
        return "{\"type\":\"status\",\"connected\":" + snapshot.connected()
            + ",\"hostActive\":" + snapshot.hostActive() + ",\"phase\":\""
            + snapshot.phase().name().toLowerCase(java.util.Locale.ROOT) + "\",\"health\":" + feedback.health()
            + ",\"maxHealth\":" + feedback.maxHealth() + ",\"dodgeCooldown\":" + feedback.dodgeCooldown()
            + ",\"alive\":" + feedback.alive() + "}";
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            var snapshot = state.snapshot(now);
            boolean changed = snapshot.revision() != lastRevision;
            lastRevision = snapshot.revision();
            for (Phone phone : phones.values()) {
                if (now - phone.lastSeen > 10_000) phone.end("timeout");
                else if (changed || now - phone.lastStatus >= (snapshot.phase() == LobbyState.Phase.SESSION ? 200 : 3_000))
                    phone.deliver(status(phone.slot));
            }
        } catch (RuntimeException ignored) { /* A closed socket must not stop the heartbeat. */ }
    }

    public void remove(int player) {
        String id = state.remove(player);
        if (id != null && timer != null) {
            timer.execute(() -> {
                Phone phone = phones.get(id);
                if (phone != null) {
                    phone.deliver("{\"type\":\"removed\"}");
                    phone.end("removed");
                }
            });
        }
    }

    @Override public void stop() {
        if (timer != null) { timer.shutdownNow(); timer = null; }
        for (Phone phone : phones.values()) phone.end("server_stopped");
        super.stop();
        phones.clear();
    }

    private final class Phone extends WebSocket {
        private final IHTTPSession request;
        private final String id = UUID.randomUUID().toString();
        private volatile long lastSeen = System.currentTimeMillis();
        private volatile long lastStatus;
        private int slot;
        Phone(IHTTPSession request) { super(request); this.request = request; }

        @Override protected void onOpen() {
            if (!"/socket".equals(request.getUri()) || !room.equals(request.getParms().get("room"))) {
                deliver("{\"type\":\"error\",\"reason\":\"invalid_room\"}"); end("invalid_room"); return;
            }
            String origin = request.getHeaders().get("origin");
            if (origin != null && !origin.equals("http://" + request.getHeaders().get("host"))) {
                end("invalid_origin"); return;
            }
            var joined = state.join(request.getParms().get("token"), id, System.currentTimeMillis());
            if (joined.error() != null) {
                deliver("{\"type\":\"error\",\"reason\":\"" + joined.error() + "\"}");
                end(joined.error()); return;
            }
            slot = joined.slot();
            phones.put(id, this);
            if (!state.owns(id)) { end("removed"); return; }
            deliver("{\"type\":\"joined\",\"player\":" + (joined.slot() + 1)
                + ",\"token\":\"" + joined.token() + "\"}");
            deliver(status(slot));
        }

        @Override protected void onMessage(WebSocketFrame frame) {
            String text = frame.getTextPayload();
            if (text == null || text.length() > 512) { end("bad_message"); return; }
            try {
                JsonValue message = new JsonReader().parse(text);
                if ("ping".equals(message.getString("type", ""))) {
                    lastSeen = System.currentTimeMillis(); deliver(status(slot));
                }
                else if ("input".equals(message.getString("type", ""))) {
                    JsonValue sequence = message.get("seq"), x = message.get("x"), y = message.get("y"), dodge = message.get("dodge");
                    if (sequence == null || !sequence.isLong() || sequence.asLong() < 0 || sequence.asLong() > Integer.MAX_VALUE
                        || x == null || !x.isNumber() || y == null || !y.isNumber() || dodge == null || !dodge.isBoolean()) {
                        end("bad_message"); return;
                    }
                    long now = System.currentTimeMillis();
                    float inputX = x.asFloat(), inputY = y.asFloat();
                    if (!Float.isFinite(inputX) || !Float.isFinite(inputY)) { end("bad_message"); return; }
                    if (state.owns(id)) {
                        lastSeen = now;
                        state.input(id, sequence.asInt(), inputX, inputY, dodge.asBoolean(), now);
                    }
                }
            } catch (RuntimeException malformed) { end("bad_message"); }
        }

        synchronized void deliver(String message) {
            try { if (isOpen()) { send(message); lastStatus = System.currentTimeMillis(); } }
            catch (IOException failure) { end("connection_lost"); }
        }

        void end(String reason) {
            state.disconnected(id, System.currentTimeMillis());
            phones.remove(id);
            try { close(WebSocketFrame.CloseCode.NormalClosure, reason, false); }
            catch (IOException ignored) { }
        }

        @Override protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean remote) {
            phones.remove(id); state.disconnected(id, System.currentTimeMillis());
        }
        @Override protected void onPong(WebSocketFrame pong) { lastSeen = System.currentTimeMillis(); }
        @Override protected void onException(IOException exception) { end("connection_lost"); }
    }
}
