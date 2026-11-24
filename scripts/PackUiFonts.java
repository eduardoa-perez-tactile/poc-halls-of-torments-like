import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Offline, lossless relocation of glyph rectangles and their original two-pixel surroundings. */
public final class PackUiFonts {
    private static final int WIDTH = 1024, HEIGHT = 768, GUTTER = 2;

    /** Owns only the packed page; the caller retains ownership of the original atlas. */
    public static final class Result implements AutoCloseable {
        public final Pixmap pixels;
        public final int glyphCount, blockCount;
        public final long copiedPixels;
        private final PixmapPacker packer;
        private boolean closed;

        private Result(PixmapPacker packer, int glyphCount, int blockCount, long copiedPixels) {
            this.packer = packer;
            pixels = packer.getPages().first().getPixmap();
            this.glyphCount = glyphCount; this.blockCount = blockCount; this.copiedPixels = copiedPixels;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            packer.dispose();
        }
    }

    /** Changes only srcX/srcY/page after the complete, single-page copy has been verified. */
    @SuppressWarnings("unchecked")
    public static Result pack(Pixmap original, Map<String, Object> fonts) {
        if (original.getFormat() != Pixmap.Format.RGBA8888)
            throw new IllegalArgumentException("Font source must retain RGBA8888 pixels");
        List<Block> blocks = new ArrayList<>();
        List<Map<String, Object>> emptyGlyphs = new ArrayList<>();
        int glyphCount = 0;
        for (var font : fonts.entrySet()) {
            Map<String, Object> definition = (Map<String, Object>) font.getValue();
            for (Map<String, Object> glyph : (List<Map<String, Object>>) definition.get("glyphs")) {
                glyphCount++;
                int width = integer(glyph, "width"), height = integer(glyph, "height");
                if (width < 0 || height < 0 || integer(glyph, "page") != 0)
                    throw new IllegalArgumentException("Expected nonnegative glyph dimensions on one source page");
                if (width == 0 || height == 0) {
                    emptyGlyphs.add(glyph);
                    continue;
                }
                Block block = new Block(font.getKey(), glyph, width, height);
                if (block.sourceX < 0 || block.sourceY < 0
                    || block.width > original.getWidth() - block.sourceX
                    || block.height > original.getHeight() - block.sourceY)
                    throw new IllegalArgumentException("Original glyph gutters lie outside the source: " + block.name());
                blocks.add(block);
            }
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("No visible glyph rectangles to pack");
        blocks.sort(Comparator.comparingInt((Block b) -> b.height).reversed()
            .thenComparing(Comparator.comparingInt((Block b) -> b.width).reversed())
            .thenComparing(b -> b.font).thenComparingInt(b -> b.id));
        // Blocks already include all four source gutters. No padding, duplication, rotation or resizing.
        PixmapPacker packer = new PixmapPacker(WIDTH, HEIGHT, Pixmap.Format.RGBA8888, 0, false,
            new PixmapPacker.SkylineStrategy());
        boolean transferred = false;
        try {
            long copiedPixels = 0;
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                Pixmap copy = new Pixmap(block.width, block.height, Pixmap.Format.RGBA8888);
                try {
                    copy.setBlending(Pixmap.Blending.None);
                    copy.drawPixmap(original, 0, 0, block.sourceX, block.sourceY, block.width, block.height);
                    var rectangle = packer.pack("glyph-" + i, copy);
                    if (packer.getPages().size != 1)
                        throw new IllegalStateException("Glyphs exceeded the single 1024x768 font page");
                    block.x = (int) rectangle.getX(); block.y = (int) rectangle.getY();
                    if (rectangle.getX() != block.x || rectangle.getY() != block.y
                        || rectangle.getWidth() != block.width || rectangle.getHeight() != block.height)
                        throw new IllegalStateException("Packer changed glyph block geometry: " + block.name());
                } finally { copy.dispose(); }
                copiedPixels += (long) block.width * block.height;
            }
            Pixmap packed = packer.getPages().first().getPixmap();
            // Use the packer for placement only; direct copies preserve RGB even under zero alpha.
            packed.setBlending(Pixmap.Blending.None);
            for (Block block : blocks)
                packed.drawPixmap(original, block.x, block.y, block.sourceX, block.sourceY, block.width, block.height);
            verify(original, packed, blocks);
            for (Block block : blocks) relocate(block.glyph, block.x + GUTTER, block.y + GUTTER);
            for (Map<String, Object> glyph : emptyGlyphs) relocate(glyph, 0, 0);
            Result result = new Result(packer, glyphCount, blocks.size(), copiedPixels);
            transferred = true;
            return result;
        } finally {
            if (!transferred) packer.dispose();
        }
    }

    private static void verify(Pixmap original, Pixmap packed, List<Block> blocks) {
        if (packed.getWidth() != WIDTH || packed.getHeight() != HEIGHT)
            throw new IllegalStateException("Packed font page dimensions changed");
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.x < 0 || block.y < 0 || block.x > WIDTH - block.width || block.y > HEIGHT - block.height)
                throw new IllegalStateException("Packed glyph block lies outside its page: " + block.name());
            for (int j = 0; j < i; j++) {
                Block other = blocks.get(j);
                if (block.x < other.x + other.width && block.x + block.width > other.x
                    && block.y < other.y + other.height && block.y + block.height > other.y)
                    throw new IllegalStateException("Packed glyph blocks overlap: " + block.name() + " / " + other.name());
            }
            for (int y = 0; y < block.height; y++) for (int x = 0; x < block.width; x++)
                if (original.getPixel(block.sourceX + x, block.sourceY + y) != packed.getPixel(block.x + x, block.y + y))
                    throw new IllegalStateException("Glyph RGBA or gutter changed: " + block.name() + " at " + x + "," + y);
        }
    }

    private static int integer(Map<String, Object> value, String key) { return ((Number) value.get(key)).intValue(); }

    private static void relocate(Map<String, Object> glyph, int x, int y) {
        glyph.put("srcX", x); glyph.put("srcY", y); glyph.put("page", 0);
    }

    private static final class Block {
        final String font;
        final Map<String, Object> glyph;
        final int id, sourceX, sourceY, width, height;
        int x, y;

        Block(String font, Map<String, Object> glyph, int width, int height) {
            this.font = font; this.glyph = glyph; id = integer(glyph, "id");
            sourceX = integer(glyph, "srcX") - GUTTER; sourceY = integer(glyph, "srcY") - GUTTER;
            this.width = width + GUTTER * 2; this.height = height + GUTTER * 2;
        }

        String name() { return font + "/" + id; }
    }
}
