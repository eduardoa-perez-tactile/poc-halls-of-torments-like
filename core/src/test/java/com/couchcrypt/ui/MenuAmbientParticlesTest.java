package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuAmbientParticlesTest {
    private static final float LEFT = .118f, RIGHT = .881f, ORB_Y = .354f;

    @Test void longRunningFieldStaysBoundedAndOnlyWrapsInvisibleDust() {
        MenuAmbientParticles field = new MenuAmbientParticles(LEFT, RIGHT, ORB_Y);
        Capture capture = new Capture();
        float[] previous = new float[36 * Capture.STRIDE];
        capture.draw(field);
        System.arraycopy(capture.values, 0, previous, 0, previous.length);
        int wraps = 0;
        for (int frame = 0; frame < 6_000; frame++) {
            field.advance(.1f);
            capture.draw(field);
            assertEquals(36, capture.count);
            for (int i = 0; i < 36; i++) {
                int offset = i * Capture.STRIDE;
                float x = capture.values[offset], y = capture.values[offset + 1];
                float alpha = capture.values[offset + 2];
                assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(alpha));
                assertTrue(alpha >= 0 && alpha <= capture.color.a);
                if (i < 24) {
                    assertTrue(x > 0 && x < 1920 && y >= 54 && y <= 1026);
                    if (Math.abs(y - previous[offset + 1]) > 20) {
                        wraps++;
                        assertTrue(alpha < .00001f && previous[offset + 2] < .00001f,
                            "Dust crossing a scene edge must be invisible on both sides");
                    } else assertTrue(Math.hypot(x - previous[offset], y - previous[offset + 1]) < 3);
                } else {
                    float anchor = i < 30 ? LEFT : RIGHT;
                    double distance = Math.hypot(x - anchor * 1920, y - ORB_Y * 1080);
                    assertTrue(distance >= 35.9 && distance <= 65.1,
                        "Motes must stay close to their own orb instead of drifting across the scene");
                    assertTrue(Math.hypot(x - previous[offset], y - previous[offset + 1]) < 6,
                        "A completed orbit must remain spatially continuous");
                }
            }
            System.arraycopy(capture.values, 0, previous, 0, previous.length);
        }
        assertTrue(wraps >= 48, "Exercise multiple real lifecycle wraps rather than an initial snapshot");
    }

    @Test void motionDependsOnElapsedTimeAndIgnoresInvalidOrPausedDeltas() {
        MenuAmbientParticles coarse = new MenuAmbientParticles(LEFT, RIGHT, ORB_Y);
        MenuAmbientParticles fine = new MenuAmbientParticles(LEFT, RIGHT, ORB_Y);
        for (int i = 0; i < 1_000; i++) coarse.advance(.0625f);
        for (int i = 0; i < 2_000; i++) fine.advance(.03125f);
        Capture first = new Capture(), second = new Capture();
        first.draw(coarse); second.draw(fine);
        assertArrayEquals(first.values, second.values, .0001f);

        coarse.advance(0); coarse.advance(-1); coarse.advance(Float.NaN);
        coarse.advance(Float.POSITIVE_INFINITY); coarse.advance(Float.NEGATIVE_INFINITY);
        second.draw(coarse);
        assertArrayEquals(first.values, second.values);

        coarse.advance(30); fine.advance(.1f);
        first.draw(coarse); second.draw(fine);
        assertArrayEquals(first.values, second.values, .0001f,
            "A long stall must not fast-forward the atmosphere");
    }

    @Test void drawingRespectsCallerTintAlphaTransformAndBlendState() {
        MenuAmbientParticles field = new MenuAmbientParticles(LEFT, RIGHT, ORB_Y);
        Capture original = new Capture(), transformed = new Capture();
        original.draw(field);
        Color inherited = new Color(transformed.color);
        transformed.draw(field, 100, 50, 960, 540);
        assertEquals(inherited, transformed.color);
        assertEquals(0, transformed.blendChanges);
        assertEquals(36, transformed.count);
        for (int i = 0; i < 36; i++) {
            int offset = i * Capture.STRIDE;
            assertEquals(100 + original.values[offset] * .5f, transformed.values[offset], .001f);
            assertEquals(50 + original.values[offset + 1] * .5f, transformed.values[offset + 1], .001f);
            assertEquals(original.values[offset + 2], transformed.values[offset + 2]);
            assertEquals(original.values[offset + 3] * .5f, transformed.values[offset + 3], .00001f);
            assertEquals(original.values[offset + 4] * .5f, transformed.values[offset + 4], .00001f);
            assertTrue(transformed.values[offset + 2] <= inherited.a,
                "Particle opacity must multiply, not replace, the drawable's inherited alpha");
        }
        assertEquals(.8f * .91f, transformed.firstDustRed);
        assertEquals(.6f * .90f, transformed.firstMoteGreen);
    }

    /** Records the real draw API without creating a GL context or adding a mocking dependency. */
    private static final class Capture {
        static final int STRIDE = 5;
        final float[] values = new float[36 * STRIDE];
        final Color color = new Color(.8f, .6f, .4f, .7f);
        final TextureRegion point = new TextureRegion();
        int count, blendChanges;
        float firstDustRed, firstMoteGreen;
        final Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getColor")) return color;
                if (method.getName().equals("setColor")) {
                    color.set((float) args[0], (float) args[1], (float) args[2], (float) args[3]);
                } else if (method.getName().equals("draw")) {
                    if (count == 0) firstDustRed = color.r;
                    if (count == 24) firstMoteGreen = color.g;
                    int offset = count++ * STRIDE;
                    float width = (float) args[3], height = (float) args[4];
                    values[offset] = (float) args[1] + width / 2;
                    values[offset + 1] = (float) args[2] + height / 2;
                    values[offset + 2] = color.a;
                    values[offset + 3] = width; values[offset + 4] = height;
                } else if (method.getName().startsWith("setBlend")) blendChanges++;
                return null;
            });

        void draw(MenuAmbientParticles field) { draw(field, 0, 0, 1920, 1080); }
        void draw(MenuAmbientParticles field, float x, float y, float width, float height) {
            count = 0;
            field.drawDust(batch, point, x, y, width, height);
            field.drawOrbMotes(batch, point, x, y, width, height);
        }
    }
}
