package com.couchcrypt.game;

/** A bounded, fixed-step shared world. Presentation and sockets never run inside its tick. */
public final class ArenaWorld {
    public static final float HALF_WIDTH = 1200f, HALF_HEIGHT = 900f, ROUND_SECONDS = 180f;
    public static final int MAX_ENEMIES = 64, MAX_BOLTS = 96, MAX_PICKUPS = 96;
    public static final int MAX_SHOTS = 96, MAX_HAZARDS = 12, MAX_EFFECTS = 96;
    public static final float STEP = 1f / 60f;
    private static final float DODGE_DURATION = .22f, PLAYER_RADIUS = 20f;
    public enum Outcome { RUNNING, VICTORY, DEFEAT }
    public enum Hero {
        WARDEN(100, 28, .7f, 180, 160, 2.4f),
        RANGER(100, 16, .55f, 198, 800, 2.4f);

        public final float baseHealth, baseDamage, attackInterval, moveSpeed, attackRange, dodgeCooldown;
        Hero(float baseHealth, float baseDamage, float attackInterval, float moveSpeed, float attackRange, float dodgeCooldown) {
            this.baseHealth = baseHealth; this.baseDamage = baseDamage; this.attackInterval = attackInterval;
            this.moveSpeed = moveSpeed; this.attackRange = attackRange; this.dodgeCooldown = dodgeCooldown;
        }
    }
    public enum EnemyKind { GHOUL, WRAITH, BRUTE, CULTIST, BOSS }
    public enum EffectKind { SLASH, HIT, DEATH, AURA, LIGHTNING }

    public Outcome outcome;
    public float elapsed;
    public int kills, souls, activeEnemies, wave, elitesKilled, bossPhase, runSeed;
    public boolean bossSpawned, bossDefeated;
    public Enemy boss;
    public int swordSwings, arrowsFired, enemyDeaths, pickupsCollected, heroHits;
    public final Player[] players = {new Player(0), new Player(1)};
    public final Enemy[] enemies = new Enemy[MAX_ENEMIES];
    public final Bolt[] bolts = new Bolt[MAX_BOLTS];
    public final Pickup[] pickups = new Pickup[MAX_PICKUPS];
    public final Shot[] shots = new Shot[MAX_SHOTS];
    public final Hazard[] hazards = new Hazard[MAX_HAZARDS];
    public final Effect[] effects = new Effect[MAX_EFFECTS];
    public final PartyProgression progression;
    public final RunEquipment equipment;
    public final ArenaTimeline timeline;
    private final float[] inputX = new float[2], inputY = new float[2];
    private final boolean[] pendingDodge = new boolean[2];
    private float accumulator, spawnCountdown;
    private double simulationElapsed;
    private int sharedXpRemainder;
    private int randomState, runNumber, nextCheckpoint, encounterPhase;

    public ArenaWorld() { this(ArenaTimeline.SHORT); }

    public ArenaWorld(ArenaTimeline timeline) {
        this.timeline = java.util.Objects.requireNonNull(timeline);
        for (int i = 0; i < enemies.length; i++) enemies[i] = new Enemy();
        for (int i = 0; i < bolts.length; i++) bolts[i] = new Bolt();
        for (int i = 0; i < pickups.length; i++) pickups[i] = new Pickup();
        for (int i = 0; i < shots.length; i++) shots[i] = new Shot();
        for (int i = 0; i < hazards.length; i++) hazards[i] = new Hazard();
        for (int i = 0; i < effects.length; i++) effects[i] = new Effect();
        progression = new PartyProgression(this);
        equipment = new RunEquipment(this);
        reset();
    }

    public void reset() {
        outcome = Outcome.RUNNING; elapsed = accumulator = 0; simulationElapsed = 0; sharedXpRemainder = 0;
        kills = souls = activeEnemies = elitesKilled = nextCheckpoint = encounterPhase = 0;
        wave = bossPhase = 1; bossSpawned = bossDefeated = false; boss = null;
        swordSwings = arrowsFired = enemyDeaths = pickupsCollected = heroHits = 0;
        spawnCountdown = .65f;
        runSeed = 0x5a17c9e3 ^ (++runNumber * 0x45d9f3b); randomState = runSeed;
        progression.reset(); equipment.reset();
        for (Player p : players) {
            p.active = p.alive = false; p.x = p.y = p.walkTime = p.reviveProgress = 0;
            resetBuild(p); p.dodgeCooldown = p.dodgeTime = p.invulnerable = p.attackCooldown = 0;
            p.facingX = 0; p.facingY = -1; p.kills = p.revives = 0;
            p.damageDealt = p.damageTaken = 0; p.dodgeX = 0; p.dodgeY = -1;
            inputX[p.slot] = inputY[p.slot] = 0; pendingDodge[p.slot] = false;
        }
        for (Enemy e : enemies) e.active = false;
        for (Bolt b : bolts) b.active = false;
        for (Pickup p : pickups) p.active = false;
        for (Shot s : shots) s.active = false;
        for (Hazard h : hazards) h.active = false;
        for (Effect e : effects) e.active = false;
    }

    private void resetBuild(Player p) {
        p.maxHealth = p.health = p.hero.baseHealth;
        p.damageMultiplier = p.attackRateMultiplier = p.moveMultiplier = p.dodgeCooldownMultiplier = 1;
        p.cleaveMultiplier = p.pickupMultiplier = 1; p.critChance = 0;
        p.projectileCount = 1; p.pierce = p.auraLevel = p.stormLevel = 0;
        p.auraCooldown = p.stormCooldown = 0;
    }

    public void setHero(int slot, Hero hero) {
        Player p = player(slot);
        if (p.active) throw new IllegalStateException("Heroes can only change before joining a run");
        if (hero == null) throw new IllegalArgumentException("Hero is required");
        p.hero = hero;
    }

    public void activatePlayer(int slot) {
        Player p = player(slot); if (p.active) return;
        Player ally = players[1 - slot];
        p.x = ally.active && ally.alive ? clamp(ally.x + (slot == 1 ? 64 : -64), -HALF_WIDTH + 20, HALF_WIDTH - 20) : 0;
        p.y = ally.active && ally.alive ? ally.y : 0;
        p.active = p.alive = true; p.health = p.maxHealth;
        p.dodgeCooldown = p.dodgeTime = p.attackCooldown = p.reviveProgress = 0;
        p.invulnerable = 1; inputX[slot] = inputY[slot] = 0; pendingDodge[slot] = false;
        // Gear first: inherited skill cards must preview the foundation's effective stats.
        equipment.activatePlayer(slot); progression.activatePlayer(slot);
        // A new participant arrives ready to fight with their granted foundation's full health.
        // Reconnecting an already-active participant returns above and never refills health.
        p.health = equipment.maxHealth(p);
    }

    public void deactivatePlayer(int slot) {
        Player p = player(slot); p.active = p.alive = false; resetBuild(p);
        p.kills = p.revives = 0; p.damageDealt = p.damageTaken = 0;
        progression.deactivatePlayer(slot); equipment.deactivatePlayer(slot); inputX[slot] = inputY[slot] = 0; pendingDodge[slot] = false;
    }

    public void clearInputs() {
        for (int i = 0; i < 2; i++) { inputX[i] = inputY[i] = 0; pendingDodge[i] = false; }
    }

    public void setInput(int slot, float x, float y, boolean dodge) {
        player(slot);
        if (!Float.isFinite(x) || !Float.isFinite(y)) { x = y = 0; }
        x = clamp(x, -1, 1); y = clamp(y, -1, 1);
        float length = x * x + y * y;
        if (length > 1) { float inverse = 1f / (float) Math.sqrt(length); x *= inverse; y *= inverse; }
        inputX[slot] = x; inputY[slot] = y; pendingDodge[slot] |= dodge;
    }

    public void update(float delta) {
        if (outcome != Outcome.RUNNING || !Float.isFinite(delta) || delta <= 0) return;
        if (!players[0].active && !players[1].active) return;
        accumulator += Math.min(delta, .1f);
        while (accumulator + .000001f >= STEP && outcome == Outcome.RUNNING) {
            accumulator = Math.max(0, accumulator - STEP); tick();
        }
    }

    /** A personal skill choice pauses combat for the whole party, including players already done. */
    public boolean levelUpPending() {
        for (int slot = 0; slot < players.length; slot++)
            if (players[slot].active && progression.offers[slot].pending) return true;
        return false;
    }

    public boolean lootPending() {
        for (int slot = 0; slot < players.length; slot++)
            if (players[slot].active && equipment.offers[slot].pending) return true;
        return false;
    }

    public boolean choicePending() { return levelUpPending() || lootPending(); }

    public float durationSeconds() { return timeline.durationSeconds(); }
    public int waveCount() { return timeline.phaseCount(); }
    public float timeRemaining() { return Math.max(0, durationSeconds() - elapsed); }

    private void tick() {
        if (levelUpPending()) {
            clearInputs();
            progression.update(STEP);
            return;
        }
        // A completed skill round can release queued loot; prepare it before advancing combat.
        equipment.updateChoices(0);
        if (lootPending()) {
            clearInputs();
            equipment.updateChoices(STEP);
            return;
        }
        // Preserve direct boundary/fixture jumps while avoiding float accumulation drift over twenty minutes.
        if (elapsed != (float) simulationElapsed) simulationElapsed = elapsed;
        simulationElapsed += 1.0 / 60.0;
        elapsed = (float) simulationElapsed;
        wave = timeline.phaseIndex(elapsed) + 1;
        for (Effect e : effects) if (e.active) { e.ttl -= STEP; if (e.ttl <= 0) e.active = false; }
        for (Player p : players) updatePlayer(p);
        updateEnemies(); updateBolts(); updateShots(); updateHazards(); updatePickups(); updateRevival();
        progression.update(STEP); equipment.update(STEP);
        boolean living = false; for (Player p : players) living |= p.active && p.alive;
        if (!living) { outcome = Outcome.DEFEAT; return; }
        if (bossDefeated) { outcome = Outcome.VICTORY; return; }
        if (elapsed >= durationSeconds() - .0001f && !bossSpawned) debugStartBoss();
        if (!bossSpawned) {
            while (nextCheckpoint < timeline.phaseCount() && elapsed >= timeline.phaseStart(nextCheckpoint)) {
                EnemyKind elite = timeline.phase(nextCheckpoint).eliteKind();
                if (elite != null && spawn(elite, true) == null) break; // Retry a temporarily full/unsafe checkpoint.
                nextCheckpoint++;
            }
        }
        updateEncounters();
    }

    private void updatePlayer(Player p) {
        p.dodgeCooldown = Math.max(0, p.dodgeCooldown - STEP);
        p.invulnerable = Math.max(0, p.invulnerable - STEP);
        p.attackCooldown = Math.max(0, p.attackCooldown - STEP);
        p.auraCooldown = Math.max(0, p.auraCooldown - STEP); p.stormCooldown = Math.max(0, p.stormCooldown - STEP);
        boolean dodge = pendingDodge[p.slot]; pendingDodge[p.slot] = false;
        if (!p.active || !p.alive) return;
        boolean choosing = equipment.choosing(p.slot);
        float x = choosing ? 0 : inputX[p.slot], y = choosing ? 0 : inputY[p.slot], magnitude = x * x + y * y;
        if (choosing) dodge = false;
        equipment.setMoving(p, magnitude > .0025f || p.dodgeTime > 0);
        p.health = Math.min(equipment.maxHealth(p), p.health + equipment.regeneration(p) * STEP);
        if (magnitude > .0025f) {
            float inverse = 1f / (float) Math.sqrt(magnitude); p.facingX = x * inverse; p.facingY = y * inverse;
        }
        if (dodge && p.dodgeCooldown <= 0 && p.dodgeTime <= 0) {
            p.dodgeTime = DODGE_DURATION; p.dodgeCooldown = equipment.dodgeCooldown(p);
            p.invulnerable = Math.max(p.invulnerable, DODGE_DURATION); p.dodgeX = p.facingX; p.dodgeY = p.facingY;
        }
        float speed = p.hero.moveSpeed * equipment.moveMultiplier(p);
        if (p.dodgeTime > 0) {
            float duration = Math.min(STEP, p.dodgeTime); p.x += p.dodgeX * speed * 3 * duration;
            p.y += p.dodgeY * speed * 3 * duration; p.dodgeTime = Math.max(0, p.dodgeTime - STEP); p.walkTime += STEP * 2;
        } else {
            p.x += x * speed * STEP; p.y += y * speed * STEP; if (magnitude > .0025f) p.walkTime += STEP;
        }
        p.x = clamp(p.x, -HALF_WIDTH + 20, HALF_WIDTH - 20); p.y = clamp(p.y, -HALF_HEIGHT + 20, HALF_HEIGHT - 20);
        if (p.attackCooldown <= 0) attack(p);
        if (p.auraLevel > 0 && p.auraCooldown <= 0) aura(p);
        if (p.stormLevel > 0 && p.stormCooldown <= 0) storm(p);
    }

    private void attack(Player p) {
        float reach = p.hero.attackRange * (p.hero == Hero.WARDEN ? equipment.reachMultiplier(p) : 1);
        Enemy target = nearest(p.x, p.y, reach, 0);
        if (target == null) return;
        float dx = target.x - p.x, dy = target.y - p.y, length = length(dx, dy);
        p.facingX = dx / length; p.facingY = dy / length;
        if (p.hero == Hero.WARDEN) {
            float halfArcCos = .5735764f; // cos(55 degrees)
            Effect fx = effect(EffectKind.SLASH, p.x, p.y, p.slot, .18f);
            if (fx != null) { fx.angle = (float) Math.atan2(dy, dx); fx.radius = reach; fx.sweep = 1.9198622f; }
            swordSwings++;
            for (Enemy e : enemies) {
                if (!e.active) continue;
                float ex = e.x - p.x, ey = e.y - p.y, distance = length(ex, ey);
                if (distance > reach + e.radius || (ex * p.facingX + ey * p.facingY) / distance < halfArcCos) continue;
                hit(e, critical(p, p.hero.baseDamage * equipment.damageMultiplier(p)), p.slot);
                if (e.active) {
                    e.stunTime = Math.max(e.stunTime, e.kind == EnemyKind.BOSS ? .02f : .16f);
                    float knockback = e.kind == EnemyKind.BOSS ? 3 : e.kind == EnemyKind.BRUTE ? 12 : 25;
                    e.x = clamp(e.x + ex / distance * knockback, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
                    e.y = clamp(e.y + ey / distance * knockback, -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
                }
            }
            p.attackCooldown = p.hero.attackInterval / equipment.attackRate(p);
        } else {
            int count = equipment.projectileCount(p);
            float angle = (float) Math.atan2(dy, dx);
            for (int i = 0; i < count; i++) {
                Bolt b = freeBolt(); if (b == null) break;
                float direction = angle + (i - (count - 1) * .5f) * .13f;
                b.active = true; b.x = p.x; b.y = p.y;
                b.vx = (float) Math.cos(direction) * 760; b.vy = (float) Math.sin(direction) * 760;
                b.ttl = 1.2f; b.owner = p.slot; b.damage = critical(p, p.hero.baseDamage * equipment.damageMultiplier(p));
                b.pierce = equipment.pierce(p); b.hitMask = 0; arrowsFired++;
            }
            p.attackCooldown = p.hero.attackInterval / equipment.attackRate(p);
        }
    }

    private void aura(Player p) {
        p.auraCooldown = .7f; float radius = 100 + 20 * p.auraLevel;
        Effect fx = effect(EffectKind.AURA, p.x, p.y, p.slot, .24f); if (fx != null) fx.radius = radius;
        for (Enemy e : enemies) if (e.active && distanceSquared(p.x, p.y, e.x, e.y) <= radius * radius)
            hit(e, (4 + p.auraLevel * 3) * equipment.damageMultiplier(p) * equipment.auraMultiplier(p), p.slot);
    }

    private void storm(Player p) {
        Enemy target = nearest(p.x, p.y, 500, 0); if (target == null) return;
        p.stormCooldown = 2;
        float x = p.x, y = p.y; long mask = 0;
        for (int chain = 0; chain < p.stormLevel + 1; chain++) {
            target = nearest(x, y, chain == 0 ? 500 : 240, mask); if (target == null) break;
            int index = enemyIndex(target); mask |= 1L << index;
            Effect fx = effect(EffectKind.LIGHTNING, x, y, p.slot, .2f);
            if (fx != null) { fx.x2 = target.x; fx.y2 = target.y; }
            x = target.x; y = target.y; hit(target, (12 + p.stormLevel * 5) * equipment.damageMultiplier(p) * equipment.stormMultiplier(p), p.slot);
        }
    }

    private float critical(Player p, float damage) { return damage * (random() < equipment.critChance(p) ? equipment.critDamage(p) : 1); }

    private void updateEnemies() {
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.animTime += STEP; e.hitFlash = Math.max(0, e.hitFlash - STEP);
            e.stunTime = Math.max(0, e.stunTime - STEP); e.attackCooldown = Math.max(0, e.attackCooldown - STEP);
            Player target = nearestPlayer(e.x, e.y); if (target == null) { e.vx = e.vy = 0; continue; }
            if (e.kind == EnemyKind.BOSS) {
                bossPhase = e.health <= e.maxHealth * .5f ? 2 : 1;
                if (e.attackCooldown <= 0) bossAttack(e, target);
            } else if (e.kind == EnemyKind.CULTIST && e.attackCooldown <= 0) {
                e.windup = .7f; e.attackCooldown = 2.6f;
            }
            if (e.windup > 0) {
                e.windup = Math.max(0, e.windup - STEP); e.vx = e.vy = 0;
                if (e.windup <= 0 && e.kind == EnemyKind.CULTIST) fireShot(e.x, e.y, target.x - e.x, target.y - e.y, 180, e.damage);
            } else if (e.stunTime <= 0) moveEnemy(e, target);
            for (Player p : players) if (e.active && p.active && p.alive && distanceSquared(p.x, p.y, e.x, e.y) < square(PLAYER_RADIUS + e.radius))
                hurt(p, e.damage, e);
        }
    }

    private void moveEnemy(Enemy e, Player target) {
        float dx = target.x - e.x, dy = target.y - e.y, distance = length(dx, dy);
        float speed = switch (e.kind) {
            case WRAITH -> 112; case BRUTE -> 37; case CULTIST -> 48; case BOSS -> bossPhase == 2 ? 56 : 44; default -> 57;
        };
        speed *= e.speedMultiplier;
        if (e.elite) speed *= 1.15f;
        float desired = e.kind == EnemyKind.CULTIST ? 300 : e.radius + 18;
        float direction = distance < desired - 30 && e.kind == EnemyKind.CULTIST ? -1 : distance > desired ? 1 : 0;
        e.vx = dx / distance * speed * direction; e.vy = dy / distance * speed * direction;
        e.x = clamp(e.x + e.vx * STEP, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
        e.y = clamp(e.y + e.vy * STEP, -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
    }

    private void bossAttack(Enemy e, Player target) {
        e.windup = 1f;
        e.attackCooldown = bossPhase == 2 ? 2.4f : 3.4f;
        // A marked ground impact plus slow, dodgeable bolts; the boss never teleports on top of players.
        groundWarning(target.x, target.y, bossPhase == 2 ? 150 : 125, 1f, bossPhase == 2 ? 28 : 22);
        float angle = (float) Math.atan2(target.y - e.y, target.x - e.x);
        int count = bossPhase == 2 ? 7 : 5;
        for (int i = 0; i < count; i++) {
            float a = angle + (i - (count - 1) * .5f) * .22f;
            fireShot(e.x, e.y, (float) Math.cos(a), (float) Math.sin(a), bossPhase == 2 ? 165 : 145, 15);
        }
    }

    private void updateBolts() {
        for (Bolt b : bolts) {
            if (!b.active) continue;
            float oldX = b.x, oldY = b.y; b.x += b.vx * STEP; b.y += b.vy * STEP; b.ttl -= STEP;
            for (int i = 0; i < enemies.length; i++) {
                Enemy e = enemies[i]; long bit = 1L << i;
                if (!e.active || (b.hitMask & bit) != 0 || segmentDistanceSquared(oldX, oldY, b.x, b.y, e.x, e.y) > square(e.radius + 5)) continue;
                b.hitMask |= bit; hit(e, b.damage, b.owner);
                if (b.pierce-- <= 0) { b.active = false; break; }
            }
            if (b.ttl <= 0 || Math.abs(b.x) > HALF_WIDTH + 50 || Math.abs(b.y) > HALF_HEIGHT + 50) b.active = false;
        }
    }

    private void fireShot(float x, float y, float dx, float dy, float speed, float damage) {
        for (Shot shot : shots) {
            if (shot.active) continue;
            float distance = length(dx, dy); shot.active = true; shot.x = x; shot.y = y;
            shot.vx = dx / distance * speed; shot.vy = dy / distance * speed;
            shot.ttl = 5; shot.damage = damage; shot.radius = 10; return;
        }
    }

    private void updateShots() {
        for (Shot s : shots) {
            if (!s.active) continue;
            float oldX = s.x, oldY = s.y; s.x += s.vx * STEP; s.y += s.vy * STEP; s.ttl -= STEP;
            for (Player p : players) if (p.active && p.alive && segmentDistanceSquared(oldX, oldY, s.x, s.y, p.x, p.y) <= square(PLAYER_RADIUS + s.radius)) {
                hurt(p, s.damage); s.active = false; break;
            }
            if (s.ttl <= 0 || Math.abs(s.x) > HALF_WIDTH + 50 || Math.abs(s.y) > HALF_HEIGHT + 50) s.active = false;
        }
    }

    private void groundWarning(float x, float y, float radius, float warning, float damage) {
        for (Hazard h : hazards) if (!h.active) {
            h.active = true; h.x = x; h.y = y; h.radius = radius; h.warning = warning; h.ttl = warning + .3f; h.damage = damage; return;
        }
    }

    private void updateHazards() {
        for (Hazard h : hazards) {
            if (!h.active) continue;
            float previous = h.warning; h.warning = Math.max(0, h.warning - STEP); h.ttl -= STEP;
            if (previous > 0 && h.warning <= .00001f)
                for (Player p : players) if (p.active && p.alive && distanceSquared(p.x, p.y, h.x, h.y) <= square(h.radius + PLAYER_RADIUS)) hurt(p, h.damage);
            if (h.ttl <= 0) h.active = false;
        }
    }

    private void hurt(Player p, float damage) { hurt(p, damage, null); }

    private void hurt(Player p, float damage, Enemy attacker) {
        if (p.invulnerable > 0 || !p.alive) return;
        if (equipment.blockChance(p) > 0 && random() < equipment.blockChance(p)) { p.invulnerable = .3f; return; }
        damage *= 1 - equipment.damageReduction(p);
        float actual = Math.min(p.health, damage); p.health = Math.max(0, p.health - damage); p.damageTaken += actual;
        p.invulnerable = .75f; heroHits++;
        effect(EffectKind.HIT, p.x, p.y, p.slot, .13f);
        if (p.health <= 0) { p.alive = false; p.dodgeTime = 0; }
        if (attacker != null && attacker.active && equipment.thorns(p) > 0) hit(attacker, equipment.thorns(p), p.slot, false);
    }

    private void hit(Enemy enemy, float damage, int owner) { hit(enemy, damage, owner, true); }

    private void hit(Enemy enemy, float damage, int owner, boolean drains) {
        if (!enemy.active) return;
        float actual = Math.min(enemy.health, damage); players[owner].damageDealt += actual;
        if (drains) equipment.drain(players[owner], actual);
        enemy.health -= damage; enemy.hitFlash = .12f;
        Effect fx = effect(EffectKind.HIT, enemy.x, enemy.y, owner, .1f); if (fx != null) fx.radius = enemy.radius;
        if (enemy.health > 0) return;
        enemy.active = false; activeEnemies = Math.max(0, activeEnemies - 1); kills++; enemyDeaths++; players[owner].kills++;
        equipment.killHeal(players[owner]);
        fx = effect(EffectKind.DEATH, enemy.x, enemy.y, owner, .35f); if (fx != null) fx.radius = enemy.radius;
        int reward = enemy.kind == EnemyKind.BOSS ? 20 : enemy.elite ? 8 : enemy.kind == EnemyKind.BRUTE ? 3 : enemy.kind == EnemyKind.CULTIST ? 2 : 1;
        // Snapshot party scaling at spawn; late joining cannot alter souls already on the floor.
        int rewardHundredths = reward * enemy.soulPercent + sharedXpRemainder;
        int soulReward = rewardHundredths / 100; sharedXpRemainder = rewardHundredths % 100;
        if (soulReward > 0) drop(enemy.x, enemy.y, false, soulReward);
        if (enemy.elite) { elitesKilled++; equipment.rewardElite(); drop(enemy.x + 14, enemy.y, true, 40); }
        else {
            // Keep the opening forgiving; gear sustain replaces some late-run incidental healing.
            boolean gearedRun = timeline.isFullRun() && elapsed >= 180;
            if (kills % (gearedRun ? 12 : 7) == 0) drop(enemy.x + 14, enemy.y, true, gearedRun ? 16 : 24);
        }
        if (enemy == boss) bossDefeated = true;
    }

    private void drop(float x, float y, boolean healing, int value) {
        for (Pickup p : pickups) if (!p.active) { p.active = true; p.x = x; p.y = y; p.healing = healing; p.value = value; return; }
        for (Pickup p : pickups) if (p.healing == healing) { p.value = (int) Math.min(Integer.MAX_VALUE, (long) p.value + value); return; }
    }

    private void updatePickups() {
        for (Pickup pickup : pickups) {
            if (!pickup.active) continue;
            Player collector = null; float nearest = Float.MAX_VALUE;
            for (Player p : players) {
                if (!p.active || !p.alive || (pickup.healing && p.health >= equipment.maxHealth(p))) continue;
                float distance = distanceSquared(p.x, p.y, pickup.x, pickup.y);
                float attraction = (pickup.healing ? 110 : 200) * equipment.pickupMultiplier(p);
                if (distance <= attraction * attraction && distance < nearest) { nearest = distance; collector = p; }
            }
            if (collector == null) continue;
            if (nearest > 52 * 52) {
                float inverse = 1f / (float) Math.sqrt(nearest), travel = Math.min(380 * STEP, (float) Math.sqrt(nearest));
                pickup.x += (collector.x - pickup.x) * inverse * travel; pickup.y += (collector.y - pickup.y) * inverse * travel;
                continue;
            }
            if (pickup.healing) collector.health = Math.min(equipment.maxHealth(collector), collector.health + pickup.value);
            else { souls += pickup.value; progression.addXp(equipment.collectedXp(collector, pickup.value)); }
            pickup.active = false; pickupsCollected++;
        }
    }

    private void updateRevival() {
        for (Player down : players) {
            if (!down.active || down.alive) continue;
            Player ally = players[1 - down.slot];
            if (!ally.active || !ally.alive || distanceSquared(ally.x, ally.y, down.x, down.y) > 95 * 95) { down.reviveProgress = 0; continue; }
            down.reviveProgress += STEP;
            if (down.reviveProgress >= 2 - .0001f) {
                down.alive = true; down.health = equipment.maxHealth(down) * .45f; down.invulnerable = 1.5f;
                down.reviveProgress = 0; pendingDodge[down.slot] = false; ally.revives++;
            }
        }
    }

    private void updateEncounters() {
        int phaseIndex = wave - 1;
        ArenaTimeline.Phase phase = timeline.phase(phaseIndex);
        if (encounterPhase != phaseIndex) {
            encounterPhase = phaseIndex;
            spawnCountdown = 1.4f; // Give a new checkpoint elite a moment of its own.
        }
        if (!bossSpawned && phase.breathing(elapsed - timeline.phaseStart(phaseIndex))) {
            spawnCountdown = Math.min(spawnCountdown, .35f);
            return; // Existing enemies remain dangerous; only reinforcements pause.
        }
        spawnCountdown -= STEP;
        if (spawnCountdown > 0) return;
        if (bossSpawned) {
            if (activeEnemies < (timeline.isFullRun() ? 12 + 6 * (livingPlayerCount() - 1) : MAX_ENEMIES - 2))
                spawn(phase.pattern().member((int) (random() * phase.pattern().size())), false);
            spawnCountdown = timeline.isFullRun() ? 3.5f : 2.5f;
            return;
        }
        Player anchor = livingAnchor();
        float angle = random() * (float) (Math.PI * 2);
        ArenaTimeline.Pattern pattern = phase.pattern();
        for (int member = 0; member < pattern.size() && activeEnemies < reinforcementLimit(); member++) {
            float offset = (member - (pattern.size() - 1) * .5f) * .3f;
            spawn(pattern.member(member), false, anchor, angle + offset);
        }
        spawnCountdown = phase.encounterInterval()
            * (timeline.isFullRun() && livingPlayerCount() > 1 ? .82f : 1);
    }

    private Enemy spawn(EnemyKind kind, boolean elite) {
        return spawn(kind, elite, livingAnchor(), random() * (float) (Math.PI * 2));
    }

    private Enemy spawn(EnemyKind kind, boolean elite, Player anchor, float angle) {
        if (activeEnemies >= MAX_ENEMIES) return null;
        if (anchor == null) return null;
        for (Enemy e : enemies) {
            if (e.active) continue;
            e.radius = kind == EnemyKind.BOSS ? 54 : kind == EnemyKind.BRUTE ? 31 : kind == EnemyKind.WRAITH ? 17 : 20;
            boolean safe = false;
            for (int attempt = 0; attempt < 12 && !safe; attempt++) {
                float direction = angle + attempt * 2.3999632f;
                e.x = clamp(anchor.x + (float) Math.cos(direction) * 620, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
                e.y = clamp(anchor.y + (float) Math.sin(direction) * 620, -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
                safe = true;
                for (Player p : players) if (p.active && p.alive && distanceSquared(e.x, e.y, p.x, p.y) < 300 * 300)
                    safe = false;
            }
            if (!safe) return null; // Skip a reinforcement rather than materializing on either player.
            e.kind = kind; e.elite = elite;
            e.soulPercent = timeline.isFullRun() && livingPlayerCount() > 1 ? 82 : 100;
            float health = switch (kind) { case WRAITH -> 18; case BRUTE -> 95; case CULTIST -> 35; case BOSS -> 1100; default -> 32; };
            ArenaTimeline.Phase phase = timeline.phase(timeline.phaseIndex(elapsed));
            if (kind == EnemyKind.BOSS) {
                if (timeline.isFullRun()) health = 8200;
                health *= 1 + .6f * (activePlayerCount() - 1);
            } else if (timeline.isFullRun()) {
                health *= phase.healthMultiplier() * (1 + .2f * (livingPlayerCount() - 1));
            } else health *= 1 + Math.min(elapsed / durationSeconds(), 1) * .2f;
            e.maxHealth = e.health = health * (elite ? 4 : 1);
            e.damage = kind == EnemyKind.BOSS ? 20 : kind == EnemyKind.BRUTE ? 18 : kind == EnemyKind.WRAITH ? 8 : 12;
            if (timeline.isFullRun() && kind != EnemyKind.BOSS) e.damage *= phase.damageMultiplier();
            e.speedMultiplier = timeline.isFullRun() && kind != EnemyKind.BOSS
                ? 1 + .2f * Math.min(elapsed / durationSeconds(), 1) : 1;
            e.animTime = random(); e.hitFlash = e.vx = e.vy = e.stunTime = e.windup = 0; e.attackCooldown = kind == EnemyKind.BOSS ? 1.5f : 1;
            e.active = true; activeEnemies++; return e;
        }
        return null;
    }

    /** Developer fixture: start the final encounter immediately without a survival playthrough. */
    public void debugStartBoss() {
        if (bossSpawned || outcome != Outcome.RUNNING || activePlayerCount() == 0) return;
        elapsed = Math.max(elapsed, durationSeconds()); wave = timeline.phaseCount();
        if (activeEnemies >= MAX_ENEMIES) for (Enemy e : enemies) if (e.active) { e.active = false; activeEnemies--; break; }
        boss = spawn(EnemyKind.BOSS, false); bossSpawned = boss != null; bossPhase = 1;
    }

    /** Profile fixture: bounded late-run density, with no survival fast-forward or historical elites. */
    public void debugStartLateEncounter() {
        if (outcome != Outcome.RUNNING || activePlayerCount() == 0) return;
        elapsed = durationSeconds() * .925f; simulationElapsed = elapsed;
        wave = timeline.phaseIndex(elapsed) + 1; encounterPhase = wave - 1;
        nextCheckpoint = wave; spawnCountdown = 1.4f;
        bossSpawned = bossDefeated = false; boss = null; bossPhase = 1;
        for (Enemy enemy : enemies) enemy.active = false;
        for (Shot shot : shots) shot.active = false;
        for (Bolt bolt : bolts) bolt.active = false;
        for (Hazard hazard : hazards) hazard.active = false;
        for (Pickup pickup : pickups) pickup.active = false;
        for (Effect effect : effects) effect.active = false;
        activeEnemies = 0;
        ArenaTimeline.Phase phase = timeline.phase(encounterPhase);
        spawn(phase.eliteKind() == null ? EnemyKind.BRUTE : phase.eliteKind(), true);
        for (int i = 1, limit = reinforcementLimit(); i < limit; i++)
            spawn(phase.pattern().member(i % phase.pattern().size()), false);
    }

    /** Soft pressure cap reserves hard-pool room for checkpoint elites and the boss. */
    public int reinforcementLimit() {
        ArenaTimeline.Phase phase = timeline.phase(timeline.phaseIndex(elapsed));
        return Math.min(MAX_ENEMIES - 2, phase.soloEnemyLimit()
            + (timeline.isFullRun() ? 12 * Math.max(0, livingPlayerCount() - 1) : 0));
    }

    private int livingPlayerCount() {
        int count = 0; for (Player player : players) if (player.active && player.alive) count++; return count;
    }

    private Enemy nearest(float x, float y, float range, long excluded) {
        Enemy result = null; float best = range * range;
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i]; if (!e.active || (excluded & (1L << i)) != 0) continue;
            float distance = distanceSquared(x, y, e.x, e.y);
            if (distance < best) { best = distance; result = e; }
        }
        return result;
    }
    private Player nearestPlayer(float x, float y) {
        Player result = null; float best = Float.MAX_VALUE;
        for (Player p : players) if (p.active && p.alive) {
            float distance = distanceSquared(x, y, p.x, p.y); if (distance < best) { best = distance; result = p; }
        }
        return result;
    }
    private Player livingAnchor() {
        int count = 0; for (Player p : players) if (p.active && p.alive) count++;
        int chosen = (int) (random() * Math.max(1, count));
        for (Player p : players) if (p.active && p.alive && chosen-- == 0) return p;
        return null;
    }
    private int activePlayerCount() { int count = 0; for (Player p : players) if (p.active) count++; return count; }
    private int enemyIndex(Enemy enemy) { for (int i = 0; i < enemies.length; i++) if (enemies[i] == enemy) return i; return 0; }
    private Bolt freeBolt() { for (Bolt b : bolts) if (!b.active) return b; return null; }
    private Effect effect(EffectKind kind, float x, float y, int owner, float duration) {
        for (Effect e : effects) if (!e.active) {
            e.active = true; e.kind = kind; e.x = x; e.y = y; e.x2 = x; e.y2 = y;
            e.angle = e.radius = e.sweep = 0; e.ttl = e.maxTtl = duration; e.owner = owner; return e;
        }
        return null;
    }
    private float random() {
        randomState ^= randomState << 13; randomState ^= randomState >>> 17; randomState ^= randomState << 5;
        return (randomState >>> 8) * (1f / 16777216f);
    }
    private Player player(int slot) { if (slot < 0 || slot >= 2) throw new IllegalArgumentException("Player slot must be 0 or 1"); return players[slot]; }
    private static float square(float n) { return n * n; }
    private static float length(float x, float y) { return (float) Math.sqrt(Math.max(.0001f, x * x + y * y)); }
    private static float distanceSquared(float x, float y, float xx, float yy) { return square(x - xx) + square(y - yy); }
    private static float segmentDistanceSquared(float x, float y, float xx, float yy, float px, float py) {
        float dx = xx - x, dy = yy - y, squared = dx * dx + dy * dy;
        float t = squared > 0 ? clamp(((px - x) * dx + (py - y) * dy) / squared, 0, 1) : 0;
        return distanceSquared(px, py, x + dx * t, y + dy * t);
    }
    private static float clamp(float n, float min, float max) { return Math.max(min, Math.min(max, n)); }

    public static final class Player {
        public final int slot;
        public Hero hero;
        public int kills, projectileCount = 1, pierce, auraLevel, stormLevel, revives;
        public boolean active, alive;
        public float x, y, health, maxHealth, dodgeCooldown, dodgeTime, invulnerable, attackCooldown;
        public float reviveProgress, facingX, facingY, walkTime;
        public float damageMultiplier = 1, attackRateMultiplier = 1, moveMultiplier = 1, dodgeCooldownMultiplier = 1;
        public float cleaveMultiplier = 1, critChance, pickupMultiplier = 1, auraCooldown, stormCooldown, damageDealt, damageTaken;
        private float dodgeX, dodgeY;
        private Player(int slot) { this.slot = slot; hero = slot == 0 ? Hero.WARDEN : Hero.RANGER; }
    }
    public static final class Enemy {
        public boolean active, elite;
        public EnemyKind kind = EnemyKind.GHOUL;
        public int soulPercent = 100;
        public float x, y, health, maxHealth, animTime, hitFlash, vx, vy;
        public float radius = 20, stunTime, attackCooldown, windup, damage = 12, speedMultiplier = 1;
    }
    public static final class Bolt {
        public boolean active;
        public float x, y, vx, vy, ttl, damage = 16;
        public int owner, pierce;
        public long hitMask;
    }
    public static final class Pickup { public boolean active, healing; public float x, y; public int value; }
    public static final class Shot { public boolean active; public float x, y, vx, vy, ttl, damage, radius; }
    public static final class Hazard { public boolean active; public float x, y, radius, warning, ttl, damage; }
    public static final class Effect {
        public boolean active; public EffectKind kind;
        public float x, y, x2, y2, angle, radius, sweep, ttl, maxTtl; public int owner;
    }
}
