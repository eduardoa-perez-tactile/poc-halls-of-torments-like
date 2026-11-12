package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.ui.UiArt;

/** Bounded isometric renderer; all scene resources are owned by GameArt/UiArt. */
public final class ArenaRenderer implements Disposable {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f, TILE = 120f;
    private static final float HUD_WIDTH = 1920f, HUD_HEIGHT = 1080f;
    private static final int PLAYER = 0, ENEMY = 1, PROP = 2, PROP_COUNT = 28;
    private final GameArt art;
    private final UiArt ui;
    private final SpriteBatch batch = new SpriteBatch(1800);
    private final OrthographicCamera[] cameras = { new OrthographicCamera(), new OrthographicCamera() };
    private final OrthographicCamera hud = new OrthographicCamera();
    private final boolean[] cameraReady = new boolean[2];
    private final float[] tileVertices = new float[20];
    private final float[] propX = new float[PROP_COUNT], propY = new float[PROP_COUNT];
    private final float[] depths = new float[ArenaWorld.MAX_ENEMIES + 2 + PROP_COUNT];
    private final int[] types = new int[depths.length], indices = new int[depths.length];
    private final String[] healthText = { "100 / 100", "100 / 100" };
    private final String[] dodgeText = { "DODGE READY", "DODGE READY" };
    private final StringBuilder textBuilder = new StringBuilder(72);
    private String timerText = "2:00", rewardsText = "0 SLAIN    0 SOULS";
    private float hudRefresh;
    private int sortCount;
    public int drawCalls;

    public ArenaRenderer(GameArt gameArt, UiArt uiArt) {
        art = gameArt; ui = uiArt;
        hud.setToOrtho(false, HUD_WIDTH, HUD_HEIGHT);
        for (int i = 0; i < 8; i++) {
            float x = -1080f + i * 300f;
            propX[i] = x; propY[i] = -840f;
            propX[i + 8] = x; propY[i + 8] = 840f;
        }
        for (int i = 0; i < 6; i++) {
            float y = -650f + i * 260f;
            propX[i + 16] = -1140f; propY[i + 16] = y;
            propX[i + 22] = 1140f; propY[i + 22] = y;
        }
    }

    public void resetCameras() {
        cameraReady[0] = cameraReady[1] = false;
        hudRefresh = 0f;
    }

    public void render(ArenaWorld world, float delta) {
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        if (width <= 0 || height <= 0) return;
        int active = (world.players[0].active ? 1 : 0) + (world.players[1].active ? 1 : 0);
        boolean split = active == 2;
        drawCalls = 0;
        hudRefresh -= Math.max(0f, delta);
        if (hudRefresh <= 0f) { refreshHud(world); hudRefresh = .2f; }
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        for (int slot = 0; slot < 2; slot++) {
            if (!world.players[slot].active) continue;
            int paneX = split && slot == 1 ? width / 2 : 0;
            int paneWidth = split ? (slot == 0 ? width / 2 : width - width / 2) : width;
            Gdx.gl.glViewport(paneX, 0, paneWidth, height);
            Gdx.gl.glScissor(paneX, 0, paneWidth, height);
            OrthographicCamera camera = cameras[slot];
            updateCamera(camera, world.players[slot], slot, split, paneWidth, height, delta);
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            drawFloor(camera);
            drawPickups(world, camera);
            gatherSprites(world, camera);
            drawShadows(world);
            drawBolts(world, camera);
            drawSprites(world);
            batch.end();
            drawCalls += batch.renderCalls;
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, width, height);
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        drawHud(world, split);
        batch.setColor(Color.WHITE);
        batch.end();
        drawCalls += batch.renderCalls;
    }

    private void updateCamera(OrthographicCamera camera, ArenaWorld.Player player, int slot,
                              boolean split, int width, int height, float delta) {
        camera.viewportWidth = split ? 650f : 1100f;
        camera.viewportHeight = camera.viewportWidth * height / width;
        float targetX = screenX(player.x, player.y), targetY = screenY(player.x, player.y) + 65f;
        if (!cameraReady[slot]) {
            camera.position.set(targetX, targetY, 0f);
            cameraReady[slot] = true;
        } else {
            float follow = Math.min(1f, Math.max(0f, delta) * 9f);
            camera.position.x += (targetX - camera.position.x) * follow;
            camera.position.y += (targetY - camera.position.y) * follow;
        }
        camera.update();
    }

    private void drawFloor(OrthographicCamera camera) {
        float minX = camera.position.x - camera.viewportWidth * .5f - 90f;
        float maxX = camera.position.x + camera.viewportWidth * .5f + 90f;
        float minY = camera.position.y - camera.viewportHeight * .5f - 45f;
        float maxY = camera.position.y + camera.viewportHeight * .5f + 45f;
        for (int row = 0; row < 15; row++) {
            float y = -ArenaWorld.HALF_HEIGHT + row * TILE;
            for (int col = 0; col < 20; col++) {
                float x = -ArenaWorld.HALF_WIDTH + col * TILE;
                float sx = screenX(x, y), sy = screenY(x, y);
                if (sx + 85f < minX || sx - 85f > maxX || sy + 85f < minY || sy > maxY) continue;
                float shade = .82f + ((col * 7 + row * 11) & 3) * .035f;
                float color = Color.toFloatBits(shade, shade, shade, 1f);
                vertex(0, sx, sy, color, 0f, 1f);
                vertex(5, sx - TILE * ISO_X, sy + TILE * ISO_Y, color, 0f, 0f);
                vertex(10, sx, sy + TILE * ISO_Y * 2f, color, 1f, 0f);
                vertex(15, sx + TILE * ISO_X, sy + TILE * ISO_Y, color, 1f, 1f);
                batch.draw(art.floor, tileVertices, 0, 20);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void vertex(int offset, float x, float y, float color, float u, float v) {
        tileVertices[offset] = x; tileVertices[offset + 1] = y; tileVertices[offset + 2] = color;
        tileVertices[offset + 3] = u; tileVertices[offset + 4] = v;
    }

    private void gatherSprites(ArenaWorld world, OrthographicCamera camera) {
        sortCount = 0;
        for (int i = 0; i < PROP_COUNT; i++) if (visible(camera, propX[i], propY[i], 150f)) add(PROP, i, propX[i], propY[i]);
        for (int i = 0; i < world.enemies.length; i++) {
            ArenaWorld.Enemy enemy = world.enemies[i];
            if (enemy.active && visible(camera, enemy.x, enemy.y, 100f)) add(ENEMY, i, enemy.x, enemy.y);
        }
        for (int i = 0; i < world.players.length; i++) {
            ArenaWorld.Player player = world.players[i];
            if (player.active && visible(camera, player.x, player.y, 130f)) add(PLAYER, i, player.x, player.y);
        }
        // Small bounded insertion sort keeps anchors stable and avoids comparator allocation.
        for (int i = 1; i < sortCount; i++) {
            float depth = depths[i]; int type = types[i], index = indices[i], j = i - 1;
            while (j >= 0 && depths[j] < depth) {
                depths[j + 1] = depths[j]; types[j + 1] = types[j]; indices[j + 1] = indices[j]; j--;
            }
            depths[j + 1] = depth; types[j + 1] = type; indices[j + 1] = index;
        }
    }

    private void add(int type, int index, float x, float y) {
        types[sortCount] = type; indices[sortCount] = index; depths[sortCount++] = screenY(x, y);
    }

    private void drawShadows(ArenaWorld world) {
        batch.setColor(1f, 1f, 1f, .62f);
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i]; float x, y, width;
            if (types[i] == PLAYER) {
                ArenaWorld.Player player = world.players[index];
                x = player.x; y = player.y; width = player.alive ? 62f : 72f;
            } else if (types[i] == ENEMY) {
                x = world.enemies[index].x; y = world.enemies[index].y; width = 44f;
            } else { x = propX[index]; y = propY[index]; width = 62f; }
            batch.draw(art.shadow, screenX(x, y) - width * .5f, screenY(x, y) - 9f, width, 20f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawPickups(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Pickup pickup : world.pickups) {
            if (!pickup.active || !visible(camera, pickup.x, pickup.y, 32f)) continue;
            float x = screenX(pickup.x, pickup.y), y = screenY(pickup.x, pickup.y);
            if (pickup.healing) batch.setColor(.9f, .24f, .18f, .65f);
            else batch.setColor(.3f, .8f, .78f, .45f);
            batch.draw(art.glow, x - 17f, y - 8f, 34f, 25f);
            batch.setColor(Color.WHITE);
            if (pickup.healing) {
                batch.setColor(.95f, .34f, .23f, 1f);
                batch.draw(art.pixel, x - 2f, y + 1f, 4f, 13f);
                batch.draw(art.pixel, x - 6f, y + 5f, 12f, 4f);
            } else batch.draw(art.soul, x - 7f, y - 1f, 14f, 20f);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawBolts(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Bolt bolt : world.bolts) {
            if (!bolt.active || !visible(camera, bolt.x, bolt.y, 32f)) continue;
            float x = screenX(bolt.x, bolt.y), y = screenY(bolt.x, bolt.y) + 24f;
            if (bolt.owner == 0) batch.setColor(1f, .65f, .25f, .8f);
            else batch.setColor(.4f, .9f, .85f, .8f);
            batch.draw(art.glow, x - 13f, y - 9f, 26f, 18f);
            batch.setColor(1f, .94f, .75f, 1f);
            batch.draw(art.pixel, x - 3f, y - 2f, 6f, 4f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawSprites(ArenaWorld world) {
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i];
            if (types[i] == PROP) {
                drawAnchored(art.props[index % art.props.length], propX[index], propY[index], 80f, 120f, 5f, false);
            } else if (types[i] == ENEMY) {
                ArenaWorld.Enemy enemy = world.enemies[index];
                if (enemy.hitFlash > 0f) batch.setColor(1f, .55f, .42f, 1f);
                drawAnchored(art.enemies[(int) (enemy.animTime * 7f) % art.enemies.length], enemy.x, enemy.y,
                        74f, 84f, 5.25f, screenX(enemy.vx, enemy.vy) < 0f);
                batch.setColor(Color.WHITE);
            } else {
                ArenaWorld.Player player = world.players[index];
                float sx = screenX(player.x, player.y), sy = screenY(player.x, player.y);
                if (!player.alive) {
                    batch.setColor(.27f, .3f, .31f, .8f);
                    drawAnchored(art.players[index][0], player.x, player.y, 96f, 120f, 6f, false);
                    batch.setColor(.9f, .68f, .33f, 1f);
                    batch.draw(art.pixel, sx - 26f, sy + 122f, 52f * Math.min(1f, player.reviveProgress / 2f), 4f);
                    batch.setColor(Color.WHITE);
                    continue;
                }
                if (player.dodgeTime > 0f) {
                    batch.setColor(index == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
                    batch.draw(art.glow, sx - 47f, sy - 10f, 94f, 58f);
                }
                float alpha = player.invulnerable > 0f && ((int) (world.elapsed * 16f) & 1) == 0 ? .58f : 1f;
                batch.setColor(1f, 1f, 1f, alpha);
                int frame = (int) (player.walkTime * 7f) % art.players[index].length;
                drawAnchored(art.players[index][frame], player.x, player.y, 96f, 120f, 6f,
                        screenX(player.facingX, player.facingY) < 0f);
                batch.setColor(Color.WHITE);
                batch.setColor(index == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
                batch.draw(art.pixel, sx - 10f, sy - 4f, 20f, 2f);
                batch.setColor(Color.WHITE);
            }
        }
    }

    private void drawAnchored(TextureRegion region, float x, float y, float width, float height, float bottom, boolean flip) {
        float left = screenX(x, y) - width * .5f, base = screenY(x, y) - bottom;
        batch.draw(region, flip ? left + width : left, base, flip ? -width : width, height);
    }

    private void refreshHud(ArenaWorld world) {
        int remaining = (int) Math.ceil(world.timeRemaining());
        textBuilder.setLength(0);
        textBuilder.append(remaining / 60).append(':');
        if (remaining % 60 < 10) textBuilder.append('0');
        timerText = textBuilder.append(remaining % 60).toString();
        textBuilder.setLength(0);
        rewardsText = textBuilder.append(world.kills).append(" SLAIN    ").append(world.souls).append(" SOULS").toString();
        for (ArenaWorld.Player player : world.players) {
            textBuilder.setLength(0);
            healthText[player.slot] = textBuilder.append((int) Math.ceil(player.health)).append(" / ").append((int) player.maxHealth).toString();
            if (!player.alive) dodgeText[player.slot] = "STAND NEAR TO REVIVE";
            else if (player.dodgeCooldown <= 0f) dodgeText[player.slot] = "DODGE READY";
            else {
                textBuilder.setLength(0);
                int tenths = (int) Math.ceil(player.dodgeCooldown * 10f);
                dodgeText[player.slot] = textBuilder.append("DODGE ").append(tenths / 10).append('.').append(tenths % 10).append('s').toString();
            }
        }
    }

    private void drawHud(ArenaWorld world, boolean split) {
        if (split) {
            batch.setColor(.06f, .05f, .04f, 1f);
            batch.draw(art.pixel, 957f, 0f, 6f, HUD_HEIGHT);
            batch.setColor(UiArt.GOLD);
            batch.draw(art.pixel, 959f, 0f, 2f, HUD_HEIGHT);
        }
        for (ArenaWorld.Player player : world.players) {
            if (!player.active) continue;
            float x = split && player.slot == 1 ? 1590f : 32f;
            batch.setColor(.025f, .028f, .032f, .88f);
            batch.draw(art.pixel, x - 12f, 924f, 330f, 132f);
            batch.setColor(player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
            batch.draw(art.pixel, x - 12f, 1054f, 330f, 2f);
            text(ui.section, player.slot == 0 ? "PLAYER I" : "PLAYER II", x, 1040f, .75f,
                    player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
            text(ui.small, healthText[player.slot], x + 205f, 1037f, 1f, UiArt.IVORY);
            batch.setColor(.18f, .08f, .075f, 1f);
            batch.draw(art.pixel, x, 983f, 292f, 14f);
            batch.setColor(player.alive ? .65f : .23f, .16f, .12f, 1f);
            batch.draw(art.pixel, x, 983f, 292f * Math.max(0f, Math.min(1f, player.health / Math.max(1f, player.maxHealth))), 14f);
            text(ui.small, dodgeText[player.slot], x, 966f, .85f, player.dodgeCooldown <= 0f ? UiArt.GOLD : UiArt.MUTED);
        }
        batch.setColor(.025f, .028f, .032f, .86f);
        batch.draw(art.pixel, 780f, 949f, 360f, 106f);
        text(ui.section, timerText, 920f, 1041f, 1f, UiArt.IVORY);
        text(ui.small, rewardsText, 816f, 986f, .9f, UiArt.GOLD);
        text(ui.small, "BACK  Pause", 1703f, 34f, 1f, UiArt.IVORY);
    }

    private void text(BitmapFont font, String text, float x, float y, float scale, Color color) {
        float scaleX = font.getData().scaleX, scaleY = font.getData().scaleY;
        float r = font.getColor().r, g = font.getColor().g, b = font.getColor().b, a = font.getColor().a;
        font.getData().setScale(scale); font.setColor(color);
        batch.setColor(Color.WHITE);
        font.draw(batch, text, x, y);
        font.getData().setScale(scaleX, scaleY); font.setColor(r, g, b, a);
    }

    private static boolean visible(OrthographicCamera camera, float x, float y, float margin) {
        return Math.abs(screenX(x, y) - camera.position.x) <= camera.viewportWidth * .5f + margin
                && Math.abs(screenY(x, y) - camera.position.y) <= camera.viewportHeight * .5f + margin;
    }
    private static float screenX(float x, float y) { return (x - y) * ISO_X; }
    private static float screenY(float x, float y) { return (x + y) * ISO_Y; }
    @Override public void dispose() { batch.dispose(); }
}
