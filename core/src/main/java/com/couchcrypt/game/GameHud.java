package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.couchcrypt.ui.UiArt;

/** Compact, unboxed combat information; the playfield remains visible beneath every element. */
public final class GameHud {
    private static final Color TRACK = Color.valueOf("252025");
    private static final Color BLOOD = Color.valueOf("b33936");
    private static final Color WARNING = Color.valueOf("e5a56a");
    private static final Color SOUL = Color.valueOf("9bc3bb");
    private final GameArt art;
    private final Text[] health = new Text[2], dodge = new Text[2], playerLabels = new Text[2];
    private final Text timer, slain, souls, bossName, bossPhase, noticeTitle, pause;
    private final StringBuilder builder = new StringBuilder(64);
    private final boolean[] wasActive = new boolean[2], wasAlive = new boolean[2];
    private float refresh;

    public GameHud(GameArt art, UiArt ui) {
        this.art = art;
        for (int slot = 0; slot < 2; slot++) {
            Color accent = slot == 0 ? UiArt.GOLD : SOUL;
            health[slot] = new Text(ui.small, 1f, 120f, UiArt.IVORY);
            dodge[slot] = new Text(ui.small, 1f, 258f, accent);
            playerLabels[slot] = new Text(ui.small, 1f, 70f, accent);
            playerLabels[slot].set(slot == 0 ? "P1" : "P2");
        }
        timer = new Text(ui.section, .9f, 110f, UiArt.IVORY);
        slain = new Text(ui.small, 1f, 230f, UiArt.IVORY);
        souls = new Text(ui.small, 1f, 230f, SOUL);
        bossName = new Text(ui.small, 1f, 330f, UiArt.IVORY);
        bossPhase = new Text(ui.small, 1f, 250f, UiArt.GOLD);
        noticeTitle = new Text(ui.small, 1f, 670f, UiArt.GOLD);
        pause = new Text(ui.small, 1f, 200f, UiArt.MUTED);
        pause.set("BACK  /  PAUSE");
    }

    public void reset() {
        refresh = 0;
        wasActive[0] = wasActive[1] = false;
        wasAlive[0] = wasAlive[1] = false;
    }

    /** Batch is begun in 1920 x 1080 logical coordinates by ArenaRenderer. */
    public void render(SpriteBatch batch, ArenaWorld world, ArenaHudState state, boolean split, float delta) {
        refresh -= Math.max(0, delta);
        boolean changed = false;
        for (int slot = 0; slot < 2; slot++) {
            changed |= wasActive[slot] != world.players[slot].active || wasAlive[slot] != world.players[slot].alive;
            wasActive[slot] = world.players[slot].active;
            wasAlive[slot] = world.players[slot].alive;
        }
        if (refresh <= 0 || changed) { refreshText(world); refresh = .2f; }
        if (split) rect(batch, 959.5f, 0, 1f, 1080f, Color.BLACK, .45f);
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerVitals(batch, player, split && player.slot == 1 ? 1592f : 48f);
        timer.center(batch, 960f, 1030f);
        slain.right(batch, 870f, 1025f);
        souls.draw(batch, 1050f, 1025f);
        if (state.bossVisible) boss(batch, state);
        ArenaHudState.Notice notice = state.current();
        if (notice != null) notification(batch, notice, state.bossVisible ? 913f : 974f);
        pause.center(batch, 960f, 71f);
        batch.setColor(Color.WHITE);
    }

    private void refreshText(ArenaWorld world) {
        int remaining = (int) Math.ceil(world.timeRemaining());
        builder.setLength(0); builder.append(remaining / 60).append(':');
        if (remaining % 60 < 10) builder.append('0');
        timer.set(builder.append(remaining % 60).toString());
        builder.setLength(0); slain.set(builder.append(world.kills).append(" SLAIN").toString());
        builder.setLength(0); souls.set(builder.append(world.souls).append(" SOULS").toString());
        for (ArenaWorld.Player player : world.players) {
            int slot = player.slot;
            builder.setLength(0); health[slot].set(builder.append((int) Math.ceil(player.health)).append(" HP").toString());
            if (!player.alive) dodge[slot].set("DOWNED  /  REVIVE NEARBY");
            else if (player.dodgeCooldown <= 0) dodge[slot].set("DODGE READY");
            else {
                int tenths = (int) Math.ceil(player.dodgeCooldown * 10);
                builder.setLength(0);
                dodge[slot].set(builder.append("DODGE ").append(tenths / 10).append('.').append(tenths % 10).append('s').toString());
            }
        }
    }

    private void playerVitals(SpriteBatch batch, ArenaWorld.Player player, float x) {
        Color accent = player.slot == 0 ? UiArt.GOLD : SOUL;
        playerLabels[player.slot].draw(batch, x, 115f);
        health[player.slot].right(batch, x + 280f, 115f);
        float ratio = Math.max(0, Math.min(1, player.health / Math.max(1, player.maxHealth)));
        rail(batch, x, 85f, 280f, 6f, ratio, BLOOD);
        diamond(batch, x + 4f, 65f, 5f, accent, 1f);
        dodge[player.slot].draw(batch, x + 18f, 75f);
        // Revival and dodge progress share a hairline, so no extra meter or frame is needed.
        float ready = player.alive ? 1f - Math.min(1, player.dodgeCooldown / 2.4f) : Math.min(1, player.reviveProgress / 2f);
        if (ready < 1f || !player.alive) rect(batch, x + 18f, 48f, 262f * ready, 1f, accent, .9f);
    }

    private void boss(SpriteBatch batch, ArenaHudState state) {
        bossName.set(state.bossName); bossPhase.set(state.bossPhase);
        bossName.draw(batch, 660f, 977f);
        bossPhase.right(batch, 1260f, 977f);
        float ratio = Math.max(0, Math.min(1, state.bossHealth / Math.max(1, state.bossMaxHealth)));
        rail(batch, 660f, 948f, 600f, 8f, ratio, BLOOD);
    }

    private void notification(SpriteBatch batch, ArenaHudState.Notice notice, float y) {
        Color accent = notice.tone == ArenaHudState.Tone.WARNING ? WARNING
            : notice.tone == ArenaHudState.Tone.SUCCESS ? SOUL : UiArt.GOLD;
        noticeTitle.set(notice.title.isEmpty() ? notice.detail : notice.title);
        float alpha = Math.max(0, Math.min(1f, notice.remaining / .3f));
        float left = 960f - noticeTitle.layout.width / 2f;
        diamond(batch, left - 17f, y - 9f, 5f, accent, alpha);
        noticeTitle.draw(batch, left, y, accent, alpha);
    }

    private void rail(SpriteBatch batch, float x, float y, float width, float height, float ratio, Color color) {
        rect(batch, x, y - 1f, width, height + 2f, Color.BLACK, .65f);
        rect(batch, x, y, width, height, TRACK, .85f);
        rect(batch, x, y, width * ratio, height, color, 1f);
    }

    private void rect(SpriteBatch batch, float x, float y, float width, float height, Color color, float alpha) {
        if (width <= 0 || height <= 0) return;
        batch.setColor(color.r, color.g, color.b, alpha); batch.draw(art.pixel, x, y, width, height);
    }

    private void diamond(SpriteBatch batch, float cx, float cy, float size, Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(art.pixel, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f,
            size, size, 1f, 1f, 45f);
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
