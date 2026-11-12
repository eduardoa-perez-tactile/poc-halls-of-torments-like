package com.couchcrypt.game;

/** Fixed-step, allocation-free combat simulation. Coordinates are independent of rendering. */
public final class ArenaWorld {
    public static final float HALF_WIDTH = 1200f, HALF_HEIGHT = 900f, ROUND_SECONDS = 120f;
    public static final int MAX_ENEMIES = 64, MAX_BOLTS = 96, MAX_PICKUPS = 96;
    public static final float STEP = 1f / 60f;
    private static final float WALK_SPEED = 180f, DODGE_DURATION = .22f, DODGE_COOLDOWN = 2.4f;
    private static final float PLAYER_RADIUS = 20f, ENEMY_RADIUS = 20f;
    public enum Outcome { RUNNING, VICTORY, DEFEAT }

    public Outcome outcome;
    public float elapsed;
    public int kills, souls, activeEnemies;
    public final Player[] players = { new Player(0), new Player(1) };
    public final Enemy[] enemies = new Enemy[MAX_ENEMIES];
    public final Bolt[] bolts = new Bolt[MAX_BOLTS];
    public final Pickup[] pickups = new Pickup[MAX_PICKUPS];
    private final float[] inputX = new float[2], inputY = new float[2];
    private final boolean[] pendingDodge = new boolean[2];
    private float accumulator, spawnCountdown;
    private int randomState;

    public ArenaWorld() {
        for (int i = 0; i < enemies.length; i++) enemies[i] = new Enemy();
        for (int i = 0; i < bolts.length; i++) bolts[i] = new Bolt();
        for (int i = 0; i < pickups.length; i++) pickups[i] = new Pickup();
        reset();
    }

    /** Clears the round; callers activate the connected slots explicitly. */
    public void reset() {
        outcome = Outcome.RUNNING;
        elapsed = accumulator = 0f;
        kills = souls = activeEnemies = 0;
        spawnCountdown = .65f;
        randomState = 0x5a17c9e3;
        for (Player p : players) {
            p.active = p.alive = false;
            p.x = p.y = p.walkTime = p.reviveProgress = 0f;
            p.maxHealth = p.health = 100f;
            p.dodgeCooldown = p.dodgeTime = p.invulnerable = p.attackCooldown = 0f;
            p.facingX = 0f; p.facingY = -1f; p.kills = 0;
            p.dodgeX = 0f; p.dodgeY = -1f;
            inputX[p.slot] = inputY[p.slot] = 0f;
            pendingDodge[p.slot] = false;
        }
        for (Enemy e : enemies) e.active = false;
        for (Bolt b : bolts) b.active = false;
        for (Pickup p : pickups) p.active = false;
    }

    public void activatePlayer(int slot) {
        Player p = player(slot);
        if (p.active) return;
        Player ally = players[1 - slot];
        p.x = ally.active && ally.alive ? clamp(ally.x + (slot == 1 ? 64f : -64f), -HALF_WIDTH + PLAYER_RADIUS, HALF_WIDTH - PLAYER_RADIUS) : 0f;
        p.y = ally.active && ally.alive ? ally.y : 0f;
        p.active = p.alive = true;
        p.health = p.maxHealth;
        p.dodgeCooldown = p.dodgeTime = p.attackCooldown = p.reviveProgress = 0f;
        p.invulnerable = 1f;
        inputX[slot] = inputY[slot] = 0f;
        pendingDodge[slot] = false;
    }

    public void deactivatePlayer(int slot) {
        Player p = player(slot);
        p.active = p.alive = false;
        inputX[slot] = inputY[slot] = 0f;
        pendingDodge[slot] = false;
    }

    /** Pausing must also discard a press waiting for the next fixed tick. */
    public void clearInputs() {
        for (int i = 0; i < 2; i++) { inputX[i] = inputY[i] = 0; pendingDodge[i] = false; }
    }

    public void setInput(int slot, float x, float y, boolean dodge) {
        player(slot);
        if (!Float.isFinite(x) || !Float.isFinite(y)) { x = 0f; y = 0f; }
        x = clamp(x, -1f, 1f); y = clamp(y, -1f, 1f);
        float lengthSquared = x * x + y * y;
        if (lengthSquared > 1f) {
            float inverse = 1f / (float) Math.sqrt(lengthSquared);
            x *= inverse; y *= inverse;
        }
        inputX[slot] = x; inputY[slot] = y;
        pendingDodge[slot] |= dodge;
    }

    public void update(float delta) {
        if (outcome != Outcome.RUNNING || !Float.isFinite(delta) || delta <= 0f) return;
        boolean anyoneActive = false;
        for (Player p : players) anyoneActive |= p.active;
        if (!anyoneActive) return;
        accumulator += Math.min(delta, .1f);
        while (accumulator + .000001f >= STEP && outcome == Outcome.RUNNING) {
            accumulator = Math.max(0f, accumulator - STEP);
            tick();
        }
    }

    public float timeRemaining() { return Math.max(0f, ROUND_SECONDS - elapsed); }

    private void tick() {
        elapsed = Math.min(ROUND_SECONDS, elapsed + STEP);
        for (Player p : players) updatePlayer(p);
        updateEnemies();
        updateBolts();
        updatePickups();
        updateRevival();
        boolean living = false;
        for (Player p : players) living |= p.active && p.alive;
        if (!living) { outcome = Outcome.DEFEAT; return; }
        if (elapsed >= ROUND_SECONDS - .0001f) { elapsed = ROUND_SECONDS; outcome = Outcome.VICTORY; return; }
        spawnCountdown -= STEP;
        if (spawnCountdown <= 0f) {
            spawnEnemy();
            spawnCountdown += Math.max(.23f, 1.15f - elapsed * .007f);
        }
    }

    private void updatePlayer(Player p) {
        p.dodgeCooldown = Math.max(0f, p.dodgeCooldown - STEP);
        p.invulnerable = Math.max(0f, p.invulnerable - STEP);
        p.attackCooldown = Math.max(0f, p.attackCooldown - STEP);
        boolean dodge = pendingDodge[p.slot];
        pendingDodge[p.slot] = false;
        if (!p.active || !p.alive) return;
        float x = inputX[p.slot], y = inputY[p.slot];
        float magnitudeSquared = x * x + y * y;
        if (magnitudeSquared > .0025f) {
            float inverse = 1f / (float) Math.sqrt(magnitudeSquared);
            p.facingX = x * inverse; p.facingY = y * inverse;
        }
        if (dodge && p.dodgeCooldown <= 0f && p.dodgeTime <= 0f) {
            p.dodgeTime = DODGE_DURATION;
            p.dodgeCooldown = DODGE_COOLDOWN;
            p.invulnerable = Math.max(p.invulnerable, DODGE_DURATION);
            p.dodgeX = p.facingX; p.dodgeY = p.facingY;
        }
        if (p.dodgeTime > 0f) {
            float duration = Math.min(STEP, p.dodgeTime);
            p.x += p.dodgeX * WALK_SPEED * 3f * duration;
            p.y += p.dodgeY * WALK_SPEED * 3f * duration;
            p.dodgeTime = Math.max(0f, p.dodgeTime - STEP);
            p.walkTime += STEP * 2f;
        } else {
            p.x += x * WALK_SPEED * STEP; p.y += y * WALK_SPEED * STEP;
            if (magnitudeSquared > .0025f) p.walkTime += STEP;
        }
        p.x = clamp(p.x, -HALF_WIDTH + PLAYER_RADIUS, HALF_WIDTH - PLAYER_RADIUS);
        p.y = clamp(p.y, -HALF_HEIGHT + PLAYER_RADIUS, HALF_HEIGHT - PLAYER_RADIUS);
        if (p.attackCooldown <= 0f) fire(p);
    }

    private void fire(Player p) {
        Enemy target = null;
        float nearest = 700f * 700f;
        for (Enemy e : enemies) {
            if (!e.active) continue;
            float dx = e.x - p.x, dy = e.y - p.y, distance = dx * dx + dy * dy;
            if (distance < nearest) { nearest = distance; target = e; }
        }
        if (target == null) return;
        for (Bolt b : bolts) {
            if (b.active) continue;
            float dx = target.x - p.x, dy = target.y - p.y;
            float length = (float) Math.sqrt(Math.max(.001f, dx * dx + dy * dy));
            b.active = true; b.x = p.x; b.y = p.y;
            b.vx = dx / length * 680f; b.vy = dy / length * 680f;
            b.ttl = 1.15f; b.owner = p.slot;
            p.attackCooldown = .55f;
            return;
        }
    }

    private void updateEnemies() {
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.animTime += STEP;
            e.hitFlash = Math.max(0f, e.hitFlash - STEP);
            Player target = null;
            float nearest = Float.MAX_VALUE;
            for (Player p : players) {
                if (!p.active || !p.alive) continue;
                float dx = p.x - e.x, dy = p.y - e.y, distance = dx * dx + dy * dy;
                if (distance < nearest) { nearest = distance; target = p; }
            }
            if (target == null) { e.vx = e.vy = 0f; continue; }
            float length = (float) Math.sqrt(Math.max(nearest, .001f));
            float speed = 53f + elapsed * .16f;
            e.vx = (target.x - e.x) / length * speed;
            e.vy = (target.y - e.y) / length * speed;
            if (nearest > 28f * 28f) { e.x += e.vx * STEP; e.y += e.vy * STEP; }
            for (Player p : players) {
                if (!p.active || !p.alive || p.invulnerable > 0f) continue;
                float dx = p.x - e.x, dy = p.y - e.y;
                if (dx * dx + dy * dy < (PLAYER_RADIUS + ENEMY_RADIUS) * (PLAYER_RADIUS + ENEMY_RADIUS)) {
                    p.health = Math.max(0f, p.health - 12f);
                    p.invulnerable = .75f;
                    if (p.health <= 0f) { p.alive = false; p.dodgeTime = 0f; }
                }
            }
        }
    }

    private void updateBolts() {
        for (Bolt b : bolts) {
            if (!b.active) continue;
            float oldX = b.x, oldY = b.y;
            b.x += b.vx * STEP; b.y += b.vy * STEP; b.ttl -= STEP;
            for (Enemy e : enemies) {
                if (!e.active) continue;
                float dx = b.x - oldX, dy = b.y - oldY;
                float segmentLength = dx * dx + dy * dy;
                float t = segmentLength > 0f ? clamp(((e.x - oldX) * dx + (e.y - oldY) * dy) / segmentLength, 0f, 1f) : 0f;
                float ex = e.x - (oldX + dx * t), ey = e.y - (oldY + dy * t);
                if (ex * ex + ey * ey > 25f * 25f) continue;
                b.active = false;
                e.health -= 12f; e.hitFlash = .12f;
                if (e.health <= 0f) {
                    e.active = false; activeEnemies = Math.max(0, activeEnemies - 1);
                    kills++; players[b.owner].kills++;
                    drop(e.x, e.y, false);
                    if (kills % 7 == 0) drop(e.x + 14f, e.y, true);
                }
                break;
            }
            if (b.ttl <= 0f || Math.abs(b.x) > HALF_WIDTH + 50f || Math.abs(b.y) > HALF_HEIGHT + 50f) b.active = false;
        }
    }

    private void drop(float x, float y, boolean healing) {
        for (Pickup p : pickups) {
            if (p.active) continue;
            p.active = true; p.x = x; p.y = y; p.healing = healing; p.value = healing ? 24 : 1;
            return;
        }
        // A full pool retains rewards by merging into an existing pickup of the same type.
        for (Pickup p : pickups) if (p.healing == healing) { p.value += healing ? 24 : 1; return; }
    }

    private void updatePickups() {
        for (Pickup pickup : pickups) {
            if (!pickup.active) continue;
            for (Player p : players) {
                if (!p.active || !p.alive || (pickup.healing && p.health >= p.maxHealth)) continue;
                float dx = p.x - pickup.x, dy = p.y - pickup.y;
                if (dx * dx + dy * dy > 56f * 56f) continue;
                if (pickup.healing) p.health = Math.min(p.maxHealth, p.health + pickup.value);
                else souls += pickup.value;
                pickup.active = false;
                break;
            }
        }
    }

    private void updateRevival() {
        for (Player down : players) {
            if (!down.active || down.alive) continue;
            Player ally = players[1 - down.slot];
            float dx = ally.x - down.x, dy = ally.y - down.y;
            if (!ally.active || !ally.alive || dx * dx + dy * dy > 95f * 95f) { down.reviveProgress = 0f; continue; }
            down.reviveProgress += STEP;
            if (down.reviveProgress >= 2f - .0001f) {
                down.alive = true; down.health = down.maxHealth * .45f;
                down.invulnerable = 1.5f; down.reviveProgress = 0f;
                pendingDodge[down.slot] = false;
            }
        }
    }

    private void spawnEnemy() {
        if (activeEnemies >= MAX_ENEMIES) return;
        Player anchor = null;
        int living = 0;
        for (Player p : players) if (p.active && p.alive) living++;
        int selected = (int) (random() * Math.max(1, living));
        for (Player p : players) if (p.active && p.alive && selected-- == 0) { anchor = p; break; }
        if (anchor == null) return;
        for (Enemy e : enemies) {
            if (e.active) continue;
            float angle = random() * (float) (Math.PI * 2);
            e.x = clamp(anchor.x + (float) Math.cos(angle) * 650f, -HALF_WIDTH + ENEMY_RADIUS, HALF_WIDTH - ENEMY_RADIUS);
            e.y = clamp(anchor.y + (float) Math.sin(angle) * 650f, -HALF_HEIGHT + ENEMY_RADIUS, HALF_HEIGHT - ENEMY_RADIUS);
            float dx = e.x - anchor.x, dy = e.y - anchor.y;
            if (dx * dx + dy * dy < 300f * 300f) {
                e.x = clamp(anchor.x + (anchor.x > 0f ? -600f : 600f), -HALF_WIDTH + ENEMY_RADIUS, HALF_WIDTH - ENEMY_RADIUS);
                e.y = anchor.y;
            }
            e.maxHealth = e.health = 24f + (int) (elapsed / 40f) * 12f;
            e.animTime = random(); e.hitFlash = e.vx = e.vy = 0f;
            e.active = true; activeEnemies++;
            return;
        }
    }

    private float random() {
        randomState ^= randomState << 13; randomState ^= randomState >>> 17; randomState ^= randomState << 5;
        return (randomState >>> 8) * (1f / 16777216f);
    }
    private Player player(int slot) {
        if (slot < 0 || slot >= players.length) throw new IllegalArgumentException("Player slot must be 0 or 1");
        return players[slot];
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    public static final class Player {
        public final int slot;
        public int kills;
        public boolean active, alive;
        public float x, y, health, maxHealth, dodgeCooldown, dodgeTime, invulnerable, attackCooldown;
        public float reviveProgress, facingX, facingY, walkTime;
        private float dodgeX, dodgeY;
        private Player(int slot) { this.slot = slot; }
    }
    public static final class Enemy {
        public boolean active;
        public float x, y, health, maxHealth, animTime, hitFlash, vx, vy;
    }
    public static final class Bolt {
        public boolean active;
        public float x, y, vx, vy, ttl;
        public int owner;
    }
    public static final class Pickup {
        public boolean active;
        public float x, y;
        public int value;
        public boolean healing;
    }
}
