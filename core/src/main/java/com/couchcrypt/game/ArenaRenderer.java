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
    private static final float SOUL_PULSE_RADIUS = 60f;
    // Keep the painted cutting trail near the weapon's scale, independent of reach upgrades.
    private static final float WARDEN_SLASH_RADIUS = 48f;
    // Fixed SE/SW/NE/NW release landmarks, in untrimmed source pixels relative to the ground root.
    // Only the rendered origin moves; the simulation keeps its original projectile coordinates and trajectory.
    private static final float[] BOLT_ORIGIN = {23, 51, -29, 55, 27, 65, -25, 65};
    private static final float[] SOVEREIGN_ORIGIN = {37, 78, -12, 67, 18, 90, -35, 82};
    private static final int PLAYER = 0, ENEMY = 1, PROP = 2, PAINTED_BORDER = 3, DODGE_GHOST = 4, CHEST = 5,
        SENTINEL_BLADE = 6, RETURNING_GLAIVE = 7, WORLD_RELIC = 8, PAINTED_CORNER = 9, PAINTED_GATE = 10;
    private final GameArt art;
    private final GameHud gameHud;
    private final EnemyRankMarkers rankMarkers;
    private final PlayerFootRing playerFootRing;
    private final EnemyGroundVfx enemyGroundVfx;
    private final LootChestVfx chestVfx;
    private final SkillExpansionArt skillArt;
    private final DamageNumberArt damageNumberArt;
    private final ArenaAmbience ambience;
    private final SpriteBatch batch;
    private final ShaderProgram paletteShader;
    private final SoftIris iris;
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
    private final GateWallOpening gateOpening;
    private final PaintedCornerArt.Scene paintedCorners;
    private final PaintedGateArt.Scene paintedGate;
    private final GameplayAtlas.Region[] propArt;
    private final GameplayAtlas.Region chestArt;
    private final float chestScale;
    private final float[] environmentVertices = new float[20];
    private final float[] depths = new float[ArenaWorld.MAX_ENEMIES + 2 + 2 * DodgeTrail.CAPACITY
        + ArenaLayout.MAX_PLACEMENTS + PaintedBorderArt.MAX_SCENERY + PaintedCornerArt.MAX_SCENERY
        + PaintedGateArt.MAX_SCENERY + ArenaWorld.MAX_CHESTS
        + SkillExpansion.MAX_ORBITERS + SkillExpansion.MAX_GLAIVES + SkillExpansion.MAX_RELICS];
    private final int[] types = new int[depths.length], indices = new int[depths.length], layers = new int[depths.length];
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
        paletteShader = EnemyPalette.createShader();
        batch = metrics == null ? new SpriteBatch(1800, paletteShader) : new ProfileBatch(paletteShader);
        iris = new SoftIris();
        art = gameArt; gameHud = new GameHud(gameArt, uiArt, buildIcons, hudArt);
        rankMarkers = new EnemyRankMarkers(art.pixel);
        playerFootRing = new PlayerFootRing(art.pixel);
        enemyGroundVfx = new EnemyGroundVfx(art.pixel, art.glow);
        chestVfx = new LootChestVfx(art.pixel, art.glow);
        skillArt = new SkillExpansionArt(buildIcons, art.glow);
        damageNumberArt = new DamageNumberArt(uiArt.body);
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
        ambience = new ArenaAmbience(floorLayout, layout);
        paintedBorders = art.paintedBorders.place(layout, art.metadata);
        gateOpening = new GateWallOpening(paintedBorders, layout, art.metadata);
        paintedCorners = art.paintedCorners.place(layout, paintedBorders);
        paintedGate = art.paintedGate.place(layout);
        propArt = new GameplayAtlas.Region[layout.placements.length];
        for (int i = 0; i < propArt.length; i++)
            propArt[i] = art.environment(layout.placements[i].region);
        chestArt = art.environment("elite-chest");
        chestScale = chestArt.worldHeight() / art.metadata.frames[chestArt.frame()].sourceHeight;
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
            drawChestLights(world, camera);
            skillArt.ground(batch, world);
            // All floor effects precede the complete depth-sorted actor pass. Their painted
            // foreground rims must never be laid over a closer enemy or the player's body.
            drawHazards(world, camera, SpriteEffectArt.Layer.ALL);
            drawPlayerAuras(world, camera);
            drawGroundMarks(world, camera, SpriteEffectArt.Layer.ALL);
            drawEnemyFloorCues(world, camera);
            drawFootEffects(world, camera);
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
            skillArt.followups(batch, world);
            drawDamageNumbers(world, camera);
            drawEnemyRanks(world);
            batch.end();
            drawCalls += batch.renderCalls;
            if (metrics != null) {
                metrics.endStage(FrameMetrics.WORLD_DRAW);
                worldDraws += batch.renderCalls;
            }
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, width, height);
        // One outer TV iris shades the completed scene; the split divider stays clear.
        if (metrics != null) metrics.beginStage(FrameMetrics.WORLD_DRAW);
        iris.render(split);
        drawCalls++;
        if (metrics != null) {
            metrics.endStage(FrameMetrics.WORLD_DRAW);
            worldDraws++;
            metrics.beginStage(FrameMetrics.HUD);
        }
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
        ProfileBatch(ShaderProgram shader) { super(1800, shader); }
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
        // The continuous painted walls are the only perimeter. The legacy tile
        // foundation included an exterior walkway and a second set of corners.
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
        if (!floorLayout.usesPavingBands()) {
            ArrayList<EngravedFloorArt.Quad> curbs = new ArrayList<>();
            for (float[] polygon : new StonePathBorders(floorLayout).polygons)
                java.util.Collections.addAll(curbs, art.engravedFloor.geometry(art.engravedFloor.curb, polygon, 1f));
            quads += cacheGround(curbs);
        }
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
        for (int i = 0; i < world.chests.length; i++) {
            ArenaWorld.Chest chest = world.chests[i];
            if (chest.active && visible(camera, chest.x, chest.y, 60f)) add(CHEST, i, chest.x, chest.y);
        }
        for (int i = 0; i < layout.placements.length; i++) {
            ArenaLayout.Placement placement = layout.placements[i];
            if (placement.wall || placement.region.equals("sealed-gate")) continue;
            GameplayAtlas.Frame frame = art.metadata.frames[propArt[i].frame()];
            float scale = placement.scale;
            GameplayAtlas.Region source = propArt[i];
            float bottomLeft = EnvironmentProjection.projectedY(frame.left * scale, frame.bottom * scale, source);
            float bottomRight = EnvironmentProjection.projectedY((frame.left + frame.width) * scale, frame.bottom * scale, source);
            float topLeft = EnvironmentProjection.projectedY(frame.left * scale, (frame.bottom + frame.height) * scale, source);
            float topRight = EnvironmentProjection.projectedY((frame.left + frame.width) * scale, (frame.bottom + frame.height) * scale, source);
            if (visible(camera, placement.x, placement.y, Math.max(placement.shadowWidth * .5f, -frame.left * scale),
                Math.max(placement.shadowWidth * .5f, (frame.left + frame.width) * scale),
                Math.max(placement.shadowHeight * .5f, Math.max(topLeft, topRight)),
                Math.max(placement.shadowHeight * .5f, -Math.min(bottomLeft, bottomRight))))
                add(PROP, i, placement.x, placement.y);
        }
        for (int i = 0; i < paintedBorders.quads.length; i++) if (paintedBorders.visible(camera, i)) {
            float[] cut = paintedCorners.wallVertices[i];
            if (cut != null && cut.length == 0) continue;
            PaintedBorderArt.Quad q = paintedBorders.quads[i];
            add(PAINTED_BORDER, i, q.worldX, q.worldY);
            depths[sortCount - 1] = q.sortDepth;
            layers[sortCount - 1] = q.renderLayer;
        }
        for (int i = 0; i < paintedCorners.quads.length; i++) if (paintedCorners.visible(camera, i)) {
            PaintedCornerArt.Quad q = paintedCorners.quads[i];
            add(PAINTED_CORNER, i, q.worldX, q.worldY);
            depths[sortCount - 1] = q.sortDepth;
            layers[sortCount - 1] = q.renderLayer;
        }
        for (int i = 0; i < paintedGate.quads.length; i++) if (paintedGate.visible(camera, i)) {
            PaintedGateArt.Quad q = paintedGate.quads[i];
            add(PAINTED_GATE, i, q.worldX, q.worldY);
            depths[sortCount - 1] = q.sortDepth;
            layers[sortCount - 1] = q.renderLayer;
        }
        for (int i = 0; i < world.skills.orbiters.length; i++) {
            SkillExpansion.OrbitBlade blade = world.skills.orbiters[i];
            if (blade.active && visible(camera, blade.x, blade.y, 70f))
                add(SENTINEL_BLADE, i, blade.x, blade.y);
        }
        for (int i = 0; i < world.skills.glaives.length; i++) {
            SkillExpansion.Glaive glaive = world.skills.glaives[i];
            if (glaive.active && visible(camera, glaive.x, glaive.y, 70f))
                add(RETURNING_GLAIVE, i, glaive.x, glaive.y);
        }
        for (int i = 0; i < world.skills.relics.length; i++) {
            SkillExpansion.WorldRelic relic = world.skills.relics[i];
            if (relic.active && visible(camera, relic.x, relic.y, 65f))
                add(WORLD_RELIC, i, relic.x, relic.y);
        }
        for (int i = 0; i < world.enemies.length; i++) {
            ArenaWorld.Enemy enemy = world.enemies[i];
            if (!enemy.active) continue;
            boolean body = visibleActor(camera, enemy.x, enemy.y,
                art.actor(ActorAnimation.actor(enemy.kind)), enemyScale(enemy));
            boolean arrival = enemy.spawnAge < art.vfx.duration(VfxArt.Animation.SPAWN)
                && visible(camera, enemy.x, enemy.y, art.vfx.maxRadius(VfxArt.Animation.SPAWN) * spawnScale(enemy));
            boolean attack = enemy.attack != ArenaWorld.EnemyAttack.NONE && art.enemyAttacks.hasActor(enemy.kind)
                && visible(camera, enemy.x, enemy.y, art.enemyAttacks.actorRadius(enemy.kind)
                    * art.actor(ActorAnimation.actor(enemy.kind)).pixelScale * enemyScale(enemy) + 20f);
            if (body || arrival || attack) add(ENEMY, i, enemy.x, enemy.y);
        }
        for (int i = 0; i < world.players.length; i++) {
            ArenaWorld.Player player = world.players[i];
            if (!player.active) continue;
            if (player.alive) for (int ghost = 0; ghost < DodgeTrail.CAPACITY; ghost++) {
                DodgeTrail.Snapshot snapshot = player.dodgeTrail.snapshots[ghost];
                if (snapshot.ttl > 0 && visibleActor(camera, snapshot.x, snapshot.y,
                    art.actor(ActorAnimation.actor(snapshot.hero)), 1f))
                    add(DODGE_GHOST, i * DodgeTrail.CAPACITY + ghost, snapshot.x, snapshot.y);
            }
            boolean body = visibleActor(camera, player.x, player.y,
                art.actor(ActorAnimation.actor(player.hero)), 1f);
            boolean level = player.alive && world.levelUpFxAge < art.vfx.duration(VfxArt.Animation.LEVEL_UP)
                && visible(camera, player.x, player.y, art.vfx.maxRadius(VfxArt.Animation.LEVEL_UP) * LEVEL_FX_SCALE);
            if (body || level) add(PLAYER, i, player.x, player.y);
        }
        // Rear/near wall planes enclose every actor. Atlas storage columns are
        // never independent obstacles that can slice one body at a packing seam.
        // Within each plane/pass, keep the existing stable ground-depth ordering.
        for (int i = 1; i < sortCount; i++) {
            float depth = depths[i]; int type = types[i], index = indices[i], layer = layers[i], j = i - 1;
            while (j >= 0 && (layers[j] > layer || layers[j] == layer && depths[j] < depth)) {
                depths[j + 1] = depths[j]; types[j + 1] = types[j]; indices[j + 1] = indices[j]; layers[j + 1] = layers[j]; j--;
            }
            depths[j + 1] = depth; types[j + 1] = type; indices[j + 1] = index; layers[j + 1] = layer;
        }
    }

    private void add(int type, int index, float x, float y) {
        layers[sortCount] = PaintedBorderArt.INTERIOR_LAYER;
        types[sortCount] = type; indices[sortCount] = index; depths[sortCount++] = screenY(x, y);
    }

    private void drawShadows(ArenaWorld world) {
        batch.setColor(1f, 1f, 1f, .62f);
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i]; float x, y, width;
            if (types[i] == PAINTED_BORDER || types[i] == PAINTED_CORNER || types[i] == PAINTED_GATE || types[i] == DODGE_GHOST
                || types[i] == SENTINEL_BLADE || types[i] == RETURNING_GLAIVE || types[i] == WORLD_RELIC) continue;
            if (types[i] == PLAYER) {
                ArenaWorld.Player player = world.players[index];
                x = player.x; y = player.y; width = player.alive ? 62f : 72f;
            } else if (types[i] == ENEMY) {
                x = world.enemies[index].x; y = world.enemies[index].y; width = 44f * enemyScale(world.enemies[index]);
            } else if (types[i] == CHEST) {
                ArenaWorld.Chest chest = world.chests[index];
                batch.draw(art.shadow, screenX(chest.x, chest.y) - 24f,
                    screenY(chest.x, chest.y) - 8f, 48f, 18f);
                continue;
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
            float fade = pickup.healing && pickup.maxTtl > 0 ? MathUtils.clamp(pickup.ttl / 5f, 0, 1) : 1;
            if (pickup.healing) batch.setColor(.9f, .24f, .18f, .65f * fade);
            else batch.setColor(.3f, .8f, .78f, .45f);
            batch.draw(art.glow, x - 17f, y - 8f, 34f, 25f);
            batch.setColor(Color.WHITE);
            if (pickup.healing) {
                batch.setColor(.95f, .34f, .23f, fade);
                batch.draw(art.pixel, x - 2f, y + 1f, 4f, 13f);
                batch.draw(art.pixel, x - 6f, y + 5f, 12f, 4f);
            } else batch.draw(art.soul, x - 7f, y - 1f, 14f, 20f);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawChestLights(ArenaWorld world, OrthographicCamera camera) {
        boolean lit = false;
        for (ArenaWorld.Chest chest : world.chests) {
            if (!chest.active || !visible(camera, chest.x, chest.y, 75f)) continue;
            if (!lit) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                lit = true;
            }
            chestVfx.groundLight(batch, screenX(chest.x, chest.y), screenY(chest.x, chest.y));
        }
        if (!lit) return;
        // Batch all reward lights together; restore ordinary blending before warnings/actors.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        for (ArenaWorld.Chest chest : world.chests)
            if (chest.active && visible(camera, chest.x, chest.y, 75f))
                chestVfx.groundRim(batch, screenX(chest.x, chest.y), screenY(chest.x, chest.y));
    }

    private void drawDust(ArenaWorld world, OrthographicCamera camera) {
        // Both cameras sample one fixed world field; combat pauses freeze all ambient motion.
        ambience.draw(batch, art, camera, world.elapsed);
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
            boolean graveKing = shot.casterKind == ArenaWorld.EnemyKind.BOSS;
            boolean sovereign = shot.casterKind == ArenaWorld.EnemyKind.SOVEREIGN;
            boolean boss = graveKing || sovereign;
            ActorAnimation.Facing facing = ActorAnimation.facing(shot.launchFacingX,
                shot.launchFacingY, ActorAnimation.Facing.SE);
            int origin = facing.ordinal() * 2;
            float scale = art.actor(ActorAnimation.actor(shot.casterKind)).pixelScale * shot.casterRenderScale;
            // The launch pose is a value snapshot: turning, death and pool reuse cannot move a missile's emitter.
            float ox = sovereign ? SOVEREIGN_ORIGIN[origin] : graveKing ? 0f
                : art.enemyAttacks.muzzleX(ArenaWorld.EnemyKind.CULTIST, facing);
            float oy = sovereign ? SOVEREIGN_ORIGIN[origin + 1] : graveKing ? 36f
                : art.enemyAttacks.muzzleY(ArenaWorld.EnemyKind.CULTIST, facing);
            float x = screenX(shot.x, shot.y) + ox * scale;
            float y = screenY(shot.x, shot.y) + oy * scale
                + (graveKing ? graveKingHover(world.elapsed - shot.age) : 0f);
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
            float sampleAge = age - ghost * .025f;
            if (sampleAge < 0) continue;
            batch.setColor(1f, 1f, 1f, ghost == 1 ? .2f : .09f);
            art.projectiles.draw(batch, type, sampleAge,
                x - dx * ghost * 9f, y - dy * ghost * 9f,
                scale * ProjectilePresentation.spawnScale(sampleAge), angle);
        }
        batch.setColor(Color.WHITE);
        art.projectiles.draw(batch, type, age, x, y, scale * ProjectilePresentation.spawnScale(age), angle);
    }

    private void drawHazards(ArenaWorld world, OrthographicCamera camera, SpriteEffectArt.Layer layer) {
        for (ArenaWorld.Hazard hazard : world.hazards) {
            if (!hazard.active || !visible(camera, hazard.x, hazard.y, hazard.radius + 50f)) continue;
            if (hazard.warning > 0f) {
                drawWarningRune(.94f, hazard.x, hazard.y, hazard.radius);
            } else {
                float fade = MathUtils.clamp(hazard.ttl / .3f, 0, 1);
                playerFootRing.draw(batch, screenX(hazard.x, hazard.y), screenY(hazard.x, hazard.y),
                    hazard.radius, 1f, .63f, .38f, fade);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawGroundMarks(ArenaWorld world, OrthographicCamera camera, SpriteEffectArt.Layer layer) {
        for (ArenaWorld.Enemy enemy : world.enemies) {
            if (!enemy.active || enemy.attack == ArenaWorld.EnemyAttack.NONE) continue;
            float age = enemy.attackAge;
            if (enemy.kind == ArenaWorld.EnemyKind.MOURNING_BELL) {
                drawBellGroundCues(enemy, camera);
            } else if (enemy.attack == ArenaWorld.EnemyAttack.BRUTE_SLAM) {
                if (!visible(camera, enemy.attackTargetX, enemy.attackTargetY, enemy.attackRadius + 80f)) continue;
                if (age < ArenaWorld.BRUTE_WINDUP) {
                    drawWarningRune(.94f, enemy.attackTargetX, enemy.attackTargetY, enemy.attackRadius);
                }
                float strikeAge = age - ArenaWorld.BRUTE_WINDUP;
                if (strikeAge >= 0 && strikeAge < .3f) {
                    playerFootRing.draw(batch, screenX(enemy.attackTargetX, enemy.attackTargetY),
                        screenY(enemy.attackTargetX, enemy.attackTargetY), enemy.attackRadius,
                        1f, .63f, .38f, 1f - strikeAge / .3f);
                    enemyGroundVfx.bruteSmash(batch, enemy.attackTargetX, enemy.attackTargetY,
                        enemy.attackRadius, strikeAge / .3f);
                }
            } else if (enemy.attack == ArenaWorld.EnemyAttack.WRAITH_DASH
                && age < ArenaWorld.WRAITH_WINDUP + ArenaWorld.WRAITH_DASH_SECONDS) {
                // Three compact arrows point along the fixed lane without becoming a guide bar.
                float dx = enemy.attackTargetX - enemy.attackStartX;
                float dy = enemy.attackTargetY - enemy.attackStartY;
                float length = Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
                dx /= length; dy /= length;
                boolean released = age >= ArenaWorld.WRAITH_WINDUP;
                float alpha = released ? .68f * (1f - (age - ArenaWorld.WRAITH_WINDUP)
                    / ArenaWorld.WRAITH_DASH_SECONDS) : .96f;
                for (int arrow = 1; arrow <= 3; arrow++) {
                    float along = arrow * .24f;
                    float x = MathUtils.lerp(enemy.attackStartX, enemy.attackTargetX, along);
                    float y = MathUtils.lerp(enemy.attackStartY, enemy.attackTargetY, along);
                    if (!visible(camera, x, y, 40f)) continue;
                    enemyGroundVfx.arrow(batch, x, y, dx, dy, alpha, released);
                }
            } else if (enemy.attack == ArenaWorld.EnemyAttack.CULTIST_CAST
                && age >= ArenaWorld.CULTIST_WINDUP - .15f && age < ArenaWorld.CULTIST_WINDUP
                && visible(camera, enemy.attackTargetX, enemy.attackTargetY, 50f)) {
                drawWarningRune(.94f, enemy.attackTargetX, enemy.attackTargetY, 18f);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawBellGroundCues(ArenaWorld.Enemy enemy, OrthographicCamera camera) {
        if (enemy.attack == ArenaWorld.EnemyAttack.BELL_LUNGE) {
            float age = enemy.attackAge;
            if (age >= MourningBell.LUNGE_WINDUP + MourningBell.LUNGE_SECONDS) return;
            boolean released = age >= MourningBell.LUNGE_WINDUP;
            float alpha = released ? .7f * (1f - (age - MourningBell.LUNGE_WINDUP) / MourningBell.LUNGE_SECONDS) : .96f;
            for (int arrow = 1; arrow <= 3; arrow++) {
                float along = arrow * .25f;
                float x = MathUtils.lerp(enemy.attackStartX, enemy.attackTargetX, along);
                float y = MathUtils.lerp(enemy.attackStartY, enemy.attackTargetY, along);
                if (visible(camera, x, y, 45f))
                    enemyGroundVfx.arrow(batch, x, y, enemy.castFacingX, enemy.castFacingY, alpha, released);
            }
        } else if (enemy.attack == ArenaWorld.EnemyAttack.BELL_TOLL
            && visible(camera, enemy.attackStartX, enemy.attackStartY, MourningBell.TOLL_RADIUS + 30f)) {
            for (int beat = 0; beat < 2; beat++) {
                if (MourningBell.tollWarning(enemy, beat))
                    drawWarningRune(.86f, enemy.attackStartX, enemy.attackStartY, MourningBell.TOLL_RADIUS);
                if (MourningBell.tollActive(enemy, beat)) {
                    float progress = MathUtils.clamp((enemy.attackAge - MourningBell.tollRelease(beat))
                        / MourningBell.TOLL_WAVE_SECONDS, 0f, 1f);
                    playerFootRing.draw(batch, screenX(enemy.attackStartX, enemy.attackStartY),
                        screenY(enemy.attackStartX, enemy.attackStartY), MourningBell.tollRadius(enemy, beat),
                        1f, .75f, .43f, .96f * (1f - .5f * progress));
                }
            }
        }
    }

    private void drawWarningRune(float alpha, float x, float y, float radius) {
        playerFootRing.draw(batch, screenX(x, y), screenY(x, y), radius,
            1f, .93f, .77f, alpha);
    }

    /** One simple outline shares the primary attack's live acquisition radius. */
    private void drawPlayerAuras(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Player player : world.players) {
            if (!player.active || !player.alive) continue;
            float radius = world.primaryAttackRange(player);
            if (!visible(camera, player.x, player.y, radius + 3f, radius + 3f,
                radius * .5f + 3f, radius * .5f + 3f)) continue;
            playerFootRing.draw(batch, screenX(player.x, player.y), screenY(player.x, player.y), radius, player.slot);
        }
        batch.setColor(Color.WHITE);
    }

    private void groundSprite(SpriteEffectArt.Type type, float progress, float x, float y,
                              float radius, float angle, SpriteEffectArt.Layer layer) {
        art.spriteEffects.drawGround(batch, type, progress, screenX(x, y), screenY(x, y), radius, angle, layer);
    }

    private void participantTint(int owner, float alpha) {
        if (owner == 0) batch.setColor(1f, 1f, 1f, alpha);
        else batch.setColor(.55f, .95f, .95f, alpha);
    }

    private void drawFootEffects(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Effect effect : world.effects) {
            if (!effect.active || !visible(camera, effect.x, effect.y, 200f)) continue;
            if (effect.kind != ArenaWorld.EffectKind.AURA && effect.kind != ArenaWorld.EffectKind.DODGE) continue;
            participantTint(effect.owner, .85f);
            if (effect.kind == ArenaWorld.EffectKind.AURA) {
                groundSprite(SpriteEffectArt.Type.SOUL_PULSE, effectProgress(effect), effect.x, effect.y,
                    SOUL_PULSE_RADIUS, 0, SpriteEffectArt.Layer.ALL);
            } else {
                art.spriteEffects.drawProjected(batch, SpriteEffectArt.Type.DODGE, effectProgress(effect),
                    screenX(effect.x, effect.y), screenY(effect.x, effect.y),
                    .22f * art.spriteEffects.referenceScale(SpriteEffectArt.Type.DODGE),
                    MathUtils.atan2(effect.dirY, effect.dirX) + MathUtils.PI * .25f, SpriteEffectArt.Layer.ALL);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private static float effectProgress(ArenaWorld.Effect effect) {
        return MathUtils.clamp(1f - effect.ttl / Math.max(.001f, effect.maxTtl), 0, 1);
    }

    private void drawAura(float age, float x, float y, float radius, EnemyAttackArt.Layer layer) {
        art.enemyAttacks.drawAura(batch, age, screenX(x, y), screenY(x, y),
            radius / art.enemyAttacks.auraGroundRadius(), layer);
    }

    private void drawEnemyFloorCues(ArenaWorld world, OrthographicCamera camera) {
        for (ArenaWorld.Enemy enemy : world.enemies) {
            if (!enemy.active || !visible(camera, enemy.x, enemy.y, 150f)) continue;
            if (enemy.kind == ArenaWorld.EnemyKind.BOSS || enemy.kind == ArenaWorld.EnemyKind.SOVEREIGN) {
                batch.setColor(1f, .26f, .18f, .56f);
                drawAura(enemy.animTime, enemy.x, enemy.y, Math.max(32f, enemy.radius + 12f), EnemyAttackArt.Layer.ALL);
            }
            if (enemy.attack != ArenaWorld.EnemyAttack.NONE
                && enemy.attack != ArenaWorld.EnemyAttack.WRAITH_DASH && enemy.windup > 0) {
                drawWarningRune(.86f, enemy.x, enemy.y, 29f * Math.min(1.4f, enemyScale(enemy)));
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Overhead rank cues remain readable above the complete depth-sorted body pass. */
    private void drawEnemyRanks(ArenaWorld world) {
        for (int i = 0; i < sortCount; i++) if (types[i] == ENEMY) {
            ArenaWorld.Enemy enemy = world.enemies[indices[i]];
            if (ArenaWorld.isBoss(enemy.kind)
                || !enemy.elite && enemy.variant.healthMultiplier <= 1f) continue;
            GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(enemy.kind));
            float scale = enemyScale(enemy), top = screenY(enemy.x, enemy.y) + actor.maxAbove * scale;
            if (enemy.attack == ArenaWorld.EnemyAttack.BRUTE_SLAM) {
                // Follow the small head rise under lifted arms, rather than anchoring
                // every idle badge above the highest outstretched fist in the bank.
                float phase = MathUtils.clamp(enemy.attackAge / ArenaWorld.BRUTE_ATTACK_SECONDS, 0, 1);
                top += 7f * actor.pixelScale * scale * MathUtils.sin(phase * MathUtils.PI);
            }
            rankMarkers.draw(batch, screenX(enemy.x, enemy.y), top,
                enemy.elite, enemy.health / Math.max(1f, enemy.maxHealth));
        }
    }

    private void drawDamageNumbers(ArenaWorld world, OrthographicCamera camera) {
        for (DamageNumbers.Entry entry : world.damageNumbers.entries) {
            if (entry.ttl <= 0) continue;
            GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(entry.enemyKind));
            float x = screenX(entry.x, entry.y) + entry.offsetX;
            float y = screenY(entry.x, entry.y) + actor.maxAbove * entry.renderScale + entry.liftPadding + entry.rise();
            if (entry.enemyKind == ArenaWorld.EnemyKind.BOSS) y += graveKingHover(world.elapsed);
            else if (entry.enemyKind == ArenaWorld.EnemyKind.MOURNING_BELL) y += MourningBellPresentation.hover(world.elapsed);
            if (visibleProjected(camera, x, y, 48f)) damageNumberArt.draw(batch, entry, x, y);
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
                    batch.setColor(Color.WHITE);
                    // The painted crescent faces screen-down at angle zero. Rotate before projection.
                    art.spriteEffects.drawGround(batch, SpriteEffectArt.Type.SWORD_SWEEP, effectProgress(effect),
                        x, y + 45f, WARDEN_SLASH_RADIUS, effect.angle + MathUtils.PI * .75f, SpriteEffectArt.Layer.ALL);
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
                    if (!effect.heroHit && effect.impactKind == ArenaWorld.ImpactKind.SWORD) {
                        float dx = screenX(effect.dirX, effect.dirY), dy = screenY(effect.dirX, effect.dirY);
                        float rotation = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees - 25f;
                        batch.setColor(Color.WHITE);
                        art.vfx.draw(batch, VfxArt.Animation.SWORD_HIT, effect.maxTtl - effect.ttl,
                            impactX, impactY, 1.45f * (effect.critical ? 1.22f : 1f), rotation, VfxArt.Layer.ALL);
                        if (effect.critical) {
                            batch.setColor(1f, .92f, .65f, .8f);
                            bodySprite(SpriteEffectArt.Type.CONTACT, effectProgress(effect), impactX, impactY, .13f, 0);
                        }
                    } else {
                        if (effect.heroHit) batch.setColor(1f, .35f, .22f, 1f);
                        else if (effect.critical) batch.setColor(1f, .95f, .78f, 1f);
                        else batch.setColor(Color.WHITE);
                        // Radial artwork never receives an incoming-direction rotation or fan.
                        bodySprite(SpriteEffectArt.Type.CONTACT, effectProgress(effect), impactX, impactY,
                            .18f * (effect.critical ? 1.22f : 1f), 0);
                    }
                }
                case DEATH -> {
                    boolean spirit = effect.enemyKind == ArenaWorld.EnemyKind.WRAITH;
                    if (spirit) {
                        for (int i = 0; i < 4; i++) {
                            float sign = (i & 1) == 0 ? -1f : 1f;
                            float drift = sign * (9f + 28f * (1f - life)) * (.3f + i * .2f);
                            float rise = (1f - life) * 58f + i * 4f;
                            batch.setColor(.67f, .93f, .91f, life * .75f);
                            batch.draw(art.soul, x + drift - 3f, y + rise, 6f, 10f);
                        }
                    } else {
                        batch.setColor(.95f, .77f, .48f, .65f);
                        // The authored decay frames supply flecks, rather than drawing short lines.
                        bodySprite(SpriteEffectArt.Type.CONTACT, .55f + .45f * effectProgress(effect),
                            x, y + 14f, .16f, 0);
                    }
                }
                case AURA, DODGE -> { /* Floor sprites are drawn beneath every actor in drawFootEffects. */ }
                case LIGHTNING -> {
                    batch.setColor(Color.WHITE);
                    art.spriteEffects.drawStretch(batch, SpriteEffectArt.Type.STORM, effectProgress(effect),
                        x, y + 32f, screenX(effect.x2, effect.y2), screenY(effect.x2, effect.y2) + 24f,
                        .16f * art.spriteEffects.referenceScale(SpriteEffectArt.Type.STORM));
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
                    float inverse = 1f / Math.max(.001f, (float) Math.sqrt(dx * dx + dy * dy));
                    dx *= inverse; dy *= inverse;
                    batch.setColor(Color.WHITE);
                    bodySprite(SpriteEffectArt.Type.BLOCK, effectProgress(effect),
                        x - dx * 15f, y + impactLift(effect) - dy * 15f,
                        .3f, MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees);
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

    private void bodySprite(SpriteEffectArt.Type type, float progress, float x, float y,
                            float rawScale, float rotationDegrees) {
        art.spriteEffects.draw(batch, type, progress, x, y,
            rawScale * art.spriteEffects.referenceScale(type), rotationDegrees, SpriteEffectArt.Layer.ALL);
    }

    private float impactLift(ArenaWorld.Effect effect) {
        if (effect.heroHit) return art.actor(ActorAnimation.actor(ArenaWorld.Hero.WARDEN)).worldHeight * .5f;
        if (effect.enemyKind == null) return 38f;
        return art.actor(ActorAnimation.actor(effect.enemyKind)).worldHeight * .5f
            * EnemySizing.renderScale(effect.enemyKind, effect.enemyVariant, effect.elite);
    }

    private static float enemyScale(ArenaWorld.Enemy enemy) {
        return EnemySizing.renderScale(enemy.kind, enemy.variant, enemy.elite);
    }

    private static float spawnScale(ArenaWorld.Enemy enemy) {
        return Math.min(1.8f, 1.4f * enemyScale(enemy));
    }

    /** Visual lift uses the paused combat clock; collision and ground sorting never hover. */
    private static float graveKingHover(float combatTime) {
        return 18f + MathUtils.sin(combatTime * 2.2f) * 3.5f;
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

    private void setEnemyBodyColor(ArenaWorld.Enemy enemy) {
        // Material palettes preserve luminance in the shader; ordinary tints use vertex colour.
        if (enemy.hitFlash > 0f) batch.setColor(1f, .8f, .63f, 1f);
        else if (EnemyPalette.materialAware(enemy.variant)) batch.setColor(Color.WHITE);
        else batch.setColor(enemy.variant.red, enemy.variant.green, enemy.variant.blue, 1f);
    }

    private void drawSprites(ArenaWorld world, OrthographicCamera camera, int viewer) {
        for (int i = 0; i < sortCount; i++) {
            int index = indices[i];
            if (types[i] == PAINTED_BORDER) {
                batch.setColor(Color.WHITE);
                float[] cut = paintedCorners.wallVertices[index];
                if (cut == null) cut = gateOpening.vertices[index];
                if (cut == null) paintedBorders.draw(batch, index);
                else if (cut.length > 0) paintedBorders.draw(batch, index, cut);
            } else if (types[i] == PAINTED_CORNER) {
                batch.setColor(Color.WHITE);
                paintedCorners.draw(batch, index);
            } else if (types[i] == PAINTED_GATE) {
                batch.setColor(Color.WHITE);
                paintedGate.draw(batch, index);
            } else if (types[i] == PROP) {
                drawEnvironment(world, camera, viewer, index);
            } else if (types[i] == CHEST) {
                ArenaWorld.Chest chest = world.chests[index];
                float x = screenX(chest.x, chest.y), y = screenY(chest.x, chest.y);
                chestVfx.wisps(batch, x, y, world.elapsed, index);
                batch.setColor(Color.WHITE);
                drawFrame(chestArt.frame(), chest.x, chest.y, chestScale);
                chestVfx.glints(batch, x, y, world.elapsed, index);
            } else if (types[i] == SENTINEL_BLADE) {
                skillArt.orbiter(batch, world.skills.orbiters[index]);
            } else if (types[i] == RETURNING_GLAIVE) {
                skillArt.glaive(batch, world.skills.glaives[index]);
            } else if (types[i] == WORLD_RELIC) {
                skillArt.relic(batch, world.skills.relics[index]);
            } else if (types[i] == DODGE_GHOST) {
                int owner = index / DodgeTrail.CAPACITY;
                DodgeTrail.Snapshot snapshot = world.players[owner].dodgeTrail.snapshots[index % DodgeTrail.CAPACITY];
                GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(snapshot.hero));
                int frame = actor.clip(snapshot.state, snapshot.facing).frame(snapshot.phase);
                if (owner == 0) batch.setColor(1f, .9f, .75f, snapshot.alpha());
                else batch.setColor(.65f, .95f, .95f, snapshot.alpha());
                drawFrame(frame, snapshot.x, snapshot.y, actor.pixelScale);
                batch.setColor(Color.WHITE);
            } else if (types[i] == ENEMY) {
                ArenaWorld.Enemy enemy = world.enemies[index];
                float scale = enemyScale(enemy);
                drawArrival(enemy, VfxArt.Layer.BACK);
                setEnemyBodyColor(enemy);
                EnemyVariant bodyVariant = enemy.hitFlash > 0f ? EnemyVariant.ORIGINAL : enemy.variant;
                GameplayAtlas.Actor actor = art.actor(ActorAnimation.actor(enemy.kind));
                ActorAnimation.State state = ActorAnimation.state(enemy);
                float recoil = MathUtils.sin(MathUtils.clamp(enemy.hitFlash / .12f, 0f, 1f) * MathUtils.PI)
                    * (ArenaWorld.isBoss(enemy.kind) ? 1.5f
                        : enemy.kind == ArenaWorld.EnemyKind.BRUTE ? 4f : 8f);
                // This short reaction moves only the paint. Ground root, shadow, sort and collision stay fixed.
                if (enemy.kind == ArenaWorld.EnemyKind.MOURNING_BELL) {
                    int frame = actor.clip(state, ActorAnimation.facing(enemy, state)).frame(ActorAnimation.phase(enemy, state));
                    drawBellFrame(frame, enemy, actor.pixelScale * scale, world.elapsed,
                        screenX(enemy.hitDirectionX, enemy.hitDirectionY) * recoil,
                        screenY(enemy.hitDirectionX, enemy.hitDirectionY) * recoil);
                } else if (enemy.attack != ArenaWorld.EnemyAttack.NONE) {
                    ActorAnimation.Facing facing = ActorAnimation.facing(enemy, state);
                    float sx = screenX(enemy.x, enemy.y), sy = screenY(enemy.x, enemy.y);
                    if (enemy.attack == ArenaWorld.EnemyAttack.WRAITH_DASH
                        && enemy.attackAge >= ArenaWorld.WRAITH_WINDUP
                        && enemy.attackAge < ArenaWorld.WRAITH_WINDUP + ArenaWorld.WRAITH_DASH_SECONDS) {
                        for (int ghost = 2; ghost >= 1; ghost--) {
                            float alpha = ghost == 1 ? .24f : .12f;
                            if (EnemyPalette.materialAware(enemy.variant)) batch.setColor(1f, 1f, 1f, alpha);
                            else batch.setColor(enemy.variant.red, enemy.variant.green, enemy.variant.blue, alpha);
                            art.enemyAttacks.drawActor(batch, enemy.kind, facing, enemy.attackAge,
                                sx - screenX(enemy.castFacingX, enemy.castFacingY) * ghost * 18f,
                                sy - screenY(enemy.castFacingX, enemy.castFacingY) * ghost * 18f,
                                actor.pixelScale * scale, enemy.variant);
                        }
                        setEnemyBodyColor(enemy);
                    }
                    art.enemyAttacks.drawActor(batch, enemy.kind, facing, enemy.attackAge,
                        sx + screenX(enemy.hitDirectionX, enemy.hitDirectionY) * recoil,
                        sy + screenY(enemy.hitDirectionX, enemy.hitDirectionY) * recoil, actor.pixelScale * scale, bodyVariant);
                } else {
                    int frame = actor.clip(state, ActorAnimation.facing(enemy, state)).frame(ActorAnimation.phase(enemy, state));
                    drawFrameOffset(frame, enemy.x, enemy.y, actor.pixelScale * scale,
                        screenX(enemy.hitDirectionX, enemy.hitDirectionY) * recoil,
                        screenY(enemy.hitDirectionX, enemy.hitDirectionY) * recoil
                            + (enemy.kind == ArenaWorld.EnemyKind.BOSS ? graveKingHover(world.elapsed) : 0f), bodyVariant);
                }
                drawArrival(enemy, VfxArt.Layer.FRONT);
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
                skillArt.ward(batch, player);
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
        GameplayAtlas.Region source = propArt[index];
        float shear = source.renderShearY();
        float leftShift = (left - rootX) * shear, rightShift = (left + width - rootX) * shear;
        float lowShift = Math.min(leftShift, rightShift), highShift = Math.max(leftShift, rightShift);
        TextureRegion region = art.frame(propArt[index].frame());
        float groundSlope = placement.region.startsWith("wall-x") || placement.region.equals("sealed-gate") ? .5f
            : placement.region.startsWith("wall-y") || placement.region.equals("altar") ? -.5f : 0f;
        float projectedTop = rootY + (bottom + height - rootY) * source.renderScaleY() + highShift;
        if (!placement.fadeWhenOccluding || height * source.renderScaleY() <= ENVIRONMENT_BASE_HEIGHT * 2f
            || !environmentOccludes(world, camera, viewer, groundSlope, rootY, left, rootY + ENVIRONMENT_BASE_HEIGHT + lowShift,
                left + width, projectedTop)) {
            batch.setColor(Color.WHITE);
            drawEnvironmentPart(region, left, bottom, width, height, region.getV2(), region.getV(), rootX, rootY, source);
            return;
        }
        // Keep the ground footprint and the low wall/landmark base solid. Reveal only its upper paint.
        float baseHeight = MathUtils.clamp(rootY + ENVIRONMENT_BASE_HEIGHT / source.renderScaleY() - bottom, 0f, height);
        float splitV = MathUtils.lerp(region.getV2(), region.getV(), baseHeight / height);
        batch.setColor(Color.WHITE);
        if (baseHeight > 0f)
            drawEnvironmentPart(region, left, bottom, width, baseHeight, region.getV2(), splitV, rootX, rootY, source);
        batch.setColor(1f, 1f, 1f, placement.wall ? .14f : .24f);
        if (baseHeight < height)
            drawEnvironmentPart(region, left, bottom + baseHeight, width, height - baseHeight,
                splitV, region.getV(), rootX, rootY, source);
        batch.setColor(Color.WHITE);
    }

    /** Calibrate painted ground edges to the floor projection using the existing four sprite vertices. */
    private void drawEnvironmentPart(TextureRegion region, float x, float y, float width, float height,
                                     float bottomV, float topV, float rootX, float rootY, GameplayAtlas.Region source) {
        if (source.renderShearY() == 0f && source.renderScaleY() == 1f) {
            batch.draw(region.getTexture(), x, y, width, height, region.getU(), bottomV, region.getU2(), topV);
            return;
        }
        float leftY = rootY + EnvironmentProjection.projectedY(x - rootX, y - rootY, source);
        float rightY = rootY + EnvironmentProjection.projectedY(x + width - rootX, y - rootY, source);
        float projectedHeight = height * source.renderScaleY();
        float color = batch.getPackedColor();
        environmentVertex(0, x, leftY, color, region.getU(), bottomV);
        environmentVertex(5, x, leftY + projectedHeight, color, region.getU(), topV);
        environmentVertex(10, x + width, rightY + projectedHeight, color, region.getU2(), topV);
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
        drawFrameOffset(index, x, y, scale, offsetX, offsetY, EnemyVariant.ORIGINAL);
    }

    private void drawFrameOffset(int index, float x, float y, float scale, float offsetX, float offsetY, EnemyVariant variant) {
        GameplayAtlas.Frame frame = art.metadata.frames[index];
        batch.draw(art.frame(index, variant), screenX(x, y) + frame.left * scale + offsetX,
            screenY(x, y) + frame.bottom * scale + offsetY, frame.width * scale, frame.height * scale);
    }

    private void drawBellFrame(int index, ArenaWorld.Enemy enemy, float scale, float combatTime, float recoilX, float recoilY) {
        GameplayAtlas.Frame frame = art.metadata.frames[index];
        float x = screenX(enemy.x, enemy.y) + recoilX;
        float y = screenY(enemy.x, enemy.y) + MourningBellPresentation.hover(combatTime) + recoilY;
        batch.draw(art.frame(index), x + frame.left * scale, y + frame.bottom * scale,
            -frame.left * scale, -frame.bottom * scale, frame.width * scale, frame.height * scale,
            1f, 1f, MourningBellPresentation.tilt(enemy));
    }

    private void drawMuzzle(ArenaWorld.Player player) {
        if (player.hero != ArenaWorld.Hero.RANGER || player.attackAge >= .1f) return;
        int origin = ActorAnimation.facing(player.attackFacingX, player.attackFacingY, ActorAnimation.Facing.SE).ordinal() * 2;
        float scale = art.actor(ActorAnimation.Actor.RANGER).pixelScale;
        float x = screenX(player.x, player.y) + BOLT_ORIGIN[origin] * scale;
        float y = screenY(player.x, player.y) + BOLT_ORIGIN[origin + 1] * scale;
        float life = 1f - player.attackAge / .1f;
        float emergence = ProjectilePresentation.spawnScale(player.attackAge);
        float dx = screenX(player.attackFacingX, player.attackFacingY), dy = screenY(player.attackFacingX, player.attackFacingY);
        float angle = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
        batch.setColor(.48f, .88f, 1f, life * .65f);
        float glowRadius = 18f * emergence;
        batch.draw(art.glow, x - glowRadius, y - glowRadius, glowRadius * 2, glowRadius * 2);
        batch.setColor(1f, 1f, 1f, life * .85f);
        art.projectiles.draw(batch, MagicProjectileArt.Type.RANGER, player.attackAge, x, y,
            (.6f + life * .25f) * emergence, angle);
        batch.setColor(Color.WHITE);
    }

    private static boolean visibleActor(OrthographicCamera camera, float x, float y,
                                        GameplayAtlas.Actor actor, float scale) {
        // Include a small shadow/elite-bar margin around the precomputed complete action envelope.
        float motionMargin = RECOIL_MARGIN + (actor.id == ActorAnimation.Actor.MOURNING_BELL ? 48f : 0f);
        float above = actor.maxAbove * scale
            + (actor.id == ActorAnimation.Actor.GRAVE_KING || actor.id == ActorAnimation.Actor.MOURNING_BELL ? 40f : 12f);
        if (actor.id == ActorAnimation.Actor.WARDEN || actor.id == ActorAnimation.Actor.RANGER)
            above = Math.max(above, REVIVE_BAR_Y + REVIVE_BAR_HEIGHT);
        return visible(camera, x, y, Math.max(40f, actor.maxLeft * scale) + motionMargin,
            Math.max(40f, actor.maxRight * scale) + motionMargin, above + RECOIL_MARGIN,
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
    @Override public void dispose() { floorCache.dispose(); batch.dispose(); paletteShader.dispose(); iris.dispose(); }
}
