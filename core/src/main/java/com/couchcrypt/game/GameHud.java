package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.couchcrypt.ui.BuildIcons;
import com.couchcrypt.ui.UiArt;

/** Compact shared-XP HUD. All art is borrowed, text cached, and render work bounded. */
public final class GameHud {
    private static final Color TRACK = Color.valueOf("252025");
    private static final Color WARNING = Color.valueOf("e5a56a");
    private static final Color CRITICAL = Color.valueOf("f36d51");
    private static final Color SOUL = Color.valueOf("9bc3bb");
    private static final float ORB_SIZE = 224f, ORB_Y = 104f, ORB_LEFT = 32f;
    private static final float ORB_SCALE = ORB_SIZE / HudArt.ORB_SIZE;
    private static final float SKILL_Y = 100f, SKILL_SIZE = 88f, SKILL_STEP = 96f, SKILL_LEFT = 376f;
    private static final float DODGE_Y = 104f, DODGE_SIZE = 80f, DODGE_LEFT = 272f;
    private static final float CRITICAL_HEALTH = .2f, PULSE_SECONDS = 1.6f;
    private static final float BOSS_X = 288f, BOSS_Y = 928f, BOSS_WIDTH = 1344f, BOSS_HEIGHT = 86f;
    private static final float BOSS_SCALE_X = BOSS_WIDTH / 512f, BOSS_SCALE_Y = BOSS_HEIGHT / 96f;
    private static final int ARC_SEGMENTS = 48;
    private final GameArt art;
    private final HudArt hudArt;
    private final TextureRegion[] icons;
    private final PlayerHudModel[] builds = {new PlayerHudModel(), new PlayerHudModel()};
    private final Text[] dodge = new Text[2], playerLabels = new Text[2], overflow = new Text[2], revivalStatus = new Text[2];
    private final Text[][] ranks = new Text[2][PlayerHudModel.MAX_SKILLS];
    private final Text[] allyDown = new Text[2], revival = new Text[2];
    private final Text timer, bossName, partyLevel, lowHealth, relicNotice;
    private final StringBuilder builder = new StringBuilder(64);
    private final boolean[] wasActive = new boolean[2], wasAlive = new boolean[2];
    private float refresh, pulseClock;
    private int seenRelics;
    private float relicNoticeUntil;

    public GameHud(GameArt art, UiArt ui, BuildIcons buildIcons, HudArt hudArt) {
        this.art = art; this.hudArt = hudArt; icons = buildIcons.regions;
        for (int slot = 0; slot < 2; slot++) {
            Color accent = slot == 0 ? UiArt.GOLD : SOUL;
            dodge[slot] = new Text(ui.body, 1f, 76f, UiArt.IVORY);
            revivalStatus[slot] = new Text(ui.small, .8f, 140f, accent);
            overflow[slot] = new Text(ui.small, 1.1f, 46f, accent);
            playerLabels[slot] = new Text(ui.small, 1f, 96f, accent);
            playerLabels[slot].set(slot == 0 ? "P1" : "P2");
            allyDown[slot] = new Text(ui.small, 1f, 170f, WARNING);
            allyDown[slot].set(slot == 0 ? "P2 DOWN" : "P1 DOWN");
            revival[slot] = new Text(ui.small, 1f, 320f, accent);
            for (int i = 0; i < ranks[slot].length; i++) ranks[slot][i] = new Text(ui.body, 1f, 30f, UiArt.IVORY);
        }
        lowHealth = new Text(ui.small, .85f, 180f, CRITICAL); lowHealth.set("LOW HEALTH");
        partyLevel = new Text(ui.small, .9f, 480f, UiArt.GOLD);
        timer = new Text(ui.section, 1.15f, 160f, UiArt.IVORY);
        bossName = new Text(ui.heading, .78f, 1240f, UiArt.IVORY);
        relicNotice = new Text(ui.small, .9f, 750f, UiArt.IVORY);
    }

    public void reset() {
        refresh = pulseClock = 0;
        seenRelics = 0; relicNoticeUntil = 0; relicNotice.set("");
        wasActive[0] = wasActive[1] = false;
        wasAlive[0] = wasAlive[1] = false;
    }

    /** Atlas art first, shared skill emblems second, then cached font glyphs. */
    public void render(SpriteBatch batch, ArenaWorld world, ArenaHudState state, boolean split, float delta) {
        float elapsed = Math.max(0, delta);
        refresh -= elapsed;
        // The caller supplies zero while manually paused; the local warning freezes with the HUD.
        pulseClock = (pulseClock + elapsed) % PULSE_SECONDS;
        boolean changed = false;
        for (int slot = 0; slot < 2; slot++) {
            changed |= wasActive[slot] != world.players[slot].active || wasAlive[slot] != world.players[slot].alive;
            wasActive[slot] = world.players[slot].active; wasAlive[slot] = world.players[slot].alive;
        }
        if (refresh <= 0 || changed) { refreshText(world); refresh = .2f; }
        if (seenRelics != world.skills.relicsCollected) {
            seenRelics = world.skills.relicsCollected;
            relicNoticeUntil = world.elapsed + 2.5f;
            relicNotice.set(world.skills.lastRelicKind == SkillExpansion.RelicKind.STASIS
                ? "STASIS  /  ENEMIES SLOWED" : "SOUL HARVEST  /  GATHERING SOULS");
        }
        for (ArenaWorld.Player player : world.players) if (player.active) {
            boolean mirror = split && player.slot == 1;
            batch.setColor(0, 0, 0, .64f);
            batch.draw(art.shadow, mirror ? 1020f : 12f, 76f, 888f, 276f);
        }
        batch.setColor(0, 0, 0, .35f); batch.draw(art.shadow, 40f, 16f, 1840f, 90f);
        if (split) {
            batch.setColor(0, 0, 0, .45f); batch.draw(art.pixel, 959.5f, 0, 1, 1080);
        }
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerArt(batch, player, world, split && player.slot == 1);
        // A single party XP rail has a permanent bottom lane, including during the boss fight.
        float xp = world.progression.xpToNext > 0
            ? MathUtils.clamp((float) world.progression.xp / world.progression.xpToNext, 0, 1) : 1f;
        rect(batch, 60f, 46f, 1800f, 8f, Color.BLACK, .65f);
        rect(batch, 60f, 48f, 1800f, 4f, TRACK, .9f);
        if (xp > 0) { batch.setColor(Color.WHITE); batch.draw(hudArt.xpGold, 60f, 48f, 1800f * xp, 4f); }
        if (state.bossVisible) bossArt(batch, state);
        else {
            batch.setColor(0, 0, 0, .38f);
            batch.draw(art.shadow, 860f, 962f, 200f, 100f);
        }
        batch.setColor(Color.WHITE);
        for (ArenaWorld.Player player : world.players) if (player.active) {
            boolean mirror = split && player.slot == 1;
            PlayerHudModel build = builds[player.slot];
            for (int i = 0; i < build.count; i++)
                batch.draw(icons[build.iconIndices[i]], skillX(mirror, i) + 4f, SKILL_Y + 4f, 80f, 80f);
        }
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerText(batch, player, world, split && player.slot == 1);
        partyLevel.center(batch, 960f, 82f);
        if (world.elapsed < relicNoticeUntil) relicNotice.center(batch, 960f, 370f);
        if (state.bossVisible) {
            bossName.set(state.bossName);
            bossName.center(batch, 960f, 1058f);
        } else timer.center(batch, 960f, 1028f);
        batch.setColor(Color.WHITE);
    }

    /** Painted frame and live fill share the same managed HUD page. No baked text or health. */
    private void bossArt(SpriteBatch batch, ArenaHudState state) {
        float x = BOSS_X + HudArt.BOSS_TRACK_X * BOSS_SCALE_X;
        float y = BOSS_Y + HudArt.BOSS_TRACK_Y * BOSS_SCALE_Y;
        float width = HudArt.BOSS_TRACK_WIDTH * BOSS_SCALE_X;
        float height = HudArt.BOSS_TRACK_HEIGHT * BOSS_SCALE_Y;
        rect(batch, x, y, width, height, Color.BLACK, .94f);
        float ratio = MathUtils.clamp(state.bossHealth / Math.max(1f, state.bossMaxHealth), 0, 1);
        if (ratio > 0) {
            batch.setColor(Color.WHITE);
            // Crop the texture rather than compressing its painted detail as health falls.
            TextureRegion fill = hudArt.bossFill;
            batch.draw(fill.getTexture(), x, y, width * ratio, height,
                fill.getU(), fill.getV2(), MathUtils.lerp(fill.getU(), fill.getU2(), ratio), fill.getV());
        }
        batch.setColor(Color.WHITE);
        batch.draw(hudArt.bossFrame, BOSS_X, BOSS_Y, BOSS_WIDTH, BOSS_HEIGHT);
    }

    private void refreshText(ArenaWorld world) {
        int remaining = (int) Math.ceil(world.timeRemaining());
        builder.setLength(0); builder.append(remaining / 60).append(':');
        if (remaining % 60 < 10) builder.append('0');
        timer.set(builder.append(remaining % 60).toString());
        builder.setLength(0); builder.append("PARTY LV ").append(world.progression.level);
        partyLevel.set(builder.append(world.progression.xpToNext > 0 ? "  /  XP" : "  /  MAX LEVEL").toString());
        for (ArenaWorld.Player player : world.players) {
            int slot = player.slot;
            if (!player.alive) {
                builder.setLength(0); dodge[slot].set(player.reviveProgress > 0
                    ? builder.append(Math.min(100, (int) (player.reviveProgress * 50))).append('%').toString() : "DOWN");
                revivalStatus[slot].set(player.reviveProgress > 0 ? "REVIVING" : "AWAIT ALLY");
            } else if (player.dodgeCooldown <= .05f) {
                dodge[slot].set(""); revivalStatus[slot].set("");
            } else {
                int tenths = (int) Math.ceil(player.dodgeCooldown * 10);
                builder.setLength(0); dodge[slot].set(builder.append(tenths / 10).append('.').append(tenths % 10).toString());
                revivalStatus[slot].set("");
            }
            builds[slot].refresh(world, slot);
            for (int i = 0; i < builds[slot].count; i++) {
                builder.setLength(0); ranks[slot][i].set(builder.append(builds[slot].ranks[i]).toString());
            }
            builder.setLength(0); overflow[slot].set(builds[slot].overflow > 0 ? builder.append('+').append(builds[slot].overflow).toString() : "");
            ArenaWorld.Player ally = world.players[1 - slot];
            if (ally.active && !ally.alive) {
                builder.setLength(0);
                if (ally.reviveProgress > 0) builder.append("REVIVING P").append(ally.slot + 1).append("  ")
                    .append(Math.min(100, (int) (ally.reviveProgress * 50))).append('%');
                else builder.append("STAND NEAR P").append(ally.slot + 1).append(" TO REVIVE");
                revival[slot].set(builder.toString());
            }
        }
    }

    private void playerArt(SpriteBatch batch, ArenaWorld.Player player, ArenaWorld world, boolean mirror) {
        boolean teal = player.slot == 1;
        float orbX = mirror ? 1920f - ORB_LEFT - ORB_SIZE : ORB_LEFT;
        float ratio = healthRatio(player, world);
        if (player.alive && ratio <= CRITICAL_HEALTH) {
            float pulse = .45f + .25f * MathUtils.sin(pulseClock / PULSE_SECONDS * MathUtils.PI2);
            batch.setColor(1f, .55f, .4f, pulse);
            batch.draw(hudArt.lowHealthGlow, orbX - 20f, ORB_Y - 20f, ORB_SIZE + 40f, ORB_SIZE + 40f);
        }
        batch.setColor(Color.WHITE); batch.draw(hudArt.orbBack, orbX, ORB_Y, ORB_SIZE, ORB_SIZE);
        if (ratio > 0) {
            TextureRegion blood = hudArt.orbBlood;
            float inset = HudArt.BLOOD_INSET * ORB_SCALE, size = HudArt.BLOOD_SIZE * ORB_SCALE;
            float topV = blood.getV2() - (blood.getV2() - blood.getV()) * ratio;
            batch.draw(blood.getTexture(), orbX + inset, ORB_Y + inset, size, size * ratio,
                blood.getU(), blood.getV2(), blood.getU2(), topV);
            float radius = size * .5f, dy = -radius + size * ratio;
            float chord = (float) Math.sqrt(Math.max(0, radius * radius - dy * dy));
            rect(batch, orbX + ORB_SIZE * .5f - chord, ORB_Y + inset + size * ratio, chord * 2, 1f, WARNING, .55f);
        }
        batch.setColor(Color.WHITE); batch.draw(hudArt.orbReflection, orbX, ORB_Y, ORB_SIZE, ORB_SIZE);
        batch.draw(teal ? hudArt.orbTeal : hudArt.orbGold, orbX, ORB_Y, ORB_SIZE, ORB_SIZE);
        float dodgeX = mirror ? 1920f - DODGE_LEFT - DODGE_SIZE : DODGE_LEFT;
        batch.draw(hudArt.dodgeBack, dodgeX, DODGE_Y, DODGE_SIZE, DODGE_SIZE);
        if (player.alive) {
            float tint = player.dodgeCooldown <= .05f ? 1f : .36f;
            batch.setColor(tint, tint, tint, 1f);
            batch.draw(hudArt.dodgeIcon, dodgeX + 4f, DODGE_Y + 4f, 72f, 72f);
        }
        float ready = player.alive
            ? 1f - MathUtils.clamp(player.dodgeCooldown / world.equipment.dodgeCooldown(player), 0, 1)
            : MathUtils.clamp(player.reviveProgress / 2f, 0, 1);
        arc(batch, dodgeX + 40f, DODGE_Y + 40f, ready, teal ? SOUL : UiArt.GOLD);
        PlayerHudModel build = builds[player.slot];
        for (int i = 0; i < build.count; i++) {
            float x = skillX(mirror, i);
            batch.setColor(Color.WHITE);
            batch.draw(teal ? hudArt.skillTeal : hudArt.skillGold, x, SKILL_Y, SKILL_SIZE, SKILL_SIZE);
            batch.setColor(.45f, .45f, .45f, 1f);
            batch.draw(hudArt.dodgeBack, x + 60f, SKILL_Y - 12f, 32f, 32f);
        }
    }

    private void playerText(SpriteBatch batch, ArenaWorld.Player player, ArenaWorld world, boolean mirror) {
        int slot = player.slot;
        float center = mirror ? 1920f - ORB_LEFT - ORB_SIZE * .5f : ORB_LEFT + ORB_SIZE * .5f;
        float dodgeX = mirror ? 1920f - DODGE_LEFT - DODGE_SIZE * .5f : DODGE_LEFT + DODGE_SIZE * .5f;
        playerLabels[slot].center(batch, center, ORB_Y + ORB_SIZE + 18f);
        if (player.alive && healthRatio(player, world) <= CRITICAL_HEALTH) lowHealth.center(batch, center, 100f);
        dodge[slot].center(batch, dodgeX, DODGE_Y + 52f);
        if (!player.alive) revivalStatus[slot].center(batch, dodgeX, DODGE_Y - 12f);
        PlayerHudModel build = builds[slot];
        for (int i = 0; i < build.count; i++)
            ranks[slot][i].center(batch, skillX(mirror, i) + 76f, SKILL_Y + 14f);
        overflow[slot].center(batch, mirror ? 1026f : 894f, SKILL_Y + 55f);
    }

    private static float skillX(boolean mirror, int index) {
        return mirror ? 1920f - SKILL_LEFT - SKILL_SIZE - index * SKILL_STEP : SKILL_LEFT + index * SKILL_STEP;
    }

    private static float healthRatio(ArenaWorld.Player player, ArenaWorld world) {
        return MathUtils.clamp(player.health / Math.max(1f, world.equipment.maxHealth(player)), 0, 1);
    }

    private void arc(SpriteBatch batch, float cx, float cy, float progress, Color color) {
        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float amount = MathUtils.clamp(progress * ARC_SEGMENTS - i, 0, 1);
            if (amount <= 0) break;
            float angle = 90f - (i + .5f) * 360f / ARC_SEGMENTS;
            float x = cx + MathUtils.cosDeg(angle) * 37f, y = cy + MathUtils.sinDeg(angle) * 37f;
            batch.setColor(color);
            batch.draw(hudArt.pixel, x - 2.8f * amount, y - 1.5f, 2.8f * amount, 1.5f, 5.6f * amount, 3f, 1, 1, angle + 90f);
        }
    }

    private void rail(SpriteBatch batch, float x, float y, float width, float height, float ratio, Color color) {
        rect(batch, x, y - 1f, width, height + 2f, Color.BLACK, .65f);
        rect(batch, x, y, width, height, TRACK, .85f); rect(batch, x, y, width * ratio, height, color, 1f);
    }

    private void rect(SpriteBatch batch, float x, float y, float width, float height, Color color, float alpha) {
        if (width <= 0 || height <= 0) return;
        batch.setColor(color.r, color.g, color.b, alpha); batch.draw(hudArt.pixel, x, y, width, height);
    }

    /** Fallen-ally cues use projected positions supplied by the matching split camera. */
    public void allyGuidance(SpriteBatch batch, ArenaWorld world, int viewer, float allyX, float allyY) {
        ArenaWorld.Player self = world.players[viewer], ally = world.players[1 - viewer];
        if (!self.active || !self.alive || !ally.active || ally.alive) return;
        float paneLeft = viewer == 0 ? 0 : 960f;
        Color accent = ally.slot == 0 ? UiArt.GOLD : SOUL;
        if (allyX < 24f || allyX > 936f || allyY < 24f || allyY > 1056f) {
            // Intersect a ray with a safe inner rectangle; leave the existing HUD lanes clear.
            float dx = allyX - 480f, dy = allyY - 540f;
            float factorX = Math.abs(dx) < .001f ? Float.MAX_VALUE : 424f / Math.abs(dx);
            float factorY = Math.abs(dy) < .001f ? Float.MAX_VALUE : (dy > 0 ? 332f : 230f) / Math.abs(dy);
            float factor = Math.min(factorX, factorY);
            float x = 480f + dx * factor, y = 540f + dy * factor;
            float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
            arrowStroke(batch, paneLeft + x, y, angle + 145f, accent);
            arrowStroke(batch, paneLeft + x, y, angle - 145f, accent);
            float captionX = MathUtils.clamp(x, 110f, 850f);
            allyDown[viewer].center(batch, paneLeft + captionX, y > 540f ? y - 24f : y + 39f);
        } else {
            float x = MathUtils.clamp(allyX, 190f, 770f), y = MathUtils.clamp(allyY + 180f, 310f, 870f);
            revival[viewer].center(batch, paneLeft + x, y);
            if (ally.reviveProgress > 0)
                rail(batch, paneLeft + x - 80f, y - 32f, 160f, 4f, Math.min(1f, ally.reviveProgress / 2f), accent);
        }
        batch.setColor(Color.WHITE);
    }

    private void arrowStroke(SpriteBatch batch, float x, float y, float angle, Color color) {
        batch.setColor(color);
        batch.draw(art.pixel, x, y - 2f, 0, 2f, 21f, 4f, 1f, 1f, angle);
    }

    /** Cached layouts with a subtle text shadow; no panel texture or per-frame layout creation. */
    private static final class Text {
        final BitmapFont font;
        final GlyphLayout layout = new GlyphLayout();
        final Color color;
        final float desiredScale, maxWidth;
        float scale;
        String value;
        Text(BitmapFont font, float scale, float maxWidth, Color color) {
            this.font = font; this.desiredScale = scale; this.maxWidth = maxWidth; this.color = color;
        }
        void set(String text) {
            if (text == null) text = "";
            if (text.equals(value)) return;
            value = text;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            float r = font.getColor().r, g = font.getColor().g, b = font.getColor().b, a = font.getColor().a;
            scale = desiredScale; font.getData().setScale(scale); font.setColor(Color.WHITE);
            layout.setText(font, value);
            if (layout.width > maxWidth) {
                scale *= maxWidth / layout.width;
                font.getData().setScale(scale); layout.setText(font, value);
            }
            font.getData().setScale(oldX, oldY); font.setColor(r, g, b, a);
        }
        void draw(SpriteBatch batch, float x, float y) { draw(batch, x, y, color, 1f); }
        void draw(SpriteBatch batch, float x, float y, Color tint, float alpha) {
            if (value == null || value.isEmpty()) return;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            font.getData().setScale(scale); batch.setColor(Color.WHITE);
            var cache = font.getCache();
            cache.clear(); cache.addText(layout, x + 1f, y - 2f);
            cache.setColors(0, 0, 0, alpha * .9f); cache.draw(batch);
            cache.clear(); cache.addText(layout, x, y);
            cache.setColors(tint.r, tint.g, tint.b, alpha); cache.draw(batch);
            font.getData().setScale(oldX, oldY);
        }
        void center(SpriteBatch batch, float cx, float y) { draw(batch, cx - layout.width / 2f, y); }
        void right(SpriteBatch batch, float x, float y) { draw(batch, x - layout.width, y); }
    }
}
