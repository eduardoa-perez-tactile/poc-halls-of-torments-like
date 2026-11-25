package com.couchcrypt.game;

import java.util.Arrays;

/** Run-only secondary attacks and relics. All storage is fixed and advanced exclusively by combat ticks. */
public final class SkillExpansion {
    public static final int MAX_ORBITERS = 8, MAX_FIELDS = 6, MAX_GLAIVES = 2, MAX_RELICS = 4, MAX_DELAYED = 24;
    public enum RelicKind { SOUL_HARVEST, STASIS }
    public enum StrikeKind { ECHO, SHATTER, REAP }
    public final OrbitBlade[] orbiters = new OrbitBlade[MAX_ORBITERS];
    public final GroundField[] fields = new GroundField[MAX_FIELDS];
    public final Glaive[] glaives = new Glaive[MAX_GLAIVES];
    public final WorldRelic[] relics = new WorldRelic[MAX_RELICS];
    public final DelayedStrike[] delayed = new DelayedStrike[MAX_DELAYED];
    public int relicsCollected, relicsPlaced, fieldAcquireMisses, delayedAcquireMisses;
    public RelicKind lastRelicKind;
    private final ArenaWorld world;
    private final float[] fieldClocks = new float[2], glaiveClocks = new float[2];
    private int randomState;
    private static final float TAU = (float) (Math.PI * 2);

    SkillExpansion(ArenaWorld world) {
        this.world = world;
        for (int i = 0; i < orbiters.length; i++) orbiters[i] = new OrbitBlade();
        for (int i = 0; i < fields.length; i++) fields[i] = new GroundField();
        for (int i = 0; i < glaives.length; i++) glaives[i] = new Glaive();
        for (int i = 0; i < relics.length; i++) relics[i] = new WorldRelic();
        for (int i = 0; i < delayed.length; i++) delayed[i] = new DelayedStrike();
    }

    public static float projectileSpeed(ArenaWorld.Player player) { return 1 + .1f * player.projectileSpeedLevel; }
    public static float duration(ArenaWorld.Player player) { return 1 + .15f * player.durationLevel; }
    public static float ricochetMultiplier() { return .75f; }
    public static int sentinelCount(int rank) { return rank + 1; }
    public static float sentinelDamage(int rank) { return 8 + 4 * rank; }
    public static float sentinelRadius() { return 90; }
    public static float sentinelInterval() { return .6f; }
    public static float fieldDamage(int rank) { return 7 + 4 * rank; }
    public static float fieldDuration(int rank) { return 2.5f + .5f * rank; }
    public static float fieldRadius(int rank) { return 65 + 10 * rank; }
    public static int fieldCount(int rank) { return rank; }
    public static float fieldInterval() { return .5f; }
    public static float fieldCooldown() { return 4; }
    public static float fieldCooldown(int rank) { return 4f / Math.max(1, rank); }
    public static float glaiveDamage(int rank) { return 14 + 6 * rank; }
    public static float glaiveRange(int rank) { return 240 + 40 * rank; }
    public static float glaiveCooldown() { return 3.2f; }
    public static float frostAmount(int rank) { return .1f + .05f * rank; }
    public static float frostDuration(int rank) { return .75f + .25f * rank; }
    public static float wardCooldown(int rank) { return 21 - 3 * rank; }
    public static float echoMultiplier() { return .45f; }
    public static float shatterMultiplier() { return .45f; }
    public static float reapMultiplier() { return .4f; }

    void reset(int seed) {
        randomState = seed ^ 0x4e62b7d1;
        relicsCollected = relicsPlaced = fieldAcquireMisses = delayedAcquireMisses = 0; lastRelicKind = null;
        Arrays.fill(fieldClocks, 0); Arrays.fill(glaiveClocks, 0);
        for (OrbitBlade blade : orbiters) { blade.active = false; Arrays.fill(blade.contactClocks, 0); }
        for (GroundField field : fields) field.active = false;
        for (Glaive glaive : glaives) glaive.active = false;
        for (WorldRelic relic : relics) relic.active = false;
        for (DelayedStrike strike : delayed) strike.active = false;
    }

    void removePlayer(int slot) {
        fieldClocks[slot] = glaiveClocks[slot] = 0;
        for (OrbitBlade blade : orbiters) if (blade.owner == slot) blade.active = false;
        for (GroundField field : fields) if (field.owner == slot) field.active = false;
        for (Glaive glaive : glaives) if (glaive.owner == slot) glaive.active = false;
        for (DelayedStrike strike : delayed) if (strike.owner == slot) strike.active = false;
        for (ArenaWorld.Bolt bolt : world.bolts) if (bolt.owner == slot) bolt.active = false;
    }

    void update() {
        float step = ArenaWorld.STEP;
        for (ArenaWorld.Player player : world.players) {
            player.wardFlash = Math.max(0, player.wardFlash - step);
            if (!player.active || !player.alive) {
                for (int i = player.slot * 4; i < player.slot * 4 + 4; i++) orbiters[i].active = false;
                continue;
            }
            player.wardCooldown = Math.max(0, Math.min(player.wardCooldown, wardCooldown(player.wardLevel)) - step);
            fieldClocks[player.slot] = Math.max(0, fieldClocks[player.slot] - step);
            glaiveClocks[player.slot] = Math.max(0, glaiveClocks[player.slot] - step);
            updateOrbiters(player);
            if (player.consecrationLevel > 0 && fieldClocks[player.slot] <= 0) castField(player);
            if (player.glaiveLevel > 0 && glaiveClocks[player.slot] <= 0) castGlaive(player);
        }
        updateFields(); updateGlaives(); updateDelayed(); updateRelics();
    }

    public boolean wardReady(ArenaWorld.Player player) { return player.wardLevel > 0 && player.wardCooldown <= 0; }

    boolean absorb(ArenaWorld.Player player) {
        if (!wardReady(player)) return false;
        player.wardCooldown = wardCooldown(player.wardLevel); player.wardFlash = .3f;
        return true;
    }

    void frost(ArenaWorld.Player player, ArenaWorld.Enemy enemy) {
        if (player.frostLevel > 0)
            slow(enemy, frostAmount(player.frostLevel), frostDuration(player.frostLevel) * duration(player));
    }

    void slow(ArenaWorld.Enemy enemy, float amount, float seconds) {
        if (!enemy.active) return;
        float resistance = ArenaWorld.isBoss(enemy.kind) ? .25f : enemy.elite ? .5f : 1;
        float boundedAmount = Math.min(.6f, amount) * resistance;
        float boundedSeconds = Math.min(4, seconds) * (ArenaWorld.isBoss(enemy.kind) ? .5f : 1);
        // A weaker hit must not extend a stronger relic slow beyond its own duration.
        if (enemy.slowTime <= 0 || boundedAmount > enemy.slowAmount + .00001f) {
            enemy.slowAmount = boundedAmount; enemy.slowTime = boundedSeconds;
        } else if (Math.abs(boundedAmount - enemy.slowAmount) <= .00001f)
            enemy.slowTime = Math.max(enemy.slowTime, boundedSeconds);
    }

    boolean ricochet(ArenaWorld.Bolt bolt) {
        if (bolt.ricochetsRemaining <= 0) return false;
        ArenaWorld.Enemy target = null; float best = 260 * 260;
        for (int i = 0; i < world.enemies.length; i++) {
            ArenaWorld.Enemy enemy = world.enemies[i];
            if (!enemy.active || (bolt.hitMask & 1L << i) != 0 && bolt.hitGenerations[i] == enemy.spawnId) continue;
            float distance = distanceSquared(bolt.x, bolt.y, enemy.x, enemy.y);
            if (distance < best) { best = distance; target = enemy; }
        }
        if (target == null) return false;
        float dx = target.x - bolt.x, dy = target.y - bolt.y;
        float inverse = 1 / length(dx, dy), speed = length(bolt.vx, bolt.vy);
        bolt.vx = dx * inverse * speed; bolt.vy = dy * inverse * speed;
        bolt.damage *= ricochetMultiplier(); bolt.ricochetsRemaining--; bolt.pierce = 0;
        return true;
    }

    void shatter(ArenaWorld.Bolt bolt) {
        DelayedStrike strike = acquireStrike(StrikeKind.SHATTER, bolt.owner, bolt.x, bolt.y, 65,
            bolt.damage * shatterMultiplier(), 0);
        if (strike != null) {
            // The collision victim is excluded; the burst damages surrounding enemies once.
            strike.excludedMask = bolt.hitMask;
            System.arraycopy(bolt.hitGenerations, 0, strike.excludedGenerations, 0, ArenaWorld.MAX_ENEMIES);
        }
    }

    void echo(int owner, float x, float y, float damage) {
        acquireStrike(StrikeKind.ECHO, owner, x, y, 42, damage * echoMultiplier(), .3f);
    }

    void swordFollowup(ArenaWorld.Player player, float reach) {
        if (!player.reapingStroke) return;
        DelayedStrike strike = acquireStrike(StrikeKind.REAP, player.slot, player.x, player.y, reach,
            player.hero.baseDamage * world.equipment.damageMultiplier(player) * reapMultiplier(), .18f);
        if (strike != null) { strike.dirX = player.facingX; strike.dirY = player.facingY; }
    }

    private DelayedStrike acquireStrike(StrikeKind kind, int owner, float x, float y, float radius, float damage, float delay) {
        for (DelayedStrike strike : delayed) if (!strike.active) {
            strike.active = true; strike.fired = false; strike.kind = kind; strike.owner = owner;
            strike.x = x; strike.y = y; strike.radius = radius; strike.damage = damage;
            strike.delay = delay; strike.ttl = delay + .22f; strike.maxTtl = strike.ttl;
            strike.dirX = strike.dirY = 0; strike.excludedMask = 0;
            return strike;
        }
        delayedAcquireMisses++; return null;
    }

    private void updateDelayed() {
        ArenaWorld.Diagnostics diagnostics = world.diagnostics();
        for (DelayedStrike strike : delayed) {
            if (!strike.active) continue;
            ArenaWorld.Player owner = world.players[strike.owner];
            if (!owner.active || !owner.alive) { strike.active = false; continue; }
            strike.delay -= ArenaWorld.STEP; strike.ttl -= ArenaWorld.STEP;
            if (!strike.fired && strike.delay <= .000001f) {
                strike.fired = true;
                for (int i = 0; i < world.enemies.length; i++) {
                    ArenaWorld.Enemy enemy = world.enemies[i];
                    if (!enemy.active || (strike.excludedMask & 1L << i) != 0
                        && strike.excludedGenerations[i] == enemy.spawnId) continue;
                    if (diagnostics != null) diagnostics.expansionCollisionCandidates++;
                    float dx = enemy.x - strike.x, dy = enemy.y - strike.y;
                    float range = strike.radius + (strike.kind == StrikeKind.REAP ? enemy.radius : 0);
                    if (dx * dx + dy * dy > range * range) continue;
                    if (strike.kind == StrikeKind.REAP && (dx * strike.dirX + dy * strike.dirY) / length(dx, dy) < .5735764f) continue;
                    damage(enemy, strike.damage, strike.owner, strike.kind == StrikeKind.REAP ? ArenaWorld.ImpactKind.SWORD : ArenaWorld.ImpactKind.MAGIC,
                        dx, dy);
                }
            }
            if (strike.ttl <= 0) strike.active = false;
        }
    }

    private void updateOrbiters(ArenaWorld.Player player) {
        ArenaWorld.Diagnostics diagnostics = world.diagnostics();
        int count = player.sentinelLevel > 0 ? sentinelCount(player.sentinelLevel) : 0;
        player.sentinelAngle = (player.sentinelAngle + ArenaWorld.STEP * 1.8f) % TAU;
        for (int i = 0; i < 4; i++) {
            OrbitBlade blade = orbiters[player.slot * 4 + i];
            if (i >= count) { blade.active = false; continue; }
            if (!blade.active) { Arrays.fill(blade.contactClocks, 0); Arrays.fill(blade.contactGenerations, -1); }
            blade.active = true; blade.owner = player.slot;
            blade.angle = player.sentinelAngle + TAU * i / count;
            blade.x = player.x + (float) Math.cos(blade.angle) * sentinelRadius();
            blade.y = player.y + (float) Math.sin(blade.angle) * sentinelRadius();
            for (int index = 0; index < world.enemies.length; index++) {
                blade.contactClocks[index] = Math.max(0, blade.contactClocks[index] - ArenaWorld.STEP);
                ArenaWorld.Enemy enemy = world.enemies[index];
                if (!enemy.active || blade.contactClocks[index] > 0 && blade.contactGenerations[index] == enemy.spawnId) continue;
                if (diagnostics != null) diagnostics.expansionCollisionCandidates++;
                if (distanceSquared(blade.x, blade.y, enemy.x, enemy.y) > square(enemy.radius + 12)) continue;
                blade.contactClocks[index] = sentinelInterval(); blade.contactGenerations[index] = enemy.spawnId;
                damage(enemy, sentinelDamage(player.sentinelLevel) * world.equipment.damageMultiplier(player), player.slot,
                    ArenaWorld.ImpactKind.MAGIC, enemy.x - blade.x, enemy.y - blade.y);
            }
        }
    }

    private void castField(ArenaWorld.Player player) {
        ArenaWorld.Enemy target = nearest(player.x, player.y, 520);
        if (target == null) return;
        int count = 0;
        for (GroundField field : fields) if (field.active && field.owner == player.slot) count++;
        if (count >= fieldCount(player.consecrationLevel)) return;
        // Three dedicated slots per owner prevent one build starving the other player's fields.
        for (int i = player.slot * 3; i < player.slot * 3 + 3; i++) {
            GroundField field = fields[i]; if (field.active) continue;
            field.active = true; field.owner = player.slot; field.x = target.x; field.y = target.y;
            field.radius = fieldRadius(player.consecrationLevel);
            field.ttl = field.maxTtl = Math.min(8, fieldDuration(player.consecrationLevel) * duration(player));
            field.damage = fieldDamage(player.consecrationLevel) * world.equipment.damageMultiplier(player);
            field.pulseClock = 0; fieldClocks[player.slot] = fieldCooldown(player.consecrationLevel); return;
        }
        fieldAcquireMisses++;
    }

    private void updateFields() {
        ArenaWorld.Diagnostics diagnostics = world.diagnostics();
        for (GroundField field : fields) {
            if (!field.active) continue;
            ArenaWorld.Player owner = world.players[field.owner];
            if (!owner.active || !owner.alive) { field.active = false; continue; }
            field.ttl -= ArenaWorld.STEP; field.pulseClock -= ArenaWorld.STEP;
            if (field.ttl <= 0) { field.active = false; continue; }
            if (field.pulseClock <= .000001f) {
                field.pulseClock += fieldInterval();
                for (ArenaWorld.Enemy enemy : world.enemies) {
                    if (!enemy.active) continue;
                    if (diagnostics != null) diagnostics.expansionCollisionCandidates++;
                    if (distanceSquared(enemy.x, enemy.y, field.x, field.y) <= square(field.radius))
                        damage(enemy, field.damage, field.owner, ArenaWorld.ImpactKind.MAGIC, enemy.x - field.x, enemy.y - field.y);
                }
            }
        }
    }

    private void castGlaive(ArenaWorld.Player player) {
        Glaive glaive = glaives[player.slot]; if (glaive.active) return;
        ArenaWorld.Enemy target = nearest(player.x, player.y, glaiveRange(player.glaiveLevel) + 100);
        if (target == null) return;
        float dx = target.x - player.x, dy = target.y - player.y, inverse = 1 / length(dx, dy);
        glaive.active = true; glaive.returning = false; glaive.owner = player.slot;
        glaive.x = player.x; glaive.y = player.y; glaive.vx = dx * inverse; glaive.vy = dy * inverse;
        glaive.speed = 420 * projectileSpeed(player); glaive.remaining = glaiveRange(player.glaiveLevel);
        glaive.damage = glaiveDamage(player.glaiveLevel) * world.equipment.damageMultiplier(player);
        glaive.age = glaive.angle = 0; glaive.ttl = 5;
        Arrays.fill(glaive.hitGenerations, -1); glaiveClocks[player.slot] = glaiveCooldown();
    }

    private void updateGlaives() {
        ArenaWorld.Diagnostics diagnostics = world.diagnostics();
        for (Glaive glaive : glaives) {
            if (!glaive.active) continue;
            ArenaWorld.Player owner = world.players[glaive.owner];
            if (!owner.active || !owner.alive) { glaive.active = false; continue; }
            float oldX = glaive.x, oldY = glaive.y;
            glaive.age += ArenaWorld.STEP; glaive.ttl -= ArenaWorld.STEP; glaive.angle = glaive.age * 12;
            float travel = glaive.speed * ArenaWorld.STEP;
            if (glaive.returning) {
                float dx = owner.x - glaive.x, dy = owner.y - glaive.y, distance = length(dx, dy);
                travel = Math.min(travel, distance); glaive.vx = dx / distance; glaive.vy = dy / distance;
            } else travel = Math.min(travel, glaive.remaining);
            glaive.x = clamp(glaive.x + glaive.vx * travel, -ArenaWorld.HALF_WIDTH, ArenaWorld.HALF_WIDTH);
            glaive.y = clamp(glaive.y + glaive.vy * travel, -ArenaWorld.HALF_HEIGHT, ArenaWorld.HALF_HEIGHT);
            for (int i = 0; i < world.enemies.length; i++) {
                ArenaWorld.Enemy enemy = world.enemies[i];
                if (!enemy.active || glaive.hitGenerations[i] == enemy.spawnId) continue;
                if (diagnostics != null) diagnostics.expansionCollisionCandidates++;
                if (segmentDistanceSquared(oldX, oldY, glaive.x, glaive.y, enemy.x, enemy.y) > square(enemy.radius + 12)) continue;
                glaive.hitGenerations[i] = enemy.spawnId;
                damage(enemy, glaive.damage, glaive.owner, ArenaWorld.ImpactKind.BOLT, glaive.vx, glaive.vy);
            }
            if (!glaive.returning) {
                glaive.remaining -= travel;
                if (glaive.remaining <= .0001f) { glaive.returning = true; Arrays.fill(glaive.hitGenerations, -1); }
            } else if (distanceSquared(glaive.x, glaive.y, owner.x, owner.y) <= 18 * 18) glaive.active = false;
            if (glaive.ttl <= 0) glaive.active = false;
        }
    }

    private void updateRelics() {
        ArenaWorld.Diagnostics diagnostics = world.diagnostics();
        // Five rare discoveries across either authored timeline; never share the combat/chest RNG stream.
        if (relicsPlaced < 5 && world.elapsed >= world.durationSeconds() * (.13f + relicsPlaced * .19f)) {
            WorldRelic available = null;
            for (WorldRelic relic : relics) if (!relic.active) { available = relic; break; }
            if (available == null) {
                if (diagnostics != null) diagnostics.relicPoolBlockedTicks++;
            } else if (placeRelic(available)) {
                available.kind = (relicsPlaced & 1) == 0 ? RelicKind.SOUL_HARVEST : RelicKind.STASIS;
                available.active = true; available.age = 0; relicsPlaced++;
            } else if (diagnostics != null) {
                diagnostics.relicPlacementMisses++;
            }
        }
        for (WorldRelic relic : relics) {
            if (!relic.active) continue;
            relic.age += ArenaWorld.STEP;
            for (ArenaWorld.Player player : world.players) {
                if (!player.active || !player.alive || distanceSquared(player.x, player.y, relic.x, relic.y) > 40 * 40) continue;
                collectRelic(relic, player); break;
            }
        }
    }

    private boolean placeRelic(WorldRelic relic) {
        ArenaWorld.Player anchor = null;
        for (ArenaWorld.Player player : world.players) if (player.active && player.alive) {
            if (anchor == null || nextRandom() < .5f) anchor = player;
        }
        if (anchor == null) return false;
        for (int attempt = 0; attempt < 32; attempt++) {
            float angle = nextRandom() * TAU, distance = 240 + nextRandom() * 260;
            float x = clamp(anchor.x + (float) Math.cos(angle) * distance, -ArenaWorld.HALF_WIDTH + 160, ArenaWorld.HALF_WIDTH - 160);
            float y = clamp(anchor.y + (float) Math.sin(angle) * distance, -ArenaWorld.HALF_HEIGHT + 160, ArenaWorld.HALF_HEIGHT - 160);
            if (!ArenaWorld.chestLandmarkClear(x, y)) continue;
            boolean clear = true;
            for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active && distanceSquared(enemy.x, enemy.y, x, y) < 100 * 100) clear = false;
            for (WorldRelic other : relics) if (other.active && distanceSquared(other.x, other.y, x, y) < 100 * 100) clear = false;
            for (ArenaWorld.Chest chest : world.chests) if (chest.active && distanceSquared(chest.x, chest.y, x, y) < 100 * 100) clear = false;
            if (!clear) continue;
            relic.x = x; relic.y = y; return true;
        }
        return false;
    }

    void collectRelic(WorldRelic relic, ArenaWorld.Player player) {
        if (!relic.active || !player.active || !player.alive) return;
        relic.active = false; relicsCollected++; lastRelicKind = relic.kind;
        if (relic.kind == RelicKind.SOUL_HARVEST) {
            for (ArenaWorld.Pickup pickup : world.pickups)
                if (pickup.active && !pickup.healing) pickup.vacuumOwner = player.slot;
        } else {
            for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active) slow(enemy, .5f, 3);
        }
    }

    private float nextRandom() {
        randomState ^= randomState << 13; randomState ^= randomState >>> 17; randomState ^= randomState << 5;
        return (randomState >>> 8) * (1f / 16777216f);
    }

    private void damage(ArenaWorld.Enemy enemy, float damage, int owner, ArenaWorld.ImpactKind kind, float dx, float dy) {
        world.hit(enemy, damage, owner, true, false, kind, dx, dy);
    }

    private ArenaWorld.Enemy nearest(float x, float y, float range) {
        ArenaWorld.Enemy target = null; float best = range * range;
        for (ArenaWorld.Enemy enemy : world.enemies) if (enemy.active) {
            float distance = distanceSquared(x, y, enemy.x, enemy.y);
            if (distance < best) { best = distance; target = enemy; }
        }
        return target;
    }

    private static float square(float value) { return value * value; }
    private static float distanceSquared(float x, float y, float xx, float yy) { return square(xx - x) + square(yy - y); }
    private static float length(float x, float y) { return (float) Math.sqrt(Math.max(.0001f, x * x + y * y)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float segmentDistanceSquared(float x, float y, float xx, float yy, float px, float py) {
        float dx = xx - x, dy = yy - y, squared = dx * dx + dy * dy;
        float t = squared > 0 ? clamp(((px - x) * dx + (py - y) * dy) / squared, 0, 1) : 0;
        return distanceSquared(px, py, x + dx * t, y + dy * t);
    }

    public static final class OrbitBlade {
        public boolean active; public int owner; public float x, y, angle;
        private final float[] contactClocks = new float[ArenaWorld.MAX_ENEMIES];
        private final int[] contactGenerations = new int[ArenaWorld.MAX_ENEMIES];
    }
    public static final class GroundField {
        public boolean active; public int owner; public float x, y, radius, ttl, maxTtl, damage;
        private float pulseClock;
    }
    public static final class Glaive {
        public boolean active, returning; public int owner; public float x, y, vx, vy, angle, age, ttl;
        private float speed, remaining, damage;
        private final int[] hitGenerations = new int[ArenaWorld.MAX_ENEMIES];
    }
    public static final class WorldRelic {
        public boolean active; public RelicKind kind; public float x, y, age;
    }
    public static final class DelayedStrike {
        public boolean active, fired; public StrikeKind kind; public int owner;
        public float x, y, radius, delay, ttl, maxTtl, damage, dirX, dirY;
        private long excludedMask;
        private final int[] excludedGenerations = new int[ArenaWorld.MAX_ENEMIES];
    }
}
