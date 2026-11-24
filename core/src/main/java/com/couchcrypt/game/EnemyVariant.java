package com.couchcrypt.game;

/** Cosmetic colour multipliers for the existing painted frames; never used by combat rules. */
public enum EnemyVariant {
    ORIGINAL(1f, 1f, 1f),
    MOSS(.67f, 1f, .74f),
    FROST(.22f, .80f, 1f),
    EMBER(1f, .70f, .56f),
    DUSK(.18f, 1f, 1f);

    public final float red, green, blue;

    EnemyVariant(float red, float green, float blue) {
        this.red = red; this.green = green; this.blue = blue;
    }

    /** A separate cosmetic hash avoids consuming the combat random stream or tying colour to pool slots. */
    static EnemyVariant forSpawn(ArenaWorld.EnemyKind kind, int seed, int serial) {
        if (kind == ArenaWorld.EnemyKind.BOSS) return ORIGINAL;
        int hash = seed ^ (serial * 0x45d9f3b) ^ (kind.ordinal() * 0x27d4eb2d);
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        int palette = Math.floorMod(hash ^ (hash >>> 16), 3);
        if (palette == 0) return ORIGINAL;
        return switch (kind) {
            case GHOUL, BRUTE -> palette == 1 ? MOSS : FROST;
            case WRAITH, CULTIST -> palette == 1 ? EMBER : DUSK;
            case BOSS -> ORIGINAL;
        };
    }
}
