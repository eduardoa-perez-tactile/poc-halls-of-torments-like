package com.couchcrypt;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.ArenaHudState;
import com.couchcrypt.game.GameArt;
import com.couchcrypt.game.GameAudio;
import com.couchcrypt.game.ArenaRenderer;
import com.couchcrypt.lobby.JoinServer;
import com.couchcrypt.lobby.LanAddress;
import com.couchcrypt.lobby.LobbyState;
import com.couchcrypt.ui.RemoteButton;
import com.couchcrypt.ui.UiArt;
import com.couchcrypt.ui.OutcomeArt;
import com.couchcrypt.ui.BuildIcons;
import com.couchcrypt.ui.RunDetailsView;
import com.couchcrypt.ui.CharacterInfoView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CryptLobby extends ApplicationAdapter {
    private enum Screen { HOME, LOBBY, SESSION, INVITE, CHARACTER_INFO, RESULTS }
    private enum Modal { GENERAL, PAUSE, RETURN_CONFIRM, SETTINGS_NETWORK }
    private final GameConfig config;
    private final LobbyState state = new LobbyState();
    private Stage stage;
    private UiArt art;
    private Image background, shade;
    private TextureRegionDrawable homeBackground, partyBackground;
    private JoinServer server;
    private Table content, overlay;
    private final List<RemoteButton> buttons = new ArrayList<>();
    private List<RemoteButton> savedButtons;
    private int focus, savedFocus;
    private Screen screen = Screen.HOME;
    private Modal modalKind = Modal.GENERAL;
    private boolean debug;
    private String host, serverError;
    private Label firstStatus, secondStatus, partyStatus, startLabel, connectionLabel, diagnostics;
    private final ArenaWorld world = new ArenaWorld();
    private final ArenaHudState hudState = new ArenaHudState();
    private final boolean[] previouslyAlive = new boolean[2], lowHealthNotified = new boolean[2];
    private boolean finalWarning;
    private GameArt gameArt;
    private ArenaRenderer arena;
    private GameAudio audio;
    private OutcomeArt outcomeArt;
    private OutcomeArt.Titles outcomeTitles;
    private BuildIcons buildIcons;
    private boolean resultDetails;
    private RemoteButton resultDetailsLink;
    private int characterInfoSlot, characterInfoFocus;
    private boolean characterInfoInvitation;
    private final ArenaWorld.Hero[] selectedHeroes = {ArenaWorld.Hero.WARDEN, ArenaWorld.Hero.RANGER};
    private int notifiedWave = 1, notifiedEliteKills;
    private boolean bossAnnounced, enrageAnnounced, hudPreview;
    private final LobbyState.Controls[] controls = {new LobbyState.Controls(), new LobbyState.Controls()};
    private final long[] generations = new long[2];
    private final LobbyState.UpgradeRequest[] upgradeRequests = {new LobbyState.UpgradeRequest(), new LobbyState.UpgradeRequest()};
    private int notifiedLevel = 1;
    private boolean hostActive = true;
    private RemoteButton start, pauseResume;
    private Image qrImage;
    private Label qrStatus;
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
        gameArt = new GameArt();
        outcomeTitles = new OutcomeArt.Titles();
        buildIcons = new BuildIcons();
        arena = new ArenaRenderer(gameArt, art);
        audio = new GameAudio();
        if (residentArtBytes() > 32L * 1_048_576)
            throw new IllegalStateException("Combined art exceeds 32 MiB texture budget");
        Gdx.app.log("GameArt", "Combined resident textures: " + residentArtBytes() / 1_048_576f + " MiB");
        homeBackground = new TextureRegionDrawable(art.homeBackdrop);
        partyBackground = homeBackground;
        background = new Image(partyBackground);
        background.setBounds(0, 0, 1920, 1080); stage.addActor(background);
        background.setTouchable(Touchable.disabled);
        shade = new Image(art.pixel);
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
            assets.put("/art/crypt.png", new JoinServer.Asset(art.controllerBackdrop(), "image/png"));
            assets.put("/art/ui-panel-v2.png", new JoinServer.Asset(art.controllerPanel(), "image/png"));
            assets.put("/art/home-button.png", new JoinServer.Asset(art.controllerButton(false), "image/png"));
            assets.put("/art/home-button-focused.png", new JoinServer.Asset(art.controllerButton(true), "image/png"));
            assets.put("/art/home-logo.png", new JoinServer.Asset(art.controllerLogo(), "image/png"));
            server = new JoinServer(config.port(), state, assets); server.open(); serverError = null;
            Gdx.app.log("Lobby", "Join URL: " + (host == null ? "No LAN address" : server.url(host)));
        } catch (Exception failure) {
            if (server != null) server.stop(); server = null;
            serverError = "Cannot open controller port " + config.port() + ". Close another host and retry.";
            Gdx.app.error("Lobby", "Could not start local controller server", failure);
        }
    }
    private JoinServer.Asset web(String file, String mime) { return new JoinServer.Asset(Gdx.files.internal(file).readBytes(), mime); }

    private void reset(Screen next) {
        if (overlay != null) closeModal();
        screen = next; content.clearChildren(); content.setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null); buttons.clear(); focus = 0;
        resultDetailsLink = null;
        if (next != Screen.RESULTS) {
            resultDetails = false;
            background.setDrawable(homeBackground);
            if (outcomeArt != null) { outcomeArt.dispose(); outcomeArt = null; }
        }
        background.setDrawable(next == Screen.HOME ? homeBackground : partyBackground);
        boolean gameView = next == Screen.SESSION || next == Screen.RESULTS;
        background.setVisible(!gameView);
        shade.setVisible(next != Screen.HOME && !gameView);
        firstStatus = secondStatus = partyStatus = connectionLabel = startLabel = null;
        start = null; qrImage = null; qrStatus = null; lastRevision = -1;
        content.pad(90, 100, 64, 100);
    }

    private void home() {
        reset(Screen.HOME);
        state.setPhase(LobbyState.Phase.LOBBY);
        content.pad(30, 100, 30, 100);
        Table menu = new Table();
        Image wordmark = new Image(art.homeLogo);
        wordmark.setScaling(Scaling.fit);
        menu.add(wordmark).size(1360, 280).padBottom(8).row();
        menu.add(art.label("T W O  S O U L S .   O N E  D E S C E N T .", art.small, UiArt.GOLD)).padBottom(32).row();
        menu.add(homeButton("PLAY", this::showLobby)).width(680).height(106).padBottom(20).row();
        menu.add(homeButton("HOW TO JOIN", this::help)).width(680).height(106).padBottom(20).row();
        menu.add(homeButton("SETTINGS", this::settings)).width(680).height(106).padBottom(48).row();
        menu.add(homeButton("EXIT", this::confirmExit)).width(360).height(76).row();
        menu.add(art.label("1-2 PLAYERS  /  LOCAL CO-OP", art.small, UiArt.GOLD)).padTop(24);
        content.add(menu).expand();
        content.validate();
        setFocus(0);
    }

    private RemoteButton homeButton(String text, Runnable command) {
        boolean compact = "EXIT".equals(text);
        RemoteButton result = new RemoteButton(compact ? art.exitNormal : art.homeNormal,
            compact ? art.exitFocused : art.homeFocused, command);
        result.pad(6, 24, 6, 24);
        result.add(art.label(text, compact ? art.section : art.heading, UiArt.BUTTON_INK));
        buttons.add(result);
        return result;
    }

    private void settings() {
        modal("Settings", "Sound and local phone connection.",
            new String[]{audio.enabled() ? "SOUND: ON" : "SOUND: OFF", "NETWORK", "BACK"},
            new Runnable[]{() -> { audio.setEnabled(!audio.enabled()); settings(); }, () -> network(true), this::closeModal});
    }

    private void confirmExit() {
        modal("Leave the crypt?", "Your connected controllers will be disconnected.",
            new String[]{"STAY", "EXIT"}, new Runnable[]{this::closeModal, () -> Gdx.app.exit()});
    }

    private void showLobby() {
        showPartyScreen(false);
    }

    private void showPartyScreen(boolean invitation) {
        reset(invitation ? Screen.INVITE : Screen.LOBBY);
        state.setPhase(invitation ? LobbyState.Phase.INVITE : LobbyState.Phase.LOBBY);
        Table header = new Table();
        Table titles = new Table(); titles.left();
        titles.add(art.label(invitation ? "SESSION PAUSED" : "THE GATHERING", art.small, UiArt.GOLD)).left().padBottom(10).row();
        titles.add(art.label(invitation ? "Invite a companion" : "Gather your party", art.heading, UiArt.IVORY)).left();
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
        actions.add(textButton("BACK", this::backFromParty)).width(230).height(72).left();
        actions.add().expandX();
        if (invitation) {
            actions.add(textButton("BACK TO PAUSE", this::backToPause)).width(620).height(78);
        } else {
            start = textButton("CONNECT A PHONE TO START", this::startSession);
            startLabel = (Label) start.getChildren().first();
            actions.add(start).width(620).height(78);
        }
        actions.add().expandX();
        actions.add(textButton("NETWORK", this::network)).width(230).height(72).right();
        content.add(actions).growX().padBottom(24).row();
        content.add(art.label(invitation
            ? "D-PAD  Navigate     OK  Select     BACK  Pause menu     Select a player to manage their connection"
            : "D-PAD  Navigate     OK  Select     BACK  Home     Start solo or invite a companion", art.small, UiArt.MUTED)).left();
        content.validate(); updateStatus(); setFocus(0);
    }

    private RemoteButton playerCard(int index) {
        RemoteButton card = button(true, () -> managePlayer(index));
        card.pad(24);
        card.add(art.label(index == 0 ? "PLAYER I" : "PLAYER II", art.section,
            index == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO)).padBottom(10).row();
        Image portrait = new Image(selectedHeroes[index] == ArenaWorld.Hero.WARDEN ? art.knight : art.ranger);
        portrait.setScaling(Scaling.fit);
        card.add(portrait).size(380, 300).padBottom(10).row();
        Label status = art.label("AWAITING A SOUL", art.body, UiArt.GOLD);
        if (index == 0) firstStatus = status; else secondStatus = status;
        card.add(status).padBottom(12).row();
        card.add(art.label(selectedHeroes[index] == ArenaWorld.Hero.WARDEN ? "WARDEN  /  SWORD SWEEP" : "RANGER  /  BOW", art.small, UiArt.MUTED));
        return card;
    }

    private Table joinPanel() {
        Table join = new Table();
        join.add(art.label("SCAN TO JOIN", art.section, UiArt.IVORY)).padBottom(16).row();
        qrImage = new Image();
        qrStatus = art.label("", art.body, UiArt.IVORY);
        qrStatus.setAlignment(Align.center); qrStatus.setWrap(true);
        Table statusOverlay = new Table();
        statusOverlay.add(qrStatus).width(230);
        refreshQr();
        join.add(new Stack(qrImage, statusOverlay)).size(320).padBottom(16).row();
        join.add(art.label("SAME WI-FI", art.small, UiArt.MUTED)).padBottom(12).row();
        connectionLabel = art.label(connectionText(), art.small, UiArt.MUTED);
        connectionLabel.setAlignment(Align.center); connectionLabel.setWrap(true);
        connectionLabel.setVisible(!connectionText().isEmpty());
        join.add(connectionLabel).width(600);
        return join;
    }

    private String connectionText() {
        if (serverError != null) return serverError;
        if (host == null) return "No local network found. Connect the TV to Wi-Fi, then open Network.";
        return "";
    }

    private void refreshQr() {
        if (qr != null) { qr.dispose(); qr = null; }
        if (qrImage == null) return;
        qrStatus.setVisible(false);
        if (server == null || host == null) {
            qrImage.setDrawable(art.panel);
            qrStatus.setText(server == null ? "HOST OFFLINE\n\nOpen Network\nto retry" : "NO NETWORK\n\nConnect the TV\nto Wi-Fi");
            qrStatus.setVisible(true);
            return;
        }
        try {
            var bits = new QRCodeWriter().encode(server.url(host), BarcodeFormat.QR_CODE, 360, 360,
                Map.of(EncodeHintType.MARGIN, 4));
            Pixmap pixmap = new Pixmap(360, 360, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                pixmap.drawPixel(x, y, bits.get(x, y) ? 0x111111ff : 0xffffffff);
            qr = new Texture(pixmap); pixmap.dispose();
            qr.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            qrImage.setDrawable(new TextureRegionDrawable(qr));
        } catch (Exception failure) {
            qrImage.setDrawable(art.panel);
            qrStatus.setText("CODE UNAVAILABLE\n\nOpen Network\nto retry");
            qrStatus.setVisible(true);
            Gdx.app.error("QR", "Cannot encode local join URL", failure);
        }
    }

    private void managePlayer(int index) {
        var snapshot = state.snapshot(System.currentTimeMillis());
        var player = index == 0 ? snapshot.first() : snapshot.second();
        if (screen == Screen.LOBBY || !world.players[index].active || generations[index] != player.generation()) {
            boolean invitation = screen == Screen.INVITE;
            List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
            labels.add("WARDEN  /  SWORD SWEEP"); actions.add(() -> { selectedHeroes[index] = ArenaWorld.Hero.WARDEN; showPartyScreen(invitation); });
            labels.add("WARDEN INFO"); actions.add(() -> showCharacterInfo(index, ArenaWorld.Hero.WARDEN, invitation, 1));
            labels.add("RANGER  /  BOW"); actions.add(() -> { selectedHeroes[index] = ArenaWorld.Hero.RANGER; showPartyScreen(invitation); });
            labels.add("RANGER INFO"); actions.add(() -> showCharacterInfo(index, ArenaWorld.Hero.RANGER, invitation, 3));
            if (player.reserved()) {
                labels.add("REMOVE CONTROLLER"); actions.add(() -> { server.remove(index); showPartyScreen(invitation); });
            }
            labels.add("BACK"); actions.add(this::closeModal);
            modal("Choose a hero", "Attacks are automatic. View a hero's story, base stats and available skills before choosing.",
                labels.toArray(String[]::new), actions.toArray(Runnable[]::new));
            return;
        }
        if (!player.reserved()) {
            modal("An empty place", "Player " + (index + 1) + " joins by scanning the QR code on their phone.",
                new String[]{"BACK TO LOBBY"}, new Runnable[]{this::closeModal}); return;
        }
        modal("Player " + (index + 1), "Remove this controller to free its place for another phone?",
            new String[]{"HERO INFO", "KEEP PLAYER", "REMOVE PLAYER"}, new Runnable[]{
                () -> showCharacterInfo(index, world.players[index].hero, true, 0), this::closeModal,
                () -> { server.remove(index); closeModal(); updateStatus(); }});
    }

    private void showCharacterInfo(int slot, ArenaWorld.Hero hero, boolean invitation, int returnFocus) {
        characterInfoSlot = slot; characterInfoInvitation = invitation; characterInfoFocus = returnFocus;
        reset(Screen.CHARACTER_INFO);
        // Leave the existing LOBBY/INVITE phase intact: viewing lore never changes joining or a paused run.
        content.setBackground(art.scrim); content.pad(0);
        content.add(CharacterInfoView.create(art, hero, buildIcons.regions,
            this::backFromCharacterInfo, buttons::add)).grow();
        content.validate(); setFocus(0);
    }

    private void backFromCharacterInfo() {
        showPartyScreen(characterInfoInvitation);
        managePlayer(characterInfoSlot);
        setFocus(characterInfoFocus);
    }

    private void startSession() {
        if (state.snapshot(System.currentTimeMillis()).connected() < 1 || server == null || host == null) return;
        world.reset(); hudState.reset(); finalWarning = false; notifiedLevel = 1;
        notifiedWave = 1; notifiedEliteKills = 0; bossAnnounced = enrageAnnounced = hudPreview = false;
        for (int i = 0; i < 2; i++) { previouslyAlive[i] = false; lowHealthNotified[i] = false; }
        reconcilePlayers();
        audio.reset(world);
        hudState.notify("THE DESCENT BEGINS", "Survive the waves, then defeat the Hollow Sovereign.", ArenaHudState.Tone.INFO, 4f);
        arena.resetCameras();
        showSession();
        publishProgression();
    }

    private void reconcilePlayers() {
        var snapshot = state.snapshot(System.currentTimeMillis());
        for (int i = 0; i < 2; i++) {
            var slot = i == 0 ? snapshot.first() : snapshot.second();
            if (!slot.reserved()) world.deactivatePlayer(i);
            if (slot.connected()) {
                if (generations[i] != slot.generation()) world.deactivatePlayer(i);
                boolean joining = !world.players[i].active;
                if (joining) world.setHero(i, selectedHeroes[i]);
                world.activatePlayer(i);
                previouslyAlive[i] = world.players[i].alive;
                if (joining && world.elapsed > 0) {
                    hudState.notify(i == 0 ? "PLAYER I JOINED" : "PLAYER II JOINED", i == 0 ? "Player I has joined the descent." : "Player II has joined the descent.",
                        ArenaHudState.Tone.SUCCESS, 4f);
                    arena.resetCameras();
                }
                generations[i] = slot.generation();
            }
        }
    }

    private void showSession() {
        reset(Screen.SESSION);
        state.setPhase(LobbyState.Phase.SESSION);
    }

    private boolean canResume() {
        var snapshot = state.snapshot(System.currentTimeMillis());
        if (snapshot.connected() == 0) return false;
        for (int i = 0; i < 2; i++) {
            var slot = i == 0 ? snapshot.first() : snapshot.second();
            if (world.players[i].active && slot.reserved() && !slot.connected()) return false;
        }
        return true;
    }

    private void showPause() {
        audio.stop();
        world.clearInputs();
        state.setPhase(LobbyState.Phase.PAUSED);
        modal("Paused", "Resume, invite a companion, or restart using the TV remote.\nIf a phone disconnects, reconnect it or remove it under Invite Player 2.",
            new String[]{"RESUME", "INVITE PLAYER 2", "RESTART RUN", "RETURN TO LOBBY"},
            new Runnable[]{this::resumeSession, () -> showPartyScreen(true), this::confirmRestart, this::confirmReturnToLobby});
        modalKind = Modal.PAUSE;
        pauseResume = buttons.get(0);
        setTextButtonDisabled(pauseResume, !canResume());
        if (pauseResume.isDisabled()) setFocus(1);
    }

    private void resumeSession() {
        if (canResume()) {
            reconcilePlayers(); closeModal(); state.setPhase(LobbyState.Phase.SESSION);
        }
    }

    private void confirmRestart() {
        modal("Restart this descent?", "Start a new round with your connected party.",
            new String[]{"KEEP PLAYING", "RESTART RUN"}, new Runnable[]{this::showPause, this::startSession});
        modalKind = Modal.RETURN_CONFIRM;
    }

    /** Profiling-only sample data for reviewing both end screens without playing a full run. */
    private void previewResults(boolean victory) {
        world.reset();
        for (int i = 0; i < 2; i++) { world.setHero(i, selectedHeroes[i]); world.activatePlayer(i); }
        world.progression.addXp(96);
        for (int i = 0; i < 2; i++) {
            while (world.progression.offers[i].pending)
                world.progression.choose(i, world.progression.offers[i].id, 1);
            var player = world.players[i];
            player.kills = i == 0 ? 81 : 62; player.damageDealt = i == 0 ? 4820 : 3960;
            player.damageTaken = i == 0 ? 154 : 112; player.revives = i == 0 ? 1 : 2;
        }
        world.elapsed = 214; world.kills = 143; world.souls = 96;
        world.bossDefeated = victory;
        world.outcome = victory ? ArenaWorld.Outcome.VICTORY : ArenaWorld.Outcome.DEFEAT;
        showResults();
    }

    private void prepareResults() {
        boolean victory = world.outcome == ArenaWorld.Outcome.VICTORY;
        if (outcomeArt == null || outcomeArt.victory != victory) {
            if (outcomeArt != null) { outcomeArt.dispose(); outcomeArt = null; }
            outcomeArt = new OutcomeArt(victory, outcomeTitles);
            long bytes = residentArtBytes();
            if (bytes > 32L * 1_048_576) throw new IllegalStateException("Outcome art exceeds combined texture budget");
            Gdx.app.log("OutcomeArt", "Combined resident art: " + bytes / 1_048_576f + " MiB (excludes QR)");
        }
        state.setPhase(LobbyState.Phase.RESULTS);
        background.setDrawable(new TextureRegionDrawable(outcomeArt.backdrop));
        background.setVisible(true); shade.setVisible(false);
    }

    private void showResults() {
        reset(Screen.RESULTS); prepareResults(); resultDetails = false;
        content.pad(0);
        Image title = new Image(outcomeArt.title); title.setScaling(Scaling.fit);
        title.setTouchable(Touchable.disabled); title.setBounds(360, 790, 1200, 230); content.addActor(title);
        Label subtitle = art.label(outcomeArt.victory ? "THE HOLLOW SOVEREIGN HAS FALLEN" : "THE CRYPT CLAIMS ANOTHER SOUL", art.section, UiArt.IVORY);
        subtitle.setFontScale(.88f); subtitle.setAlignment(Align.center);
        subtitle.setBounds(240, 730, 1440, 60); content.addActor(subtitle);
        Label summary = art.label(formatTime(world.elapsed) + "   /   LEVEL " + world.progression.level + "   /   " + world.kills + " SLAIN", art.section, UiArt.IVORY);
        summary.setFontScale(.85f); summary.setAlignment(Align.center);
        summary.setBounds(400, 250, 1120, 40); content.addActor(summary);
        Table actions = new Table();
        actions.add(resultAction(outcomeArt.victory ? "PLAY AGAIN" : "TRY AGAIN", this::playAgain)).width(500).height(94).padRight(32);
        actions.add(resultAction("RETURN TO LOBBY", this::showLobby)).width(500).height(94);
        actions.setBounds(444, 126, 1032, 94); content.addActor(actions);
        Button.ButtonStyle normal = new Button.ButtonStyle(), focused = new Button.ButtonStyle();
        normal.up = new ResultUnderline(false); normal.down = new ResultUnderline(true);
        focused.up = focused.down = new ResultUnderline(true);
        resultDetailsLink = new RemoteButton(normal, focused, this::showRunDetails);
        resultDetailsLink.pad(0); resultDetailsLink.add(art.label("RUN DETAILS", art.section, UiArt.IVORY));
        resultDetailsLink.setBounds(800, 46, 320, 58);
        buttons.add(resultDetailsLink); content.addActor(resultDetailsLink);
        content.validate(); actions.validate(); setFocus(0);
    }

    private void playAgain() {
        if (state.snapshot(System.currentTimeMillis()).connected() > 0) startSession();
        else showLobby();
    }

    private RemoteButton resultAction(String text, Runnable command) {
        RemoteButton result = button(command);
        result.add(art.label(text, art.section, UiArt.BUTTON_INK));
        return result;
    }

    private final class ResultUnderline extends BaseDrawable {
        private final boolean selected;
        private ResultUnderline(boolean selected) { this.selected = selected; }
        @Override public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float x, float y, float width, float height) {
            Color saved = batch.getColor();
            float r = saved.r, g = saved.g, b = saved.b, a = saved.a;
            batch.setColor(UiArt.GOLD.r, UiArt.GOLD.g, UiArt.GOLD.b, selected ? 1f : .45f);
            art.pixel.draw(batch, x + 28, y + 2, width - 56, selected ? 4 : 1);
            batch.setColor(r, g, b, a);
        }
    }

    private void showRunDetails() {
        reset(Screen.RESULTS); prepareResults(); resultDetails = true;
        if (buildIcons == null) buildIcons = new BuildIcons();
        if (residentArtBytes() + (qr == null ? 0 : (long) qr.getWidth() * qr.getHeight() * 4) > 32L * 1_048_576)
            throw new IllegalStateException("Run details exceeds combined texture budget");
        content.setBackground(art.scrim); content.pad(0);
        content.add(RunDetailsView.create(art, world, buildIcons.regions, this::backToResults, buttons::add)).grow();
        content.validate(); setFocus(0);
    }

    private void backToResults() { showResults(); setFocus(2); }

    private long residentArtBytes() {
        return art.textureBytes + gameArt.textureBytes + (outcomeTitles == null ? 0 : outcomeTitles.textureBytes)
            + (outcomeArt == null ? 0 : outcomeArt.textureBytes) + (buildIcons == null ? 0 : buildIcons.textureBytes);
    }

    private static String formatTime(float seconds) {
        int time = (int) seconds;
        return time / 60 + ":" + (time % 60 < 10 ? "0" : "") + time % 60;
    }

    private void confirmReturnToLobby() {
        modal("Return to the lobby?", "Leave this session and keep your connected phones in the party?",
            new String[]{"KEEP SESSION", "RETURN TO LOBBY"}, new Runnable[]{this::showPause, this::showLobby});
        modalKind = Modal.RETURN_CONFIRM;
    }

    private void backToPause() { showSession(); showPause(); }
    private void backFromParty() { if (screen == Screen.INVITE) backToPause(); else home(); }

    private boolean partyScreen() { return screen == Screen.LOBBY || screen == Screen.INVITE; }

    private void help() {
        modal("Answer the call", "1. Connect one or two phones to the same Wi-Fi as the TV.\n\n2. Open the lobby and scan its QR code with your phone.\n\n3. Start solo, or connect a second phone for co-op.\n\nChoose a hero on the TV. Move with the phone joystick and tap Dodge. Attacks are automatic. Shared XP grants personal phone upgrades. Stay near a fallen ally to revive them. Survive three waves, then defeat the Sovereign.\n\nA companion can also join later: press Back to pause, then choose Invite Player 2.",
            new String[]{"BACK"}, new Runnable[]{this::closeModal});
    }

    private void network() { network(false); }

    private void network(boolean fromSettings) {
        Runnable finish = fromSettings ? this::settings : this::closeModal;
        List<String> addresses = new ArrayList<>(LanAddress.find());
        if (host != null && !addresses.contains(host)) addresses.add(0, host);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (String address : addresses) {
            labels.add((address.equals(host) ? "CURRENT: " : "USE: ") + address);
            actions.add(() -> {
                host = address; finish.run();
                if (partyScreen()) {
                    refreshQr(); connectionLabel.setText(connectionText());
                    connectionLabel.setVisible(!connectionText().isEmpty());
                }
            });
        }
        labels.add("RETRY CONNECTION"); actions.add(() -> {
            closeModal();
            if (server == null) openServer();
            if (host == null) host = LanAddress.find().stream().findFirst().orElse(null);
            if (partyScreen()) showPartyScreen(screen == Screen.INVITE); else network(fromSettings);
        });
        labels.add("BACK"); actions.add(finish);
        modal("Local network", "Your phone must reach this address.\nPort " + config.port()
            + "\n\nFor emulator phone access, use the supplied TV development launcher.",
            labels.toArray(String[]::new), actions.toArray(Runnable[]::new));
        if (fromSettings) modalKind = Modal.SETTINGS_NETWORK;
    }

    private void modal(String title, String message, String[] options, Runnable[] actions) {
        if (overlay != null) closeModal();
        modalKind = Modal.GENERAL;
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
        pauseResume = null; modalKind = Modal.GENERAL;
        buttons.clear(); buttons.addAll(savedButtons); savedButtons = null;
        setFocus(savedFocus);
    }

    private RemoteButton button(Runnable command) { return button(false, command); }
    private RemoteButton button(boolean panel, Runnable command) {
        RemoteButton result = new RemoteButton(art, command, panel); buttons.add(result); return result;
    }
    private RemoteButton textButton(String text, Runnable command) {
        RemoteButton result = button(command);
        Label caption = art.label(text, art.section, UiArt.BUTTON_INK);
        caption.setFontScale(0.82f);
        result.add(caption); return result;
    }
    private void setTextButtonDisabled(RemoteButton button, boolean disabled) {
        button.setDisabled(disabled);
        ((Label) button.getChildren().first()).setColor(disabled ? UiArt.BUTTON_DISABLED_INK : UiArt.BUTTON_INK);
    }
    private void setFocus(int index) {
        if (buttons.isEmpty()) return;
        focus = Math.max(0, Math.min(index, buttons.size() - 1));
        for (int i = 0; i < buttons.size(); i++) {
            RemoteButton button = buttons.get(i); button.focused(i == focus);
            if (button == resultDetailsLink) ((Label) button.getChildren().first()).setColor(i == focus ? UiArt.GOLD : UiArt.IVORY);
        }
    }

    private boolean key(int key) {
        if (key == Input.Keys.F5 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.progression.addXp(world.progression.xpToNext);
            announceLevelUp(); publishProgression(); return true;
        }
        if (config.profile() && (screen == Screen.SESSION || screen == Screen.RESULTS)
            && (key == Input.Keys.F7 || key == Input.Keys.F8)) {
            previewResults(key == Input.Keys.F7); return true;
        }
        if (key == Input.Keys.F6 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.debugStartBoss(); syncEncounterHud(); return true;
        }
        if (key == Input.Keys.F4 && config.profile() && screen == Screen.SESSION && !world.bossSpawned) {
            hudPreview = !hudPreview; syncEncounterHud(); return true;
        }
        if (key == Input.Keys.F3) { debug = !debug; diagnostics.setVisible(debug); return true; }
        if (key == Input.Keys.BACK || key == Input.Keys.ESCAPE) {
            if (overlay != null) {
                if (screen == Screen.RESULTS) { showLobby(); return true; }
                if (modalKind == Modal.PAUSE) resumeSession();
                else if (modalKind == Modal.RETURN_CONFIRM) showPause();
                else if (modalKind == Modal.SETTINGS_NETWORK) settings();
                else closeModal();
            } else if (screen == Screen.SESSION) showPause();
            else if (screen == Screen.CHARACTER_INFO) backFromCharacterInfo();
            else if (screen == Screen.RESULTS) { if (resultDetails) backToResults(); else showLobby(); }
            else if (partyScreen()) backFromParty(); else confirmExit();
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
        if (screen == Screen.HOME) return;
        var snapshot = state.snapshot(System.currentTimeMillis());
        if (snapshot.revision() == lastRevision && snapshot.first().secondsLeft() == lastFirstSeconds
            && snapshot.second().secondsLeft() == lastSecondSeconds) return;
        lastRevision = snapshot.revision(); lastFirstSeconds = snapshot.first().secondsLeft(); lastSecondSeconds = snapshot.second().secondsLeft();
        if (partyScreen()) {
            playerStatus(firstStatus, snapshot.first()); playerStatus(secondStatus, snapshot.second());
            partyStatus.setText(snapshot.connected() + " / 2 CONNECTED");
            if (start != null) {
                setTextButtonDisabled(start, snapshot.connected() == 0 || server == null || host == null);
                startLabel.setText(start.isDisabled() ? "CONNECT A PHONE TO START" : snapshot.connected() == 1 ? "START SOLO" : "START CO-OP");
            }
        } else if (screen == Screen.SESSION && pauseResume != null) {
            setTextButtonDisabled(pauseResume, !canResume());
            if (pauseResume.isDisabled() && buttons.get(focus) == pauseResume) setFocus(1);
        }
    }

    private void playerStatus(Label label, LobbyState.Player player) {
        label.setText(player.connected() ? "CONNECTED" : player.reserved() ? "RECONNECTING  " + player.secondsLeft() + "s"
            : player.index() == 1 ? "OPTIONAL COMPANION" : "AWAITING A SOUL");
        label.setColor(player.connected() ? UiArt.GREEN : UiArt.GOLD);
    }

    private void announceLevelUp() {
        if (world.progression.level <= notifiedLevel) return;
        notifiedLevel = world.progression.level;
        hudState.notify("LEVEL " + notifiedLevel + "  /  CHOOSE ON YOUR PHONE", "Combat continues. Choose within 30 seconds.",
            ArenaHudState.Tone.SUCCESS, 4f);
    }

    private void publishProgression() {
        var progression = world.progression;
        boolean inRun = (screen == Screen.SESSION || screen == Screen.INVITE
            || screen == Screen.CHARACTER_INFO && characterInfoInvitation) && world.outcome == ArenaWorld.Outcome.RUNNING;
        for (int i = 0; i < 2; i++) {
            var offer = progression.offers[i];
            boolean pending = inRun && world.players[i].active && offer.pending;
            state.publishProgression(i, progression.level, progression.xp, progression.xpToNext,
                pending ? offer.id : 0, pending ? offer.level : 0, pending ? offer.secondsLeft : 0,
                pending ? offer.options : null);
        }
    }

    private void syncEncounterHud() {
        if (world.bossSpawned && !world.bossDefeated && world.boss != null && world.boss.active)
            hudState.setBoss("THE HOLLOW SOVEREIGN", world.bossPhase == 2 ? "ENRAGED" : "PHASE I", world.boss.health, world.boss.maxHealth);
        else if (hudPreview) hudState.setBoss("THE HOLLOW SOVEREIGN", "HUD PREVIEW", 720, 1000);
        else hudState.clearBoss();
    }

    private void updateCombatNotices() {
        syncEncounterHud();
        if (world.wave > notifiedWave && !world.bossSpawned) {
            notifiedWave = world.wave;
            hudState.notify(world.wave == 2 ? "WAVE II  /  THE HUNT" : "WAVE III  /  THE ONSLAUGHT", "", ArenaHudState.Tone.WARNING, 3f);
        }
        if (world.elitesKilled > notifiedEliteKills) {
            notifiedEliteKills = world.elitesKilled;
            hudState.notify("ELITE SLAIN  /  CLAIM ITS SOULS", "", ArenaHudState.Tone.SUCCESS, 3f);
        }
        if (world.bossSpawned && !bossAnnounced) {
            bossAnnounced = true; hudPreview = false;
            hudState.notify("THE HOLLOW SOVEREIGN AWAKENS", "", ArenaHudState.Tone.WARNING, 4f);
        }
        if (world.bossPhase == 2 && !enrageAnnounced) {
            enrageAnnounced = true;
            hudState.notify("THE SOVEREIGN IS ENRAGED", "", ArenaHudState.Tone.WARNING, 3f);
        }
        for (int i = 0; i < 2; i++) {
            var player = world.players[i];
            if (!player.active) continue;
            if (previouslyAlive[i] && !player.alive)
                hudState.notify(i == 0 ? "PLAYER I HAS FALLEN" : "PLAYER II HAS FALLEN",
                    "Stand near your companion to revive them.", ArenaHudState.Tone.WARNING, 4f);
            else if (!previouslyAlive[i] && player.alive)
                hudState.notify(i == 0 ? "PLAYER I REVIVED" : "PLAYER II REVIVED", i == 0 ? "Player I returns to the fight." : "Player II returns to the fight.",
                    ArenaHudState.Tone.SUCCESS, 3.5f);
            previouslyAlive[i] = player.alive;
            if (player.alive && player.health <= player.maxHealth * .25f && !lowHealthNotified[i]) {
                lowHealthNotified[i] = true;
                hudState.notify(i == 0 ? "PLAYER I LOW HEALTH" : "PLAYER II LOW HEALTH",
                    "Dodge to safety. Seek a healing ember.", ArenaHudState.Tone.WARNING, 3.5f);
            }
            if (player.health > player.maxHealth * .4f) lowHealthNotified[i] = false;
        }
        if (!finalWarning && !world.bossSpawned && world.timeRemaining() <= 30 && world.outcome == ArenaWorld.Outcome.RUNNING) {
            finalWarning = true;
            hudState.notify("THE SOVEREIGN ARRIVES IN 30s", "", ArenaHudState.Tone.WARNING, 4f);
        }
    }

    @Override public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        refreshClock += delta; profileClock += delta;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 2; i++) state.readControls(i, controls[i], now);
        if (screen == Screen.SESSION && overlay == null && hostActive) {
            boolean disconnected = false;
            for (int i = 0; i < 2; i++) disconnected |= world.players[i].active && !controls[i].connected;
            if (disconnected) showPause();
            else {
                for (int i = 0; i < 2; i++) {
                    var input = controls[i];
                    float x = .7071068f * input.x + 1.4142136f * input.y;
                    float y = -.7071068f * input.x + 1.4142136f * input.y;
                    float magnitude = (float) Math.sqrt(input.x * input.x + input.y * input.y);
                    float length = (float) Math.sqrt(x * x + y * y);
                    float scale = length > 0 ? magnitude / length : 0;
                    world.setInput(i, x * scale, y * scale, input.dodge);
                }
                for (int i = 0; i < 2; i++) {
                    var request = upgradeRequests[i];
                    if (state.pollUpgrade(i, request) && request.generation == controls[i].generation
                        && world.progression.choose(i, request.offerId, request.option)) {
                        hudState.notify(i == 0 ? "PLAYER I UPGRADED" : "PLAYER II UPGRADED", "", ArenaHudState.Tone.SUCCESS, 2f);
                    }
                }
                world.update(delta);
                audio.update(world, delta);
                announceLevelUp();
                hudState.update(delta);
                updateCombatNotices();
                if (world.outcome != ArenaWorld.Outcome.RUNNING) showResults();
            }
        }
        if (refreshClock >= 0.2f) {
            refreshClock = 0; updateStatus(); publishProgression();
            for (int i = 0; i < 2; i++) {
                var player = world.players[i];
                state.setPlayerFeedback(i, player.health, player.maxHealth, player.dodgeCooldown, player.alive);
            }
        }
        if (profileClock >= 5) {
            profileClock = 0;
            if (debug || config.profile()) {
                String stats = Gdx.graphics.getFramesPerSecond() + " FPS  /  Java " + Gdx.app.getJavaHeap() / 1_048_576
                    + " MiB  /  Art " + residentArtBytes() / 1_048_576 + " MiB  /  Draws " + (arena.drawCalls + ((SpriteBatch) stage.getBatch()).renderCalls);
                diagnostics.setText(stats);
                if (config.profile()) Gdx.app.log("LobbyPerf", stats);
            }
        }
        ScreenUtils.clear(0.025f, 0.035f, 0.04f, 1);
        if (screen == Screen.SESSION) arena.render(world, hudState, delta, true);
        stage.getViewport().apply(); stage.act(delta); stage.draw();
    }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {
        hostActive = false; state.setHostActive(false);
        if (audio != null) audio.stop();
        if (screen == Screen.SESSION && overlay == null) showPause();
    }
    @Override public void resume() {
        hostActive = true; state.setHostActive(true);
        if (art != null && qrImage != null) refreshQr();
    }
    @Override public void dispose() {
        if (server != null) server.stop();
        if (qr != null) qr.dispose();
        stage.dispose(); arena.dispose(); audio.dispose();
        if (outcomeArt != null) outcomeArt.dispose();
        if (buildIcons != null) buildIcons.dispose();
        if (outcomeTitles != null) outcomeTitles.dispose();
        gameArt.dispose(); art.dispose();
    }
}
