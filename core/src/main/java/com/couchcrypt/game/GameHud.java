package com.couchcrypt.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.couchcrypt.ui.UiArt;

/** Shared, camera-independent combat UI. Reuses the approved paintings and existing atlas. */
public final class GameHud {
    private static final Color BRASS = Color.valueOf("796044");
    private static final Color BLACK = Color.valueOf("111214");
    private static final Color BLOOD = Color.valueOf("a22628");
    private static final Color BLOOD_LIGHT = Color.valueOf("df6452");
    private static final Color WARNING = Color.valueOf("e5a56a");
    private static final Color SOUL = Color.valueOf("9bc3bb");
    private final GameArt art;
    private final UiArt ui;
    private final TextureRegion[] portraits = new TextureRegion[2];
    private final Text[] health = new Text[2], dodge = new Text[2], names = new Text[2], playerLabels = new Text[2];
    private final Text timer, slain, souls, bossName, bossPhase, noticeTitle, noticeDetail, pause;
    private final StringBuilder builder = new StringBuilder(64);
    private final boolean[] wasActive = new boolean[2], wasAlive = new boolean[2];
    private float refresh;
    private boolean wasBossVisible;

    public GameHud(GameArt art, UiArt ui) {
        this.art = art; this.ui = ui;
        for (int slot = 0; slot < 2; slot++) {
            TextureRegion source = slot == 0 ? ui.knight : ui.ranger;
            portraits[slot] = new TextureRegion(source, (int) (source.getRegionWidth() * .25f), 0,
                (int) (source.getRegionWidth() * .58f), (int) (source.getRegionHeight() * .43f));
            health[slot] = new Text(ui.body, .84f, 144f, UiArt.IVORY);
            dodge[slot] = new Text(ui.small, 1f, 268f, slot == 0 ? UiArt.GOLD : SOUL);
            names[slot] = new Text(ui.section, .78f, 280f, UiArt.IVORY);
            names[slot].set(slot == 0 ? "THE WARDEN" : "THE RANGER");
            playerLabels[slot] = new Text(ui.small, 1f, 280f, slot == 0 ? UiArt.GOLD : SOUL);
            playerLabels[slot].set(slot == 0 ? "PLAYER I" : "PLAYER II");
        }
        timer = new Text(ui.heading, .76f, 152f, UiArt.BUTTON_INK);
        slain = new Text(ui.small, 1f, 260f, UiArt.IVORY);
        souls = new Text(ui.small, 1f, 260f, SOUL);
        bossName = new Text(ui.section, .85f, 630f, UiArt.IVORY);
        bossPhase = new Text(ui.small, 1f, 600f, UiArt.GOLD);
        noticeTitle = new Text(ui.section, .72f, 600f, UiArt.GOLD);
        noticeDetail = new Text(ui.small, 1f, 600f, UiArt.IVORY);
        pause = new Text(ui.small, 1f, 250f, UiArt.IVORY);
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
        boolean changed = state.bossVisible != wasBossVisible;
        for (int slot = 0; slot < 2; slot++) {
            changed |= wasActive[slot] != world.players[slot].active || wasAlive[slot] != world.players[slot].alive;
            wasActive[slot] = world.players[slot].active; wasAlive[slot] = world.players[slot].alive;
        }
        wasBossVisible = state.bossVisible;
        if (refresh <= 0 || changed) { refreshText(world); refresh = .2f; }
        if (split) divider(batch);
        for (ArenaWorld.Player player : world.players) if (player.active)
            playerPlate(batch, player, split && player.slot == 1 ? 1382f : 48f);
        timerCrest(batch);
        if (state.bossVisible) boss(batch, state);
        ArenaHudState.Notice notice = state.current();
        if (notice != null) notification(batch, notice);
        pause.center(batch, 960f, 75f);
        batch.setColor(Color.WHITE);
    }

    private void refreshText(ArenaWorld world) {
        int remaining = (int) Math.ceil(world.timeRemaining());
        builder.setLength(0); builder.append(remaining / 60).append(':');
        if (remaining % 60 < 10) builder.append('0');
        timer.set(builder.append(remaining % 60).toString());
        builder.setLength(0); slain.set(builder.append(world.kills).append("  SLAIN").toString());
        builder.setLength(0); souls.set(builder.append(world.souls).append("  SOULS").toString());
        for (ArenaWorld.Player player : world.players) {
            int slot = player.slot;
            builder.setLength(0);
            health[slot].set(builder.append((int) Math.ceil(player.health)).append(" / ").append((int) player.maxHealth).toString());
            if (!player.alive) dodge[slot].set("ALLY NEARBY TO REVIVE");
            else if (player.dodgeCooldown <= 0) dodge[slot].set("DODGE READY");
            else {
                int tenths = (int) Math.ceil(player.dodgeCooldown * 10);
                builder.setLength(0);
                dodge[slot].set(builder.append("DODGE  ").append(tenths / 10).append('.').append(tenths % 10).append('s').toString());
            }
        }
    }

    private void playerPlate(SpriteBatch batch, ArenaWorld.Player player, float x) {
        float y = 48f;
        Color accent = player.slot == 0 ? UiArt.GOLD : SOUL;
        batch.setColor(Color.WHITE); ui.panel.draw(batch, x, y, 490f, 160f);
        // Armorial portrait seal, gold pin and crimson inset sit inside the shared carved frame.
        diamond(batch, x + 76f, y + 82f, 78f, BRASS, 1f);
        diamond(batch, x + 76f, y + 82f, 72f, BLACK, 1f);
        batch.setColor(player.alive ? Color.WHITE : UiArt.MUTED);
        batch.draw(portraits[player.slot], x + 23f, y + 33f, 106f, 112f);
        diamond(batch, x + 76f, y + 29f, 8f, accent, 1f);
        playerLabels[player.slot].draw(batch, x + 145f, y + 136f);
        names[player.slot].draw(batch, x + 145f, y + 111f);
        float ratio = Math.max(0, Math.min(1, player.health / Math.max(1, player.maxHealth)));
        bar(batch, x + 145f, y + 62f, 306f, 23f, ratio, BLOOD, BLOOD_LIGHT, 5);
        health[player.slot].right(batch, x + 451f, y + 137f);
        // Dodge readiness is a physical sigil with a refill rail, rather than another large panel.
        diamond(batch, x + 154f, y + 34f, 8f, player.dodgeCooldown <= 0 && player.alive ? accent : BRASS, 1f);
        dodge[player.slot].draw(batch, x + 173f, y + 43f);
        float ready = player.alive ? 1f - Math.min(1, player.dodgeCooldown / 2.4f) : Math.min(1, player.reviveProgress / 2f);
        rect(batch, x + 173f, y + 18f, 278f, 2f, BRASS, .6f);
        rect(batch, x + 173f, y + 18f, 278f * ready, 2f, accent, .95f);
    }

    private void timerCrest(SpriteBatch batch) {
        // Long engraved wings leave the boss and notification lanes clear below.
        rect(batch, 540f, 996f, 242f, 2f, BRASS, .85f);
        rect(batch, 1138f, 996f, 242f, 2f, BRASS, .85f);
        diamond(batch, 535f, 997f, 5f, UiArt.GOLD, 1f);
        diamond(batch, 1385f, 997f, 5f, UiArt.GOLD, 1f);
        batch.setColor(Color.WHITE); ui.exitNormal.up.draw(batch, 786f, 962f, 348f, 70f);
        timer.center(batch, 960f, 1015f);
        diamond(batch, 805f, 997f, 6f, BLOOD, 1f);
        diamond(batch, 1115f, 997f, 6f, BLOOD, 1f);
        slain.right(batch, 755f, 1022f);
        souls.draw(batch, 1165f, 1022f);
    }

    private void boss(SpriteBatch batch, ArenaHudState state) {
        bossName.set(state.bossName); bossPhase.set(state.bossPhase);
        batch.setColor(Color.WHITE); ui.panel.draw(batch, 560f, 854f, 800f, 100f);
        diamond(batch, 580f, 910f, 14f, BRASS, 1f);
        diamond(batch, 580f, 910f, 9f, BLOOD, 1f);
        diamond(batch, 1340f, 910f, 14f, BRASS, 1f);
        diamond(batch, 1340f, 910f, 9f, BLOOD, 1f);
        bossName.center(batch, 960f, 938f);
        bossPhase.center(batch, 960f, 889f);
        float ratio = Math.max(0, Math.min(1, state.bossHealth / Math.max(1, state.bossMaxHealth)));
        bar(batch, 614f, 898f, 692f, 17f, ratio, BLOOD, BLOOD_LIGHT, 8);
    }

    private void notification(SpriteBatch batch, ArenaHudState.Notice notice) {
        Color accent = notice.tone == ArenaHudState.Tone.WARNING ? WARNING
            : notice.tone == ArenaHudState.Tone.SUCCESS ? SOUL : UiArt.GOLD;
        noticeTitle.set(notice.title); noticeDetail.set(notice.detail);
        float alpha = Math.min(1f, notice.remaining / .3f);
        batch.setColor(1f, 1f, 1f, alpha); ui.panel.draw(batch, 600f, 744f, 720f, 94f);
        rect(batch, 618f, 777f, 3f, 34f, accent, alpha);
        diamond(batch, 640f, 791f, 7f, accent, alpha);
        noticeTitle.center(batch, 971f, 814f);
        noticeDetail.center(batch, 971f, 783f);
        // A subtle duration stroke signals that the message will dismiss itself.
        rect(batch, 666f, 760f, 588f * Math.max(0, Math.min(1, notice.remaining / Math.max(.1f, notice.lifetime))), 2f, accent, alpha * .8f);
    }

    private void divider(SpriteBatch batch) {
        rect(batch, 958f, 0, 4f, 1080f, BLACK, .85f);
        rect(batch, 959.5f, 0, 1f, 1080f, BRASS, .5f);
        diamond(batch, 960f, 232f, 5f, UiArt.GOLD, .7f);
        diamond(batch, 960f, 700f, 5f, UiArt.GOLD, .7f);
    }

    private void bar(SpriteBatch batch, float x, float y, float w, float h, float ratio, Color fill, Color light, int segments) {
        rect(batch, x - 2f, y - 2f, w + 4f, h + 4f, BRASS, 1f);
        rect(batch, x, y, w, h, BLACK, 1f);
        rect(batch, x + 2f, y + 2f, (w - 4f) * ratio, h - 4f, fill, 1f);
        rect(batch, x + 2f, y + h - 7f, (w - 4f) * ratio, 3f, light, .5f);
        for (int segment = 1; segment < segments; segment++)
            rect(batch, x + w * segment / segments, y, 2f, h, BLACK, .55f);
        diamond(batch, x - 5f, y + h / 2f, 4f, UiArt.GOLD, 1f);
        diamond(batch, x + w + 5f, y + h / 2f, 4f, UiArt.GOLD, 1f);
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

    /** Caches layouts until text changes; drawing restores the shared font scale. */
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
            scale = desiredScale; font.getData().setScale(scale); font.setColor(color);
            layout.setText(font, value);
            if (layout.width > maxWidth) {
                scale *= maxWidth / layout.width;
                font.getData().setScale(scale); layout.setText(font, value);
            }
            font.getData().setScale(oldX, oldY); font.setColor(r, g, b, a);
        }
        void draw(SpriteBatch batch, float x, float y) {
            if (value == null || value.isEmpty()) return;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            font.getData().setScale(scale); batch.setColor(Color.WHITE);
            font.draw(batch, layout, x, y); font.getData().setScale(oldX, oldY);
        }
        void center(SpriteBatch batch, float cx, float y) { draw(batch, cx - layout.width / 2f, y); }
        void right(SpriteBatch batch, float x, float y) { draw(batch, x - layout.width, y); }
    }
}
