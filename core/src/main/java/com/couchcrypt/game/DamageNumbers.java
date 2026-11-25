package com.couchcrypt.game;

/** Bounded combat-owned hit labels, independent of combat's effect pool and pooled enemy identities. */
final class DamageNumbers {
    static final int CAPACITY = 32, MAX_DIGITS = 5;
    static final float LIFETIME = .65f;
    final Entry[] entries = new Entry[CAPACITY];
    private int next;

    DamageNumbers() {
        for (int i = 0; i < entries.length; i++) entries[i] = new Entry();
    }

    void clear() {
        for (Entry entry : entries) entry.ttl = 0;
        next = 0;
    }

    void update(float delta) {
        for (Entry entry : entries) entry.ttl = Math.max(0, entry.ttl - delta);
    }

    void record(ArenaWorld.Enemy enemy, float actual, boolean critical) {
        if (!Float.isFinite(actual) || actual <= 0) return;
        Entry entry = entries[next];
        entry.x = enemy.x; entry.y = enemy.y;
        entry.enemyKind = enemy.kind;
        entry.renderScale = EnemySizing.renderScale(enemy.kind, enemy.variant, enemy.elite);
        entry.liftPadding = enemy.elite ? 38f : enemy.variant.healthMultiplier > 1f ? 24f : 8f;
        entry.critical = critical; entry.actual = actual; entry.ttl = LIFETIME;
        entry.offsetX = ((next % 3) - 1) * 7f;
        int value = Math.max(1, Math.min(99_999, Math.round(actual)));
        entry.digitCount = 0;
        do {
            entry.digits[entry.digitCount++] = value % 10;
            value /= 10;
        } while (value > 0);
        next = (next + 1) % entries.length;
    }

    static final class Entry {
        final int[] digits = new int[MAX_DIGITS]; // Least significant first; no formatted strings.
        int digitCount;
        float x, y, renderScale, actual, ttl, offsetX, liftPadding;
        boolean critical;
        ArenaWorld.EnemyKind enemyKind;

        float rise() { return 24f * (1f - ttl / LIFETIME); }
        float alpha() { return Math.min(1f, ttl / .2f); }
    }
}
