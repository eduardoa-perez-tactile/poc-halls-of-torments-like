package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentProjectionTest {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;

    @Test void bothPaintedGroundEdgesFollowTheFloorWithoutRotatingUprights() throws Exception {
        Path root = root();
        GameplayAtlas atlas = load();
        JsonValue sources = new JsonReader().parse(Files.readString(root.resolve("art-source/crypt-environment-v2/environment.json")));
        for (String name : new String[]{"altar"}) {
            var region = atlas.regions.get(name);
            var frame = atlas.frames[region.frame()];
            BufferedImage page = ImageIO.read(root.resolve("assets/art/" + atlas.pages[frame.page].file()).toFile());
            var calibration = sources.get("regions").get(name).get("groundProjectionCalibration");
            var windows = calibration.get("edgeXWindowsInclusive");
            for (int edge = 0; edge < windows.size; edge++) {
                int first = windows.get(edge).getInt(0), last = windows.get(edge).getInt(1);
                double[] fit = fitBottomEdge(page, frame, first, last);
                assertEquals(calibration.get("sourceSlopesDown").getDouble(edge), fit[0], .0000001,
                    "The correction must fit the resident painted edge, rather than a guessed rotation");
                float left = EnvironmentProjection.projectedY(-50, (float) (50 * fit[0]), region);
                float right = EnvironmentProjection.projectedY(50, (float) (-50 * fit[0]), region);
                assertEquals(-calibration.get("targetSlopesDown").getFloat(edge), (right - left) / 100f, .000001f,
                    name + " must follow both floor axes");
            }
            assertEquals(0, EnvironmentProjection.projectedY(0, 0, region));
            assertEquals(region.renderScaleY() * 100, EnvironmentProjection.projectedY(0, 100, region), .00001f,
                "Only the Y distance changes: painted uprights retain a vertical direction");
        }
        var guardian = atlas.regions.get("guardian");
        assertEquals(1, guardian.renderScaleY());
        assertEquals(45, EnvironmentProjection.projectedY(30, 45, guardian));
    }

    @Test void measuredFrontBasesMeetTheirTrueGroundPlanesAtTheAuthoredPivots() throws Exception {
        Path root = root();
        GameplayAtlas atlas = load();
        JsonValue sources = new JsonReader().parse(Files.readString(root.resolve("art-source/crypt-environment-v2/environment.json")));
        for (String name : new String[]{"altar", "sealed-gate"}) {
            var region = atlas.regions.get(name); var frame = atlas.frames[region.frame()];
            var source = sources.get("regions").get(name);
            var fit = source.get("groundProjectionCalibration");
            float sourceX = frame.pivotX - source.get("normalization").get("offset").getFloat(0);
            float frontY = source.get("normalization").get("offset").getFloat(1)
                + fit.get("sourceSlopesDown").getFloat(0) * sourceX + fit.get("sourceInterceptsDown").getFloat(0);
            float scale = ISO_X * (region.footprintWorldX() + region.footprintWorldY()) / region.footprintWidth();
            float actual = EnvironmentProjection.projectedY(0, (frame.pivotY - frontY) * scale, region);
            float thickness = name.equals("altar") ? region.footprintWorldX() : region.footprintWorldY();
            assertEquals(-ISO_Y * thickness, actual, .0001f,
                "The ground root is the footprint centre, not a transparent point below the paint");
            assertEquals(source.get("pivot").getFloat(1), frame.pivotY);
        }
    }

    @Test void projectionMetadataRemainsBoundedAndCannotDistortOtherProps() throws Exception {
        JsonValue invalidScale = metadata();
        invalidScale.get("regions").get("altar").get("renderScaleY").set(new JsonValue(1.5));
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(invalidScale));
        JsonValue wrongProp = metadata();
        wrongProp.get("regions").get("guardian").addChild("renderScaleY", new JsonValue(1.1));
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(wrongProp));
    }

    private static double[] fitBottomEdge(BufferedImage page, GameplayAtlas.Frame frame, int first, int last) {
        int count = last - first + 1;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int x = first; x <= last; x++) {
            int y = frame.height - 1;
            while (y >= 0 && ((page.getRGB(frame.x + x, frame.y + y) >>> 24) & 255) < 32) y--;
            assertTrue(y >= 0, "A calibration edge may not include empty columns");
            sx += x; sy += y; sxx += x * x; sxy += x * y;
        }
        double slope = (count * sxy - sx * sy) / (count * sxx - sx * sx);
        return new double[]{slope, (sy - slope * sx) / count};
    }

    private static Path root() { return Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of("."); }
    private static JsonValue metadata() throws Exception {
        return new JsonReader().parse(Files.readString(root().resolve("assets/art/gameplay.json")));
    }
    private static GameplayAtlas load() throws Exception { return new GameplayAtlas(metadata()); }
}
