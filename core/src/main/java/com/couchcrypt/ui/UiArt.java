package com.couchcrypt.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
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

public final class UiArt implements Disposable {
    public static final Color GOLD = Color.valueOf("d4b47c");
    public static final Color IVORY = Color.valueOf("eee7d8");
    public static final Color MUTED = Color.valueOf("b5b4ac");
    public static final Color GREEN = Color.valueOf("9bc3a9");
    public static final Color PLAYER_TWO = Color.valueOf("9bc3bb");
    public final Texture backdrop = load("art/crypt.png");
    public final Texture heroes = load("art/adventurers.png");
    public final TextureRegion knight = new TextureRegion(heroes, 0, 0, heroes.getWidth() / 2, heroes.getHeight());
    public final TextureRegion ranger = new TextureRegion(heroes, heroes.getWidth() / 2, 0, heroes.getWidth() / 2, heroes.getHeight());
    public final BitmapFont title, heading, section, body, small;
    public final Drawable pixel;
    public final Drawable panel, focusPanel, disabledPanel, scrim, goldLine;
    public final Button.ButtonStyle normal, focused;
    private final Array<Texture> generated = new Array<>();
    private final Array<Pixmap> generatedPixmaps = new Array<>();
    private final PixmapPacker fontPacker;
    public final long textureBytes;

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
        panel = bordered(Color.valueOf("151a1de8"), Color.valueOf("77716377"));
        focusPanel = bordered(Color.valueOf("252923f5"), GOLD);
        disabledPanel = bordered(Color.valueOf("151a1dbb"), Color.valueOf("5e605d66"));
        normal = new Button.ButtonStyle(panel, focusPanel, panel);
        normal.disabled = disabledPanel;
        focused = new Button.ButtonStyle(focusPanel, focusPanel, focusPanel);
        focused.disabled = disabledPanel;
        long bytes = (long) backdrop.getWidth() * backdrop.getHeight() * 4 + (long) heroes.getWidth() * heroes.getHeight() * 4;
        for (Texture texture : generated) bytes += (long) texture.getWidth() * texture.getHeight() * 4;
        textureBytes = bytes;
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
        pixmap.setColor(edge); pixmap.fill();
        pixmap.setColor(fill); pixmap.fillRectangle(2, 2, 8, 8);
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        generatedPixmaps.add(pixmap); generated.add(texture);
        return new NinePatchDrawable(new NinePatch(texture, 4, 4, 4, 4));
    }
    public Label label(String text, BitmapFont font, Color color) { return new Label(text, new Label.LabelStyle(font, color)); }
    @Override public void dispose() {
        title.dispose(); heading.dispose(); section.dispose(); body.dispose(); small.dispose();
        backdrop.dispose(); heroes.dispose();
        for (Texture texture : generated) texture.dispose();
        fontPacker.dispose();
        for (Pixmap pixmap : generatedPixmaps) pixmap.dispose();
    }
}
