import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.couchcrypt.ui.UiArt;
import com.couchcrypt.ui.MenuSceneArt;
import com.couchcrypt.ui.BuildIcons;
import com.couchcrypt.ui.OutcomeArt;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Offline export of the pinned original LibGDX bakers. No GL context or GPU upload is needed. */
public final class BakeUiAssets {
    private static int textureId;
    private static final Map<String, Object> outputs = new LinkedHashMap<>();
    private static Path output;

    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]);
        Path phone = Path.of(args[1]);
        Files.createDirectories(output); Files.createDirectories(phone);
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        Gdx.files = new Lwjgl3Files();
        Gdx.app = proxy(Application.class);
        Gdx.gl = Gdx.gl20 = proxy(GL20.class);
        Gdx.graphics = proxy(Graphics.class);
        UiArt ui = new UiArt();
        BuildIcons icons = new BuildIcons();
        OutcomeArt.Titles titles = new OutcomeArt.Titles();
        try {
            MenuSceneArt scene = ui.menuScene;
            png("menu-base.png", pixels(scene.base));
            png("menu-atlas.png", (Pixmap) field(scene, "atlasPixels"));
            Map<String, Object> layers = new LinkedHashMap<>();
            for (String name : List.of("left", "right", "orb")) layers.put(name, layer(field(scene, name)));
            // The original crest only retained its TextureRegion, not its normalized placement.
            var crest = scene.levelUpCrest;
            layers.put("crest", Map.of("atlasX", crest.getRegionX(), "atlasY", crest.getRegionY(),
                "atlasWidth", crest.getRegionWidth(), "atlasHeight", crest.getRegionHeight(),
                "x", 0, "y", 0, "width", 1, "height", 1));
            writeJson(output.resolve("menu-layers.json"), Map.of("layers", layers));
            png("panel.png", pixels((Texture) field(ui, "panelTexture")));
            png("button.png", pixels((Texture) field(ui, "buttonTexture")));
            png("button-focused.png", pixels((Texture) field(ui, "focusedButtonTexture")));
            png("logo.png", pixels(ui.homeLogo));
            png("skill-icons.png", (Pixmap) field(icons, "pixels"));
            png("victory-title.png", pixels(titles.get(true)));
            png("defeat-title.png", pixels(titles.get(false)));
            var packer = (PixmapPacker) field(ui, "fontPacker");
            List<String> pages = new ArrayList<>();
            for (int i = 0; i < packer.getPages().size; i++) {
                String name = "font-page-" + i + ".png";
                png(name, packer.getPages().get(i).getPixmap()); pages.add(name.replace(".png", ".zktx"));
            }
            Map<String, Object> fonts = new LinkedHashMap<>();
            for (String name : List.of("title", "heading", "section", "body", "small"))
                fonts.put(name, font((BitmapFont) UiArt.class.getField(name).get(ui)));
            writeJson(output.resolve("fonts.json"), Map.of("pages", pages, "fonts", fonts));
            Map<String, byte[]> phones = new LinkedHashMap<>();
            phones.put("crypt.png", ui.controllerBackdrop()); phones.put("ui-panel-v2.png", ui.controllerPanel());
            phones.put("home-button.png", ui.controllerButton(false)); phones.put("home-button-focused.png", ui.controllerButton(true));
            phones.put("home-logo.png", ui.controllerLogo()); phones.put("skill-icons.png", icons.controllerIcons());
            Map<String, String> shared = Map.of("ui-panel-v2.png", "panel.png", "home-button.png", "button.png",
                "home-button-focused.png", "button-focused.png", "skill-icons.png", "skill-icons.png");
            for (var entry : phones.entrySet()) {
                String reused = shared.get(entry.getKey());
                if (reused != null) {
                    if (!Arrays.equals(entry.getValue(), Files.readAllBytes(output.resolve(reused))))
                        throw new IllegalStateException("Phone export differs from shared runtime file: " + reused);
                } else Files.write(phone.resolve(entry.getKey()), entry.getValue());
                outputs.put("web/art/" + entry.getKey(), Map.of("pngSha256", sha(entry.getValue()),
                    "file", reused == null ? "web/art/" + entry.getKey() : "art/runtime-ui/" + reused));
            }
            verifyFonts(ui, fonts);
            Map<String, Object> inputs = new TreeMap<>();
            for (String name : List.of("art/menu-crypt-base-v2.png", "art/menu-crypt-guardians-v2.png",
                "art/menu-crypt-orb-v2.png", "art/level-up-crest.png", "art/ui-panel-v2.png", "art/home-button.png",
                "art/home-button-focused.png", "art/home-logo.png", "art/skill-icons.png", "art/end-victory-title.png",
                "art/end-defeat-title.png", "fonts/cinzel.ttf", "fonts/lato.ttf"))
                inputs.put(name, sha(Files.readAllBytes(Path.of(name))));
            writeJson(output.resolve("manifest.json"), Map.of("version", 1, "sourceCommit", args[2],
                "gdxVersion", "1.14.2", "inputs", inputs, "outputs", outputs));
            System.out.println("Exported exact LibGDX pixels, five font datasets and six phone PNGs.");
        } finally { titles.dispose(); icons.dispose(); ui.dispose(); }
    }

    /** Compare actual old FreeType layout with the file loader, including UVs and wrapped runs. */
    private static void verifyFonts(UiArt ui, Map<String, Object> fonts) throws Exception {
        var reader = Class.forName("com.couchcrypt.ui.BakedUiFonts").getDeclaredMethod("readData", com.badlogic.gdx.utils.JsonValue.class);
        reader.setAccessible(true);
        int runs = 0;
        for (String name : fonts.keySet()) {
            BitmapFont original = (BitmapFont) UiArt.class.getField(name).get(ui);
            var data = (BitmapFont.BitmapFontData) reader.invoke(null, new com.badlogic.gdx.utils.JsonReader().parse(json(fonts.get(name))));
            BitmapFont restored = new BitmapFont(data, original.getRegions(), false);
            restored.setOwnsTexture(false);
            try {
                for (var page : original.getData().glyphs) if (page != null) for (var glyph : page) if (glyph != null) {
                    var other = data.getGlyph((char) glyph.id);
                    for (String key : List.of("id", "srcX", "srcY", "width", "height", "xoffset", "yoffset", "xadvance", "fixedWidth", "page", "u", "v", "u2", "v2")) {
                        Field field = BitmapFont.Glyph.class.getField(key);
                        if (!Objects.equals(field.get(glyph), field.get(other))) throw new IllegalStateException(name + " glyph field differs: " + key);
                    }
                    for (var second : original.getData().glyphs) if (second != null) for (var after : second) if (after != null)
                        if (glyph.getKerning((char) after.id) != other.getKerning((char) after.id))
                            throw new IllegalStateException("Kerning differs for " + name);
                }
                for (String text : List.of("AV To WA", "Play / How to Join / Settings", "LEVEL UP\n\nCHECK YOUR PHONE",
                    "PARTY LV 18  /  XP", "Run Details 100% +3", "Warden & Ranger • Dodge", "Current → Next", "")) {
                    var before = new com.badlogic.gdx.graphics.g2d.GlyphLayout(original, text, com.badlogic.gdx.graphics.Color.WHITE, 220, com.badlogic.gdx.utils.Align.left, true);
                    var after = new com.badlogic.gdx.graphics.g2d.GlyphLayout(restored, text, com.badlogic.gdx.graphics.Color.WHITE, 220, com.badlogic.gdx.utils.Align.left, true);
                    if (before.width != after.width || before.height != after.height || before.runs.size != after.runs.size)
                        throw new IllegalStateException("Layout dimensions differ for " + name);
                    for (int i = 0; i < before.runs.size; i++) {
                        var a = before.runs.get(i); var b = after.runs.get(i);
                        if (a.x != b.x || a.y != b.y || a.width != b.width || !a.xAdvances.equals(b.xAdvances) || a.glyphs.size != b.glyphs.size)
                            throw new IllegalStateException("Glyph run differs for " + name);
                        for (int g = 0; g < a.glyphs.size; g++) if (a.glyphs.get(g).id != b.glyphs.get(g).id)
                            throw new IllegalStateException("Glyph sequence differs for " + name);
                    }
                    runs++;
                }
            } finally { restored.dispose(); }
        }
        System.out.println("Verified all original glyph metrics, kerning, UVs and " + runs + " real font layouts.");
    }

    private static Map<String, Object> font(BitmapFont font) throws Exception {
        var data = font.getData();
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (String key : List.of("name", "flipped", "padTop", "padRight", "padBottom", "padLeft", "lineHeight",
            "capHeight", "ascent", "descent", "down", "blankLineScale", "scaleX", "scaleY", "markupEnabled",
            "cursorX", "spaceXadvance", "xHeight", "breakChars", "xChars", "capChars")) {
            Object value = BitmapFont.BitmapFontData.class.getField(key).get(data);
            metrics.put(key, value instanceof char[] chars ? new String(chars) : value);
        }
        List<Object> glyphs = new ArrayList<>();
        for (var page : data.glyphs) if (page != null) for (var glyph : page) if (glyph != null) {
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String key : List.of("id", "srcX", "srcY", "width", "height", "xoffset", "yoffset", "xadvance", "fixedWidth", "page"))
                entry.put(key, BitmapFont.Glyph.class.getField(key).get(glyph));
            List<Object> kerning = new ArrayList<>();
            if (glyph.kerning != null) for (int p = 0; p < glyph.kerning.length; p++) if (glyph.kerning[p] != null)
                for (int c = 0; c < glyph.kerning[p].length; c++) if (glyph.kerning[p][c] != 0)
                    kerning.add(List.of((p << 9) + c, (int) glyph.kerning[p][c]));
            entry.put("kerning", kerning); glyphs.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metrics", metrics); result.put("glyphs", glyphs);
        result.put("missingGlyph", data.missingGlyph == null ? null : data.missingGlyph.id);
        return result;
    }

    private static Map<String, Object> layer(Object layer) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("atlasX", "atlasY", "atlasWidth", "atlasHeight", "x", "y", "width", "height"))
            result.put(key, field(layer, key));
        return result;
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Pixmap pixels(Texture texture) { return texture.getTextureData().consumePixmap(); }
    private static void png(String name, Pixmap pixels) throws Exception {
        ByteBuffer source = pixels.getPixels().duplicate(); source.position(0);
        byte[] rgba = new byte[pixels.getWidth() * pixels.getHeight() * 4]; source.get(rgba);
        // Four PNGs are also HTTP assets. The TV uses lossless ready-to-upload RGBA KTX.
        if (Set.of("panel.png", "button.png", "button-focused.png", "skill-icons.png").contains(name)) {
            PixmapIO.PNG encoder = new PixmapIO.PNG(128 * 1024);
            encoder.setFlipY(false); encoder.setCompression(4);
            try (var out = Files.newOutputStream(output.resolve(name))) { encoder.write(out, pixels); }
            finally { encoder.dispose(); }
            outputs.put(name, Map.of("width", pixels.getWidth(), "height", pixels.getHeight(),
                "rgbaSha256", sha(rgba), "pngSha256", sha(Files.readAllBytes(output.resolve(name)))));
        }
        ByteBuffer ktx = ByteBuffer.allocate(68 + rgba.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        ktx.put(new byte[]{(byte) 0xab,0x4b,0x54,0x58,0x20,0x31,0x31,(byte) 0xbb,0x0d,0x0a,0x1a,0x0a});
        ktx.putInt(0x04030201).putInt(0x1401).putInt(1).putInt(0x1908).putInt(0x1908).putInt(0x1908);
        ktx.putInt(pixels.getWidth()).putInt(pixels.getHeight()).putInt(0).putInt(0).putInt(1).putInt(1).putInt(0);
        ktx.putInt(rgba.length).put(rgba);
        String packed = name.replace(".png", ".zktx");
        try (var out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(output.resolve(packed))))) {
            out.writeInt(ktx.capacity()); out.write(ktx.array());
        }
        outputs.put(packed, Map.of("width", pixels.getWidth(), "height", pixels.getHeight(),
            "rgbaSha256", sha(rgba), "fileSha256", sha(Files.readAllBytes(output.resolve(packed)))));
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static void writeJson(Path file, Object value) throws Exception { Files.writeString(file, json(value) + "\n"); }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (var entry : map.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).toList()) entries.add(json(entry.getKey().toString()) + ":" + json(entry.getValue()));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> list) {
            List<String> entries = new ArrayList<>(); for (Object entry : list) entries.add(json(entry));
            return "[" + String.join(",", entries) + "]";
        }
        throw new IllegalArgumentException("Cannot serialize " + value.getClass());
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (instance, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
            if (method.getName().equals("equals")) return instance == args[0];
            if (method.getName().equals("toString")) return "OfflineUiBake";
            if (method.getName().equals("getType")) return Application.ApplicationType.Desktop;
            if (method.getName().equals("glGenTexture")) return ++textureId;
            if (method.getName().equals("getGL20")) return Gdx.gl20;
            Class<?> result = method.getReturnType();
            if (result == boolean.class) return false;
            if (result == int.class) return 0;
            if (result == long.class) return 0L;
            if (result == float.class) return 0f;
            return null;
        });
    }
}
