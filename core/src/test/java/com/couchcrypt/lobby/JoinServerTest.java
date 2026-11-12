package com.couchcrypt.lobby;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinServerTest {
    private static final class Client implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket ws) { ws.request(1); }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            partial.append(text);
            if (last) { messages.add(partial.toString()); partial.setLength(0); }
            ws.request(1); return CompletableFuture.completedFuture(null);
        }
        JsonValue expect(String type) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < until) {
                String next = messages.poll(100, TimeUnit.MILLISECONDS);
                if (next != null) {
                    JsonValue value = new JsonReader().parse(next);
                    if (type.equals(value.getString("type"))) return value;
                }
            }
            throw new AssertionError("No message of type " + type);
        }
    }
    @Test void servesControllerAndEnforcesTwoPlayerLimitOverRealWebSockets() throws Exception {
        var state = new LobbyState();
        var server = new JoinServer(0, state, Map.of("/", new JoinServer.Asset("controller".getBytes(StandardCharsets.UTF_8), "text/html")));
        server.open();
        try (var http = HttpClient.newHttpClient()) {
            String base = server.url("127.0.0.1");
            var response = http.send(HttpRequest.newBuilder(URI.create(base)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); assertEquals("controller", response.body());
            var session = http.send(HttpRequest.newBuilder(URI.create(base + "session")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(server.room(), new JsonReader().parse(session.body()).getString("room"));
            String url = "ws://127.0.0.1:" + server.getListeningPort() + "/socket?room=" + server.room();
            Client one = new Client(), two = new Client(), three = new Client();
            WebSocket first = http.newWebSocketBuilder().buildAsync(URI.create(url), one).get(3, TimeUnit.SECONDS);
            WebSocket second = http.newWebSocketBuilder().buildAsync(URI.create(url), two).get(3, TimeUnit.SECONDS);
            WebSocket third = http.newWebSocketBuilder().buildAsync(URI.create(url), three).get(3, TimeUnit.SECONDS);
            assertEquals(1, one.expect("joined").getInt("player"));
            assertEquals(2, two.expect("joined").getInt("player"));
            assertEquals("lobby_full", three.expect("error").getString("reason"));
            assertEquals(2, state.snapshot(System.currentTimeMillis()).connected());
            server.remove(0);
            one.expect("removed");
            assertEquals(1, state.snapshot(System.currentTimeMillis()).connected());
            first.abort(); second.abort(); third.abort();
        } finally { server.stop(); }
    }
    @Test void wrongRoomCannotClaimAPlayerSlot() throws Exception {
        var state = new LobbyState(); var server = new JoinServer(0, state, Map.of()); server.open();
        try (var http = HttpClient.newHttpClient()) {
            Client listener = new Client();
            var socket = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + server.getListeningPort() + "/socket?room=WRONG"), listener).get(3, TimeUnit.SECONDS);
            assertEquals("invalid_room", listener.expect("error").getString("reason"));
            assertEquals(0, state.snapshot(System.currentTimeMillis()).connected());
            socket.abort();
        } finally { server.stop(); }
    }
    @Test void inputOverWebSocketCanOnlyControlItsAssignedSlot() throws Exception {
        var state = new LobbyState(); var server = new JoinServer(0, state, Map.of()); server.open();
        try (var http = HttpClient.newHttpClient()) {
            Client listener = new Client();
            var socket = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + server.getListeningPort() + "/socket?room=" + server.room()), listener).get(3, TimeUnit.SECONDS);
            listener.expect("joined"); state.join(null, "second", System.currentTimeMillis());
            state.setPhase(LobbyState.Phase.SESSION);
            socket.sendText("{\"type\":\"input\",\"player\":2,\"x\":1,\"y\":0,\"dodge\":true,\"seq\":0}", true).get(3, TimeUnit.SECONDS);
            var controls = new LobbyState.Controls();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            do {
                state.readControls(0, controls, System.currentTimeMillis());
                if (controls.x == 1) break;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            } while (System.nanoTime() < until);
            assertEquals(1, controls.x); assertTrue(controls.dodge);
            state.readControls(1, controls, System.currentTimeMillis()); assertEquals(0, controls.x); assertFalse(controls.dodge);
            socket.abort();
        } finally { server.stop(); }
    }
}
