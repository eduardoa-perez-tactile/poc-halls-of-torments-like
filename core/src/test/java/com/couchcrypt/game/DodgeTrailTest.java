package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DodgeTrailTest {
    @Test void snapshotsStayOnActualPathAndOutliveLongerDodgeWithoutChangingCooldown() {
        ArenaWorld world = solo();
        ArenaWorld.Player player = world.players[0];
        world.setInput(0, 1, 0, true);
        world.update(ArenaWorld.STEP);
        DodgeTrail.Snapshot first = player.dodgeTrail.snapshots[0];
        assertEquals(0, first.x); assertEquals(0, first.y);
        assertEquals(ActorAnimation.State.DODGE, first.state);
        assertEquals(ActorAnimation.Facing.NE, first.facing);
        assertEquals(player.hero.dodgeCooldown, player.dodgeCooldown, .0001f);
        assertEquals(player.hero.moveSpeed * 4.25f * ArenaWorld.STEP, player.x, .0001f);
        float firstAlpha = first.alpha();
        world.update(.1f);
        assertEquals(0, first.x, "A stored ghost must not travel with its player");
        assertTrue(first.alpha() < firstAlpha);
        for (DodgeTrail.Snapshot snapshot : player.dodgeTrail.snapshots) if (snapshot.ttl > 0) {
            assertEquals(0, snapshot.y);
            assertTrue(snapshot.x >= 0 && snapshot.x < player.x);
        }
        world.setInput(0, 0, 0, false);
        world.update(.1f); world.update(ArenaWorld.STEP);
        assertEquals(0, player.dodgeTime);
        assertEquals(player.hero.moveSpeed * 4.25f * .22f, player.x, .001f);
        assertTrue(first.ttl > 0, "The oldest snapshot remains available through the complete dash");
        assertEquals(0, first.x, "A longer history must retain the first pose until it fades");
        assertTrue(active(player) > 0, "The last ghosts fade briefly after the dash ends");
        world.update(.1f); world.update(.1f);
        assertTrue(active(player) > 0, "The newest snapshots remain visible briefly beyond the old fade window");
        world.update(.05f);
        assertEquals(0, active(player));
        assertEquals(DodgeTrail.CAPACITY, player.dodgeTrail.snapshots.length);
    }

    @Test void choicePauseFreezesHistoryAndRemovalResetAndDeathClearIt() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        world.setInput(0, 0, 1, true); world.update(ArenaWorld.STEP);
        DodgeTrail.Snapshot snapshot = player.dodgeTrail.snapshots[0];
        float ttl = snapshot.ttl, x = player.x, y = player.y;
        world.progression.addXp(world.progression.xpToNext);
        world.update(.1f);
        assertEquals(ttl, snapshot.ttl); assertEquals(x, player.x); assertEquals(y, player.y);
        world.activatePlayer(0);
        assertEquals(ttl, snapshot.ttl, "Existing-participant reconnect preserves the frozen history");
        world.progression.choose(0, world.progression.offers[0].id, 0);
        player.alive = false; world.update(ArenaWorld.STEP);
        assertEquals(0, active(player));
        world.reset(); world.activatePlayer(0);
        assertEquals(0, active(player));
        world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
        assertTrue(active(player) > 0);
        world.deactivatePlayer(0); assertEquals(0, active(player));
    }

    @Test void clampedDodgeDoesNotLeaveAStackOfStationaryGhosts() {
        ArenaWorld world = solo(); ArenaWorld.Player player = world.players[0];
        player.x = ArenaWorld.HALF_WIDTH - 20;
        world.setInput(0, 1, 0, true); world.update(.1f);
        assertEquals(0, active(player));
    }

    @Test void bothHeroesTravelFartherWithTheSameEscapeWindowAndCooldown() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.activatePlayer(0);
            ArenaWorld.Player player = world.players[0]; player.invulnerable = 0;
            world.setInput(0, 1, 1, true); world.update(ArenaWorld.STEP);
            world.setInput(0, 0, 0, false);
            assertEquals(.22f, player.invulnerable, .0001f);
            assertEquals(hero.dodgeCooldown, player.dodgeCooldown, .0001f);
            for (int i = 0; i < 13; i++) world.update(ArenaWorld.STEP);
            float oldDistance = hero.moveSpeed * 3 * .22f;
            assertEquals(oldDistance * (4.25f / 3f), Math.hypot(player.x, player.y), .001f);
            assertEquals(player.x, player.y, .0001f, "Diagonal input must not gain extra distance");
            assertEquals(0, player.dodgeTime);
            world.update(ArenaWorld.STEP);
            assertEquals(0, player.invulnerable, .0001f, "The distance increase does not extend invulnerability");
            assertTrue(player.dodgeCooldown > 2f);
        }
    }

    private static int active(ArenaWorld.Player player) {
        int count = 0;
        for (DodgeTrail.Snapshot snapshot : player.dodgeTrail.snapshots) if (snapshot.ttl > 0) count++;
        return count;
    }

    private static ArenaWorld solo() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0); return world;
    }
}
