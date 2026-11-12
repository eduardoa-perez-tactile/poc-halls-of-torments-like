package com.couchcrypt;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.couchcrypt.lobby.JoinServer;
import com.couchcrypt.lobby.LanAddress;
import com.couchcrypt.lobby.LobbyState;
import com.couchcrypt.ui.RemoteButton;
import com.couchcrypt.ui.UiArt;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CryptLobby extends ApplicationAdapter {
    private final GameConfig config;
    private final LobbyState state = new LobbyState();
    private Stage stage;
    private UiArt art;
    private JoinServer server;
    private Table content, overlay;
    private final List<RemoteButton> buttons = new ArrayList<>();
    private List<RemoteButton> savedButtons;
    private int focus, savedFocus;
    private boolean lobby, debug;
    private String host, serverError;
    private Label firstStatus, secondStatus, partyStatus, startLabel, connectionLabel, diagnostics;
    private RemoteButton start;
    private Image qrImage;
    private Texture qr;
    private float refreshClock, profileClock;
    private long lastRevision = -1;
    private int lastFirstSeconds = -1, lastSecondSeconds = -1;
    private final Vector2 from = new Vector2(), to = new Vector2();

    public CryptLobby(GameConfig config) { this.config = config; }

    @Override public void create() {
        Gdx.graphics.setForegroundFPS(60);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        stage = new Stage(new FitViewport(1920, 1080));
        art = new UiArt();
        Image background = new Image(art.backdrop);
        background.setBounds(0, 0, 1920, 1080); stage.addActor(background);
        Image shade = new Image(art.pixel);
        shade.setColor(0.025f, 0.035f, 0.04f, 0.34f); shade.setBounds(0, 0, 1920, 1080);
        shade.setTouchable(Touchable.disabled); stage.addActor(shade);
        content = new Table(); content.setFillParent(true); stage.addActor(content);
        diagnostics = art.label("", art.small, UiArt.IVORY);
        diagnostics.setPosition(105, 22); diagnostics.setVisible(false); stage.addActor(diagnostics);
        host = config.advertisedHost();
        if (host == null) host = LanAddress.find().stream().findFirst().orElse(null);
        openServer();
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) { return key(key); }
        }, stage));
        home();
    }

    private void openServer() {
        try {
            Map<String, JoinServer.Asset> assets = new LinkedHashMap<>();
            assets.put("/", web("web/index.html", "text/html; charset=utf-8"));
            assets.put("/controller.css", web("web/controller.css", "text/css; charset=utf-8"));
            assets.put("/controller.js", web("web/controller.js", "application/javascript; charset=utf-8"));
            assets.put("/art/crypt.png", web("art/crypt.png", "image/png"));
            server = new JoinServer(config.port(), state, assets); server.open(); serverError = null;
            Gdx.app.log("Lobby", "Join URL: " + (host == null ? "No LAN address" : server.url(host)));
        } catch (Exception failure) {
            if (server != null) server.stop(); server = null;
            serverError = "Cannot open controller port " + config.port() + ". Close another host and retry.";
            Gdx.app.error("Lobby", "Could not start local controller server", failure);
        }
    }
    private JoinServer.Asset web(String file, String mime) { return new JoinServer.Asset(Gdx.files.internal(file).readBytes(), mime); }

    private void reset(boolean isLobby) {
        lobby = isLobby; content.clearChildren(); buttons.clear(); focus = 0;
        firstStatus = secondStatus = partyStatus = connectionLabel = startLabel = null;
        start = null; qrImage = null; lastRevision = -1;
        content.pad(90, 100, 64, 100);
    }

    private void home() {
        reset(false);
        Table menu = new Table(); menu.left();
        menu.add(art.label("LOCAL CO-OP  /  TWO PLAYERS", art.small, UiArt.GOLD)).left().padBottom(22).row();
        menu.add(art.label("COUCH CRYPT", art.title, UiArt.IVORY)).left().padBottom(18).row();
        menu.add(art.label("Two souls. One descent.", art.section, UiArt.MUTED)).left().padBottom(70).row();
        menu.add(textButton("GATHER YOUR PARTY", this::showLobby)).width(510).height(82).left().padBottom(16).row();
        menu.add(textButton("HOW TO JOIN", this::help)).width(510).height(72).left().padBottom(16).row();
        menu.add(textButton("NETWORK", this::network)).width(510).height(72).left().row();
        content.add(menu).expand().left().bottom().padBottom(100).row();
        content.add(art.label("D-PAD  Navigate     OK  Select     BACK  Return", art.small, UiArt.MUTED)).left();
        setFocus(0);
    }

    private void showLobby() {
        reset(true);
        Table header = new Table();
        Table titles = new Table(); titles.left();
        titles.add(art.label("THE GATHERING", art.small, UiArt.GOLD)).left().padBottom(10).row();
        titles.add(art.label("Gather your party", art.heading, UiArt.IVORY)).left();
        header.add(titles).expandX().left();
        partyStatus = art.label("0 / 2 CONNECTED", art.body, UiArt.GOLD);
        header.add(partyStatus).right();
        content.add(header).growX().padBottom(36).row();
        Table body = new Table();
        body.add(playerCard(0)).width(500).height(590).padRight(46);
        body.add(joinPanel()).width(628).height(590).padRight(46);
        body.add(playerCard(1)).width(500).height(590);
        content.add(body).expandY().padBottom(30).row();
        Table actions = new Table();
        actions.add(textButton("BACK", this::home)).width(230).height(72).left();
        actions.add().expandX();
        start = textButton("WAITING FOR PLAYERS", this::partyReady);
        startLabel = (Label) start.getChildren().first();
        actions.add(start).width(620).height(78);
        actions.add().expandX();
        actions.add(textButton("NETWORK", this::network)).width(230).height(72).right();
        content.add(actions).growX().padBottom(24).row();
        content.add(art.label("D-PAD  Navigate     OK  Select     BACK  Home     Select a player to manage their connection", art.small, UiArt.MUTED)).left();
        content.validate(); updateStatus(); setFocus(0);
    }

    private RemoteButton playerCard(int index) {
        RemoteButton card = button(() -> managePlayer(index));
        card.pad(24);
        card.add(art.label(index == 0 ? "PLAYER I" : "PLAYER II", art.section,
            index == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO)).padBottom(10).row();
        Image portrait = new Image(index == 0 ? art.knight : art.ranger);
        portrait.setScaling(Scaling.fit);
        card.add(portrait).size(380, 300).padBottom(10).row();
        Label status = art.label("AWAITING A SOUL", art.body, UiArt.GOLD);
        if (index == 0) firstStatus = status; else secondStatus = status;
        card.add(status).padBottom(12).row();
        card.add(art.label("Scan the code to take your place", art.small, UiArt.MUTED));
        return card;
    }

    private Table joinPanel() {
        Table join = new Table();
        join.add(art.label("SCAN TO JOIN", art.section, UiArt.IVORY)).padBottom(16).row();
        qrImage = new Image();
        refreshQr();
        join.add(qrImage).size(280).padBottom(16).row();
        join.add(art.label("Same Wi-Fi. No app required.", art.small, UiArt.IVORY)).padBottom(12).row();
        connectionLabel = art.label(connectionText(), art.small, UiArt.MUTED);
        connectionLabel.setAlignment(Align.center); connectionLabel.setWrap(true);
        join.add(connectionLabel).width(600).padBottom(10).row();
        join.add(art.label(server == null ? "OFFLINE" : "ROOM  " + server.room(), art.body, UiArt.GOLD));
        return join;
    }

    private String connectionText() {
        if (serverError != null) return serverError;
        if (host == null) return "No local network found. Connect the TV to Wi-Fi, then open Network.";
        return "Or open " + host + ":" + config.port() + " on your phone and enter the room code.";
    }

    private void refreshQr() {
        if (qr != null) { qr.dispose(); qr = null; }
        if (qrImage == null) return;
        if (server == null || host == null) { qrImage.setDrawable(art.panel); return; }
        try {
            var bits = new QRCodeWriter().encode(server.url(host), BarcodeFormat.QR_CODE, 360, 360,
                Map.of(EncodeHintType.MARGIN, 4));
            Pixmap pixmap = new Pixmap(360, 360, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                pixmap.drawPixel(x, y, bits.get(x, y) ? 0x111111ff : 0xffffffff);
            qr = new Texture(pixmap); pixmap.dispose();
            qr.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            qrImage.setDrawable(new TextureRegionDrawable(qr));
        } catch (Exception failure) { Gdx.app.error("QR", "Cannot encode local join URL", failure); }
    }

    private void managePlayer(int index) {
        var snapshot = state.snapshot(System.currentTimeMillis());
        var player = index == 0 ? snapshot.first() : snapshot.second();
        if (!player.reserved()) {
            modal("An empty place", "Player " + (index + 1) + " joins by scanning the QR code on their phone.",
                new String[]{"BACK TO LOBBY"}, new Runnable[]{this::closeModal}); return;
        }
        modal("Player " + (index + 1), "Remove this controller to free its place for another phone?",
            new String[]{"KEEP PLAYER", "REMOVE PLAYER"}, new Runnable[]{this::closeModal, () -> { server.remove(index); closeModal(); updateStatus(); }});
    }

    private void partyReady() {
        if (state.snapshot(System.currentTimeMillis()).connected() != 2) return;
        modal("Your party is assembled", "Both phones are connected. The adventure begins in the next build.",
            new String[]{"BACK TO LOBBY"}, new Runnable[]{this::closeModal});
    }

    private void help() {
        modal("Answer the call", "1. Connect both phones to the same Wi-Fi as the TV.\n\n2. Open the lobby and scan its QR code with each phone.\n\n3. Use the TV remote to manage your party.\n\nPhones stay connected while the host uses the menus.",
            new String[]{"BACK"}, new Runnable[]{this::closeModal});
    }

    private void network() {
        List<String> addresses = new ArrayList<>(LanAddress.find());
        if (host != null && !addresses.contains(host)) addresses.add(0, host);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (String address : addresses) {
            labels.add((address.equals(host) ? "CURRENT: " : "USE: ") + address);
            actions.add(() -> { host = address; closeModal(); if (lobby) { refreshQr(); connectionLabel.setText(connectionText()); } });
        }
        labels.add("RETRY CONNECTION"); actions.add(() -> {
            closeModal();
            if (server == null) openServer();
            if (host == null) host = LanAddress.find().stream().findFirst().orElse(null);
            if (lobby) showLobby(); else network();
        });
        labels.add("BACK"); actions.add(this::closeModal);
        modal("Local network", "Both phones must reach this address.\nRoom " + (server == null ? "offline" : server.room())
            + "  /  Port " + config.port() + "\n\nFor emulator phone access, use the supplied TV development launcher.",
            labels.toArray(String[]::new), actions.toArray(Runnable[]::new));
    }

    private void modal(String title, String message, String[] options, Runnable[] actions) {
        if (overlay != null) closeModal();
        savedButtons = new ArrayList<>(buttons); savedFocus = focus; buttons.clear();
        overlay = new Table(); overlay.setFillParent(true); overlay.setBackground(art.scrim);
        overlay.setTouchable(Touchable.enabled);
        Table window = new Table(); window.setBackground(art.panel); window.pad(48);
        window.add(art.label(title, art.heading, UiArt.IVORY)).left().padBottom(28).row();
        Label text = art.label(message, art.body, UiArt.MUTED); text.setWrap(true);
        window.add(text).width(1000).left().padBottom(32).row();
        for (int i = 0; i < options.length; i++)
            window.add(textButton(options[i], actions[i])).width(1000).height(68).padTop(12).row();
        overlay.add(window); stage.addActor(overlay); overlay.validate(); setFocus(0);
    }

    private void closeModal() {
        if (overlay == null) return;
        overlay.remove(); overlay = null;
        buttons.clear(); buttons.addAll(savedButtons); savedButtons = null;
        setFocus(savedFocus);
    }

    private RemoteButton button(Runnable command) { RemoteButton result = new RemoteButton(art, command); buttons.add(result); return result; }
    private RemoteButton textButton(String text, Runnable command) {
        RemoteButton result = button(command);
        result.add(art.label(text, art.body, UiArt.IVORY)); return result;
    }
    private void setFocus(int index) {
        if (buttons.isEmpty()) return;
        focus = Math.max(0, Math.min(index, buttons.size() - 1));
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).focused(i == focus);
    }

    private boolean key(int key) {
        if (key == Input.Keys.F3) { debug = !debug; diagnostics.setVisible(debug); return true; }
        if (key == Input.Keys.BACK || key == Input.Keys.ESCAPE) {
            if (overlay != null) closeModal(); else if (lobby) home(); else
                modal("Leave the crypt?", "Your connected controllers will be disconnected.", new String[]{"STAY", "EXIT"}, new Runnable[]{this::closeModal, () -> Gdx.app.exit()});
            return true;
        }
        if (buttons.isEmpty()) return false;
        if (key == Input.Keys.ENTER || key == Input.Keys.DPAD_CENTER || key == Input.Keys.SPACE
            || key == Input.Keys.BUTTON_A) { buttons.get(focus).activate(); return true; }
        int dx = key == Input.Keys.LEFT ? -1 : key == Input.Keys.RIGHT ? 1 : 0;
        int dy = key == Input.Keys.UP ? 1 : key == Input.Keys.DOWN ? -1 : 0;
        if (dx == 0 && dy == 0) return false;
        RemoteButton current = buttons.get(focus);
        current.localToStageCoordinates(from.set(current.getWidth() / 2, current.getHeight() / 2));
        float best = Float.MAX_VALUE; int target = focus;
        for (int i = 0; i < buttons.size(); i++) {
            RemoteButton candidate = buttons.get(i);
            if (i == focus || candidate.isDisabled()) continue;
            candidate.localToStageCoordinates(to.set(candidate.getWidth() / 2, candidate.getHeight() / 2));
            float forward = dx != 0 ? (to.x - from.x) * dx : (to.y - from.y) * dy;
            float cross = dx != 0 ? Math.abs(to.y - from.y) : Math.abs(to.x - from.x);
            float score = forward + cross * 3;
            if (forward > 1 && score < best) { best = score; target = i; }
        }
        setFocus(target); return true;
    }

    private void updateStatus() {
        if (!lobby) return;
        var snapshot = state.snapshot(System.currentTimeMillis());
        if (snapshot.revision() == lastRevision && snapshot.first().secondsLeft() == lastFirstSeconds
            && snapshot.second().secondsLeft() == lastSecondSeconds) return;
        lastRevision = snapshot.revision(); lastFirstSeconds = snapshot.first().secondsLeft(); lastSecondSeconds = snapshot.second().secondsLeft();
        playerStatus(firstStatus, snapshot.first()); playerStatus(secondStatus, snapshot.second());
        partyStatus.setText(snapshot.connected() + " / 2 CONNECTED");
        start.setDisabled(snapshot.connected() != 2 || server == null || host == null);
        startLabel.setText(start.isDisabled() ? "WAITING FOR PLAYERS" : "START GAME");
        startLabel.setColor(start.isDisabled() ? UiArt.MUTED : UiArt.IVORY);
    }
    private void playerStatus(Label label, LobbyState.Player player) {
        label.setText(player.connected() ? "CONNECTED" : player.reserved() ? "RECONNECTING  " + player.secondsLeft() + "s" : "AWAITING A SOUL");
        label.setColor(player.connected() ? UiArt.GREEN : UiArt.GOLD);
    }

    @Override public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        refreshClock += delta; profileClock += delta;
        if (refreshClock >= 0.5f) { refreshClock = 0; updateStatus(); }
        if (profileClock >= 5) {
            profileClock = 0;
            if (debug || config.profile()) {
                String stats = Gdx.graphics.getFramesPerSecond() + " FPS  /  Java " + Gdx.app.getJavaHeap() / 1_048_576
                    + " MiB  /  Art " + art.textureBytes / 1_048_576 + " MiB  /  Draws " + ((SpriteBatch) stage.getBatch()).renderCalls;
                diagnostics.setText(stats);
                if (config.profile()) Gdx.app.log("LobbyPerf", stats);
            }
        }
        ScreenUtils.clear(0.025f, 0.035f, 0.04f, 1);
        stage.getViewport().apply(); stage.act(delta); stage.draw();
    }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() { state.setHostActive(false); }
    @Override public void resume() {
        state.setHostActive(true);
        if (art != null && qrImage != null) refreshQr();
    }
    @Override public void dispose() {
        if (server != null) server.stop();
        if (qr != null) qr.dispose();
        stage.dispose(); art.dispose();
    }
}
