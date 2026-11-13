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
                || eliteKind == ArenaWorld.EnemyKind.BOSS)
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
        longPhase("THE CRYPT STIRS", 90, Pattern.SWARM, 3.5f, 4, null, 1, 1, 26),
        longPhase("RESTLESS HUNT", 90, Pattern.HUNT, 3.3f, 4, null, 1.12f, 1.04f, 28),
        longPhase("THE IRON MARCH", 120, Pattern.BRUTE_PRESSURE, 3.2f, 5, ArenaWorld.EnemyKind.BRUTE, 1.3f, 1.08f, 30),
        longPhase("CHOIR OF ASH", 120, Pattern.CULTIST_FORMATION, 3.3f, 4, null, 1.5f, 1.12f, 32),
        longPhase("BROKEN RANKS", 120, Pattern.SIEGE, 3.1f, 5, ArenaWorld.EnemyKind.CULTIST, 1.7f, 1.17f, 34),
        longPhase("WRAITH PROCESSION", 120, Pattern.HUNT, 2.5f, 4, null, 1.9f, 1.22f, 36),
        longPhase("SOVEREIGN'S GUARD", 120, Pattern.SIEGE, 2.8f, 5, ArenaWorld.EnemyKind.BRUTE, 2.1f, 1.27f, 38),
        longPhase("THE CLOSING CIRCLE", 120, Pattern.ONSLAUGHT, 3, 4, null, 2.35f, 1.32f, 40),
        longPhase("DREAD REINFORCEMENTS", 90, Pattern.BRUTE_PRESSURE, 2.2f, 5, ArenaWorld.EnemyKind.CULTIST, 2.55f, 1.37f, 42),
        longPhase("LAST VIGIL", 90, Pattern.CULTIST_FORMATION, 2.5f, 4, null, 2.8f, 1.42f, 44),
        longPhase("THE HOLLOW HOST", 60, Pattern.ONSLAUGHT, 2.5f, 5, ArenaWorld.EnemyKind.BRUTE, 3, 1.46f, 46),
        longPhase("BEFORE THE THRONE", 60, Pattern.ONSLAUGHT, 2.1f, 4, null, 3.2f, 1.5f, 46));

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

    public boolean isFullRun() { return duration >= 1200; }

    private static Phase longPhase(String name, float seconds, Pattern pattern, float interval,
                                   float breather, ArenaWorld.EnemyKind elite, float health, float damage, int limit) {
        return new Phase(name, seconds, pattern, interval, 24, breather, elite, health, damage, limit);
    }

    private static Phase phase(float seconds, Pattern pattern, float interval, ArenaWorld.EnemyKind elite) {
        return new Phase(seconds, pattern, interval, 18, 3, elite);
    }
}
