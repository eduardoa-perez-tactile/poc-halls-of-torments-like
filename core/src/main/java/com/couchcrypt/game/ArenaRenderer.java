package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.couchcrypt.ui.UiArt;

/** Bounded isometric renderer; all scene resources are owned by GameArt/UiArt. */
public final class ArenaRenderer implements Disposable {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f, TILE = 120f;
    private static final float HUD_WIDTH = 1920f, HUD_HEIGHT = 1080f;
    private static final int PLAYER = 0, ENEMY = 1, PROP = 2, PROP_COUNT = 28;
    private final GameArt art;
    private final GameHud gameHud;
    private final SpriteBatch batch = new SpriteBatch(1800);
    private final OrthographicCamera[] cameras = { new OrthographicCamera(), new OrthographicCamera() };
    private final OrthographicCamera hud = new OrthographicCamera();
    private final boolean[] cameraReady = new boolean[2];
    private final float[] tileVertices = new float[20];
    private final float[] propX = new float[PROP_COUNT], propY = new float[PROP_COUNT];
    private final float[] depths = new float[ArenaWorld.MAX_ENEMIES + 2 + PROP_COUNT];
    private final int[] types = new int[depths.length], indices = new int[depths.length];
    private int sortCount;
    public int drawCalls;

    public ArenaRenderer(GameArt gameArt, UiArt uiArt) {
        art = gameArt; gameHud = new GameHud(gameArt, uiArt);
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
        gameHud.reset();
    }

    public void render(ArenaWorld world, ArenaHudState state, float delta, boolean showHud) {
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        if (width <= 0 || height <= 0) return;
        int active = (world.players[0].active ? 1 : 0) + (world.players[1].active ? 1 : 0);
        boolean split = active == 2;
        drawCalls = 0;
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
            drawHazards(world, camera);
            drawGroundMarks(world, camera);
            drawPickups(world, camera);
            gatherSprites(world, camera);
            drawShadows(world);
            drawSprites(world);
            drawBolts(world, camera);
            drawShots(world, camera);
            drawEffects(world, camera);
            batch.end();
            drawCalls += batch.renderCalls;
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, width, height);
        batch.setProjectionMatrix(hud.combined);
        batch.begin();
        if (showHud) {
            gameHud.render(batch, world, state, split, delta);
            if (split) for (int slot = 0; slot < 2; slot++) {
                ArenaWorld.Player ally = world.players[1 - slot];
                OrthographicCamera camera = cameras[slot];
                float allyX = 480f + (screenX(ally.x, ally.y) - camera.position.x) * 960f / camera.viewportWidth;
                float allyY = 540f + (screenY(ally.x, ally.y) - camera.position.y) * 1080f / camera.viewportHeight;
                gameHud.allyGuidance(batch, world, slot, allyX, allyY);
            }
        }
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
            if (enemy.active && visible(camera, enemy.x, enemy.y, 230f)) add(ENEMY, i, enemy.x, enemy.y);
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
                x = world.enemies[index].x; y = world.enemies[index].y; width = 44f * enemyScale(world.enemies[index]);
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
            if (!bolt.active || !visible(camera, bolt.x, bolt.y, 36f)) continue;
            float x = screenX(bolt.x, bolt.y), y = screenY(bolt.x, bolt.y) + 24f;
            float vx = screenX(bolt.vx, bolt.vy), vy = screenY(bolt.vx, bolt.vy);
            float length = Math.max(.001f, (float) Math.sqrt(vx * vx + vy * vy));
            float dx = vx / length, dy = vy / length;
            batch.setColor(bolt.owner == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
            line(x - dx * 21f, y - dy * 21f, x, y, 2f);
            batch.setColor(1f, .95f, .8f, 1f);
            line(x, y, x - dx * 7f - dy * 3f, y - dy * 7f + dx * 3f, 2f);
            line(x, y, x - dx * 7f + dy * 3f, y - dy * 7f - dx * 3f, 2f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawShots(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Shot shot : world.shots) {
            if (!shot.active || !visible(camera, shot.x, shot.y, 40f)) continue;
            float x = screenX(shot.x, shot.y), y = screenY(shot.x, shot.y) + 18f;
            float radius = Math.max(8f, shot.radius * 1.5f);
            batch.setColor(1f, .18f, .06f, .85f);
            batch.draw(art.glow, x - radius * 1.5f, y - radius * 1.5f, radius * 3f, radius * 3f);
            batch.setColor(1f, .68f, .24f, 1f);
            batch.draw(art.pixel, x - 2.5f, y - 2.5f, 5f, 5f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawHazards(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Hazard hazard : world.hazards) {
            if (!hazard.active || !visible(camera, hazard.x, hazard.y, hazard.radius + 50f)) continue;
            float x = screenX(hazard.x, hazard.y), y = screenY(hazard.x, hazard.y);
            if (hazard.warning > 0f) {
                float progress = Math.max(0f, Math.min(1f, 1f - hazard.warning));
                batch.setColor(1f, .4f, .08f, .1f + progress * .1f);
                batch.draw(art.glow, x - hazard.radius, y - hazard.radius * .5f,
                    hazard.radius * 2f, hazard.radius);
                batch.setColor(1f, .52f, .15f, .82f);
                ring(hazard.x, hazard.y, hazard.radius, 0f, 2f, 32);
                batch.setColor(1f, .75f, .3f, .55f);
                ring(hazard.x, hazard.y, hazard.radius * Math.max(.04f, 1f - progress), 0f, 1.5f, 24);
            } else {
                float alpha = Math.min(1f, Math.max(0f, hazard.ttl * 3f));
                batch.setColor(1f, .17f, .05f, alpha * .3f);
                batch.draw(art.glow, x - hazard.radius, y - hazard.radius * .5f,
                    hazard.radius * 2f, hazard.radius);
                batch.setColor(1f, .62f, .25f, alpha);
                ring(hazard.x, hazard.y, hazard.radius, 0f, 3f, 32);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawGroundMarks(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Enemy enemy : world.enemies) {
            if (!enemy.active || !visible(camera, enemy.x, enemy.y, 170f)) continue;
            if (enemy.kind == ArenaWorld.EnemyKind.BOSS || enemy.elite) {
                boolean boss = enemy.kind == ArenaWorld.EnemyKind.BOSS;
                batch.setColor(1f, boss ? .2f : .64f, boss ? .15f : .24f, .7f);
                ring(enemy.x, enemy.y, Math.max(32f, enemy.radius + 12f), 0f, 2f, 24);
            } else if (enemy.kind == ArenaWorld.EnemyKind.CULTIST && enemy.windup > 0f) {
                batch.setColor(1f, .62f, .16f, .7f);
                ring(enemy.x, enemy.y, 30f, 0f, 1.5f, 20);
            }
        }
        for (ArenaWorld.Player player : world.players) {
            if (!player.active || !player.alive || player.auraLevel <= 0
                || !visible(camera, player.x, player.y, 180f)) continue;
            batch.setColor(player.slot == 0 ? 1f : .4f, .8f, .65f, .16f);
            ring(player.x, player.y, 100f + player.auraLevel * 20f, 0f, 1f, 28);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawEffects(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Effect effect : world.effects) {
            if (!effect.active || !visible(camera, effect.x, effect.y,
                effect.kind == ArenaWorld.EffectKind.LIGHTNING ? 600f : effect.radius + 80f)) continue;
            float life = Math.max(0f, Math.min(1f, effect.ttl / Math.max(.001f, effect.maxTtl)));
            float x = screenX(effect.x, effect.y), y = screenY(effect.x, effect.y);
            switch (effect.kind) {
                case SLASH -> {
                    batch.setColor(1f, .9f, .64f, life * .85f);
                    float previousX = 0f, previousY = 0f;
                    for (int segment = 0; segment <= 13; segment++) {
                        float angle = effect.angle - effect.sweep * .5f + effect.sweep * segment / 13f;
                        float wx = effect.x + MathUtils.cos(angle) * effect.radius;
                        float wy = effect.y + MathUtils.sin(angle) * effect.radius;
                        float sx = screenX(wx, wy), sy = screenY(wx, wy) + 22f;
                        if (segment > 0) line(previousX, previousY, sx, sy, 2.5f * Math.max(.3f, life));
                        previousX = sx; previousY = sy;
                    }
                    float sweepAngle = effect.angle - effect.sweep * .5f + effect.sweep * (1f - life);
                    float wx = effect.x + MathUtils.cos(sweepAngle) * effect.radius;
                    float wy = effect.y + MathUtils.sin(sweepAngle) * effect.radius;
                    batch.setColor(1f, .97f, .84f, life);
                    line(x, y + 22f, screenX(wx, wy), screenY(wx, wy) + 22f, 2f);
                }
                case HIT -> {
                    float size = 10f * life + 3f;
                    batch.setColor(1f, .84f, .48f, life);
                    line(x - size, y + 24f, x + size, y + 24f, 1.5f);
                    line(x, y + 24f - size, x, y + 24f + size, 1.5f);
                    batch.setColor(1f, .95f, .78f, life * .6f);
                    batch.draw(art.glow, x - 12f, y + 12f, 24f, 24f);
                }
                case DEATH -> {
                    float radius = 12f + 25f * (1f - life);
                    batch.setColor(.7f, .65f, .52f, life * .4f);
                    batch.draw(art.glow, x - radius, y - radius * .3f, radius * 2f, radius);
                    batch.setColor(.8f, .7f, .56f, life * .6f);
                    line(x - radius, y, x - radius * .65f, y + 6f, 1.5f);
                    line(x + radius * .7f, y + 5f, x + radius, y + 10f, 1.5f);
                }
                case AURA -> {
                    batch.setColor(effect.owner == 0 ? 1f : .4f, .8f, .7f, life * .65f);
                    ring(effect.x, effect.y, effect.radius * (1f - life * .2f), 0f, 2f, 28);
                }
                case LIGHTNING -> {
                    float x2 = screenX(effect.x2, effect.y2), y2 = screenY(effect.x2, effect.y2) + 24f;
                    float dx = x2 - x, dy = y2 - (y + 32f);
                    float length = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float nx = -dy / length, ny = dx / length;
                    float previousX = x, previousY = y + 32f;
                    batch.setColor(.66f, .86f, 1f, life);
                    for (int segment = 1; segment <= 4; segment++) {
                        float jag = segment == 4 ? 0f : (segment % 2 == 0 ? -9f : 9f);
                        float sx = x + dx * segment / 4f + nx * jag;
                        float sy = y + 32f + dy * segment / 4f + ny * jag;
                        line(previousX, previousY, sx, sy, 2f);
                        previousX = sx; previousY = sy;
                    }
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void ring(float x, float y, float radius, float height, float thickness, int segments) {
        float previousX = screenX(x + radius, y), previousY = screenY(x + radius, y) + height;
        for (int segment = 1; segment <= segments; segment++) {
            float angle = MathUtils.PI2 * segment / segments;
            float wx = x + MathUtils.cos(angle) * radius, wy = y + MathUtils.sin(angle) * radius;
            float sx = screenX(wx, wy), sy = screenY(wx, wy) + height;
            line(previousX, previousY, sx, sy, thickness);
            previousX = sx; previousY = sy;
        }
    }

    /** Pixel-region line segments stay in the existing atlas and never allocate geometry. */
    private void line(float x1, float y1, float x2, float y2, float thickness) {
        float dx = x2 - x1, dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < .001f) return;
        batch.draw(art.pixel, x1, y1 - thickness * .5f, 0f, thickness * .5f,
            length, thickness, 1f, 1f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
    }

    private static float enemyScale(ArenaWorld.Enemy enemy) {
        float scale = enemy.kind == ArenaWorld.EnemyKind.BOSS ? 2.2f
            : enemy.kind == ArenaWorld.EnemyKind.BRUTE ? 1.4f
            : enemy.kind == ArenaWorld.EnemyKind.WRAITH ? .82f : 1f;
        return scale * (enemy.elite ? 1.18f : 1f);
    }

    private void drawSprites(ArenaWorld world) {
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i];
            if (types[i] == PROP) {
                drawAnchored(art.props[index % art.props.length], propX[index], propY[index], 80f, 120f, 5f, false);
            } else if (types[i] == ENEMY) {
                ArenaWorld.Enemy enemy = world.enemies[index];
                float scale = enemyScale(enemy);
                if (enemy.kind == ArenaWorld.EnemyKind.WRAITH) batch.setColor(.54f, .94f, .92f, .72f);
                else if (enemy.kind == ArenaWorld.EnemyKind.BRUTE) batch.setColor(.88f, .6f, .91f, 1f);
                else if (enemy.kind == ArenaWorld.EnemyKind.CULTIST) batch.setColor(1f, .74f, .4f, 1f);
                else if (enemy.kind == ArenaWorld.EnemyKind.BOSS) batch.setColor(1f, .48f, .37f, 1f);
                else batch.setColor(Color.WHITE);
                if (enemy.hitFlash > 0f) batch.setColor(1f, .95f, .78f, 1f);
                drawAnchored(art.enemies[(int) (enemy.animTime * 7f) % art.enemies.length], enemy.x, enemy.y,
                    74f * scale, 84f * scale, 5.25f * scale, screenX(enemy.vx, enemy.vy) < 0f);
                if (enemy.elite && enemy.kind != ArenaWorld.EnemyKind.BOSS) {
                    float x = screenX(enemy.x, enemy.y), y = screenY(enemy.x, enemy.y) + 84f * scale;
                    batch.setColor(.15f, .1f, .12f, .85f); batch.draw(art.pixel, x - 25f, y, 50f, 3f);
                    batch.setColor(.88f, .38f, .27f, .95f);
                    batch.draw(art.pixel, x - 25f, y, 50f * Math.max(0f, Math.min(1f, enemy.health / Math.max(1f, enemy.maxHealth))), 3f);
                }
                batch.setColor(Color.WHITE);
            } else {
                ArenaWorld.Player player = world.players[index];
                float sx = screenX(player.x, player.y), sy = screenY(player.x, player.y);
                int hero = player.hero == ArenaWorld.Hero.WARDEN ? 0 : 1;
                if (!player.alive) {
                    batch.setColor(.27f, .3f, .31f, .8f);
                    drawAnchored(art.players[hero][0], player.x, player.y, 96f, 120f, 6f, false);
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
                int frame = (int) (player.walkTime * 7f) % art.players[hero].length;
                drawAnchored(art.players[hero][frame], player.x, player.y, 96f, 120f, 6f,
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

    private static boolean visible(OrthographicCamera camera, float x, float y, float margin) {
        return Math.abs(screenX(x, y) - camera.position.x) <= camera.viewportWidth * .5f + margin
                && Math.abs(screenY(x, y) - camera.position.y) <= camera.viewportHeight * .5f + margin;
    }
    private static float screenX(float x, float y) { return (x - y) * ISO_X; }
    private static float screenY(float x, float y) { return (x + y) * ISO_Y; }
    @Override public void dispose() { batch.dispose(); }
}
