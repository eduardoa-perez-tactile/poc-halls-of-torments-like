package com.couchcrypt.game;

/** Small combat-clock history of actual Dodge poses; both cameras read the same snapshots. */
final class DodgeTrail {
    static final int CAPACITY = 6;
    static final float LIFETIME = .24f, INTERVAL = .05f;
    final Snapshot[] snapshots = new Snapshot[CAPACITY];
    private float untilNext;
    private int next;

    DodgeTrail() {
        for (int i = 0; i < snapshots.length; i++) snapshots[i] = new Snapshot();
    }

    void clear() {
        for (Snapshot snapshot : snapshots) snapshot.ttl = 0;
        untilNext = 0; next = 0;
    }

    void update(float delta) {
        for (Snapshot snapshot : snapshots) snapshot.ttl = Math.max(0, snapshot.ttl - delta);
        untilNext = Math.max(0, untilNext - delta);
    }

    void capture(ArenaWorld.Player player) {
        if (untilNext > .00001f) return;
        Snapshot snapshot = snapshots[next];
        snapshot.x = player.x; snapshot.y = player.y; snapshot.hero = player.hero;
        snapshot.state = ActorAnimation.state(player);
        snapshot.facing = ActorAnimation.facing(player, snapshot.state);
        snapshot.phase = ActorAnimation.phase(player, snapshot.state);
        snapshot.ttl = LIFETIME;
        next = (next + 1) % snapshots.length;
        untilNext = INTERVAL;
    }

    static final class Snapshot {
        float x, y, phase, ttl;
        ArenaWorld.Hero hero;
        ActorAnimation.State state;
        ActorAnimation.Facing facing;

        float alpha() { return .42f * ttl / LIFETIME; }
    }
}
