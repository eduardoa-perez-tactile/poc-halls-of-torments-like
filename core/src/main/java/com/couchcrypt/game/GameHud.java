package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.couchcrypt.ui.BuildIcons;
import com.couchcrypt.ui.UiArt;

/** Approved orb-and-build HUD. All art is borrowed, text cached, and render work bounded. */
public final class GameHud {
    private static final Color TRACK = Color.valueOf("252025");
    private static final Color WARNING = Color.valueOf("e5a56a");
    private static final Color SOUL = Color.valueOf("9bc3bb");
    private static final float SKILL_Y = 48f;
    private static final int ARC_SEGMENTS = 32;
    private final GameArt art;
    private final HudArt hudArt;
    private final TextureRegion[] icons;
    private final PlayerHudModel[] builds = {new PlayerHudModel(), new PlayerHudModel()};
    private final Text[] health = new Text[2], orbHealth = new Text[2], dodge = new Text[2], playerLabels = new Text[2], experience = new Text[2];
    private final Text[] partyLevel = new Text[2], overflow = new Text[2], dodgeCaption = new Text[2], dodgeStatus = new Text[2];
    private final Text[][] ranks = new Text[2][PlayerHudModel.MAX_SKILLS];
    private final Text[] allyDown = new Text[2], revival = new Text[2];
    private final Text timer, slain, souls, bossName, bossPhase, noticeTitle, pause, hp;
    private final StringBuilder builder = new StringBuilder(64);
    private final boolean[] wasActive = new boolean[2], wasAlive = new boolean[2];
    private float refresh;

    public GameHud(GameArt art, UiArt ui, BuildIcons buildIcons, HudArt hudArt) {
        this.art = art; this.hudArt = hudArt; icons = buildIcons.regions;
        for (int slot = 0; slot < 2; slot++) {
            Color accent = slot == 0 ? UiArt.GOLD : SOUL;
            health[slot] = new Text(ui.small, 1f, 286f, UiArt.IVORY);
            orbHealth[slot] = new Text(ui.body, 1f, 66f, UiArt.IVORY);
            dodge[slot] = new Text(ui.small, 1f, 58f, UiArt.IVORY);
            dodgeCaption[slot] = new Text(ui.small, .91f, 92f, accent);
            dodgeStatus[slot] = new Text(ui.small, .82f, 110f, accent);
            experience[slot] = new Text(ui.small, 1f, 286f, accent);
            partyLevel[slot] = new Text(ui.small, 1f, 240f, UiArt.IVORY);
            overflow[slot] = new Text(ui.small, 1f, 50f, accent);
            playerLabels[slot] = new Text(ui.section, .8f, 96f, accent);
            playerLabels[slot].set(slot == 0 ? "P1" : "P2");
            allyDown[slot] = new Text(ui.small, 1f, 170f, WARNING);
            allyDown[slot].set(slot == 0 ? "P2 DOWN" : "P1 DOWN");
            revival[slot] = new Text(ui.small, 1f, 320f, accent);
            for (int i = 0; i < ranks[slot].length; i++) ranks[slot][i] = new Text(ui.small, 1f, 28f, UiArt.IVORY);
        }
        hp = new Text(ui.small, .64f, 40f, UiArt.IVORY); hp.set("HP");
        timer = new Text(ui.section, .9f, 110f, UiArt.IVORY);
        slain = new Text(ui.small, 1f, 230f, UiArt.IVORY);
        souls = new Text(ui.small, 1f, 230f, SOUL);
        bossName = new Text(ui.small, 1f, 410f, UiArt.IVORY);
        bossPhase = new Text(ui.small, 1f, 190f, UiArt.GOLD);
        noticeTitle = new Text(ui.small, 1f, 670f, UiArt.GOLD);
        pause = new Text(ui.small, 1f, 200f, UiArt.MUTED); pause.set("BACK  /  PAUSE");
    }

    public void reset() {
        refresh = 0;
        wasActive[0] = wasActive[1] = false;
        wasAlive[0] = wasAlive[1] = false;
    }

    /** Three simple passes: shared HUD atlas, shared skill atlas, then cached font glyphs. */
    public void render(SpriteBatch batch, ArenaWorld world, ArenaHudState state, boolean split, float delta) {
        refresh -= Math.max(0, delta);
        boolean changed = false;
        for (int slot = 0; slot < 2; slot++) {
            changed |= wasActive[slot] != world.players[slot].active || wasAlive[slot] != world.players[slot].alive;
            wasActive[slot] = world.players[slot].active; wasAlive[slot] = world.players[slot].alive;
        }
        if (refresh <= 0 || changed) { refreshText(world); refresh = .2f; }
        for (ArenaWorld.Player player : world.players) if (player.active) {
            boolean mirror = split && player.slot == 1;
            batch.setColor(0, 0, 0, .64f);
            batch.draw(art.shadow, mirror ? 1348f : 12f, 12f, 560f, 250f);
        }
        if (split) {
            batch.setColor(0, 0, 0, .45f); batch.draw(art.pixel, 959.5f, 0, 1, 1080);
        }
        // Every component below shares HudArt's single texture.
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerArt(batch, player, world.progression, split && player.slot == 1);
        if (state.bossVisible) forgedRail(batch, 660f, 948f, 600f, 8f,
            MathUtils.clamp(state.bossHealth / Math.max(1, state.bossMaxHealth), 0, 1), hudArt.healthFill, false);
        ArenaHudState.Notice notice = state.current();
        float noticeY = state.bossVisible ? 913f : 974f;
        if (notice != null) {
            noticeTitle.set(notice.title.isEmpty() ? notice.detail : notice.title);
            diamond(batch, 960f - noticeTitle.layout.width / 2f - 17f, noticeY - 9f, 5f,
                notice.tone == ArenaHudState.Tone.WARNING ? WARNING : notice.tone == ArenaHudState.Tone.SUCCESS ? SOUL : UiArt.GOLD,
                MathUtils.clamp(notice.remaining / .3f, 0, 1));
        }
        // All acquired emblems share the existing BuildIcons atlas; no cloned GPU resources.
        batch.setColor(Color.WHITE);
        for (ArenaWorld.Player player : world.players) if (player.active) {
            boolean mirror = split && player.slot == 1;
            PlayerHudModel build = builds[player.slot];
            for (int i = 0; i < build.count; i++) {
                float x = mirror ? 1696f - i * 57f : 176f + i * 57f;
                batch.draw(icons[build.iconIndices[i]], x, SKILL_Y + 8f, 48f, 48f);
            }
        }
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerText(batch, player, split && player.slot == 1);
        timer.center(batch, 960f, 1030f); slain.right(batch, 870f, 1025f); souls.draw(batch, 1050f, 1025f);
        if (state.bossVisible) {
            bossName.set(state.bossName); bossPhase.set(state.bossPhase);
            bossName.draw(batch, 660f, 977f); bossPhase.right(batch, 1260f, 977f);
        }
        if (notice != null) noticeTitle.draw(batch, 960f - noticeTitle.layout.width / 2f, noticeY,
            notice.tone == ArenaHudState.Tone.WARNING ? WARNING : notice.tone == ArenaHudState.Tone.SUCCESS ? SOUL : UiArt.GOLD,
            MathUtils.clamp(notice.remaining / .3f, 0, 1));
        pause.center(batch, 960f, 71f); batch.setColor(Color.WHITE);
    }

    private void refreshText(ArenaWorld world) {
        int remaining = (int) Math.ceil(world.timeRemaining());
        builder.setLength(0); builder.append(remaining / 60).append(':');
        if (remaining % 60 < 10) builder.append('0');
        timer.set(world.bossSpawned ? "BOSS" : builder.append(remaining % 60).toString());
        builder.setLength(0); slain.set(builder.append(world.kills).append(" SLAIN").toString());
        builder.setLength(0); souls.set(builder.append(world.souls).append(" SOULS").toString());
        for (ArenaWorld.Player player : world.players) {
            int slot = player.slot;
            builder.setLength(0); health[slot].set(builder.append((int) Math.ceil(player.health)).append(" / ")
                .append((int) player.maxHealth).append(" HP").toString());
            builder.setLength(0); orbHealth[slot].set(builder.append((int) Math.ceil(player.health)).toString());
            builder.setLength(0); partyLevel[slot].set(builder.append("PARTY LV ").append(world.progression.level).toString());
            builder.setLength(0); experience[slot].set(world.progression.xpToNext > 0
                ? builder.append("XP ").append(world.progression.xp).append(" / ").append(world.progression.xpToNext).toString() : "MAX LEVEL");
            dodgeCaption[slot].set(player.alive ? "DODGE" : "REVIVE");
            if (!player.alive) {
                builder.setLength(0); dodge[slot].set(player.reviveProgress > 0
                    ? builder.append(Math.min(100, (int) (player.reviveProgress * 50))).append('%').toString() : "DOWN");
                dodgeStatus[slot].set(player.reviveProgress > 0 ? "ALLY NEAR" : "AWAIT ALLY");
            } else if (player.dodgeCooldown <= .05f) {
                dodge[slot].set(""); dodgeStatus[slot].set("READY");
            } else {
                int tenths = (int) Math.ceil(player.dodgeCooldown * 10);
                builder.setLength(0); dodge[slot].set(builder.append(tenths / 10).append('.').append(tenths % 10).append('s').toString());
                dodgeStatus[slot].set("");
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

    private void playerArt(SpriteBatch batch, ArenaWorld.Player player, PartyProgression progression, boolean mirror) {
        boolean teal = player.slot == 1;
        float orbX = mirror ? 1744f : 48f, statsX = mirror ? 1458f : 176f;
        float ratio = MathUtils.clamp(player.health / Math.max(1, player.maxHealth), 0, 1);
        batch.setColor(Color.WHITE); batch.draw(hudArt.orbBack, orbX, 82f, 128f, 128f);
        if (ratio > 0) {
            TextureRegion blood = hudArt.orbBlood;
            float topV = blood.getV2() - (blood.getV2() - blood.getV()) * ratio;
            batch.draw(blood.getTexture(), orbX + 21f, 103f, 86f, 86f * ratio,
                blood.getU(), blood.getV2(), blood.getU2(), topV);
            float dy = -43f + 86f * ratio;
            float chord = (float) Math.sqrt(Math.max(0, 43f * 43f - dy * dy));
            rect(batch, orbX + 64f - chord, 103f + 86f * ratio, chord * 2, 1f, WARNING, .55f);
        }
        batch.setColor(Color.WHITE); batch.draw(hudArt.orbReflection, orbX, 82f, 128f, 128f);
        batch.draw(teal ? hudArt.orbTeal : hudArt.orbGold, orbX, 82f, 128f, 128f);
        forgedRail(batch, statsX, 158f, 286f, 10f, ratio, hudArt.healthFill, teal);
        float xp = progression.xpToNext > 0 ? MathUtils.clamp((float) progression.xp / progression.xpToNext, 0, 1) : 1f;
        forgedRail(batch, statsX, 137f, 286f, 4f, xp, teal ? hudArt.xpTeal : hudArt.xpGold, teal);
        float dodgeX = mirror ? 1370f : 486f;
        batch.setColor(Color.WHITE); batch.draw(hudArt.dodgeBack, dodgeX, 137f, 64f, 64f);
        float ready = player.alive
            ? 1f - MathUtils.clamp(player.dodgeCooldown / (player.hero.dodgeCooldown * player.dodgeCooldownMultiplier), 0, 1)
            : MathUtils.clamp(player.reviveProgress / 2f, 0, 1);
        arc(batch, dodgeX + 32f, 169f, ready, teal ? SOUL : UiArt.GOLD);
        if (player.alive && player.dodgeCooldown <= .05f) {
            batch.setColor(teal ? SOUL : UiArt.GOLD); batch.draw(hudArt.dodgeGlyph, dodgeX, 137f, 64f, 64f);
        }
        PlayerHudModel build = builds[player.slot];
        batch.setColor(Color.WHITE);
        for (int i = 0; i < build.count; i++) {
            float x = mirror ? 1688f - i * 57f : 168f + i * 57f;
            batch.draw(teal ? hudArt.skillTeal : hudArt.skillGold, x, SKILL_Y, 64f, 64f);
        }
    }

    private void playerText(SpriteBatch batch, ArenaWorld.Player player, boolean mirror) {
        int slot = player.slot;
        float center = mirror ? 1808f : 112f, dodgeX = mirror ? 1402f : 518f;
        playerLabels[slot].center(batch, center, 240f);
        if (mirror) {
            partyLevel[slot].right(batch, 1744f, 240f); health[slot].right(batch, 1744f, 202f); experience[slot].right(batch, 1744f, 130f);
        } else {
            partyLevel[slot].draw(batch, 176f, 240f); health[slot].draw(batch, 176f, 202f); experience[slot].draw(batch, 176f, 130f);
        }
        orbHealth[slot].center(batch, center, 158f); hp.center(batch, center, 128f);
        dodge[slot].center(batch, dodgeX, 180f); dodgeCaption[slot].center(batch, dodgeX, 129f); dodgeStatus[slot].center(batch, dodgeX, 106f);
        PlayerHudModel build = builds[slot];
        for (int i = 0; i < build.count; i++) {
            float right = mirror ? 1744f - i * 57f : 224f + i * 57f;
            ranks[slot][i].center(batch, right, 71f);
        }
        overflow[slot].center(batch, mirror ? 1442f : 478f, 88f);
    }

    private void arc(SpriteBatch batch, float cx, float cy, float progress, Color color) {
        for (int i = 0; i < ARC_SEGMENTS; i++) {
            float amount = MathUtils.clamp(progress * ARC_SEGMENTS - i, 0, 1);
            if (amount <= 0) break;
            float angle = 90f - (i + .5f) * 360f / ARC_SEGMENTS;
            float x = cx + MathUtils.cosDeg(angle) * 28f, y = cy + MathUtils.sinDeg(angle) * 28f;
            batch.setColor(color);
            batch.draw(hudArt.pixel, x - 3f * amount, y - 1.5f, 3f * amount, 1.5f, 6f * amount, 3f, 1, 1, angle + 90f);
        }
    }

    private void forgedRail(SpriteBatch batch, float x, float y, float width, float height, float ratio, TextureRegion fill, boolean teal) {
        Color rim = teal ? SOUL : UiArt.GOLD;
        rect(batch, x - 2f, y - 3f, width + 4f, height + 6f, Color.BLACK, .8f);
        rect(batch, x, y, width, height, TRACK, 1f);
        rect(batch, x, y + height + 1f, width, 1f, rim, .7f);
        rect(batch, x, y - 2f, width, 1f, rim, .7f);
        diamond(batch, x - 4f, y + height / 2f, 5f, rim, .8f);
        diamond(batch, x + width + 4f, y + height / 2f, 5f, rim, .8f);
        if (ratio > 0) { batch.setColor(Color.WHITE); batch.draw(fill, x, y, width * ratio, height); }
    }

    private void rail(SpriteBatch batch, float x, float y, float width, float height, float ratio, Color color) {
        rect(batch, x, y - 1f, width, height + 2f, Color.BLACK, .65f);
        rect(batch, x, y, width, height, TRACK, .85f); rect(batch, x, y, width * ratio, height, color, 1f);
    }

    private void rect(SpriteBatch batch, float x, float y, float width, float height, Color color, float alpha) {
        if (width <= 0 || height <= 0) return;
        batch.setColor(color.r, color.g, color.b, alpha); batch.draw(hudArt.pixel, x, y, width, height);
    }

    private void diamond(SpriteBatch batch, float cx, float cy, float size, Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(hudArt.pixel, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, 45f);
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
