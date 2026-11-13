import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Bakes Android density resources from the original painted guardian master. JDK 21 only. */
public final class GenerateLauncherArt {
    private static final Color INK = Color.decode("#0b151b");
    private static final Color GOLD = Color.decode("#e1be7b");
    private static final Color IVORY = Color.decode("#eee7d8");

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = graphics(result);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage banner(int width, int height, Font font, BufferedImage guardian) {
        // Compose type at 4x output size, then downsample; no AI-generated lettering.
        int factor = 4;
        BufferedImage large = new BufferedImage(width * factor, height * factor, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(large);
        g.scale(width * factor / 640.0, height * factor / 360.0);
        g.setColor(INK); g.fillRect(0, 0, 640, 360);
        g.drawImage(guardian, 0, 0, 360, 360, null);
        // Blend painted shoulders into the title field without a framed thumbnail.
        g.setPaint(new LinearGradientPaint(240, 0, 392, 0,
            new float[]{0, 0.55f, 1},
            new Color[]{new Color(11, 21, 27, 0), new Color(11, 21, 27, 205), INK}));
        g.fillRect(240, 0, 400, 360);
        g.setFont(font.deriveFont(67f));
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString("COUCH", 305, 169); g.drawString("CRYPT", 305, 244);
        g.setColor(IVORY); g.drawString("COUCH", 303, 167);
        g.setColor(GOLD); g.drawString("CRYPT", 303, 242);
        g.dispose();
        return resize(large, width, height);
    }

    private static void save(BufferedImage image, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
        System.out.println(path + " " + image.getWidth() + "x" + image.getHeight());
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        BufferedImage guardian = ImageIO.read(root.resolve("docs/art/launcher-guardian.png").toFile());
        if (guardian == null || guardian.getWidth() != guardian.getHeight())
            throw new IllegalArgumentException("Expected a square painted guardian master");
        Font font = Font.createFont(Font.TRUETYPE_FONT, root.resolve("assets/fonts/cinzel.ttf").toFile());
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] icons = {80, 120, 160, 240, 320};
        int[] widths = {160, 240, 320, 480, 640};
        int[] heights = {90, 135, 180, 270, 360};
        for (int i = 0; i < densities.length; i++) {
            Path folder = root.resolve("android/src/main/res/mipmap-" + densities[i]);
            save(resize(guardian, icons[i], icons[i]), folder.resolve("ic_launcher.png"));
            save(banner(widths[i], heights[i], font, guardian), folder.resolve("tv_banner.png"));
        }
        // A 432px raster supplies the adaptive 108dp foreground at xxxhdpi density.
        save(resize(guardian, 432, 432), root.resolve("android/src/main/res/drawable-nodpi/launcher_guardian_foreground.png"));
    }
}
