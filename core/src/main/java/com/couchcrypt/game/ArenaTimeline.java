package com.couchcrypt.game;

import java.util.List;

/** Immutable, bounded encounter schedules; SHORT remains a development fixture. */
public final class ArenaTimeline {
    public enum Pattern {
        SWARM(ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.GHOUL),
        BRUTE_PRESSURE(ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH),
        CULTIST_FORMATION(ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.GHOUL,
            ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.CULTIST),
        HUNT(ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH),
        SIEGE(ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.GHOUL,
            ArenaWorld.EnemyKind.GHOUL),
        ONSLAUGHT(ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.CULTIST,
            ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH);

        private final ArenaWorld.EnemyKind[] members;
        Pattern(ArenaWorld.EnemyKind... members) { this.members = members; }
        public int size() { return members.length; }
        public ArenaWorld.EnemyKind member(int index) { return members[index]; }
    }

    public record Phase(String name, float durationSeconds, Pattern pattern, float encounterInterval,
                        float cycleSeconds, float breatherSeconds, ArenaWorld.EnemyKind eliteKind,
                        float healthMultiplier, float damageMultiplier, int soloEnemyLimit) {
        /** Backward-compatible custom/short fixture phase. */
        public Phase(float durationSeconds, Pattern pattern, float encounterInterval,
                     float cycleSeconds, float breatherSeconds, ArenaWorld.EnemyKind eliteKind) {
            this(pattern == null ? "" : pattern.name().replace('_', ' '), durationSeconds, pattern,
                encounterInterval, cycleSeconds, breatherSeconds, eliteKind, 1, 1, ArenaWorld.MAX_ENEMIES - 2);
        }
        public Phase {
            if (name == null || name.isBlank() || !Float.isFinite(healthMultiplier) || healthMultiplier <= 0
                || !Float.isFinite(damageMultiplier) || damageMultiplier <= 0
                || soloEnemyLimit < 1 || soloEnemyLimit > ArenaWorld.MAX_ENEMIES - 2
                || !Float.isFinite(durationSeconds) || durationSeconds <= 0 || pattern == null
                || !Float.isFinite(encounterInterval) || encounterInterval <= 0
                || !Float.isFinite(cycleSeconds) || cycleSeconds <= 0
                || !Float.isFinite(breatherSeconds) || breatherSeconds < 0 || breatherSeconds >= cycleSeconds
                || ArenaWorld.isBoss(eliteKind))
                throw new IllegalArgumentException("Invalid encounter phase");
        }
        public boolean breathing(float phaseElapsed) { return phaseElapsed % cycleSeconds >= cycleSeconds - breatherSeconds; }
    }

    public static final ArenaTimeline SHORT = new ArenaTimeline(
        phase(60, Pattern.SWARM, 2.8f, null),
        phase(60, Pattern.BRUTE_PRESSURE, 2.5f, ArenaWorld.EnemyKind.BRUTE),
        phase(60, Pattern.CULTIST_FORMATION, 2.5f, ArenaWorld.EnemyKind.CULTIST));

    /** Authored twenty-minute survival schedule. Balance targets require human gameplay feedback. */
    public static final ArenaTimeline TWENTY_MINUTES = new ArenaTimeline(
        longPhase("THE CRYPT STIRS", 90, Pattern.SWARM, 2.7f, 4, null, 1, 1, 28),
        longPhase("RESTLESS HUNT", 90, Pattern.HUNT, 2.55f, 3, null, 1.2f, 1.1f, 30),
        longPhase("THE IRON MARCH", 120, Pattern.BRUTE_PRESSURE, 2.35f, 3, ArenaWorld.EnemyKind.BRUTE, 1.4f, 1.15f, 32),
        longPhase("CHOIR OF ASH", 120, Pattern.CULTIST_FORMATION, 2.8f, 3, null, 1.7f, 1.25f, 34),
        longPhase("BROKEN RANKS", 120, Pattern.SIEGE, 2.5f, 3, ArenaWorld.EnemyKind.CULTIST, 2, 1.3f, 36),
        longPhase("WRAITH PROCESSION", 120, Pattern.HUNT, 1.75f, 3, null, 2.5f, 1.4f, 38),
        longPhase("SOVEREIGN'S GUARD", 120, Pattern.SIEGE, 2.1f, 2, ArenaWorld.EnemyKind.BRUTE, 3, 1.5f, 40),
        longPhase("THE CLOSING CIRCLE", 120, Pattern.ONSLAUGHT, 2.4f, 2, null, 3.4f, 1.65f, 42),
        longPhase("DREAD REINFORCEMENTS", 90, Pattern.BRUTE_PRESSURE, 1.3f, 2, ArenaWorld.EnemyKind.CULTIST, 3.9f, 1.8f, 44),
        longPhase("LAST VIGIL", 90, Pattern.CULTIST_FORMATION, 1.6f, 2, null, 4.2f, 1.85f, 46),
        longPhase("THE HOLLOW HOST", 60, Pattern.ONSLAUGHT, 1.85f, 2, ArenaWorld.EnemyKind.BRUTE, 4.5f, 1.95f, 50),
        longPhase("BEFORE THE THRONE", 60, Pattern.ONSLAUGHT, 5f / 3f, 2, null, 4.8f, 2, 50));

    private final List<Phase> phases;
    private final float duration;

    public ArenaTimeline(Phase... phases) {
        if (phases.length == 0) throw new IllegalArgumentException("At least one phase is required");
        this.phases = List.of(phases);
        float total = 0;
        for (Phase phase : this.phases) total += phase.durationSeconds();
        if (!Float.isFinite(total)) throw new IllegalArgumentException("Timeline duration must be finite");
        duration = total;
    }

    public int phaseCount() { return phases.size(); }
    public Phase phase(int index) { return phases.get(index); }
    public float durationSeconds() { return duration; }
    /** Twelve minutes in production, proportionally shortened only in development fixtures. */
    public float sovereignSeconds() { return duration * .6f; }

    public int phaseIndex(float elapsed) {
        float end = 0;
        for (int i = 0; i < phases.size() - 1; i++) {
            end += phases.get(i).durationSeconds();
            if (elapsed < end) return i;
        }
        return phases.size() - 1;
    }

    public float phaseStart(int index) {
        float start = 0;
        for (int i = 0; i < index; i++) start += phases.get(i).durationSeconds();
        return start;
    }

    /** Progress belongs to the active phase and is bounded before/after the survival schedule. */
    public float phaseProgress(float elapsed) {
        int index = phaseIndex(elapsed);
        return Math.max(0, Math.min(1, (elapsed - phaseStart(index)) / phases.get(index).durationSeconds()));
    }

    /** Full-run nodes are phase-start values; the final node remains steady into the boss encounter. */
    public float healthMultiplier(float elapsed) {
        int index = phaseIndex(elapsed);
        float current = phases.get(index).healthMultiplier();
        if (!isFullRun() || index == phases.size() - 1) return current;
        return current + (phases.get(index + 1).healthMultiplier() - current) * phaseProgress(elapsed);
    }

    public float damageMultiplier(float elapsed) {
        int index = phaseIndex(elapsed);
        float current = phases.get(index).damageMultiplier();
        if (!isFullRun() || index == phases.size() - 1) return current;
        return current + (phases.get(index + 1).damageMultiplier() - current) * phaseProgress(elapsed);
    }

    public boolean isFullRun() { return duration >= 1200; }

    private static Phase longPhase(String name, float seconds, Pattern pattern, float interval,
                                   float breather, ArenaWorld.EnemyKind elite, float health, float damage, int limit) {
        return new Phase(name, seconds, pattern, interval, 24, breather, elite, health, damage, limit);
    }

    private static Phase phase(float seconds, Pattern pattern, float interval, ArenaWorld.EnemyKind elite) {
        return new Phase(seconds, pattern, interval, 18, 3, elite);
    }
}
