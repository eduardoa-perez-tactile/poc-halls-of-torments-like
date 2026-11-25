package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/** Small deterministic world-space ambient field; the combat clock owns all motion. */
final class ArenaAmbience {
    static final int DUST_COUNT = 160, SOUL_COUNT = 12;
    private static final int DUST_STRIDE = 10, SOUL_STRIDE = 8;
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    private final float[] dust = new float[DUST_COUNT * DUST_STRIDE];
    private final float[] souls = new float[SOUL_COUNT * SOUL_STRIDE];

    ArenaAmbience(ArenaFloorLayout floor, ArenaLayout layout) {
        for (int i = 0; i < DUST_COUNT; i++) {
            int at = i * DUST_STRIDE;
            dust[at] = random(i, 0) * ArenaWorld.HALF_WIDTH * 2;
            dust[at + 1] = random(i, 1) * ArenaWorld.HALF_HEIGHT * 2;
            dust[at + 2] = random(i, 2);
            dust[at + 3] = 15f + random(i, 3) * 14f;
            dust[at + 4] = 1.4f + random(i, 4) * 3.2f;
            dust[at + 5] = -.9f + random(i, 5) * 1.8f;
            dust[at + 6] = 18f + random(i, 6) * 55f;
            dust[at + 7] = 1.25f + random(i, 7) * 1.1f;
            dust[at + 8] = .30f + random(i, 8) * .20f;
            dust[at + 9] = 4f + random(i, 9) * 8f;
        }
        // Six very small ivory motes over the original seal, one near each of the six tombs.
        int count = 0;
        for (; count < 6; count++) {
            float angle = MathUtils.PI2 * count / 6f + .35f;
            float radius = floor.medallionDiameter * (.18f + random(count, 10) * .23f);
            soul(count, floor.centerX + MathUtils.cos(angle) * radius,
                floor.centerY + MathUtils.sin(angle) * radius);
        }
        for (ArenaLayout.Placement placement : layout.placements) {
            if (count == SOUL_COUNT) break;
            if (!placement.region.startsWith("sarcophagus")) continue;
            // Float beside the stone rather than sitting where a collectible would rest.
            soul(count++, placement.x + 45f, placement.y - 55f);
        }
        // Keep the bounded field valid if a future authored layout contains fewer tombs.
        for (; count < SOUL_COUNT; count++) soul(count, floor.centerX, floor.centerY);
    }

    private void soul(int index, float x, float y) {
        int at = index * SOUL_STRIDE;
        souls[at] = x; souls[at + 1] = y;
        souls[at + 2] = random(index, 11);
        souls[at + 3] = 20f + random(index, 12) * 12f;
        souls[at + 4] = 40f + random(index, 13) * 25f;
        souls[at + 5] = 1.7f + random(index, 14) * .6f;
        souls[at + 6] = .38f + random(index, 15) * .13f;
        souls[at + 7] = 8f + random(index, 16) * 10f;
    }

    void draw(SpriteBatch batch, GameArt art, OrthographicCamera camera, float combatTime) {
        float width = ArenaWorld.HALF_WIDTH * 2, height = ArenaWorld.HALF_HEIGHT * 2;
        for (int i = 0; i < DUST_COUNT; i++) {
            int at = i * DUST_STRIDE;
            float phase = (dust[at + 2] + combatTime / dust[at + 3]) % 1f;
            float x = wrap(dust[at] + combatTime * dust[at + 4], width) - ArenaWorld.HALF_WIDTH;
            float y = wrap(dust[at + 1] + combatTime * dust[at + 5], height) - ArenaWorld.HALF_HEIGHT;
            float sway = MathUtils.sin((phase * 1.4f + dust[at + 2]) * MathUtils.PI2) * dust[at + 9];
            float sx = (x - y) * ISO_X + sway;
            float sy = (x + y) * ISO_Y + dust[at + 6] + phase * 85f;
            if (!visible(camera, sx, sy, 8f)) continue;
            float edge = Math.min(1f, Math.min(ArenaWorld.HALF_WIDTH - Math.abs(x),
                ArenaWorld.HALF_HEIGHT - Math.abs(y)) / 60f);
            float alpha = dust[at + 8] * fade(phase) * edge;
            float size = dust[at + 7];
            batch.setColor(.72f, .76f, .79f, alpha * .36f);
            batch.draw(art.glow, sx - size * 2f, sy - size * 2f, size * 4f, size * 4f);
            batch.setColor(.84f, .83f, .77f, alpha);
            batch.draw(art.pixel, sx - size * .5f, sy - size * .5f, size, size * .8f);
        }
        for (int i = 0; i < SOUL_COUNT; i++) {
            int at = i * SOUL_STRIDE;
            float phase = (souls[at + 2] + combatTime / souls[at + 3]) % 1f;
            float drift = MathUtils.sin((phase + souls[at + 2]) * MathUtils.PI2) * souls[at + 7];
            float sx = (souls[at] - souls[at + 1]) * ISO_X + drift;
            float sy = (souls[at] + souls[at + 1]) * ISO_Y + souls[at + 4] + phase * 70f;
            if (!visible(camera, sx, sy, 9f)) continue;
            float alpha = souls[at + 6] * fade(phase), size = souls[at + 5];
            batch.setColor(1f, .94f, .79f, alpha * .32f);
            batch.draw(art.glow, sx - 7f, sy - 7f, 14f, 14f);
            batch.setColor(1f, .97f, .87f, alpha);
            batch.draw(art.pixel, sx - size * .5f, sy - size * .5f, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    private static float fade(float phase) { return Math.min(1f, Math.min(phase, 1f - phase) * 7f); }
    private static float wrap(float value, float length) { return (value % length + length) % length; }
    private static boolean visible(OrthographicCamera camera, float x, float y, float margin) {
        return Math.abs(x - camera.position.x) <= camera.viewportWidth * .5f + margin
            && Math.abs(y - camera.position.y) <= camera.viewportHeight * .5f + margin;
    }
    private static float random(int index, int channel) {
        int hash = (index + 1) * 0x45d9f3b ^ (channel + 1) * 0x27d4eb2d;
        hash ^= hash >>> 16; hash *= 0x45d9f3b; hash ^= hash >>> 16;
        return (hash & 0xffff) / 65535f;
    }
}
