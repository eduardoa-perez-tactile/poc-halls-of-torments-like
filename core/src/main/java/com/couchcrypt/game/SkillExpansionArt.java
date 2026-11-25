package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.couchcrypt.ui.BuildIcons;

/** Shared resident art over combat-owned positions; neither camera advances effects. */
final class SkillExpansionArt {
    private final TextureRegion seal, frost, ward, blade, glaive, harvest, stasis, glow;
    private final TextureRegion echo, shatter, reap;

    SkillExpansionArt(BuildIcons icons, TextureRegion glow) {
        seal = icons.regions[PartyProgression.Upgrade.CONSECRATION.ordinal()];
        frost = icons.regions[PartyProgression.Upgrade.FROST.ordinal()];
        ward = icons.regions[PartyProgression.Upgrade.WARD.ordinal()];
        echo = icons.regions[PartyProgression.Upgrade.ECHO_STORM.ordinal()];
        shatter = icons.regions[PartyProgression.Upgrade.SHATTER_BOLT.ordinal()];
        reap = icons.regions[PartyProgression.Upgrade.REAPING_STROKE.ordinal()];
        blade = icons.sentinelBlade; glaive = icons.glaiveWeapon;
        harvest = icons.soulHarvestRelic; stasis = icons.stasisRelic;
        this.glow = glow;
    }

    /** Friendly fields stay faint and flat, before hostile warnings and all bodies. */
    void ground(SpriteBatch batch, ArenaWorld world) {
        for (SkillExpansion.GroundField field : world.skills.fields) {
            if (!field.active) continue;
            float fade = Math.min(1f, field.ttl / .45f);
            float radius = field.radius;
            accent(batch, field.owner, .32f * fade);
            batch.draw(seal, sx(field.x, field.y) - radius, sy(field.x, field.y) - radius * .5f,
                radius * 2, radius);
        }
        for (ArenaWorld.Enemy enemy : world.enemies) {
            if (!enemy.active || enemy.slowTime <= 0) continue;
            batch.setColor(.60f, .86f, 1f, .4f * Math.min(1f, enemy.slowTime / .25f));
            batch.draw(frost, sx(enemy.x, enemy.y) - 11f, sy(enemy.x, enemy.y) - 5.5f, 22f, 11f);
        }
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) {
            if (!strike.active || strike.kind == SkillExpansion.StrikeKind.REAP) continue;
            float fade = strike.fired ? MathUtils.clamp(strike.ttl / .22f, 0, 1) : .2f;
            TextureRegion glyph = strike.kind == SkillExpansion.StrikeKind.ECHO ? echo : shatter;
            float radius = Math.min(52f, strike.radius) * .70710678f;
            accent(batch, strike.owner, .6f * fade);
            batch.draw(glyph, sx(strike.x, strike.y) - radius, sy(strike.x, strike.y) - radius * .5f,
                radius * 2, radius);
        }
        for (SkillExpansion.WorldRelic relic : world.skills.relics) if (relic.active) {
            boolean cold = relic.kind == SkillExpansion.RelicKind.STASIS;
            batch.setColor(cold ? .40f : .76f, cold ? .77f : .90f, cold ? 1f : .55f, .25f);
            batch.draw(glow, sx(relic.x, relic.y) - 23f, sy(relic.x, relic.y) - 11.5f, 46f, 23f);
        }
        batch.setColor(Color.WHITE);
    }

    void orbiter(SpriteBatch batch, SkillExpansion.OrbitBlade orbit) {
        accent(batch, orbit.owner, .95f);
        rotated(batch, blade, sx(orbit.x, orbit.y), sy(orbit.x, orbit.y) + 26f,
            36f, orbit.angle * MathUtils.radiansToDegrees + 45f);
        batch.setColor(Color.WHITE);
    }

    void glaive(SpriteBatch batch, SkillExpansion.Glaive weapon) {
        accent(batch, weapon.owner, 1f);
        rotated(batch, glaive, sx(weapon.x, weapon.y), sy(weapon.x, weapon.y) + 28f,
            43f, weapon.angle * MathUtils.radiansToDegrees);
        batch.setColor(Color.WHITE);
    }

    void relic(SpriteBatch batch, SkillExpansion.WorldRelic relic) {
        batch.setColor(Color.WHITE);
        float y = sy(relic.x, relic.y) + 20f + MathUtils.sin(relic.age * 1.7f) * 3f;
        TextureRegion icon = relic.kind == SkillExpansion.RelicKind.STASIS ? stasis : harvest;
        batch.draw(icon, sx(relic.x, relic.y) - 20f, y - 20f, 40f, 40f);
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

    /** Follow-up strike remains compact regardless of acquired reach, like the main sword trail. */
    void followups(SpriteBatch batch, ArenaWorld world) {
        for (SkillExpansion.DelayedStrike strike : world.skills.delayed) {
            if (!strike.active || !strike.fired || strike.kind != SkillExpansion.StrikeKind.REAP) continue;
            float fade = MathUtils.clamp(strike.ttl / .22f, 0, 1);
            float x = strike.x + strike.dirX * 36f, y = strike.y + strike.dirY * 36f;
            accent(batch, strike.owner, .55f * fade);
            rotated(batch, reap, sx(x, y), sy(x, y) + 35f, 54f,
                MathUtils.atan2(sy(strike.dirX, strike.dirY), sx(strike.dirX, strike.dirY)) * MathUtils.radiansToDegrees);
        }
        batch.setColor(Color.WHITE);
    }

    private static void rotated(SpriteBatch batch, TextureRegion region, float x, float y, float size, float angle) {
        batch.draw(region, x - size * .5f, y - size * .5f, size * .5f, size * .5f,
            size, size, 1f, 1f, angle);
    }

    private static void accent(SpriteBatch batch, int owner, float alpha) {
        if (owner == 0) batch.setColor(1f, .90f, .69f, alpha);
        else batch.setColor(.64f, .97f, .94f, alpha);
    }

    private static float sx(float x, float y) { return (x - y) * .70710678f; }
    private static float sy(float x, float y) { return (x + y) * .35355339f; }
}
