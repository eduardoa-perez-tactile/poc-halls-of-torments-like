package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

public final class UiArt implements Disposable {
    public static final Color GOLD = Color.valueOf("d4b47c");
    public static final Color IVORY = Color.valueOf("eee7d8");
    public static final Color MUTED = Color.valueOf("b5b4ac");
    public static final Color GREEN = Color.valueOf("9bc3a9");
    public static final Color PLAYER_TWO = Color.valueOf("9bc3bb");
    public static final Color BUTTON_INK = Color.valueOf("1d1710");
    public static final Color BUTTON_DISABLED_INK = Color.valueOf("554b3e");
    // Offline exports keep full-size masters out of GPU residency. Both portrait
    // pages together cost less than the former 1024x512 full-body page alone.
    public final Texture heroes = load("art/adventurers-runtime.png");
    public final Texture heroFaces = load("art/hero-portraits.png");
    public final TextureRegion wardenFace = new TextureRegion(heroFaces, 0, 0, heroFaces.getWidth() / 2, heroFaces.getHeight());
    public final TextureRegion rangerFace = new TextureRegion(heroFaces, heroFaces.getWidth() / 2, 0, heroFaces.getWidth() / 2, heroFaces.getHeight());
    public final Texture homeLogo;
    public final MenuSceneArt menuScene;
    public final TextureRegion knight = new TextureRegion(heroes, 0, 0, heroes.getWidth() / 2, heroes.getHeight());
    public final TextureRegion ranger = new TextureRegion(heroes, heroes.getWidth() / 2, 0, heroes.getWidth() / 2, heroes.getHeight());
    public final BitmapFont title, heading, section, body, small;
    public final Drawable pixel;
    public final Drawable panel, focusPanel, disabledPanel, scrim, goldLine;
    public final Button.ButtonStyle normal, focused, cardNormal, cardFocused, homeNormal, homeFocused, exitNormal, exitFocused;
    private final Texture panelTexture, buttonTexture, focusedButtonTexture;
    private final Array<Texture> generated = new Array<>();
    private final Array<Pixmap> generatedPixmaps = new Array<>();
    private final BakedUiFonts fontBank;
    public final long textureBytes;
    /** Native CPU pixel copies retained for managed Android texture restoration. */
    public final long retainedPixmapBytes;

    public UiArt() {
        fontBank = new BakedUiFonts();
        title = fontBank.title; heading = fontBank.heading; section = fontBank.section;
        body = fontBank.body; small = fontBank.small;
        pixel = fill(Color.WHITE);
        scrim = fill(new Color(0.015f, 0.02f, 0.025f, 0.82f));
        goldLine = fill(new Color(GOLD.r, GOLD.g, GOLD.b, 0.42f));
        // Exact former bake outputs; Android reloads from these files without retained decoded copies.
        panelTexture = runtime("panel.ktx", 512, 512);
        panel = patch(panelTexture, 72, 72, 36, 36);
        // Shared bounds are essential: focused glow must not move or shrink the slab.
        buttonTexture = runtime("button.ktx", 1024, 224);
        focusedButtonTexture = runtime("button-focused.ktx", 1024, 224);
        NinePatchDrawable plate = patch(buttonTexture, 144, 64, 42, 20);
        NinePatchDrawable selectedPlate = patch(focusedButtonTexture, 144, 64, 42, 20);
        Drawable focusRim = bordered(Color.CLEAR, GOLD);
        focusPanel = new FocusDrawable(((NinePatchDrawable) panel).tint(new Color(1f, 0.94f, 0.82f, 1f)), focusRim);
        disabledPanel = plate.tint(new Color(0.82f, 0.82f, 0.82f, 0.8f));
        normal = new Button.ButtonStyle(plate, selectedPlate, plate);
        normal.disabled = disabledPanel;
        focused = new Button.ButtonStyle(selectedPlate, selectedPlate, selectedPlate);
        focused.disabled = disabledPanel;
        cardNormal = new Button.ButtonStyle(panel, focusPanel, panel);
        cardFocused = new Button.ButtonStyle(focusPanel, focusPanel, focusPanel);
        menuScene = new MenuSceneArt();
        homeLogo = runtime("logo.ktx", 1536, 384);
        NinePatchDrawable homePlate = patch(buttonTexture, 144, 64, 72, 32);
        NinePatchDrawable homeFocus = patch(focusedButtonTexture, 144, 64, 72, 32);
        homeNormal = new Button.ButtonStyle(homePlate, homeFocus, homePlate);
        homeFocused = new Button.ButtonStyle(homeFocus, homeFocus, homeFocus);
        NinePatchDrawable exitPlate = patch(buttonTexture, 144, 64, 48, 20);
        NinePatchDrawable exitFocus = patch(focusedButtonTexture, 144, 64, 48, 20);
        exitNormal = new Button.ButtonStyle(exitPlate, exitFocus, exitPlate);
        exitFocused = new Button.ButtonStyle(exitFocus, exitFocus, exitFocus);
        long bytes = (long) heroes.getWidth() * heroes.getHeight() * 4
            + (long) heroFaces.getWidth() * heroFaces.getHeight() * 4 + menuScene.textureBytes + fontBank.textureBytes;
        for (Texture texture : generated) bytes += (long) texture.getWidth() * texture.getHeight() * 4;
        textureBytes = bytes;
        long retained = menuScene.retainedPixmapBytes;
        for (Pixmap image : generatedPixmaps) retained += (long) image.getWidth() * image.getHeight() * 4;
        retainedPixmapBytes = retained;
        if (textureBytes > 32L * 1_048_576) throw new IllegalStateException("UI art exceeds its 32 MiB texture budget");
        Gdx.app.log("Art", "Resident texture budget: " + (textureBytes / 1_048_576f) + " MiB (excludes QR)");
    }

    private Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path), false);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }
    private Drawable fill(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color); pixmap.fill();
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        generatedPixmaps.add(pixmap); generated.add(texture);
        return new TextureRegionDrawable(texture);
    }
    private Drawable bordered(Color fill, Color edge) {
        Pixmap pixmap = new Pixmap(12, 12, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(edge); pixmap.fill();
        pixmap.setColor(fill); pixmap.fillRectangle(2, 2, 8, 8);
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        generatedPixmaps.add(pixmap); generated.add(texture);
        return new NinePatchDrawable(new NinePatch(texture, 4, 4, 4, 4));
    }
    private NinePatchDrawable patch(Texture texture, int splitX, int splitY, float cornerX, float cornerY) {
        NinePatch patch = new NinePatch(texture, splitX, splitX, splitY, splitY);
        patch.scale(cornerX / splitX, cornerY / splitY);
        NinePatchDrawable drawable = new NinePatchDrawable(patch);
        drawable.setMinWidth(0); drawable.setMinHeight(0);
        return drawable;
    }
    private Texture runtime(String name, int width, int height) {
        Texture texture = load("art/runtime-ui/" + name);
        if (!texture.isManaged() || texture.getWidth() != width || texture.getHeight() != height || texture.getTextureData().isPrepared()) {
            texture.dispose();
            throw new IllegalStateException("Runtime UI texture layout changed: " + name);
        }
        generated.add(texture);
        return texture;
    }

    /** Preencoded copies of the original normalized phone artwork; no image work at launch. */
    public byte[] controllerPanel() { return Gdx.files.internal("art/runtime-ui/panel.png").readBytes(); }
    public byte[] controllerButton(boolean selected) {
        return Gdx.files.internal(selected ? "art/runtime-ui/button-focused.png" : "art/runtime-ui/button.png").readBytes();
    }
    public byte[] controllerLogo() { return Gdx.files.internal("web/art/home-logo.png").readBytes(); }
    public byte[] controllerBackdrop() { return Gdx.files.internal("web/art/crypt.png").readBytes(); }

    /** Profiling diagnostic: exercise managed file decoding/upload without replacing GL handles. */
    public void reloadFileTextures() {
        heroes.load(heroes.getTextureData()); heroFaces.load(heroFaces.getTextureData());
        fontBank.reloadFileTextures(); menuScene.reloadFileTextures();
        for (Texture texture : generated)
            if (!(texture.getTextureData() instanceof PixmapTextureData))
                texture.load(texture.getTextureData());
    }

    public Label label(String text, BitmapFont font, Color color) { return new Label(text, new Label.LabelStyle(font, color)); }
    @Override public void dispose() {
        fontBank.dispose();
        heroes.dispose();
        heroFaces.dispose();
        menuScene.dispose();
        for (Texture texture : generated) texture.dispose();
        for (Pixmap pixmap : generatedPixmaps) pixmap.dispose();
    }
}
