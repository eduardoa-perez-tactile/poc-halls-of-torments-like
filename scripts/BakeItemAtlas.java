import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Offline packing only: generated originals stay unchanged; the game loads this bounded atlas. */
class BakeItemAtlas {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path sources = args.length == 0 ? root.resolve(".run/item-art-originals") : Path.of(args[0]);
        var matcher = Pattern.compile("item\\(\"([a-z0-9_]+)\", \"[^\"]+\", Slot\\.")
            .matcher(Files.readString(root.resolve("core/src/main/java/com/couchcrypt/game/ItemCatalog.java")));
        List<String> ids = new ArrayList<>();
        while (matcher.find()) ids.add(matcher.group(1));
        if (ids.size() != 48) throw new IllegalStateException("Expected 48 catalog entries, found " + ids.size());
        for (String id : ids) if (!Files.isRegularFile(sources.resolve(id + ".png")))
            throw new IllegalStateException("Missing original item art: " + id);
        BufferedImage atlas = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        StringBuilder manifest = new StringBuilder("{\n  \"cell\": 128, \"columns\": 8, \"rows\": 6,\n  \"items\": [\n");
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            BufferedImage source = ImageIO.read(sources.resolve(id + ".png").toFile());
            if (source == null || source.getWidth() > 4096 || source.getHeight() > 4096)
                throw new IllegalStateException("Invalid source dimensions: " + id);
            int left = source.getWidth(), top = source.getHeight(), right = -1, bottom = -1;
            int transparent = 0;
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int alpha = source.getRGB(x, y) >>> 24;
                if (alpha < 8) transparent++;
                else { left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y); }
            }
            if (right < left || transparent < source.getWidth() * source.getHeight() / 10)
                throw new IllegalStateException("Expected isolated equipment with transparent margins: " + id);
            int width = right - left + 1, height = bottom - top + 1;
            double scale = Math.min(112.0 / width, 112.0 / height);
            int w = Math.max(1, (int) Math.round(width * scale)), h = Math.max(1, (int) Math.round(height * scale));
            int x = index % 8 * 128 + (128 - w) / 2, y = index / 8 * 128 + (128 - h) / 2;
            graphics.drawImage(source, x, y, x + w, y + h, left, top, right + 1, bottom + 1, null);
            source.flush();
            manifest.append("    {\"id\": \"").append(id).append("\", \"index\": ").append(index).append('}');
            manifest.append(index == ids.size() - 1 ? "\n" : ",\n");
        }
        graphics.dispose();
        manifest.append("  ]\n}\n");
        Path output = root.resolve("assets/art/item-icons.png");
        ImageIO.write(atlas, "png", output.toFile());
        Files.writeString(root.resolve("assets/art/item-icons.json"), manifest);
        System.out.println("Packed " + ids.size() + " illustrated items into " + output + " (3 MiB RGBA GPU)");
    }
}
