package com.couchcrypt.game;

/** A bounded, fixed-step shared world. Presentation and sockets never run inside its tick. */
public final class ArenaWorld {
    public static final float HALF_WIDTH = 1200f, HALF_HEIGHT = 900f, ROUND_SECONDS = 180f;
    public static final int MAX_ENEMIES = 64, MAX_BOLTS = 96, MAX_PICKUPS = 96;
    public static final int MAX_CHESTS = 5;
    public static final float CHEST_PICKUP_RADIUS = 40f, CHEST_LANDING_SECONDS = .35f;
    public static final int MAX_SHOTS = 96, MAX_HAZARDS = 12, MAX_EFFECTS = 96;
    public static final float STEP = 1f / 60f;
    /** Cosmetic sweep lifetime only; damage and attack cadence still commit immediately. */
    public static final float WARDEN_SLASH_SECONDS = .38f;
    private static final float DODGE_DURATION = .22f, DODGE_SPEED_MULTIPLIER = 3.75f, PLAYER_RADIUS = 20f;
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
    public enum EnemyKind { GHOUL, WRAITH, BRUTE, CULTIST, BOSS, SOVEREIGN }
    public enum EnemyAttack { NONE, WRAITH_DASH, BRUTE_SLAM, CULTIST_CAST }
    public static final float WRAITH_WINDUP = .5f, WRAITH_DASH_SECONDS = .25f, WRAITH_ATTACK_SECONDS = 1.3f;
    public static final float WRAITH_ATTACK_RANGE = 240f, WRAITH_DASH_RANGE = 285f, WRAITH_ATTACK_COOLDOWN = 2.6f;
    public static final float BRUTE_WINDUP = .75f, BRUTE_STRIKE_END = .95f, BRUTE_ATTACK_SECONDS = 1.45f;
    public static final float BRUTE_ATTACK_RANGE = 115f, BRUTE_SLAM_RADIUS = 55f, BRUTE_ATTACK_COOLDOWN = 2.8f;
    public static final float CULTIST_WINDUP = .7f, CULTIST_RELEASE_END = .82f, CULTIST_ATTACK_SECONDS = 1.44f;
    public static final float CULTIST_ATTACK_RANGE = 650f, CULTIST_ATTACK_COOLDOWN = 2.6f;
    public enum EffectKind { SLASH, HIT, DEATH, AURA, LIGHTNING, DODGE, COLLECT, BLOCK }
    public enum ImpactKind { SWORD, BOLT, MAGIC }

    /** Opt-in, cumulative run counters. Never consulted by simulation rules. */
    public static final class Diagnostics {
        /** Active, not-yet-hit bolt/enemy pairs submitted to the geometric collision test. */
        public long boltCollisionCandidates;
        public long enemySpawnMisses, boltAcquireMisses, shotAcquireMisses, hazardAcquireMisses, effectAcquireMisses;
        /** Rewards merged into existing pickups because every pickup slot was occupied. */
        public long pickupMerges;

        private void reset() {
            boltCollisionCandidates = enemySpawnMisses = boltAcquireMisses = shotAcquireMisses = 0;
            hazardAcquireMisses = effectAcquireMisses = pickupMerges = 0;
        }
    }

    public Outcome outcome;
    public float elapsed;
    /** Cosmetic time: advances during host-owned choice updates, never moves combat. */
    public float levelUpFxAge = 2;
    public int kills, souls, activeEnemies, wave, elitesKilled, bossPhase, runSeed;
    public boolean bossSpawned, bossDefeated, sovereignSpawned, sovereignDefeated;
    public Enemy boss, sovereign;
    public float sovereignDefeatedAt = -1;
    public int swordSwings, arrowsFired, enemyDeaths, pickupsCollected, heroHits;
    /** Committed combat events, consumed independently by presentation/audio; never drive game rules. */
    public int swordHits, boltHits, criticalHits, dodges, blockedHits, healsCollected, auraPulses, stormCasts;
    public int wraithDashes, bruteSlams, hostileShotsFired;
    /** Fixed steps executed by the most recent update, including choice-only steps. */
    public int lastUpdateSteps;
    public final Player[] players = {new Player(0), new Player(1)};
    public final Enemy[] enemies = new Enemy[MAX_ENEMIES];
    public final Bolt[] bolts = new Bolt[MAX_BOLTS];
    public final Pickup[] pickups = new Pickup[MAX_PICKUPS];
    /** Elite loot never competes with souls/healing for pool space and stays until collected. */
    public final Chest[] chests = new Chest[MAX_CHESTS];
    public final Shot[] shots = new Shot[MAX_SHOTS];
    public final Hazard[] hazards = new Hazard[MAX_HAZARDS];
    public final Effect[] effects = new Effect[MAX_EFFECTS];
    final DamageNumbers damageNumbers = new DamageNumbers();
    public final PartyProgression progression;
    public final RunEquipment equipment;
    public final HealthDropPolicy healthDrops = new HealthDropPolicy();
    public final ArenaTimeline timeline;
    private final float[] inputX = new float[2], inputY = new float[2];
    private final boolean[] pendingDodge = new boolean[2];
    private float accumulator, spawnCountdown;
    private ActorAnimation.Facing debugSizingFacing;
    private double simulationElapsed;
    private int sharedXpRemainder;
    private int randomState, runNumber, nextCheckpoint, encounterPhase, enemySpawnSerial;
    private int presentedChoiceRound, chestsDropped;
    private boolean sovereignSkipped;
    private Diagnostics diagnostics, diagnosticsStorage;

    public ArenaWorld() { this(ArenaTimeline.SHORT); }

    public ArenaWorld(ArenaTimeline timeline) {
        this.timeline = java.util.Objects.requireNonNull(timeline);
        for (int i = 0; i < enemies.length; i++) enemies[i] = new Enemy();
        for (int i = 0; i < bolts.length; i++) bolts[i] = new Bolt();
        for (int i = 0; i < pickups.length; i++) pickups[i] = new Pickup();
        for (int i = 0; i < chests.length; i++) chests[i] = new Chest();
        for (int i = 0; i < shots.length; i++) shots[i] = new Shot();
        for (int i = 0; i < hazards.length; i++) hazards[i] = new Hazard();
        for (int i = 0; i < effects.length; i++) effects[i] = new Effect();
        progression = new PartyProgression(this);
        equipment = new RunEquipment(this);
        reset();
    }

    public void reset() {
        lastUpdateSteps = 0;
        if (diagnosticsStorage != null) diagnosticsStorage.reset();
        outcome = Outcome.RUNNING; elapsed = accumulator = 0; simulationElapsed = 0; sharedXpRemainder = 0;
        kills = souls = activeEnemies = elitesKilled = nextCheckpoint = encounterPhase = enemySpawnSerial = 0;
        wave = bossPhase = 1; bossSpawned = bossDefeated = sovereignSpawned = sovereignDefeated = false; boss = sovereign = null;
        sovereignDefeatedAt = -1;
        levelUpFxAge = 2; presentedChoiceRound = 0; sovereignSkipped = false;
        chestsDropped = 0;
        swordSwings = arrowsFired = enemyDeaths = pickupsCollected = heroHits = 0;
        swordHits = boltHits = criticalHits = dodges = blockedHits = healsCollected = auraPulses = stormCasts = 0;
        wraithDashes = bruteSlams = hostileShotsFired = 0;
        spawnCountdown = .65f;
        runSeed = 0x5a17c9e3 ^ (++runNumber * 0x45d9f3b); randomState = runSeed;
        debugSizingFacing = null;
        progression.reset(); equipment.reset(); healthDrops.reset(); damageNumbers.clear();
        for (Player p : players) {
            p.active = p.alive = false; p.x = p.y = p.walkTime = p.reviveProgress = 0;
            resetBuild(p); p.dodgeCooldown = p.dodgeTime = p.invulnerable = p.attackCooldown = 0;
            p.facingX = 0; p.facingY = -1; p.kills = p.revives = 0;
            p.damageDealt = p.damageTaken = 0; p.dodgeX = 0; p.dodgeY = -1;
            p.healingFromDrops = p.healingFromRegeneration = p.healingFromSustain = 0;
            resetPresentation(p);
            inputX[p.slot] = inputY[p.slot] = 0; pendingDodge[p.slot] = false;
        }
        for (Enemy e : enemies) { e.active = false; e.variant = EnemyVariant.ORIGINAL; resetPresentation(e); }
        for (Bolt b : bolts) b.active = false;
        for (Pickup p : pickups) p.active = false;
        for (Chest chest : chests) { chest.active = false; chest.x = chest.y = chest.pickupDelay = 0; }
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
        resetPresentation(p);
        // Gear first: inherited skill cards must preview the foundation's effective stats.
        equipment.activatePlayer(slot); progression.activatePlayer(slot);
        // A new participant arrives ready to fight with their granted foundation's full health.
        // Reconnecting an already-active participant returns above and never refills health.
        p.health = equipment.maxHealth(p);
    }

    public void deactivatePlayer(int slot) {
        Player p = player(slot); p.active = p.alive = false; resetBuild(p);
        p.kills = p.revives = 0; p.damageDealt = p.damageTaken = 0;
        p.healingFromDrops = p.healingFromRegeneration = p.healingFromSustain = 0;
        resetPresentation(p);
        progression.deactivatePlayer(slot); equipment.deactivatePlayer(slot); inputX[slot] = inputY[slot] = 0; pendingDodge[slot] = false;
    }

    private static void resetPresentation(Player p) {
        p.moving = false; p.walkTime = p.hurtTime = 0; p.attackAge = 1;
        p.attackFacingX = p.facingX; p.attackFacingY = p.facingY;
        p.dodgeTrail.clear();
    }

    private static void resetPresentation(Enemy e) {
        e.moving = false; e.moveTime = e.castDuration = e.windup = 0; e.castAge = e.releaseAge = 1;
        e.facingX = e.castFacingX = 0; e.facingY = e.castFacingY = -1;
        e.hitDirectionX = e.hitDirectionY = 0;
        e.spawnAge = 1;
        e.attack = EnemyAttack.NONE; e.attackAge = 2; e.attackHitMask = 0;
        e.encounterPhase = 1; e.bossAttackIndex = 0; e.bossVolleyPending = e.bossCastEnraged = false;
        e.attackStartX = e.attackStartY = e.attackTargetX = e.attackTargetY = e.attackRadius = e.dashDistance = 0;
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
        lastUpdateSteps = 0;
        if (outcome != Outcome.RUNNING || !Float.isFinite(delta) || delta <= 0) return;
        if (!players[0].active && !players[1].active) return;
        accumulator += Math.min(delta, .1f);
        while (accumulator + .000001f >= STEP && outcome == Outcome.RUNNING) {
            accumulator = Math.max(0, accumulator - STEP); lastUpdateSteps++; tick();
        }
    }

    /** Allocate diagnostics once on first enable; disabling stops counting without discarding run history. */
    public void setDiagnosticsEnabled(boolean enabled) {
        if (enabled && diagnosticsStorage == null) diagnosticsStorage = new Diagnostics();
        diagnostics = enabled ? diagnosticsStorage : null;
    }

    /** Null unless enabled. The returned object is reused and its counters clear with reset(). */
    public Diagnostics diagnostics() { return diagnostics; }

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

    /** Presentation reads one live encounter: the final boss takes priority when both survive. */
    public Enemy activeBoss() {
        if (bossSpawned && !bossDefeated && boss != null && boss.active) return boss;
        if (sovereignSpawned && !sovereignDefeated && sovereign != null && sovereign.active) return sovereign;
        return null;
    }

    public static String bossName(Enemy enemy) {
        return enemy != null && enemy.kind == EnemyKind.SOVEREIGN ? "THE HOLLOW SOVEREIGN" : "THE GRAVE KING";
    }

    public static boolean isBoss(EnemyKind kind) { return kind == EnemyKind.BOSS || kind == EnemyKind.SOVEREIGN; }

    void beginLevelUpFx(int round) {
        if (round == presentedChoiceRound) return;
        presentedChoiceRound = round; levelUpFxAge = 0;
    }

    private void tick() {
        beginLevelUpFx(progression.choiceRound);
        levelUpFxAge = Math.min(2, levelUpFxAge + STEP);
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
        damageNumbers.update(STEP);
        for (Effect e : effects) if (e.active) { e.ttl -= STEP; if (e.ttl <= 0) e.active = false; }
        if (timeline.isFullRun()) healthDrops.update(pickups, STEP, livingPlayerCount());
        for (Player p : players) updatePlayer(p);
        updateEnemies(); updateBolts(); updateShots(); updateHazards(); updatePickups(); updateRevival();
        updateChests();
        progression.update(STEP); equipment.update(STEP);
        boolean living = false; for (Player p : players) living |= p.active && p.alive;
        if (!living) { outcome = Outcome.DEFEAT; return; }
        if (bossDefeated) { outcome = Outcome.VICTORY; return; }
        if (elapsed >= timeline.sovereignSeconds() - .0001f && !sovereignSpawned && !sovereignSkipped) spawnSovereign();
        if (elapsed >= durationSeconds() - .0001f && !bossSpawned) spawnFinalBoss();
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
        p.dodgeTrail.update(STEP);
        p.dodgeCooldown = Math.max(0, p.dodgeCooldown - STEP);
        p.invulnerable = Math.max(0, p.invulnerable - STEP);
        p.attackCooldown = Math.max(0, p.attackCooldown - STEP);
        p.auraCooldown = Math.max(0, p.auraCooldown - STEP); p.stormCooldown = Math.max(0, p.stormCooldown - STEP);
        p.moving = false;
        if (p.active) {
            p.attackAge = Math.min(1, p.attackAge + STEP);
            p.hurtTime = Math.max(0, p.hurtTime - STEP);
        }
        boolean dodge = pendingDodge[p.slot]; pendingDodge[p.slot] = false;
        if (!p.active || !p.alive) { p.dodgeTrail.clear(); return; }
        boolean choosing = equipment.choosing(p.slot);
        float x = choosing ? 0 : inputX[p.slot], y = choosing ? 0 : inputY[p.slot], magnitude = x * x + y * y;
        if (choosing) dodge = false;
        equipment.setMoving(p, magnitude > .0025f || p.dodgeTime > 0);
        float previousHealth = p.health;
        p.health = Math.min(equipment.maxHealth(p), p.health + equipment.regeneration(p) * STEP);
        p.healingFromRegeneration += p.health - previousHealth;
        if (magnitude > .0025f) {
            float inverse = 1f / (float) Math.sqrt(magnitude); p.facingX = x * inverse; p.facingY = y * inverse;
        }
        if (dodge && p.dodgeCooldown <= 0 && p.dodgeTime <= 0) {
            p.dodgeTime = DODGE_DURATION; p.dodgeCooldown = equipment.dodgeCooldown(p);
            p.invulnerable = Math.max(p.invulnerable, DODGE_DURATION); p.dodgeX = p.facingX; p.dodgeY = p.facingY;
            dodges++;
            Effect fx = effect(EffectKind.DODGE, p.x, p.y, p.slot, DODGE_DURATION);
            if (fx != null) { fx.dirX = p.dodgeX; fx.dirY = p.dodgeY; }
        }
        float speed = p.hero.moveSpeed * equipment.moveMultiplier(p);
        float previousX = p.x, previousY = p.y;
        if (p.dodgeTime > 0) {
            float duration = Math.min(STEP, p.dodgeTime);
            float nextX = clamp(p.x + p.dodgeX * speed * DODGE_SPEED_MULTIPLIER * duration, -HALF_WIDTH + 20, HALF_WIDTH - 20);
            float nextY = clamp(p.y + p.dodgeY * speed * DODGE_SPEED_MULTIPLIER * duration, -HALF_HEIGHT + 20, HALF_HEIGHT - 20);
            // Retain real past positions/poses rather than copies attached to the current body.
            if (nextX != p.x || nextY != p.y) p.dodgeTrail.capture(p);
            p.x = nextX; p.y = nextY;
            p.dodgeTime = Math.max(0, p.dodgeTime - STEP); p.walkTime += STEP * 2;
        } else {
            p.x += x * speed * STEP; p.y += y * speed * STEP; if (magnitude > .0025f) p.walkTime += STEP;
        }
        p.x = clamp(p.x, -HALF_WIDTH + 20, HALF_WIDTH - 20); p.y = clamp(p.y, -HALF_HEIGHT + 20, HALF_HEIGHT - 20);
        p.moving = p.x != previousX || p.y != previousY;
        if (p.attackCooldown <= 0) attack(p);
        if (p.auraLevel > 0 && p.auraCooldown <= 0) aura(p);
        if (p.stormLevel > 0 && p.stormCooldown <= 0) storm(p);
    }

    /** Primary targeting envelope, also used by the live ground outline. Target centers must enter this range. */
    public float primaryAttackRange(Player player) {
        return player.hero.attackRange * (player.hero == Hero.WARDEN ? equipment.reachMultiplier(player) : 1f);
    }

    private void attack(Player p) {
        float reach = primaryAttackRange(p);
        Enemy target = nearest(p.x, p.y, reach, 0);
        if (target == null) return;
        float dx = target.x - p.x, dy = target.y - p.y, length = length(dx, dy);
        p.facingX = dx / length; p.facingY = dy / length;
        if (p.hero == Hero.WARDEN) {
            beginAttackPresentation(p);
            float halfArcCos = .5735764f; // cos(55 degrees)
            // A haste build replaces its older cosmetic trail instead of stacking overlapping sweeps.
            for (Effect previous : effects)
                if (previous.active && previous.kind == EffectKind.SLASH && previous.owner == p.slot) previous.active = false;
            Effect fx = effect(EffectKind.SLASH, p.x, p.y, p.slot, WARDEN_SLASH_SECONDS);
            if (fx != null) { fx.angle = (float) Math.atan2(dy, dx); fx.radius = reach; fx.sweep = 1.9198622f; }
            swordSwings++;
            for (Enemy e : enemies) {
                if (!e.active) continue;
                float ex = e.x - p.x, ey = e.y - p.y, distance = length(ex, ey);
                if (distance > reach + e.radius || (ex * p.facingX + ey * p.facingY) / distance < halfArcCos) continue;
                boolean critical = rollCritical(p);
                hit(e, p.hero.baseDamage * equipment.damageMultiplier(p) * criticalMultiplier(p, critical),
                    p.slot, true, critical, ImpactKind.SWORD, ex, ey);
                swordHits++;
                if (e.active) {
                    e.stunTime = Math.max(e.stunTime, isBoss(e.kind) ? .02f : .16f);
                    float knockback = isBoss(e.kind) ? 3 : e.kind == EnemyKind.BRUTE ? 12 : 25;
                    knockbackEnemy(e, ex / distance * knockback, ey / distance * knockback);
                }
            }
            p.attackCooldown = p.hero.attackInterval / equipment.attackRate(p);
        } else {
            int count = equipment.projectileCount(p);
            float angle = (float) Math.atan2(dy, dx);
            for (int i = 0; i < count; i++) {
                Bolt b = freeBolt(); if (b == null) break;
                if (i == 0) beginAttackPresentation(p);
                float direction = angle + (i - (count - 1) * .5f) * .13f;
                b.active = true; b.x = p.x; b.y = p.y; b.age = 0;
                b.launchFacingX = p.attackFacingX; b.launchFacingY = p.attackFacingY;
                b.vx = (float) Math.cos(direction) * 760; b.vy = (float) Math.sin(direction) * 760;
                b.critical = rollCritical(p);
                b.ttl = 1.2f; b.owner = p.slot;
                b.damage = p.hero.baseDamage * equipment.damageMultiplier(p) * criticalMultiplier(p, b.critical);
                b.pierce = equipment.pierce(p); b.hitMask = 0; arrowsFired++;
            }
            p.attackCooldown = p.hero.attackInterval / equipment.attackRate(p);
        }
    }

    private static void beginAttackPresentation(Player player) {
        player.attackAge = 0;
        player.attackFacingX = player.facingX; player.attackFacingY = player.facingY;
    }

    private void aura(Player p) {
        p.auraCooldown = .7f; float radius = 100 + 20 * p.auraLevel;
        auraPulses++;
        Effect fx = effect(EffectKind.AURA, p.x, p.y, p.slot, .24f); if (fx != null) fx.radius = radius;
        for (Enemy e : enemies) if (e.active && distanceSquared(p.x, p.y, e.x, e.y) <= radius * radius)
            hit(e, (4 + p.auraLevel * 3) * equipment.damageMultiplier(p) * equipment.auraMultiplier(p), p.slot);
    }

    private void storm(Player p) {
        Enemy target = nearest(p.x, p.y, 500, 0); if (target == null) return;
        p.stormCooldown = 2;
        stormCasts++;
        float x = p.x, y = p.y; long mask = 0;
        for (int chain = 0; chain < p.stormLevel + 1; chain++) {
            target = nearest(x, y, chain == 0 ? 500 : 240, mask); if (target == null) break;
            int index = enemyIndex(target); mask |= 1L << index;
            Effect fx = effect(EffectKind.LIGHTNING, x, y, p.slot, .2f);
            if (fx != null) { fx.x2 = target.x; fx.y2 = target.y; }
            x = target.x; y = target.y; hit(target, (12 + p.stormLevel * 5) * equipment.damageMultiplier(p) * equipment.stormMultiplier(p), p.slot);
        }
    }

    // One roll in the original location/order, shared by damage and its visual/audio identity.
    private boolean rollCritical(Player p) { return random() < equipment.critChance(p); }
    private float criticalMultiplier(Player p, boolean critical) { return critical ? equipment.critDamage(p) : 1; }

    private void updateEnemies() {
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.moving = false; e.castAge = Math.min(2, e.castAge + STEP);
            e.spawnAge = Math.min(1, e.spawnAge + STEP);
            e.releaseAge = Math.min(1, e.releaseAge + STEP);
            float previousX = e.x, previousY = e.y;
            e.animTime += STEP; e.hitFlash = Math.max(0, e.hitFlash - STEP);
            e.stunTime = Math.max(0, e.stunTime - STEP); e.attackCooldown = Math.max(0, e.attackCooldown - STEP);
            Player target = nearestPlayer(e.x, e.y); if (target == null) { e.vx = e.vy = 0; continue; }
            if (e.attack != EnemyAttack.NONE) {
                // The target may move or change slots; an authored action never turns to chase it.
                e.facingX = e.castFacingX; e.facingY = e.castFacingY;
                updateEnemyAttack(e);
            } else {
                if (debugSizingFacing == null || e.stunTime <= 0) faceTarget(e, target);
                if (isBoss(e.kind)) {
                    e.encounterPhase = e.health <= e.maxHealth * .5f ? 2 : 1;
                    if (e.kind == EnemyKind.BOSS) bossPhase = e.encounterPhase;
                    if (e.windup > 0) {
                        // A cast commits its aim; moving players cannot redirect the warned volley.
                        e.facingX = e.castFacingX; e.facingY = e.castFacingY;
                        e.windup = Math.max(0, e.windup - STEP); e.vx = e.vy = 0;
                        if (e.windup <= .000001f) {
                            e.windup = 0;
                            if (e.kind == EnemyKind.BOSS || e.bossVolleyPending) e.releaseAge = 0;
                            if (e.bossVolleyPending) { fireBossVolley(e); e.bossVolleyPending = false; }
                        }
                    } else if (e.attackCooldown <= 0) bossAttack(e, target);
                    else if (e.stunTime <= 0) moveEnemy(e, target);
                } else if (e.stunTime <= 0 && e.attackCooldown <= 0 && attackInRange(e, target)) {
                    beginEnemyAttack(e, target);
                } else if (e.stunTime <= 0) moveEnemy(e, target);
            }
            e.moving = e.x != previousX || e.y != previousY;
            if (e.moving) e.moveTime += STEP;
            // Wraith/Brute damage is exclusively the telegraphed strike, including during recovery.
            if (e.kind == EnemyKind.GHOUL || isBoss(e.kind)
                || e.kind == EnemyKind.CULTIST && e.attack == EnemyAttack.NONE)
                for (Player p : players) if (e.active && p.active && p.alive
                    && distanceSquared(p.x, p.y, e.x, e.y) < square(PLAYER_RADIUS + e.radius))
                    hurt(p, e.damage, e);
        }
    }

    private static boolean attackInRange(Enemy e, Player target) {
        float range = switch (e.kind) {
            case WRAITH -> WRAITH_ATTACK_RANGE;
            case BRUTE -> BRUTE_ATTACK_RANGE;
            case CULTIST -> CULTIST_ATTACK_RANGE;
            default -> 0;
        };
        return range > 0 && distanceSquared(e.x, e.y, target.x, target.y) <= square(range);
    }

    private static void beginEnemyAttack(Enemy e, Player target) {
        e.attack = switch (e.kind) {
            case WRAITH -> EnemyAttack.WRAITH_DASH;
            case BRUTE -> EnemyAttack.BRUTE_SLAM;
            case CULTIST -> EnemyAttack.CULTIST_CAST;
            default -> EnemyAttack.NONE;
        };
        e.attackAge = 0; e.attackHitMask = 0;
        e.attackStartX = e.x; e.attackStartY = e.y; e.vx = e.vy = 0;
        e.attackTargetX = target.x; e.attackTargetY = target.y; e.attackRadius = 0;
        float windup = switch (e.attack) {
            case WRAITH_DASH -> WRAITH_WINDUP;
            case BRUTE_SLAM -> BRUTE_WINDUP;
            default -> CULTIST_WINDUP;
        };
        e.windup = windup; beginCastPresentation(e, windup);
        switch (e.attack) {
            case WRAITH_DASH -> {
                e.attackCooldown = WRAITH_ATTACK_COOLDOWN;
                e.dashDistance = Math.min(WRAITH_DASH_RANGE,
                    Math.max(160, length(target.x - e.x, target.y - e.y) + 55));
                e.attackTargetX = clamp(e.x + e.castFacingX * e.dashDistance,
                    -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
                e.attackTargetY = clamp(e.y + e.castFacingY * e.dashDistance,
                    -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
            }
            case BRUTE_SLAM -> {
                e.attackCooldown = BRUTE_ATTACK_COOLDOWN; e.attackRadius = BRUTE_SLAM_RADIUS;
                ActorAnimation.Facing facing = ActorAnimation.facing(e.castFacingX, e.castFacingY, ActorAnimation.Facing.SE);
                e.attackTargetX = e.x + EnemyAttackGeometry.bruteSlamX(facing, e.variant, e.elite);
                e.attackTargetY = e.y + EnemyAttackGeometry.bruteSlamY(facing, e.variant, e.elite);
            }
            case CULTIST_CAST -> e.attackCooldown = CULTIST_ATTACK_COOLDOWN;
            default -> { }
        }
    }

    private void updateEnemyAttack(Enemy e) {
        float previousAge = e.attackAge;
        e.attackAge += STEP; e.vx = e.vy = 0;
        // A boundary may accumulate a fraction below its authored value. Snap once so
        // damage, body frames and renderer cue colours switch in the same fixed tick.
        float windup = e.attack == EnemyAttack.WRAITH_DASH ? WRAITH_WINDUP
            : e.attack == EnemyAttack.BRUTE_SLAM ? BRUTE_WINDUP : CULTIST_WINDUP;
        if (Math.abs(e.attackAge - windup) < .000001f) e.attackAge = windup;
        if (e.attack == EnemyAttack.WRAITH_DASH
            && Math.abs(e.attackAge - (WRAITH_WINDUP + WRAITH_DASH_SECONDS)) < .000001f)
            e.attackAge = WRAITH_WINDUP + WRAITH_DASH_SECONDS;
        float duration;
        switch (e.attack) {
            case WRAITH_DASH -> {
                duration = WRAITH_ATTACK_SECONDS;
                e.windup = remainingWindup(WRAITH_WINDUP, e.attackAge);
                float dashTime = Math.max(0, Math.min(e.attackAge, WRAITH_WINDUP + WRAITH_DASH_SECONDS)
                    - Math.max(previousAge, WRAITH_WINDUP));
                if (dashTime > 0) {
                    if (previousAge <= WRAITH_WINDUP) wraithDashes++;
                    float oldX = e.x, oldY = e.y;
                    float speed = e.dashDistance / WRAITH_DASH_SECONDS;
                    e.x = clamp(e.x + e.castFacingX * speed * dashTime, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
                    e.y = clamp(e.y + e.castFacingY * speed * dashTime, -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
                    e.vx = (e.x - oldX) / STEP; e.vy = (e.y - oldY) / STEP;
                    for (Player p : players) {
                        int bit = 1 << p.slot;
                        if ((e.attackHitMask & bit) != 0 || !p.active || !p.alive
                            || segmentDistanceSquared(oldX, oldY, e.x, e.y, p.x, p.y) > square(e.radius + PLAYER_RADIUS)) continue;
                        // An invulnerable Dodge consumes this crossing too; no delayed contact after it ends.
                        e.attackHitMask |= bit; hurt(p, e.damage, e, e.castFacingX, e.castFacingY);
                        if (!e.active) break; // Thorns may kill the striking enemy.
                    }
                }
            }
            case BRUTE_SLAM -> {
                duration = BRUTE_ATTACK_SECONDS;
                e.windup = remainingWindup(BRUTE_WINDUP, e.attackAge);
                if (previousAge + .000001f < BRUTE_WINDUP && e.attackAge + .000001f >= BRUTE_WINDUP) {
                    e.releaseAge = 0; bruteSlams++;
                    for (Player p : players) if (p.active && p.alive
                        && distanceSquared(p.x, p.y, e.attackTargetX, e.attackTargetY) <= square(e.attackRadius + PLAYER_RADIUS)) {
                        e.attackHitMask |= 1 << p.slot;
                        hurt(p, e.damage, e, p.x - e.attackTargetX, p.y - e.attackTargetY);
                        if (!e.active) break;
                    }
                }
            }
            case CULTIST_CAST -> {
                duration = CULTIST_ATTACK_SECONDS;
                e.windup = remainingWindup(CULTIST_WINDUP, e.attackAge);
                if (previousAge + .000001f < CULTIST_WINDUP && e.attackAge + .000001f >= CULTIST_WINDUP) {
                    e.releaseAge = 0;
                    float speed = timeline.isFullRun() ? 210 + 40 * Math.min(elapsed / durationSeconds(), 1) : 180;
                    fireShot(e, e.castFacingX, e.castFacingY, speed, e.damage);
                }
            }
            default -> { return; }
        }
        if (e.attackAge + .000001f >= duration) {
            e.attack = EnemyAttack.NONE; e.attackAge = duration; e.windup = 0;
        }
    }

    private static void knockbackEnemy(Enemy e, float dx, float dy) {
        float previousX = e.x, previousY = e.y;
        e.x = clamp(e.x + dx, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
        e.y = clamp(e.y + dy, -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
        boolean warningMoves = e.attack == EnemyAttack.WRAITH_DASH && e.attackAge < WRAITH_WINDUP + WRAITH_DASH_SECONDS
            || e.attack == EnemyAttack.BRUTE_SLAM && e.attackAge < BRUTE_WINDUP
            || e.attack == EnemyAttack.CULTIST_CAST && e.attackAge < CULTIST_WINDUP;
        if (warningMoves) {
            // Physical displacement moves the warned geometry with its origin, never its aim.
            // Already committed slam impacts and released shots retain their world positions.
            float actualX = e.x - previousX, actualY = e.y - previousY;
            e.attackStartX += actualX; e.attackStartY += actualY;
            e.attackTargetX += actualX; e.attackTargetY += actualY;
        }
    }

    private static float remainingWindup(float duration, float age) {
        float remaining = duration - age;
        return remaining > .000001f ? remaining : 0;
    }

    private static void faceTarget(Enemy e, Player target) {
        float dx = target.x - e.x, dy = target.y - e.y;
        if (dx * dx + dy * dy <= .0001f) return;
        float distance = length(dx, dy); e.facingX = dx / distance; e.facingY = dy / distance;
    }

    private static void beginCastPresentation(Enemy e, float duration) {
        e.castAge = 0; e.castDuration = duration;
        e.castFacingX = e.facingX; e.castFacingY = e.facingY;
    }

    private void moveEnemy(Enemy e, Player target) {
        float lead = timeline.isFullRun() && e.kind == EnemyKind.WRAITH && target.moving ? 120 : 0;
        float dx = target.x + target.facingX * lead - e.x,
            dy = target.y + target.facingY * lead - e.y, distance = length(dx, dy);
        if (distance < .001f) { e.vx = e.vy = 0; return; }
        float speed = switch (e.kind) {
            case WRAITH -> timeline.isFullRun() ? 120 : 112;
            case BRUTE -> timeline.isFullRun() ? 46 : 37;
            case CULTIST -> timeline.isFullRun() ? 60 : 48;
            case SOVEREIGN -> timeline.isFullRun() ? (e.encounterPhase == 2 ? 80 : 65) : (e.encounterPhase == 2 ? 56 : 44);
            case BOSS -> e.encounterPhase == 2 ? 56 : 44;
            default -> timeline.isFullRun() ? 65 : 57;
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
        boolean enraged = e.encounterPhase == 2;
        boolean harderSovereign = timeline.isFullRun() && e.kind == EnemyKind.SOVEREIGN;
        e.bossCastEnraged = enraged;
        e.attackCooldown = harderSovereign ? (enraged ? 2.1f : 2.8f) : (enraged ? 2.4f : 3.4f);
        e.vx = e.vy = 0;
        if (e.kind == EnemyKind.SOVEREIGN) {
            // Both threats commit the same aim, with a visible full-run cast before release.
            e.windup = 1f; beginCastPresentation(e, 1f); e.releaseAge = harderSovereign ? 1 : 0;
            groundWarning(target.x, target.y, enraged ? 150 : 125, 1f, enraged ? 28 : 22);
            if (harderSovereign) e.bossVolleyPending = true;
            else fireBossVolley(e);
        } else {
            // The Grave King alternates clear attacks rather than layering both at once.
            boolean volley = (e.bossAttackIndex++ & 1) != 0;
            e.windup = volley ? .7f : 1f;
            beginCastPresentation(e, e.windup);
            e.releaseAge = 1;
            e.bossVolleyPending = volley;
            if (!volley) groundWarning(target.x, target.y, enraged ? 150 : 125, 1f, enraged ? 28 : 22);
        }
    }

    private void fireBossVolley(Enemy e) {
        boolean enraged = e.bossCastEnraged;
        boolean harderSovereign = timeline.isFullRun() && e.kind == EnemyKind.SOVEREIGN;
        float angle = (float) Math.atan2(e.castFacingY, e.castFacingX);
        int count = enraged ? 7 : 5;
        for (int i = 0; i < count; i++) {
            float a = angle + (i - (count - 1) * .5f) * .22f;
            fireShot(e, (float) Math.cos(a), (float) Math.sin(a),
                harderSovereign ? (enraged ? 250 : 220) : (enraged ? 165 : 145),
                harderSovereign ? (enraged ? 22 : 18) : 15);
        }
    }

    private void updateBolts() {
        for (Bolt b : bolts) {
            if (!b.active) continue;
            float oldX = b.x, oldY = b.y; b.age += STEP; b.x += b.vx * STEP; b.y += b.vy * STEP; b.ttl -= STEP;
            for (int i = 0; i < enemies.length; i++) {
                Enemy e = enemies[i]; long bit = 1L << i;
                if (!e.active || (b.hitMask & bit) != 0) continue;
                if (diagnostics != null) diagnostics.boltCollisionCandidates++;
                if (segmentDistanceSquared(oldX, oldY, b.x, b.y, e.x, e.y) > square(e.radius + 5)) continue;
                b.hitMask |= bit;
                Effect impact = hit(e, b.damage, b.owner, true, b.critical, ImpactKind.BOLT, b.vx, b.vy);
                if (impact != null) {
                    impact.x = b.x; impact.y = b.y;
                    impact.projectileFacingX = b.launchFacingX; impact.projectileFacingY = b.launchFacingY;
                }
                boltHits++;
                if (b.pierce-- <= 0) { b.active = false; break; }
            }
            if (b.ttl <= 0 || Math.abs(b.x) > HALF_WIDTH + 50 || Math.abs(b.y) > HALF_HEIGHT + 50) b.active = false;
        }
    }

    private void fireShot(Enemy caster, float dx, float dy, float speed, float damage) {
        for (Shot shot : shots) {
            if (shot.active) continue;
            float distance = length(dx, dy); shot.active = true; shot.x = caster.x; shot.y = caster.y; shot.age = 0;
            // Value snapshots survive caster death and enemy-pool reuse; only drawing reads them.
            shot.casterKind = caster.kind; shot.casterElite = caster.elite;
            shot.casterVariant = caster.variant;
            shot.casterRenderScale = EnemySizing.renderScale(caster.kind, caster.variant, caster.elite);
            shot.launchFacingX = caster.castFacingX; shot.launchFacingY = caster.castFacingY;
            shot.vx = dx / distance * speed; shot.vy = dy / distance * speed;
            shot.ttl = 5; shot.damage = damage; shot.radius = 10; hostileShotsFired++; return;
        }
        if (diagnostics != null) diagnostics.shotAcquireMisses++;
    }

    private void updateShots() {
        for (Shot s : shots) {
            if (!s.active) continue;
            float oldX = s.x, oldY = s.y; s.age += STEP; s.x += s.vx * STEP; s.y += s.vy * STEP; s.ttl -= STEP;
            for (Player p : players) if (p.active && p.alive && segmentDistanceSquared(oldX, oldY, s.x, s.y, p.x, p.y) <= square(PLAYER_RADIUS + s.radius)) {
                hurt(p, s.damage, null, s.vx, s.vy); s.active = false; break;
            }
            if (s.ttl <= 0 || Math.abs(s.x) > HALF_WIDTH + 50 || Math.abs(s.y) > HALF_HEIGHT + 50) s.active = false;
        }
    }

    private void groundWarning(float x, float y, float radius, float warning, float damage) {
        for (Hazard h : hazards) if (!h.active) {
            h.active = true; h.x = x; h.y = y; h.radius = radius; h.warning = warning; h.ttl = warning + .3f; h.damage = damage; return;
        }
        if (diagnostics != null) diagnostics.hazardAcquireMisses++;
    }

    private void updateHazards() {
        for (Hazard h : hazards) {
            if (!h.active) continue;
            float previous = h.warning; h.warning = Math.max(0, h.warning - STEP); h.ttl -= STEP;
            if (previous > 0 && h.warning <= .00001f)
                for (Player p : players) if (p.active && p.alive && distanceSquared(p.x, p.y, h.x, h.y) <= square(h.radius + PLAYER_RADIUS))
                    hurt(p, h.damage, null, p.x - h.x, p.y - h.y);
            if (h.ttl <= 0) h.active = false;
        }
    }

    private void hurt(Player p, float damage) { hurt(p, damage, null); }

    private void hurt(Player p, float damage, Enemy attacker) {
        hurt(p, damage, attacker, attacker == null ? 0 : p.x - attacker.x, attacker == null ? 1 : p.y - attacker.y);
    }

    private void hurt(Player p, float damage, Enemy attacker, float directionX, float directionY) {
        if (p.invulnerable > 0 || !p.alive) return;
        if (equipment.blockChance(p) > 0 && random() < equipment.blockChance(p)) {
            p.invulnerable = .3f; blockedHits++;
            Effect block = effect(EffectKind.BLOCK, p.x, p.y, p.slot, .18f);
            if (block != null) { block.heroHit = true; direction(block, directionX, directionY); }
            return;
        }
        damage *= 1 - equipment.damageReduction(p);
        float actual = Math.min(p.health, damage); p.health = Math.max(0, p.health - damage); p.damageTaken += actual;
        if (actual > 0) p.hurtTime = .13f;
        p.invulnerable = .75f; heroHits++;
        Effect fx = effect(EffectKind.HIT, p.x, p.y, p.slot, .13f);
        if (fx != null) { fx.heroHit = true; fx.amount = actual; direction(fx, directionX, directionY); }
        if (p.health <= 0) { p.alive = false; p.dodgeTime = 0; p.moving = false; }
        if (attacker != null && attacker.active && equipment.thorns(p) > 0) hit(attacker, equipment.thorns(p), p.slot, false);
    }

    private void hit(Enemy enemy, float damage, int owner) { hit(enemy, damage, owner, true); }

    private void hit(Enemy enemy, float damage, int owner, boolean drains) {
        hit(enemy, damage, owner, drains, false, ImpactKind.MAGIC, enemy.x - players[owner].x, enemy.y - players[owner].y);
    }

    private Effect hit(Enemy enemy, float damage, int owner, boolean drains, boolean critical, ImpactKind kind,
                     float directionX, float directionY) {
        if (!enemy.active) return null;
        float actual = Math.min(enemy.health, damage); players[owner].damageDealt += actual;
        damageNumbers.record(enemy, actual, critical);
        if (drains) {
            float before = players[owner].health;
            equipment.drain(players[owner], actual);
            players[owner].healingFromSustain += players[owner].health - before;
        }
        enemy.health -= damage; enemy.hitFlash = .12f;
        float distance = length(directionX, directionY);
        enemy.hitDirectionX = directionX / distance; enemy.hitDirectionY = directionY / distance;
        if (critical) criticalHits++;
        Effect fx = effect(EffectKind.HIT, enemy.x, enemy.y, owner,
            kind == ImpactKind.MAGIC ? .12f : critical ? .28f : .24f);
        impact(fx, enemy, actual, critical);
        if (fx != null) fx.impactKind = kind;
        Effect contact = fx;
        if (enemy.health > 0) return contact;
        enemy.active = false; enemy.moving = false; activeEnemies = Math.max(0, activeEnemies - 1); kills++; enemyDeaths++; players[owner].kills++;
        float beforeKillHeal = players[owner].health;
        equipment.killHeal(players[owner]);
        players[owner].healingFromSustain += players[owner].health - beforeKillHeal;
        fx = effect(EffectKind.DEATH, enemy.x, enemy.y, owner, .35f);
        impact(fx, enemy, actual, critical);
        if (fx != null) fx.impactKind = kind;
        int reward = enemy.kind == EnemyKind.BOSS ? 20 : enemy.kind == EnemyKind.SOVEREIGN ? 8 : enemy.elite ? 8 : enemy.kind == EnemyKind.BRUTE ? 3 : enemy.kind == EnemyKind.CULTIST ? 2 : 1;
        // Snapshot party scaling at spawn; late joining cannot alter souls already on the floor.
        int rewardHundredths = reward * enemy.soulPercent + sharedXpRemainder;
        int soulReward = rewardHundredths / 100; sharedXpRemainder = rewardHundredths % 100;
        if (soulReward > 0) drop(enemy.x, enemy.y, false, soulReward);
        if (enemy.elite) { elitesKilled++; dropChest(enemy.x, enemy.y); }
        if (timeline.isFullRun()) {
            if (!isBoss(enemy.kind)) healthDrops.defeated(pickups, enemy.elite, elapsed,
                livingPlayerCount(), enemy.x + 14, enemy.y);
        } else if (enemy.elite || kills % 7 == 0)
            drop(enemy.x + 14, enemy.y, true, enemy.elite ? 40 : 24);
        if (enemy == boss && enemy.kind == EnemyKind.BOSS) bossDefeated = true;
        if (enemy == sovereign && enemy.kind == EnemyKind.SOVEREIGN) {
            sovereignDefeated = true; sovereignDefeatedAt = elapsed;
        }
        return contact;
    }

    private static void direction(Effect fx, float x, float y) {
        float distance = length(x, y); fx.dirX = x / distance; fx.dirY = y / distance;
    }

    private static void impact(Effect fx, Enemy enemy, float actual, boolean critical) {
        if (fx == null) return;
        fx.radius = enemy.radius; fx.amount = actual; fx.critical = critical;
        fx.enemyKind = enemy.kind; fx.elite = enemy.elite; fx.enemyVariant = enemy.variant;
        fx.dirX = enemy.hitDirectionX; fx.dirY = enemy.hitDirectionY;
    }

    private void drop(float x, float y, boolean healing, int value) {
        for (Pickup p : pickups) if (!p.active) { setPickup(p, x, y, healing, value); return; }
        if (!healing && timeline.isFullRun()) {
            Pickup oldHeal = HealthDropPolicy.oldestHealing(pickups);
            if (oldHeal != null) { setPickup(oldHeal, x, y, false, value); return; }
        }
        for (Pickup p : pickups) if (p.healing == healing) {
            p.value = (int) Math.min(Integer.MAX_VALUE, (long) p.value + value);
            if (diagnostics != null) diagnostics.pickupMerges++;
            return;
        }
    }

    private static void setPickup(Pickup p, float x, float y, boolean healing, int value) {
        p.active = true; p.x = x; p.y = y; p.healing = healing; p.value = value;
        p.ttl = p.maxTtl = 0; p.eliteHealing = false;
    }

    private void dropChest(float x, float y) {
        if (chestsDropped >= MAX_CHESTS) return;
        // Five elite opportunities per run: retain a distinct slot even after collection.
        Chest chest = chests[chestsDropped++];
        chest.active = true; chest.x = x; chest.y = y; chest.pickupDelay = CHEST_LANDING_SECONDS;
    }

    private void updateChests() {
        if (levelUpPending()) return;
        for (Chest chest : chests) {
            if (!chest.active) continue;
            // Let the death/drop read before a player already standing at its feet collects it.
            if (chest.pickupDelay > 0) {
                chest.pickupDelay = Math.max(0, chest.pickupDelay - STEP);
                continue;
            }
            for (Player player : players) {
                if (!player.active || !player.alive
                    || distanceSquared(player.x, player.y, chest.x, chest.y) > CHEST_PICKUP_RADIUS * CHEST_PICKUP_RADIUS)
                    continue;
                chest.active = false;
                equipment.rewardElite();
                // One shared round at a time, including when several chests overlap.
                return;
            }
        }
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
            if (pickup.healing) {
                float before = collector.health;
                collector.health = Math.min(equipment.maxHealth(collector), collector.health + pickup.value);
                collector.healingFromDrops += collector.health - before;
                healsCollected++;
            }
            else { souls += pickup.value; progression.addXp(equipment.collectedXp(collector, pickup.value)); }
            Effect fx = effect(EffectKind.COLLECT, collector.x, collector.y, collector.slot, .24f);
            if (fx != null) { fx.healing = pickup.healing; fx.amount = pickup.value; }
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
            Player packAnchor = anchor;
            if (timeline.isFullRun() && phaseIndex > 0) {
                // Split an authored pack across approach directions instead of a single trailing clump.
                offset = (member & 1) == 0 ? member * .12f : 2.4f + member * .12f;
                if (pattern.member(member) == EnemyKind.WRAITH) offset += (member & 1) == 0 ? -.9f : .9f;
                if (livingPlayerCount() > 1 && (member & 1) != 0) packAnchor = players[1 - anchor.slot];
            }
            spawn(pattern.member(member), false, packAnchor, angle + offset);
        }
        spawnCountdown = phase.encounterInterval()
            * (timeline.isFullRun() && livingPlayerCount() > 1 ? .82f : 1);
    }

    private Enemy spawn(EnemyKind kind, boolean elite) {
        return spawn(kind, elite, livingAnchor(), random() * (float) (Math.PI * 2));
    }

    private Enemy spawn(EnemyKind kind, boolean elite, Player anchor, float angle) {
        if (activeEnemies >= MAX_ENEMIES) {
            if (diagnostics != null) diagnostics.enemySpawnMisses++;
            return null;
        }
        if (anchor == null) return null;
        for (Enemy e : enemies) {
            if (e.active) continue;
            EnemyVariant variant = EnemyVariant.forSpawn(kind, runSeed, enemySpawnSerial);
            e.radius = EnemySizing.groundRadius(kind, variant, elite);
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
            e.variant = variant; enemySpawnSerial++;
            e.soulPercent = timeline.isFullRun() && livingPlayerCount() > 1 ? 82 : 100;
            float health = switch (kind) { case WRAITH -> 18; case BRUTE -> 95; case CULTIST -> 35; case BOSS -> 1100; case SOVEREIGN -> 450; default -> 32; };
            if (isBoss(kind)) {
                if (timeline.isFullRun()) health = kind == EnemyKind.BOSS ? 8200 : 5400;
                health *= 1 + .6f * (activePlayerCount() - 1);
            } else if (timeline.isFullRun()) {
                health *= timeline.healthMultiplier(elapsed) * (1 + .6f * (livingPlayerCount() - 1));
            } else health *= 1 + Math.min(elapsed / durationSeconds(), 1) * .2f;
            // Spawn from base values every time, so reused slots cannot compound an old variant's stats.
            e.maxHealth = e.health = health * (elite ? 4 : 1) * e.variant.healthMultiplier;
            e.damage = isBoss(kind) ? 20 : kind == EnemyKind.BRUTE ? 18 : kind == EnemyKind.WRAITH ? 8 : 12;
            if (timeline.isFullRun() && !isBoss(kind)) e.damage *= timeline.damageMultiplier(elapsed);
            e.damage *= e.variant.damageMultiplier;
            e.speedMultiplier = (timeline.isFullRun() && !isBoss(kind)
                ? 1 + .35f * Math.min(elapsed / durationSeconds(), 1) : 1) * e.variant.speedMultiplier;
            e.animTime = random(); e.hitFlash = e.vx = e.vy = e.stunTime = e.windup = 0; e.attackCooldown = isBoss(kind) ? 1.5f : 1;
            resetPresentation(e); faceTarget(e, anchor);
            e.spawnAge = 0;
            e.active = true; activeEnemies++; return e;
        }
        if (diagnostics != null) diagnostics.enemySpawnMisses++;
        return null;
    }

    /** Profile-only scenery inspection: preserve the run/build while giving the camera a quiet view. */
    public void debugSceneryView(float x, float y) {
        if (outcome != Outcome.RUNNING || choicePending()) return;
        debugSizingFacing = null;
        for (Enemy enemy : enemies) enemy.active = false;
        for (Bolt bolt : bolts) bolt.active = false;
        for (Shot shot : shots) shot.active = false;
        for (Hazard hazard : hazards) hazard.active = false;
        for (Effect effect : effects) effect.active = false;
        for (Pickup pickup : pickups) pickup.active = false;
        activeEnemies = 0; spawnCountdown = 12f;
        for (Player player : players) if (player.active) {
            // Separate split views enough to inspect independent scenery reveal.
            player.x = clamp(x + player.slot * 600f, -HALF_WIDTH + PLAYER_RADIUS, HALF_WIDTH - PLAYER_RADIUS);
            player.y = clamp(y, -HALF_HEIGHT + PLAYER_RADIUS, HALF_HEIGHT - PLAYER_RADIUS);
            player.invulnerable = 0f; // The cleared, quiet scene needs no blinking inspection shield.
        }
    }

    /** Profile-only real elite death/drop for a brief chest rendering and contact check. */
    public void debugEliteChestEncounter() {
        if (outcome != Outcome.RUNNING || choicePending() || bossSpawned || livingPlayerCount() == 0
            || chestsDropped >= MAX_CHESTS) return;
        debugSceneryView(0, 0);
        spawnCountdown = 30;
        Player anchor = players[0].active && players[0].alive ? players[0] : players[1];
        for (Player player : players) if (player.active && player.alive)
            player.attackCooldown = player.auraCooldown = player.stormCooldown = 30;
        Enemy elite = spawnSizingFixture(EnemyKind.BRUTE, EnemyVariant.ORIGINAL, true, anchor);
        if (elite != null) {
            elite.x = 100; elite.y = -100;
            hit(elite, elite.health, anchor.slot, false);
        }
        clearInputs();
    }

    /** Profile-only size proof: four species, each Original / variant / elite, with a hero reference. */
    public void debugEnemySizeLineup(ActorAnimation.Facing facing) {
        if (outcome != Outcome.RUNNING || choicePending() || bossSpawned || activePlayerCount() == 0) return;
        debugSceneryView(0, 0);
        debugSizingFacing = facing; spawnCountdown = 30;
        float dx = facing == ActorAnimation.Facing.NE ? 1 : facing == ActorAnimation.Facing.SW ? -1 : 0;
        float dy = facing == ActorAnimation.Facing.NW ? 1 : facing == ActorAnimation.Facing.SE ? -1 : 0;
        Player anchor = players[0].active && players[0].alive ? players[0] : players[1];
        for (Player player : players) if (player.active && player.alive) {
            player.health = equipment.maxHealth(player); player.attackCooldown = player.auraCooldown = player.stormCooldown = 30;
            player.facingX = dx; player.facingY = dy; resetPresentation(player);
        }
        for (int i = 0; i < 12; i++) {
            int column = i % 4, row = i / 4;
            EnemyKind kind = column == 0 ? EnemyKind.GHOUL : column == 1 ? EnemyKind.WRAITH
                : column == 2 ? EnemyKind.CULTIST : EnemyKind.BRUTE;
            EnemyVariant variant = row == 0 ? EnemyVariant.ORIGINAL
                : kind == EnemyKind.GHOUL || kind == EnemyKind.BRUTE ? EnemyVariant.MOSS : EnemyVariant.DUSK;
            Enemy e = spawnSizingFixture(kind, variant, row == 2, anchor);
            if (e == null) continue;
            float sx = -330 + column * 220, sy = -210 + row * 150;
            e.x = .5f * (sx / .70710678f + sy / .35355339f);
            e.y = .5f * (sy / .35355339f - sx / .70710678f);
            e.health = e.maxHealth = 600; e.stunTime = e.attackCooldown = 30; e.spawnAge = 1;
            e.facingX = e.castFacingX = dx; e.facingY = e.castFacingY = dy;
        }
        clearInputs();
    }

    /** Profile-only real elite cast; no historical waves or long survival fast-forward. */
    public void debugSizedCultistAttack(ActorAnimation.Facing facing) {
        if (outcome != Outcome.RUNNING || choicePending() || bossSpawned || activePlayerCount() == 0) return;
        debugSceneryView(0, 0);
        float dx = facing == ActorAnimation.Facing.NE ? 1 : facing == ActorAnimation.Facing.SW ? -1 : 0;
        float dy = facing == ActorAnimation.Facing.NW ? 1 : facing == ActorAnimation.Facing.SE ? -1 : 0;
        Player anchor = players[0].active && players[0].alive ? players[0] : players[1];
        for (Player player : players) if (player.active && player.alive) {
            player.health = equipment.maxHealth(player); player.invulnerable = 0;
            player.attackCooldown = player.auraCooldown = player.stormCooldown = 30;
        }
        Enemy e = spawnSizingFixture(EnemyKind.CULTIST, EnemyVariant.ORIGINAL, true, anchor);
        if (e != null) {
            e.x = anchor.x - dx * 300; e.y = anchor.y - dy * 300;
            e.health = e.maxHealth = 600; e.attackCooldown = 0; e.spawnAge = 1;
            faceTarget(e, anchor);
        }
        clearInputs();
    }

    private Enemy spawnSizingFixture(EnemyKind kind, EnemyVariant variant, boolean elite, Player anchor) {
        for (int attempt = 0; attempt < 64; attempt++) {
            Enemy enemy = spawn(kind, elite, anchor, 0);
            if (enemy == null || enemy.variant == variant) return enemy;
            enemy.active = false; activeEnemies--;
        }
        return null;
    }

    /** Profile-only close combat fixture for brief sprite capture; normal encounters never use it. */
    public void debugVfxEncounter() {
        if (outcome != Outcome.RUNNING || choicePending() || bossSpawned) return;
        for (Enemy e : enemies) e.active = false;
        for (Bolt b : bolts) b.active = false;
        for (Shot s : shots) s.active = false;
        for (Hazard h : hazards) h.active = false;
        for (Effect e : effects) e.active = false;
        activeEnemies = 0; spawnCountdown = 6;
        for (Player p : players) if (p.active && p.alive) {
            p.attackCooldown = .25f;
            p.invulnerable = Math.max(p.invulnerable, 1.5f);
            for (int i = 0; i < 2; i++) {
                Enemy e = spawn(i == 0 ? EnemyKind.BRUTE : EnemyKind.GHOUL, false, p, i);
                if (e == null) continue;
                e.x = clamp(p.x + 90 + i * 70, -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
                e.y = clamp(p.y + (i == 0 ? 25 : -50), -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
                faceTarget(e, p);
            }
        }
    }

    /** Profile-only short attack composition; normal encounter spawning never calls this. */
    public void debugEnemyAttackEncounter() {
        if (outcome != Outcome.RUNNING || choicePending() || bossSpawned || activePlayerCount() == 0) return;
        for (Enemy e : enemies) e.active = false;
        for (Bolt b : bolts) b.active = false;
        for (Shot s : shots) s.active = false;
        for (Hazard h : hazards) h.active = false;
        for (Effect e : effects) e.active = false;
        for (Pickup p : pickups) p.active = false;
        activeEnemies = 0; spawnCountdown = 12;
        Player anchor = players[0].active && players[0].alive ? players[0] : players[1];
        for (Player p : players) if (p.active && p.alive) {
            p.health = equipment.maxHealth(p); p.invulnerable = 12; p.attackCooldown = .3f;
        }
        for (int i = 0; i < 3; i++) {
            EnemyKind kind = i == 0 ? EnemyKind.WRAITH : i == 1 ? EnemyKind.BRUTE : EnemyKind.CULTIST;
            Enemy e = spawn(kind, false, anchor, i);
            if (e == null) continue;
            e.x = clamp(anchor.x + (i == 0 ? -10 : i == 1 ? 85 : 290), -HALF_WIDTH + e.radius, HALF_WIDTH - e.radius);
            e.y = clamp(anchor.y + (i == 0 ? 220 : i == 1 ? -50 : 10), -HALF_HEIGHT + e.radius, HALF_HEIGHT - e.radius);
            e.health = e.maxHealth = 600; e.attackCooldown = .25f + i * .35f; e.spawnAge = 1;
            faceTarget(e, anchor);
        }
    }

    /** Developer fixture: start the final encounter immediately without a survival playthrough. */
    public void debugStartBoss() {
        if (bossSpawned || outcome != Outcome.RUNNING || activePlayerCount() == 0) return;
        elapsed = Math.max(elapsed, durationSeconds()); wave = timeline.phaseCount();
        if (!sovereignSpawned) sovereignSkipped = true; // Isolate the final fixture from unearned history.
        spawnFinalBoss();
    }

    /** Developer fixture: jump to the twelve-minute miniboss without advancing a full run. */
    public void debugStartSovereign() {
        if (sovereignSpawned || outcome != Outcome.RUNNING || activePlayerCount() == 0) return;
        elapsed = Math.max(elapsed, timeline.sovereignSeconds()); wave = timeline.phaseIndex(elapsed) + 1;
        sovereignSkipped = false; spawnSovereign();
    }

    private void reserveBossSlot() {
        if (activeEnemies < MAX_ENEMIES) return;
        // Required encounters may replace one ordinary reinforcement, never the other boss or an elite.
        for (Enemy e : enemies) if (e.active && !isBoss(e.kind) && !e.elite) {
            e.active = false; activeEnemies--; return;
        }
    }

    private void spawnSovereign() {
        if (sovereignSpawned) return;
        reserveBossSlot();
        Enemy spawned = spawn(EnemyKind.SOVEREIGN, false);
        if (spawned != null) { sovereign = spawned; sovereignSpawned = true; }
    }

    private void spawnFinalBoss() {
        if (bossSpawned) return;
        reserveBossSlot();
        Enemy spawned = spawn(EnemyKind.BOSS, false);
        if (spawned != null) { boss = spawned; bossSpawned = true; bossPhase = 1; }
    }

    /** Profile fixture: bounded late-run density, with no survival fast-forward or historical elites. */
    public void debugStartLateEncounter() {
        if (outcome != Outcome.RUNNING || activePlayerCount() == 0) return;
        elapsed = durationSeconds() * .925f; simulationElapsed = elapsed;
        wave = timeline.phaseIndex(elapsed) + 1; encounterPhase = wave - 1;
        nextCheckpoint = wave; spawnCountdown = 1.4f;
        bossSpawned = bossDefeated = false; boss = null; bossPhase = 1;
        // This late fixture deliberately omits historical encounters, including the Sovereign.
        sovereignSpawned = sovereignDefeated = false; sovereign = null; sovereignSkipped = true;
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
    private Bolt freeBolt() {
        for (Bolt b : bolts) if (!b.active) return b;
        if (diagnostics != null) diagnostics.boltAcquireMisses++;
        return null;
    }
    private Effect effect(EffectKind kind, float x, float y, int owner, float duration) {
        for (Effect e : effects) if (!e.active) {
            e.active = true; e.kind = kind; e.x = x; e.y = y; e.x2 = x; e.y2 = y;
            e.angle = e.radius = e.sweep = e.dirX = e.dirY = e.amount = e.projectileFacingX = e.projectileFacingY = 0;
            e.critical = e.heroHit = e.healing = e.elite = false; e.enemyKind = null;
            e.enemyVariant = EnemyVariant.ORIGINAL;
            e.impactKind = ImpactKind.MAGIC;
            e.ttl = e.maxTtl = duration; e.owner = owner; return e;
        }
        if (diagnostics != null) diagnostics.effectAcquireMisses++;
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
        public boolean active, alive, moving;
        public float x, y, health, maxHealth, dodgeCooldown, dodgeTime, invulnerable, attackCooldown;
        public float reviveProgress, facingX, facingY, walkTime;
        /** Presentation only; ages saturate outside a short action and advance in combat ticks. */
        public float attackAge = 1, attackFacingX, attackFacingY = -1, hurtTime;
        public float damageMultiplier = 1, attackRateMultiplier = 1, moveMultiplier = 1, dodgeCooldownMultiplier = 1;
        public float cleaveMultiplier = 1, critChance, pickupMultiplier = 1, auraCooldown, stormCooldown, damageDealt, damageTaken;
        public float healingFromDrops, healingFromRegeneration, healingFromSustain;
        private float dodgeX, dodgeY;
        final DodgeTrail dodgeTrail = new DodgeTrail();
        float dodgeFacingX() { return dodgeX; }
        float dodgeFacingY() { return dodgeY; }
        private Player(int slot) { this.slot = slot; hero = slot == 0 ? Hero.WARDEN : Hero.RANGER; }
    }
    public static final class Enemy {
        public boolean active, elite, moving, bossCastEnraged;
        public int encounterPhase = 1, bossAttackIndex;
        private boolean bossVolleyPending;
        public EnemyAttack attack = EnemyAttack.NONE;
        public float attackAge = 2, attackStartX, attackStartY, attackTargetX, attackTargetY, attackRadius;
        public int attackHitMask;
        private float dashDistance;
        public EnemyKind kind = EnemyKind.GHOUL;
        public EnemyVariant variant = EnemyVariant.ORIGINAL;
        public int soulPercent = 100;
        public float x, y, health, maxHealth, animTime, hitFlash, vx, vy;
        public float hitDirectionX, hitDirectionY;
        public float spawnAge = 1;
        public float facingX, facingY = -1, moveTime, castAge = 1, castDuration, castFacingX, castFacingY = -1, releaseAge = 1;
        public float radius = 20, stunTime, attackCooldown, windup, damage = 12, speedMultiplier = 1;
    }
    public static final class Bolt {
        public boolean active, critical;
        public float x, y, vx, vy, ttl, age, damage = 16;
        public float launchFacingX, launchFacingY;
        public int owner, pierce;
        public long hitMask;
    }
    public static final class Pickup {
        public boolean active, healing, eliteHealing;
        public float x, y, ttl, maxTtl;
        public int value;
    }
    public static final class Chest {
        public boolean active;
        public float x, y, pickupDelay;
    }
    public static final class Shot {
        public boolean active, casterElite;
        public EnemyKind casterKind;
        public EnemyVariant casterVariant = EnemyVariant.ORIGINAL;
        public float casterRenderScale = 1f;
        public float x, y, vx, vy, ttl, age, damage, radius, launchFacingX, launchFacingY;
    }
    public static final class Hazard { public boolean active; public float x, y, radius, warning, ttl, damage; }
    public static final class Effect {
        public boolean active; public EffectKind kind;
        public float x, y, x2, y2, angle, radius, sweep, ttl, maxTtl; public int owner;
        public float dirX, dirY, amount;
        public float projectileFacingX, projectileFacingY;
        public ImpactKind impactKind = ImpactKind.MAGIC;
        public boolean critical, heroHit, healing, elite;
        public EnemyKind enemyKind;
        public EnemyVariant enemyVariant = EnemyVariant.ORIGINAL;
    }
}
