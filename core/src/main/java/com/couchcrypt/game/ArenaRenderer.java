package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.SpriteCache;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.couchcrypt.ui.UiArt;
import com.couchcrypt.ui.BuildIcons;
import java.util.ArrayList;
import java.util.Comparator;

/** Bounded isometric renderer; all scene resources are owned by GameArt/UiArt. */
public final class ArenaRenderer implements Disposable {
    private static final float ISO_X = .70710678f, ISO_Y = .35355339f;
    private static final float HUD_WIDTH = 1920f, HUD_HEIGHT = 1080f;
    private static final float REVIVE_BAR_Y = 122f, REVIVE_BAR_HEIGHT = 4f;
    private static final float SHADOW_Y = -9f, SHADOW_HEIGHT = 20f;
    private static final float ENVIRONMENT_BASE_HEIGHT = 24f;
    private static final float LEVEL_FX_SCALE = 1.6f, RECOIL_MARGIN = 30f;
    // Fixed SE/SW/NE/NW release landmarks, in untrimmed source pixels relative to the ground root.
    // Only the rendered origin moves; the simulation keeps its original projectile coordinates and trajectory.
    private static final float[] BOLT_ORIGIN = {23, 51, -29, 55, 27, 65, -25, 65};
    private static final float[] SOVEREIGN_ORIGIN = {37, 78, -12, 67, 18, 90, -35, 82};
    private static final int PLAYER = 0, ENEMY = 1, PROP = 2, PAINTED_BORDER = 3;
    private final GameArt art;
    private final GameHud gameHud;
    private final SpriteBatch batch;
    private final FrameMetrics metrics;
    // One bounded static mesh; both cameras reuse the same floor, paths and wear colours.
    private final SpriteCache floorCache = createFloorCache();
    private final int floorCacheId;
    private final ArenaFloorLayout floorLayout;
    private final EngravedFloorArt.Quad[] medallion;
    private final OrthographicCamera[] cameras = { new OrthographicCamera(), new OrthographicCamera() };
    private final OrthographicCamera hud = new OrthographicCamera();
    private final boolean[] cameraReady = new boolean[2];
    private final ArenaLayout layout;
    private final PaintedBorderArt.Scene paintedBorders;
    private final GameplayAtlas.Region[] propArt;
    private final float[] environmentVertices = new float[20];
    private final float[] depths = new float[ArenaWorld.MAX_ENEMIES + 2 + ArenaLayout.MAX_PLACEMENTS + PaintedBorderArt.MAX_SCENERY];
    private final int[] types = new int[depths.length], indices = new int[depths.length];
    private int sortCount;
    public int drawCalls;
    /** Startup inventory; shared by both cameras and never rebuilt while playing. */
    public int cachedFloorQuads;
    public int medallionQuads() { return medallion.length; }

    public ArenaRenderer(GameArt gameArt, UiArt uiArt, BuildIcons buildIcons, HudArt hudArt) {
        this(gameArt, uiArt, buildIcons, hudArt, null);
    }

    public ArenaRenderer(GameArt gameArt, UiArt uiArt, BuildIcons buildIcons, HudArt hudArt, FrameMetrics frameMetrics) {
        metrics = frameMetrics;
        batch = metrics == null ? new SpriteBatch(1800) : new ProfileBatch();
        art = gameArt; gameHud = new GameHud(gameArt, uiArt, buildIcons, hudArt);
        floorLayout = new ArenaFloorLayout(new JsonReader().parse(Gdx.files.internal("arena/crypt-floor.json")));
        float radius = floorLayout.medallionDiameter * .5f;
        float[] engraving = EngravedFloorArt.rectangle(floorLayout.centerX - radius, floorLayout.centerY - radius,
            floorLayout.centerX + radius, floorLayout.centerY + radius);
        // The approved flourish faces screen up/right; its stone joints align with the other paving.
        for (int i = 0; i < engraving.length; i += 4) {
            float x = engraving[i] - floorLayout.centerX, y = engraving[i + 1] - floorLayout.centerY;
            engraving[i] = floorLayout.centerX + (x + y) * ISO_X;
            engraving[i + 1] = floorLayout.centerY + (y - x) * ISO_X;
        }
        medallion = art.engravedFloor.geometry(art.engravedFloor.medallion, engraving, 1f);
        floorCacheId = cacheFloor();
        hud.setToOrtho(false, HUD_WIDTH, HUD_HEIGHT);
        layout = ArenaLayout.load(art.metadata);
        paintedBorders = art.paintedBorders.place(layout, art.metadata);
        propArt = new GameplayAtlas.Region[layout.placements.length];
        for (int i = 0; i < propArt.length; i++)
            propArt[i] = art.environment(layout.placements[i].region);
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
        int floorDraws = 0, worldDraws = 0, visibleSprites = 0;
        if (metrics != null) ((ProfileBatch) batch).textureSelections = 0;
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        for (int slot = 0; slot < 2; slot++) {
            if (!world.players[slot].active) continue;
            int paneX = split && slot == 1 ? width / 2 : 0;
            int paneWidth = split ? (slot == 0 ? width / 2 : width - width / 2) : width;
            Gdx.gl.glViewport(paneX, 0, paneWidth, height);
            Gdx.gl.glScissor(paneX, 0, paneWidth, height);
            OrthographicCamera camera = cameras[slot];
            updateCamera(camera, world.players[slot], slot, split, paneWidth, height, delta);
            if (metrics != null) metrics.beginStage(FrameMetrics.FLOOR);
            drawFloor(camera);
            if (metrics != null) {
                metrics.endStage(FrameMetrics.FLOOR);
                floorDraws += floorCache.renderCalls;
                metrics.beginStage(FrameMetrics.WORLD_DRAW);
            }
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            art.engravedFloor.draw(batch, medallion);
            drawHazards(world, camera);
            drawAttackRanges(world, camera);
            drawGroundMarks(world, camera);
            drawPickups(world, camera);
            drawDust(world, camera);
            if (metrics != null) {
                metrics.endStage(FrameMetrics.WORLD_DRAW);
                metrics.beginStage(FrameMetrics.GATHER);
            }
            gatherSprites(world, camera);
            if (metrics != null) {
                metrics.endStage(FrameMetrics.GATHER);
                visibleSprites += sortCount;
                metrics.beginStage(FrameMetrics.WORLD_DRAW);
            }
            drawShadows(world);
            drawSprites(world, camera, slot);
            drawBolts(world, camera);
            drawShots(world, camera);
            drawEffects(world, camera);
            batch.end();
            drawCalls += batch.renderCalls;
            if (metrics != null) {
                metrics.endStage(FrameMetrics.WORLD_DRAW);
                worldDraws += batch.renderCalls;
            }
        }
        if (metrics != null) metrics.beginStage(FrameMetrics.HUD);
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
        if (metrics != null) {
            metrics.endStage(FrameMetrics.HUD);
            metrics.recordRenderCounts(floorDraws, worldDraws, batch.renderCalls,
                ((ProfileBatch) batch).textureSelections, visibleSprites);
        }
    }

    /** Counts arena batch selections without intercepting GL or forcing extra flushes/fences. */
    private static final class ProfileBatch extends SpriteBatch {
        int textureSelections;
        ProfileBatch() { super(1800); }
        @Override protected void switchTexture(Texture texture) {
            textureSelections++;
            super.switchTexture(texture);
        }
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

    private int cacheFloor() {
        floorCache.beginCache();
        int quads = 0;
        float scale = ArenaTileLayout.CELL * 2f * ISO_X / art.borders.tileWidth;
        int minColumn = -ArenaTileLayout.PADDING, maxColumn = ArenaTileLayout.COLUMNS + ArenaTileLayout.PADDING - 1;
        int minRow = -ArenaTileLayout.PADDING, maxRow = ArenaTileLayout.ROWS + ArenaTileLayout.PADDING - 1;
        // The exterior is a backdrop: it must not cover the foreground foundation skirts.
        for (int pass = 0; pass < 2; pass++) {
            // Match the export's far-to-near order, including the i tie-break.
            for (int depth = maxColumn + maxRow; depth >= minColumn + minRow; depth--) {
                for (int column = minColumn; column <= maxColumn; column++) {
                    int row = depth - column;
                    if (row < minRow || row > maxRow) continue;
                    boolean exterior = ArenaTileLayout.exterior(column, row);
                    if (exterior != (pass == 0)) continue;
                    if (column >= 0 && column < ArenaTileLayout.COLUMNS && row >= 0 && row < ArenaTileLayout.ROWS) continue;
                    float x = ArenaTileLayout.farX(column), y = ArenaTileLayout.farY(row);
                    float shade = exterior ? .10f : 1f;
                    floorCache.setColor(shade, shade, shade, 1f);
                    BorderTileArt.Region tile = art.borders.region(ArenaTileLayout.tile(column, row));
                    floorCache.add(tile.texture(), screenX(x, y) + tile.left() * scale,
                        screenY(x, y) + tile.bottom() * scale, tile.width() * scale, tile.height() * scale);
                    quads++;
                }
            }
        }
        // Materials retain their own world repeat size rather than shrinking to the foundation grid.
        ArrayList<EngravedFloorArt.Quad> field = new ArrayList<>();
        float repeat = floorLayout.fieldRepeat;
        for (float x = -ArenaWorld.HALF_WIDTH; x < ArenaWorld.HALF_WIDTH; x += repeat)
            for (float y = -ArenaWorld.HALF_HEIGHT; y < ArenaWorld.HALF_HEIGHT; y += repeat) {
                float right = Math.min(x + repeat, ArenaWorld.HALF_WIDTH), top = Math.min(y + repeat, ArenaWorld.HALF_HEIGHT);
                float[] polygon = EngravedFloorArt.rectangle(x, y, right, top);
                // Partial boundary cells crop the material instead of stretching its slabs.
                polygon[2] = polygon[6] = 0; polygon[10] = polygon[14] = (right - x) / repeat;
                polygon[3] = polygon[15] = 1 - (top - y) / repeat; polygon[7] = polygon[11] = 1;
                java.util.Collections.addAll(field, art.engravedFloor.geometry(art.engravedFloor.field, polygon, 1f));
            }
        quads += cacheGround(field);
        ArrayList<EngravedFloorArt.Quad> paths = new ArrayList<>();
        for (var cell : floorLayout.pathCells)
            java.util.Collections.addAll(paths, art.engravedFloor.geometry(art.engravedFloor.path, cell.vertices, 1f, .10f, .90f));
        quads += cacheGround(paths);
        ArrayList<EngravedFloorArt.Quad> curbs = new ArrayList<>();
        for (float[] polygon : new StonePathBorders(floorLayout).polygons)
            java.util.Collections.addAll(curbs, art.engravedFloor.geometry(art.engravedFloor.curb, polygon, 1f));
        quads += cacheGround(curbs);
        if (quads > EngravedFloorArt.MAX_FLOOR_QUADS) throw new IllegalStateException("Floor cache capacity exceeded");
        cachedFloorQuads = quads;
        Gdx.app.log("FloorArt", "Cached ground quads=" + quads + ", medallion quads=" + medallion.length);
        floorCache.setColor(Color.WHITE);
        return floorCache.endCache();
    }

    private int cacheGround(ArrayList<EngravedFloorArt.Quad> quads) {
        quads.sort(Comparator.comparingInt(EngravedFloorArt.Quad::page));
        for (var quad : quads) {
            float[] vertices = quad.vertices();
            for (int i = 0; i < vertices.length; i += 5) {
                float x = (vertices[i] / ISO_X + vertices[i + 1] / ISO_Y) * .5f;
                float y = (vertices[i + 1] / ISO_Y - vertices[i] / ISO_X) * .5f;
                float shade = Math.min(1f, floorLayout.shade(x, y));
                vertices[i + 2] = Color.toFloatBits(shade, shade, shade, 1f);
            }
        }
        art.engravedFloor.cache(floorCache, quads.toArray(new EngravedFloorArt.Quad[0]));
        return quads.size();
    }

    private static SpriteCache createFloorCache() {
        String vertex = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projectionViewMatrix;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a *= 255.0 / 254.0;
                v_texCoords = a_texCoord0;
                gl_Position = u_projectionViewMatrix * a_position;
            }
            """;
        // These tiles own opaque cells, rather than translucent sprite edges.
        // Linear filtering interpolates their zero-RGB transparent pixels and
        // would darken shared diagonals twice through ordinary alpha blending.
        // Unpremultiply that sampled coverage, then draw the owning cell opaque.
        // A four-cell junction can distribute coverage across four samples, so
        // retain samples below one quarter instead of using a 0.5 alpha test.
        String fragment = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            void main() {
                vec4 sampleColor = texture2D(u_texture, v_texCoords);
                if (sampleColor.a < 0.2) discard;
                gl_FragColor = vec4(sampleColor.rgb / sampleColor.a, 1.0) * v_color;
            }
            """;
        ShaderProgram shader = new ShaderProgram(vertex, fragment);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Cannot compile arena tile coverage shader: " + log);
        }
        try {
            // SpriteCache owns and disposes its constructor-supplied base shader.
            return new SpriteCache(EngravedFloorArt.MAX_FLOOR_QUADS, shader, true);
        } catch (RuntimeException | Error failure) {
            shader.dispose();
            throw failure;
        }
    }

    private void drawFloor(OrthographicCamera camera) {
        // Projection/scissor clip the unchanged static tiles independently for each pane.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        floorCache.setProjectionMatrix(camera.combined);
        floorCache.begin();
        floorCache.draw(floorCacheId);
        floorCache.end();
        drawCalls += floorCache.renderCalls;
    }

    private void gatherSprites(ArenaWorld world, OrthographicCamera camera) {
        sortCount = 0;
        for (int i = 0; i < layout.placements.length; i++) {
            ArenaLayout.Placement placement = layout.placements[i];
            if (placement.wall) continue;
            GameplayAtlas.Frame frame = art.metadata.frames[propArt[i].frame()];
            float scale = placement.scale;
            float shear = propArt[i].renderShearY();
            float lowShift = Math.min(frame.left * shear, (frame.left + frame.width) * shear) * scale;
            float highShift = Math.max(frame.left * shear, (frame.left + frame.width) * shear) * scale;
            if (visible(camera, placement.x, placement.y, Math.max(placement.shadowWidth * .5f, -frame.left * scale),
                Math.max(placement.shadowWidth * .5f, (frame.left + frame.width) * scale),
                Math.max(placement.shadowHeight * .5f, (frame.bottom + frame.height) * scale + highShift),
                Math.max(placement.shadowHeight * .5f, -frame.bottom * scale - lowShift)))
                add(PROP, i, placement.x, placement.y);
        }
        for (int i = 0; i < paintedBorders.quads.length; i++) if (paintedBorders.visible(camera, i)) {
            PaintedBorderArt.Quad q = paintedBorders.quads[i];
            add(PAINTED_BORDER, i, q.worldX, q.worldY);
            depths[sortCount - 1] = q.sortDepth;
        }
        for (int i = 0; i < world.enemies.length; i++) {
            ArenaWorld.Enemy enemy = world.enemies[i];
            if (!enemy.active) continue;
            boolean body = visibleActor(camera, enemy.x, enemy.y,
                art.actor(ActorAnimation.actor(enemy.kind)), enemyScale(enemy));
            boolean arrival = enemy.spawnAge < art.vfx.duration(VfxArt.Animation.SPAWN)
                && visible(camera, enemy.x, enemy.y, art.vfx.maxRadius(VfxArt.Animation.SPAWN) * spawnScale(enemy));
            boolean attack = enemy.attack != ArenaWorld.EnemyAttack.NONE
                && visible(camera, enemy.x, enemy.y, art.enemyAttacks.actorRadius(enemy.kind)
                    * art.actor(ActorAnimation.actor(enemy.kind)).pixelScale * enemyScale(enemy) + 20f);
            if (body || arrival || attack) add(ENEMY, i, enemy.x, enemy.y);
        }
        for (int i = 0; i < world.players.length; i++) {
            ArenaWorld.Player player = world.players[i];
            if (!player.active) continue;
            boolean body = visibleActor(camera, player.x, player.y,
                art.actor(ActorAnimation.actor(player.hero)), 1f);
            boolean level = player.alive && world.levelUpFxAge < art.vfx.duration(VfxArt.Animation.LEVEL_UP)
                && visible(camera, player.x, player.y, art.vfx.maxRadius(VfxArt.Animation.LEVEL_UP) * LEVEL_FX_SCALE);
            if (body || level) add(PLAYER, i, player.x, player.y);
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
            if (types[i] == PAINTED_BORDER) continue;
            if (types[i] == PLAYER) {
                ArenaWorld.Player player = world.players[index];
                x = player.x; y = player.y; width = player.alive ? 62f : 72f;
            } else if (types[i] == ENEMY) {
                x = world.enemies[index].x; y = world.enemies[index].y; width = 44f * enemyScale(world.enemies[index]);
            } else {
                ArenaLayout.Placement placement = layout.placements[index];
                if (placement.shadowWidth > 0 && placement.shadowHeight > 0)
                    batch.draw(art.shadow, screenX(placement.x, placement.y) - placement.shadowWidth * .5f,
                        screenY(placement.x, placement.y) - placement.shadowHeight * .5f,
                        placement.shadowWidth, placement.shadowHeight);
                continue;
            }
            batch.draw(art.shadow, screenX(x, y) - width * .5f, screenY(x, y) + SHADOW_Y, width, SHADOW_HEIGHT);
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

    private void drawDust(ArenaWorld world, OrthographicCamera camera) {
        // A fixed world field: both panes see the same motes, and simulation pauses freeze them.
        for (int i = 0; i < 48; i++) {
            int hash = (i + 1) * 0x45d9f3b;
            hash ^= hash >>> 16;
            float x = -ArenaWorld.HALF_WIDTH + ((hash & 0xffff) / 65535f * ArenaWorld.HALF_WIDTH * 2f
                + world.elapsed * 2f) % (ArenaWorld.HALF_WIDTH * 2f);
            float y = -ArenaWorld.HALF_HEIGHT + (((hash >>> 16) & 0xffff) / 65535f * ArenaWorld.HALF_HEIGHT * 2f
                + world.elapsed * .8f) % (ArenaWorld.HALF_HEIGHT * 2f);
            float phase = ((i * .173f + world.elapsed * .045f) % 1f);
            float height = 20f + phase * 120f;
            float sx = screenX(x, y), sy = screenY(x, y) + height;
            if (!visibleProjected(camera, sx, sy, 3f)) continue;
            float alpha = .13f * Math.min(1f, Math.min(phase, 1f - phase) * 8f);
            float size = (i & 1) == 0 ? 1f : 1.6f;
            batch.setColor(.64f, .7f, .72f, alpha);
            batch.draw(art.pixel, sx, sy, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawBolts(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Bolt bolt : world.bolts) {
            if (!bolt.active) continue;
            int origin = ActorAnimation.facing(bolt.launchFacingX, bolt.launchFacingY,
                ActorAnimation.Facing.SE).ordinal() * 2;
            float sourceScale = art.actor(ActorAnimation.Actor.RANGER).pixelScale;
            float x = screenX(bolt.x, bolt.y) + BOLT_ORIGIN[origin] * sourceScale;
            float y = screenY(bolt.x, bolt.y) + BOLT_ORIGIN[origin + 1] * sourceScale;
            drawMagicProjectile(camera, MagicProjectileArt.Type.RANGER, bolt.age,
                x, y, bolt.vx, bolt.vy, bolt.critical ? 1.4f : 1.2f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawShots(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Shot shot : world.shots) {
            if (!shot.active) continue;
            boolean boss = shot.casterKind == ArenaWorld.EnemyKind.BOSS;
            ActorAnimation.Facing facing = ActorAnimation.facing(shot.launchFacingX,
                shot.launchFacingY, ActorAnimation.Facing.SE);
            int origin = facing.ordinal() * 2;
            float scale = art.actor(boss ? ActorAnimation.Actor.SOVEREIGN : ActorAnimation.Actor.CULTIST).pixelScale
                * enemyScale(boss ? ArenaWorld.EnemyKind.BOSS : ArenaWorld.EnemyKind.CULTIST, shot.casterElite);
            // The launch pose is a value snapshot: turning, death and pool reuse cannot move a missile's emitter.
            float ox = boss ? SOVEREIGN_ORIGIN[origin] : art.enemyAttacks.muzzleX(ArenaWorld.EnemyKind.CULTIST, facing);
            float oy = boss ? SOVEREIGN_ORIGIN[origin + 1] : art.enemyAttacks.muzzleY(ArenaWorld.EnemyKind.CULTIST, facing);
            float x = screenX(shot.x, shot.y) + ox * scale;
            float y = screenY(shot.x, shot.y) + oy * scale;
            drawMagicProjectile(camera, boss ? MagicProjectileArt.Type.SOVEREIGN : MagicProjectileArt.Type.CULTIST,
                shot.age, x, y, shot.vx, shot.vy, boss ? 1.5f : 1.15f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawMagicProjectile(OrthographicCamera camera, MagicProjectileArt.Type type,
                                     float age, float x, float y, float vx, float vy, float scale) {
        if (!visibleProjected(camera, x, y, art.projectiles.maxRadius(type) * scale + 24f)) return;
        float dx = screenX(vx, vy), dy = screenY(vx, vy);
        float length = Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        dx /= length; dy /= length;
        // The painted energy tail rotates with projected world velocity, never a screen-space target line.
        for (int ghost = 2; ghost >= 1; ghost--) {
            batch.setColor(1f, 1f, 1f, ghost == 1 ? .2f : .09f);
            art.projectiles.draw(batch, type, age - ghost * .025f,
                x - dx * ghost * 9f, y - dy * ghost * 9f, scale, angle);
        }
        batch.setColor(Color.WHITE);
        art.projectiles.draw(batch, type, age, x, y, scale, angle);
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
            if (!enemy.active || enemy.attack == ArenaWorld.EnemyAttack.NONE) continue;
            float age = enemy.attackAge;
            if (enemy.attack == ArenaWorld.EnemyAttack.BRUTE_SLAM) {
                if (!visible(camera, enemy.attackTargetX, enemy.attackTargetY, enemy.attackRadius + 80f)) continue;
                if (age < ArenaWorld.BRUTE_STRIKE_END) {
                    boolean windup = age < ArenaWorld.BRUTE_WINDUP;
                    batch.setColor(1f, windup ? .68f : .24f, windup ? .25f : .16f, .82f);
                    drawAura(world.elapsed, enemy.attackTargetX, enemy.attackTargetY,
                        enemy.attackRadius, EnemyAttackArt.Layer.ALL);
                }
                float strikeAge = age - ArenaWorld.BRUTE_WINDUP;
                if (strikeAge >= 0 && strikeAge < .3f) {
                    float progress = strikeAge / .3f;
                    batch.setColor(1f, .74f, .42f, (1f - progress) * .8f);
                    ring(enemy.attackTargetX, enemy.attackTargetY, 12f + 54f * progress, 0f, 2.5f, 28);
                    for (int mote = 0; mote < 6; mote++) {
                        float a = mote * MathUtils.PI2 / 6f, r = 12f + 50f * progress;
                        float x = enemy.attackTargetX + MathUtils.cos(a) * r;
                        float y = enemy.attackTargetY + MathUtils.sin(a) * r;
                        float lift = 10f * MathUtils.sin(progress * MathUtils.PI);
                        batch.draw(art.glow, screenX(x, y) - 4f, screenY(x, y) + lift - 2f, 8f, 4f);
                    }
                }
            } else if (enemy.attack == ArenaWorld.EnemyAttack.WRAITH_DASH
                && age < ArenaWorld.WRAITH_WINDUP + ArenaWorld.WRAITH_DASH_SECONDS) {
                // Three low, separate runes show the fixed lane without covering the tiles with a guide bar.
                for (int rune = 1; rune <= 3; rune++) {
                    float along = rune * .24f;
                    float x = MathUtils.lerp(enemy.attackStartX, enemy.attackTargetX, along);
                    float y = MathUtils.lerp(enemy.attackStartY, enemy.attackTargetY, along);
                    if (!visible(camera, x, y, 40f)) continue;
                    boolean windup = age < ArenaWorld.WRAITH_WINDUP;
                    batch.setColor(1f, windup ? .68f : .28f, .25f, windup ? .55f : .3f);
                    drawAura(world.elapsed + rune * .125f, x, y, 13f, EnemyAttackArt.Layer.ALL);
                }
            } else if (enemy.attack == ArenaWorld.EnemyAttack.CULTIST_CAST
                && age >= ArenaWorld.CULTIST_WINDUP - .15f && age < ArenaWorld.CULTIST_WINDUP
                && visible(camera, enemy.attackTargetX, enemy.attackTargetY, 50f)) {
                batch.setColor(1f, .68f, .25f, .65f);
                drawAura(world.elapsed, enemy.attackTargetX, enemy.attackTargetY, 18f, EnemyAttackArt.Layer.ALL);
            }
        }
        for (ArenaWorld.Player player : world.players) {
            if (!player.active || !player.alive || player.auraLevel <= 0
                || !visible(camera, player.x, player.y, 180f)) continue;
            batch.setColor(.32f, .85f, .78f, .3f);
            drawAura(world.elapsed, player.x, player.y, 100f + player.auraLevel * 20f, EnemyAttackArt.Layer.ALL);
        }
        batch.setColor(Color.WHITE);
    }

    /** One quiet projected ground ring, using the same primary range as target acquisition. */
    private void drawAttackRanges(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Player player : world.players) {
            if (!player.active || !player.alive) continue;
            float radius = world.primaryAttackRange(player);
            if (!visible(camera, player.x, player.y, radius)) continue;
            // A slim dark edge keeps the understated participant colour visible on pale floor tiles.
            batch.setColor(0, 0, 0, .12f);
            ring(player.x, player.y, radius, 0f, 2.2f, 64);
            if (player.slot == 0) batch.setColor(.82f, .7f, .46f, .42f);
            else batch.setColor(.49f, .75f, .7f, .42f);
            ring(player.x, player.y, radius, 0f, 1.15f, 64);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawAura(float age, float x, float y, float radius, EnemyAttackArt.Layer layer) {
        art.enemyAttacks.drawAura(batch, age, screenX(x, y), screenY(x, y),
            radius / art.enemyAttacks.auraGroundRadius(), layer);
    }

    private void drawEnemyAura(ArenaWorld.Enemy enemy, EnemyAttackArt.Layer layer) {
        if (enemy.elite || enemy.kind == ArenaWorld.EnemyKind.BOSS) {
            boolean boss = enemy.kind == ArenaWorld.EnemyKind.BOSS;
            batch.setColor(boss ? 1f : .72f, boss ? .26f : .47f, boss ? .18f : 1f, .56f);
            drawAura(enemy.animTime, enemy.x, enemy.y, Math.max(32f, enemy.radius + 12f), layer);
        }
        if (enemy.attack != ArenaWorld.EnemyAttack.NONE && enemy.windup > 0) {
            batch.setColor(1f, .68f, .25f, .72f);
            drawAura(enemy.attackAge, enemy.x, enemy.y, 29f * Math.min(1.4f, enemyScale(enemy)), layer);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawEffects(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Effect effect : world.effects) {
            if (!effect.active || !visible(camera, effect.x, effect.y,
                effect.kind == ArenaWorld.EffectKind.LIGHTNING ? 600f : effectMargin(effect))) continue;
            float life = Math.max(0f, Math.min(1f, effect.ttl / Math.max(.001f, effect.maxTtl)));
            float x = screenX(effect.x, effect.y), y = screenY(effect.x, effect.y);
            switch (effect.kind) {
                case SLASH -> {
                    // Spend most of the cosmetic lifetime sweeping, then let the final crescent fade.
                    float lift = art.actor(ActorAnimation.Actor.WARDEN).worldHeight * .5f;
                    float phase = 1f - life;
                    float head = .18f + .82f * Math.min(1f, phase / .7f);
                    float tail = Math.max(0f, head - .65f);
                    float visibility = Math.min(1f, life / .3f);
                    for (int layer = 0; layer < 2; layer++) {
                        if (layer == 0) batch.setColor(1f, .61f, .2f, visibility * .38f);
                        else batch.setColor(1f, .97f, .78f, visibility * .88f);
                        float previousX = 0f, previousY = 0f;
                        for (int segment = 0; segment <= 13; segment++) {
                            float progress = segment / 13f;
                            float angle = effect.angle - effect.sweep * .5f
                                + effect.sweep * (tail + (head - tail) * progress);
                            float wx = effect.x + MathUtils.cos(angle) * effect.radius;
                            float wy = effect.y + MathUtils.sin(angle) * effect.radius;
                            float sx = screenX(wx, wy), sy = screenY(wx, wy) + lift;
                            float width = (layer == 0 ? 8f : 3.5f) * (.15f + progress * .85f) * visibility;
                            if (segment > 0) line(previousX, previousY, sx, sy, Math.max(.6f, width));
                            previousX = sx; previousY = sy;
                        }
                    }
                }
                case HIT -> {
                    float lift = impactLift(effect);
                    float impactX = x, impactY = y + lift;
                    if (!effect.heroHit && effect.impactKind == ArenaWorld.ImpactKind.BOLT) {
                        int origin = ActorAnimation.facing(effect.projectileFacingX,
                            effect.projectileFacingY, ActorAnimation.Facing.SE).ordinal() * 2;
                        float sourceScale = art.actor(ActorAnimation.Actor.RANGER).pixelScale;
                        impactX += BOLT_ORIGIN[origin] * sourceScale;
                        impactY = y + BOLT_ORIGIN[origin + 1] * sourceScale;
                    }
                    if (!effect.heroHit && effect.impactKind == ArenaWorld.ImpactKind.BOLT) {
                        // The collision origin follows the projectile plane; the burst itself has no heading.
                        projectileBurst(impactX, impactY, life, effect.critical);
                    } else {
                        float dx = screenX(effect.dirX, effect.dirY), dy = screenY(effect.dirX, effect.dirY);
                        float magnitude = Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
                        dx /= magnitude; dy /= magnitude;
                        if (!effect.heroHit && effect.impactKind == ArenaWorld.ImpactKind.SWORD) {
                            float scale = 1.45f * (effect.critical ? 1.22f : 1f);
                            float rotation = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 25f;
                            batch.setColor(Color.WHITE);
                            art.vfx.draw(batch, VfxArt.Animation.SWORD_HIT, effect.maxTtl - effect.ttl,
                                impactX, impactY, scale, rotation, VfxArt.Layer.ALL);
                        } else {
                            float reach = (effect.heroHit ? 25f : 18f) * (1f - life * .55f);
                            batch.setColor(1f, effect.heroHit ? .22f : .84f, effect.heroHit ? .12f : .58f, life);
                            burst(x, y + lift, dx, dy, reach, effect.heroHit ? 3.5f : 2.2f, 5);
                            batch.setColor(1f, effect.heroHit ? .3f : .92f, effect.heroHit ? .16f : .72f, life * .45f);
                            batch.draw(art.glow, x - 18f, y + lift - 18f, 36f, 36f);
                        }
                        if (effect.critical) {
                            batch.setColor(1f, .92f, .57f, life * .9f);
                            burst(impactX, impactY, dx, dy, 32f * (1f - life * .4f), 2f, 3);
                        }
                    }
                }
                case DEATH -> {
                    boolean spirit = effect.enemyKind == ArenaWorld.EnemyKind.WRAITH;
                    float radius = 9f + 28f * (1f - life);
                    if (spirit) batch.setColor(.4f, .83f, .84f, life * .42f);
                    else batch.setColor(.72f, .62f, .44f, life * .32f);
                    batch.draw(art.glow, x - radius, y - radius * .3f, radius * 2f, radius);
                    for (int i = 0; i < 4; i++) {
                        float sign = (i & 1) == 0 ? -1f : 1f;
                        float drift = sign * radius * (.3f + i * .2f);
                        float rise = (1f - life) * (spirit ? 58f : 22f) + i * 4f;
                        if (spirit) {
                            batch.setColor(.67f, .93f, .91f, life * .75f);
                            batch.draw(art.soul, x + drift - 3f, y + rise, 6f, 10f);
                        } else {
                            batch.setColor(.87f, .73f, .49f, life * .7f);
                            line(x + drift, y + rise, x + drift + sign * 5f, y + rise + 4f * life, 2f * life + .5f);
                        }
                    }
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
                case DODGE -> {
                    float dx = screenX(effect.dirX, effect.dirY), dy = screenY(effect.dirX, effect.dirY);
                    float length = Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
                    dx /= length; dy /= length;
                    batch.setColor(effect.owner == 0 ? 1f : .4f, .82f, .57f, life * .55f);
                    for (int i = -1; i <= 1; i++) {
                        float ox = -dy * i * 7f, oy = dx * i * 7f;
                        line(x + ox - dx * 17f, y + oy + 3f - dy * 17f,
                            x + ox + dx * 26f * (1f - life), y + oy + 3f + dy * 26f * (1f - life), 2f * life + .6f);
                    }
                }
                case COLLECT -> {
                    for (int i = 0; i < 3; i++) {
                        float phase = 1f - life, side = (i - 1) * (12f - 7f * phase);
                        batch.setColor(effect.healing ? 1f : .48f, effect.healing ? .54f : .88f,
                            effect.healing ? .32f : .84f, life * .8f);
                        batch.draw(art.soul, x + side - 3f, y + 12f + phase * 35f + i * 5f, 6f, 9f);
                    }
                }
                case BLOCK -> {
                    float dx = screenX(effect.dirX, effect.dirY), dy = screenY(effect.dirX, effect.dirY);
                    float length = Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
                    dx /= length; dy /= length;
                    float cx = x - dx * 15f, cy = y + impactLift(effect) - dy * 15f;
                    batch.setColor(1f, .91f, .6f, life);
                    line(cx - dy * 16f, cy + dx * 16f, cx - dx * 8f, cy - dy * 8f, 3f * life + 1f);
                    line(cx - dx * 8f, cy - dy * 8f, cx + dy * 16f, cy - dx * 16f, 3f * life + 1f);
                }
            }
        }
        batch.setColor(Color.WHITE);
    }

    private float effectMargin(ArenaWorld.Effect effect) {
        float radius = effect.radius + 80f;
        if (effect.kind == ArenaWorld.EffectKind.HIT) {
            float reach = effect.impactKind == ArenaWorld.ImpactKind.BOLT
                ? 42f * (effect.critical ? 1.22f : 1f)
                : art.vfx.maxRadius(VfxArt.Animation.SWORD_HIT) * 1.8f;
            radius = Math.max(radius, reach + Math.max(100f, impactLift(effect)));
        }
        return radius;
    }

    /** Short circular impact in screen space: core flash, soft bloom, six round motes and one ring. */
    private void projectileBurst(float x, float y, float life, boolean critical) {
        float age = 1f - life;
        float scale = critical ? 1.22f : 1f;
        float radius = (8f + 24f * (float) Math.sqrt(age)) * scale;
        float fade = life * life;
        float glowRadius = radius + 7f * scale;
        batch.setColor(.3f, .8f, 1f, fade * .6f);
        batch.draw(art.glow, x - glowRadius, y - glowRadius, glowRadius * 2f, glowRadius * 2f);
        // The bright center is gone during the latter part of the existing pooled hit's lifetime.
        float flash = Math.max(0f, 1f - age * 3.3f);
        float coreRadius = (5f + 8f * flash) * scale;
        batch.setColor(.9f, 1f, 1f, flash * .95f);
        batch.draw(art.glow, x - coreRadius, y - coreRadius, coreRadius * 2f, coreRadius * 2f);
        batch.setColor(critical ? 1f : .6f, critical ? .92f : .94f, critical ? .65f : 1f, fade * .85f);
        float moteRadius = (3f * life + .7f) * scale;
        for (int i = 0; i < 6; i++) {
            float angle = MathUtils.PI2 * i / 6f + MathUtils.PI / 6f;
            float px = x + MathUtils.cos(angle) * radius, py = y + MathUtils.sin(angle) * radius;
            batch.draw(art.glow, px - moteRadius, py - moteRadius, moteRadius * 2f, moteRadius * 2f);
        }
        batch.setColor(.65f, .96f, 1f, fade * .8f);
        float previousX = x + radius, previousY = y;
        for (int i = 1; i <= 24; i++) {
            float angle = MathUtils.PI2 * i / 24f;
            float px = x + MathUtils.cos(angle) * radius, py = y + MathUtils.sin(angle) * radius;
            line(previousX, previousY, px, py, (.6f + 1.6f * life) * scale);
            previousX = px; previousY = py;
        }
    }

    private float impactLift(ArenaWorld.Effect effect) {
        if (effect.heroHit) return art.actor(ActorAnimation.actor(ArenaWorld.Hero.WARDEN)).worldHeight * .5f;
        if (effect.enemyKind == null) return 38f;
        return art.actor(ActorAnimation.actor(effect.enemyKind)).worldHeight * .5f
            * enemyScale(effect.enemyKind, effect.elite);
    }

    private void burst(float x, float y, float dx, float dy, float radius, float width, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (i - (count - 1) * .5f) * .47f;
            float ux = dx * MathUtils.cos(angle) - dy * MathUtils.sin(angle);
            float uy = dx * MathUtils.sin(angle) + dy * MathUtils.cos(angle);
            line(x + ux * radius * .2f, y + uy * radius * .2f,
                x + ux * radius, y + uy * radius, width);
        }
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
        return enemyScale(enemy.kind, enemy.elite);
    }

    private static float enemyScale(ArenaWorld.EnemyKind kind, boolean elite) {
        float scale = kind == ArenaWorld.EnemyKind.BOSS ? 2.2f
            : kind == ArenaWorld.EnemyKind.BRUTE ? 1.4f
            : kind == ArenaWorld.EnemyKind.WRAITH ? .82f : 1f;
        return scale * (elite ? 1.18f : 1f);
    }

    private static float spawnScale(ArenaWorld.Enemy enemy) {
        return Math.min(1.8f, 1.4f * enemyScale(enemy));
    }

    private void drawArrival(ArenaWorld.Enemy enemy, VfxArt.Layer layer) {
        batch.setColor(Color.WHITE);
        art.vfx.draw(batch, VfxArt.Animation.SPAWN, enemy.spawnAge,
            screenX(enemy.x, enemy.y), screenY(enemy.x, enemy.y), spawnScale(enemy), 0f, layer);
    }

    private void drawLevelUp(ArenaWorld world, ArenaWorld.Player player, VfxArt.Layer layer) {
        batch.setColor(Color.WHITE);
        art.vfx.draw(batch, VfxArt.Animation.LEVEL_UP, world.levelUpFxAge,
            screenX(player.x, player.y), screenY(player.x, player.y), LEVEL_FX_SCALE, 0f, layer);
    }

    private void drawSprites(ArenaWorld world, OrthographicCamera camera, int viewer) {
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i];
            if (types[i] == PAINTED_BORDER) {
                batch.setColor(Color.WHITE);
                paintedBorders.draw(batch, index);
            } else if (types[i] == PROP) {
                drawEnvironment(world, camera, viewer, index);
            } else if (types[i] == ENEMY) {
                ArenaWorld.Enemy enemy = world.enemies[index];
                float scale = enemyScale(enemy);
                drawArrival(enemy, VfxArt.Layer.BACK);
                drawEnemyAura(enemy, EnemyAttackArt.Layer.BACK);
                // Original painted identities retain their own colours. Damage feedback is transient.
                if (enemy.hitFlash > 0f) batch.setColor(1f, .8f, .63f, 1f);
                else batch.setColor(Color.WHITE);
                GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(enemy.kind));
                ActorAnimation.State state = ActorAnimation.state(enemy);
                float recoil = MathUtils.sin(MathUtils.clamp(enemy.hitFlash / .12f, 0f, 1f) * MathUtils.PI)
                    * (enemy.kind == ArenaWorld.EnemyKind.BOSS ? 1.5f : enemy.kind == ArenaWorld.EnemyKind.BRUTE ? 4f : 8f);
                // This short reaction moves only the paint. Ground root, shadow, sort and collision stay fixed.
                if (enemy.attack != ArenaWorld.EnemyAttack.NONE) {
                    ActorAnimation.Facing facing = ActorAnimation.facing(enemy, state);
                    float sx = screenX(enemy.x, enemy.y), sy = screenY(enemy.x, enemy.y);
                    if (enemy.attack == ArenaWorld.EnemyAttack.WRAITH_DASH
                        && enemy.attackAge >= ArenaWorld.WRAITH_WINDUP
                        && enemy.attackAge < ArenaWorld.WRAITH_WINDUP + ArenaWorld.WRAITH_DASH_SECONDS) {
                        for (int ghost = 2; ghost >= 1; ghost--) {
                            batch.setColor(1f, 1f, 1f, ghost == 1 ? .24f : .12f);
                            art.enemyAttacks.drawActor(batch, enemy.kind, facing, enemy.attackAge,
                                sx - screenX(enemy.castFacingX, enemy.castFacingY) * ghost * 18f,
                                sy - screenY(enemy.castFacingX, enemy.castFacingY) * ghost * 18f,
                                actor.pixelScale * scale);
                        }
                        batch.setColor(Color.WHITE);
                    }
                    art.enemyAttacks.drawActor(batch, enemy.kind, facing, enemy.attackAge,
                        sx + screenX(enemy.hitDirectionX, enemy.hitDirectionY) * recoil,
                        sy + screenY(enemy.hitDirectionX, enemy.hitDirectionY) * recoil, actor.pixelScale * scale);
                } else {
                    int frame = actor.clip(state, ActorAnimation.facing(enemy, state)).frame(ActorAnimation.phase(enemy, state));
                    drawFrameOffset(frame, enemy.x, enemy.y, actor.pixelScale * scale,
                        screenX(enemy.hitDirectionX, enemy.hitDirectionY) * recoil,
                        screenY(enemy.hitDirectionX, enemy.hitDirectionY) * recoil);
                }
                drawEnemyAura(enemy, EnemyAttackArt.Layer.FRONT);
                drawArrival(enemy, VfxArt.Layer.FRONT);
                if (enemy.elite && enemy.kind != ArenaWorld.EnemyKind.BOSS) {
                    float x = screenX(enemy.x, enemy.y), y = screenY(enemy.x, enemy.y) + actor.maxAbove * scale + 4f;
                    batch.setColor(.15f, .1f, .12f, .85f); batch.draw(art.pixel, x - 25f, y, 50f, 3f);
                    batch.setColor(.88f, .38f, .27f, .95f);
                    batch.draw(art.pixel, x - 25f, y, 50f * Math.max(0f, Math.min(1f, enemy.health / Math.max(1f, enemy.maxHealth))), 3f);
                }
                batch.setColor(Color.WHITE);
            } else {
                ArenaWorld.Player player = world.players[index];
                float sx = screenX(player.x, player.y), sy = screenY(player.x, player.y);
                GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(player.hero));
                ActorAnimation.State state = ActorAnimation.state(player);
                int frame = actor.clip(state, ActorAnimation.facing(player, state)).frame(ActorAnimation.phase(player, state));
                if (!player.alive) {
                    batch.setColor(.65f, .65f, .65f, .8f);
                    drawFrame(frame, player.x, player.y, actor.pixelScale);
                    batch.setColor(.9f, .68f, .33f, 1f);
                    batch.draw(art.pixel, sx - 26f, sy + REVIVE_BAR_Y,
                        52f * Math.min(1f, player.reviveProgress / 2f), REVIVE_BAR_HEIGHT);
                    batch.setColor(Color.WHITE);
                    continue;
                }
                if (player.dodgeTime > 0f) {
                    batch.setColor(index == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
                    batch.draw(art.glow, sx - 47f, sy - 10f, 94f, 58f);
                    for (int ghost = 2; ghost >= 1; ghost--) {
                        float distance = ghost * 13f;
                        if (index == 0) batch.setColor(1f, .82f, .58f, ghost == 1 ? .19f : .1f);
                        else batch.setColor(.42f, .86f, .85f, ghost == 1 ? .19f : .1f);
                        drawFrameOffset(frame, player.x, player.y, actor.pixelScale,
                            -screenX(player.dodgeFacingX(), player.dodgeFacingY()) * distance,
                            -screenY(player.dodgeFacingX(), player.dodgeFacingY()) * distance);
                    }
                }
                drawLevelUp(world, player, VfxArt.Layer.BACK);
                float alpha = player.invulnerable > 0f && ((int) (world.elapsed * 16f) & 1) == 0 ? .58f : 1f;
                if (player.hurtTime > 0f) batch.setColor(1f, .57f, .44f, alpha);
                else batch.setColor(1f, 1f, 1f, alpha);
                float recoil = player.hero == ArenaWorld.Hero.RANGER && player.attackAge < .09f
                    ? 3.4f * (1f - player.attackAge / .09f) : 0f;
                drawFrameOffset(frame, player.x, player.y, actor.pixelScale,
                    -screenX(player.attackFacingX, player.attackFacingY) * recoil,
                    -screenY(player.attackFacingX, player.attackFacingY) * recoil);
                drawLevelUp(world, player, VfxArt.Layer.FRONT);
                drawMuzzle(player);
                batch.setColor(Color.WHITE);
            }
        }
    }

    /** Each camera computes its own reveal; shared placements and cached floor colours stay immutable. */
    private void drawEnvironment(ArenaWorld world, OrthographicCamera camera, int viewer, int index) {
        ArenaLayout.Placement placement = layout.placements[index];
        GameplayAtlas.Frame frame = art.metadata.frames[propArt[index].frame()];
        float rootX = screenX(placement.x, placement.y), rootY = screenY(placement.x, placement.y);
        float left = rootX + frame.left * placement.scale, bottom = rootY + frame.bottom * placement.scale;
        float width = frame.width * placement.scale, height = frame.height * placement.scale;
        float shear = propArt[index].renderShearY();
        float leftShift = (left - rootX) * shear, rightShift = (left + width - rootX) * shear;
        float lowShift = Math.min(leftShift, rightShift), highShift = Math.max(leftShift, rightShift);
        TextureRegion region = art.frame(propArt[index].frame());
        float groundSlope = placement.region.startsWith("wall-x") ? .5f
            : placement.region.startsWith("wall-y") ? -.5f : 0f;
        if (!placement.fadeWhenOccluding || height <= ENVIRONMENT_BASE_HEIGHT * 2f
            || !environmentOccludes(world, camera, viewer, groundSlope, rootY, left, rootY + ENVIRONMENT_BASE_HEIGHT + lowShift,
                left + width, bottom + height + highShift)) {
            batch.setColor(Color.WHITE);
            drawEnvironmentPart(region, left, bottom, width, height, region.getV2(), region.getV(), rootX, shear);
            return;
        }
        // Keep the ground footprint and the low wall/landmark base solid. Reveal only its upper paint.
        float baseHeight = MathUtils.clamp(rootY + ENVIRONMENT_BASE_HEIGHT - bottom, 0f, height);
        float splitV = MathUtils.lerp(region.getV2(), region.getV(), baseHeight / height);
        batch.setColor(Color.WHITE);
        if (baseHeight > 0f)
            drawEnvironmentPart(region, left, bottom, width, baseHeight, region.getV2(), splitV, rootX, shear);
        batch.setColor(1f, 1f, 1f, placement.wall ? .14f : .24f);
        if (baseHeight < height)
            drawEnvironmentPart(region, left, bottom + baseHeight, width, height - baseHeight,
                splitV, region.getV(), rootX, shear);
        batch.setColor(Color.WHITE);
    }

    /** Calibrate painted ground edges to the floor projection using the existing four sprite vertices. */
    private void drawEnvironmentPart(TextureRegion region, float x, float y, float width, float height,
                                     float bottomV, float topV, float rootX, float shear) {
        if (shear == 0f) {
            batch.draw(region.getTexture(), x, y, width, height, region.getU(), bottomV, region.getU2(), topV);
            return;
        }
        float leftY = environmentProjectedY(y, x, rootX, shear);
        float rightY = environmentProjectedY(y, x + width, rootX, shear);
        float color = batch.getPackedColor();
        environmentVertex(0, x, leftY, color, region.getU(), bottomV);
        environmentVertex(5, x, leftY + height, color, region.getU(), topV);
        environmentVertex(10, x + width, rightY + height, color, region.getU2(), topV);
        environmentVertex(15, x + width, rightY, color, region.getU2(), bottomV);
        batch.draw(region.getTexture(), environmentVertices, 0, environmentVertices.length);
    }

    private void environmentVertex(int offset, float x, float y, float color, float u, float v) {
        environmentVertices[offset] = x; environmentVertices[offset + 1] = y;
        environmentVertices[offset + 2] = color; environmentVertices[offset + 3] = u;
        environmentVertices[offset + 4] = v;
    }

    static float environmentProjectedY(float y, float x, float rootX, float shear) {
        return y + (x - rootX) * shear;
    }

    private boolean environmentOccludes(ArenaWorld world, OrthographicCamera camera, int viewer, float groundSlope,
                                         float rootY, float left, float bottom, float right, float top) {
        ArenaWorld.Player self = world.players[viewer];
        if (environmentOccludesActor(groundSlope, rootY, left, bottom, right, top, self.x, self.y,
            art.actor(ActorAnimation.actor(self.hero)), 1f)) return true;
        ArenaWorld.Player ally = world.players[1 - viewer];
        if (ally.active && visibleActor(camera, ally.x, ally.y, art.actor(ActorAnimation.actor(ally.hero)), 1f)
            && environmentOccludesActor(groundSlope, rootY, left, bottom, right, top, ally.x, ally.y,
                art.actor(ActorAnimation.actor(ally.hero)), 1f)) return true;
        // Only sprites already gathered for this view can trigger a reveal; off-screen enemies cannot.
        for (int i = 0; i < sortCount; i++) if (types[i] == ENEMY) {
            ArenaWorld.Enemy enemy = world.enemies[indices[i]];
            if (environmentOccludesActor(groundSlope, rootY, left, bottom, right, top, enemy.x, enemy.y,
                art.actor(ActorAnimation.actor(enemy.kind)), enemyScale(enemy))) return true;
        }
        return false;
    }

    private static boolean environmentOccludesActor(float groundSlope, float rootY, float left, float bottom, float right, float top,
                                                     float x, float y, GameplayAtlas.Actor actor, float scale) {
        return environmentOccludesBody(groundSlope, rootY, left, bottom, right, top,
            screenX(x, y), screenY(x, y), Math.max(actor.maxLeft, actor.maxRight) * scale,
            actor.maxAbove * scale);
    }

    /** Ground-depth ordering and upper-body overlap decide fading, never distance or another camera's state. */
    static boolean environmentOccludesBody(float rootY, float left, float bottom, float right, float top,
                                           float actorX, float actorY, float halfWidth, float actorHeight) {
        return environmentOccludesBody(0f, rootY, left, bottom, right, top, actorX, actorY, halfWidth, actorHeight);
    }

    static boolean environmentOccludesBody(float groundSlope, float rootY, float left, float bottom, float right, float top,
                                           float actorX, float actorY, float halfWidth, float actorHeight) {
        // A diagonal wall's far end can be high in its rectangle while stone beside the hero stays low.
        float localTop = Math.min(top, top - Math.abs(groundSlope) * (right - left) * .5f
            + groundSlope * (actorX - (left + right) * .5f) + Math.abs(groundSlope) * halfWidth);
        return rootY < actorY && left < actorX + halfWidth && right > actorX - halfWidth
            && bottom < actorY + actorHeight && localTop > actorY + actorHeight * .7f;
    }

    private void drawFrame(int index, float x, float y, float scale) {
        drawFrameOffset(index, x, y, scale, 0f, 0f);
    }

    private void drawFrameOffset(int index, float x, float y, float scale, float offsetX, float offsetY) {
        GameplayAtlas.Frame frame = art.metadata.frames[index];
        batch.draw(art.frame(index), screenX(x, y) + frame.left * scale + offsetX,
            screenY(x, y) + frame.bottom * scale + offsetY, frame.width * scale, frame.height * scale);
    }

    private void drawMuzzle(ArenaWorld.Player player) {
        if (player.hero != ArenaWorld.Hero.RANGER || player.attackAge >= .1f) return;
        int origin = ActorAnimation.facing(player.attackFacingX, player.attackFacingY, ActorAnimation.Facing.SE).ordinal() * 2;
        float scale = art.actor(ActorAnimation.Actor.RANGER).pixelScale;
        float x = screenX(player.x, player.y) + BOLT_ORIGIN[origin] * scale;
        float y = screenY(player.x, player.y) + BOLT_ORIGIN[origin + 1] * scale;
        float life = 1f - player.attackAge / .1f;
        float dx = screenX(player.attackFacingX, player.attackFacingY), dy = screenY(player.attackFacingX, player.attackFacingY);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.setColor(.48f, .88f, 1f, life * .65f);
        batch.draw(art.glow, x - 18f, y - 18f, 36f, 36f);
        batch.setColor(1f, 1f, 1f, life * .85f);
        art.projectiles.draw(batch, MagicProjectileArt.Type.RANGER, player.attackAge, x, y, .6f + life * .25f, angle);
        batch.setColor(Color.WHITE);
    }

    private static boolean visibleActor(OrthographicCamera camera, float x, float y,
                                        GameplayAtlas.Actor actor, float scale) {
        // Include a small shadow/elite-bar margin around the precomputed complete action envelope.
        float above = actor.maxAbove * scale + 12f;
        if (actor.id == ActorAnimation.Actor.WARDEN || actor.id == ActorAnimation.Actor.RANGER)
            above = Math.max(above, REVIVE_BAR_Y + REVIVE_BAR_HEIGHT);
        return visible(camera, x, y, Math.max(40f, actor.maxLeft * scale) + RECOIL_MARGIN,
            Math.max(40f, actor.maxRight * scale) + RECOIL_MARGIN, above + RECOIL_MARGIN,
            Math.max(12f, actor.maxBelow * scale) + RECOIL_MARGIN);
    }

    private static boolean visible(OrthographicCamera camera, float x, float y,
                                   float left, float right, float above, float below) {
        float sx = screenX(x, y) - camera.position.x, sy = screenY(x, y) - camera.position.y;
        return sx + right >= -camera.viewportWidth * .5f && sx - left <= camera.viewportWidth * .5f
            && sy + above >= -camera.viewportHeight * .5f && sy - below <= camera.viewportHeight * .5f;
    }

    private static boolean visible(OrthographicCamera camera, float x, float y, float margin) {
        return visibleProjected(camera, screenX(x, y), screenY(x, y), margin);
    }
    private static boolean visibleProjected(OrthographicCamera camera, float x, float y, float margin) {
        return Math.abs(x - camera.position.x) <= camera.viewportWidth * .5f + margin
            && Math.abs(y - camera.position.y) <= camera.viewportHeight * .5f + margin;
    }
    private static float screenX(float x, float y) { return (x - y) * ISO_X; }
    private static float screenY(float x, float y) { return (x + y) * ISO_Y; }
    @Override public void dispose() { floorCache.dispose(); batch.dispose(); }
}
