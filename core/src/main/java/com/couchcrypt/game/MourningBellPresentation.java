package com.couchcrypt.game;

/** Pure, combat-clock-owned posing; both cameras read the same body and never animate simulation. */
final class MourningBellPresentation {
    private MourningBellPresentation() { }

    static float hover(float combatTime) {
        return 15f + (float) Math.sin(combatTime * 2.1f) * 2.5f;
    }

    static float tilt(ArenaWorld.Enemy enemy) {
        float direction = enemy.castFacingX - enemy.castFacingY >= 0 ? -1f : 1f;
        float age = enemy.attackAge;
        if (enemy.attack == ArenaWorld.EnemyAttack.BELL_LUNGE) {
            if (age < MourningBell.LUNGE_WINDUP)
                return -direction * 6f * clamp(age / MourningBell.LUNGE_WINDUP);
            if (age < MourningBell.LUNGE_WINDUP + MourningBell.LUNGE_SECONDS) return direction * 11f;
            float recovery = clamp((age - MourningBell.LUNGE_WINDUP - MourningBell.LUNGE_SECONDS)
                / (MourningBell.LUNGE_DURATION - MourningBell.LUNGE_WINDUP - MourningBell.LUNGE_SECONDS));
            return direction * 7f * (1f - recovery) * (float) Math.cos(recovery * Math.PI * 3);
        }
        if (enemy.attack == ArenaWorld.EnemyAttack.BELL_TOLL && enemy.releaseAge < .3f)
            return -direction * 5f * (float) Math.sin(clamp(enemy.releaseAge / .3f) * Math.PI);
        return 0f;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
