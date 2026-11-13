package com.couchcrypt.lobby;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.ItemCatalog;
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
            + ",\"alive\":" + feedback.alive() + ",\"progression\":" + progressionJson(slot)
            + ",\"equipment\":" + equipmentJson(slot) + ",\"catchUp\":" + catchUpJson(slot) + "}";
    }

    private String progressionJson(int slot) {
        var feedback = state.progressionFeedback(slot);
        StringBuilder result = new StringBuilder(512);
        result.append("{\"level\":").append(feedback.level()).append(",\"xp\":").append(feedback.xp())
            .append(",\"xpToNext\":").append(feedback.xpToNext()).append(",\"upgrade\":");
        var upgrade = feedback.upgrade();
        if (upgrade == null) return result.append("null}").toString();
        result.append("{\"id\":").append(upgrade.id()).append(",\"level\":").append(upgrade.level())
            .append(",\"secondsLeft\":").append(upgrade.secondsLeft())
            .append(",\"catchUp\":").append(upgrade.catchUp()).append(",\"durationSeconds\":").append(upgrade.durationSeconds())
            .append(",\"options\":[");
        appendUpgrade(result, upgrade.first(), upgrade.firstCard()); result.append(',');
        appendUpgrade(result, upgrade.second(), upgrade.secondCard()); result.append(',');
        appendUpgrade(result, upgrade.third(), upgrade.thirdCard());
        return result.append("]}}").toString();
    }

    private static void appendUpgrade(StringBuilder target, PartyProgression.Upgrade upgrade, PartyProgression.CardPreview card) {
        target.append("{\"id\":"); quote(target, upgrade.id);
        target.append(",\"title\":"); quote(target, upgrade.title);
        target.append(",\"description\":"); quote(target, upgrade.description);
        if (card != null) {
            target.append(",\"icon\":").append(card.icon()).append(",\"rank\":").append(card.rank());
            target.append(",\"category\":"); quote(target, card.category());
            target.append(",\"headline\":"); quote(target, card.headline());
            target.append(",\"before\":"); quote(target, card.before());
            target.append(",\"after\":"); quote(target, card.after());
        }
        target.append('}');
    }

    private String equipmentJson(int slot) {
        var offer = state.equipmentFeedback(slot);
        if (offer == null) return "null";
        StringBuilder result = new StringBuilder(768);
        result.append("{\"id\":").append(offer.id()).append(",\"secondsLeft\":").append(offer.secondsLeft())
            .append(",\"catchUp\":").append(offer.catchUp()).append(",\"durationSeconds\":").append(offer.durationSeconds())
            .append(",\"options\":[");
        appendEquipment(result, offer.first(), offer.firstCard()); result.append(',');
        appendEquipment(result, offer.second(), offer.secondCard()); result.append(',');
        appendEquipment(result, offer.third(), offer.thirdCard());
        return result.append("]}").toString();
    }

    private String catchUpJson(int slot) {
        var feedback = state.catchUpFeedback(slot);
        if (feedback.skillsRemaining() == 0 && feedback.equipmentRemaining() == 0) return "null";
        StringBuilder result = new StringBuilder(384);
        result.append("{\"skillsRemaining\":").append(feedback.skillsRemaining())
            .append(",\"equipmentRemaining\":").append(feedback.equipmentRemaining())
            .append(",\"skillFoundation\":"); quote(result, feedback.skillFoundation());
        result.append(",\"equipmentFoundation\":"); quote(result, feedback.equipmentFoundation());
        result.append(",\"skills\":[");
        for (int i = 0; i < feedback.skills().size(); i++) {
            if (i > 0) result.append(',');
            var skill = feedback.skills().get(i);
            result.append("{\"id\":"); quote(result, skill.id());
            result.append(",\"title\":"); quote(result, skill.title());
            result.append(",\"icon\":").append(skill.icon()).append(",\"rank\":").append(skill.rank()).append('}');
        }
        result.append("],\"equipment\":[");
        for (int i = 0; i < feedback.equipment().size(); i++) {
            if (i > 0) result.append(',');
            var item = feedback.equipment().get(i);
            result.append("{\"id\":"); quote(result, item.id());
            result.append(",\"title\":"); quote(result, item.title());
            result.append(",\"icon\":").append(item.icon()).append(",\"slot\":"); quote(result, item.slot());
            result.append('}');
        }
        return result.append("]}").toString();
    }

    private static void appendEquipment(StringBuilder target, ItemCatalog.Item item, PartyProgression.CardPreview card) {
        target.append("{\"id\":"); quote(target, item.id());
        target.append(",\"title\":"); quote(target, item.title());
        target.append(",\"description\":"); quote(target, item.description());
        target.append(",\"slot\":"); quote(target, item.slot().name());
        target.append(",\"icon\":").append(ItemCatalog.artIndex(item)).append(",\"rank\":0");
        target.append(",\"category\":"); quote(target, card.category());
        target.append(",\"headline\":"); quote(target, card.headline());
        target.append(",\"before\":"); quote(target, card.before());
        target.append(",\"after\":"); quote(target, card.after());
        target.append('}');
    }

    private static void quote(StringBuilder target, String text) {
        target.append('"');
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == '"' || value == '\\') target.append('\\').append(value);
            else if (value < 32) {
                target.append("\\u00").append("0123456789abcdef".charAt(value >>> 4))
                    .append("0123456789abcdef".charAt(value & 15));
            } else target.append(value);
        }
        target.append('"');
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
                else if ("upgrade".equals(message.getString("type", "")) || "equipment".equals(message.getString("type", ""))) {
                    boolean equipment = "equipment".equals(message.getString("type"));
                    JsonValue offerId = message.get("offerId"), option = message.get("option");
                    if (offerId == null || !offerId.isLong() || offerId.asLong() <= 0 || offerId.asLong() > Integer.MAX_VALUE
                        || option == null || !option.isLong() || option.asLong() < (equipment ? -1 : 0) || option.asLong() > 2) return;
                    if (state.owns(id)) {
                        lastSeen = System.currentTimeMillis();
                        if (equipment) state.chooseEquipment(id, offerId.asInt(), option.asInt());
                        else state.chooseUpgrade(id, offerId.asInt(), option.asInt());
                    }
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
