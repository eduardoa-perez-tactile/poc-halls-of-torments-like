package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.JsonReader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingGeometryTest {
    @Test void textureFreeColourMatchesThePreviouslySampledOpaqueWhiteTexel() throws Exception {
        Path root = Files.exists(Path.of("../assets/art/gameplay.json")) ? Path.of("..") : Path.of(".");
        var metadata = new JsonReader().parse(Files.readString(root.resolve("assets/art/gameplay.json")));
        int index = metadata.get("regions").get("white").getInt("frame");
        var frame = metadata.get("frames").get(index);
        var image = ImageIO.read(root.resolve("assets/art/" + metadata.get("pages").get(frame.getInt("page")).getString("file")).toFile());
        assertEquals(1, frame.get("rect").getInt(2));
        assertEquals(1, frame.get("rect").getInt(3));
        assertEquals(0xffffffff, image.getRGB(frame.get("rect").getInt(0), frame.get("rect").getInt(1)));
    }

    @Test void staticMeshReproducesEveryTriangleOfThePreviousLargeOutline() {
        RingGeometry geometry = new RingGeometry();
        // Includes the resolution boundary, Ranger range and enlarged reach.
        for (float radius : new float[]{90.1f, 800f, 1100f}) {
            int step = 1;
            int at = 0;
            int start = at;
            float[] edges = {-1.3f, -.6f, .6f, 1.3f};
            for (int edge = 0; edge < 3; edge++) {
                for (int segment = 0; segment < 192; segment += step) {
                    int next = (segment + step) % 192;
                    // SpriteBatch's former per-quad triangle order: a,b,c / c,d,a.
                    int[] segments = {segment, segment, next, next, next, segment};
                    int[] bands = {edge, edge + 1, edge + 1, edge + 1, edge, edge};
                    for (int triangleVertex = 0; triangleVertex < 6; triangleVertex++) {
                        int vertex = geometry.indices[at++] * RingGeometry.STRIDE;
                        float angle = MathUtils.PI2 * segments[triangleVertex] / 192;
                        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
                        float normalLength = (float) Math.sqrt(c * c + 4f * s * s);
                        float oldX = -377f + c * radius + c / normalLength * edges[bands[triangleVertex]];
                        float oldY = 221f + s * .5f * radius + 2f * s / normalLength * edges[bands[triangleVertex]];
                        float newX = -377f + geometry.vertices[vertex] * radius
                            + geometry.vertices[vertex + 2] * geometry.vertices[vertex + 4];
                        float newY = 221f + geometry.vertices[vertex + 1] * radius
                            + geometry.vertices[vertex + 3] * geometry.vertices[vertex + 4];
                        assertEquals(oldX, newX, 0f);
                        assertEquals(oldY, newY, 0f);
                        assertEquals(bands[triangleVertex] == 0 || bands[triangleVertex] == 3 ? 0f : 1f,
                            geometry.vertices[vertex + 5]);
                    }
                }
            }
            assertEquals(RingGeometry.FULL_INDICES, at - start);
        }
        assertEquals(25_344, geometry.vertices.length * Float.BYTES + geometry.indices.length * Short.BYTES);
    }

    @Test void smallWarningBufferPreservesEveryOriginalPositionPackedColourAndWhiteTexelUv() {
        RingGeometry.Small small = new RingGeometry.Small(.125f, .625f);
        for (float radius : new float[]{26f, 55f, 90f}) {
            for (float alpha : new float[]{0f, .35f, .57f, 1f}) {
                small.update(-377f, 221f, radius, 1f, .93f, .77f, alpha);
                int vertex = 0;
                float[] edges = {-1.3f, -.6f, .6f, 1.3f};
                for (int edge = 0; edge < 3; edge++) {
                    for (int segment = 0; segment < 192; segment += 4) {
                        int next = (segment + 4) % 192;
                        int[] segments = {segment, segment, next, next};
                        int[] bands = {edge, edge + 1, edge + 1, edge};
                        for (int point = 0; point < 4; point++) {
                            float angle = MathUtils.PI2 * segments[point] / 192;
                            float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
                            float length = (float) Math.sqrt(c * c + 4f * s * s);
                            int at = vertex++ * 5;
                            assertEquals(-377f + c * radius + c / length * edges[bands[point]], small.vertices[at], 0f);
                            assertEquals(221f + s * .5f * radius + 2f * s / length * edges[bands[point]], small.vertices[at + 1], 0f);
                            float expected = Color.toFloatBits(1f, .93f, .77f, bands[point] == 0 || bands[point] == 3 ? 0 : alpha);
                            assertEquals(NumberUtils.floatToRawIntBits(expected), NumberUtils.floatToRawIntBits(small.vertices[at + 2]));
                            assertEquals(.125f, small.vertices[at + 3]);
                            assertEquals(.625f, small.vertices[at + 4]);
                        }
                    }
                }
                assertEquals(144 * 20, small.vertices.length);
            }
        }
    }

    @Test void uniformsPreserveExistingPackedSpriteColourAndAlpha() {
        for (float[] color : new float[][]{{1f, .82f, .42f, .57f}, {.36f, .95f, .90f, .57f},
            {1f, .93f, .77f, .35f}, {1f, .8f, .4f, 0f}, {1f, 1f, 1f, 1f}}) {
            int packed = NumberUtils.floatToRawIntBits(Color.toFloatBits(color[0], color[1], color[2], color[3]));
            assertEquals((packed & 255) / 255f, RingGeometry.channel(color[0]));
            assertEquals(((packed >>> 8) & 255) / 255f, RingGeometry.channel(color[1]));
            assertEquals(((packed >>> 16) & 255) / 255f, RingGeometry.channel(color[2]));
            assertEquals((packed >>> 24) / 255f * (255f / 254f), RingGeometry.alpha(color[3]), .000001f);
        }
    }
}
