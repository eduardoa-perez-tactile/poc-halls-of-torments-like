package com.couchcrypt;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.ArenaTimeline;
import com.couchcrypt.game.ArenaHudState;
import com.couchcrypt.game.GameArt;
import com.couchcrypt.game.HudArt;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.GameAudio;
import com.couchcrypt.game.ArenaRenderer;
import com.couchcrypt.game.ActorAnimation;
import com.couchcrypt.lobby.JoinServer;
import com.couchcrypt.lobby.LanAddress;
import com.couchcrypt.lobby.LobbyState;
import com.couchcrypt.ui.RemoteButton;
import com.couchcrypt.ui.UiArt;
import com.couchcrypt.ui.OutcomeArt;
import com.couchcrypt.ui.MenuSceneArt;
import com.couchcrypt.ui.BuildIcons;
import com.couchcrypt.ui.ItemArt;
import com.couchcrypt.ui.RunDetailsView;
import com.couchcrypt.ui.ResultsSummaryView;
import com.couchcrypt.ui.CharacterInfoView;
import com.couchcrypt.ui.HeroSelectionView;
import com.couchcrypt.ui.HeroPortraitDrawable;
import com.couchcrypt.ui.LevelUpView;
import com.couchcrypt.game.FrameMetrics;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
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
    private Drawable homeBackground, partyBackground;
    private JoinServer server;
    private Table content, overlay;
    private Table fontParityOverlay;
    private final List<RemoteButton> buttons = new ArrayList<>();
    private List<RemoteButton> savedButtons;
    private int focus, savedFocus;
    private Screen screen = Screen.HOME;
    private Modal modalKind = Modal.GENERAL;
    private boolean debug;
    private String host, serverError;
    private Label firstStatus, secondStatus, partyStatus, startLabel, connectionLabel, diagnostics;
    private final ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
    private final ArenaHudState hudState = new ArenaHudState();
    private final boolean[] previouslyAlive = new boolean[2], lowHealthNotified = new boolean[2];
    private boolean finalWarning;
    private GameArt gameArt;
    private HudArt hudArt;
    private ArenaRenderer arena;
    private GameAudio audio;
    private OutcomeArt.Titles outcomeTitles;
    private BuildIcons buildIcons;
    private ItemArt itemArt;
    private boolean resultDetails;
    private boolean equipmentDetails;
    private int resultPage;
    private ResultsSummaryView resultSummary;
    private int characterInfoSlot, characterInfoFocus;
    private boolean characterInfoInvitation;
    private boolean characterInfoFromChooser;
    private ArenaWorld.Hero characterInfoPreview;
    private HeroSelectionView heroChooser;
    private final ArenaWorld.Hero[] selectedHeroes = {ArenaWorld.Hero.WARDEN, ArenaWorld.Hero.RANGER};
    private int notifiedWave = 1, notifiedEliteKills;
    private boolean bossAnnounced, sovereignAnnounced, enrageAnnounced, hudPreview;
    private final LobbyState.Controls[] controls = {new LobbyState.Controls(), new LobbyState.Controls()};
    private final long[] generations = new long[2];
    private final LobbyState.UpgradeRequest[] upgradeRequests = {new LobbyState.UpgradeRequest(), new LobbyState.UpgradeRequest()};
    private final LobbyState.EquipmentRequest[] equipmentRequests = {new LobbyState.EquipmentRequest(), new LobbyState.EquipmentRequest()};
    private int notifiedLevel = 1;
    private int notifiedReliquaries;
    private boolean hostActive = true;
    private RemoteButton start, pauseResume;
    private Label modalMessage, pauseMessage;
    private LevelUpView levelUpView;
    private boolean lootCeremony;
    private int pauseTopology;
    private Image qrImage;
    private Label qrStatus;
    private Texture qr;
    private Pixmap qrPixels;
    private FrameMetrics frameMetrics;
    private float refreshClock, profileClock;
    private long lastRevision = -1;
    private int lastFirstSeconds = -1, lastSecondSeconds = -1;
    private final Vector2 from = new Vector2(), to = new Vector2();

    public CryptLobby(GameConfig config) { this.config = config; }

    @Override public void create() {
        long startupBegin = config.profile() ? System.nanoTime() : 0;
        if (config.profile()) {
            frameMetrics = new FrameMetrics();
            world.setDiagnosticsEnabled(true);
        }
        Gdx.graphics.setForegroundFPS(60);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        stage = new Stage(new FitViewport(1920, 1080));
        long startupStage = config.profile() ? System.nanoTime() : 0;
        art = new UiArt();
        startupStage = profileStartup("uiArt", startupStage);
        gameArt = new GameArt();
        startupStage = profileStartup("gameArt", startupStage);
        outcomeTitles = new OutcomeArt.Titles();
        buildIcons = new BuildIcons();
        itemArt = new ItemArt();
        hudArt = new HudArt();
        startupStage = profileStartup("sharedTitlesIconsHud", startupStage);
        arena = new ArenaRenderer(gameArt, art, buildIcons, hudArt, frameMetrics);
        audio = new GameAudio();
        startupStage = profileStartup("rendererAudio", startupStage);
        if (residentArtBytes() + 360L * 360 * 4 > 32L * 1_048_576)
            throw new IllegalStateException("Combined art and join QR exceed 32 MiB texture budget");
        Gdx.app.log("GameArt", "Combined resident textures: " + residentArtBytes() / 1_048_576f + " MiB");
        homeBackground = art.menuScene;
        partyBackground = homeBackground;
        background = new Image(partyBackground);
        background.setBounds(0, 0, 1920, 1080); stage.addActor(background);
        background.setTouchable(Touchable.disabled);
        shade = new Image(art.pixel);
        shade.setColor(0.025f, 0.035f, 0.04f, 0.34f); shade.setBounds(0, 0, 1920, 1080);
        shade.setTouchable(Touchable.disabled); stage.addActor(shade);
        content = new Table(); content.setFillParent(true); stage.addActor(content);
        levelUpView = new LevelUpView(art); levelUpView.setVisible(false); stage.addActor(levelUpView);
        diagnostics = art.label("", art.small, UiArt.IVORY);
        diagnostics.setPosition(105, 22); diagnostics.setVisible(false); stage.addActor(diagnostics);
        host = config.advertisedHost();
        if (host == null) host = LanAddress.find().stream().findFirst().orElse(null);
        startupStage = profileStartup("initialUi", startupStage);
        openServer();
        startupStage = profileStartup("phoneAssetsServer", startupStage);
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) { return key(key); }
        }, stage));
        home();
        profileStartup("homeLayout", startupStage);
        if (config.profile()) {
            profileStartup("createTotal", startupBegin);
            Gdx.app.log("ProfileMemory", "uiTextureBytes=" + art.textureBytes
                + " uiRetainedPixmapBytes=" + art.retainedPixmapBytes
                + " gameplayTextureBytes=" + gameArt.textureBytes
                + " gameplayRetainedPixmapBytes=" + gameArt.retainedPixmapBytes
                + " hudTextureBytes=" + hudArt.textureBytes
                + " titlesTextureBytes=" + outcomeTitles.textureBytes
                + " skillsTextureBytes=" + buildIcons.textureBytes
                + " itemsTextureBytes=" + itemArt.textureBytes
                + " textureLimitBytes=" + 32L * 1_048_576
                + " floorCachedQuads=" + arena.cachedFloorQuads
                + " floorUsedVertexBytes=" + arena.cachedFloorQuads * 80L
                + " floorUsedIndexBytes=" + arena.cachedFloorQuads * 12L
                + " floorCapacityVertexBytes=" + com.couchcrypt.game.EngravedFloorArt.MAX_FLOOR_QUADS * 80L
                + " floorCapacityIndexBytes=" + com.couchcrypt.game.EngravedFloorArt.MAX_FLOOR_QUADS * 12L
                + " medallionVertexBytes=" + arena.medallionQuads() * 80L);
        }
    }

    /** Startup wall durations are separate from settled frame measurements. */
    private long profileStartup(String stageName, long begin) {
        if (!config.profile()) return 0;
        long end = System.nanoTime();
        Gdx.app.log("ProfileStartup", "stage=" + stageName + " wallNs=" + Math.max(0, end - begin));
        return System.nanoTime();
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
            assets.put("/art/skill-icons.png", new JoinServer.Asset(buildIcons.controllerIcons(), "image/png"));
            assets.put("/art/item-icons.png", web("art/item-icons.png", "image/png"));
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
        resultSummary = null;
        if (next != Screen.RESULTS) {
            resultDetails = false;
            art.menuScene.setMood(MenuSceneArt.Mood.NEUTRAL);
            background.setDrawable(homeBackground);
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
        Image portrait = new Image(new HeroPortraitDrawable(
            selectedHeroes[index] == ArenaWorld.Hero.WARDEN ? art.wardenFace : art.rangerFace, 1));
        portrait.setScaling(Scaling.fit);
        card.add(portrait).size(360).padBottom(10).row();
        Label status = art.label("AWAITING A SOUL", art.body, UiArt.GOLD);
        if (index == 0) firstStatus = status; else secondStatus = status;
        card.add(status).padBottom(12).row();
        card.add(art.label(selectedHeroes[index] == ArenaWorld.Hero.WARDEN ? "WARDEN  /  SWORD SWEEP" : "RANGER  /  ARCANE CROSSBOW", art.small, UiArt.MUTED));
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
        disposeQr();
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
            qrPixels = new Pixmap(360, 360, Pixmap.Format.RGBA8888);
            for (int y = 0; y < 360; y++) for (int x = 0; x < 360; x++)
                qrPixels.drawPixel(x, y, bits.get(x, y) ? 0x111111ff : 0xffffffff);
            qr = new Texture(new PixmapTextureData(qrPixels, null, false, false, true));
            qr.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            qrImage.setDrawable(new TextureRegionDrawable(qr));
        } catch (Exception failure) {
            disposeQr();
            qrImage.setDrawable(art.panel);
            qrStatus.setText("CODE UNAVAILABLE\n\nOpen Network\nto retry");
            qrStatus.setVisible(true);
            Gdx.app.error("QR", "Cannot encode local join URL", failure);
        }
    }

    private void disposeQr() {
        if (qr != null) { qr.dispose(); qr = null; }
        if (qrPixels != null) { qrPixels.dispose(); qrPixels = null; }
    }

    private void managePlayer(int index) {
        var snapshot = state.snapshot(System.currentTimeMillis());
        var player = index == 0 ? snapshot.first() : snapshot.second();
        if (screen == Screen.LOBBY || !world.players[index].active || generations[index] != player.generation()) {
            boolean invitation = screen == Screen.INVITE;
            showHeroChooser(index, invitation, selectedHeroes[index], selectedHeroes[index].ordinal());
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

    private void showHeroChooser(int slot, boolean invitation, ArenaWorld.Hero preview, int returnFocus) {
        if (overlay != null) closeModal();
        modalKind = Modal.GENERAL;
        savedButtons = new ArrayList<>(buttons); savedFocus = focus; buttons.clear();
        overlay = new Table(); overlay.setFillParent(true); overlay.setBackground(art.scrim);
        overlay.setTouchable(Touchable.enabled);
        var snapshot = state.snapshot(System.currentTimeMillis());
        boolean reserved = (slot == 0 ? snapshot.first() : snapshot.second()).reserved();
        heroChooser = new HeroSelectionView(art, slot, preview,
            () -> {
                selectedHeroes[slot] = heroChooser.selectedHero();
                showPartyScreen(invitation); setFocus(slot);
            },
            () -> showCharacterInfo(slot, heroChooser.selectedHero(), invitation, focus),
            reserved ? () -> { server.remove(slot); showPartyScreen(invitation); setFocus(slot); } : null,
            buttons::add);
        overlay.add(heroChooser).width(1380);
        stage.addActor(overlay); overlay.validate(); setFocus(returnFocus);
    }

    private void showCharacterInfo(int slot, ArenaWorld.Hero hero, boolean invitation, int returnFocus) {
        characterInfoSlot = slot; characterInfoInvitation = invitation; characterInfoFocus = returnFocus;
        characterInfoFromChooser = heroChooser != null;
        characterInfoPreview = hero;
        reset(Screen.CHARACTER_INFO);
        // Leave the existing LOBBY/INVITE phase intact: viewing lore never changes joining or a paused run.
        content.setBackground(art.scrim); content.pad(0);
        content.add(CharacterInfoView.create(art, hero, buildIcons.regions,
            this::backFromCharacterInfo, buttons::add)).grow();
        content.validate(); setFocus(0);
    }

    private void backFromCharacterInfo() {
        showPartyScreen(characterInfoInvitation);
        setFocus(characterInfoSlot);
        if (characterInfoFromChooser)
            showHeroChooser(characterInfoSlot, characterInfoInvitation, characterInfoPreview, characterInfoFocus);
        else managePlayer(characterInfoSlot);
        setFocus(characterInfoFocus);
    }

    private void startSession() {
        if (state.snapshot(System.currentTimeMillis()).connected() < 1 || server == null || host == null) return;
        world.reset(); hudState.reset(); finalWarning = false; notifiedLevel = 1; notifiedReliquaries = 0;
        notifiedWave = 1; notifiedEliteKills = 0;
        bossAnnounced = sovereignAnnounced = enrageAnnounced = hudPreview = false;
        for (int i = 0; i < 2; i++) { previouslyAlive[i] = false; lowHealthNotified[i] = false; }
        reconcilePlayers();
        audio.reset(world);
        hudState.notify("THE DESCENT BEGINS", "Face the Sovereign at twelve minutes, then defeat the Grave King.", ArenaHudState.Tone.INFO, 4f);
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
        return canResume(snapshot);
    }

    private boolean canResume(LobbyState.Snapshot snapshot) {
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
        var snapshot = state.snapshot(System.currentTimeMillis());
        pauseTopology = pauseTopology(snapshot);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("RESUME"); actions.add(this::resumeSession);
        labels.add(snapshot.first().reserved() && snapshot.second().reserved() ? "MANAGE PARTY" : "INVITE PLAYER 2");
        actions.add(() -> showPartyScreen(true));
        for (int slot = 0; slot < 2; slot++) {
            var player = slot == 0 ? snapshot.first() : snapshot.second();
            if (!world.players[slot].active || !player.reserved() || player.connected()) continue;
            final int missing = slot;
            labels.add(slot == 0 ? "REMOVE PLAYER I" : "REMOVE PLAYER II");
            actions.add(() -> confirmRemoveDisconnected(missing));
        }
        labels.add("RESTART RUN"); actions.add(this::confirmRestart);
        labels.add("RETURN TO LOBBY"); actions.add(this::confirmReturnToLobby);
        modal("Paused", pauseDescription(snapshot), labels.toArray(String[]::new), actions.toArray(Runnable[]::new));
        modalKind = Modal.PAUSE;
        pauseMessage = modalMessage;
        pauseResume = buttons.get(0);
        setTextButtonDisabled(pauseResume, !canResume(snapshot));
        if (pauseResume.isDisabled()) setFocus(1);
    }

    private static int pauseTopology(LobbyState.Snapshot snapshot) {
        return (snapshot.first().connected() ? 1 : 0) | (snapshot.first().reserved() ? 2 : 0)
            | (snapshot.second().connected() ? 4 : 0) | (snapshot.second().reserved() ? 8 : 0);
    }

    private String pauseDescription(LobbyState.Snapshot snapshot) {
        StringBuilder message = new StringBuilder(200);
        for (int slot = 0; slot < 2; slot++) {
            if (!world.players[slot].active) continue;
            var player = slot == 0 ? snapshot.first() : snapshot.second();
            message.append(slot == 0 ? "PLAYER I" : "PLAYER II");
            if (player.connected()) message.append(" CONNECTED");
            else if (player.reserved()) {
                message.append(" DISCONNECTED - ");
                if (player.secondsLeft() > 0) message.append("reconnect within ").append(player.secondsLeft()).append('s');
                else message.append("reconnect or remove player");
            }
            else message.append(" PLACE RELEASED");
            message.append('\n');
        }
        if (snapshot.connected() == 0) message.append("Reconnect a phone to resume. Keep its controller page open.");
        else if (!canResume(snapshot)) message.append("Waiting for the missing phone. Reconnect it or remove its place below.");
        else message.append("Ready to resume. The run and upgrade timers stay frozen until you continue.");
        return message.toString();
    }

    private void confirmRemoveDisconnected(int slot) {
        modal("Release this place?", (slot == 0 ? "Player I" : "Player II")
            + " will leave this run. The connected companion can continue, and another phone can join through Invite Player 2.",
            new String[]{"KEEP PLAYER", "REMOVE PLAYER"}, new Runnable[]{this::showPause, () -> {
                var snapshot = state.snapshot(System.currentTimeMillis());
                var player = slot == 0 ? snapshot.first() : snapshot.second();
                // A phone may have reconnected while this confirmation was visible.
                if (!player.connected()) server.remove(slot);
                showPause();
            }});
        modalKind = Modal.RETURN_CONFIRM;
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
        boolean first = world.players[0].active, second = world.players[1].active;
        world.reset();
        for (int i = 0; i < 2; i++) if (i == 0 ? first : second) {
            world.setHero(i, selectedHeroes[i]); world.activatePlayer(i);
        }
        previewHud(18);
        for (int i = 0; i < 2; i++) {
            var player = world.players[i];
            if (!player.active) continue;
            player.kills = i == 0 ? 1081 : 962; player.damageDealt = i == 0 ? 148820 : 123960;
            player.damageTaken = i == 0 ? 854 : 712; player.revives = first && second ? i == 0 ? 1 : 2 : 0;
        }
        previewEquipment();
        world.debugStartBoss();
        if (world.boss != null) world.boss.health = victory ? 0 : world.boss.maxHealth * .12f;
        world.elapsed = 1234; world.kills = world.players[0].kills + world.players[1].kills;
        world.bossDefeated = victory;
        world.outcome = victory ? ArenaWorld.Outcome.VICTORY : ArenaWorld.Outcome.DEFEAT;
        showResults();
    }

    private void prepareResults() {
        boolean victory = world.outcome == ArenaWorld.Outcome.VICTORY;
        art.menuScene.setMood(victory ? MenuSceneArt.Mood.VICTORY : MenuSceneArt.Mood.DEFEAT);
        state.publishResult(victory);
        background.setDrawable(homeBackground);
        background.setVisible(true); shade.setVisible(false);
    }

    private void showResults() {
        reset(Screen.RESULTS); prepareResults(); resultDetails = false;
        content.pad(0);
        boolean victory = world.outcome == ArenaWorld.Outcome.VICTORY;
        resultSummary = ResultsSummaryView.create(art, world, outcomeTitles.get(victory),
            this::playAgain, this::showLobby, this::showRunDetails, buttons::add);
        content.add(resultSummary.root).grow();
        resultSummary.updateParty(state.snapshot(System.currentTimeMillis()).connected());
        content.validate(); setFocus(0);
    }

    private void playAgain() {
        if (state.snapshot(System.currentTimeMillis()).connected() > 0) startSession();
        else showLobby();
    }

    private void showRunDetails() { equipmentDetails = false; resultPage = 0; renderRunDetails(); }

    private void toggleRunDetails() { equipmentDetails = !equipmentDetails; resultPage = 0; renderRunDetails(); setFocus(1); }

    private void turnResultPage(int direction) {
        resultPage = Math.max(0, Math.min(resultPage + direction, RunDetailsView.pageCount(world, equipmentDetails) - 1));
        renderRunDetails();
        int target = direction < 0 ? 2 : 3;
        setFocus(buttons.get(target).isDisabled() ? (direction < 0 ? 3 : 2) : target);
    }

    private void renderRunDetails() {
        reset(Screen.RESULTS); prepareResults(); resultDetails = true;
        if (buildIcons == null) buildIcons = new BuildIcons();
        if (residentArtBytes() > 32L * 1_048_576)
            throw new IllegalStateException("Run details exceeds combined texture budget");
        content.setBackground(art.scrim); content.pad(0);
        content.add(RunDetailsView.create(art, world, buildIcons.regions, itemArt.regions,
            equipmentDetails, resultPage, () -> turnResultPage(-1), () -> turnResultPage(1),
            this::toggleRunDetails, this::backToResults, buttons::add)).grow();
        content.validate(); setFocus(0);
    }

    private void backToResults() { showResults(); setFocus(2); }

    private long residentArtBytes() {
        return art.textureBytes + gameArt.textureBytes + (hudArt == null ? 0 : hudArt.textureBytes) + (outcomeTitles == null ? 0 : outcomeTitles.textureBytes)
            + (buildIcons == null ? 0 : buildIcons.textureBytes) + (itemArt == null ? 0 : itemArt.textureBytes)
            + (qr == null ? 0 : (long) qr.getWidth() * qr.getHeight() * 4);
    }

    private long residentPixmapBytes() {
        return art.retainedPixmapBytes + gameArt.retainedPixmapBytes
            + (hudArt == null ? 0 : hudArt.retainedPixmapBytes)
            + (outcomeTitles == null ? 0 : outcomeTitles.retainedPixmapBytes)
            + (buildIcons == null ? 0 : buildIcons.retainedPixmapBytes)
            + (qrPixels == null ? 0 : (long) qrPixels.getWidth() * qrPixels.getHeight() * 4);
    }

    private String profileMode() {
        if (screen == Screen.SESSION) {
            if (overlay != null) return "PAUSED";
            if (world.levelUpPending()) return "LEVEL_UP";
            if (world.lootPending()) return "LOOT";
            boolean coop = world.players[0].active && world.players[1].active;
            if (world.bossSpawned) return coop ? "COOP_BOSS" : "SOLO_BOSS";
            if (world.elapsed >= world.durationSeconds() * .75f) return coop ? "COOP_LATE" : "SOLO_LATE";
            return coop ? "COOP" : "SOLO";
        }
        if (screen == Screen.RESULTS && resultDetails) return "RESULT_DETAILS";
        return screen.name();
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
        modal("Answer the call", "1. Connect one or two phones to the same Wi-Fi as the TV.\n\n2. Open the lobby and scan its QR code with your phone.\n\n3. Start solo, or connect a second phone for co-op.\n\nChoose a hero on the TV. Move with the phone joystick and tap Dodge. Attacks are automatic. Shared XP grants personal phone upgrades. Stay near a fallen ally to revive them. The Hollow Sovereign arrives at twelve minutes. Survive twenty minutes, then defeat the Grave King.\n\nA companion can also join later: press Back to pause, then choose Invite Player 2.",
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
        modalMessage = text;
        window.add(text).width(1000).left().padBottom(32).row();
        for (int i = 0; i < options.length; i++)
            window.add(textButton(options[i], actions[i])).width(1000).height(68).padTop(12).row();
        overlay.add(window); stage.addActor(overlay); overlay.validate(); setFocus(0);
    }

    private void closeModal() {
        if (overlay == null) return;
        overlay.remove(); overlay = null;
        heroChooser = null;
        pauseResume = null; pauseMessage = modalMessage = null; modalKind = Modal.GENERAL;
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
        if (heroChooser != null) heroChooser.focusChanged(focus);
        for (int i = 0; i < buttons.size(); i++) {
            RemoteButton button = buttons.get(i); button.focused(i == focus);
        }
    }

    /** Profile-only visual fixture: actual earned ranks and stats, without a survival playthrough. */
    private void previewHud(int targetLevel) {
        while (world.progression.level < targetLevel) {
            world.progression.addXp(world.progression.xpToNext - world.progression.xp);
            for (int round = 0; round < PartyProgression.MAX_LEVEL && world.levelUpPending(); round++)
            for (ArenaWorld.Player player : world.players) if (player.active) {
                PartyProgression.Offer offer = world.progression.offers[player.slot];
                if (offer.pending) {
                    int choice = 0, leastRank = Integer.MAX_VALUE;
                    for (int i = 0; i < offer.options.length; i++) {
                        int rank = world.progression.rank(player.slot, offer.options[i]);
                        if (rank < leastRank) { choice = i; leastRank = rank; }
                    }
                    world.progression.choose(player.slot, offer.id, choice);
                }
            }
        }
        if (world.progression.xpToNext > 0) {
            int target = Math.round(world.progression.xpToNext * .64f);
            world.progression.addXp(Math.max(0, target - world.progression.xp));
        }
        for (ArenaWorld.Player player : world.players) if (player.active) {
            player.alive = true; player.reviveProgress = 0; player.invulnerable = 999;
            player.health = world.equipment.maxHealth(player) * (player.slot == 0 ? .72f : .54f);
            player.dodgeCooldown = player.slot == 0 ? 0 : world.equipment.dodgeCooldown(player) * .5f;
        }
        world.souls = world.progression.totalXp;
        hudPreview = true; syncEncounterHud();
        hudState.notify("THE PARTY GROWS STRONGER", "", ArenaHudState.Tone.INFO, 6f);
        notifiedLevel = world.progression.level;
        publishProgression();
    }

    private void previewEquipment() {
        for (int guard = 0; guard < 8 && world.equipment.rewardsEarned() < 8; guard++) world.equipment.grantReward();
        for (int round = 0; round < 8 && world.lootPending(); round++)
        for (int slot = 0; slot < 2; slot++) {
            var offer = world.equipment.offers[slot];
            if (offer.pending) world.equipment.choose(slot, offer.id, 0);
        }
    }

    /** Profile-only camera landmarks avoid walking a survival run to inspect the new room. */
    private int arenaPreviewIndex = -1;
    private int enemySizePreviewFacing, enemyCastPreviewFacing;
    private void previewArenaLandmark() {
        arenaPreviewIndex = (arenaPreviewIndex + 1) % 8;
        float x = switch (arenaPreviewIndex) { case 0, 1 -> 0f; case 2 -> 600f;
            case 3 -> -1100f; case 4, 5 -> 1100f; case 6 -> -1100f; default -> 0f; };
        float y = switch (arenaPreviewIndex) { case 0 -> 650f; case 1 -> -440f; case 2 -> 0f;
            case 3, 5 -> -780f; case 4, 6 -> 780f; default -> 0f; };
        world.debugSceneryView(x, y);
        arena.resetCameras();
    }

    /** Profile-only opaque glyph fixture; does not pause or change the underlying screen. */
    private void toggleFontParityOverlay() {
        if (fontParityOverlay != null) {
            fontParityOverlay.remove(); fontParityOverlay = null;
            return;
        }
        Table fixture = new Table();
        fixture.setFillParent(true);
        fixture.setTouchable(Touchable.disabled);
        fixture.setBackground(((TextureRegionDrawable) art.pixel).tint(Color.BLACK));
        BitmapFont[] fonts = {art.title, art.heading, art.section, art.body, art.small};
        Color[] colors = {UiArt.GOLD, UiArt.IVORY, UiArt.PLAYER_TWO, UiArt.MUTED, UiArt.GREEN};
        for (int row = 0; row < fonts.length; row++) {
            float bottom = 810 - row * 180;
            Label normal = art.label("AV To WA 0123", fonts[row], colors[row]);
            normal.setAlignment(Align.left);
            normal.setBounds(100.25f, bottom + .5f, 860, 160);
            fixture.addActor(normal);
            Label scaled = art.label("AV To 0123\nRank 3 +20%", fonts[row], colors[(row + 1) % colors.length]);
            scaled.setFontScale(.73f); scaled.setAlignment(Align.left);
            scaled.setBounds(1020.5f, bottom + .25f, 800, 160);
            fixture.addActor(scaled);
        }
        fontParityOverlay = fixture;
        stage.addActor(fixture); fixture.toFront();
    }

    private boolean key(int key) {
        if (key == Input.Keys.END && config.profile()) {
            toggleFontParityOverlay(); return true;
        }
        if (key == Input.Keys.INSERT && config.profile()) {
            long begin = System.nanoTime();
            art.reloadFileTextures(); buildIcons.reloadFileTexture(); outcomeTitles.reloadFileTextures();
            Gdx.app.log("UiReload", "Managed UI file reload wallNs=" + (System.nanoTime() - begin)
                + " gpuTextureBytes=" + residentArtBytes() + " retainedPixmapBytes=" + residentPixmapBytes());
            return true;
        }
        // Profiling capture boundary only: exclude fixture/warm-up work without changing the run.
        if (key == Input.Keys.PAGE_DOWN && frameMetrics != null) {
            frameMetrics.reset();
            return true;
        }
        if (key == Input.Keys.PAGE_UP && config.profile() && screen == Screen.SESSION
            && overlay == null && !world.choicePending()) {
            previewArenaLandmark(); return true;
        }
        if ((key == Input.Keys.NUM_1 || key == Input.Keys.MEDIA_NEXT) && config.profile() && screen == Screen.SESSION
            && overlay == null && !world.choicePending()) {
            world.debugEnemySizeLineup(ActorAnimation.Facing.values()[enemySizePreviewFacing++ % 4]);
            arena.resetCameras(); return true;
        }
        if ((key == Input.Keys.NUM_2 || key == Input.Keys.MEDIA_PREVIOUS) && config.profile() && screen == Screen.SESSION
            && overlay == null && !world.choicePending()) {
            world.debugSizedCultistAttack(ActorAnimation.Facing.values()[enemyCastPreviewFacing++ % 4]);
            arena.resetCameras(); return true;
        }
        if (key == Input.Keys.F1 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.debugEnemyAttackEncounter(); return true;
        }
        if (key == Input.Keys.F2 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.equipment.grantReward(); publishProgression(); return true;
        }
        if (key == Input.Keys.F12 && config.profile() && screen == Screen.SESSION && overlay == null) {
            previewHud(18); previewEquipment(); world.debugStartLateEncounter();
            hudPreview = false; notifiedWave = world.wave;
            notifiedReliquaries = world.equipment.rewardsEarned();
            syncEncounterHud(); publishProgression(); return true;
        }
        if (key == Input.Keys.F11 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.debugStartLateEncounter();
            previewHud(18);
            hudPreview = false;
            notifiedWave = world.wave;
            syncEncounterHud();
            return true;
        }
        if (key == Input.Keys.F10 && config.profile() && screen == Screen.SESSION && overlay == null) {
            // Repeat the populated HUD preview to inspect critical health without a survival run.
            boolean critical = hudPreview && world.players[0].health > world.equipment.maxHealth(world.players[0]) * .2f;
            previewHud(12);
            if (critical) world.players[0].health = world.equipment.maxHealth(world.players[0]) * .15f;
            return true;
        }
        if (key == Input.Keys.F9 && config.profile() && screen == Screen.SESSION && overlay == null
            && world.players[0].active && world.players[0].alive && world.players[1].active) {
            // Brief UI fixture: first press puts P2 out of view; second brings them into revival range.
            var first = world.players[0]; var second = world.players[1];
            if (second.alive) {
                second.alive = false; second.health = second.reviveProgress = 0;
                second.x = first.x > 0 ? -1000f : 1000f;
                second.y = first.y > 0 ? -600f : 600f;
            } else {
                second.x = first.x + (first.x > 1000f ? -64f : 64f); second.y = first.y;
                second.reviveProgress = 0;
            }
            return true;
        }
        if (key == Input.Keys.F5 && config.profile() && screen == Screen.SESSION && overlay == null) {
            world.progression.addXp(world.progression.xpToNext);
            announceLevelUp(); publishProgression(); return true;
        }
        if (config.profile() && (screen == Screen.SESSION || screen == Screen.RESULTS)
            && (key == Input.Keys.F7 || key == Input.Keys.F8)) {
            previewResults(key == Input.Keys.F7); return true;
        }
        if ((key == Input.Keys.F6 || key == Input.Keys.FORWARD_DEL) && config.profile() && screen == Screen.SESSION && overlay == null) {
            // Delete also works with Android key injection without opening its letter-entry keyboard.
            if (key == Input.Keys.FORWARD_DEL || Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))
                world.debugStartSovereign();
            else world.debugStartBoss();
            hudPreview = false; syncEncounterHud(); return true;
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
        if (heroChooser != null) {
            int target = heroChooser.navigate(key, focus);
            if (target >= 0) { setFocus(target); return true; }
        }
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
        } else if (screen == Screen.RESULTS && resultSummary != null) {
            resultSummary.updateParty(snapshot.connected());
        } else if (screen == Screen.SESSION && pauseResume != null) {
            if (pauseTopology != pauseTopology(snapshot)) {
                String previousAction = ((Label) buttons.get(focus).getChildren().first()).getText().toString();
                showPause();
                for (int i = 0; i < buttons.size(); i++) if (!buttons.get(i).isDisabled()
                    && ((Label) buttons.get(i).getChildren().first()).textEquals(previousAction)) { setFocus(i); break; }
                return;
            }
            pauseMessage.setText(pauseDescription(snapshot));
            setTextButtonDisabled(pauseResume, !canResume(snapshot));
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
        hudState.notify("PARTY LEVEL " + notifiedLevel, "",
            ArenaHudState.Tone.SUCCESS, 4f);
    }

    private void syncChoices() {
        boolean visible = screen == Screen.SESSION && overlay == null
            && world.outcome == ArenaWorld.Outcome.RUNNING && world.choicePending();
        boolean loot = visible && !world.levelUpPending();
        boolean changed = levelUpView.isVisible() != visible || lootCeremony != loot;
        lootCeremony = loot;
        levelUpView.setVisible(visible);
        if (visible) { world.clearInputs(); levelUpView.update(world); }
        if (changed) publishProgression();
        if (screen == Screen.SESSION && overlay == null)
            state.setPhase(visible ? loot ? LobbyState.Phase.LOOT : LobbyState.Phase.LEVEL_UP : LobbyState.Phase.SESSION);
    }

    private void publishProgression() {
        var progression = world.progression;
        boolean inRun = (screen == Screen.SESSION || screen == Screen.INVITE
            || screen == Screen.CHARACTER_INFO && characterInfoInvitation) && world.outcome == ArenaWorld.Outcome.RUNNING;
        var snapshot = state.snapshot(System.currentTimeMillis());
        for (int i = 0; i < 2; i++) {
            var player = i == 0 ? snapshot.first() : snapshot.second();
            boolean participant = inRun && world.players[i].active && player.reserved()
                && generations[i] == player.generation();
            var offer = progression.offers[i];
            boolean pending = participant && offer.pending;
            state.publishProgression(i, progression.level, progression.xp, progression.xpToNext,
                pending ? offer.id : 0, pending ? offer.level : 0, pending ? offer.secondsLeft : 0,
                pending ? offer.options : null, pending ? offer.cards : null,
                pending && offer.catchUp, pending ? offer.durationSeconds : PartyProgression.CHOICE_SECONDS);
            var itemOffer = world.equipment.offers[i];
            boolean itemPending = participant && !world.levelUpPending() && itemOffer.pending;
            state.publishEquipment(i, itemPending ? itemOffer.id : 0, itemPending ? itemOffer.secondsLeft : 0,
                itemPending ? itemOffer.options : null, itemPending ? itemOffer.cards : null,
                itemPending && itemOffer.catchUp, itemPending ? itemOffer.durationSeconds : 30f,
                itemPending ? itemOffer.origin : null);
            state.publishStarterBuild(i, participant, progression.foundationCount(i), world.equipment.foundationCount(i));
        }
    }

    private void syncEncounterHud() {
        ArenaWorld.Enemy encounter = world.activeBoss();
        if (encounter != null)
            hudState.setBoss(ArenaWorld.bossName(encounter), encounter.encounterPhase == 2 ? "ENRAGED" : "PHASE I",
                encounter.health, encounter.maxHealth);
        else if (hudPreview) hudState.setBoss("THE GRAVE KING", "HUD PREVIEW", 720, 1000);
        else hudState.clearBoss();
    }

    private void updateCombatNotices() {
        syncEncounterHud();
        if (world.equipment.rewardsEarned() > notifiedReliquaries) {
            notifiedReliquaries = world.equipment.rewardsEarned();
            hudState.notify("RELIQUARY FOUND  /  CHOOSE GEAR ON YOUR PHONE", "", ArenaHudState.Tone.SUCCESS, 4f);
        }
        if (world.wave > notifiedWave && !world.bossSpawned) {
            notifiedWave = world.wave;
            hudState.notify("WAVE " + world.wave + " / " + world.waveCount() + "  /  "
                + world.timeline.phase(world.wave - 1).name(), "", ArenaHudState.Tone.WARNING, 3f);
        }
        if (world.elitesKilled > notifiedEliteKills) {
            notifiedEliteKills = world.elitesKilled;
            hudState.notify("ELITE SLAIN  /  CLAIM ITS SOULS", "", ArenaHudState.Tone.SUCCESS, 3f);
        }
        if (world.sovereignSpawned && !sovereignAnnounced) {
            sovereignAnnounced = true; hudPreview = false;
            hudState.notify("THE HOLLOW SOVEREIGN AWAKENS", "", ArenaHudState.Tone.WARNING, 4f);
        }
        if (world.bossSpawned && !bossAnnounced) {
            bossAnnounced = true; hudPreview = false;
            hudState.notify("THE GRAVE KING AWAKENS", "", ArenaHudState.Tone.WARNING, 4f);
        }
        if (world.bossPhase == 2 && !enrageAnnounced) {
            enrageAnnounced = true;
            hudState.notify("THE GRAVE KING IS ENRAGED", "", ArenaHudState.Tone.WARNING, 3f);
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
            if (player.alive && player.health <= world.equipment.maxHealth(player) * .25f && !lowHealthNotified[i]) {
                lowHealthNotified[i] = true;
                hudState.notify(i == 0 ? "PLAYER I LOW HEALTH" : "PLAYER II LOW HEALTH",
                    "Dodge to safety. Seek a healing ember.", ArenaHudState.Tone.WARNING, 3.5f);
            }
            if (player.health > world.equipment.maxHealth(player) * .4f) lowHealthNotified[i] = false;
        }
        if (!finalWarning && !world.bossSpawned && world.timeRemaining() <= 30 && world.outcome == ArenaWorld.Outcome.RUNNING) {
            finalWarning = true;
            hudState.notify("THE GRAVE KING ARRIVES IN 30s", "", ArenaHudState.Tone.WARNING, 4f);
        }
    }

    @Override public void render() {
        if (frameMetrics != null) frameMetrics.beginFrame();
        float rawDelta = Gdx.graphics.getDeltaTime();
        float delta = Math.min(rawDelta, 0.1f);
        int simulationSteps = 0;
        if (frameMetrics != null) frameMetrics.beginStage(FrameMetrics.HANDOFF);
        refreshClock += delta; profileClock += delta;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 2; i++) state.readControls(i, controls[i], now);
        syncChoices();
        if (screen == Screen.SESSION && overlay == null && hostActive) {
            boolean disconnected = false;
            for (int i = 0; i < 2; i++) disconnected |= world.players[i].active && !controls[i].connected;
            if (disconnected) showPause();
            else {
                for (int i = 0; i < 2; i++) {
                    var input = controls[i];
                    if (world.progression.offers[i].pending || world.equipment.offers[i].pending) {
                        world.setInput(i, 0, 0, false);
                        continue;
                    }
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
                    var itemRequest = equipmentRequests[i];
                    if (!world.progression.offers[i].pending && state.pollEquipment(i, itemRequest)
                        && itemRequest.generation == controls[i].generation
                        && world.equipment.choose(i, itemRequest.offerId, itemRequest.option)) {
                        hudState.notify((i == 0 ? "PLAYER I " : "PLAYER II ")
                            + (itemRequest.option < 0 ? "GEAR KEPT" : "EQUIPPED"), "", ArenaHudState.Tone.SUCCESS, 2f);
                    }
                }
                if (frameMetrics != null) {
                    frameMetrics.endStage(FrameMetrics.HANDOFF);
                    frameMetrics.beginStage(FrameMetrics.SIMULATION);
                }
                world.update(delta);
                simulationSteps = world.lastUpdateSteps;
                if (frameMetrics != null) {
                    frameMetrics.endStage(FrameMetrics.SIMULATION);
                    frameMetrics.beginStage(FrameMetrics.AUDIO);
                }
                audio.update(world, delta);
                if (frameMetrics != null) {
                    frameMetrics.endStage(FrameMetrics.AUDIO);
                    frameMetrics.beginStage(FrameMetrics.HANDOFF);
                }
                announceLevelUp();
                hudState.update(delta);
                updateCombatNotices();
                if (world.outcome != ArenaWorld.Outcome.RUNNING) showResults();
            }
        }
        syncChoices();
        if (frameMetrics != null) {
            frameMetrics.endStage(FrameMetrics.HANDOFF);
            frameMetrics.beginStage(FrameMetrics.STATUS);
        }
        if (refreshClock >= 0.2f) {
            refreshClock = 0; updateStatus(); publishProgression();
            for (int i = 0; i < 2; i++) {
                var player = world.players[i];
                state.setPlayerFeedback(i, player.health, world.equipment.maxHealth(player), player.dodgeCooldown, player.alive);
            }
        }
        if (profileClock >= 5) {
            profileClock = 0;
            if (debug || config.profile()) {
                String stats = Gdx.graphics.getFramesPerSecond() + " FPS  /  Java " + Gdx.app.getJavaHeap() / 1_048_576
                    + " MiB  /  Art " + residentArtBytes() / 1_048_576 + " MiB  /  Draws " + (arena.drawCalls + ((SpriteBatch) stage.getBatch()).renderCalls);
                diagnostics.setText(stats);
                if (config.profile()) {
                    Gdx.app.log("LobbyPerf", stats);
                    var counters = world.diagnostics();
                    int healingOnFloor = 0, healingValue = 0;
                    for (var pickup : world.pickups) if (pickup.active && pickup.healing) {
                        healingOnFloor++; healingValue += pickup.value;
                    }
                    Gdx.app.log("ProfileWorld", "phase=" + profileMode() + " runSeed=" + world.runSeed
                        + " elapsed=" + world.elapsed + " candidates=" + counters.boltCollisionCandidates
                        + " enemyMisses=" + counters.enemySpawnMisses + " boltMisses=" + counters.boltAcquireMisses
                        + " shotMisses=" + counters.shotAcquireMisses + " hazardMisses=" + counters.hazardAcquireMisses
                        + " effectMisses=" + counters.effectAcquireMisses + " pickupMerges=" + counters.pickupMerges
                        + " partyLevel=" + world.progression.level + " partyXp=" + world.progression.totalXp
                        + " kills=" + world.kills + " activeEnemies=" + world.activeEnemies
                        + " healingOnFloor=" + healingOnFloor + " healingFloorValue=" + healingValue
                        + " healingGenerated=" + world.healthDrops.generatedValue + " healingExpired=" + world.healthDrops.expiredValue
                        + " healingCollected=" + (world.players[0].healingFromDrops + world.players[1].healingFromDrops)
                        + " regeneration=" + (world.players[0].healingFromRegeneration + world.players[1].healingFromRegeneration)
                        + " gearSustain=" + (world.players[0].healingFromSustain + world.players[1].healingFromSustain)
                        + " sovereignFightSeconds=" + (world.sovereignSpawned
                            ? Math.max(0, (world.sovereignDefeated ? world.sovereignDefeatedAt : world.elapsed) - world.timeline.sovereignSeconds()) : 0));
                }
            }
        }
        if (hostActive && background.isVisible()) art.menuScene.advance(delta);
        if (frameMetrics != null) {
            frameMetrics.endStage(FrameMetrics.STATUS);
            frameMetrics.beginStage(FrameMetrics.ARENA);
        }
        ScreenUtils.clear(0.025f, 0.035f, 0.04f, 1);
        if (screen == Screen.SESSION) arena.render(world, hudState,
            overlay != null || world.choicePending() ? 0f : delta, !world.choicePending());
        if (frameMetrics != null) {
            frameMetrics.endStage(FrameMetrics.ARENA);
            frameMetrics.beginStage(FrameMetrics.UI);
        }
        stage.getViewport().apply(); stage.act(delta);
        if (fontParityOverlay != null) fontParityOverlay.toFront();
        stage.draw();
        if (frameMetrics != null) {
            frameMetrics.endStage(FrameMetrics.UI);
            frameMetrics.addSimulationSteps(simulationSteps, rawDelta);
            int enemies = 0, bolts = 0, pickups = 0, shots = 0, hazards = 0, effects = 0;
            for (var value : world.enemies) if (value.active) enemies++;
            for (var value : world.bolts) if (value.active) bolts++;
            for (var value : world.pickups) if (value.active) pickups++;
            for (var value : world.shots) if (value.active) shots++;
            for (var value : world.hazards) if (value.active) hazards++;
            for (var value : world.effects) if (value.active) effects++;
            frameMetrics.recordPoolCounts(enemies, bolts, pickups, shots, hazards, effects);
            frameMetrics.endFrame(profileMode(), residentArtBytes(), residentPixmapBytes(),
                Gdx.app.getJavaHeap(), (screen == Screen.SESSION ? arena.drawCalls : 0) + ((SpriteBatch) stage.getBatch()).renderCalls);
        }
    }
    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void pause() {
        if (frameMetrics != null) frameMetrics.reset();
        hostActive = false; state.setHostActive(false);
        if (audio != null) audio.stop();
        if (screen == Screen.SESSION && overlay == null) showPause();
    }
    @Override public void resume() {
        if (frameMetrics != null) frameMetrics.reset();
        hostActive = true; state.setHostActive(true);
        if (art != null && qrImage != null) refreshQr();
    }
    @Override public void dispose() {
        if (server != null) server.stop();
        disposeQr();
        stage.dispose(); arena.dispose(); audio.dispose();
        if (buildIcons != null) buildIcons.dispose();
        if (itemArt != null) itemArt.dispose();
        if (outcomeTitles != null) outcomeTitles.dispose();
        if (hudArt != null) hudArt.dispose();
        gameArt.dispose(); art.dispose();
    }
}
