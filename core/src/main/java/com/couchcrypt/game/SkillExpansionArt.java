package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.couchcrypt.ui.BuildIcons;

/** Resident painted art projected through the arena plane; all poses/clocks remain combat-owned. */
final class SkillExpansionArt {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    private static final float BLADE_HEIGHT = 26f, GLAIVE_HEIGHT = 28f;
    private final TextureRegion seal, frost, ward, blade, glaive, harvest, stasis, glow, shadow, pixel;
    private final TextureRegion echo, shatter;
    private final SpriteEffectArt effects;
    private final RingGeometry.Small auraRim;
    private final float[] vertices = new float[20];

    SkillExpansionArt(BuildIcons icons, TextureRegion pixel, TextureRegion glow, TextureRegion shadow,
                      SpriteEffectArt effects) {
        seal = icons.regions[PartyProgression.Upgrade.CONSECRATION.ordinal()];
        frost = icons.regions[PartyProgression.Upgrade.FROST.ordinal()];
        ward = icons.regions[PartyProgression.Upgrade.WARD.ordinal()];
        echo = icons.regions[PartyProgression.Upgrade.ECHO_STORM.ordinal()];
        shatter = icons.regions[PartyProgression.Upgrade.SHATTER_BOLT.ordinal()];
        blade = icons.sentinelBlade; glaive = icons.glaiveWeapon;
        harvest = icons.soulHarvestRelic; stasis = icons.stasisRelic;
        this.pixel = pixel; this.glow = glow; this.shadow = shadow; this.effects = effects;
        auraRim = new RingGeometry.Small((pixel.getU() + pixel.getU2()) * .5f,
            (pixel.getV() + pixel.getV2()) * .5f);
    }

    /** Readable sustained fields and contact shadows precede hostile warnings and all bodies. */
    void ground(SpriteBatch batch, ArenaWorld world, OrthographicCamera camera) {
        // Group light, paint and rims by resident texture instead of switching pages per field.
        for (int pass = 0; pass < 3; pass++) for (SkillExpansion.GroundField field : world.skills.fields) {
            if (!field.active || !visible(camera, sx(field.x, field.y), sy(field.x, field.y),
                field.radius + RingGeometry.STROKE_MARGIN, field.radius * .5f + RingGeometry.STROKE_MARGIN)) continue;
            float fade = Math.min(1f, field.ttl / .45f);
            float radius = field.radius, x = sx(field.x, field.y), y = sy(field.x, field.y);
            // The light and engraving stay inside the existing damaging footprint, with no pulse clock.
            if (pass == 0) {
                accent(batch, field.owner, .18f * fade);
                batch.draw(glow, x - radius, y - radius * .5f, radius * 2, radius);
            } else if (pass == 1) {
                accent(batch, field.owner, .64f * fade);
                batch.draw(seal, x - radius, y - radius * .5f, radius * 2, radius);
            } else {
                float red = field.owner == 0 ? 1f : .57f;
                float green = field.owner == 0 ? .87f : 1f;
                float blue = field.owner == 0 ? .57f : .94f;
                auraRim.update(x, y, radius, red, green, blue, .78f * fade);
                batch.draw(pixel.getTexture(), auraRim.vertices, 0, auraRim.vertices.length);
            }
        }
        for (ArenaWorld.Enemy enemy : world.enemies) {
            if (!enemy.active || enemy.slowTime <= 0
                || !visible(camera, sx(enemy.x, enemy.y), sy(enemy.x, enemy.y), 11f, 5.5f)) continue;
            batch.setColor(.60f, .86f, 1f, .4f * Math.min(1f, enemy.slowTime / .25f));
            batch.draw(frost, sx(enemy.x, enemy.y) - 11f, sy(enemy.x, enemy.y) - 5.5f, 22f, 11f);
        }
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) {
            if (!strike.active || strike.kind == SkillExpansion.StrikeKind.REAP) continue;
            float fade = strike.fired ? MathUtils.clamp(strike.ttl / .22f, 0, 1) : .2f;
            TextureRegion glyph = strike.kind == SkillExpansion.StrikeKind.ECHO ? echo : shatter;
            float radius = Math.min(52f, strike.radius) * ISO_X;
            if (!visible(camera, sx(strike.x, strike.y), sy(strike.x, strike.y), radius, radius * .5f)) continue;
            accent(batch, strike.owner, .72f * fade);
            batch.draw(glyph, sx(strike.x, strike.y) - radius, sy(strike.x, strike.y) - radius * .5f,
                radius * 2, radius);
        }
        for (SkillExpansion.OrbitBlade weapon : world.skills.orbiters) if (weapon.active
            && visible(camera, sx(weapon.x, weapon.y), sy(weapon.x, weapon.y), 24f, 12f))
            contactShadow(batch, weapon.x, weapon.y, 19f, .34f);
        for (SkillExpansion.Glaive weapon : world.skills.glaives) if (weapon.active
            && visible(camera, sx(weapon.x, weapon.y), sy(weapon.x, weapon.y), 24f, 12f))
            contactShadow(batch, weapon.x, weapon.y, 25f, .38f);
        for (SkillExpansion.WorldRelic relic : world.skills.relics) if (relic.active
            && visible(camera, sx(relic.x, relic.y), sy(relic.x, relic.y), 23f, 11.5f)) {
            contactShadow(batch, relic.x, relic.y, 27f, .54f);
            boolean cold = relic.kind == SkillExpansion.RelicKind.STASIS;
            batch.setColor(cold ? .40f : 1f, cold ? .90f : .82f, cold ? 1f : .42f, .48f);
            batch.draw(glow, sx(relic.x, relic.y) - 23f, sy(relic.x, relic.y) - 11.5f, 46f, 23f);
        }
        batch.setColor(Color.WHITE);
    }

    void orbiter(SpriteBatch batch, SkillExpansion.OrbitBlade orbit) {
        weaponTrail(batch, orbit.history, BLADE_HEIGHT, orbit.owner, .65f);
        // The source sword points up: its long axis is tangent to the actual orbit.
        accent(batch, orbit.owner, 1f);
        projectedWeapon(batch, blade, orbit.x, orbit.y, BLADE_HEIGHT, 46f, orbit.angle);
        batch.setColor(Color.WHITE);
    }

    void glaive(SpriteBatch batch, SkillExpansion.Glaive weapon) {
        weaponTrail(batch, weapon.history, GLAIVE_HEIGHT, weapon.owner, .72f);
        // Two low-opacity real past poses describe spin, without inventing a future return path.
        for (int i = Math.min(6, weapon.history.count - 1); i >= 2; i -= 2) {
            accent(batch, weapon.owner, .18f * (1f - i / 7f));
            projectedWeapon(batch, glaive, weapon.history.x[i], weapon.history.y[i],
                GLAIVE_HEIGHT, 54f, weapon.history.angle[i]);
        }
        accent(batch, weapon.owner, 1f);
        projectedWeapon(batch, glaive, weapon.x, weapon.y, GLAIVE_HEIGHT, 54f, weapon.angle);
        batch.setColor(Color.WHITE);
    }

    /** Low hovering props retain upright crystal/metal, seated over their projected contact light. */
    void relic(SpriteBatch batch, SkillExpansion.WorldRelic relic) {
        batch.setColor(Color.WHITE);
        float lift = 14f + MathUtils.sin(relic.age * 1.7f) * 1.5f;
        TextureRegion icon = relic.kind == SkillExpansion.RelicKind.STASIS ? stasis : harvest;
        // A shallow three-quarter lean, keeping vertical facets upright instead of flattening a solid prop.
        affine(batch, icon, sx(relic.x, relic.y), sy(relic.x, relic.y) + lift,
            .94f, .10f, -.10f, .86f, 40f);
    }

    /** Small shield at the owner's depth, never a screen overlay or a new HUD row. */
    void ward(SpriteBatch batch, ArenaWorld.Player player) {
        if (player.wardLevel <= 0 || !player.alive) return;
        boolean ready = player.wardCooldown <= 0;
        if (!ready && player.wardFlash <= 0) return;
        float flash = MathUtils.clamp(player.wardFlash / .25f, 0, 1);
        accent(batch, player.slot, ready ? .68f : flash * .9f);
        float size = 28f + flash * 8f;
        batch.draw(ward, sx(player.x, player.y) + 18f - size * .5f,
            sy(player.x, player.y) + 59f - size * .5f, size, size);
        batch.setColor(Color.WHITE);
    }

    /** Reuse the approved painted cutting edge, flat beneath actors, instead of the skill-card emblem. */
    void followups(SpriteBatch batch, ArenaWorld world, OrthographicCamera camera) {
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) {
            if (!strike.active || !strike.fired || strike.kind != SkillExpansion.StrikeKind.REAP) continue;
            float fade = MathUtils.clamp(strike.ttl / .22f, 0, 1);
            float x = strike.x + strike.dirX * 36f, y = strike.y + strike.dirY * 36f;
            if (!visible(camera, sx(x, y), sy(x, y), 62f, 32f)) continue;
            accent(batch, strike.owner, .78f * fade);
            effects.drawGround(batch, SpriteEffectArt.Type.SWORD_SWEEP, 0, sx(x, y), sy(x, y),
                48f, MathUtils.atan2(strike.dirY, strike.dirX) + MathUtils.PI * .5f,
                SpriteEffectArt.Layer.ALL);
        }
        batch.setColor(Color.WHITE);
    }

    private void contactShadow(SpriteBatch batch, float x, float y, float width, float alpha) {
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(shadow, sx(x, y) - width * .5f, sy(x, y) - width * .25f, width, width * .5f);
    }

    /** Short tapered ribbon samples only real past positions, shared by the two camera draws. */
    private void weaponTrail(SpriteBatch batch, SkillExpansion.PoseHistory history, float height, int owner,
                             float peakAlpha) {
        for (int i = history.count - 1; i > 0; i--) {
            float x1 = sx(history.x[i], history.y[i]), y1 = sy(history.x[i], history.y[i]) + height;
            float x2 = sx(history.x[i - 1], history.y[i - 1]), y2 = sy(history.x[i - 1], history.y[i - 1]) + height;
            float dx = x2 - x1, dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < .001f) continue;
            float oldWeight = 1f - i / 6f, newWeight = 1f - (i - 1) / 6f;
            float nx = -dy / length, ny = dx / length;
            // Broad low-alpha shoulder plus a fine ivory core, both fading to the recorded oldest point.
            ribbon(batch, x1, y1, x2, y2, nx, ny, .9f * oldWeight, .9f * newWeight,
                owner, peakAlpha * oldWeight * .23f, peakAlpha * newWeight * .23f);
            ribbon(batch, x1, y1, x2, y2, nx, ny, .35f * oldWeight, .35f * newWeight,
                owner, peakAlpha * oldWeight, peakAlpha * newWeight);
        }
    }

    private void ribbon(SpriteBatch batch, float x1, float y1, float x2, float y2, float nx, float ny,
                        float w1, float w2, int owner, float a1, float a2) {
        float u = (pixel.getU() + pixel.getU2()) * .5f, v = (pixel.getV() + pixel.getV2()) * .5f;
        float c1 = trailColor(owner, a1), c2 = trailColor(owner, a2);
        vertex(0, x1 - nx * w1, y1 - ny * w1, c1, u, v);
        vertex(5, x1 + nx * w1, y1 + ny * w1, c1, u, v);
        vertex(10, x2 + nx * w2, y2 + ny * w2, c2, u, v);
        vertex(15, x2 - nx * w2, y2 - ny * w2, c2, u, v);
        batch.draw(pixel.getTexture(), vertices, 0, vertices.length);
    }

    private static float trailColor(int owner, float alpha) {
        return owner == 0 ? Color.toFloatBits(1f, .91f, .63f, alpha)
            : Color.toFloatBits(.65f, 1f, .95f, alpha);
    }

    /** Rotate in world space BEFORE projection, keeping weapon height separate from the floor axes. */
    private void projectedWeapon(SpriteBatch batch, TextureRegion region, float x, float y, float height,
                                 float size, float angle) {
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        affine(batch, region, sx(x, y), sy(x, y) + height,
            (c - s) * ISO_X, (-s - c) * ISO_X, (c + s) * ISO_Y, (c - s) * ISO_Y, size);
    }

    private void affine(SpriteBatch batch, TextureRegion region, float x, float y,
                        float a, float b, float c, float d, float size) {
        float r = size * .5f, color = batch.getPackedColor();
        vertex(0, x - a * r - b * r, y - c * r - d * r, color, region.getU(), region.getV2());
        vertex(5, x - a * r + b * r, y - c * r + d * r, color, region.getU(), region.getV());
        vertex(10, x + a * r + b * r, y + c * r + d * r, color, region.getU2(), region.getV());
        vertex(15, x + a * r - b * r, y + c * r - d * r, color, region.getU2(), region.getV2());
        batch.draw(region.getTexture(), vertices, 0, vertices.length);
    }

    private void vertex(int at, float x, float y, float color, float u, float v) {
        vertices[at] = x; vertices[at + 1] = y; vertices[at + 2] = color;
        vertices[at + 3] = u; vertices[at + 4] = v;
    }

    private static void accent(SpriteBatch batch, int owner, float alpha) {
        if (owner == 0) batch.setColor(1f, .90f, .69f, alpha);
        else batch.setColor(.64f, .97f, .94f, alpha);
    }

    static boolean visible(OrthographicCamera camera, float x, float y, float halfWidth, float halfHeight) {
        return Math.abs(x - camera.position.x) <= camera.viewportWidth * camera.zoom * .5f + halfWidth + 1f
            && Math.abs(y - camera.position.y) <= camera.viewportHeight * camera.zoom * .5f + halfHeight + 1f;
    }

    private static float sx(float x, float y) { return (x - y) * ISO_X; }
    private static float sy(float x, float y) { return (x + y) * ISO_Y; }
}
