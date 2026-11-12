import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Original vector-native launcher artwork. Run with JDK 21 from the repository root. */
public final class GenerateLauncherArt {
    private static final Color INK = Color.decode("#141c22");
    private static final Color BRASS = Color.decode("#b99a63");
    private static final Color GOLD = Color.decode("#e1be7b");
    private static final Color IVORY = Color.decode("#eee7d8");
    private static final Color TEAL = Color.decode("#9bc3bb");
    private static final String ARCH = "M25 85 L25 49 C25 31 38 22 54 22 C70 22 83 31 83 49 L83 85 Z M35 81 L73 81 L73 50 C73 38 65 32 54 32 C43 32 35 38 35 50 Z";
    private static final String GOLD_FLAME = "M45 77 C35 75 35 67 41 60 C44 56 45 52 44 48 C51 54 55 63 52 70 C51 75 48 77 45 77 Z";
    private static final String TEAL_FLAME = "M63 77 C54 75 54 68 59 62 C63 57 64 53 63 49 C70 56 73 65 70 71 C69 75 66 77 63 77 Z";

    private static Path2D path(String instructions) {
        String[] parts = instructions.replaceAll("([MLCZ])", " $1 ").trim().split(" +");
        Path2D result = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        int at = 0;
        while (at < parts.length) {
            String command = parts[at++];
            switch (command) {
                case "M" -> result.moveTo(Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]));
                case "L" -> result.lineTo(Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]));
                case "C" -> result.curveTo(Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]), Double.parseDouble(parts[at++]));
                case "Z" -> result.closePath();
                default -> throw new IllegalArgumentException(command);
            }
        }
        return result;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }

    private static void crest(Graphics2D graphics, double x, double y, double size) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y); g.scale(size / 108, size / 108);
        g.setColor(BRASS); g.fill(path(ARCH));
        g.setColor(GOLD); g.fill(path("M50 22 L58 22 L57 33 L51 33 Z"));
        g.setColor(Color.decode("#7e8077"));
        g.fill(path("M25 54 L35 54 L35 58 L25 58 Z M73 54 L83 54 L83 58 L73 58 Z"));
        g.setColor(GOLD); g.fill(path(GOLD_FLAME));
        g.setColor(TEAL); g.fill(path(TEAL_FLAME));
        g.setColor(IVORY);
        g.fill(path("M45 72 C42 70 43 66 46 63 C49 67 49 71 45 72 Z M63 72 C60 70 61 67 64 64 C67 68 67 71 63 72 Z"));
        g.setColor(BRASS); g.fill(path("M22 83 L86 83 L86 88 L22 88 Z"));
        g.dispose();
    }

    private static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setPaint(new GradientPaint(0, 0, Color.decode("#243138"), size, size, INK));
        g.fillRect(0, 0, size, size);
        crest(g, 0, 0, size);
        g.dispose();
        return image;
    }

    private static BufferedImage banner(int width, int height, Font font) {
        // Render at four times target size, then downsample once for clean small-TV lettering.
        int factor = 4;
        BufferedImage large = new BufferedImage(width * factor, height * factor, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(large);
        g.scale(width * factor / 640.0, height * factor / 360.0);
        g.setPaint(new GradientPaint(0, 0, Color.decode("#26343b"), 640, 360, Color.decode("#10161b")));
        g.fillRect(0, 0, 640, 360);
        // Restrained architectural shadow; no border that a launcher mask could crop.
        g.setColor(new Color(124, 145, 143, 13));
        for (int i = 0; i < 5; i++) {
            g.fillRect(26 + i * 128, 0, 2, 360);
            g.fillRect(0, 24 + i * 84, 640, 1);
        }
        crest(g, 0, 34, 292);
        g.setFont(font.deriveFont(71f));
        g.setColor(IVORY); g.drawString("COUCH", 264, 168);
        g.setColor(GOLD); g.drawString("CRYPT", 264, 241);
        g.dispose();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D out = graphics(image);
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        out.drawImage(large, 0, 0, width, height, null); out.dispose();
        return image;
    }

    private static void save(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
        System.out.println(path + " " + image.getWidth() + "x" + image.getHeight());
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        Font font = Font.createFont(Font.TRUETYPE_FONT, root.resolve("assets/fonts/cinzel.ttf").toFile());
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] icons = {80, 120, 160, 240, 320};
        int[] widths = {160, 240, 320, 480, 640};
        int[] heights = {90, 135, 180, 270, 360};
        for (int i = 0; i < densities.length; i++) {
            Path folder = root.resolve("android/src/main/res/mipmap-" + densities[i]);
            save(icon(icons[i]), folder.resolve("ic_launcher.png"));
            save(banner(widths[i], heights[i], font), folder.resolve("tv_banner.png"));
        }
    }
}
