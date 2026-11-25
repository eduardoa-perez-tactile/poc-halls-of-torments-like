package com.couchcrypt.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthDropPolicyTest {
    @Test void ordinaryKillEventsUseTheOpeningAndLateValuesWithoutCooldownBacklogs() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(8);
        assertFalse(kills(policy, pickups, 13, 179, 1));
        assertTrue(kills(policy, pickups, 1, 179, 1));
        assertEquals(16, pickups[0].value); assertEquals(35f, pickups[0].ttl);
        assertFalse(kills(policy, pickups, 20, 180, 1), "Cooldown consumes this event instead of banking it");
        assertEquals(1, policy.skipped);
        policy.update(pickups, 18, 1);
        assertFalse(kills(policy, pickups, 19, 198, 1), "No immediate backlog drop after the interval");
        assertTrue(kills(policy, pickups, 1, 198, 1));
        assertEquals(12, pickups[1].value);
        assertEquals(28L, policy.generatedValue); assertEquals(2, policy.generated);
    }

    @Test void cadenceTracksCurrentLivingPartyWithoutDeadPlayersReceivingDrops() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(8);
        assertTrue(kills(policy, pickups, 20, 200, 2));
        policy.update(pickups, 12, 1);
        assertFalse(kills(policy, pickups, 20, 212, 1), "A dead ally cannot retain the faster co-op cadence");
        policy.update(pickups, 6, 1);
        assertTrue(kills(policy, pickups, 20, 218, 1));
        policy.update(pickups, 12, 2);
        assertTrue(kills(policy, pickups, 20, 230, 2));
        assertFalse(policy.defeated(pickups, true, 230, 0, 0, 0));
        assertEquals(3, policy.generated);
    }

    @Test void floorCapsSkipOrdinaryDropsAndElitesReplaceOnlyTheOldestRegularHealing() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(8);
        healing(pickups[0], false, 16, 25, 35); // age10
        healing(pickups[1], false, 12, 15, 35); // age20, oldest regular
        healing(pickups[2], true, 24, 20, 45); // age25, oldest overall
        assertFalse(kills(policy, pickups, 20, 200, 1));
        assertTrue(policy.defeated(pickups, true, 200, 1, 70, 80));
        assertTrue(pickups[1].eliteHealing); assertEquals(24, pickups[1].value);
        assertEquals(45f, pickups[1].ttl); assertEquals(45f, pickups[1].maxTtl);
        assertEquals(70f, pickups[1].x); assertEquals(80f, pickups[1].y);
        assertEquals(20f, pickups[2].ttl, "The older elite reward is preserved");
        assertFalse(pickups[3].active, "A free slot cannot bypass the three-drop floor limit");
        assertEquals(1, policy.replaced);
        assertTrue(policy.defeated(pickups, true, 200, 1, 0, 0));
        assertFalse(policy.defeated(pickups, true, 200, 1, 0, 0), "An all-elite floor budget cannot grow or merge");
        assertEquals(3, activeHealing(pickups));
    }

    @Test void expiryAndPartyShrinkStayBoundedWithoutTouchingXpOrMergingHealing() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(8);
        for (int i = 0; i < 4; i++) healing(pickups[i], i == 0, 24, 30 - i * 2, 45);
        pickups[4].active = true; pickups[4].healing = false; pickups[4].value = 500;
        policy.update(pickups, 1, 1);
        assertFalse(pickups[3].active, "The oldest healing is trimmed when co-op becomes solo");
        assertEquals(1, policy.trimmed); assertEquals(3, activeHealing(pickups));
        policy.update(pickups, 0, 1);
        assertEquals(29f, pickups[0].ttl, "A frozen combat clock cannot age healing");
        policy.update(pickups, 35, 1);
        assertEquals(0, activeHealing(pickups)); assertEquals(3, policy.expired);
        assertEquals(72L, policy.expiredValue);
        assertTrue(pickups[4].active); assertEquals(500, pickups[4].value);

        var timed = pool(2);
        assertTrue(policy.defeated(timed, true, 300, 1, 0, 0));
        policy.update(timed, 44.9f, 1); assertTrue(timed[0].active);
        policy.update(timed, .2f, 1); assertFalse(timed[0].active);
    }

    @Test void fullSharedPoolsNeverSacrificeOrModifyXpForHealing() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(2);
        for (var pickup : pickups) { pickup.active = true; pickup.value = 123; }
        assertFalse(kills(policy, pickups, 20, 200, 1));
        assertFalse(policy.defeated(pickups, true, 200, 1, 0, 0));
        for (var pickup : pickups) { assertTrue(pickup.active); assertFalse(pickup.healing); assertEquals(123, pickup.value); }
        assertNull(HealthDropPolicy.oldestHealing(pickups));
        healing(pickups[1], false, 12, 25, 35);
        assertSame(pickups[1], HealthDropPolicy.oldestHealing(pickups));
        assertTrue(policy.defeated(pickups, true, 200, 1, 0, 0));
        assertEquals(123, pickups[0].value); assertFalse(pickups[0].healing);
        assertEquals(24, pickups[1].value, "An elite replacement never adds to the old heal value");
    }

    @Test void resetClearsKillProgressCadenceAndDiagnostics() {
        HealthDropPolicy policy = new HealthDropPolicy();
        var pickups = pool(4);
        assertTrue(kills(policy, pickups, 14, 0, 1));
        assertFalse(kills(policy, pickups, 13, 0, 1));
        policy.reset();
        assertEquals(0, policy.generated); assertEquals(0L, policy.generatedValue);
        assertEquals(0, policy.skipped); assertEquals(0, policy.expired);
        assertEquals(0, policy.trimmed); assertEquals(0, policy.replaced);
        assertFalse(kills(policy, pickups, 1, 0, 1));
        assertTrue(kills(policy, pickups, 13, 0, 1));
    }

    private static boolean kills(HealthDropPolicy policy, ArenaWorld.Pickup[] pickups, int count, float elapsed, int living) {
        boolean generated = false;
        for (int i = 0; i < count; i++) generated |= policy.defeated(pickups, false, elapsed, living, 0, 0);
        return generated;
    }
    private static ArenaWorld.Pickup[] pool(int size) {
        var pickups = new ArenaWorld.Pickup[size];
        for (int i = 0; i < size; i++) pickups[i] = new ArenaWorld.Pickup();
        return pickups;
    }
    private static void healing(ArenaWorld.Pickup pickup, boolean elite, int value, float ttl, float maxTtl) {
        pickup.active = pickup.healing = true; pickup.eliteHealing = elite;
        pickup.value = value; pickup.ttl = ttl; pickup.maxTtl = maxTtl;
    }
    private static int activeHealing(ArenaWorld.Pickup[] pickups) {
        int count = 0; for (var pickup : pickups) if (pickup.active && pickup.healing) count++; return count;
    }
}
