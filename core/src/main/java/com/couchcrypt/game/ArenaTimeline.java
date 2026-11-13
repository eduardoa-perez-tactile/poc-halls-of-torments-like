package com.couchcrypt.game;

import java.util.List;

/** Immutable encounter schedule. The default remains a short, playable arena slice. */
public final class ArenaTimeline {
    public enum Pattern {
        SWARM(ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.GHOUL),
        BRUTE_PRESSURE(ArenaWorld.EnemyKind.BRUTE, ArenaWorld.EnemyKind.GHOUL, ArenaWorld.EnemyKind.WRAITH),
        CULTIST_FORMATION(ArenaWorld.EnemyKind.CULTIST, ArenaWorld.EnemyKind.GHOUL,
            ArenaWorld.EnemyKind.WRAITH, ArenaWorld.EnemyKind.CULTIST);

        private final ArenaWorld.EnemyKind[] members;
        Pattern(ArenaWorld.EnemyKind... members) { this.members = members; }
        public int size() { return members.length; }
        public ArenaWorld.EnemyKind member(int index) { return members[index]; }
    }

    public record Phase(float durationSeconds, Pattern pattern, float encounterInterval,
                        float cycleSeconds, float breatherSeconds, ArenaWorld.EnemyKind eliteKind) {
        public Phase {
            if (!Float.isFinite(durationSeconds) || durationSeconds <= 0 || pattern == null
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

    /** Scheduling capability for future long runs; content and progression balance still need playtesting. */
    public static final ArenaTimeline TWENTY_MINUTES = new ArenaTimeline(
        phase(200, Pattern.SWARM, 2.8f, null),
        phase(200, Pattern.BRUTE_PRESSURE, 2.5f, ArenaWorld.EnemyKind.BRUTE),
        phase(200, Pattern.CULTIST_FORMATION, 2.5f, null),
        phase(200, Pattern.SWARM, 2.4f, ArenaWorld.EnemyKind.CULTIST),
        phase(200, Pattern.BRUTE_PRESSURE, 2.3f, null),
        phase(200, Pattern.CULTIST_FORMATION, 2.3f, ArenaWorld.EnemyKind.BRUTE));

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

    private static Phase phase(float seconds, Pattern pattern, float interval, ArenaWorld.EnemyKind elite) {
        return new Phase(seconds, pattern, interval, 18, 3, elite);
    }
}
