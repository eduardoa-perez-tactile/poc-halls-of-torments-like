package com.couchcrypt.lobby;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.ItemCatalog;
import com.couchcrypt.game.RunEquipment;
import java.util.List;
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
    @Test void equipmentStatusAndSelectionUseSeparateSocketOwnedOffersAndHostAcknowledgment() throws Exception {
        var state = new LobbyState(); var server = new JoinServer(0, state, Map.of()); server.open();
        try (var http = HttpClient.newHttpClient()) {
            Client one = new Client(), two = new Client();
            var url = URI.create("ws://127.0.0.1:" + server.getListeningPort() + "/socket?room=" + server.room());
            var first = http.newWebSocketBuilder().buildAsync(url, one).get(3, TimeUnit.SECONDS);
            var second = http.newWebSocketBuilder().buildAsync(url, two).get(3, TimeUnit.SECONDS);
            one.expect("joined"); two.expect("joined");
            var options = ItemCatalog.ALL.subList(0, 3).toArray(ItemCatalog.Item[]::new);
            var cards = new PartyProgression.CardPreview[3];
            for (int i = 0; i < 3; i++) cards[i] = new PartyProgression.CardPreview(options[i].icon(), 0,
                "DEFENSE", "HELMET: REPLACES Old Guard", "+10 HP", options[i].description());
            state.publishEquipment(0, 10, 26.5f, options, cards);
            state.publishEquipment(1, 20, 30, options, cards);
            state.setPhase(LobbyState.Phase.SESSION);
            JsonValue status;
            do { status = one.expect("status"); } while (status.get("equipment").isNull());
            var offer = status.get("equipment");
            assertEquals(10, offer.getInt("id")); assertEquals(26.5f, offer.getFloat("secondsLeft"));
            assertEquals(3, offer.get("options").size);
            assertTrue(status.get("progression").get("upgrade").isNull());
            for (int i = 0; i < 3; i++) {
                var json = offer.get("options").get(i); var item = options[i]; var card = cards[i];
                assertEquals(item.id(), json.getString("id")); assertEquals(item.title(), json.getString("title"));
                assertEquals(item.description(), json.getString("description")); assertEquals(item.slot().name(), json.getString("slot"));
                assertEquals(0, json.getInt("rank")); assertEquals(ItemCatalog.artIndex(item), json.getInt("icon"));
                assertEquals(card.category(), json.getString("category")); assertEquals(card.headline(), json.getString("headline"));
                assertEquals(card.before(), json.getString("before")); assertEquals(card.after(), json.getString("after"));
            }
            first.sendText("{\"type\":\"upgrade\",\"offerId\":10,\"option\":0}", true).get(3, TimeUnit.SECONDS);
            first.sendText("{\"type\":\"equipment\",\"offerId\":20,\"option\":0}", true).get(3, TimeUnit.SECONDS);
            first.sendText("{\"type\":\"equipment\",\"offerId\":10,\"option\":3}", true).get(3, TimeUnit.SECONDS);
            first.sendText("{\"type\":\"equipment\",\"player\":2,\"offerId\":10,\"option\":2}", true).get(3, TimeUnit.SECONDS);
            var request = new LobbyState.EquipmentRequest();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean found;
            do {
                found = state.pollEquipment(0, request);
                if (found) break;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            } while (System.nanoTime() < until);
            assertTrue(found); assertEquals(10, request.offerId); assertEquals(2, request.option);
            assertFalse(state.pollEquipment(1, request)); assertFalse(state.pollUpgrade(0, new LobbyState.UpgradeRequest()));
            assertEquals(10, state.equipmentFeedback(0).id(), "A click leaves the offer visible until simulation acknowledgment");
            state.publishEquipment(0, 0, 0, null, null);
            first.sendText("{\"type\":\"ping\"}", true).get(3, TimeUnit.SECONDS);
            do { status = one.expect("status"); } while (!status.get("equipment").isNull());
            assertEquals(2, status.getInt("connected"));
            state.publishEquipment(0, 11, 14.5f, options, cards, true, 15);
            var foundationItem = ItemCatalog.byId("emberheart_pendant");
            state.publishCatchUp(0, 0, 2, "Soulfire 3 / Soul Circle 2", "Gatekeeper Helm / Emberheart Pendant",
                List.of(new PartyProgression.FoundationSkill("power", "Soulfire", 0, 3),
                    new PartyProgression.FoundationSkill("aura", "Soul Circle", 8, 2)),
                List.of(new RunEquipment.FoundationItem(foundationItem.id(), foundationItem.title(),
                    ItemCatalog.artIndex(foundationItem), foundationItem.slot().name())));
            first.sendText("{\"type\":\"ping\"}", true).get(3, TimeUnit.SECONDS);
            do { status = one.expect("status"); }
            while (status.get("equipment").isNull() || status.get("equipment").getInt("id") != 11);
            assertTrue(status.get("equipment").getBoolean("catchUp"));
            assertEquals(15, status.get("equipment").getFloat("durationSeconds"));
            assertEquals(14.5f, status.get("equipment").getFloat("secondsLeft"));
            assertEquals(2, status.get("catchUp").getInt("equipmentRemaining"));
            assertEquals("Soulfire 3 / Soul Circle 2", status.get("catchUp").getString("skillFoundation"));
            var foundation = status.get("catchUp");
            assertEquals(2, foundation.get("skills").size);
            assertEquals("power", foundation.get("skills").get(0).getString("id"));
            assertEquals("Soulfire", foundation.get("skills").get(0).getString("title"));
            assertEquals(0, foundation.get("skills").get(0).getInt("icon"));
            assertEquals(3, foundation.get("skills").get(0).getInt("rank"));
            assertEquals(8, foundation.get("skills").get(1).getInt("icon"));
            assertEquals(2, foundation.get("skills").get(1).getInt("rank"));
            assertEquals(1, foundation.get("equipment").size);
            var itemJson = foundation.get("equipment").get(0);
            assertEquals(foundationItem.id(), itemJson.getString("id"));
            assertEquals(foundationItem.title(), itemJson.getString("title"));
            assertEquals(ItemCatalog.artIndex(foundationItem), itemJson.getInt("icon"));
            assertEquals(foundationItem.slot().name(), itemJson.getString("slot"));
            first.sendText("{\"type\":\"equipment\",\"offerId\":11,\"option\":-1}", true).get(3, TimeUnit.SECONDS);
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            do {
                found = state.pollEquipment(0, request);
                if (!found) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            } while (!found && System.nanoTime() < until);
            assertTrue(found); assertEquals(-1, request.option);
            state.publishEquipment(0, 0, 0, null, null);
            state.publishProgression(0, 20, 0, 0, 30, 19, 12,
                PartyProgression.Upgrade.values(), null, true, 15);
            state.publishCatchUp(0, 1, 0, "Soulfire 3", "Gatekeeper Helm");
            first.sendText("{\"type\":\"ping\"}", true).get(3, TimeUnit.SECONDS);
            do { status = one.expect("status"); }
            while (status.get("progression").get("upgrade").isNull());
            assertTrue(status.get("progression").get("upgrade").getBoolean("catchUp"));
            assertEquals(15, status.get("progression").get("upgrade").getFloat("durationSeconds"));
            assertEquals(1, status.get("catchUp").getInt("skillsRemaining"));
            assertEquals(0, status.get("catchUp").get("skills").size);
            assertEquals(0, status.get("catchUp").get("equipment").size);
            first.abort(); second.abort();
        } finally { server.stop(); }
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
    @Test void upgradeStatusAndSelectionStayBoundToTheirSocketPlayer() throws Exception {
        var state = new LobbyState(); var server = new JoinServer(0, state, Map.of()); server.open();
        try (var http = HttpClient.newHttpClient()) {
            Client one = new Client(), two = new Client();
            var url = URI.create("ws://127.0.0.1:" + server.getListeningPort() + "/socket?room=" + server.room());
            var first = http.newWebSocketBuilder().buildAsync(url, one).get(3, TimeUnit.SECONDS);
            var second = http.newWebSocketBuilder().buildAsync(url, two).get(3, TimeUnit.SECONDS);
            one.expect("joined"); two.expect("joined");
            var world = new com.couchcrypt.game.ArenaWorld(); world.activatePlayer(0); world.activatePlayer(1);
            world.progression.addXp(6);
            var options = world.progression.offers[0].options;
            var cards = world.progression.offers[0].cards;
            state.publishProgression(0, 2, 3, 8, 10, 2, 27.5f, options, cards);
            state.publishProgression(1, 2, 3, 8, 20, 2, 27.5f,
                world.progression.offers[1].options, world.progression.offers[1].cards);
            state.setPhase(LobbyState.Phase.SESSION);
            JsonValue status;
            do { status = one.expect("status"); } while (status.get("progression").get("upgrade").isNull());
            var progression = status.get("progression"); var offer = progression.get("upgrade");
            assertEquals(2, progression.getInt("level")); assertEquals(3, progression.getInt("xp"));
            assertEquals(8, progression.getInt("xpToNext")); assertEquals(10, offer.getInt("id"));
            assertEquals(27.5f, offer.getFloat("secondsLeft")); assertEquals(3, offer.get("options").size);
            assertEquals(options[0].id, offer.get("options").get(0).getString("id"));
            assertEquals(options[0].title, offer.get("options").get(0).getString("title"));
            assertEquals(options[0].description, offer.get("options").get(0).getString("description"));
            for (int index = 0; index < 3; index++) {
                var json = offer.get("options").get(index); var card = cards[index];
                assertEquals(card.icon(), json.getInt("icon")); assertEquals(card.rank(), json.getInt("rank"));
                assertEquals(card.category(), json.getString("category")); assertEquals(card.headline(), json.getString("headline"));
                assertEquals(card.before(), json.getString("before")); assertEquals(card.after(), json.getString("after"));
            }
            first.sendText("{\"type\":\"upgrade\",\"offerId\":20,\"option\":0}", true).get(3, TimeUnit.SECONDS);
            first.sendText("{\"type\":\"upgrade\",\"offerId\":10,\"option\":-1}", true).get(3, TimeUnit.SECONDS);
            first.sendText("{\"type\":\"upgrade\",\"player\":2,\"offerId\":10,\"option\":1}", true).get(3, TimeUnit.SECONDS);
            var request = new LobbyState.UpgradeRequest();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            boolean found;
            do {
                found = state.pollUpgrade(0, request);
                if (found) break;
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            } while (System.nanoTime() < until);
            assertTrue(found); assertEquals(10, request.offerId); assertEquals(1, request.option);
            assertFalse(state.pollUpgrade(1, request));
            assertEquals(2, state.snapshot(System.currentTimeMillis()).connected(), "Invalid or stale choices do not disconnect the controller");
            first.abort(); second.abort();
        } finally { server.stop(); }
    }

}
