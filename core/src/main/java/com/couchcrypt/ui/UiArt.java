package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class UiArt implements Disposable {
    public static final Color GOLD = Color.valueOf("d4b47c");
    public static final Color IVORY = Color.valueOf("eee7d8");
    public static final Color MUTED = Color.valueOf("b5b4ac");
    public static final Color GREEN = Color.valueOf("9bc3a9");
    public static final Color PLAYER_TWO = Color.valueOf("9bc3bb");
    public static final Color BUTTON_INK = Color.valueOf("1d1710");
    public static final Color BUTTON_DISABLED_INK = Color.valueOf("554b3e");
    // The unchanged illustration is exported above the largest portrait draw.
    // Keep its full-size master out of GPU residency (roughly 4 MiB saved).
    public final Texture heroes = load("art/adventurers-runtime.png");
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
    private final PixmapPacker fontPacker;
    public final long textureBytes;
    /** Native CPU pixel copies retained for managed Android texture restoration. */
    public final long retainedPixmapBytes;

    public UiArt() {
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false);
        fontPacker = packer;
        FreeTypeFontGenerator serif = new FreeTypeFontGenerator(Gdx.files.internal("fonts/cinzel.ttf"));
        FreeTypeFontGenerator sans = new FreeTypeFontGenerator(Gdx.files.internal("fonts/lato.ttf"));
        title = font(serif, packer, 96);
        heading = font(serif, packer, 58);
        section = font(serif, packer, 34);
        body = font(sans, packer, 28);
        small = font(sans, packer, 22);
        Array<TextureRegion> regions = new Array<>();
        packer.updateTextureRegions(regions, Texture.TextureFilter.Linear, Texture.TextureFilter.Linear, false);
        for (TextureRegion region : regions) if (!generated.contains(region.getTexture(), true)) generated.add(region.getTexture());
        // Retain the small font atlas pixmaps so Android can restore textures after GL context loss.
        serif.dispose(); sans.dispose();
        pixel = fill(Color.WHITE);
        scrim = fill(new Color(0.015f, 0.02f, 0.025f, 0.82f));
        goldLine = fill(new Color(GOLD.r, GOLD.g, GOLD.b, 0.42f));
        // Downsample the large source paintings once, before any frame is rendered.
        // Source masters stay untouched; only small managed runtime textures are retained.
        panelTexture = baked("art/ui-panel-v2.png", null, 512, 512, false);
        panel = patch(panelTexture, 72, 72, 36, 36);
        // Shared bounds are essential: focused glow must not move or shrink the slab.
        int[] buttonCrop = {0, 124, 2172, 456};
        buttonTexture = baked("art/home-button.png", buttonCrop, 1024, 224, false);
        focusedButtonTexture = baked("art/home-button-focused.png", buttonCrop, 1024, 224, false);
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
        homeLogo = baked("art/home-logo.png", null, 1536, 384, true);
        NinePatchDrawable homePlate = patch(buttonTexture, 144, 64, 72, 32);
        NinePatchDrawable homeFocus = patch(focusedButtonTexture, 144, 64, 72, 32);
        homeNormal = new Button.ButtonStyle(homePlate, homeFocus, homePlate);
        homeFocused = new Button.ButtonStyle(homeFocus, homeFocus, homeFocus);
        NinePatchDrawable exitPlate = patch(buttonTexture, 144, 64, 48, 20);
        NinePatchDrawable exitFocus = patch(focusedButtonTexture, 144, 64, 48, 20);
        exitNormal = new Button.ButtonStyle(exitPlate, exitFocus, exitPlate);
        exitFocused = new Button.ButtonStyle(exitFocus, exitFocus, exitFocus);
        long bytes = (long) heroes.getWidth() * heroes.getHeight() * 4 + menuScene.textureBytes;
        for (Texture texture : generated) bytes += (long) texture.getWidth() * texture.getHeight() * 4;
        textureBytes = bytes;
        long retained = menuScene.retainedPixmapBytes;
        for (Pixmap image : generatedPixmaps) retained += (long) image.getWidth() * image.getHeight() * 4;
        for (PixmapPacker.Page page : fontPacker.getPages()) {
            Pixmap image = page.getPixmap();
            retained += (long) image.getWidth() * image.getHeight() * 4;
        }
        retainedPixmapBytes = retained;
        if (textureBytes > 32L * 1_048_576) throw new IllegalStateException("UI art exceeds its 32 MiB texture budget");
        Gdx.app.log("Art", "Resident texture budget: " + (textureBytes / 1_048_576f) + " MiB (excludes QR)");
    }

    private Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path), false);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }
    private BitmapFont font(FreeTypeFontGenerator generator, PixmapPacker packer, int size) {
        var parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = size; parameter.packer = packer;
        parameter.characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?/()'&+-_=<>%\u2022";
        parameter.minFilter = Texture.TextureFilter.Linear; parameter.magFilter = Texture.TextureFilter.Linear;
        BitmapFont result = generator.generateFont(parameter);
        result.setOwnsTexture(false); result.setUseIntegerPositions(false);
        return result;
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
    private Texture baked(String path, int[] crop, int width, int height, boolean trimAlpha) {
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        if (crop == null && trimAlpha) crop = alphaBounds(source);
        Pixmap small = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        small.setBlending(Pixmap.Blending.None);
        small.setFilter(Pixmap.Filter.BiLinear);
        int x = crop == null ? 0 : crop[0], y = crop == null ? 0 : crop[1];
        int w = crop == null ? source.getWidth() : crop[2];
        int h = crop == null ? source.getHeight() : crop[3];
        small.drawPixmap(source, x, y, w, h, 0, 0, width, height);
        source.dispose();
        Texture texture = new Texture(new PixmapTextureData(small, null, false, false, true));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        generated.add(texture); generatedPixmaps.add(small);
        return texture;
    }
    private int[] alphaBounds(Pixmap image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getPixel(x, y) & 255) >= 64) {
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX) return new int[]{0, 0, image.getWidth(), image.getHeight()};
        minX = Math.max(0, minX - 8); minY = Math.max(0, minY - 8);
        maxX = Math.min(image.getWidth() - 1, maxX + 8); maxY = Math.min(image.getHeight() - 1, maxY + 8);
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }
    /** Encode the already baked skins once for phones, rather than retaining full-size source PNGs. */
    public byte[] controllerPanel() { return png(panelTexture, 512, 512); }
    public byte[] controllerButton(boolean selected) { return png(selected ? focusedButtonTexture : buttonTexture, 1024, 224); }
    public byte[] controllerLogo() { return png(homeLogo, 512, 128); }
    public byte[] controllerBackdrop() {
        Pixmap composed = menuScene.controllerPixmap();
        try { return png(composed); }
        finally { composed.dispose(); }
    }
    private byte[] png(Texture texture, int width, int height) {
        Pixmap source = texture.getTextureData().consumePixmap();
        Pixmap outputImage = source;
        if (source.getWidth() != width || source.getHeight() != height) {
            outputImage = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            outputImage.setBlending(Pixmap.Blending.None);
            outputImage.setFilter(Pixmap.Filter.BiLinear);
            outputImage.drawPixmap(source, 0, 0, source.getWidth(), source.getHeight(), 0, 0, width, height);
        }
        try { return png(outputImage); }
        finally { if (outputImage != source) outputImage.dispose(); }
    }
    private byte[] png(Pixmap image) {
        PixmapIO.PNG encoder = new PixmapIO.PNG(128 * 1024);
        encoder.setFlipY(false); encoder.setCompression(4);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            encoder.write(bytes, image);
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new GdxRuntimeException("Cannot encode controller artwork", failure);
        } finally {
            encoder.dispose();
        }
    }
    public Label label(String text, BitmapFont font, Color color) { return new Label(text, new Label.LabelStyle(font, color)); }
    @Override public void dispose() {
        title.dispose(); heading.dispose(); section.dispose(); body.dispose(); small.dispose();
        heroes.dispose();
        menuScene.dispose();
        for (Texture texture : generated) texture.dispose();
        fontPacker.dispose();
        for (Pixmap pixmap : generatedPixmaps) pixmap.dispose();
    }
}
