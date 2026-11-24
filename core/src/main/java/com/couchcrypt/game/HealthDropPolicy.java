package com.couchcrypt.game;

/** Bounded twenty-minute-run healing; callers advance this only during combat ticks. */
public final class HealthDropPolicy {
    private static final float EARLY_SECONDS = 180f;
    private static final float SOLO_INTERVAL = 18f, COOP_INTERVAL = 12f;
    private static final float REGULAR_TTL = 35f, ELITE_TTL = 45f;
    private int ordinaryKills;
    private float secondsSinceRegular = SOLO_INTERVAL;

    /** Skips count eligible drops blocked by the interval, floor budget or shared pool. */
    public int generated, expired, skipped, trimmed, replaced;
    public long generatedValue, expiredValue;

    public HealthDropPolicy() {}

    public void reset() {
        ordinaryKills = 0; secondsSinceRegular = SOLO_INTERVAL;
        generated = expired = skipped = trimmed = replaced = 0;
        generatedValue = expiredValue = 0;
    }

    /** XP never expires here. Zero/nonfinite deltas do not advance timers or change pickups. */
    public void update(ArenaWorld.Pickup[] pickups, float delta, int livingPlayers) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        secondsSinceRegular = Math.min(SOLO_INTERVAL, secondsSinceRegular + delta);
        int activeHealing = 0;
        for (ArenaWorld.Pickup pickup : pickups) {
            if (!pickup.active || !pickup.healing) continue;
            pickup.ttl = Math.max(0, pickup.ttl - delta);
            if (pickup.ttl <= 0) {
                pickup.active = false; expired++; expiredValue += pickup.value;
            } else activeHealing++;
        }
        int cap = floorCap(livingPlayers);
        while (activeHealing > cap) {
            ArenaWorld.Pickup oldest = oldestHealing(pickups);
            if (oldest == null) break;
            oldest.active = false; trimmed++; activeHealing--;
        }
    }

    /** Call once per ordinary or elite death, excluding boss deaths; false also covers non-drop kills. */
    public boolean defeated(ArenaWorld.Pickup[] pickups, boolean elite, float elapsed,
                            int livingPlayers, float x, float y) {
        if (!Float.isFinite(elapsed) || elapsed < 0) return false;
        boolean early = elapsed < EARLY_SECONDS;
        if (!elite) {
            if (++ordinaryKills < (early ? 14 : 20)) return false;
            // An event is consumed even when blocked; stored kills cannot burst into later drops.
            ordinaryKills = 0;
        }
        if (livingPlayers <= 0 || !elite && secondsSinceRegular < (livingPlayers > 1 ? COOP_INTERVAL : SOLO_INTERVAL)) {
            skipped++; return false;
        }

        int activeHealing = 0;
        ArenaWorld.Pickup free = null, oldestRegular = null;
        for (ArenaWorld.Pickup pickup : pickups) {
            if (!pickup.active) {
                if (free == null) free = pickup;
                continue;
            }
            if (!pickup.healing) continue;
            activeHealing++;
            if (!pickup.eliteHealing && (oldestRegular == null || age(pickup) > age(oldestRegular)))
                oldestRegular = pickup;
        }
        ArenaWorld.Pickup destination = activeHealing < floorCap(livingPlayers) ? free : null;
        if (destination == null && elite) destination = oldestRegular;
        if (destination == null) { skipped++; return false; }
        if (destination.active) replaced++;

        destination.active = destination.healing = true;
        destination.eliteHealing = elite;
        destination.x = x; destination.y = y;
        destination.value = elite ? 24 : early ? 16 : 12;
        destination.ttl = destination.maxTtl = elite ? ELITE_TTL : REGULAR_TTL;
        if (!elite) secondsSinceRegular = 0;
        generated++; generatedValue += destination.value;
        return true;
    }

    /** XP allocation may reclaim this slot before merging XP; ties retain stable pool order. */
    public static ArenaWorld.Pickup oldestHealing(ArenaWorld.Pickup[] pickups) {
        ArenaWorld.Pickup oldest = null;
        for (ArenaWorld.Pickup pickup : pickups)
            if (pickup.active && pickup.healing && (oldest == null || age(pickup) > age(oldest))) oldest = pickup;
        return oldest;
    }

    private static int floorCap(int livingPlayers) { return livingPlayers > 1 ? 4 : 3; }
    private static float age(ArenaWorld.Pickup pickup) { return pickup.maxTtl - pickup.ttl; }
}
