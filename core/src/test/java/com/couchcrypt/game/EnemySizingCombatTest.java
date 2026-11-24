package com.couchcrypt.game;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemySizingCombatTest {
    @Test void sweptBoltsHitInsideAndMissOutsideTheEnlargedGroundBoundaries() throws Exception {
        for (var kind : new ArenaWorld.EnemyKind[]{ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH,
            ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.BRUTE})
            for (boolean elite : new boolean[]{false, true}) for (boolean inside : new boolean[]{false, true}) {
                ArenaWorld world = solo();
                var enemy = spawn(world, kind, elite, EnemyVariant.ORIGINAL);
                enemy.stunTime = 10; enemy.attackCooldown = 20; enemy.health = enemy.maxHealth = 1000;
                var bolt = world.bolts[0];
                bolt.active = true; bolt.owner = 0; bolt.damage = 16; bolt.pierce = 0; bolt.ttl = 2;
                bolt.x = enemy.x - 80; bolt.y = enemy.y + enemy.radius + 5 + (inside ? -.05f : .05f);
                bolt.vx = 10000; bolt.vy = 0;
                world.update(ArenaWorld.STEP);
                assertEquals(inside ? 984f : 1000f, enemy.health, .0001f, kind + " elite=" + elite);
                assertEquals(inside ? 1 : 0, world.boltHits);
            }
    }

    @Test void actualContactUsesThePaintedFootprintRatherThanTheOldRadius() throws Exception {
        for (var variant : new EnemyVariant[]{EnemyVariant.ORIGINAL, EnemyVariant.MOSS, EnemyVariant.FROST})
            for (boolean elite : new boolean[]{false, true}) for (boolean inside : new boolean[]{false, true}) {
                ArenaWorld world = solo();
                var enemy = spawn(world, ArenaWorld.EnemyKind.GHOUL, elite, variant);
                enemy.stunTime = 10; enemy.attackCooldown = 20;
                var player = world.players[0];
                player.x = enemy.x - (20 + enemy.radius + (inside ? -.05f : .05f)); player.y = enemy.y;
                world.update(ArenaWorld.STEP);
                assertEquals(inside ? 88f : 100f, player.health, .0001f,
                    variant + " elite=" + elite + " inside=" + inside);
                assertEquals(inside ? 1 : 0, world.heroHits);
            }
    }

    @Test void reusedSlotsReplaceFootprintsAndVariantStatsWithoutStackingOldEliteBulk() throws Exception {
        ArenaWorld world = solo();
        var first = spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.MOSS);
        assertEquals(EnemySizing.groundRadius(first.kind, first.variant, true), first.radius);
        first.active = false; world.activeEnemies--;
        var next = spawn(world, ArenaWorld.EnemyKind.WRAITH, false, EnemyVariant.DUSK);
        assertSame(first, next);
        assertEquals(EnemySizing.groundRadius(next.kind, next.variant, false), next.radius);
        assertEquals(18 * 1.15f, next.health, .001f);
        assertFalse(next.elite);
        world.reset(); world.activatePlayer(0);
        next = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.ORIGINAL);
        assertSame(first, next);
        assertEquals(EnemySizing.groundRadius(next.kind, next.variant, false), next.radius);
        assertEquals(32, next.health, .001f);
    }

    @Test void releasedShotsAndHitEffectsKeepTheirSizeIdentityAfterCasterReuse() throws Exception {
        ArenaWorld world = solo();
        var caster = spawn(world, ArenaWorld.EnemyKind.CULTIST, true, EnemyVariant.EMBER);
        caster.x = 350; caster.y = 0; caster.attackCooldown = 0;
        for (int i = 0; i < 44; i++) world.update(ArenaWorld.STEP);
        var shot = world.shots[0];
        assertTrue(shot.active);
        assertEquals(EnemyVariant.EMBER, shot.casterVariant);
        assertEquals(EnemySizing.renderScale(caster.kind, caster.variant, true), shot.casterRenderScale);
        float launchScale = shot.casterRenderScale;
        caster.active = false; world.activeEnemies--;
        var moss = spawn(world, ArenaWorld.EnemyKind.GHOUL, false, EnemyVariant.MOSS);
        assertSame(caster, moss);
        assertEquals(EnemyVariant.EMBER, shot.casterVariant);
        assertEquals(launchScale, shot.casterRenderScale);

        moss.health = moss.maxHealth = 1000; moss.stunTime = 10; moss.attackCooldown = 20;
        var bolt = world.bolts[0];
        bolt.active = true; bolt.owner = 0; bolt.damage = 16; bolt.ttl = 2; bolt.pierce = 0;
        bolt.x = moss.x; bolt.y = moss.y; bolt.vx = bolt.vy = 0;
        world.update(ArenaWorld.STEP);
        ArenaWorld.Effect impact = null;
        for (var effect : world.effects)
            if (effect.active && effect.kind == ArenaWorld.EffectKind.HIT && !effect.heroHit) impact = effect;
        assertNotNull(impact);
        assertEquals(EnemyVariant.MOSS, impact.enemyVariant);
        moss.active = false; world.activeEnemies--;
        spawn(world, ArenaWorld.EnemyKind.BRUTE, true, EnemyVariant.FROST);
        assertEquals(EnemyVariant.MOSS, impact.enemyVariant);
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.SHORT); world.activatePlayer(0);
        world.players[0].attackCooldown = 20; world.players[0].invulnerable = 0;
        return world;
    }

    private static ArenaWorld.Enemy spawn(ArenaWorld world, ArenaWorld.EnemyKind kind,
                                          boolean elite, EnemyVariant variant) throws Exception {
        Method method = ArenaWorld.class.getDeclaredMethod("spawn", ArenaWorld.EnemyKind.class,
            boolean.class, ArenaWorld.Player.class, float.class);
        method.setAccessible(true);
        for (int i = 0; i < 64; i++) {
            var enemy = (ArenaWorld.Enemy) method.invoke(world, kind, elite, world.players[0], 0f);
            assertNotNull(enemy);
            if (enemy.variant == variant) return enemy;
            enemy.active = false; world.activeEnemies--;
        }
        throw new AssertionError("No " + variant + " spawn for " + kind);
    }
}
