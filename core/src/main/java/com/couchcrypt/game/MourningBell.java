package com.couchcrypt.game;

/** Fixed, combat-owned Bell phases shared by collision and presentation. No per-cast objects. */
public final class MourningBell {
    public static final float LUNGE_WINDUP = .85f, LUNGE_SECONDS = .4f, LUNGE_DURATION = 2.1f;
    public static final float LUNGE_RANGE = 420f, LUNGE_DAMAGE = 16f;
    public static final float TOLL_WINDUP = .9f, TOLL_WAVE_SECONDS = .75f, TOLL_RADIUS = 260f;
    public static final float TOLL_INNER_RADIUS = 20f, TOLL_HALF_WIDTH = 12f, TOLL_DAMAGE = 14f;
    public static final float SECOND_TOLL_WARNING_START = 1.65f, SECOND_TOLL_RELEASE = 2.35f;
    public static final float TOLL_DURATION = 2.3f, DOUBLE_TOLL_DURATION = 3.75f;

    private MourningBell() {}

    public static float tollRelease(int beat) { return beat == 0 ? TOLL_WINDUP : SECOND_TOLL_RELEASE; }

    public static boolean tollWarning(ArenaWorld.Enemy enemy, int beat) {
        if (!hasBeat(enemy, beat)) return false;
        float start = beat == 0 ? 0 : SECOND_TOLL_WARNING_START;
        return enemy.attackAge >= start && enemy.attackAge + .000001f < tollRelease(beat);
    }

    public static boolean tollActive(ArenaWorld.Enemy enemy, int beat) {
        if (!hasBeat(enemy, beat)) return false;
        float age = enemy.attackAge - tollRelease(beat);
        return age >= -.000001f && age < TOLL_WAVE_SECONDS;
    }

    public static float tollRadius(ArenaWorld.Enemy enemy, int beat) {
        return radiusAt(enemy.attackAge, beat);
    }

    static float radiusAt(float attackAge, int beat) {
        float progress = Math.max(0, Math.min(1, (attackAge - tollRelease(beat)) / TOLL_WAVE_SECONDS));
        return TOLL_INNER_RADIUS + (TOLL_RADIUS - TOLL_INNER_RADIUS) * progress;
    }

    private static boolean hasBeat(ArenaWorld.Enemy enemy, int beat) {
        return enemy.attack == ArenaWorld.EnemyAttack.BELL_TOLL && beat >= 0 && beat < (enemy.bossCastEnraged ? 2 : 1);
    }
}
