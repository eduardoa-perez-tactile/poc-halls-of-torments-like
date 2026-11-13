package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.Random;

/** Small, deterministic menu atmosphere sharing the scene's existing soft-point atlas region. */
final class MenuAmbientParticles {
    private static final int DUST_COUNT = 24, MOTES_PER_ORB = 6;
    private static final float DESIGN_WIDTH = 1920, DESIGN_HEIGHT = 1080;
    private static final double TAU = Math.PI * 2;
    private final Particle[] dust = new Particle[DUST_COUNT];
    private final Particle[] motes = new Particle[MOTES_PER_ORB * 2];

    MenuAmbientParticles(float leftOrbX, float rightOrbX, float orbY) {
        Random seed = new Random(0x43525950544cL);
        int side = 0;
        for (int i = 0; i < dust.length; i++) {
            // Most dust stays around the architecture. Six quieter points cross the menu area.
            boolean center = i % 4 == 0;
            boolean foreground = i % 6 == 2;
            float anchorX = center ? .34f + seed.nextFloat() * .32f
                : (side++ % 2 == 0 ? .06f : .71f) + seed.nextFloat() * .23f;
            dust[i] = new Particle(anchorX, 0, seed.nextDouble(), 60 + seed.nextDouble() * 60,
                seed.nextDouble() * TAU, 22 + seed.nextFloat() * 24,
                (foreground ? 9f : 6f) + seed.nextFloat() * (foreground ? 3f : 2f),
                center ? .16f : .48f + seed.nextFloat() * .12f, 1, foreground ? .28f : .9f);
            updateDust(dust[i]);
        }
        for (int i = 0; i < motes.length; i++) {
            motes[i] = new Particle(i < MOTES_PER_ORB ? leftOrbX : rightOrbX, orbY,
                seed.nextDouble(), 8 + seed.nextDouble() * 6, seed.nextDouble() * TAU,
                39 + seed.nextFloat() * 23, 12f + seed.nextFloat() * 4f,
                .86f + seed.nextFloat() * .12f, i % 2 == 0 ? 1 : -1, 0);
            updateMote(motes[i]);
        }
    }

    /** Ignore invalid/paused deltas and cap long frames at 100ms. */
    void advance(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        double step = Math.min(delta, .1f);
        for (Particle point : dust) {
            advancePhase(point, step);
            updateDust(point);
        }
        for (Particle point : motes) {
            advancePhase(point, step);
            updateMote(point);
        }
    }

    private static void advancePhase(Particle point, double step) {
        point.phase += step / point.period;
        // Each phase stays bounded for arbitrarily long uptime; nothing is randomly respawned.
        if (point.phase >= 1) point.phase -= 1;
    }

    private static void updateDust(Particle point) {
        double angle = point.phase * TAU;
        point.x = point.anchorX + (float) Math.sin(angle + point.offset) * point.radius / DESIGN_WIDTH;
        point.y = .05f + (float) point.phase * point.verticalSpan;
        double fade = Math.sin(Math.PI * point.phase);
        point.alpha = (float) (point.opacity * fade * fade);
    }

    private static void updateMote(Particle point) {
        double angle = point.phase * TAU;
        double radius = point.radius + Math.sin(angle + point.offset) * 3;
        point.x = point.anchorX + (float) (Math.cos(angle * point.direction) * radius / DESIGN_WIDTH);
        point.y = point.anchorY + (float) (Math.sin(angle * point.direction) * radius / DESIGN_HEIGHT);
        point.alpha = point.opacity * (float) (.90 + .10 * Math.sin(angle + point.offset));
    }

    void drawDust(Batch batch, TextureRegion point, float x, float y, float width, float height) {
        draw(batch, point, dust, x, y, width, height, .91f, .89f, .82f);
    }

    void drawOrbMotes(Batch batch, TextureRegion point, float x, float y, float width, float height) {
        draw(batch, point, motes, x, y, width, height, 1f, .90f, .60f);
    }

    private static void draw(Batch batch, TextureRegion region, Particle[] points,
                             float x, float y, float width, float height, float tintR, float tintG, float tintB) {
        Color color = batch.getColor();
        float red = color.r, green = color.g, blue = color.b, alpha = color.a;
        for (Particle point : points) {
            float pointWidth = point.size * width / DESIGN_WIDTH;
            float pointHeight = point.size * height / DESIGN_HEIGHT;
            batch.setColor(red * tintR, green * tintG, blue * tintB, alpha * point.alpha);
            batch.draw(region, x + point.x * width - pointWidth / 2,
                y + point.y * height - pointHeight / 2, pointWidth, pointHeight);
        }
        batch.setColor(red, green, blue, alpha);
    }

    private static final class Particle {
        final float anchorX, anchorY, radius, size, opacity, verticalSpan;
        final double period, offset;
        final int direction;
        double phase;
        float x, y, alpha;

        Particle(float anchorX, float anchorY, double phase, double period, double offset,
                 float radius, float size, float opacity, int direction, float verticalSpan) {
            this.anchorX = anchorX; this.anchorY = anchorY; this.phase = phase; this.period = period;
            this.offset = offset; this.radius = radius; this.size = size; this.opacity = opacity;
            this.direction = direction;
            this.verticalSpan = verticalSpan;
        }
    }
}
